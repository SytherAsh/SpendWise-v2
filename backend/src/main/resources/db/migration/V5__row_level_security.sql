-- Row-Level Security backstop. See docs/security.md "Supabase Row-Level Security".
--
-- Spring Boot connects with an elevated DB role that owns these tables, and
-- Postgres table owners bypass RLS by default -- FORCE ROW LEVEL SECURITY
-- makes the policies apply even to the owning/connecting role, so the
-- documented "missing session variable -> safe-fail deny" behavior is real,
-- not just theoretical. Every user-context query must call:
--   SELECT set_config('app.current_user_id', '<authenticated-user-uuid>', true);
-- before running, or RLS-protected queries return zero rows.
--
-- NOTE: this session-variable mechanism denies cross-user access outright,
-- which is correct for all regular user-facing queries. The Admin module
-- (Epic 11) needs cross-user reads (e.g. GET /admin/users) -- how that
-- coexists with FORCE RLS on these tables is an open question deferred to
-- Epic 11, not resolved by this migration. Flagging here rather than
-- inventing an unspecified bypass mechanism now.

-- users: the row's own `id` *is* the user identity (no separate user_id column).
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own user row"
ON users
FOR ALL
USING (id = current_setting('app.current_user_id', true)::uuid);

ALTER TABLE user_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_preferences FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own user_preferences"
ON user_preferences
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);

ALTER TABLE user_consent ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_consent FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own user_consent"
ON user_consent
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);

ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own refresh_tokens"
ON refresh_tokens
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);

ALTER TABLE device_api_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_api_keys FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own device_api_keys"
ON device_api_keys
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);

ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own transactions"
ON transactions
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);

ALTER TABLE budgets ENABLE ROW LEVEL SECURITY;
ALTER TABLE budgets FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own budgets"
ON budgets
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);

ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own alerts"
ON alerts
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);

ALTER TABLE emis ENABLE ROW LEVEL SECURITY;
ALTER TABLE emis FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own emis"
ON emis
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);

ALTER TABLE recommendations ENABLE ROW LEVEL SECURITY;
ALTER TABLE recommendations FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own recommendations"
ON recommendations
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);

ALTER TABLE chatbot_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE chatbot_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own chatbot_sessions"
ON chatbot_sessions
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);

ALTER TABLE chatbot_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE chatbot_conversations FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own chatbot_conversations"
ON chatbot_conversations
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);

-- admin_logs.user_id is nullable (system-wide events have no user). This
-- policy correctly hides system-wide rows (user_id IS NULL) and other
-- users' rows from any regular user-scoped session -- admin_logs is
-- Admin-only per docs/architecture.md, so no regular user should see any
-- row here regardless.
ALTER TABLE admin_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own admin_logs"
ON admin_logs
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);

-- transaction_categories and ml_corrections have no user_id column --
-- scoped to a transaction instead, per docs/security.md's join-based
-- policy pattern.
ALTER TABLE transaction_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE transaction_categories FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own transaction_categories"
ON transaction_categories
FOR ALL
USING (EXISTS (
    SELECT 1 FROM transactions
    WHERE transactions.id = transaction_categories.transaction_id
    AND transactions.user_id = current_setting('app.current_user_id', true)::uuid
));

ALTER TABLE ml_corrections ENABLE ROW LEVEL SECURITY;
ALTER TABLE ml_corrections FORCE ROW LEVEL SECURITY;
CREATE POLICY "Users can only access own ml_corrections"
ON ml_corrections
FOR ALL
USING (EXISTS (
    SELECT 1 FROM transactions
    WHERE transactions.id = ml_corrections.transaction_id
    AND transactions.user_id = current_setting('app.current_user_id', true)::uuid
));
