"""Loads and caches the trained model artifact from MODEL_PATH (E4-S2-T3),
and lets /retrain (E4-S2-T4) swap in a freshly trained pipeline so subsequent
/predict calls use it without an app restart.
"""

from pathlib import Path
from threading import Lock

import joblib

from api.config import MODEL_FILENAME, get_settings

_model = None
_lock = Lock()

# Single-row /predict requests never benefit from RandomForestClassifier's
# fit-time n_jobs=-1 -- see HierarchicalCategoryModel.set_inference_n_jobs for why
# this is worth ~5x on real request latency. Applied here (the shared load/set
# entry points), not in training code, so a retrain can still fit in parallel.
_INFERENCE_N_JOBS = 1


def _model_path() -> Path:
    return Path(get_settings().model_path) / MODEL_FILENAME


def _tune_for_inference(model):
    tune = getattr(model, "set_inference_n_jobs", None)
    if tune is not None:
        tune(_INFERENCE_N_JOBS)


def load_model():
    """Loads the artifact from disk into the in-memory cache and returns it."""
    global _model
    with _lock:
        _model = joblib.load(_model_path())
        _tune_for_inference(_model)
        return _model


def get_model():
    """Returns the cached model, lazily loading it from disk on first use."""
    if _model is None:
        return load_model()
    return _model


def set_model(pipeline) -> None:
    """Swaps the in-memory model without touching disk — used by /retrain and by tests."""
    global _model
    with _lock:
        _tune_for_inference(pipeline)
        _model = pipeline
