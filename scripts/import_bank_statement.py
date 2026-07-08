#!/usr/bin/env python3
"""Import a labeled transaction dataset (.xlsx or .csv) into `transactions` +
`transaction_categories` for a given user. See scripts/README.md.

Expects the columns produced by ml/labeling: transaction_date, debit, credit,
amount, dr_cr_indicator, transaction_id, recipient_name, upi_id, bank,
transaction_mode, note, category (category is optional; rows without one are
imported uncategorized).

Connects via `docker compose exec postgres psql` as the BYPASSRLS
`spendwise_jobs` role (see backend/db-init/02-jobs-role.sql), so it does not
need the app's `app.current_user_id` RLS session variable. Safe to re-run:
duplicate transaction_id values for the same user are skipped
(idx_transactions_unique_dedup).

Usage:
    python scripts/import_bank_statement.py \\
        --file ml/data/spendwise_labeled.xlsx \\
        --user-id 496d1347-eafc-4379-ae5a-86cf37c64b86
"""

import argparse
import csv
import subprocess
import sys
import uuid
from pathlib import Path

try:
    import openpyxl
except ImportError:
    openpyxl = None

REQUIRED_COLUMNS = [
    "transaction_date",
    "debit",
    "credit",
    "amount",
    "dr_cr_indicator",
    "transaction_id",
]


def read_rows(path: Path):
    if path.suffix.lower() in (".xlsx", ".xlsm"):
        if openpyxl is None:
            sys.exit("openpyxl is required to read .xlsx files: pip install openpyxl")
        wb = openpyxl.load_workbook(path, read_only=True)
        ws = wb.worksheets[0]
        rows = ws.iter_rows(values_only=True)
        header = [str(h) for h in next(rows)]
        for row in rows:
            yield dict(zip(header, row))
    else:
        with path.open(newline="", encoding="utf-8") as f:
            yield from csv.DictReader(f)


def sql_str(value) -> str:
    if value is None or value == "":
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def sql_num(value) -> str:
    if value is None or value == "":
        return "NULL"
    return str(value)


def build_statements(rows, user_id: str, source: str) -> list[str]:
    statements = []
    for i, row in enumerate(rows):
        missing = [c for c in REQUIRED_COLUMNS if row.get(c) in (None, "")]
        if missing:
            raise ValueError(f"row {i} (transaction_id={row.get('transaction_id')!r}) missing: {missing}")

        txn_id = str(uuid.uuid4())
        category = row.get("category")

        insert_txn = f"""
WITH ins AS (
    INSERT INTO transactions
        (id, user_id, transaction_date, debit, credit, amount, transaction_mode,
         dr_cr_indicator, transaction_id, recipient_name, bank, upi_id, note, source)
    VALUES
        ({sql_str(txn_id)}, {sql_str(user_id)}, {sql_str(row['transaction_date'])}::timestamp,
         {sql_num(row['debit'])}, {sql_num(row['credit'])}, {sql_num(row['amount'])},
         {sql_str(row.get('transaction_mode'))}, {sql_str(row['dr_cr_indicator'])},
         {sql_str(row['transaction_id'])}, {sql_str(row.get('recipient_name'))},
         {sql_str(row.get('bank'))}, {sql_str(row.get('upi_id'))}, {sql_str(row.get('note'))},
         {sql_str(source)}::transaction_source)
    ON CONFLICT (user_id, transaction_id) DO NOTHING
    RETURNING id
)"""
        if category:
            statements.append(
                insert_txn
                + f"""
INSERT INTO transaction_categories (transaction_id, category_id, confidence_score, assigned_by)
SELECT ins.id, categories.id, 1.0, 'ml' FROM ins, categories WHERE categories.name = {sql_str(category)};"""
            )
        else:
            statements.append(insert_txn + "\nSELECT 1 FROM ins;")
    return statements


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--file", required=True, type=Path)
    parser.add_argument("--user-id", required=True)
    parser.add_argument("--source", default="bank_statement", choices=["sms", "bank_statement", "manual"])
    parser.add_argument(
        "--compose-dir",
        default=Path(__file__).resolve().parent.parent / "backend",
        type=Path,
        help="Directory containing docker-compose.yml (default: backend/)",
    )
    args = parser.parse_args()

    rows = list(read_rows(args.file))
    if not rows:
        sys.exit(f"No rows found in {args.file}")
    print(f"Read {len(rows)} rows from {args.file}")

    statements = build_statements(rows, args.user_id, args.source)
    sql = "BEGIN;\n" + "\n".join(statements) + "\nCOMMIT;\n"

    result = subprocess.run(
        [
            "docker", "compose", "exec", "-T", "postgres", "psql",
            "-U", "spendwise_jobs", "-d", "spendwise", "-v", "ON_ERROR_STOP=1",
        ],
        input=sql,
        text=True,
        cwd=args.compose_dir,
        capture_output=True,
    )
    if result.returncode != 0:
        print(result.stdout)
        print(result.stderr, file=sys.stderr)
        sys.exit(result.returncode)
    print(f"Imported (or skipped as duplicates) {len(rows)} rows for user {args.user_id}.")


if __name__ == "__main__":
    main()
