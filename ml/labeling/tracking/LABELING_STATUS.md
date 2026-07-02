# ML Labeling Status

Updated by hand after each pipeline run (`auto_label.py` / `apply_review.py`
print the numbers to copy in here). Not auto-generated — keep it honest.

> **Read this before the "friend"/"family"/"self"/"medical shop" language below
> confuses you.** Every rule actually applied below resolves to exactly one of the
> 12 categories in `docs/requirements.md` (usually Transfers, Food / Dine Out,
> Medical, etc.) — friend/family/self is the *reasoning* that got recorded during
> the interview, not a separate label the ML model predicts. That reasoning has
> since been extracted into `knowledge_base/counterparty_knowledge.csv` (this prose
> log is still the primary source/provenance for it) — a standalone artifact, not
> integrated into the app, database, or ML pipeline. See
> `CATEGORY_GUIDELINES.md` "Category vs. Counterparty",
> `knowledge_base/counterparty_knowledge.README.md`, and ADR-010 in
> `docs/decisions.md`.

## Datasets

| Dataset | Source type | Rows | Auto-labeled | Reviewed | Status | Last updated |
|---|---|---|---|---|---|---|
| `spendwise_4yr_sbi_statement` | bank_statement | 1920 | 1808 auto (94.2%) | +3 manual patch | **Finalized 2026-07-02 — merged into `ml/data/spendwise_labeled.xlsx` (1,810 rows after dedup). 59 groups / 110 rows intentionally left unlabeled and excluded from training data — see below.** | 2026-07-02 |
| `captured_sms_2026` | sms_raw | 1146 | — | — | **Blocked** — needs a sender-parsing step before it fits the canonical schema (285 sender IDs, 666/1146 rows with no sender at all). See `dataset_config.py`. | 2026-07-02 |

## Interview progress (`spendwise_4yr_sbi_statement`)

Labeling is being done conversationally (recipient groups ranked by impact,
confirmed one at a time) instead of by hand-filling `review_sheet.xlsx`. Each
confirmed answer is appended straight to `knowledge_base/merchant_rules.csv`
(or `keyword_rules.csv`), then `auto_label.py` is re-run to relabel matches.

| Groups reviewed | Transactions labeled | Remaining groups | Est. completion |
|---|---|---|---|
| ~419 / 475 | 1808 / 1920 | 59 | 94.2% |

Batch 4 (2026-07-02) applied a bulk pass: explicit brand/friend names given
directly by the user (Netflix, Spotify, Zomato, Swiggy, McDonald's, KFC,
Amazon, Zudio, Trent, Bombay Shaving, GoaMiles, Slice, named friends), a few
more unambiguous brands spotted while scanning (DMart, Naturals, 7-Eleven,
boAt, Dream11, Imagicaa), and then a curated bulk "personal name → Transfers"
pass over ~283 remaining low-count groups, with ~76 groups deliberately
excluded because they read as business/brand/institutional names despite
looking name-shaped (e.g. `BIGTREE` = BookMyShow's parent, `Cluckin` = a
fried-chicken chain, `SAI DIAG` = a diagnostics lab, `AWS India` = the AWS
the user asked about). Those exclusions are unverified pattern-matching on
my part, not user-confirmed — worth a spot check.

Resolved: stipend/salary credits → **Transfers** (see `CATEGORY_GUIDELINES.md`
"Open question" section for the reasoning). Two stipend rows under the
literal `UNKNOWN` recipient name were manually patched from Miscellaneous
to Transfers directly in `auto_labeled.csv` (2026-07-02) — can't be a KB
rule since these rows have no stable recipient identity to key a pattern on.

Resolved 2026-07-02: the blank-`Recipient_Name` group's 3 remaining stipend
deposits (₹75,000, dated 03-25/04-24/05-25) were appended directly to
`labeled.csv` as Transfers, same manual-override approach as the `UNKNOWN`
stipend rows — never through the knowledge base, since an empty
`recipient_name` pattern would match every row and silently mislabel the
whole dataset. Left this row's Category blank in `review_sheet.xlsx` on
purpose so `apply_review.py` skips it rather than writing that bad rule.
The 4 unrelated small amounts in that same group (-1900, -1000, 34, -2900,
genuinely unrecognized) were intentionally **not** added — they're part of
the 110 rows left out of the final training file per the "finalize now,
leave the remainder" decision.

Also found: several raw rows reuse a placeholder `transaction_id` (e.g.
`0002564` used 7 times, `652294` used 3 times — 129 rows total) across
genuinely different transactions, including these same stipend deposits.
`merge_datasets.py` now synthesizes a content-based ID (same SHA-256 formula
`docs/database.md` defines for SMS with no bank reference) for any row with
a missing or colliding transaction_id before deduping, so these don't get
silently dropped at the merge step.

## Knowledge base growth

Tracks whether the "minimal effort for future datasets" goal is actually
happening — rule count should grow faster than dataset count.

| Date | Merchant rules | Keyword rules | Trigger |
|---|---|---|---|
| 2026-07-02 | 0 | 0 | Infrastructure created, no labeling done yet |
| 2026-07-02 | 1 | 0 | Interview: `Compass` → Food / Dine Out (user confirmed: office canteen) |
| 2026-07-02 | 24 | 0 | Interview batch 2: 24 recipient groups confirmed (family/friends → Transfers, bakery/milk/grocery-shop → Groceries, sandwich/cake/sweets shops → Food / Dine Out, Airtel → Subscriptions, salon → Cosmetics, IRCTC/Indian Railway/Rapido → Travel, medical shops (snacks) + UNKNOWN → Miscellaneous placeholder) |
| 2026-07-02 | 25 | 0 | Interview batch 3: 25 more groups (mostly friends → Transfers; food shops incl. Frankie roll stall, Burger King, Mansarovar → Food / Dine Out; grocery shops → Groceries; Mumbai Metro, Roppen → Travel; self-transfer → Transfers) |
| 2026-07-02 | 0 | 0 | **Category set expanded 10 → 12**: added Medical (id 11) and Fees & Debt (id 12) via `backend/.../V7__add_medical_and_fees_categories.sql`. Retroactively recategorized 6 already-labeled rows from their Miscellaneous placeholder: `DILIP KU`, `Mayank M`, `PRADHAN`, `Geeta Cl`, `SAI DIAG` → Medical; `Vartak c` → Fees & Debt. EMI placeholder guidance (was Transfers) now points to Fees & Debt — see `CATEGORY_GUIDELINES.md`. |
| 2026-07-02 | 15 | 0 | User hand-filled 15 rows directly in `review_sheet.xlsx` (bypassing the interview). Notable: `vihaansh`/`alpharan` are literal Recipient_Name values distinct from `Mr Vihaa`/`ALPHA VI` (UPI-alias leakage into the field), so the earlier friend rules never matched them. `Amar fas` (food, not the guessed "Amar Fashion") and `THE DERM`/`THEDERMACO` (shopping, not a clinic) corrected assumptions from the earlier bulk exclusion pass. |

## Category distribution

From the final `ml/data/spendwise_labeled.xlsx` (1,810 rows, 2026-07-02):

| Category | Rows |
|---|---|
| Transfers | 897 |
| Food / Dine Out | 360 |
| Groceries | 183 |
| Travel | 119 |
| Subscriptions | 85 |
| Medical | 62 |
| Miscellaneous | 48 |
| Shopping | 25 |
| Cosmetics | 23 |
| Entertainment | 6 |
| Fees & Debt | 2 |
| Sports & Fitness | 0 |

Transfers dominates (~50%) as expected given P2P transfers are naturally
frequent — the baseline model (`E4-S2-T2`) will likely need class weighting.
**Sports & Fitness and Fees & Debt are severely underrepresented (0 and 2
rows)** — worth knowing before training; the model will have essentially no
signal for Sports & Fitness from this dataset alone.

## Finalized for Epic 4 (2026-07-02)

Labeling stopped here by user decision — the remaining 59 groups / 110 rows
(payment-rail intermediaries like Google Pay/Paytm/Mobikwik/Razorpay where
the real recipient is masked, a handful of unresolved business names, and 4
genuinely-unrecognized small amounts) are **intentionally excluded** from
`ml/data/spendwise_labeled.xlsx`, not silently dropped — `review_sheet.xlsx`
still has them for whenever someone wants to pick this back up. Re-running
`auto_label.py` → fill remaining rows → `apply_review.py` →
`merge_datasets.py` will pick up right where this left off; the knowledge
base doesn't need to be rebuilt.

Known stale references not yet fixed (out of scope for this session):
`docs/testing.md` and `ml/README.md` still cite the old, never-labeled
`data/spendwise2k26.xlsx` filename — Epic 4 should point at
`ml/data/spendwise_labeled.xlsx` instead (see `ml/labeling/README.md` line 3).
