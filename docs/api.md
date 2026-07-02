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

### `/users` — User Profile & Preferences

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/users/me` | Get current user profile | User |
| PUT | `/users/me` | Update profile | User |
| GET | `/users/me/preferences` | Get preferences (alert channels, selected apps/banks) | User |
| PUT | `/users/me/preferences` | Update preferences | User |
| POST | `/users/me/onboarding` | Submit onboarding data; records DPDP consent; registers and returns the raw device API key (only occurrence — store immediately in device secure storage) | User |
| POST | `/users/me/bank-statement` | Upload bank statement PDF for historical transaction seed | User |

> **Onboarding response** includes the raw device API key (see Key Schemas below). The client must persist it in device secure storage immediately — it is never returned again. Only the hash is stored server-side in `device_api_keys`. The DPDP consent record is written to the `user_consent` table at this step.

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
| GET | `/transactions` | List transactions (paginated, 50/page, cursor-based); supports optional filters: `category` (category_id), `from`, `to` (ISO 8601 dates) | User |
| GET | `/transactions/:id` | Get single transaction | User |
| POST | `/transactions` | Manually create a transaction | User |
| PUT | `/transactions/:id/category` | Correct a transaction's category; writes a labeled example to `ml_corrections` for the next retraining cycle | User |

> **Category correction ownership:** `PUT /transactions/:id/category` is served by the Transaction module. The controller updates `transaction_categories` (new category assignment) and writes directly to `ml_corrections` (correction record) — both within the Transaction module's own DB access scope. No cross-module service call to the Categorization module is made; the Categorization module reads `ml_corrections` independently during its retraining cycle. This keeps the module dependency graph acyclic: Categorization → Transaction remains one-way.

### `/categories` — Categories

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/categories` | List all 12 predefined categories | User |

> **Module:** Served by the Transaction module (`com.spendwise.transaction`). Categories are the shared label set for all transaction operations. The Categorization module is responsible for ML inference and retraining, not for serving the category list.

### `/budgets` — Budget Management

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/budgets` | Get all budgets for current month | User |
| POST | `/budgets` | Create or update a category budget | User |
| GET | `/budgets/progress` | Get budget vs. actual spending per category | User |
| GET | `/budgets/suggestions` | Get history-based budget suggestions derived from spending patterns (requires bank statement or SMS history) | User |

> **Budget upsert:** `POST /budgets` is an idempotent upsert. If a budget for this category in the current month already exists, it is replaced. Repeated calls with the same parameters are safe.

### `/alerts` — Alerts

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| GET | `/alerts` | List alerts for user (paginated, cursor-based); supports optional filter: `is_read` | User |
| PUT | `/alerts/:id/read` | Mark alert as read | User |

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
| GET | `/analytics/trends` | Spending trend over time (line chart data) | User |
| GET | `/analytics/export/pdf` | Export PDF report for selected date range | User |
| GET | `/analytics/export/csv` | Export CSV for selected date range | User |

Query parameters for analytics: `from`, `to`, `granularity` (week/month/year), `category`

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

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
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

All fields correspond to the `transactions` table schema. `transaction_id` is required and must be unique per user. For SMS messages that do not include a bank reference number, synthesize it using the rule in `docs/database.md` (`transaction_id` column comment).

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
