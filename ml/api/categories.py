"""Canonical category schema for the ML service — must match docs/requirements.md
and docs/database.md's `categories` seed data exactly (13 categories; ids 1-10
seeded in V2, 11-12 added in V7, 13 added in V12). This is the full label
*schema*, not a claim that every category has training examples — see
ml/labeling/tracking/EPIC_4_HANDOFF.md (Sports & Fitness has zero examples in
the current labeled dataset) and ml/labeling/CATEGORY_GUIDELINES.md (Bills is
the same situation as of V12 — added to the schema ahead of having any labeled
examples, since neither the classifier nor the retry-based auto-assignment can
predict it until user corrections accumulate).

Kept independent of ml/labeling/scripts/categories.py: that module is a one-off
data-prep tool for building the training set, not a runtime dependency of the
deployed service. The two lists must stay in sync with docs/requirements.md.
"""

CATEGORIES: list[str] = [
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

NAME_TO_ID: dict[str, int] = {name: idx + 1 for idx, name in enumerate(CATEGORIES)}
ID_TO_NAME: dict[int, str] = {idx + 1: name for idx, name in enumerate(CATEGORIES)}


def category_id_for_name(name: str) -> int:
    try:
        return NAME_TO_ID[name]
    except KeyError as exc:
        raise ValueError(f"Unknown category name: {name!r}") from exc


def category_name_for_id(category_id: int) -> str:
    try:
        return ID_TO_NAME[category_id]
    except KeyError as exc:
        raise ValueError(f"Unknown category id: {category_id!r}") from exc
