# Security & Data Privacy

## Encryption

| Layer | Standard |
|---|---|
| Data in transit | TLS 1.2+ (HTTPS) on all API communication — no plain HTTP permitted |
| Data at rest | AES-256 for sensitive fields (transaction data, SMS text, user PII) in Supabase |
| Passwords / secrets | Never stored — Firebase handles OTP + Google OAuth credential management |

## Authentication & Authorization

- **Firebase Authentication** manages phone OTP and Google OAuth
- On successful login: Spring Boot issues a JWT (7-day access token + refresh token)
- Refresh token is rotated on every use
- All protected API endpoints require `Authorization: Bearer <token>`
- Admin endpoints require a separate admin JWT — regular user tokens are rejected at the route level

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

Users must explicitly accept a single consent screen before the app proceeds. Consent covers all four purposes:

1. **SMS read access** — required to read financial transaction SMS on-device
2. **Server-side data storage** — transaction data stored encrypted on SpendWise servers
3. **ML model improvement** — anonymized data used to retrain the categorization model
4. **Debugging purposes** — raw SMS text stored server-side to improve SMS parser accuracy

Declining consent blocks access to the app (SMS is the core feature).

### Data Subject Rights

- **Access**: Users can view all their data via the app and dashboard
- **Deletion**: User can request account deletion; admin purges all data (transactions, preferences, corrections, chatbot history) within a reasonable timeframe
- **Portability**: Users can export all their data as PDF or CSV at any time

### Privacy Policy

A privacy policy document must be:
- Linked within the consent screen
- Accessible from app settings at any time
- Written in plain English (not legalese)

## Data Handling Rules

| Data | Storage | Who can access |
|---|---|---|
| Parsed transaction fields | Supabase (encrypted) | User, Admin |
| Raw SMS text (`sms_raw_text`) | Supabase (encrypted) | Admin only — never returned by user-facing API |
| Bank statement PDF | Parsed and discarded after ingestion — not stored permanently | — |
| Chatbot conversation history | Supabase | User (own sessions only), Admin |
| ML corrections | Supabase | Admin, ML training pipeline |

## API Security Checklist

- [ ] All routes use HTTPS
- [ ] JWT validation on every protected route
- [ ] Admin routes reject non-admin tokens
- [ ] `/ingest` validates both JWT and device API key
- [ ] Rate limiting on `/auth/otp/send` and `/auth/otp/verify`
- [ ] `sms_raw_text` field excluded from all user-facing API responses
- [ ] No secrets committed to Git (see `.gitignore`)
- [ ] Supabase row-level security (RLS) policies enforce user data isolation

## Supabase Row-Level Security

Every table with a `user_id` column must have an RLS policy:

```sql
-- Example for transactions table
CREATE POLICY "Users can only access own transactions"
ON transactions
FOR ALL
USING (auth.uid() = user_id);
```

This ensures that even if a query bug omits a `WHERE user_id = ?` clause, Supabase enforces isolation at the database level.
