"""Pydantic request/response models shared across the /predict, /retrain, and
/evaluate routes."""

from pydantic import BaseModel


class PredictionRequest(BaseModel):
    """docs/deployment.md "Backend Service Communication" example request."""

    recipient_name: str | None = None
    upi_id: str | None = None
    bank: str | None = None
    transaction_mode: str | None = None
    amount: float
    note: str | None = None


class PredictionResponse(BaseModel):
    """docs/deployment.md "Backend Service Communication" example response."""

    category_id: int
    category_name: str
    confidence: float


class CorrectionExample(BaseModel):
    """One row of ml_corrections data supplied by the Spring Boot Categorization
    module for a retrain cycle (E4-S3-T4) — the corrected transaction's features
    plus the user-assigned category_id."""

    recipient_name: str | None = None
    upi_id: str | None = None
    bank: str | None = None
    transaction_mode: str | None = None
    amount: float
    note: str | None = None
    category_id: int


class RetrainRequest(BaseModel):
    corrections: list[CorrectionExample] = []


class RetrainResponse(BaseModel):
    status: str
    trained_samples: int


class CategoryMetrics(BaseModel):
    category_id: int
    category_name: str
    precision: float
    recall: float
    f1_score: float
    support: int


class ConfidenceDistribution(BaseModel):
    mean: float
    median: float
    min: float
    max: float


class EvaluationResponse(BaseModel):
    generated_at: str
    n_samples: int
    accuracy: float
    per_category: list[CategoryMetrics]
    confusion_matrix: list[list[int]]
    confusion_matrix_labels: list[int]
    confidence_distribution: ConfidenceDistribution
    report_path: str


class RecurringPredictionRequest(BaseModel):
    """One candidate group's features (training/recurring_features.py's
    compute_features() output) -- the caller (Alerts, via the Categorization
    gateway) computes these from its own loose candidate generation, mirroring
    this same feature shape."""

    occurrence_count: int
    interval_mean_days: float
    interval_cv: float
    amount_mean: float
    amount_cv: float
    span_days: float
    days_since_last_occurrence: float


class RecurringPredictionResponse(BaseModel):
    is_recurring: bool
    confidence: float
    cadence: str  # weekly/biweekly/monthly/quarterly/annual/irregular -- only meaningful when is_recurring


class RecurringCorrection(RecurringPredictionRequest):
    """Same shape as recurring_corrections (once that table exists,
    production phase) -- a candidate group's features plus the user's
    confirm/dismiss outcome."""

    was_recurring: bool


class RecurringRetrainRequest(BaseModel):
    corrections: list[RecurringCorrection] = []


class RecurringRetrainResponse(BaseModel):
    status: str
    trained_candidate_groups: int


class RecurringConfidenceDistribution(BaseModel):
    mean: float
    median: float
    min: float
    max: float


class NormalizeEntry(BaseModel):
    """One (transaction-group) entry to canonicalize -- `key` is caller-defined
    (the Spring Boot side sends a synthetic key per unique (recipient_name,
    upi_id) pair it owns, not necessarily a transaction id) and is echoed back
    unchanged in the response so the caller can map results without needing
    the canonical values themselves to be unique identifiers."""

    key: str
    recipient_name: str | None = None
    upi_id: str | None = None


class NormalizeRecipientsRequest(BaseModel):
    """All entries must belong to one user -- the clustering algorithm compares
    every entry against every other, so mixing users' recipient names in one
    call would leak one user's payee spellings into another's canonical
    assignment. The caller (Categorization module, per the FastAPI gateway
    invariant) is responsible for calling this once per user."""

    entries: list[NormalizeEntry] = []


class AmbiguousCandidate(BaseModel):
    """One candidate the clustering algorithm considered but did not confidently
    auto-merge into `anchor_key`'s cluster -- surfaced for the Merge Payees
    human-review feature (2026-07-19) rather than silently left unmerged."""

    key: str
    name: str
    score: int
    # "fuzzy_near_miss" | "prefix_ambiguous" | "upi_handle_variant" | "short_prefix"
    # (last two: Phase A enriched detectors, ADR-019)
    reason: str


class AmbiguousGroup(BaseModel):
    anchor_key: str
    anchor_name: str
    candidates: list[AmbiguousCandidate] = []


class NormalizeRecipientsResponse(BaseModel):
    canonical_names: dict[str, str]
    ambiguous_groups: list[AmbiguousGroup] = []


class RecurringEvaluationResponse(BaseModel):
    generated_at: str
    n_candidate_groups: int
    n_test_samples: int
    accuracy: float
    precision_recurring: float
    recall_recurring: float
    f1_recurring: float
    support_recurring: int
    support_not_recurring: int
    confusion_matrix: list[list[int]]
    confusion_matrix_labels: list[int]
    confidence_distribution: RecurringConfidenceDistribution
    report_path: str
