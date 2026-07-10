# Demo Feature — Complete Implementation Guide

## Status: DEPLOYED & VERIFIED (local, backend + frontend)

All components for the demo account feature are built and verified end-to-end against a live local
backend — seeding logs, DB row counts, and live API/browser calls all confirmed. Frontend
integration (landing page button, demo badge, demo date range) has landed alongside the backend
work; see "Verification" below for what was checked.

## What's Built

### 1. Demo Transaction CSV

**File:** `data/demo-transactions.csv` (also copied to `backend/src/main/resources/data/demo-transactions.csv`)
**Size:** 522 transactions
**Scope:** 1 year of data, July 2025 – June 2026

**Patterns Included:**

- Monthly salary, car EMI, Spotify, light bill, gas bill, Netflix
- Rapido for travel (consistent usage)
- Daily food/dining, varied shopping, groceries
- Family transfers with 4 contacts, received money
- Miscellaneous & a handful of intentionally miscategorized items
- Diverse merchant names, realistic Indian names

**Format:** `transaction_date, debit, credit, amount, dr_cr_indicator, transaction_id, recipient_name, upi_id, bank, transaction_mode, note, source, category` — the `category` column is demo-only curated ground truth (see §3 and "Known Issue: Live ML Model Bias" below).

### 2. CSV Parser (Reusable)

**File:** [`backend/src/main/java/com/spendwise/transaction/util/CsvTransactionParser.java`](../../backend/src/main/java/com/spendwise/transaction/util/CsvTransactionParser.java)

- `parse(InputStream)` — the reusable parser: converts CSV rows into `IngestTransactionItem`, ignoring the `category` column entirely. This is the method future user bank-statement uploads will call — real uploads never carry a pre-known category.
- `parseCategoryOverrides(InputStream)` — demo-only: extracts a `transaction_id -> category_id` map from the CSV's `category` column, consumed solely by `DemoDataSeeder` (see §4). Tolerant of a missing `category` column (returns an empty map) and of non-numeric values (logs a warning, skips the row) so it never blocks the reusable `parse()` path.
- No Lombok — this project doesn't use it anywhere else. Rewritten with an explicit `Logger` (`LoggerFactory.getLogger`), matching the rest of the codebase.

### 3. Demo Data Seeder

**File:** [`backend/src/main/java/com/spendwise/ingest/DemoDataSeeder.java`](../../backend/src/main/java/com/spendwise/ingest/DemoDataSeeder.java)

Runs on `ApplicationReadyEvent` if `demo.enabled=true`. **Reuses the real service layer throughout — no raw SQL inserts anywhere in the seeding path:**

| Step | Service called |
| --- | --- |
| Create/find demo user | `UserAccountService.findOrCreateByPhone`, `UserProfileService.updateEmail` |
| Persist + categorize transactions | `IngestService.ingestBatch` — same path a real Android device batch takes, including the real `CategorizationService → FastAPI /predict` call |
| Resolve seeded transactions' DB UUIDs | `TransactionService.list` (paged, cursor-based) — maps each CSV `transaction_id` to its generated DB `UUID` |
| Overlay curated categories | `TransactionService.correctCategory` per transaction — the same correction path a real user takes to fix a wrong category (see "Known Issue" below for *why*) |
| Budgets | `BudgetService.upsert` × 4 (see §5 for which "month" these land in) |
| Contacts | `ContactService.create` × 4 |
| EMIs | `EmiService.createManual` × 5 (see §6) |
| Alerts | `AlertsService.recordIfNotAlreadyTriggeredThisMonth` × 3, `AlertsService.recordRecurringPaymentIfNotAlreadyTriggeredThisMonth` × 1 (see §6) |
| Recommendations | `RecommendationsService.recordIfNoActiveRecommendationExists` × 4 (see §6) |

Idempotent: checks `UserAccountService.findByPhone(demoPhone)` first and skips the entire seeding pass if the demo user already exists. Even on the skip path, the demo user's ID is re-registered in `DemoUserRegistry` (see §5) on every startup — that registry is an in-memory bean with no persistence, so it must be repopulated every process start regardless of whether seeding itself ran. The demo user's ID is **not** a hardcoded constant — it's whatever `findOrCreateByPhone` generates, looked up dynamically by phone in the seeder, the login controller, and everywhere else that needs it. Seeding failures are caught and logged, never allowed to block app startup.

**Configuration** (`application.yml`, all overridable via env var):
```yaml
demo:
  enabled: ${DEMO_ENABLED:true}
  phone: ${DEMO_PHONE:+919876543210}
  email: ${DEMO_EMAIL:demo@spendwise.local}
  frozen-month: ${DEMO_FROZEN_MONTH:2026-06}
```

### 4. Demo Login Endpoint

**File:** [`backend/src/main/java/com/spendwise/auth/DemoAuthController.java`](../../backend/src/main/java/com/spendwise/auth/DemoAuthController.java)

**Endpoints:** `POST /api/v1/auth/demo/login`, `POST /api/v1/auth/demo/info` — both public (`permitAll` in `SecurityConfig`'s `defaultFilterChain`, alongside the other unauthenticated `/auth/*` routes).

Mirrors `DevAuthController`'s pattern exactly: `UserAccountService.findOrCreateByPhone(demoPhone)` (idempotent — creates on demand if seeding hasn't run yet, e.g. `demo.enabled` flipped on after startup) → `UserJwtService.issueAccessToken` → `RefreshTokenService.issue`. Unlike `DevAuthController`, it is **not** `@Profile`-gated — it's a public marketing feature meant to work in every environment where `demo.enabled=true`, not just local dev.

**Response:**
```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<jwt>",
  "expiresIn": 604800,
  "user": { "id": "<uuid>", "phone": "+919876543210", "email": "demo@spendwise.local" }
}
```

### 5. Frozen-Month Budgets: Keeping a Static CSV's Budgets Non-Blank

**Problem:** the demo CSV is static and is never re-uploaded. Every other part of the seeded data
(transactions, EMIs, alerts, recommendations) is a fixed snapshot that doesn't need "now" to mean
anything in particular — but `BudgetService.listForCurrentMonth` / `progressForCurrentMonth`
compute spend for `YearMonth.now()`. Once real wall-clock time drifts past the CSV's last covered
month, the demo's budget cards would silently show ₹0 spent against every limit, which defeats the
point of a demo meant to showcase budget tracking.

**Fix — two small, narrow, additive pieces, both real-user-safe:**

- **`common/demo/DemoUserRegistry.java`** (new file) — a `@Component` holder bean with a single
  `volatile UUID demoUserId` field and `register(UUID)` / `isDemoUser(UUID)` methods. No business
  logic, no dependency on any other module's domain types. Registered by `DemoDataSeeder` on every
  application startup (see §3). This is a deliberate, narrow exception to the "cross-module calls
  go through injected service interfaces only" rule — see [`docs/spec/architecture.md`](../spec/architecture.md#module-communication-rules)
  for why it's structured this way and why it doesn't count as a module-boundary violation.
- **`BudgetServiceImpl.resolveMonth(UUID userId)`** — a private helper, used everywhere the old code
  called `YearMonth.now()` directly (`upsert`, `listForCurrentMonth`, `progressForCurrentMonth`).
  Returns the configured `demo.frozen-month` (`YearMonth`, currently `2026-06`) **only** when
  `demoUserRegistry.isDemoUser(userId)` is true; returns `YearMonth.now()` for every other user,
  unconditionally.

**Verified real-user safety:** confirmed with a dev-login test user (a different, non-demo seeded
account) that budget calculations still resolve to the real current month — `resolveMonth` returns
`YearMonth.now()` for any `userId` that isn't the registered demo user, with no other code path
affected.

**Keep in sync:** `demo.frozen-month` (backend `application.yml`) must match the CSV's actual last
month, and must stay in sync with the frontend's hardcoded `DEMO_RANGE` constant
(`frontend/src/lib/date-range.tsx` — see §7). If the CSV is ever regenerated with a different date
range, update both.

### 6. EMIs, Alerts & Recommendations Seeding — Demo-Only, Parallel to the Real Systems

**Investigated first, before writing any seeding code:** the real recurring-payment detector and
the alert/recommendation evaluators (`AlertEvaluatorJob`, `RecommendationGeneratorJob`) are both
`@Scheduled` jobs keyed to the real wall clock (`YearMonth.now()`, `Instant.now().minus(60 days)`,
etc.) — structurally incapable of ever firing against this demo's static, never-refreshed CSV data,
no matter how long the app stays up. Unlike transactions/budgets/contacts, EMIs/alerts/recommendations
can't be produced by simply running the real ingest pipeline and waiting for a background job to
pick them up.

**This is 100% hardcoded, demo-only seeding — not a change to the real algorithms.** The real,
generic recurring-detection and alert/recommendation systems predate this session, are untouched by
it, and continue to run automatically for every real user based on their genuine transaction data.
The seeding below is a separate, parallel, demo-only code path that exists solely so the demo
dashboard's Alerts / Upcoming EMIs / Recommendations cards are never blank — it goes through each
feature's own real service methods (never a raw table insert), using hand-picked values that match
the CSV's actual recurring merchants.

**EMIs (5, via `EmiService.createManual`)** — `dueDay` is set on every one, since the dashboard's
"Upcoming EMIs" card filters out EMIs with a null `dueDay`:

| Label | Amount | Due day |
| --- | --- | --- |
| Car Loan EMI | ₹5,000 | 28th |
| Spotify Premium | ₹69 | 27th |
| Netflix Subscription | ₹199 | 1st |
| Electricity Bill | ₹1,650 | 28th |
| Gas Bill | ₹1,050 | 12th |

**Alerts (4, spanning every `AlertType` the dashboard renders)**, via
`AlertsService.recordIfNotAlreadyTriggeredThisMonth` (three threshold alerts) and
`AlertsService.recordRecurringPaymentIfNotAlreadyTriggeredThisMonth` (one recurring-payment alert):

| Type | Category | Priority | Read state |
| --- | --- | --- | --- |
| `CATEGORY_APPROACHING_LIMIT` | Shopping | Medium | Unread |
| `CATEGORY_OVERSPEND` | Food / Dine Out | High | Unread |
| `MID_MONTH_BUDGET` | (all categories) | High | **Read** — deliberately, to show both the read and unread bell-badge states in one demo session |
| `RECURRING_PAYMENT` | — | Medium | Unread — wired to the actual most-recent seeded Spotify transaction's UUID, so clicking "Confirm" in the UI creates a real EMI from a real transaction, exactly as it would for a genuine user |

**Recommendations (4)**, via `RecommendationsService.recordIfNoActiveRecommendationExists`: three
category-specific (Shopping, Travel, Food / Dine Out) using the demo's actual seeded spend figures
for each category, plus one global recommendation (`categoryId: null`) about the salary credit being
a consistent recurring pattern, suggesting a savings auto-transfer.

## Architecture

```
Landing Page → [Try Demo] → POST /api/v1/auth/demo/login → DemoAuthController
  → UserAccountService.findOrCreateByPhone → UserJwtService + RefreshTokenService
  → tokens returned → frontend stores tokens → redirect to /dashboard
  → DateRangeProvider detects the demo phone number, applies the hardcoded DEMO_RANGE once
  → all subsequent API calls use the token like any real session
```

## Data Flow: Demo Seeding on Startup

```
ApplicationReadyEvent
  → DemoDataSeeder.seedDemoDataOnStartup()
  → UserAccountService.findByPhone(demoPhone) — if present, register in DemoUserRegistry and skip (idempotent)
  → UserAccountService.findOrCreateByPhone + UserProfileService.updateEmail
  → DemoUserRegistry.register(demoUser.id())
  → CsvTransactionParser.parse(csv) → IngestService.ingestBatch(userId, items)
      (persists each transaction + triggers real ML categorization, same as a device sync)
  → TransactionService.list (paged) to resolve client transaction_id → DB UUID
  → CsvTransactionParser.parseCategoryOverrides(csv) → TransactionService.correctCategory per row
  → BudgetService.upsert × 4 (lands in BudgetServiceImpl.resolveMonth's demo.frozen-month, not YearMonth.now())
  → ContactService.create × 4
  → EmiService.createManual × 5
  → AlertsService.recordIfNotAlreadyTriggeredThisMonth × 3, recordRecurringPaymentIfNotAlreadyTriggeredThisMonth × 1
  → RecommendationsService.recordIfNoActiveRecommendationExists × 4
```

## Data Flow: Future User CSV Upload

Unchanged from the original design — `CsvTransactionParser.parse()` is the reusable half of this
feature. A future upload endpoint would call `parse()` then `IngestService.ingestBatch()` directly;
it would **not** use `parseCategoryOverrides()`, `DemoUserRegistry`, or any of the EMI/alert/
recommendation seeding above — all of that is demo-only.

## 7. Frontend Integration

**File:** [`frontend/src/lib/authApi.ts`](../../frontend/src/lib/authApi.ts)

- `demoLogin()` — mirrors the existing `devLogin()` shape: `POST /auth/demo/login` (no body, `auth: false`), stores the returned tokens via the same `setTokens` helper every other login path uses.
- `DEMO_PHONE = "+919876543210"` — exported constant, matches the backend's `demo.phone` default. Used client-side to detect a demo session without adding a new endpoint.

**File:** [`frontend/src/components/landing/Landing.tsx`](../../frontend/src/components/landing/Landing.tsx)

- A "Try demo" button in the header (replacing the redundant "Sign in" link, which pointed at the same `/login` page as "Get started") and a "Try the demo" button in the hero CTA row, both calling `onTryDemo()` → `demoLogin()` → `router.replace("/dashboard")`.
- Busy/error state (`demoBusy`, `demoError`) with a user-facing message on failure: "Demo account unavailable. Please try again or sign up for a regular account."

**File:** [`frontend/src/components/shared/TopBar.tsx`](../../frontend/src/components/shared/TopBar.tsx)

- A persistent "Demo account" badge (pill, `PlayCircle` icon) shown whenever the logged-in user's `phone` (from the already-fetched `/users/me` profile, shared SWR cache key with `UserMenu` — no extra network request) matches `DEMO_PHONE`.

**File:** [`frontend/src/lib/date-range.tsx`](../../frontend/src/lib/date-range.tsx)

- A hardcoded `DEMO_RANGE` constant (`from: "2025-07-01"`, `to: "2026-06-30"`, matching the CSV's actual coverage and the backend's `demo.frozen-month`), applied automatically and exactly once via a `useEffect` (guarded by a `useRef`) when `DateRangeProvider` detects a demo session via `/users/me`. Real users' default range (`computeRange("this-month")`) is completely unaffected — the effect only fires when `profile?.phone === DEMO_PHONE`.

## Verification (2026-07-10, local)

Ran end-to-end against a fresh local Postgres + FastAPI ML service, then against the frontend in a
browser:

```
demo_user | transactions | budgets | contacts | emis | alerts | recommendations | categorized
        1 |          522 |       4 |        4 |    5 |      4 |                4 |         522
```

Category spread after seeding (curated overlay applied):

```
Food / Dine Out  171   Travel        155   Groceries     91
Transfers         41   Shopping       23   Subscriptions 14
Miscellaneous     13   Fees & Debt    12   Medical        2
```

`POST /api/v1/auth/demo/login` → `200 OK` with valid tokens; token verified against
`GET /api/v1/transactions`, `GET /api/v1/budgets`, `GET /api/v1/contacts` — all return correct,
correctly-scoped demo data. Browser click-through confirmed: landing page "Try demo" → dashboard
loads with non-blank budget progress (frozen-month), 5 upcoming EMIs, 4 alerts (3 unread, 1 read),
4 recommendations, and the "Demo account" badge in the top bar.

## Known Issue: Live ML Model Bias

The trained classifier (`ml/models/category_classifier.joblib`) currently predicts "Transfers" for
~89% of this demo dataset at confidence just above the 0.5 threshold (e.g. "Zomato" → Transfers,
0.51 confidence, when it should clearly be Food). This is a **model training-data quality issue**,
not a bug in the ingest/seeding pipeline — `/predict` is being called correctly and the low-confidence
retry job would behave identically for a real user's transactions.

The demo works around this via the curated-category overlay (§2, §3): after real ingest + ML
categorization runs, `DemoDataSeeder` replays the CSV's own curated `category` column onto each
transaction using `TransactionService.correctCategory` — the same correction path a real user takes
to fix a wrong category, not a bypass of the categorization pipeline. This is a **known, documented
limitation of the currently-trained model**, not a permanent design choice: if the classifier is
retrained with better-discriminating data, this overlay may become unnecessary, or may need
adjusting if the CSV or model changes in the meantime. Worth tracking separately in `ml/training/`,
`ml/evaluation/` — out of scope for the demo feature itself.

## Deployment Checklist

- [x] `data/demo-transactions.csv` copied to `backend/src/main/resources/data/`
- [x] `CsvTransactionParser.java`, `DemoDataSeeder.java`, `DemoAuthController.java` compile (Lombok removed — this project doesn't use it; rewritten with explicit constructors/loggers to match the rest of the codebase)
- [x] `demo.enabled/phone/email/frozen-month` added to `application.yml`
- [x] `/api/v1/auth/demo/login` and `/api/v1/auth/demo/info` added to `SecurityConfig`'s public route list (missing this caused a 403 on first test)
- [x] Backend started, seeding logs confirmed clean
- [x] DB verification queries run — counts match expected (522 transactions / 4 budgets / 4 contacts / 5 EMIs / 4 alerts / 4 recommendations / 522 categorized)
- [x] `curl -X POST http://localhost:8080/api/v1/auth/demo/login` verified end-to-end with a follow-up authenticated call
- [x] Frontend: "Try demo" buttons added to landing page (header + hero CTA)
- [x] Frontend: `demoLogin()` implemented in `lib/authApi.ts`, wired into `Landing.tsx`
- [x] Frontend: demo badge (`TopBar.tsx`) and demo date-range default (`date-range.tsx`) implemented
- [x] Tested end-to-end in the browser: click button → demo dashboard loads with non-blank budgets/EMIs/alerts/recommendations
- [ ] Production: confirm `FASTAPI_ML_URL` / `ML_INTERNAL_KEY` are set in the deployed environment so live categorization matches local behavior
- [ ] Production: confirm `demo.frozen-month` / frontend `DEMO_RANGE` are revisited if the CSV is ever regenerated

## Known Limitations & Future Enhancements

- Demo data is static once seeded — no scheduled reset job exists yet
- No device API key for demo (demo is web-only, not Android)
- Model bias (see above) means any *newly* seeded transaction not covered by the CSV's curated category list would fall back to live (currently weak) ML output
- The frozen-month budget mechanism and the EMI/alert/recommendation seeding are both permanently tied to the CSV's current date range (Jul 2025 – Jun 2026); regenerating the CSV with a different range requires updating `demo.frozen-month`, the frontend `DEMO_RANGE`, and the hand-picked EMI/alert/recommendation values together

### Future Enhancements

1. Scheduled reset job to restore demo data to a fresh state periodically
2. Demo-to-real upgrade path (convert demo session to a real account)
3. Multiple demo personas (student, family, freelancer)
4. Investigate/retrain the classifier for better category discrimination, then re-evaluate whether the curated-category overlay is still needed

## Monitoring & Logging

**Startup logs to check:**
```
INFO  DemoDataSeeder - Starting demo data seeding...
INFO  DemoDataSeeder - Creating demo user with phone: +919876543210
INFO  DemoDataSeeder - Demo user created: <uuid>
INFO  DemoDataSeeder - Loading demo transactions from CSV: data/demo-transactions.csv
INFO  DemoDataSeeder - Parsed 522 transactions from CSV
INFO  DemoDataSeeder - Ingested demo transactions: status=200 OK, items=522
INFO  DemoDataSeeder - Applied 522 curated categories from CSV
INFO  DemoDataSeeder - Set budget for category 7 to Rs.10000
INFO  DemoDataSeeder - Created contact: Rahul Sharma (FRIEND)
INFO  DemoDataSeeder - Created EMI: Car Loan EMI (Rs.5000, due day 28)
INFO  DemoDataSeeder - Created alert: CATEGORY_APPROACHING_LIMIT (MEDIUM)
INFO  DemoDataSeeder - Created recurring-payment alert for Spotify
INFO  DemoDataSeeder - Created recommendation for category 1
INFO  DemoDataSeeder - Demo data seeding completed successfully
```

**Database verification queries:**
```sql
SELECT id, phone, email FROM users WHERE phone = '+919876543210';

SELECT COUNT(*) FROM transactions t
JOIN users u ON t.user_id = u.id WHERE u.phone = '+919876543210';

SELECT c.name, b.monthly_limit FROM budgets b
JOIN users u ON b.user_id = u.id
JOIN categories c ON b.category_id = c.id
WHERE u.phone = '+919876543210';

SELECT name, relationship_type FROM contacts c
JOIN users u ON c.user_id = u.id WHERE u.phone = '+919876543210';

SELECT label, due_day, amount FROM emis e
JOIN users u ON e.user_id = u.id WHERE u.phone = '+919876543210';

SELECT type, priority, is_read FROM alerts a
JOIN users u ON a.user_id = u.id WHERE u.phone = '+919876543210';
```

> Note: the demo user's `id` is generated dynamically by `findOrCreateByPhone`, not a fixed
> constant — always resolve it by phone (`+919876543210`), not by a hardcoded UUID.

## Next Steps

1. **QA** — walk through [demo-deployment-checklist.md](./demo-deployment-checklist.md) (flagged for a follow-up pass — it still assumes a hardcoded demo user UUID and raw-SQL seeding, both no longer accurate; see this doc's audit notes)
2. **Production deploy** — confirm ML service env vars and `demo.frozen-month` are set so live categorization and budget progress work the same way in production as they did locally
3. **Revisit the curated-category overlay** once the classifier is retrained (see "Known Issue" above)
