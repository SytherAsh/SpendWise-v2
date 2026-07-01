-- Recommendations, ML training data, admin audit log, and chatbot persistence.
-- See docs/database.md sections: recommendations, ml_corrections, admin_logs,
-- chatbot_sessions, chatbot_conversations.

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
-- Not redundant with idx_recs_user_category_active -- that index enforces per-category uniqueness;
-- this one provides the ordered list display path and covers global recs (category_id IS NULL) too.
CREATE INDEX idx_recs_user_active
    ON recommendations(user_id, generated_at DESC)
    WHERE is_dismissed = FALSE;

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

CREATE TABLE admin_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  VARCHAR NOT NULL,  -- 'parse_failure', 'model_retrain', 'sync_error', 'prediction_low_confidence'
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,  -- null for system-wide events (e.g. model_retrain)
    payload     JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_logs_event_type ON admin_logs(event_type, created_at DESC);
CREATE INDEX idx_admin_logs_user ON admin_logs(user_id, created_at DESC) WHERE user_id IS NOT NULL;

CREATE TABLE chatbot_sessions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    last_active_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chatbot_sessions_user ON chatbot_sessions(user_id, created_at DESC);

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
