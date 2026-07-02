"""GET /evaluate (E4-S2-T5) — runs accuracy evaluation against the labeled
dataset and returns the same metrics evaluation/evaluate.py's CLI writes to a
timestamped report file under ml/evaluation/reports/.
"""

from fastapi import APIRouter

from api.schemas import EvaluationResponse
from evaluation.evaluate import run_evaluation

router = APIRouter()


@router.get("/evaluate", response_model=EvaluationResponse)
def evaluate() -> EvaluationResponse:
    report = run_evaluation()
    return EvaluationResponse(**report)
