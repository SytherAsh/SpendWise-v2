-- Transactions and category schema. See docs/database.md sections:
-- transactions, categories, transaction_categories.
-- Schema grounded in real SBI bank statement data (1,653 transactions, April 2023 - March 2026).

CREATE TYPE transaction_source AS ENUM ('sms', 'bank_statement', 'manual');

CREATE TABLE transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    transaction_date TIMESTAMP NOT NULL,         -- date-only from bank statement; date+time from SMS
    debit            NUMERIC NOT NULL DEFAULT 0, -- 0 if credit transaction
    credit           NUMERIC NOT NULL DEFAULT 0, -- 0 if debit transaction
    amount           NUMERIC NOT NULL,           -- signed: negative=debit, positive=credit; canonical field for all DR/CR query filters
    balance          NUMERIC,                    -- nullable (absent from most SMS)
    transaction_mode VARCHAR,                    -- UPI, INB, IMPS, NEFT (nullable -- 36 nulls in real data)
    dr_cr_indicator  CHAR(2) NOT NULL,           -- 'DR' or 'CR'
    transaction_id   VARCHAR NOT NULL,           -- bank ref number (dedup key); for SMS without an explicit ref,
                                                 -- synthesize: hex(SHA-256(user_id || upi_id_or_recipient_name || amount || date_trunc('minute', transaction_date)))
    recipient_name   VARCHAR,                    -- nullable (48 nulls in real data)
    bank             VARCHAR,                    -- recipient bank code: HDFC, SBIN, PYTM (nullable -- 180 nulls)
    upi_id           VARCHAR,                    -- nullable (absent for IMPS/NEFT -- 180 nulls)
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

CREATE TABLE categories (
    id   SERIAL PRIMARY KEY,
    name VARCHAR NOT NULL,   -- Shopping, Entertainment, Sports & Fitness, Groceries, Travel,
                             -- Miscellaneous, Food / Dine Out, Cosmetics, Subscriptions, Transfers
    icon VARCHAR,            -- icon identifier for UI
    CONSTRAINT uq_categories_name UNIQUE (name)  -- prevents duplicate seed entries on re-deploy or repeated migrations
);

INSERT INTO categories (id, name, icon) VALUES
    (1,  'Shopping',          'shopping_bag'),
    (2,  'Entertainment',     'movie'),
    (3,  'Sports & Fitness',  'fitness_center'),
    (4,  'Groceries',         'local_grocery_store'),
    (5,  'Travel',            'flight'),
    (6,  'Miscellaneous',     'more_horiz'),
    (7,  'Food / Dine Out',   'restaurant'),
    (8,  'Cosmetics',         'face'),
    (9,  'Subscriptions',     'subscriptions'),
    (10, 'Transfers',         'swap_horiz');

-- Keep the SERIAL sequence in sync with the explicit ids inserted above,
-- so the next application-inserted category doesn't collide with id 10.
SELECT setval(pg_get_serial_sequence('categories', 'id'), 10);

CREATE TYPE assigned_by_type AS ENUM ('ml', 'user');

CREATE TABLE transaction_categories (
    transaction_id   UUID REFERENCES transactions(id) ON DELETE CASCADE,
    category_id      INT REFERENCES categories(id),
    confidence_score FLOAT,             -- ML model confidence 0-1 (null if assigned by user)
    assigned_by      assigned_by_type NOT NULL,
    assigned_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (transaction_id)
);

-- Supports budget aggregation and analytics queries that join transactions -> category.
-- Without this, every budget progress calculation scans the entire transaction_categories table.
CREATE INDEX idx_txn_categories_category ON transaction_categories(category_id);
