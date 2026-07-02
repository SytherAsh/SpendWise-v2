"""FastAPI ML categorization service entry point.

See docs/architecture.md -> FastAPI ML Service.
"""

from contextlib import asynccontextmanager

from fastapi import FastAPI

from api import model_store
from api.evaluate import router as evaluate_router
from api.predict import router as predict_router
from api.retrain import router as retrain_router
from api.security import InternalKeyMiddleware


@asynccontextmanager
async def lifespan(app: FastAPI):
    # docs/deployment.md: "The service loads the model at startup." A missing
    # artifact (e.g. a fresh checkout before training/train.py has run) must
    # not prevent /health from coming up — /predict falls back to lazily
    # loading on first call if this fails.
    try:
        model_store.load_model()
    except FileNotFoundError:
        pass
    yield


app = FastAPI(title="SpendWise ML Categorization Service", lifespan=lifespan)
app.add_middleware(InternalKeyMiddleware)
app.include_router(predict_router)
app.include_router(retrain_router)
app.include_router(evaluate_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "healthy"}
