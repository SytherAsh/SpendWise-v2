"""Canonical category list — must match docs/requirements.md exactly. Single
source of truth for every labeling script and the review-sheet dropdown."""

CATEGORIES = [
    "Shopping",
    "Entertainment",
    "Sports & Fitness",
    "Groceries",
    "Travel",
    "Miscellaneous",
    "Food / Dine Out",
    "Cosmetics",
    "Subscriptions",
    "Transfers",
    "Medical",
    "Fees & Debt",
    "Bills",
]

# Canonical transaction schema every dataset gets normalized to — matches the
# POST /ingest/transactions request shape in docs/api.md so training features
# never drift from what production inference actually receives.
CANONICAL_FIELDS = [
    "transaction_date",
    "debit",
    "credit",
    "amount",
    "dr_cr_indicator",
    "transaction_id",
    "recipient_name",
    "upi_id",
    "bank",
    "transaction_mode",
    "note",
    "source",
]
