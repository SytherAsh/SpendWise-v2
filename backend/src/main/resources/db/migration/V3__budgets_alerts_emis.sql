-- Budget, alert, and EMI schema. See docs/database.md sections:
-- budgets, alerts, emis.

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
