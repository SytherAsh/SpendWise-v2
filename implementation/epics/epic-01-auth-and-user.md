# Epic 1 — Auth & User Onboarding (Backend)

**Working Milestone:** Starting from a fresh DB, demo the full flow via curl/Postman:
send OTP → verify OTP and receive a 7-day JWT + refresh token → hit `/users/me/onboarding`
and receive a device API key (shown once) with a `user_consent` row written → fetch and
update `/users/me` and `/users/me/preferences` → refresh the token and confirm rotation →
present a stale/replayed refresh token and confirm all tokens for that user are revoked →
confirm an admin-signed token is rejected on user routes and a user-signed token is
rejected on the (stub) admin route.

---

### E1-S1 — Firebase-Backed OTP & Google Login, JWT Issuance

Delegates credential verification to Firebase; SpendWise's own JWT is the session of
record (per `CLAUDE.md`'s Auth pattern note — Firebase ID tokens are never used directly
as the backend session).

**Independently testable via:** curl against `/auth/*` endpoints.

#### E1-S1-T1 — Firebase Admin SDK integration

- **Objective:** Verify phone-OTP and Google credentials server-side via Firebase Admin SDK
  so the backend never trusts a client-asserted identity.
- **Expected Deliverable:** `com.spendwise.auth` service wrapping Firebase Admin SDK calls
  to verify an OTP session / Google ID token and resolve it to a Firebase UID.
- **Definition of Done:**
  - A valid Firebase ID token resolves to a UID; an invalid/expired one throws a typed exception mapped to 400/401.
  - `FIREBASE_PROJECT_ID` / `FIREBASE_PRIVATE_KEY` read from env, never hardcoded.
- **Required Tests:** Unit tests mocking the Firebase Admin SDK: valid token → UID; expired token → exception; malformed token → exception.
- **Estimated Complexity:** Medium
- **Depends on:** E0-S1-T1
- **Grounded in:** `CLAUDE.md` Auth pattern note, `docs/spec/decisions.md` ADR-007, `docs/operations/deployment.md` Firebase env vars.

#### E1-S1-T2 — JWT issuance & refresh-token storage

- **Objective:** Issue SpendWise's own 7-day access JWT and a rotating refresh token on
  successful Firebase verification.
- **Expected Deliverable:** JWT signing service using `JWT_SECRET`; refresh token generation
  + SHA-256 hashing before persisting to `refresh_tokens` (raw token never stored).
- **Definition of Done:**
  - Issued access token has 7-day (604800s) expiry claim.
  - `refresh_tokens` row is written with `token_hash`, `expires_at` = issuance + 30 days (per `docs/spec/security.md`), `revoked_at` null.
  - Raw refresh token never appears in any log line or DB column.
- **Required Tests:** Unit test asserting `expiresIn` claim = 604800; unit test asserting the persisted `token_hash` is the SHA-256 of the returned raw token and not equal to it.
- **Estimated Complexity:** Medium
- **Depends on:** E1-S1-T1, E0-S2-T2
- **Grounded in:** `docs/spec/security.md` Authentication & Authorization; `docs/spec/database.md` `refresh_tokens`; `docs/spec/api.md` `/auth/otp/verify` response schema.

#### E1-S1-T3 — `POST /auth/otp/send` + `/auth/otp/verify`

- **Objective:** Expose the OTP send/verify endpoints with rate limiting.
- **Expected Deliverable:** Both endpoints, plus a per-phone-number rate limiter (max 5
  requests/hour) on `/auth/otp/send`.
- **Definition of Done:**
  - 6th OTP-send request for the same phone number within an hour returns `429`.
  - `/auth/otp/verify` with an expired/invalid OTP returns `400`.
  - Success response matches the exact schema in `docs/spec/api.md` (`accessToken`, `refreshToken`, `expiresIn`, `user.{id,phone,email}` with `email: null` for phone-only users).
- **Required Tests:** Integration tests: happy path returns matching schema; 6th send in an hour → 429; invalid OTP → 400.
- **Estimated Complexity:** Medium
- **Depends on:** E1-S1-T2
- **Grounded in:** `docs/spec/api.md` `/auth` table + `POST /auth/otp/verify — Response` schema; `docs/spec/security.md` Rate Limiting; `docs/spec/requirements.md` non-functional (n/a) — this task also satisfies the OTP rate-limit item in the API Security Checklist.

#### E1-S1-T4 — `POST /auth/google`

- **Objective:** Support Google OAuth login, reusing the JWT issuance path from E1-S1-T2.
- **Expected Deliverable:** Endpoint verifying a Google ID token via Firebase, creating a
  user row if `google_id` doesn't yet exist (respecting `chk_user_identifier` and the
  partial unique indexes on `users`), then issuing tokens.
- **Definition of Done:**
  - First-time Google login creates a `users` row with `google_id` set, `phone` null.
  - Repeat login with the same Google account reuses the same `users.id`.
- **Required Tests:** Integration test: first login creates a user; second login with same Google ID returns the same `user.id`.
- **Estimated Complexity:** Medium
- **Depends on:** E1-S1-T1, E1-S1-T2
- **Grounded in:** `docs/spec/api.md` `/auth` table; `docs/spec/database.md` `users` unique indexes.

#### E1-S1-T5 — `POST /auth/token/refresh` with rotation + replay detection

- **Objective:** Implement silent rotation and the replay-attack safeguard exactly as
  specified.
- **Expected Deliverable:** Endpoint that atomically revokes the incoming refresh token and
  issues a new one; if the incoming token was already rotated (i.e., its row's
  `revoked_at` is already set), revoke **all** refresh tokens for that user.
- **Definition of Done:**
  - Normal refresh: old token's `revoked_at` set, new token issued, new `expires_at` = now + 30 days.
  - Replay case: presenting a token a second time (after it was already rotated once) sets `revoked_at` on every `refresh_tokens` row for that user.
  - A revoked token cannot be used to refresh again (`401`).
- **Required Tests:** Integration tests: (1) normal rotation succeeds and old token stops working; (2) replaying an already-rotated token revokes all sessions for that user, verified by asserting a second, unrelated valid token for the same user also stops working afterward.
- **Estimated Complexity:** Large
- **Depends on:** E1-S1-T2
- **Grounded in:** `docs/spec/security.md` Authentication & Authorization (Rotation paragraph); `docs/operations/testing.md` Auth unit tests (replay attack detection).

#### E1-S1-T6 — `POST /auth/logout`

- **Objective:** Revoke the current session's refresh token on logout.
- **Expected Deliverable:** Endpoint setting `revoked_at = NOW()` on the presented refresh
  token's row.
- **Definition of Done:** Logged-out refresh token can no longer be used to refresh; the
  still-valid 7-day access token is explicitly *not* invalidated (per spec, acceptable MVP window).
- **Required Tests:** Integration test: logout then attempt refresh with the same token → `401`.
- **Estimated Complexity:** Small
- **Depends on:** E1-S1-T5
- **Grounded in:** `docs/spec/security.md` "Logout" paragraph; `docs/spec/api.md` `/auth` table.

#### E1-S1-T7 — User JWT auth filter (protected-route guard)

- **Objective:** Build the Spring Security filter that validates `Authorization: Bearer`
  against `JWT_SECRET` on every protected route, and explicitly rejects `ADMIN_JWT_SECRET`-signed tokens.
- **Expected Deliverable:** A servlet filter/interceptor applied to all `/api/v1/**` routes
  except the public `/auth/*` and `/health` routes.
- **Definition of Done:**
  - Missing/invalid/expired Bearer token → `401` on any protected route.
  - A token signed with `ADMIN_JWT_SECRET` is rejected by this filter even if it carries a role claim.
- **Required Tests:** Unit test: `JWT_SECRET`-signed token passes; `ADMIN_JWT_SECRET`-signed token is rejected by this filter (per `docs/operations/testing.md` Auth unit tests — JWT secret routing).
- **Estimated Complexity:** Medium
- **Depends on:** E1-S1-T2
- **Grounded in:** `CLAUDE.md` security invariants; `docs/spec/security.md` Authentication & Authorization; `docs/operations/testing.md` Auth unit tests.

---

### E1-S2 — Admin Auth Isolation

**Independently testable via:** a unit test presenting each secret-signed token to each filter.

#### E1-S2-T1 — Admin JWT filter (fully independent of user filter)

- **Objective:** Build a second, completely independent Spring Security filter chain for
  `/api/v1/admin/**` that validates only `ADMIN_JWT_SECRET`-signed tokens.
- **Expected Deliverable:** A separate filter/security config bean scoped to admin routes;
  no shared validation code path with E1-S1-T7 beyond the JWT library itself.
- **Definition of Done:**
  - A `JWT_SECRET`-signed user token (even with an admin role claim, if one were added) is rejected at any `/admin/*` route with `401`/`403`.
  - An `ADMIN_JWT_SECRET`-signed token is rejected by the user filter on any non-admin route.
- **Required Tests:** Integration test per `docs/operations/testing.md`: "Admin route rejects a `JWT_SECRET`-signed token even with an admin role claim."
- **Estimated Complexity:** Medium
- **Depends on:** E1-S1-T7
- **Grounded in:** `CLAUDE.md` security invariants (Admin authentication paragraph); `docs/spec/security.md` Authentication & Authorization; `docs/operations/testing.md`.

---

### E1-S3 — User Profile & Preferences

**Independently testable via:** curl against `/users/*` with a valid user JWT.

#### E1-S3-T1 — `GET /users/me`, `PUT /users/me`

- **Objective:** Serve and update the authenticated user's profile.
- **Expected Deliverable:** Both endpoints, backed by a response DTO (not the raw entity).
- **Definition of Done:** Response contains no field beyond what's user-facing; update
  persists and is reflected on next `GET`.
- **Required Tests:** Integration test: update then re-fetch reflects the change.
- **Estimated Complexity:** Small
- **Depends on:** E1-S1-T7
- **Grounded in:** `docs/spec/api.md` `/users` table.

#### E1-S3-T2 — `GET/PUT /users/me/preferences`

- **Objective:** Serve and update alert channel preferences and selected apps/banks.
- **Expected Deliverable:** Both endpoints against `user_preferences`.
- **Definition of Done:** `alert_channels` JSONB round-trips correctly; `selected_apps`/`selected_banks` arrays persist.
- **Required Tests:** Integration test: PUT with `{"push": false, "email": true}` then GET reflects it.
- **Estimated Complexity:** Small
- **Depends on:** E1-S1-T7, E0-S2-T2
- **Grounded in:** `docs/spec/api.md` `/users` table; `docs/spec/database.md` `user_preferences`.

#### E1-S3-T3 — `POST /users/me/onboarding`

- **Objective:** Record DPDP consent, persist onboarding questionnaire data, and register
  the device API key — the single most security-sensitive user endpoint outside `/ingest`.
- **Expected Deliverable:** Endpoint that (a) writes a `user_consent` row with a snapshot of
  the exact consent text shown, (b) writes onboarding data to `user_preferences`
  (selected apps/banks, monthly spend estimate), (c) generates a device API key, hashes it,
  stores the hash in `device_api_keys`, and returns the **raw** key exactly once in the
  response body.
- **Definition of Done:**
  - Response matches `docs/spec/api.md`'s onboarding response schema exactly (`deviceApiKey`, `user.{id,phone}`).
  - The raw key is never persisted anywhere — only `key_hash` is stored.
  - A second call to this endpoint for the same user does not silently leak or regenerate an unintended duplicate active key without an explicit reason (define and document the re-onboarding behavior — e.g., issue a new device key and leave prior ones active, since multi-device is allowed per `docs/operations/user_flows.md` Multi-Device Flow).
- **Required Tests:** Integration test: response contains raw key; DB contains only the hash; a database query for the raw key string returns nothing.
- **Estimated Complexity:** Large
- **Depends on:** E1-S3-T1, E0-S2-T2
- **Grounded in:** `docs/spec/api.md` `/users` table + onboarding response schema; `docs/spec/security.md` Device API Key section; `docs/operations/user_flows.md` Onboarding flow steps 2-3; `docs/spec/requirements.md` DPDP compliance.

---

### E1-S4 — Device API Key Validation Service

**Independently testable via:** unit tests against the validation service directly (no HTTP layer needed yet — consumed by Epic 3's `/ingest` endpoint).

#### E1-S4-T1 — Device key hash-and-validate service

- **Objective:** Build the reusable service that `/ingest` (Epic 3) will call to validate
  the `X-Device-Key` header.
- **Expected Deliverable:** A service method: given a raw key + `user_id`, hash the key and
  check `SELECT WHERE user_id = ? AND is_active = TRUE AND key_hash = ?`; update
  `last_used_at` on success.
- **Definition of Done:**
  - Valid active key for the correct user → success, `last_used_at` updated.
  - Valid key hash but wrong `user_id`, or `is_active = false` → rejected.
- **Required Tests:** Unit tests covering all 4 cases from `docs/operations/testing.md` Ingest dual-auth validation (valid → pass; missing → reject; inactive → reject; mismatched user_id → reject) — this task covers the device-key half; the JWT half and the composed guard are Epic 3's concern.
- **Estimated Complexity:** Medium
- **Depends on:** E1-S3-T3
- **Grounded in:** `docs/spec/database.md` `device_api_keys`; `docs/spec/security.md` Device API Key validation flow; `docs/operations/testing.md` Ingest unit tests.

---

## Parallel Execution within Epic 1

- E1-S1-T3 (OTP endpoints) and E1-S1-T4 (Google endpoint) are independent once E1-S1-T1/T2 land — build in parallel.
- E1-S2-T1 (admin filter) has no dependency on E1-S3/E1-S4 and can be built as soon as E1-S1-T7 lands.
- E1-S3-T1/T2 (profile/preferences) are independent of each other.
- Epic 1 as a whole is parallelizable with all of Epic 2 (see `../DEPENDENCY-GRAPH.md`).
