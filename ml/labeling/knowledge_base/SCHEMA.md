# Knowledge Base Schema

Two rule files, both plain CSV so diffs stay readable in git. They are the
reusable asset this whole pipeline exists to build — every dataset labeled
grows them, and every future dataset benefits from what's already here. They
are committed to the repo (unlike `ml/labeling/datasets/`, which holds real
personal transaction data and is gitignored).

## `merchant_rules.csv`

One row per known recipient/UPI pattern. Matched first, against
`recipient_name` and `upi_id`.

| Column | Meaning |
|---|---|
| `pattern` | Substring to match, case-insensitive. |
| `match_field` | `recipient_name`, `upi_id`, or `both`. |
| `category` | One of the 10 categories in `categories.py`. |
| `source_dataset` | Which dataset first produced this rule (provenance). |
| `added_date` | ISO date the rule was added. |

Matching is case-insensitive substring containment: `pattern in field.lower()`.
First match wins — if a row matches multiple rules, the first one in file
order is used, so put more specific patterns above general ones.

## `keyword_rules.csv`

One row per free-text keyword. Matched second, only against `note`, only for
rows `merchant_rules.csv` didn't resolve. Useful for datasets where the note/
remarks field carries a merchant hint that the recipient name doesn't.

| Column | Meaning |
|---|---|
| `pattern` | Substring to match, case-insensitive, against `note`. |
| `category` | One of the 10 categories. |
| `source_dataset` | Provenance. |
| `added_date` | ISO date added. |

## How rules get added

Never edit these by hand mid-dataset. Run `apply_review.py` after filling in
a review sheet — it appends new rules automatically, deduped against what's
already there. Manual edits are fine for cleanup (merging near-duplicate
patterns, fixing a miscategorized rule) but should be reviewed like any other
change to the repo.
