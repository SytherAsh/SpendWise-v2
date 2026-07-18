# API Reference

## Base URL

All routes are versioned from day one:

```text
https://<backend-host>/api/v1/
```

## Authentication

All protected endpoints require a JWT Bearer token in the Authorization header:

```text
Authorization: Bearer <access_token>
```

- Access token expiry: 7 days
- Refresh token is rotated silently on each use
- Admin endpoints require a separate admin JWT signed with `ADMIN_JWT_SECRET` — regular user tokens are rejected at the route level

## Endpoint Groups

### `/auth` — Authentication

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| POST | `/auth/otp/send` | Send OTP to phone number | Public |
| POST | `/auth/otp/verify` | Verify OTP; return access + refresh token | Public |
| POST | `/auth/google` | Google OAuth login; return tokens | Public |
| POST | `/auth/token/refresh` | Rotate refresh token; return new access token | Public |
| POST | `/auth/logout` | Invalidate refresh token | User |
| POST | `/auth/demo/login` | **Demo login** — get tokens for pre-seeded demo account (no OTP needed) | Public |
| POST | `/auth/demo/info` | **Demo info** — get description of demo account features | Public |

### `/users` — User Profile & Preferences

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/users/me` | Get current user profile | User |
| PUT | `/users/me` | Update profile | User |
| GET | `/users/me/preferences` | Get preferences (alert channels, selected apps/banks) | User |
| PUT | `/users/me/preferences` | Update preferences | User |
| PUT | `/users/me/fcm-token` | Register/rotate the device's Firebase Cloud Messaging token, used by Alerts' push dispatch (added Epic 5 — see `docs/spec/database.md` `user_preferences.fcm_token`) | User |
| POST | `/users/me/onboarding` | Submit onboarding data; records DPDP consent; registers and returns the raw device API key (only occurrence — store immediately in device secure storage) | User |
| POST | `/users/me/bank-statement` | Upload bank statement PDF for historical transaction seed | User |

> **Onboarding response** includes the raw device API key (see Key Schemas below). The client must persist it in device secure storage immediately — it is never returned again. Only the hash is stored server-side in `device_api_keys`. The DPDP consent record is written to the `user_consent` table at this step.

### `/contacts` — Counterparty Metadata

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/contacts` | List all contacts for the current user | User |
| POST | `/contacts` | Create a contact | User |
| PUT | `/contacts/:id` | Update a contact (full replace) | User |
| DELETE | `/contacts/:id` | Delete a contact | User |

> Served by the User module (`com.spendwise.user`) per [ADR-010](./decisions.md#adr-010-counterparty-metadata-is-not-an-ml-category) — this is deliberately **not** part of the Transaction or Categorization module, and never writes to `categories`/`transaction_categories`. A contact has `name`, `relationshipType` (one of `family`, `friend`, `self`, `settlement`), and at least one of `recipientNamePattern`, `upiId`, `phoneNumber` — enforced server-side as 400 `CONTACT_MISSING_IDENTIFIER` if all three are absent. The frontend fetches the full list and matches it against a transaction's `recipient_name`/`upi_id` client-side to group and tag Transfer transactions in the Transactions page UI; the backend performs no matching and stores no link between a contact and any transaction. `PUT`/`DELETE` scope to the caller's own contacts and return 404 `CONTACT_NOT_FOUND` (not 403) for another user's contact id, matching the `TransactionNotFoundException` existence-hiding convention.

### `/ingest` — SMS Transaction Ingestion

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| POST | `/ingest/transactions` | Android app posts a batch of parsed transactions | Device API Key + User JWT |

> The `/ingest` endpoint requires both a user JWT and a device-level API key registered at onboarding. This prevents spoofed transaction injection from non-app clients.
>
> **Idempotency:** If a `transaction_id` already exists for the same `user_id`, the server returns `409 Conflict`. The Android Sync module treats `409` as a successful acknowledgment and removes the item from the local queue — it is not an error requiring a retry. A `409` on one item within a batch does not fail the remaining items.

### `/transactions` — Transaction Management

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/transactions` | List transactions (paginated, 50/page, cursor-based); supports optional filters: `category` (category_id, or the literal `uncategorized`), `direction` (`credit` or `debit`), `from`, `to` (ISO 8601 dates), `sort` (`date_desc` default, or `amount_desc`) | User |
| GET | `/transactions/:id` | Get single transaction | User |
| POST | `/transactions` | Manually create a transaction | User |
| PUT | `/transactions/:id/category` | Correct a transaction's category; writes a labeled example to `ml_corrections` for the next retraining cycle | User |

> **Category correction ownership:** `PUT /transactions/:id/category` is served by the Transaction module. The controller updates `transaction_categories` (new category assignment) and writes directly to `ml_corrections` (correction record) — both within the Transaction module's own DB access scope. No cross-module service call to the Categorization module is made; the Categorization module reads `ml_corrections` independently during its retraining cycle. This keeps the module dependency graph acyclic: Categorization → Transaction remains one-way.
>
> **`category=uncategorized` (added for the Transactions page redesign):** `GET /transactions`'s `category` filter accepts a numeric category id, the literal string `uncategorized` (filters to transactions with no `transaction_categories` row at all), or is omitted (no filter). Any other non-numeric value is a 400 (`INVALID_CATEGORY_FILTER`). Backward compatible — numeric filtering is unchanged.
>
> **`recipientCanonical` (added ML strategy phase, 2026-07-13):** every transaction in a `GET /transactions` (and `/transactions/:id`) response now carries a `recipientCanonical` field — the deduplicated payee name assigned by the weekly `RecipientCanonicalizationJob` (ADR-013), or `null` until that job has run for the user. Additive and backward compatible; the raw `recipientName` is always still present and unchanged. Clients should prefer `recipientCanonical ?? recipientName` for display and payee grouping.
>
> **Category filters are debit-only:** whenever `category` is set (a numeric id or `uncategorized`), the result additionally excludes any transaction where `debit` is not positive — money received (a refund, an incoming transfer) never appears in a category-filtered view, even if it happens to carry that category. This only applies when a category filter is active; the unfiltered list (no `category` param) is unaffected and still returns every transaction, spend or income.
>
> **`direction` (added for the Transactions page "Received" tile, UI/UX polish phase):** `credit` restricts the result to credit-direction transactions (`credit > 0`), `debit` to debit-direction (`debit > 0`); omitted applies no direction filter. Independent of `category`/`uncategorized` — passing both is accepted but the two filters simply AND together (a category filter's own implicit `debit > 0` combined with `direction=credit` yields no rows, since a transaction can't be both). The Received tile calls this with `direction=credit` and no `category`, deliberately pulling every credit-direction transaction across all categories — see `docs/spec/decisions.md` ADR-010's status update. Any value other than `credit`/`debit` is a 400 (`INVALID_DIRECTION`).
>
> **`sort=amount_desc` (added for the Analytics category deep-dive's "biggest transactions"):** ranks by `ABS(amount)` descending instead of the default `transaction_date DESC, id DESC`. This is a **bounded, non-paginated top-N read**, not a second pagination mode — `cursor` cannot be combined with it (400 `INVALID_SORT`; ranking by magnitude has no stable keyset seek across concurrent inserts the way `(transaction_date, id)` does), and the response always has `nextCursor: null`, `hasMore: false` regardless of how many rows actually match. Ask for a bigger `limit` if you need more than one "page." Any `sort` value other than `date_desc` (the default) or `amount_desc` is a 400 (`INVALID_SORT`).

### `/categories` — Categories

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/categories` | List all 13 predefined categories | User |

> **Module:** Served by the Transaction module (`com.spendwise.transaction`). Categories are the shared label set for all transaction operations. The Categorization module is responsible for ML inference and retraining, not for serving the category list.

### `/budgets` — Budget Management

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/budgets` | Get all budgets for current month | User |
| POST | `/budgets` | Create or update a category budget | User |
| GET | `/budgets/progress` | Get budget vs. actual spending per category | User |
| GET | `/budgets/suggestions` | Get history-based budget suggestions derived from spending patterns (requires bank statement or SMS history) | User |

> **Budget upsert:** `POST /budgets` is an idempotent upsert. If a budget for this category in the current month already exists, it is replaced. Repeated calls with the same parameters are safe.
>
> **`/budgets/suggestions` averaging window (updated for the Planning page redesign):** averages the trailing **6** calendar months of spend per category (current month excluded, since it's still in progress) — widened from the original 3-month default (Epic 5) at the product owner's explicit request, to smooth seasonal categories like Travel. Still an undocumented-by-spec default, not a hard requirement; a category with no spend in that window returns `available: false` rather than an error (unchanged).

### `/alerts` — Alerts

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/alerts` | List alerts for user (paginated, cursor-based); supports optional filter: `is_read` | User |
| PUT | `/alerts/:id/read` | Mark alert as read (also serves as "dismiss" for a `recurring_payment` alert — no EMI is created) | User |
| POST | `/alerts/:id/confirm` | Confirm a `recurring_payment` alert as a tracked subscription — creates an `emis` row linked to the alert's representative transaction and marks the alert read (E6-S2-T2). 400 if the alert isn't type `recurring_payment`. Idempotent: confirming the same alert twice returns the already-created EMI rather than erroring. | User |

### `/recommendations` — Savings Recommendations

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/recommendations` | Get prioritized recommendations feed | User |
| PUT | `/recommendations/:id/dismiss` | Dismiss a recommendation | User |

### `/analytics` — Analytics & Export

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/analytics/summary` | Total spend/income, category breakdown for date range | User |
| GET | `/analytics/categories` | Per-category breakdown with drilldown | User |
| GET | `/analytics/comparison` | Week/month/year comparison view | User |
| GET | `/analytics/trends` | Spending trend over time (line chart data); optionally scoped to one category | User |
| GET | `/analytics/export/pdf` | Export PDF report for selected date range | User |
| GET | `/analytics/export/csv` | Export CSV for selected date range | User |

Query parameters for analytics: `from`, `to`, `granularity` (week/month/year — `/analytics/trends` additionally accepts `day`)

> **`category` is `/analytics/trends`-only (corrected 2026-07-09):** this doc previously implied every analytics endpoint accepted a `category` filter — it doesn't. Only `GET /analytics/trends` takes `category` (a numeric category id; no `uncategorized` sentinel, unlike `/transactions`) and applies it as a real `category_id` filter. `/analytics/summary` and `/analytics/categories` always return every category's breakdown for the range (that's their contract — a caller who wants one category's total reads it off the response) and have no `category` param at all; passing one is silently ignored, not an error. `/analytics/comparison` has no `category` param either, and separately ignores `from`/`to` too (see the Epic 7 addendum below) — it is not usable for a range-scoped, category-scoped comparison. The Analytics page's category deep-dive (Current Phase redesign) computes "this period vs. previous period" itself instead: fetch `/analytics/trends?category=<id>` for the selected range and for the equal-length window immediately before it, then sum each window's buckets client-side.
>
> **Epic 7 addenda (implemented 2026-07-03, not fully spelled out above):**
> - `/analytics/summary`, `/analytics/categories`, `/analytics/trends`, and both export endpoints
>   require `from`+`to` (400 if either is missing) — inclusive both ends, matching
>   `/transactions`' existing `from`/`to` convention.
> - `/analytics/comparison` takes **only** `granularity` (default `month`) — it does not accept
>   `from`/`to`. It is always anchored to *today* (server clock, UTC): the current calendar
>   week/month/year vs. the immediately preceding one of the same length. Undocumented default,
>   resolved during the Epic 7 handoff review against `docs/operations/user_flows.md`'s "compare this month
>   vs. last" framing — the same category of gap as Epic 5's "trailing 3 months" budget-suggestion
>   default.
> - `/analytics/export/pdf` accepts either `from`+`to` **or** `financialYear=<YYYY>` (meaning the
>   Indian financial year, `YYYY`-04-01 to `(YYYY+1)`-03-31) — exactly one must be present, 400
>   otherwise.
>
> **Epic 9/local-E2E addendum (found + fixed 2026-07-05):** `/analytics/trends` additionally
> accepts `granularity=day` (`/analytics/comparison` does not — it stays week/month/year only,
> per its own periodStart/nextPeriodStart logic above). The Android dashboard's 30-day daily
> spending trend line (E9-S2-T1) calls `/analytics/trends?granularity=day`, which 400'd against
> the original week/month/year-only validator during the first live local end-to-end test —
> this endpoint's own doc line was written before that client existed and never anticipated a
> daily bucket. `date_trunc('day', ...)` is a native Postgres field, so no SQL change was needed.
>
> **"Uncategorized" row (added for the Transactions page redesign):** `GET /analytics/categories`
> now additionally returns a synthetic row for transactions with no category assigned —
> `categoryId: null`, `categoryName: "Uncategorized"` — alongside the normal per-category rows,
> but only when at least one such transaction exists in range (consistent with this endpoint's
> existing "only categories with a transaction are represented" behavior). `/analytics/summary`
> and `/analytics/comparison` are unaffected — they still call the original categorized-only
> query and never include this row.
>
> **This "Uncategorized" row is debit-only** (unlike every other row `/analytics/categories` and
> `/analytics/summary` return): money received with no category assigned is not "an uncategorized
> expense" and is never counted here. `totalIncome` on this synthetic row is therefore always
> `0`. By contrast, a *real* category's row (e.g. `categoryId: 7`) still includes any credit rows
> assigned to it in `totalIncome`/`transactionCount` — that per-category behavior is unchanged and
> intentional (docs/operations/testing.md's Epic 7 summary test covers it): it reflects the full picture for
> Analytics/Summary, including refunds. The Transactions page's frontend tiles only ever display
> each category's `totalSpend` (already debit-only) — never its `totalIncome` — so this
> distinction is invisible there; it only matters to a future consumer of this endpoint's raw
> per-category `totalIncome`/`transactionCount` fields.

### `/chatbot` — AI Chatbot

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| POST | `/chatbot/sessions` | Start a new chat session; returns `sessionId` | User |
| GET | `/chatbot/sessions` | List all chat sessions for the current user, ordered by `last_active_at` DESC | User |
| GET | `/chatbot/sessions/:id` | Get full conversation history for a session | User |
| POST | `/chatbot/message` | Send a message (requires `sessionId` in request body); returns LLM response with transaction context | User |

> **Session lifecycle:** Create a session first (`POST /chatbot/sessions`) to receive a `sessionId`. Supply that `sessionId` in the request body of every subsequent `POST /chatbot/message`. Conversation history is persisted in `chatbot_conversations` and survives across app restarts.

### `/emis` — EMI / Loan Tracking

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/emis` | List all active EMIs/loans | User |
| POST | `/emis` | Manually add an EMI | User |
| PUT | `/emis/:id` | Update EMI details (label, amount, due_day) | User |
| PATCH | `/emis/:id` | Deactivate an EMI (sets `is_active = false`; record is retained for recurring-payment history) | User |

> **Module:** The `/emis` endpoint group is owned by the Transaction module (`com.spendwise.transaction`). EMIs are specialized financial transactions rather than an independent domain and share the Transaction module's data-access and query patterns.

### `/admin` — Admin Portal

All admin endpoints require an admin JWT (signed with `ADMIN_JWT_SECRET` — a completely separate secret from `JWT_SECRET`; regular user tokens are rejected at the route level).

> **Doc gap closed in Epic 11:** `AdminJwtAuthFilter`/`SecurityConfig` (E1-S2-T1) validated an
> admin-signed token from day one, but no endpoint ever issued one — this table never had a login
> row. `POST /admin/auth/login` below is the missing issuer, added alongside the rest of Epic 11.

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| POST | `/admin/auth/login` | Exchange the seeded admin username/password (env-configured, never a regular user account) for an `ADMIN_JWT_SECRET`-signed token | Public (rate-limited) |
| GET | `/admin/users` | List all users with stats | Admin JWT |
| GET | `/admin/users/:id` | Full data for a specific user | Admin JWT |
| GET | `/admin/analytics` | Aggregate stats across all users | Admin JWT |
| GET | `/admin/analytics/comparison` | Cross-user spending comparison | Admin JWT |
| GET | `/admin/logs` | System logs (parser failures, errors, sync events) | Admin JWT |
| GET | `/admin/ml/accuracy` | ML model accuracy metrics | Admin JWT |
| POST | `/admin/ml/retrain` | Trigger manual model retraining via Categorization module | Admin JWT |
| DELETE | `/admin/users/:id` | Hard-delete user and purge all data (DPDP Act 2023 compliance) | Admin JWT |

## Pagination

Transaction and alert list endpoints use **cursor-based pagination**:

```text
GET /api/v1/transactions?limit=50&cursor=<last_transaction_id>
GET /api/v1/alerts?limit=20&cursor=<last_alert_id>
```

Response shape:

```json
{
  "data": [...],
  "nextCursor": "<id>",
  "hasMore": true
}
```

Analytics endpoints return full computed results — not paginated.

## Key Request and Response Schemas

Compact schemas for the three endpoints where ambiguity affects the Android–backend or client–backend integration contract.

### `POST /ingest/transactions` — Request

```json
{
  "transactions": [
    {
      "transaction_date": "2025-06-15T14:32:00Z",
      "debit": 350.0,
      "credit": 0.0,
      "amount": -350.0,
      "dr_cr_indicator": "DR",
      "transaction_id": "txn_abc123",
      "recipient_name": "Swiggy",
      "upi_id": "swiggy@okicici",
      "bank": "ICICI",
      "transaction_mode": "UPI",
      "note": null,
      "source": "sms"
    }
  ]
}
```

All fields correspond to the `transactions` table schema. `transaction_id` is required and must be unique per user. For SMS messages that do not include a bank reference number, synthesize it using the rule in `docs/spec/database.md` (`transaction_id` column comment).

### `POST /auth/otp/verify` — Response

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<jwt>",
  "expiresIn": 604800,
  "user": {
    "id": "<uuid>",
    "phone": "+91XXXXXXXXXX",
    "email": null
  }
}
```

`expiresIn` is in seconds (604800 = 7 days). `email` is `null` for phone-OTP users until linked.

### `POST /users/me/onboarding` — Response

```json
{
  "deviceApiKey": "<raw-key>",
  "user": {
    "id": "<uuid>",
    "phone": "+91XXXXXXXXXX"
  }
}
```

`deviceApiKey` is the raw key generated server-side. The client stores it in device secure storage and sends it in the `X-Device-Key` header on every `/ingest` request. Only the hash is persisted server-side; the raw key is never returned again after this response.

### `PUT /transactions/:id/category` — Request

```json
{
  "category_id": 7
}
```

The server writes a row to `ml_corrections` (old `category_id` → new `category_id`) before updating `transaction_categories`. This labeled example is included in the next ML retraining cycle.

## Error Responses

```json
{
  "error": "CATEGORY_NOT_FOUND",
  "message": "Category with id 15 does not exist",
  "status": 404
}
```

Standard HTTP status codes apply: 200, 201, 400, 401, 403, 404, 409, 429, 500.

Notable per-endpoint statuses:

| Endpoint | Status | Meaning |
| --- | --- | --- |
| `POST /ingest/transactions` | 409 | Duplicate `transaction_id` — treat as success in Android Sync; dequeue the item |
| `POST /auth/otp/send` | 429 | Rate-limit exceeded (max 5 OTP requests per phone number per hour) |
| `POST /auth/otp/verify` | 400 | OTP expired or invalid |
| `PUT /transactions/:id/category` | 404 | Transaction not found |
| `PUT /transactions/:id/category` | 400 | `category_id` does not exist in the `categories` table |
