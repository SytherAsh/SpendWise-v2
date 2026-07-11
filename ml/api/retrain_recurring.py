"""POST /retrain-recurring -- retrains the recurring-payment classifier on the
bootstrap dataset plus any real corrections supplied by the Spring Boot side.
Mirrors api/retrain.py's shape and ADR-003 semantics (wholesale refit, not
incremental).

There is no recurring_corrections table yet (production Alerts confirm/
dismiss wiring, not built), so `corrections` will be empty in practice until
that lands -- this endpoint already accepts the shape so no contract change
is needed once it does.
"""

from pathlib import Path

import pandas as pd
from fastapi import APIRouter

from api import recurring_model_store
from api.config import get_settings
from api.schemas import RecurringRetrainRequest, RecurringRetrainResponse
from training.model_pipeline import RECURRING_FEATURE_COLUMNS, build_recurring_pipeline
from training.train import load_labeled_dataset
from training.train_recurring import DEFAULT_DATA_PATH, build_training_frame, save_model

router = APIRouter()


def _corrections_to_frame(request: RecurringRetrainRequest) -> pd.DataFrame:
    rows = []
    for correction in request.corrections:
        row = correction.model_dump()
        row["label"] = int(row.pop("was_recurring"))
        rows.append(row)
    return pd.DataFrame(rows, columns=[*RECURRING_FEATURE_COLUMNS, "label"])


@router.post("/retrain-recurring", response_model=RecurringRetrainResponse)
def retrain_recurring(request: RecurringRetrainRequest) -> RecurringRetrainResponse:
    baseline_df = load_labeled_dataset(DEFAULT_DATA_PATH)
    bootstrap_frame = build_training_frame(baseline_df)
    corrections_frame = _corrections_to_frame(request)
    combined_frame = (
        pd.concat([bootstrap_frame, corrections_frame], ignore_index=True) if not corrections_frame.empty else bootstrap_frame
    )

    x = combined_frame[RECURRING_FEATURE_COLUMNS]
    y = combined_frame["label"]

    model = build_recurring_pipeline()
    model.fit(x, y)

    output_dir = Path(get_settings().model_path)
    save_model(model, output_dir)
    recurring_model_store.set_model(model)

    return RecurringRetrainResponse(status="success", trained_candidate_groups=len(combined_frame))
