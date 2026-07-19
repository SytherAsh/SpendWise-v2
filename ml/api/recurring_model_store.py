"""Loads and caches the trained recurring-payment classifier artifact, mirroring
api/model_store.py's pattern for the categorization model -- kept as a
separate, parallel cache rather than generalizing model_store.py into a
multi-model registry, so the well-tested categorization path stays untouched.
"""

from pathlib import Path
from threading import Lock

import joblib

from api.config import RECURRING_MODEL_FILENAME, get_settings

_model = None
_lock = Lock()

# Same reasoning as api/model_store.py's _INFERENCE_N_JOBS: a bare
# RandomForestClassifier fit with n_jobs=-1 pays joblib's process-pool overhead
# on every single-candidate /predict-recurring call, which swamps the actual
# tree-traversal cost. Applied at load/set time, not at fit time, so retraining
# still parallelizes.
_INFERENCE_N_JOBS = 1


def _model_path() -> Path:
    return Path(get_settings().model_path) / RECURRING_MODEL_FILENAME


def _tune_for_inference(model) -> None:
    if hasattr(model, "n_jobs"):
        model.n_jobs = _INFERENCE_N_JOBS


def load_model():
    global _model
    with _lock:
        _model = joblib.load(_model_path())
        _tune_for_inference(_model)
        return _model


def get_model():
    if _model is None:
        return load_model()
    return _model


def set_model(model) -> None:
    global _model
    with _lock:
        _tune_for_inference(model)
        _model = model
