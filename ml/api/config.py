"""Application settings for the FastAPI ML service.

Reads the FastAPI environment variables listed in docs/deployment.md
(Environment Variables -> FastAPI ML Service).
"""

from pydantic_settings import BaseSettings, SettingsConfigDict

# Filename of the committed model artifact within MODEL_PATH. Shared by
# training/train.py (writes it), api/model_store.py (loads/reloads it), and
# evaluation/evaluate.py (loads it for /evaluate) so all three agree on where
# the artifact lives without a circular import between api/ and training/.
MODEL_FILENAME = "category_classifier.joblib"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    supabase_url: str = ""
    supabase_key: str = ""
    sentry_dsn: str = ""
    model_path: str = "./models/"
    ml_internal_key: str = ""


def get_settings() -> Settings:
    return Settings()
