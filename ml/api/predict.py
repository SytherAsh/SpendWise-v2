"""POST /predict (E4-S2-T3) — serves category predictions for the Categorization
module. Response schema per docs/operations/deployment.md "Backend Service
Communication".

Uses predict_with_confidence() (training/model_pipeline.py) rather than
predict()/predict_proba() separately, so a Transfer prediction reports Stage
1's confidence and a Spend prediction reports Stage 2's alone -- see
HierarchicalCategoryModel's docstring for why those aren't combined.
"""

from fastapi import APIRouter

from api import model_store
from api.categories import category_name_for_id
from api.schemas import PredictionRequest, PredictionResponse
from training.preprocessing import build_feature_frame

router = APIRouter()


@router.post("/predict", response_model=PredictionResponse)
def predict(request: PredictionRequest) -> PredictionResponse:
    model = model_store.get_model()
    features = build_feature_frame([request.model_dump()])

    category_ids, confidences = model.predict_with_confidence(features)
    category_id = int(category_ids[0])
    confidence = float(confidences[0])

    return PredictionResponse(
        category_id=category_id,
        category_name=category_name_for_id(category_id),
        confidence=confidence,
    )
