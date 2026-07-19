"""FastAPI ML categorization service entry point.

See docs/architecture.md -> FastAPI ML Service.
"""

from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from api import model_store, recurring_model_store
from api.evaluate import router as evaluate_router
from api.evaluate_recurring import router as evaluate_recurring_router
from api.normalize_recipients import router as normalize_recipients_router
from api.predict import router as predict_router
from api.predict_recurring import router as predict_recurring_router
from api.retrain import router as retrain_router
from api.retrain_recurring import router as retrain_recurring_router
from api.security import InternalKeyMiddleware
from training.dataset_locator import NoLabeledDatasetFoundError


@asynccontextmanager
async def lifespan(app: FastAPI):
    # docs/operations/deployment.md: "The service loads the model at startup."
    # A missing artifact (e.g. a fresh checkout before training/train.py has
    # run) must not prevent /health from coming up — /predict falls back to
    # lazily loading on first call if this fails. Same contract for the
    # recurring-payment classifier (training/train_recurring.py).
    try:
        model_store.load_model()
    except FileNotFoundError:
        pass
    try:
        recurring_model_store.load_model()
    except FileNotFoundError:
        pass
    yield


app = FastAPI(title="SpendWise ML Categorization Service", lifespan=lifespan)
app.add_middleware(InternalKeyMiddleware)


@app.exception_handler(NoLabeledDatasetFoundError)
def handle_no_labeled_dataset(request: Request, exc: NoLabeledDatasetFoundError) -> JSONResponse:
    # Without this, an empty/missing ml/data/ turns into an unhandled 500 here, which the
    # Spring Boot caller's JWT auth filter (skipped on its own /error dispatch) then turns
    # into a bare, bodiless 403 — same masked-error shape as the recipient_merge_suggestions
    # RLS bug this same session. A clean 503 with a real message is at least diagnosable.
    return JSONResponse(status_code=503, content={"detail": str(exc)})


app.include_router(predict_router)
app.include_router(retrain_router)
app.include_router(evaluate_router)
app.include_router(predict_recurring_router)
app.include_router(retrain_recurring_router)
app.include_router(evaluate_recurring_router)
app.include_router(normalize_recipients_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "healthy"}
