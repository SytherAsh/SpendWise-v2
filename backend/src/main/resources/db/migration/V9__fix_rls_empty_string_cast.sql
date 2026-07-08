-- Fixes a latent bug in V5/V6's RLS policies: `set_config(name, value, true)` (transaction-local)
-- reverts a *custom* GUC to an empty string '' when its owning transaction ends -- not to NULL,
-- because Postgres treats app.current_user_id as a placeholder parameter with no compiled-in
-- default. Confirmed directly:
--   BEGIN; SELECT set_config('app.current_user_id', '<uuid>', true); COMMIT;
--   SELECT current_setting('app.current_user_id', true); -- returns '', not NULL
--
-- Under HikariCP connection pooling this bites the very next request to reuse that physical
-- connection: any query evaluating `current_setting('app.current_user_id', true)::uuid` now casts
-- '' to uuid and throws `invalid input syntax for type uuid: ""` instead of the intended
-- safe-fail-deny (NULL). Because FORCE ROW LEVEL SECURITY + multiple permissive policies means
-- every applicable policy expression is evaluated (they OR together), this crash happens even on
-- queries that a *different*, correctly-scoped policy (e.g. V6's auth-lookup policy) would have
-- allowed -- discovered via the login path (findByPhone) failing on the second call served by a
-- warmed-up connection, not the first.
--
-- Fix: NULLIF(..., '') turns the leftover '' back into a real NULL before the cast, restoring the
-- documented "missing session variable -> NULL -> safe-fail deny" behavior in every case, not just
-- a connection's first use.

ALTER POLICY "Users can only access own user row" ON users
    USING (id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own user_preferences" ON user_preferences
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own user_consent" ON user_consent
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own refresh_tokens" ON refresh_tokens
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own device_api_keys" ON device_api_keys
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own transactions" ON transactions
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own budgets" ON budgets
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own alerts" ON alerts
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own emis" ON emis
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own recommendations" ON recommendations
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own chatbot_sessions" ON chatbot_sessions
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own chatbot_conversations" ON chatbot_conversations
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own admin_logs" ON admin_logs
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);

ALTER POLICY "Users can only access own transaction_categories" ON transaction_categories
    USING (EXISTS (
        SELECT 1 FROM transactions
        WHERE transactions.id = transaction_categories.transaction_id
        AND transactions.user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
    ));

ALTER POLICY "Users can only access own ml_corrections" ON ml_corrections
    USING (EXISTS (
        SELECT 1 FROM transactions
        WHERE transactions.id = ml_corrections.transaction_id
        AND transactions.user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
    ));

-- V6's auth-lookup policies compare as text (no cast), so they can't crash the same way -- but
-- their "IS NOT NULL" guard is imprecise for the same reason: a leftover '' from a previous
-- transaction reads as "not null" too. Tightened for correctness even though phone/google_id are
-- never '' in practice, so this was not separately crash-prone.
ALTER POLICY "Auth module may look up a user by their own verified identifier" ON users
    USING (
        NULLIF(current_setting('app.auth_lookup_identifier', true), '') IS NOT NULL
        AND (
            phone = current_setting('app.auth_lookup_identifier', true)
            OR google_id = current_setting('app.auth_lookup_identifier', true)
        )
    );

ALTER POLICY "Auth module may look up a refresh token by its own hash" ON refresh_tokens
    USING (
        NULLIF(current_setting('app.auth_lookup_token_hash', true), '') IS NOT NULL
        AND token_hash = current_setting('app.auth_lookup_token_hash', true)
    );
