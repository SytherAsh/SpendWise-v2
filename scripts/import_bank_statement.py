#!/usr/bin/env python3
"""Import a transaction dataset (.xlsx or .csv) into `transactions` for a given
user. See scripts/README.md.

Expects the columns produced by ml/labeling: transaction_date, debit, credit,
amount, dr_cr_indicator, transaction_id, recipient_name, upi_id, bank,
transaction_mode, note, category (category is optional).

By default the file's `category` column (if present) is ignored — every row
lands uncategorized, and the backend's CategorizationRetryJob (every 30
minutes, any user) sends each one through the real /predict model, same as
any other transaction. Pass --apply-file-categories to instead write the
file's category values directly (useful for replaying already-labeled
training data), skipping the model.

Pass --categorize-now to call the FastAPI ML service's /predict directly,
right after each row is inserted -- the same call CategorizationServiceImpl
makes for a real /api/v1/ingest request -- instead of waiting on the 30
minute retry job. Requires the ML service to be running and reachable.

Connects directly via psycopg2 as the BYPASSRLS `spendwise_jobs` role (see
backend/db-init/02-jobs-role.sql), so it does not need the app's
`app.current_user_id` RLS session variable. Works against any Postgres
(Docker or Supabase) — connection parameters are never hardcoded; pass them
as flags or via the standard libpq environment variables (PGHOST, PGPORT,
PGDATABASE, PGUSER, PGPASSWORD), exactly like `psql` itself reads them.

Safe to re-run: duplicate transaction_id values for the same user are
skipped (idx_transactions_unique_dedup).

Usage:
    python scripts/import_bank_statement.py \\
        --file ml/data/SpendWise_Final_Labeled.xlsx \\
        --user-id 496d1347-eafc-4379-ae5a-86cf37c64b86 \\
        --host db.ziuzooduhevuopoysgcj.supabase.co --port 5432 \\
        --dbname postgres --user spendwise_jobs --password ...

    # or, with PGHOST/PGPORT/PGDATABASE/PGPASSWORD already exported:
    python scripts/import_bank_statement.py --file my_statement.xlsx --user-id <uuid>
"""

import argparse
import csv
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

import psycopg2

try:
    import openpyxl
except ImportError:
    openpyxl = None

# transaction_id is deliberately not required here -- see synthesize_transaction_id below,
# matching V2__transactions_and_categories.sql's documented fallback for SMS without a bank ref.
REQUIRED_COLUMNS = [
    "transaction_date",
    "debit",
    "credit",
    "amount",
    "dr_cr_indicator",
]

INSERT_TRANSACTION_SQL = """
    INSERT INTO transactions
        (user_id, transaction_date, debit, credit, amount, transaction_mode,
         dr_cr_indicator, transaction_id, recipient_name, bank, upi_id, note, source)
    VALUES
        (%(user_id)s, %(transaction_date)s, %(debit)s, %(credit)s, %(amount)s,
         %(transaction_mode)s, %(dr_cr_indicator)s, %(transaction_id)s, %(recipient_name)s,
         %(bank)s, %(upi_id)s, %(note)s, %(source)s)
    ON CONFLICT (user_id, transaction_id) DO NOTHING
    RETURNING id
"""

INSERT_CATEGORY_SQL = """
    INSERT INTO transaction_categories (transaction_id, category_id, confidence_score, assigned_by)
    SELECT %(txn_id)s, categories.id, 1.0, 'ml' FROM categories WHERE categories.name = %(category)s
"""

INSERT_ML_CATEGORY_SQL = """
    INSERT INTO transaction_categories (transaction_id, category_id, confidence_score, assigned_by)
    VALUES (%(txn_id)s, %(category_id)s, %(confidence)s, 'ml')
"""


def normalize_header(name: str) -> str:
    """'Transaction_Date' / 'DR/CR_Indicator' / 'UPI ID' -> 'transaction_date' /
    'dr_cr_indicator' / 'upi_id' -- so a differently-cased or -punctuated export
    still matches the canonical column names without a hardcoded alias table."""
    return re.sub(r"[^a-z0-9]+", "_", str(name).strip().lower()).strip("_")


def read_rows(path: Path):
    if path.suffix.lower() in (".xlsx", ".xlsm"):
        if openpyxl is None:
            sys.exit("openpyxl is required to read .xlsx files: pip install openpyxl")
        wb = openpyxl.load_workbook(path, read_only=True)
        ws = wb.worksheets[0]
        rows = ws.iter_rows(values_only=True)
        header = [normalize_header(h) for h in next(rows)]
        for row in rows:
            yield dict(zip(header, row))
    else:
        with path.open(newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            reader.fieldnames = [normalize_header(h) for h in reader.fieldnames]
            yield from reader


def none_if_blank(value):
    return None if value in (None, "") else value


def synthesize_transaction_id(user_id: str, upi_id, recipient_name, amount, transaction_date) -> str:
    """SHA-256(user_id || upi_id_or_recipient_name || amount || date_trunc('minute',
    transaction_date)) -- the fallback V2__transactions_and_categories.sql documents for SMS
    transactions with no bank-provided reference number."""
    minute = transaction_date.strftime("%Y-%m-%dT%H:%M") if hasattr(transaction_date, "strftime") else str(transaction_date)[:16]
    key = "|".join([str(user_id), str(upi_id or recipient_name or ""), str(amount), minute])
    return hashlib.sha256(key.encode("utf-8")).hexdigest()


def predict_category(ml_url: str, internal_key: str, row: dict):
    """Same request/response shape as CategorizationServiceImpl.toPredictionRequest /
    MlPredictionResponse -- mirrors the real /api/v1/ingest categorization call exactly."""
    payload = {
        "recipient_name": none_if_blank(row.get("recipient_name")),
        "upi_id": none_if_blank(row.get("upi_id")),
        "bank": none_if_blank(row.get("bank")),
        "transaction_mode": none_if_blank(row.get("transaction_mode")),
        "amount": float(row["amount"]),
        "note": none_if_blank(row.get("note")),
    }
    request = urllib.request.Request(
        ml_url.rstrip("/") + "/predict",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "X-Internal-Key": internal_key},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read())


def import_rows(
    conn,
    rows,
    user_id: str,
    source: str,
    apply_file_categories: bool,
    ml_config: dict | None = None,
) -> tuple[int, int]:
    imported = 0
    skipped = 0
    with conn.cursor() as cur:
        for i, row in enumerate(rows):
            missing = [c for c in REQUIRED_COLUMNS if row.get(c) in (None, "")]
            if missing:
                print(f"Skipping row {i}: missing {missing}", file=sys.stderr)
                skipped += 1
                continue

            transaction_id = none_if_blank(row.get("transaction_id"))
            if transaction_id is None:
                transaction_id = synthesize_transaction_id(
                    user_id, row.get("upi_id"), row.get("recipient_name"), row["amount"], row["transaction_date"])

            cur.execute(
                INSERT_TRANSACTION_SQL,
                {
                    "user_id": user_id,
                    "transaction_date": row["transaction_date"],
                    "debit": row["debit"],
                    "credit": row["credit"],
                    "amount": row["amount"],
                    "transaction_mode": none_if_blank(row.get("transaction_mode")),
                    "dr_cr_indicator": row["dr_cr_indicator"],
                    "transaction_id": transaction_id,
                    "recipient_name": none_if_blank(row.get("recipient_name")),
                    "bank": none_if_blank(row.get("bank")),
                    "upi_id": none_if_blank(row.get("upi_id")),
                    "note": none_if_blank(row.get("note")),
                    "source": source,
                },
            )
            inserted = cur.fetchone()
            if inserted is None:
                continue  # duplicate transaction_id for this user — skipped

            imported += 1
            category = none_if_blank(row.get("category"))
            if apply_file_categories and category:
                cur.execute(INSERT_CATEGORY_SQL, {"txn_id": inserted[0], "category": category})
            elif ml_config:
                try:
                    prediction = predict_category(ml_config["url"], ml_config["internal_key"], row)
                    category_id = prediction["category_id"]
                    confidence = prediction["confidence"]
                    if confidence < ml_config["low_confidence_threshold"]:
                        category_id = ml_config["fallback_category_id"]
                    cur.execute(INSERT_ML_CATEGORY_SQL, {"txn_id": inserted[0], "category_id": category_id, "confidence": confidence})
                except Exception as e:
                    # Deliberately broad -- a single row's /predict call (network error, timeout,
                    # malformed response) must never abort the whole import; the surrounding
                    # transaction would roll back every row already inserted in this run. Same
                    # "never blocks the batch" contract as CategorizationServiceImpl.categorize().
                    # Left uncategorized, eligible for CategorizationRetryJob to pick up later.
                    print(f"Row {i}: /predict failed ({e}), left uncategorized", file=sys.stderr)
    return imported, skipped


def connect(args):
    # Only pass parameters actually given — anything omitted falls back to the
    # standard libpq env vars (PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD),
    # exactly like the psql CLI. Nothing here is hardcoded to Docker or Supabase.
    kwargs = {}
    if args.host:
        kwargs["host"] = args.host
    if args.port:
        kwargs["port"] = args.port
    if args.dbname:
        kwargs["dbname"] = args.dbname
    if args.user:
        kwargs["user"] = args.user
    if args.password:
        kwargs["password"] = args.password
    return psycopg2.connect(**kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--file", required=True, type=Path)
    parser.add_argument("--user-id", required=True)
    parser.add_argument("--source", default="bank_statement", choices=["sms", "bank_statement", "manual"])
    parser.add_argument("--apply-file-categories", action="store_true",
                         help="Write the file's category column directly instead of leaving rows "
                              "uncategorized for the real model (CategorizationRetryJob) to classify.")
    parser.add_argument("--categorize-now", action="store_true",
                         help="Call the ML service's /predict for each row immediately after insert, "
                              "instead of waiting on the 30-minute CategorizationRetryJob. Ignored for "
                              "any row --apply-file-categories already labeled.")
    parser.add_argument("--ml-url", default=os.environ.get("FASTAPI_ML_URL", "http://localhost:8000"),
                         help="FastAPI ML service base URL (falls back to FASTAPI_ML_URL env var, "
                              "same default as application.yml's app.ml.base-url)")
    parser.add_argument("--ml-internal-key", default=os.environ.get("ML_INTERNAL_KEY"),
                         help="X-Internal-Key for the ML service (falls back to ML_INTERNAL_KEY env var)")
    parser.add_argument("--ml-low-confidence-threshold", type=float,
                         default=float(os.environ.get("ML_LOW_CONFIDENCE_THRESHOLD", 0.5)),
                         help="Mirrors application.yml's app.ml.low-confidence-threshold default (0.5)")
    parser.add_argument("--ml-fallback-category-id", type=int,
                         default=int(os.environ.get("ML_FALLBACK_CATEGORY_ID", 6)),
                         help="Mirrors application.yml's app.ml.fallback-category-id default (6, Miscellaneous)")
    parser.add_argument("--host", help="Postgres host (falls back to PGHOST env var)")
    parser.add_argument("--port", type=int, help="Postgres port (falls back to PGPORT env var)")
    parser.add_argument("--dbname", help="Database name (falls back to PGDATABASE env var)")
    parser.add_argument("--user", default="spendwise_jobs",
                         help="Postgres role (default: spendwise_jobs, the BYPASSRLS role — falls back to PGUSER env var if set to empty)")
    parser.add_argument("--password", help="Falls back to PGPASSWORD env var")
    args = parser.parse_args()

    if args.categorize_now and not args.ml_internal_key:
        sys.exit("--categorize-now requires --ml-internal-key or the ML_INTERNAL_KEY env var")

    ml_config = None
    if args.categorize_now:
        ml_config = {
            "url": args.ml_url,
            "internal_key": args.ml_internal_key,
            "low_confidence_threshold": args.ml_low_confidence_threshold,
            "fallback_category_id": args.ml_fallback_category_id,
        }

    rows = list(read_rows(args.file))
    if not rows:
        sys.exit(f"No rows found in {args.file}")
    print(f"Read {len(rows)} rows from {args.file}")

    conn = connect(args)
    try:
        with conn:
            imported, skipped = import_rows(conn, rows, args.user_id, args.source, args.apply_file_categories, ml_config)
    finally:
        conn.close()

    duplicates = len(rows) - imported - skipped
    print(f"Imported {imported} new rows for user {args.user_id} ({duplicates} duplicates, {skipped} skipped as invalid).")
    if not args.apply_file_categories and not args.categorize_now:
        print("Categories were left unassigned — the backend's CategorizationRetryJob "
              "(every 30 min) will run the real model on them automatically.")


if __name__ == "__main__":
    main()
