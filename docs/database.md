# Database Design

## System

- **PostgreSQL** via **Supabase** (free tier)
- Single shared database for the modular monolith
- All transaction data kept indefinitely — it is a training asset for the ML model

## Schema

### `users`

```sql
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone       VARCHAR,           -- nullable if Google login
    email       VARCHAR,           -- nullable if phone OTP login
    google_id   VARCHAR,           -- nullable if OTP login
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_identifier CHECK (phone IS NOT NULL OR google_id IS NOT NULL)
);

-- Prevents duplicate accounts: two users cannot share the same phone number or Google ID.
-- Partial indexes allow NULLs (phone-OTP user has no google_id; Google-login user may have no phone).
CREATE UNIQUE INDEX idx_users_unique_phone     ON users(phone)     WHERE phone IS NOT NULL;
CREATE UNIQUE INDEX idx_users_unique_google_id ON users(google_id) WHERE google_id IS NOT NULL;
```

### `user_preferences`

```sql
CREATE TABLE user_preferences (
    user_id                UUID REFERENCES users(id) ON DELETE CASCADE,
    alert_channels         JSONB NOT NULL DEFAULT '{"push": true, "email": true}',
    selected_apps          TEXT[],    -- e.g. ["paytm", "gpay"]
    selected_banks         TEXT[],    -- e.g. ["SBI"]
    monthly_spend_estimate NUMERIC,   -- from onboarding
    fcm_token              TEXT,      -- added V8, Epic 5: Firebase Cloud Messaging device registration
                                       -- token, set via PUT /users/me/fcm-token; nullable until the
                                       -- client registers one, and whenever the client's token rotates
                                       -- (FCM tokens are not permanent). Null = push dispatch skipped.
    PRIMARY KEY (user_id)
);
```

### `user_consent`

```sql
CREATE TABLE user_consent (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    consented_at TIMESTAMP NOT NULL DEFAULT NOW(),
    app_version  VARCHAR,           -- version of the app shown at consent time
    consent_text TEXT NOT NULL      -- snapshot of the exact consent text shown to the user
);
```

### `refresh_tokens`

```sql
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR NOT NULL,     -- SHA-256 of the raw token; never stored plain-text
    issued_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMP NOT NULL,   -- sliding window; reset on each silent rotation
    revoked_at  TIMESTAMP             -- null until logout or replay-attack detection; all user tokens revoked on compromise
);

-- Primary lookup path: every /auth/token/refresh and /auth/logout hashes the incoming token and looks up this index.
CREATE UNIQUE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
-- Supports per-user session listing and scheduled purge of expired tokens.
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id, expires_at);
```

### `transactions`

Schema grounded in real SBI bank statement data (1,653 transactions, April 2023 – March 2026).

```sql
CREATE TYPE transaction_source AS ENUM ('sms', 'bank_statement', 'manual');

CREATE TABLE transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    transaction_date TIMESTAMP NOT NULL,         -- date-only from bank statement; date+time from SMS
    debit            NUMERIC NOT NULL DEFAULT 0, -- 0 if credit transaction
    credit           NUMERIC NOT NULL DEFAULT 0, -- 0 if debit transaction
    amount           NUMERIC NOT NULL,           -- signed: negative=debit, positive=credit; canonical field for all DR/CR query filters
    balance          NUMERIC,                    -- nullable (absent from most SMS)
    transaction_mode VARCHAR,                    -- UPI, INB, IMPS, NEFT (nullable — 36 nulls in real data)
    dr_cr_indicator  CHAR(2) NOT NULL,           -- 'DR' or 'CR'
    transaction_id   VARCHAR NOT NULL,           -- bank ref number (dedup key); for SMS without an explicit ref,
                                                 -- synthesize: hex(SHA-256(user_id || upi_id_or_recipient_name || amount || date_trunc('minute', transaction_date)))
    recipient_name   VARCHAR,                    -- nullable (48 nulls in real data)
    bank             VARCHAR,                    -- recipient bank code: HDFC, SBIN, PYTM (nullable — 180 nulls)
    upi_id           VARCHAR,                    -- nullable (absent for IMPS/NEFT — 180 nulls)
    note             TEXT,                       -- nullable (933/1653 null in real data)
    sms_raw_text     TEXT,                       -- stored for debugging; never exposed via user API
    source           transaction_source NOT NULL,
    parsed_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dr_cr_consistency CHECK (
        (dr_cr_indicator = 'DR' AND amount < 0 AND debit > 0 AND credit = 0) OR
        (dr_cr_indicator = 'CR' AND amount > 0 AND credit > 0 AND debit = 0)
    )
);

CREATE INDEX idx_transactions_user_date ON transactions(user_id, transaction_date DESC);
CREATE UNIQUE INDEX idx_transactions_unique_dedup ON transactions(user_id, transaction_id); -- dedup enforced at DB level
```

### `categories`

```sql
CREATE TABLE categories (
    id   SERIAL PRIMARY KEY,
    name VARCHAR NOT NULL,   -- Shopping, Entertainment, Sports & Fitness, Groceries, Travel,
                             -- Miscellaneous, Food / Dine Out, Cosmetics, Subscriptions, Transfers
                             -- (ids 1-10, seeded in V2); Medical, Fees & Debt (ids 11-12, seeded in V7)
    icon VARCHAR,            -- icon identifier for UI
    CONSTRAINT uq_categories_name UNIQUE (name)  -- prevents duplicate seed entries on re-deploy or repeated migrations
);
```

Seed data (12 categories; 1-10 seeded in V2, 11-12 added later in V7 once
labeling ML training data surfaced medical/fee transactions with no home
among the original 10 — see `docs/requirements.md` and
`ml/labeling/CATEGORY_GUIDELINES.md`):

| id | name | icon |
| --- | --- | --- |
| 1 | Shopping | shopping_bag |
| 2 | Entertainment | movie |
| 3 | Sports & Fitness | fitness_center |
| 4 | Groceries | local_grocery_store |
| 5 | Travel | flight |
| 6 | Miscellaneous | more_horiz |
| 7 | Food / Dine Out | restaurant |
| 8 | Cosmetics | face |
| 9 | Subscriptions | subscriptions |
| 10 | Transfers | swap_horiz |
| 11 | Medical | local_hospital |
| 12 | Fees & Debt | request_quote |

### `transaction_categories`

```sql
CREATE TYPE assigned_by_type AS ENUM ('ml', 'user');

CREATE TABLE transaction_categories (
    transaction_id   UUID REFERENCES transactions(id) ON DELETE CASCADE,
    category_id      INT REFERENCES categories(id),
    confidence_score FLOAT,             -- ML model confidence 0–1 (null if assigned by user)
    assigned_by      assigned_by_type NOT NULL,
    assigned_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (transaction_id)
);

-- Supports budget aggregation and analytics queries that join transactions → category.
-- Without this, every budget progress calculation scans the entire transaction_categories table.
CREATE INDEX idx_txn_categories_category ON transaction_categories(category_id);
```

### `budgets`

```sql
CREATE TABLE budgets (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id   INT NOT NULL REFERENCES categories(id),
    monthly_limit NUMERIC NOT NULL,
    month         INT NOT NULL CHECK (month BETWEEN 1 AND 12),
    year          INT NOT NULL,
    UNIQUE (user_id, category_id, month, year),
    CONSTRAINT chk_budget_limit_positive CHECK (monthly_limit > 0)  -- a zero or negative limit causes the 80% threshold alert to fire immediately or inverts progress bars
);
```

### `alerts`

```sql
CREATE TYPE alert_type AS ENUM ('mid_month_budget', 'category_overspend', 'category_approaching_limit', 'recurring_payment');

CREATE TABLE alerts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         alert_type NOT NULL,
    priority     VARCHAR NOT NULL CHECK (priority IN ('high', 'medium', 'low')),
    triggered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMP,              -- null until successfully delivered
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    payload      JSONB                   -- alert-specific context (category_id, amount, threshold, etc.)
);

-- Covers the alerts-history list query: all alerts for a user ordered by most recent.
CREATE INDEX idx_alerts_user_date ON alerts(user_id, triggered_at DESC);
-- Covers the unread-badge count and notification-centre queries.
-- Preferred by the planner over idx_alerts_user_date when is_read = FALSE is in the predicate because it is smaller and excludes already-read rows.
CREATE INDEX idx_alerts_unread ON alerts(user_id, triggered_at DESC) WHERE is_read = FALSE;
```

### `emis`

```sql
CREATE TABLE emis (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label                 VARCHAR NOT NULL,   -- e.g. "Home Loan EMI"
    amount                NUMERIC NOT NULL,
    due_day               INT CHECK (due_day BETWEEN 1 AND 31),
    detected_from_sms     BOOLEAN NOT NULL DEFAULT TRUE,
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    source_transaction_id UUID REFERENCES transactions(id) ON DELETE SET NULL,  -- representative transaction that triggered auto-detection; null for manual entries
    CONSTRAINT chk_emi_amount_positive CHECK (amount > 0)  -- EMI amounts are always positive; the debit direction is implicit in the fact that it is a scheduled outgoing payment
);

-- Prevents the same transaction from generating duplicate EMI entries across detection runs.
CREATE UNIQUE INDEX idx_emis_source_txn   ON emis(source_transaction_id) WHERE source_transaction_id IS NOT NULL;
-- Covers the EMI tracking panel query: active EMIs for a user.
CREATE INDEX         idx_emis_user_active ON emis(user_id, is_active);
```

### `recommendations`

```sql
CREATE TABLE recommendations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id  INT REFERENCES categories(id),  -- null for global (cross-category) recommendations
    text         TEXT NOT NULL,                  -- LLM-generated one-liner
    priority     VARCHAR NOT NULL CHECK (priority IN ('high', 'medium', 'low')),
    generated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    is_dismissed BOOLEAN NOT NULL DEFAULT FALSE
);

-- Prevents duplicate active recommendations per user per category.
-- Global recommendations (category_id IS NULL) are not deduplicated by this index.
CREATE UNIQUE INDEX idx_recs_user_category_active
    ON recommendations(user_id, category_id)
    WHERE is_dismissed = FALSE AND category_id IS NOT NULL;
-- Covers the recommendations dashboard list: active recs for a user ordered by recency.
-- Not redundant with idx_recs_user_category_active — that index enforces per-category uniqueness;
-- this one provides the ordered list display path and covers global recs (category_id IS NULL) too.
CREATE INDEX idx_recs_user_active
    ON recommendations(user_id, generated_at DESC)
    WHERE is_dismissed = FALSE;
```

### `ml_corrections`

```sql
CREATE TABLE ml_corrections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    old_category_id INT REFERENCES categories(id),
    new_category_id INT NOT NULL REFERENCES categories(id),
    corrected_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_correction_different_category CHECK (old_category_id IS DISTINCT FROM new_category_id)  -- a no-op correction (old = new) produces a useless labeled example; IS DISTINCT FROM handles the NULL case where old_category_id is absent
);

-- Covers the weekly retraining job's incremental read: corrections since last_retrain_timestamp.
CREATE INDEX idx_ml_corrections_date ON ml_corrections(corrected_at);
```

### `admin_logs`

```sql
CREATE TABLE admin_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  VARCHAR NOT NULL,  -- 'parse_failure', 'model_retrain', 'sync_error', 'prediction_low_confidence'
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,  -- null for system-wide events (e.g. model_retrain)
    payload     JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_logs_event_type ON admin_logs(event_type, created_at DESC);
CREATE INDEX idx_admin_logs_user ON admin_logs(user_id, created_at DESC) WHERE user_id IS NOT NULL;
```

### `chatbot_sessions`

```sql
CREATE TABLE chatbot_sessions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    last_active_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chatbot_sessions_user ON chatbot_sessions(user_id, created_at DESC);
```

### `chatbot_conversations`

```sql
CREATE TYPE chat_role AS ENUM ('user', 'assistant');

CREATE TABLE chatbot_conversations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES chatbot_sessions(id) ON DELETE CASCADE,
    role       chat_role NOT NULL,
    message    TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chatbot_session ON chatbot_conversations(user_id, session_id, created_at);
```

### `device_api_keys`

```sql
CREATE TABLE device_api_keys (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_hash      VARCHAR NOT NULL,     -- bcrypt/SHA-256 hash of raw device key
    registered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at  TIMESTAMP,            -- updated on every successful /ingest auth; null until first use
    is_active     BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_device_keys_user ON device_api_keys(user_id, is_active);
```

Registered once per device at onboarding. The raw key is generated on-device and stored in device secure storage; only the hash is persisted here. Validation at `/ingest`: hash the incoming key → `SELECT WHERE user_id = ? AND is_active = TRUE AND key_hash = ?` → reject with 401 if not found.

### Auth login lookup addendum (V6, added during Epic 1 implementation)

`V5__row_level_security.sql`'s `users` policy only permits access when `id = current_setting('app.current_user_id')`. Login (OTP verify / Google login) must find an existing user **by phone or google_id** before any `app.current_user_id` exists — that policy can never match during this lookup, and under `FORCE ROW LEVEL SECURITY` the query would always return zero rows. `V6__auth_lookup_policy.sql` adds a second, permissive, SELECT-only policy on `users` (Postgres SELECT policies OR together): a row is visible only when the caller first sets `app.auth_lookup_identifier` to the exact phone/google_id being searched for. The same gap exists on `refresh_tokens` — `/auth/token/refresh` and `/auth/logout` must find a row **by `token_hash`** before knowing its `user_id` — so V6 adds an analogous SELECT-only policy there too, gated by `app.auth_lookup_token_hash`; exposing "a row with this exact SHA-256 hash exists" grants no capability beyond what already holding that raw token implies. See `docs/security.md` Supabase Row-Level Security for the full rationale. Approved by project owner 2026-07-02 as a deviation from this document's original RLS design.

## Deduplication Strategy

Before inserting a transaction, check:

1. **Primary**: `transaction_id` matches an existing row for the same `user_id` → reject as duplicate. Enforced by `UNIQUE INDEX idx_transactions_unique_dedup ON (user_id, transaction_id)` — the DB constraint is authoritative; the application check provides a cleaner 409 error response.
2. **Secondary**: `(upi_id, amount, transaction_date)` all match an existing row → reject as duplicate. Note: `upi_id` is nullable — this check degenerates silently for IMPS/NEFT transactions where `upi_id IS NULL`. Primary dedup handles those cases.

## Notes from Real Data (EDA on 3-year bank statement)

- 1,653 transactions spanning April 2023 – March 2026
- 47% of transactions are ₹0–50 (small everyday payments)
- `balance`, `bank`, `upi_id` are nullable — verified against real data
- `note` is sparse (56% null) — treated as optional enrichment
- `transaction_mode` has ~2% nulls in real data
- `source` column distinguishes SMS-ingested / bank-statement-uploaded / manually entered records
