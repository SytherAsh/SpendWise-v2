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


def _model_path() -> Path:
    return Path(get_settings().model_path) / RECURRING_MODEL_FILENAME


def load_model():
    global _model
    with _lock:
        _model = joblib.load(_model_path())
        return _model


def get_model():
    if _model is None:
        return load_model()
    return _model


def set_model(model) -> None:
    global _model
    with _lock:
        _model = model
