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
- **Deletion**: User can request account deletion; admin purges all data (transactions, preferences, corrections, chatbot history) within a reasonable timeframe. **Exception:** `admin_logs` rows are retained with `user_id` set to `NULL` for audit integrity — if `action` or `target_resource` fields contain identifying strings referencing the deleted user, those fields must be scrubbed or the row deleted as part of the erasure process
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
