-- Identity and session tables. See docs/database.md sections:
-- users, user_preferences, user_consent, refresh_tokens, device_api_keys.

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

CREATE TABLE user_preferences (
    user_id                UUID REFERENCES users(id) ON DELETE CASCADE,
    alert_channels         JSONB NOT NULL DEFAULT '{"push": true, "email": true}',
    selected_apps          TEXT[],    -- e.g. ["paytm", "gpay"]
    selected_banks         TEXT[],    -- e.g. ["SBI"]
    monthly_spend_estimate NUMERIC,   -- from onboarding
    PRIMARY KEY (user_id)
);

CREATE TABLE user_consent (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    consented_at TIMESTAMP NOT NULL DEFAULT NOW(),
    app_version  VARCHAR,           -- version of the app shown at consent time
    consent_text TEXT NOT NULL      -- snapshot of the exact consent text shown to the user
);

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

CREATE TABLE device_api_keys (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_hash      VARCHAR NOT NULL,     -- bcrypt/SHA-256 hash of raw device key
    registered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at  TIMESTAMP,            -- updated on every successful /ingest auth; null until first use
    is_active     BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_device_keys_user ON device_api_keys(user_id, is_active);
