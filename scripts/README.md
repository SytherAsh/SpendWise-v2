# Scripts

Utility scripts for SpendWise development and data management.

## Planned Scripts

### `import_bank_statement.py`

Parses the SBI bank statement Excel file (`SpendWise2k26.xlsx`) and imports transactions into Supabase for a given user.

```bash
python scripts/import_bank_statement.py \
  --file path/to/SpendWise2k26.xlsx \
  --user-id <uuid>
```

Used during:
- Initial ML model training data preparation
- Seeding historical data for personal account

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
