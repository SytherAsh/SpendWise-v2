# Testing Strategy

## Philosophy

Prioritize correctness of business logic and data processing over extensive UI automation. The SMS parser, transaction ingestion pipeline, and ML categorization are the highest-risk areas — test these thoroughly first. Expand the test suite as the project matures.

---

## 1. Spring Boot Backend (JUnit 5 + Spring Boot Test)

Every module has its own unit and integration test suite.

### Unit Tests

Test business logic in isolation (mock dependencies):

- **Auth**: token generation, OTP validation logic, refresh token rotation; replay attack detection (presenting an already-rotated token revokes all refresh tokens for that user); JWT secret routing (`ADMIN_JWT_SECRET`-signed tokens rejected by user auth filter; `JWT_SECRET`-signed tokens rejected by admin auth filter)
- **User**: preference updates, onboarding state transitions
- **Ingest**: deduplication logic (primary key check, composite key check), batch validation; dual-auth validation (valid JWT + valid device key → 200; missing JWT → 401; missing device key → 401; inactive device key → 401; device key not matching `user_id` → 401)
- **Transaction Management**: filtering, pagination cursor logic, sorting; category correction (verify `PUT /transactions/:id/category` writes to `transaction_categories` and inserts to `ml_corrections` atomically); EMI CRUD (create, update, deactivate via `PATCH`, `source_transaction_id` linkage on auto-detected entries)
- **Categorization**: calling ML service, handling low-confidence responses, reading `ml_corrections` as training data for retraining, evaluating model accuracy
- **Budget**: progress calculation (% spent), mid-month 50% total budget threshold (high priority), 80% per-category approaching-limit threshold (medium priority — in-app only), category budget overspend threshold (high priority)
- **Alerts**: evaluation engine logic (which alerts should fire given current spend state); recurring-payment detection (3+ transactions from the same `upi_id`/`recipient_name` within a rolling 60-day window, amounts within ±10% of each other, excluding transactions already tracked in `emis`)
- **Recommendations**: priority assignment based on spending data, dismissal logic
- **Chatbot**: context injection (transaction data summary), session management
- **Analytics**: aggregation correctness (totals, category breakdowns, comparisons)
- **Admin**: cross-user aggregate queries, log retrieval

### Integration Tests

Test full request-response cycle against a real PostgreSQL instance provisioned via Testcontainers (requires Docker in the CI environment):

> **Why Testcontainers:** The schema uses PostgreSQL-specific features (ENUM types, JSONB, `gen_random_uuid()`, `set_config()` for RLS session variables) that H2 does not support. Testcontainers spins up a real PostgreSQL container per test run, ensuring tests exercise the same behaviour as production.

- Each API endpoint: request → service → DB → response
- Auth middleware: valid token passes, invalid token returns 401, admin token required for admin routes
- Admin route rejects a `JWT_SECRET`-signed token even with an admin role claim — the admin filter must reject any token not signed with `ADMIN_JWT_SECRET`
- `sms_raw_text` field: verify it is never included in user-facing API responses
- Deduplication: POST same transaction twice → second is rejected with no DB insert
- Pagination: verify cursor-based pagination returns consistent results

### Running Spring Boot Tests

```bash
cd backend
./gradlew test
./gradlew integrationTest
```

---

## 2. FastAPI ML Service (pytest)

### ML Unit Tests

- **Preprocessing pipeline**: raw transaction dict → feature vector (test each field extraction)
- **Feature extraction**: verify `recipient_name`, `upi_id`, `bank`, `transaction_mode`, `amount`, `note` are extracted correctly
- **Prediction endpoint**: mock model, verify response schema `{category_id, category_name, confidence}`
- **Retrain endpoint**: verify it loads `ml_corrections` and triggers training without error
- **Edge cases**: null fields in input (sparse `note`, missing `bank`), empty transaction batch

### Model Evaluation Script

Runs against `ml/data/spendwise_labeled.xlsx` (1,810 labeled transactions from a 4-year SBI bank statement plus manual/interview labeling — see `ml/labeling/`). Must be re-run after every retraining cycle.

```bash
cd ml
python evaluation/evaluate.py --data data/spendwise_labeled.xlsx
```

Output metrics:

- Overall accuracy
- Per-category: Precision, Recall, F1-score
- Confusion matrix (12×12 categories)
- Confidence score distribution

The script saves a report to `ml/evaluation/reports/` with a timestamp.

### Running ML Tests

```bash
cd ml
pytest tests/ -v
python evaluation/evaluate.py --data data/spendwise_labeled.xlsx
```

---

## 3. Android Application (Kotlin Unit Tests)

UI tests (Espresso) are **deferred** to a future phase. Only unit tests are required for MVP.

### SMS Parser Tests (Mandatory)

Located in `android/app/src/test/kotlin/com/spendwise/parser/`

Test cases:

**SBI SMS formats:**

```text
"Your A/c XXXX2345 is debited for Rs.500 on 28-Jun-2026 UPI Ref no. 123456789012"
"INR 1,500.00 credited to your a/c XXXX2345 on 27-Jun-2026"
```

**Paytm SMS formats:**

```text
"Rs.200 paid to Swiggy using Paytm UPI. Ref no: PAYTM123456"
```

**GPay SMS formats:**

```text
"You have sent Rs.350.00 to restaurant@okhdfc using Google Pay UPI"
```

**Deduplication logic:**

- Same bank-provided `transaction_id` → returns null (duplicate)
- Same synthesized `transaction_id` (`hex(SHA-256(user_id || upi_id_or_recipient_name || amount || date_trunc('minute', transaction_date)))` for SMS without an explicit bank reference) → returns null (duplicate)
- Different transaction → returns parsed object

**Invalid / Unknown SMS formats:**

- Random OTP SMS → filter returns false (not financial)
- Promotional SMS → filter returns false
- Unknown sender financial SMS → keyword detector returns true, fields extracted best-effort with nulls

**Field extraction assertions:**

For every valid SMS, assert all of: `transaction_date`, `amount`, `debit`, `credit`, `dr_cr_indicator`, and `transaction_id` are non-null. Assert DR/CR consistency: `dr_cr_indicator = 'DR'` implies `amount < 0`, `debit > 0`, `credit = 0`; `dr_cr_indicator = 'CR'` implies `amount > 0`, `credit > 0`, `debit = 0`. Assert `recipient_name`, `upi_id`, `bank` are present or null (not throwing exceptions).

### Running Android Tests

```bash
cd android
./gradlew test
```

---

## 4. Next.js Web Dashboard (Vitest + React Testing Library)

Component and unit tests only — end-to-end browser automation (Playwright/Cypress) is
**deferred** to a future phase, consistent with the Philosophy above (business-logic
correctness over UI automation) and the Android section's Espresso deferral.

Tests live beside the code they cover as `*.test.ts` / `*.test.tsx` under `frontend/src/`.
The stack is Vitest + `@testing-library/react` + `@testing-library/jest-dom` on a jsdom
environment (`frontend/vitest.config.mts`, `frontend/vitest.setup.ts`).

### What is tested

- **API client (`src/lib/apiClient.ts`)**: the refresh-on-401 interceptor — a 401 triggers
  exactly one `/auth/token/refresh` + retry; a failed refresh clears tokens and surfaces a
  401 `ApiError`; public (`auth: false`) requests never refresh; concurrent 401s coalesce
  into a single refresh (so replay-detection is not tripped).
- **Login form**: success stores the SpendWise access + refresh token and redirects;
  failure renders an inline error (mocked auth API).
- **Dashboard sections**: each section renders from mocked API responses; charts render for
  line (trend) and bar/progress (budget) shapes.
- **Transactions page**: pagination and filter state; the category-correction flow reflects
  immediately without a full reload (mocked API).
- **Budget / EMI / Export / Settings / Chatbot pages**: each page's Required Test from
  `implementation/epics/epic-10-web-dashboard.md` (edit/suggestion-accept, deactivate,
  range-picker validation, preferences save, send-message + resume-history).
- **Stale-data fallback (E10-S3)**: a failed fetch after a successful one still renders the
  last-good data plus a visible stale indicator.

### Running Web Dashboard Tests

```bash
cd frontend
npm test          # vitest run (CI mode, one-shot)
npm run test:watch # vitest watch mode (local development)
```

The frontend CI job (`.github/workflows`, `frontend` — build/lint from Epic 0) should run
`npm test` alongside `npm run build` and `npm run lint`.

---

## 5. End-to-End (Golden Path)

### What it tests

The complete critical user journey:

```text
SMS received on device
    → Parsed by Android parser
    → Posted to /api/v1/ingest
    → Categorized by FastAPI ML service
    → Stored in Supabase
    → Reflected in /api/v1/analytics/summary
```

### How it works

The E2E test uses a test user account and:

1. Sends a mock parsed transaction payload directly to `/api/v1/ingest` (simulates Android app)
2. Asserts the transaction appears in `/api/v1/transactions`
3. Asserts the transaction has a category assigned (from ML service)
4. Asserts the analytics summary reflects the new transaction

This test does **not** require a physical Android device — it tests from the ingest endpoint onward.

### Running E2E Tests

```bash
cd tests/e2e
# Configure test environment variables first:
#   TEST_API_URL        — base URL of the test backend instance
#   TEST_USER_JWT       — access token for the test user account
#   TEST_DEVICE_API_KEY — pre-registered via POST /api/v1/users/me/onboarding
#                         using the test user on the test Supabase project
pytest test_golden_path.py -v
```

### When to run

- Before every push to `main`
- After any change to the ingest, categorization, or analytics modules
- After ML model retraining

---

## Test Coverage Goals (MVP)

| Area | Target |
| --- | --- |
| SMS parser (Android) | All 3 senders (SBI, Paytm, GPay) + deduplication + invalid formats |
| Spring Boot business logic | All 11 modules have unit tests |
| API endpoints | All endpoints have at least one integration test |
| ML pipeline | Preprocessing + prediction + evaluation script |
| E2E golden path | 1 automated test covering the full ingest → categorize → store → analytics flow |
| Security invariants | Dual-auth on `/ingest`; admin JWT secret isolation; refresh token rotation + replay detection |
| Android UI (Espresso) | Deferred — post-MVP |
