# Category Guidelines

The 13 categories are frozen in `docs/requirements.md` — do not add, rename,
or split categories here without updating that doc first. This file exists
to make labeling *consistent* across 475+ recipients and however many future
datasets, by writing down the boundary calls once instead of re-deciding them
per row.

## Category vs. Counterparty — these are not the same thing

**Category** (this file, `category` column in `knowledge_base/merchant_rules.csv`,
`category` column in the final `ml/data/spendwise_labeled.xlsx`) is the single label
the ML model in Epic 4 is trained to predict. It is always exactly one of the 12 values
in `docs/requirements.md`.

**Counterparty type** (friend, family, self-transfer, merchant, employer, subscription
service, etc.) is a *different* question — who or what the recipient is to this
specific user — and it is **not** a category, not an ML target, and not part of the
label set the ML model sees. During labeling, counterparty type was frequently the
*reasoning* used to arrive at a category (e.g. "Prachi is my mother, sending money →
category: Transfers"). That reasoning has since been extracted into
`knowledge_base/counterparty_knowledge.csv` — a standalone artifact, separate from this
category knowledge base, not read by `auto_label.py`/`apply_review.py`/
`merge_datasets.py`/training, and not integrated into the app or database. See
`knowledge_base/counterparty_knowledge.README.md` before using it for anything. Splitting
`Transfers` by counterparty (e.g. `Transfers-Family`, `Transfers-Friend`) was
deliberately rejected as an ML-target change — see ADR-010 in
`docs/decisions.md`. If you want to build the counterparty-metadata enrichment layer
described there, treat it as a wholly separate data asset from this file, not an
extension of it.

| # | Category | Includes | Excludes / boundary |
|---|---|---|---|
| 1 | **Shopping** | General retail & e-commerce: Amazon, Flipkart, Myntra, clothing, electronics, home goods. | Not beauty/personal-care stores (→ Cosmetics). Not groceries (→ Groceries). |
| 2 | **Entertainment** | One-off spend: movie tickets, events, gaming purchases, one-time rentals. | Recurring digital subscriptions (Netflix, Spotify monthly charge) → **Subscriptions**, even though the service is "entertainment." The line is *recurring vs. one-off*, not the merchant. |
| 3 | **Sports & Fitness** | Gyms, fitness classes, sports gear/apparel stores (Decathlon), sports goods. | — |
| 4 | **Groceries** | Supermarkets, kirana stores, produce vendors, grocery-delivery apps (BigBasket, full-cart Blinkit/Zepto/Instamart orders). | Prepared-food delivery from the same quick-commerce apps → **Food / Dine Out** if the note/order type indicates food, otherwise default to Groceries (these apps are groceries-primary). |
| 5 | **Travel** | Flights, trains (IRCTC), cabs/autos (Uber, Ola, Rapido), fuel, hotels, bus (redBus). | — |
| 6 | **Miscellaneous** | True catch-all. Also the **required default for one-off unmatched transactions** per `docs/requirements.md`. ATM cash withdrawals land here (no better fit among the 12). | Do not use as a lazy default when a better category is obvious — it's specifically for genuinely ambiguous or truly one-off unclassifiable spend. |
| 7 | **Food / Dine Out** | Swiggy, Zomato, restaurants, cafes, food delivery. Explicitly separate from Groceries per `docs/requirements.md`. | — |
| 8 | **Cosmetics** | Beauty/personal-care stores (Nykaa, Purplle), salons. | — |
| 9 | **Subscriptions** | Recurring charges: OTT (Netflix, Hotstar), music (Spotify), mobile/DTH recharges (Airtel, Jio), insurance premiums. | One-off purchase from the same category of merchant (e.g. a single movie rental) → **Entertainment**. Tie-break is recurrence, not merchant identity. |
| 10 | **Transfers** | Person-to-person UPI transfers, family transfers, wallet top-ups, self-transfers between own accounts, bank transfers with no goods/services attached. | — |
| 11 | **Medical** | Clinics, diagnostics labs, pharmacies, medical-shop purchases (even when the note says something unrelated, like buying snacks at a pharmacy counter). | — |
| 12 | **Fees & Debt** | Loan/EMI/NBFC repayments, coaching/tuition/school fees, other debt-service or fee payments. | — |
| 13 | **Bills** | Recurring household utility bills: electricity, gas, water, and maintenance/society charges. | Mobile/DTH recharges and OTT/streaming charges stay in **Subscriptions** — Bills is specifically utility/housing charges, not every recurring payment. |

Categories 11–12 were added 2026-07-02, after `Medical` and `Fees & Debt`
purchases kept surfacing during labeling with no home among the original 10
(pharmacy/medical-shop visits and coaching fees were being forced into
Miscellaneous). See `docs/requirements.md` for the canonical list and
`backend/.../db/migration/V7__add_medical_and_fees_categories.sql` for the
schema change. Rows labeled before this addition (e.g. `DILIP KU`,
`Mayank M`, `PRADHAN` medical-shop-with-snacks entries) were retroactively
moved from Miscellaneous to Medical — see `tracking/LABELING_STATUS.md`.

Category 13 (`Bills`) was added 2026-07-12, ML strategy phase, ahead of any
labeled examples in the finalized dataset — same situation `Sports & Fitness`
was already in (see `ml/labeling/tracking/EPIC_4_HANDOFF.md`). Until user
corrections supply Bills examples for a future retraining cycle, the
classifier cannot predict this category; it's reachable only via manual
category selection in the product. See
`backend/.../db/migration/V12__add_bills_category.sql` for the schema change.
Do not retroactively relabel existing Miscellaneous/Subscriptions rows into
Bills without a documented reason — there's no labeled evidence yet that any
row in `ml/data/spendwise_labeled.xlsx` belongs here.

## Open question — not yet resolved, do not invent an answer

**EMI / loan repayments** — resolved 2026-07-02: label as **Fees & Debt**
(category 12 above), no longer a Transfers placeholder. Epic 6 (EMI &
Recurring) may still introduce its own dedicated EMI tracking outside the
category system; if it does, revisit whether Fees & Debt should keep
covering EMI or hand it off entirely.

**Salary / stipend credits** — resolved 2026-07-02: label as **Transfers**.
No Income category exists among the 10 original categories, and this wasn't
folded into Fees & Debt since it's income, not a fee/debt payment.
Transfers' own definition ("bank transfers with no goods/services attached")
covers an employer NEFT credit reasonably well even though it isn't strictly
person-to-person. Revisit if an Income/Salary category is ever added.
Applies to recurring Nomura stipend deposits in `spendwise_4yr_sbi_statement`
(~₹48k–75k NEFT credits, monthly) — these have blank/`UNKNOWN`
`Recipient_Name`, so they can't be matched by a `merchant_rules.csv` pattern
like everything else; they were corrected by a one-time manual patch of the
specific `transaction_id`s in `auto_labeled.csv` rather than a knowledge-base
rule. See `tracking/LABELING_STATUS.md` for which transactions.

## Recurring vs. one-off unmatched transactions

Per `docs/requirements.md`:
- **Recurring** unmatched transaction (same recipient, repeating pattern) →
  product behavior is to prompt the user, not auto-assign. This doesn't
  affect training labels directly, but if you're labeling a dataset and see
  a clearly recurring, clearly unclear merchant — don't force it into
  Miscellaneous just because it's unfamiliar; look for the pattern.
- **One-off** unmatched transaction → defaults to **Miscellaneous**. This is
  the fallback both the product and the labeling knowledge base should agree
  on.

## When you're genuinely unsure while labeling

Leave the Category cell **blank** rather than guessing. `apply_review.py`
skips blank rows (with a warning) instead of writing a bad label into the
knowledge base — a wrong rule is worse than a missing one, since wrong rules
silently mislabel every future dataset that matches the same pattern.
