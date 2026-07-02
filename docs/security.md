# Security & Data Privacy

## Encryption

| Layer | Standard |
| --- | --- |
| Data in transit | TLS 1.2+ (HTTPS) on all API communication — no plain HTTP permitted |
| Data at rest | AES-256 full-disk encryption via Supabase (entire database volume); no automatic field-level encryption — column-level protection relies on access control and response filtering, not ciphertext |
| Passwords / secrets | Never stored — Firebase handles OTP + Google OAuth credential management |

## Authentication & Authorization

- **Firebase Authentication** manages phone OTP and Google OAuth
- On successful login: Spring Boot issues a 7-day access token and a refresh token
- Refresh token lifetime: 30 days from issuance; the window resets on every silent rotation
- Refresh tokens are stored server-side as SHA-256 hashes in the `refresh_tokens` table — the raw token is never persisted
- Rotation: each `/auth/token/refresh` call atomically revokes the incoming token and issues a new one. If a token that was already rotated is presented again, the server treats it as a replay attack and revokes **all** refresh tokens for that user, forcing re-authentication on all devices
- Logout: `/auth/logout` sets `revoked_at = NOW()` on the token row; the access token remains valid until its 7-day natural expiry (acceptable window for MVP)
- All protected API endpoints require `Authorization: Bearer <token>`
- Admin endpoints require a separate admin JWT signed with **`ADMIN_JWT_SECRET`** — a completely different secret from `JWT_SECRET`. The admin auth filter validates only `ADMIN_JWT_SECRET`-signed tokens; a valid user token signed with `JWT_SECRET` is rejected at admin routes even if it carries an admin role claim

### Device API Key (`/ingest` endpoint)

The `/api/v1/ingest` endpoint accepts parsed transactions from the Android app. In addition to the user JWT, it requires a device-level API key:

- Generated and registered when the user completes onboarding
- Tied to the specific device + user combination
- Prevents spoofed transaction injection from non-app clients
- Stored as a hashed value server-side (see `device_api_keys` table in `docs/database.md`)

**Validation flow:** raw key arrives in a request header → hash it server-side → `SELECT WHERE user_id = ? AND is_active = TRUE AND key_hash = ?` → reject with 401 if not found.

### Rate Limiting

Applied to authentication endpoints:

- OTP send: max 5 requests per phone number per hour
- Login attempts: max 10 per IP per 15 minutes
- Prevents brute force and OTP abuse

## DPDP Act 2023 Compliance (India)

The Digital Personal Data Protection Act 2023 applies to SpendWise since it collects personal data (SMS content, financial transactions) from Indian users.

### Consent at Onboarding

Users must explicitly accept a single consent screen before the app proceeds. Consent covers all three purposes:

1. **SMS read access** — required to read financial transaction SMS on-device
2. **Server-side data storage** — transaction data stored encrypted on SpendWise servers
3. **ML model improvement** — anonymized data used to retrain the categorization model

Declining consent blocks access to the app (SMS is the core feature).

### Data Subject Rights

- **Access**: Users can view all their data via the app and dashboard
- **Deletion**: User can request account deletion; admin purges all data (transactions, preferences, corrections, chatbot history) within a reasonable timeframe. **Exception:** `admin_logs` rows are retained with `user_id` set to `NULL` for audit integrity — if the `event_type` value or any value within the `payload` JSONB contains identifying strings referencing the deleted user, those fields must be scrubbed or the row deleted as part of the erasure process
- **Portability**: Users can export all their data as PDF or CSV at any time

### Privacy Policy

A privacy policy document must be:

- Linked within the consent screen
- Accessible from app settings at any time
- Written in plain English (not legalese)

## Data Handling Rules

| Data | Storage | Who can access |
| --- | --- | --- |
| Parsed transaction fields | Supabase (encrypted at rest) | User, Admin |
| Raw SMS text | Never transmitted to or stored by the backend — parsed on-device only; only structured fields reach the server | — |
| Bank statement PDF | Parsed server-side and immediately discarded — not stored permanently | — |
| Chatbot conversation history | Supabase | User (own sessions only), Admin |
| ML corrections | Supabase | Admin, ML training pipeline |
| Consent records (`user_consent`) | Supabase (encrypted at rest) | Admin only — DPDP Act 2023 compliance audit |
| Session credentials (`refresh_tokens`) | Supabase — SHA-256 hash only; raw token never persisted | Auth module only |
| Device API keys (`device_api_keys`) | Supabase — SHA-256 hash only; raw key never persisted | Ingest module only |

## API Security Checklist

- [ ] All routes use HTTPS
- [ ] JWT validation on every protected route
- [ ] Admin routes reject non-admin tokens
- [ ] `/ingest` validates both JWT and device API key
- [ ] Rate limiting on `/auth/otp/send` and `/auth/otp/verify`
- [ ] `sms_raw_text` field excluded from all user-facing API responses
- [ ] No secrets committed to Git (see `.gitignore`)
- [ ] Supabase row-level security (RLS) policies enforce user data isolation
- [ ] Admin JWT validated against `ADMIN_JWT_SECRET` only — not `JWT_SECRET`; the two auth filters are completely independent
- [ ] Refresh token revocation enforced server-side on logout (`refresh_tokens.revoked_at`)
- [ ] FastAPI ML service requires `X-Internal-Key` header on every request — not publicly reachable

## Supabase Row-Level Security

Every table with a `user_id` column must have an RLS policy. These policies act as a backstop against application-layer query bugs — they do not replace explicit `WHERE user_id = ?` scoping in every query.

**Service role key and RLS:** Spring Boot connects to Supabase using a service role key, which bypasses RLS by default. Supabase's `auth.uid()` function returns `null` under a service role connection, making `USING (auth.uid() = user_id)` ineffective. To enable RLS as a working backstop, Spring Boot must set a transaction-scoped session variable before each user-context query:

```sql
-- Set at the start of every transaction that accesses user-scoped data.
-- 'true' scopes the variable to the current transaction only — safe with connection pools.
SELECT set_config('app.current_user_id', '<authenticated-user-uuid>', true);
```

RLS policies reference this variable instead of `auth.uid()`:

```sql
CREATE POLICY "Users can only access own transactions"
ON transactions
FOR ALL
USING (user_id = current_setting('app.current_user_id', true)::uuid);
-- Second arg 'true': returns null (deny) instead of raising an error if the variable is not set.
```

With this approach, a missing session variable causes the policy to evaluate to `null` — which is a safe-fail deny. RLS policies defined this way also enforce isolation for direct database access via the Supabase dashboard or any future direct-connection path, not just Spring Boot requests.

**Tables without a `user_id` column:** `transaction_categories` and `ml_corrections` are scoped to a transaction rather than a user directly. Their RLS policy must join through `transactions` instead of comparing `user_id` directly:

```sql
CREATE POLICY "Users can only access own transaction_categories"
ON transaction_categories
FOR ALL
USING (EXISTS (
    SELECT 1 FROM transactions
    WHERE transactions.id = transaction_categories.transaction_id
    AND transactions.user_id = current_setting('app.current_user_id', true)::uuid
));
```

The same join pattern applies to `ml_corrections` (via its own `transaction_id` column).

### Auth login lookup policy (V6, added during Epic 1 implementation)

The `users` policy above only matches when `id = current_setting('app.current_user_id')`. Login (`/auth/otp/verify`, `/auth/google`) must look up a user by phone or google_id *before* that variable exists, so a second, narrowly-scoped, SELECT-only permissive policy was added in `V6__auth_lookup_policy.sql`: it exposes a row only when the caller first sets `app.auth_lookup_identifier` to the exact phone number or google_id being searched for — an identifier the caller inherently already holds, having just completed Firebase-verified OTP/Google authentication with it. This grants no INSERT/UPDATE/DELETE rights beyond what the primary policy already allows and does not enable browsing or enumeration.

The identical gap exists on `refresh_tokens`: `/auth/token/refresh` and `/auth/logout` receive a raw refresh token and must find its row **by `token_hash`** before knowing which user it belongs to. V6 adds the same pattern there, gated by `app.auth_lookup_token_hash` — a row is visible only to a caller who already supplies the exact SHA-256 hash, which requires already possessing the valid raw token.

Approved by project owner 2026-07-02 as a deviation from this document's original RLS design; see `docs/database.md` `device_api_keys` section addendum.

### Cross-user reads for background jobs (added during Epic 4 implementation)

Every RLS-protected table has `FORCE ROW LEVEL SECURITY` (`V5__row_level_security.sql`), and Spring Boot's primary connection uses `spendwise_app`, a plain role with no superuser/`BYPASSRLS` attribute — so a query with no `app.current_user_id` set returns zero rows, by design. `V5`'s own header comment flagged this as a real gap: some background jobs are inherently cross-user (e.g. "for all users" appears throughout `docs/architecture.md`'s Background Jobs table), and there was no mechanism for that.

**Resolution:** a second Postgres role, `spendwise_jobs` (`backend/db-init/02-jobs-role.sql`), created with the `BYPASSRLS` attribute and granted membership in `spendwise_app` (so it inherits privileges on every table `spendwise_app` owns, including ones created by later migrations). Spring Boot wires this up as a **second connection pool** (`com.spendwise.common.db.JobsDataSourceConfig`) alongside the normal `spendwise_app`-backed one:

- The default `DataSource`/`JdbcTemplate` — used by every controller, service, and repository in the app via unqualified injection — is a bean explicitly defined in `JobsDataSourceConfig` (not left to auto-configuration — see the incident note below for why that distinction is load-bearing) and stays fully RLS-enforced, exactly as before.
- A second `JdbcTemplate` bean, injected only via `@Qualifier("jobsJdbcTemplate")`, bypasses RLS entirely. Only `@Scheduled` job classes may use it, and only for the specific cross-user read each job needs (`TransactionRepository.findAllUncategorized`, `MlCorrectionRepository.findAllCorrections` — both in `com.spendwise.transaction`, exposed to `com.spendwise.categorization`'s jobs through the normal `TransactionService` interface, not a new cross-module dependency).
- First landed for E4-S3-T3 (categorization retry job) and E4-S3-T4 (ML retraining job); the same mechanism is available to Epic 5's alert evaluator and Epic 8's recommendation generator, both also described as system-wide jobs.

This is a real, audited exception scoped to exactly one role and exactly the job classes that explicitly request it — not a blanket RLS bypass. See `implementation/tracking/STATUS.md`'s Epic 4 close-out for the full context of why this was needed.

> **Local dev note:** `docker-entrypoint-initdb.d` scripts (`db-init/*.sql`) only run once, when a Postgres data volume is first created. An existing local `spendwise-postgres-data` volume created before `02-jobs-role.sql` was added won't have the `spendwise_jobs` role — run `docker compose down -v` (recreates the volume) or apply `02-jobs-role.sql` manually against the running container.

> **Incident (first real CI run, 2026-07-02) — the actual root cause, after two wrong guesses:** landing the second `DataSource` broke every integration test in the suite, not just ones touching a scheduled job — every request failed with `PSQLException: FATAL: password authentication failed for user "spendwise_jobs"`. Two earlier hypotheses (a `@Scheduled` job firing at startup; Hikari's background pool-warming retrying forever) were plausible from the timing alone (every failure was ~30s apart, matching Hikari's default `connectionTimeout`) but **wrong** — fixing both did not change the symptom. The real cause, found only once full application logs were captured: Spring Boot's auto-configured `JdbcTemplate` bean is conditional on `@ConditionalOnMissingBean(JdbcOperations.class)`. `jobsJdbcTemplate` is itself a `JdbcTemplate` (implements `JdbcOperations`), so its mere presence in the context silently disabled the auto-configured default entirely — leaving `jobsJdbcTemplate` as the **only** `JdbcTemplate` bean, so every unqualified injection across the whole app (every repository) silently received the `BYPASSRLS` connection instead of the RLS-enforced one. In CI this failed loudly because `spendwise_jobs` didn't exist in the test container; in an environment where the role DOES exist (real Supabase, per `db-init/02-jobs-role.sql`), the exact same bug would have "worked" — silently routing the entire application through the RLS-bypassing role. Fixed by defining the primary `JdbcTemplate` bean explicitly (`@Primary`) in `JobsDataSourceConfig` rather than relying on auto-configuration to guess correctly, plus a regression test (`ApplicationContextIntegrationTest.unqualifiedJdbcTemplateIsNotTheJobsBypassRlsPool`) asserting the unqualified `JdbcTemplate`'s `current_user` is never `spendwise_jobs`. **Lesson for any future second `DataSource`/`JdbcTemplate`/`NamedParameterJdbcTemplate`/etc. bean:** always define the primary one explicitly and `@Primary`-mark it — never assume Spring Boot's auto-configuration will keep deferring to "the original" once a second bean of the same type exists anywhere in the context. The `minimumIdle`/`initialDelay` tuning from the earlier (wrong) hypotheses was left in place — it's still correct, low-risk defensive practice, just not what fixed this.
