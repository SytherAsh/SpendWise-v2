# ML Data Labeling Pipeline

Produces the labeled training file `ml/data/spendwise_labeled.xlsx` that
`ml/training/train.py` (Epic 4) consumes. Designed so each new dataset —
another bank statement export, another SMS capture — costs less manual
labeling than the last, because the knowledge base it builds is reusable
across datasets.

## Why this exists

The first dataset (`SpendWise_4yrs.xlsx`, 1,920 rows) had no category labels
at all. Hand-labeling every row doesn't scale to "every future bank
statement and SMS dataset." Labeling by *recipient* instead of by *row*
(475 unique recipients for those 1,920 rows) does — and once a recipient like
`Swiggy` is labeled once, that rule applies to every dataset labeled after it,
forever. That compounding rule store is `knowledge_base/`.

## Directory layout

```
ml/labeling/
├── README.md                  this file
├── CATEGORY_GUIDELINES.md     definitions + boundary calls for the 12 categories
├── knowledge_base/            reusable, committed to git
│   ├── SCHEMA.md
│   ├── merchant_rules.csv     recipient/UPI pattern -> category (feeds the ML pipeline)
│   ├── keyword_rules.csv      note-field keyword -> category (feeds the ML pipeline)
│   ├── counterparty_knowledge.csv       recipient -> counterparty type (friend/family/
│   │                                    merchant/etc.) — NOT read by the ML pipeline,
│   │                                    see counterparty_knowledge.README.md
│   ├── counterparty_knowledge.README.md
│   └── build_counterparty_knowledge.py  regenerates counterparty_knowledge.csv from
│                                         merchant_rules.csv + known relationships
├── scripts/
│   ├── categories.py          canonical category list + schema (shared constant)
│   ├── dataset_config.py      per-dataset column mapping — add new datasets here
│   ├── auto_label.py          step 1: apply knowledge base, split matched/unmatched
│   ├── apply_review.py        step 2: join reviewed labels back, grow knowledge base
│   └── merge_datasets.py      step 3: combine all labeled datasets into the training file
├── datasets/                  gitignored — real transaction data lives here
│   └── <dataset_name>/
│       ├── raw/                original source file
│       ├── auto_labeled.csv    rows the knowledge base already resolved
│       ├── review_sheet.xlsx   unresolved rows, grouped by recipient, for a human
│       └── labeled.csv         auto_labeled + reviewed, combined
└── tracking/
    └── LABELING_STATUS.md      per-dataset progress + knowledge base growth
```

## Workflow for a new dataset

1. Add an entry to `scripts/dataset_config.py`: point at the raw file, map
   its column names onto the canonical schema (`categories.py`). For a
   structured bank-statement export this is the only new code you write.
2. Drop the raw file at `datasets/<name>/raw/`.
3. `python scripts/auto_label.py --dataset <name>` — applies the current
   knowledge base, prints coverage, writes `review_sheet.xlsx` for whatever
   it couldn't resolve.
4. Open `review_sheet.xlsx`, fill in the Category column (dropdown provided)
   for each recipient group. Leave genuinely unclear ones blank — see
   `CATEGORY_GUIDELINES.md` on why guessing is worse than skipping.
5. `python scripts/apply_review.py --dataset <name>` — writes `labeled.csv`
   and appends the newly-reviewed recipients to `knowledge_base/merchant_rules.csv`.
6. `python scripts/merge_datasets.py` — rebuilds `ml/data/spendwise_labeled.xlsx`
   from every dataset's `labeled.csv`, deduped by `transaction_id`.
7. Update `tracking/LABELING_STATUS.md` with the numbers printed in steps 3/5.

## Raw SMS datasets (source_type: `sms_raw`)

Raw SMS bodies (sender + text, no structured fields) need a parsing step
before they fit the canonical schema — the same regex extraction the Android
`parser` module already does (`android/app/src/main/kotlin/com/spendwise/parser/`).
Reuse those rules rather than writing a second, divergent parser here; port
them to Python only when a `sms_raw` dataset is actually ready to be labeled.
`captured_sms_2026` is registered but blocked on this — see
`tracking/LABELING_STATUS.md`.

## Non-goals

This pipeline produces the training file. It does not train the model
(`ml/training/train.py`, Epic 4) or evaluate it (`ml/evaluation/`). It also
isn't where user-correction labels from production go — those land in the
`ml_corrections` Supabase table per `docs/api.md` and feed the retraining
cycle directly, bypassing this manual pipeline entirely. This pipeline is
only for bootstrapping/extending the *initial* training set from historical
data that was never labeled at capture time.

**This pipeline produces exactly one label per row: `category`, one of the 12
values in `docs/requirements.md`.** "Counterparty type" (friend/family/merchant/etc.)
came up constantly while labeling but is a separate concern — it's extracted into
`knowledge_base/counterparty_knowledge.csv`, a standalone artifact that this pipeline
does not produce, read, or depend on. See `CATEGORY_GUIDELINES.md` "Category vs.
Counterparty", `knowledge_base/counterparty_knowledge.README.md`, and ADR-010 in
`docs/decisions.md`. Wiring that data into a queryable, production-consumable
counterparty knowledge base is a separate, not-yet-started, post-MVP effort
(`docs/roadmap.md` Phase 9) — do not conflate the two when extending this pipeline.
