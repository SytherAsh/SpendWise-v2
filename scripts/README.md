# Scripts

Utility scripts for SpendWise development and data management.

## Planned Scripts

### `import_bank_statement.py`

Imports a transaction dataset (`.xlsx` or `.csv`, in the canonical labeled schema — see
`ml/labeling`) into `transactions` for a given user, via a direct `psycopg2` connection (works
against Docker or Supabase; requires `pip install psycopg2-binary`). By default the file's
`category` column (if any) is ignored, so every imported row lands uncategorized and is picked up
by the backend's `CategorizationRetryJob` (every 30 minutes) for real model inference — pass
`--apply-file-categories` to instead write the file's labels directly and skip the model, or
`--categorize-now` to call the ML service's `/predict` immediately per row (requires the ML
service running and reachable) instead of waiting on that 30-minute job.

Connection parameters are never hardcoded — pass `--host`/`--port`/`--dbname`/`--user`/`--password`,
or leave them unset to fall back to the standard `PGHOST`/`PGPORT`/`PGDATABASE`/`PGUSER`/`PGPASSWORD`
environment variables, exactly like `psql`.

```bash
python scripts/import_bank_statement.py \
  --file path/to/statement.xlsx \
  --user-id <uuid> \
  --host db.<project-ref>.supabase.co --port 5432 --dbname postgres \
  --user spendwise_jobs --password <spendwise_jobs password>
```

Used during:

- Initial ML model training data preparation
- Seeding historical data for a real user's account

### `seed_categories.sql`

Inserts the 10 predefined categories into the `categories` table. Run once after Supabase schema creation.

```bash
psql $SUPABASE_URL -f scripts/seed_categories.sql
```

### `run_migrations.sh`

Applies all pending database migrations in order. Run after adding new migration files.

```bash
bash scripts/run_migrations.sh
```

### `export_corrections.py`

Exports all `ml_corrections` records to a CSV file for offline model analysis.

```bash
python scripts/export_corrections.py --output corrections.csv
```

## Notes

- Scripts that access Supabase require `SUPABASE_URL` and `SUPABASE_KEY` environment variables to be set.
- Never run import scripts against production data without a backup.
- The bank statement Excel file (`SpendWise2k26.xlsx`) is in `.gitignore` — do not commit it.
