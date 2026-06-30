# API Reference

## Base URL

All routes are versioned from day one:

```
https://<backend-host>/api/v1/
```

## Authentication

All protected endpoints require a JWT Bearer token in the Authorization header:

```
Authorization: Bearer <access_token>
```

- Access token expiry: 7 days
- Refresh token is rotated silently on each use
- Admin endpoints require a separate admin JWT — regular user tokens are rejected

## Endpoint Groups

### `/auth` — Authentication

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/auth/otp/send` | Send OTP to phone number | Public |
| POST | `/auth/otp/verify` | Verify OTP, return access + refresh token | Public |
| POST | `/auth/google` | Google OAuth login, return tokens | Public |
| POST | `/auth/token/refresh` | Rotate refresh token, return new access token | Public |
| POST | `/auth/logout` | Invalidate refresh token | User |

### `/users` — User Profile & Preferences

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/users/me` | Get current user profile | User |
| PUT | `/users/me` | Update profile | User |
| GET | `/users/me/preferences` | Get preferences (alert channels, selected apps/banks) | User |
| PUT | `/users/me/preferences` | Update preferences | User |
| POST | `/users/me/onboarding` | Submit onboarding data (apps, banks, spend estimate) | User |
| POST | `/users/me/bank-statement` | Upload bank statement PDF for historical seed | User |

### `/ingest` — SMS Transaction Ingestion

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/ingest/transactions` | Android app posts batch of parsed transactions | Device API Key + User JWT |

> The `/ingest` endpoint requires both a JWT and a device-level API key registered at onboarding. This prevents spoofed transaction injection.

### `/transactions` — Transaction Management

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/transactions` | List transactions (paginated, 50/page, cursor-based) | User |
| GET | `/transactions/:id` | Get single transaction | User |
| POST | `/transactions` | Manually create a transaction | User |
| GET | `/transactions?category=&from=&to=` | Filter by category and date range | User |

### `/categories` — Categories

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/categories` | List all 10 predefined categories | User |
| PUT | `/transactions/:id/category` | Correct a transaction's category (stores labeled example) | User |

### `/budgets` — Budget Management

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/budgets` | Get all budgets for current month | User |
| POST | `/budgets` | Create or update a category budget | User |
| GET | `/budgets/progress` | Get budget vs. actual spending per category | User |
| GET | `/budgets/suggestions` | Get AI-suggested budgets based on history | User |

### `/alerts` — Alerts

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/alerts` | List all alerts for user | User |
| PUT | `/alerts/:id/read` | Mark alert as read | User |

### `/recommendations` — Savings Recommendations

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/recommendations` | Get prioritized recommendations feed | User |
| PUT | `/recommendations/:id/dismiss` | Dismiss a recommendation | User |

### `/analytics` — Analytics & Export

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/analytics/summary` | Total spend/income, category breakdown for date range | User |
| GET | `/analytics/categories` | Per-category breakdown with drilldown | User |
| GET | `/analytics/comparison` | Week/month/year comparison view | User |
| GET | `/analytics/trends` | Spending trend over time (line chart data) | User |
| GET | `/analytics/export/pdf` | Export PDF report for selected date range | User |
| GET | `/analytics/export/csv` | Export CSV for selected date range | User |

Query parameters for analytics: `from`, `to`, `granularity` (week/month/year), `category`

### `/chatbot` — AI Chatbot

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/chatbot/message` | Send a message, get LLM response with transaction context | User |
| GET | `/chatbot/sessions/:sessionId` | Get conversation history for a session | User |
| POST | `/chatbot/sessions` | Start a new chat session | User |

### `/emis` — EMI / Loan Tracking

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/emis` | List all active EMIs/loans | User |
| POST | `/emis` | Manually add an EMI | User |
| PUT | `/emis/:id` | Update an EMI | User |
| DELETE | `/emis/:id` | Deactivate an EMI | User |

### `/admin` — Admin Portal

All admin endpoints require admin JWT (separate from user JWT).

| Method | Path | Description |
|---|---|---|
| GET | `/admin/users` | List all users with stats |
| GET | `/admin/users/:id` | Full data for a specific user |
| GET | `/admin/analytics` | Aggregate stats across all users |
| GET | `/admin/analytics/comparison` | Cross-user spending comparison |
| GET | `/admin/logs` | System logs (parser failures, errors, sync events) |
| GET | `/admin/ml/accuracy` | ML model accuracy metrics |
| POST | `/admin/ml/retrain` | Trigger manual model retraining |
| DELETE | `/admin/users/:id` | Delete user and purge all data (DPDP compliance) |

## Pagination

Transaction list endpoints use **cursor-based pagination**:

```
GET /api/v1/transactions?limit=50&cursor=<last_transaction_id>
```

Response includes:
```json
{
  "data": [...],
  "nextCursor": "<id>",
  "hasMore": true
}
```

Analytics endpoints return full computed results — not paginated.

## Error Responses

```json
{
  "error": "CATEGORY_NOT_FOUND",
  "message": "Category with id 15 does not exist",
  "status": 404
}
```

Standard HTTP status codes apply: 200, 201, 400, 401, 403, 404, 429, 500.
