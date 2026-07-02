# Counterparty Knowledge — future artifact, NOT wired into anything

`counterparty_knowledge.csv` is a structured extraction of the "who/what is this
recipient" reasoning that came up constantly while building `merchant_rules.csv`
during ML training-data labeling (2026-07-02), so that reasoning isn't lost by only
existing as prose in `tracking/LABELING_STATUS.md`.

## What it is not

- **Not** part of the Category Knowledge Base (`merchant_rules.csv`, `keyword_rules.csv`).
  Those feed `auto_label.py` and ultimately the `category` column the ML model trains
  on. This file is never read by `auto_label.py`, `apply_review.py`,
  `merge_datasets.py`, or anything under `ml/training/`.
- **Not** consumed by the current application, backend, or database schema. There is
  no `counterparty_metadata` table, no backend endpoint, no Android/web UI reading
  this file. It sits in the repo as data only.
- **Not** part of Epic 4 or any currently-scoped epic. It exists for a future,
  not-yet-built enrichment feature — see `docs/architecture.md` "Future Enhancement:
  Counterparty Metadata Enrichment" and ADR-010 in `docs/decisions.md`.

## What it is

Columns:

| Column | Meaning |
|---|---|
| `recipient_pattern` | Same pattern as in `merchant_rules.csv` — the recipient name/UPI substring this row matches |
| `match_field` | `recipient_name` or `upi_id`, same semantics as `merchant_rules.csv` |
| `counterparty_type` | `family`, `self`, `friend`, `merchant`, `subscription_service`, `medical_provider`, `fee_or_debt_service`, `person_unconfirmed`, or `unknown` |
| `confidence` | `high` — individually confirmed during the interview (e.g. "Prachi is my mother"); `medium` — either derived mechanically from the row's spending category, or from the bulk "any other names you find are friends" delegated pass, never confirmed per-name |
| `notes` | Free text — the actual reasoning, where available |
| `derived_from_category` | The spending category this row has in `merchant_rules.csv`, for cross-reference |
| `source_dataset` | Same as `merchant_rules.csv` |

Regenerate with `python build_counterparty_knowledge.py` after `merchant_rules.csv`
changes — it's a derived file, not hand-maintained.

**Read the confidence column before trusting this for anything.** 284 of 406 rows are
`person_unconfirmed` at `medium` confidence — the user delegated "classify remaining
personal-name-shaped recipients as friends" as a judgment call, and only 27 rows were
individually confirmed as `friend` by name, plus 3 `family` and 2 `self`. Only the
`high`-confidence rows are recipient-relationships stated directly by the user; the
`medium` rows on Transfers are worth re-confirming before treating them as fact.

## If someone builds the future enrichment feature

Don't just import this CSV as-is into a production table. At minimum: re-verify the
`medium`-confidence `person_unconfirmed` rows, decide whether `counterparty_type` needs
a different taxonomy for the actual feature, and design the per-user extension
mechanism noted in `docs/roadmap.md` Phase 9 (this file only covers one user's one
historical dataset, not new contacts going forward).
