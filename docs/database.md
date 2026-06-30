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
    email       VARCHAR NOT NULL,
    google_id   VARCHAR,           -- nullable if OTP login
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### `user_preferences`

```sql
CREATE TABLE user_preferences (
    user_id                UUID REFERENCES users(id) ON DELETE CASCADE,
    alert_channels         JSONB NOT NULL DEFAULT '{"push": true, "email": true}',
    selected_apps          TEXT[],    -- e.g. ["paytm", "gpay"]
    selected_banks         TEXT[],    -- e.g. ["SBI"]
    monthly_spend_estimate NUMERIC,   -- from onboarding
    PRIMARY KEY (user_id)
);
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
    amount           NUMERIC NOT NULL,           -- signed: negative=debit, positive=credit
    balance          NUMERIC,                    -- nullable (absent from most SMS)
    transaction_mode VARCHAR,                    -- UPI, INB, IMPS, NEFT (nullable — 36 nulls in real data)
    dr_cr_indicator  CHAR(2) NOT NULL,           -- 'DR' or 'CR'
    transaction_id   VARCHAR NOT NULL,           -- bank reference number (deduplication key)
    recipient_name   VARCHAR,                    -- nullable (48 nulls in real data)
    bank             VARCHAR,                    -- recipient bank code: HDFC, SBIN, PYTM (nullable — 180 nulls)
    upi_id           VARCHAR,                    -- nullable (absent for IMPS/NEFT — 180 nulls)
    note             TEXT,                       -- nullable (933/1653 null in real data)
    sms_raw_text     TEXT,                       -- stored for debugging; never exposed via user API
    source           transaction_source NOT NULL,
    parsed_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_user_date ON transactions(user_id, transaction_date DESC);
CREATE UNIQUE INDEX idx_transactions_unique_dedup ON transactions(user_id, transaction_id); -- dedup enforced at DB level
```

### `categories`

```sql
CREATE TABLE categories (
    id   SERIAL PRIMARY KEY,
    name VARCHAR NOT NULL,   -- Shopping, Entertainment, Sports & Fitness, Groceries, Travel,
                             -- Miscellaneous, Food/Dine Out, Cosmetics, Subscriptions
    icon VARCHAR             -- icon identifier for UI
);
```

Seed data (10 categories):

| id | name | icon |
|---|---|---|
| 1 | Shopping | shopping_bag |
| 2 | Entertainment | movie |
| 3 | Sports & Fitness | fitness_center |
| 4 | Groceries | local_grocery_store |
| 5 | Travel | flight |
| 6 | Miscellaneous | more_horiz |
| 7 | Food / Dine Out | restaurant |
| 8 | Cosmetics | face |
| 9 | Subscriptions | subscriptions |
| 10 | *(reserved)* | — |

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
    UNIQUE (user_id, category_id, month, year)
);
```

### `alerts`

```sql
CREATE TYPE alert_type AS ENUM ('mid_month_budget', 'category_overspend', 'recurring_payment');

CREATE TABLE alerts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         alert_type NOT NULL,
    triggered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMP,  -- null until successfully delivered
    payload      JSONB        -- alert-specific context (category_id, amount, threshold, etc.)
);
```

### `emis`

```sql
CREATE TABLE emis (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label              VARCHAR NOT NULL,   -- e.g. "Home Loan EMI"
    amount             NUMERIC NOT NULL,
    due_day            INT CHECK (due_day BETWEEN 1 AND 31),
    detected_from_sms  BOOLEAN NOT NULL DEFAULT TRUE,
    is_active          BOOLEAN NOT NULL DEFAULT TRUE
);
```

### `recommendations`

```sql
CREATE TABLE recommendations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text         TEXT NOT NULL,   -- LLM-generated one-liner
    priority     VARCHAR NOT NULL CHECK (priority IN ('high', 'medium', 'low')),
    generated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    is_dismissed BOOLEAN NOT NULL DEFAULT FALSE
);
```

### `ml_corrections`

```sql
CREATE TABLE ml_corrections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    old_category_id INT REFERENCES categories(id),
    new_category_id INT NOT NULL REFERENCES categories(id),
    corrected_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### `admin_logs`

```sql
CREATE TABLE admin_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  VARCHAR NOT NULL,  -- 'parse_failure', 'model_retrain', 'sync_error', 'prediction_low_confidence'
    payload     JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_logs_event_type ON admin_logs(event_type, created_at DESC);
```

### `chatbot_conversations`

```sql
CREATE TYPE chat_role AS ENUM ('user', 'assistant');

CREATE TABLE chatbot_conversations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id UUID NOT NULL,          -- groups messages into a single session
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
    is_active     BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_device_keys_user ON device_api_keys(user_id, is_active);
```

Registered once per device at onboarding. The raw key is generated on-device and stored in device secure storage; only the hash is persisted here. Validation at `/ingest`: hash the incoming key → `SELECT WHERE user_id = ? AND is_active = TRUE AND key_hash = ?` → reject with 401 if not found.

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
