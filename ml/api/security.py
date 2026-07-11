"""Internal-only access guard for the ML service (E4-S1-T1).

See docs/spec/architecture.md -> FastAPI ML Service "Internal access only" note
and docs/spec/security.md's API Security Checklist last item: every prediction/
retrain/evaluate route must reject any caller that doesn't present the shared
secret in X-Internal-Key. /health stays exempt (unauthenticated uptime
monitoring, per docs/operations/deployment.md).

PROTECTED_PATHS is a hardcoded allowlist rather than "everything except
/health" deliberately -- a new route silently landing unprotected (as
happened here when /predict-recurring, /retrain-recurring, and
/evaluate-recurring were first added and forgotten from this set) is a worse
failure mode than a new route the middleware doesn't yet know to protect
returning an unexpected 401 until this set is updated.
"""

from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.types import ASGIApp

from api.config import get_settings

PROTECTED_PATHS = {
    "/predict",
    "/retrain",
    "/evaluate",
    "/predict-recurring",
    "/retrain-recurring",
    "/evaluate-recurring",
}


class InternalKeyMiddleware(BaseHTTPMiddleware):
    def __init__(self, app: ASGIApp) -> None:
        super().__init__(app)

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint):
        if request.url.path in PROTECTED_PATHS:
            expected = get_settings().ml_internal_key
            provided = request.headers.get("X-Internal-Key")
            if provided is None or provided != expected:
                return JSONResponse(status_code=401, content={"detail": "Missing or invalid X-Internal-Key"})
        return await call_next(request)
