-- Closes a gap in V5__row_level_security.sql discovered while implementing
-- Epic 1 (Auth & User Onboarding): the login path (OTP verify / Google login)
-- must find an existing user BY phone or google_id before any user is
-- authenticated -- there is no app.current_user_id yet, so V5's
-- `id = current_setting('app.current_user_id')` policy can never match
-- during this lookup, and under FORCE ROW LEVEL SECURITY the query would
-- always return zero rows (safe-fail deny), making login impossible.
--
-- Postgres SELECT policies are permissive and OR together, so this adds a
-- second, narrowly-scoped SELECT-only policy alongside the existing one: it
-- exposes a row only to a caller who first sets app.auth_lookup_identifier
-- to the exact phone number or google_id being searched for -- an identifier
-- the caller inherently already holds, since they just completed
-- Firebase-verified OTP/Google authentication with it. This grants no
-- INSERT/UPDATE/DELETE rights beyond what V5 already allows and does not
-- enable browsing or enumeration: a caller who doesn't already know the
-- exact identifier learns nothing.
--
-- Approved by project owner 2026-07-02 as a deviation from the frozen
-- docs/database.md / docs/security.md RLS design during E1-S1-T3/T4
-- implementation -- see the corresponding doc addendum in both files.
CREATE POLICY "Auth module may look up a user by their own verified identifier"
ON users
FOR SELECT
USING (
    current_setting('app.auth_lookup_identifier', true) IS NOT NULL
    AND (
        phone = current_setting('app.auth_lookup_identifier', true)
        OR google_id = current_setting('app.auth_lookup_identifier', true)
    )
);

-- Same gap, same fix, on refresh_tokens: /auth/token/refresh and /auth/logout
-- receive a raw refresh token and must find its row BY token_hash before
-- knowing which user it belongs to (that's the whole point of the lookup).
-- Exposing "a row with this exact SHA-256 hash exists" is safe: computing a
-- matching hash requires already possessing the valid raw refresh token, so
-- this grants no capability beyond what holding that token already implies.
CREATE POLICY "Auth module may look up a refresh token by its own hash"
ON refresh_tokens
FOR SELECT
USING (
    current_setting('app.auth_lookup_token_hash', true) IS NOT NULL
    AND token_hash = current_setting('app.auth_lookup_token_hash', true)
);
