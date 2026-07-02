"""POST /retrain (E4-S2-T4) — triggers a batch retraining cycle over the
baseline labeled dataset plus corrections supplied by the Spring Boot
Categorization module (ml_corrections rows, per E4-S3-T4). Per ADR-003 this
is adaptive *supervised* retraining: the artifact is replaced wholesale by a
fresh fit, not incrementally nudged (no online/reinforcement learning).
"""

from pathlib import Path

import pandas as pd
from fastapi import APIRouter

from api import model_store
from api.categories import category_name_for_id
from api.config import get_settings
from api.schemas import RetrainRequest, RetrainResponse
from training.train import DEFAULT_DATA_PATH, load_labeled_dataset, save_model, train_model

router = APIRouter()


def _corrections_to_frame(request: RetrainRequest) -> pd.DataFrame:
    rows = []
    for correction in request.corrections:
        row = correction.model_dump()
        category_id = row.pop("category_id")
        row["category"] = category_name_for_id(category_id)
        rows.append(row)
    return pd.DataFrame(rows)


@router.post("/retrain", response_model=RetrainResponse)
def retrain(request: RetrainRequest) -> RetrainResponse:
    baseline_df = load_labeled_dataset(DEFAULT_DATA_PATH)
    corrections_df = _corrections_to_frame(request)
    combined_df = pd.concat([baseline_df, corrections_df], ignore_index=True) if not corrections_df.empty else baseline_df

    pipeline, n_samples = train_model(combined_df)

    output_dir = Path(get_settings().model_path)
    save_model(pipeline, output_dir)
    model_store.set_model(pipeline)

    return RetrainResponse(status="success", trained_samples=n_samples)
