"""FastAPI ML categorization service entry point.

See docs/architecture.md -> FastAPI ML Service.
"""

from fastapi import FastAPI

app = FastAPI(title="SpendWise ML Categorization Service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "healthy"}
