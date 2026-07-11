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
