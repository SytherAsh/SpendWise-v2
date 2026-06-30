# Tests — End-to-End

End-to-end tests for the SpendWise golden path: the complete flow from transaction ingestion to dashboard reflection.

## Golden Path Test

**File**: `e2e/test_golden_path.py`

Tests the critical user journey:

```
POST /api/v1/ingest (mock Android payload)
    → Transaction stored in Supabase
    → FastAPI ML service categorizes it
    → GET /api/v1/transactions returns the transaction with category
    → GET /api/v1/analytics/summary reflects updated totals
```

This test does **not** require a physical Android device. It simulates the Android app by posting directly to the `/ingest` endpoint with a pre-parsed transaction payload.

## Setup

```bash
cd tests/e2e
pip install -r requirements.txt

# Set environment variables:
export TEST_API_URL=https://<backend-host>/api/v1
export TEST_USER_JWT=<valid test user JWT>
export TEST_DEVICE_API_KEY=<registered device key>
```

## Running

```bash
cd tests/e2e
pytest test_golden_path.py -v
```

## When to Run

- Before every PR merge to `main`
- After any change to the ingest, categorization, or analytics modules
- After ML model retraining

## Adding New E2E Tests

When adding a new critical flow, create a new test file in `tests/e2e/` following the naming convention `test_<flow_name>.py`.

Keep E2E tests:
- Focused on the happy path + one key failure mode
- Independent (no shared state between tests)
- Fast (avoid sleeping — use retries with timeout instead)
