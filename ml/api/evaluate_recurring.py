"""GET /evaluate-recurring -- same role as GET /evaluate (api/evaluate.py) but
for the recurring-payment classifier."""

from fastapi import APIRouter

from api.schemas import RecurringEvaluationResponse
from evaluation.evaluate_recurring import run_evaluation

router = APIRouter()


@router.get("/evaluate-recurring", response_model=RecurringEvaluationResponse)
def evaluate_recurring() -> RecurringEvaluationResponse:
    report = run_evaluation()
    return RecurringEvaluationResponse(**report)
