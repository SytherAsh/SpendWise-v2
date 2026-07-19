# Deployment & Infrastructure

## Architecture Overview

SpendWise runs as two backend services + one frontend + one database:

| Component | Technology | Hosting |
| --- | --- | --- |
| Web Dashboard | Next.js (React) | Vercel (free tier) |
| Main Backend API | Spring Boot (Java 21) | Render / Railway / Fly.io (free tier) |
| ML Categorization Service | FastAPI (Python) | Render / Railway / Fly.io (free tier) |
| Database | PostgreSQL | Supabase (free tier) |
| Auth | Firebase Authentication | Firebase (free tier) |
| Error Tracking | Sentry | Sentry (free tier) |
| Uptime Monitoring | UptimeRobot | UptimeRobot (free tier) |
| Android Distribution | Firebase App Distribution | Firebase (free tier) |

> Free-tier platform for backend finalized during setup. Preference: always-on (not spin-down on inactivity) since background jobs run on a schedule.

## Environment Variables

All secrets are injected via environment variables — never hardcoded or committed to Git.

### Spring Boot Backend

```env
SUPABASE_URL=
SUPABASE_KEY=
FIREBASE_PROJECT_ID=
FIREBASE_PRIVATE_KEY=
FASTAPI_ML_URL=http://ml-service/
JWT_SECRET=
SENTRY_DSN=
EMAIL_SMTP_HOST=
EMAIL_SMTP_PORT=
EMAIL_SMTP_USER=
EMAIL_SMTP_PASS=
ADMIN_JWT_SECRET=
ADMIN_USERNAME=
ADMIN_PASSWORD_HASH=
ML_INTERNAL_KEY=
ML_LOW_CONFIDENCE_THRESHOLD=0.5
```

> `ADMIN_USERNAME`/`ADMIN_PASSWORD_HASH` (E11-S1-T1): a single seeded admin credential — never
> a regular user account with a role claim (CLAUDE.md). `ADMIN_PASSWORD_HASH` is a bcrypt hash of
> the real password, never the raw password itself, checked with Spring Security's
> `BCryptPasswordEncoder`. Both empty by default so admin login is simply unusable until
> explicitly configured, rather than shipping a guessable local default.

> `ML_LOW_CONFIDENCE_THRESHOLD` (E4-S3-T1): not specified elsewhere in this doc set — a
> `/predict` response below this confidence is treated the same as a failed call and left
> uncategorized for the categorization retry job (`docs/spec/architecture.md` Background Jobs
> table) rather than written to `transaction_categories`. Defaults to `0.5` if unset.
>
> **Background job schedules are not environment variables.** ADR-018 (2026-07-19,
> `docs/spec/decisions.md`) moved every job's schedule (previously `ML_RETRAIN_CRON` plus four
> hardcoded `@Scheduled` annotations) into the `job_schedules` table, editable at runtime from
> the admin portal's "Scheduled Jobs" page — no env var, no redeploy. See `docs/spec/database.md`
> "job_schedules" for the seeded defaults new environments start with.
>
> `SPRING_DATASOURCE_JOBS_USERNAME`/`_PASSWORD` (E4-S3-T3/T4) follow the same pattern as
> `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` above — internal JDBC role credentials for
> the second, `BYPASSRLS`-enabled connection pool background jobs use, not part of Supabase's
> client-SDK credential set, so omitted from this list too. See `docs/spec/security.md`
> "Cross-user reads for background jobs" and `backend/.env.example`.
>
> **No `FCM_SERVER_KEY` (removed during Epic 5 implementation):** the original epic spec
> (`implementation/epics/epic-05-budget-and-alerts.md` E5-S3-T1) named this var for a legacy FCM
> HTTP server-key client. Google has deprecated that legacy HTTP API in favor of the Admin SDK
> (service-account credentials), and this project already depends on `firebase-admin` for Auth's
> OTP/Google ID token verification (`FIREBASE_PROJECT_ID`/`FIREBASE_PRIVATE_KEY` above) — so
> Alerts' push dispatch reuses that same credential via a second `FirebaseMessaging` bean
> (`com.spendwise.auth.FirebaseConfig`) instead of introducing a second, deprecated auth
> mechanism and a duplicate env var. No functional gap: the existing two vars cover both uses.

> `FASTAPI_ML_URL` uses plain HTTP because `ml-service` resolves to the hosting platform's internal private network, not the public internet. This does not violate the TLS requirement. The `X-Internal-Key` header provides application-layer authentication on every request.

### FastAPI ML Service

```env
SUPABASE_URL=
SUPABASE_KEY=
SENTRY_DSN=
MODEL_PATH=./models/
ML_INTERNAL_KEY=
```

> **Model artifacts:** The trained scikit-learn model is committed to the repository at `MODEL_PATH`. Train locally, export the artifact, and commit it alongside service code. The service loads the model at startup. External model storage is not used at MVP.

### Next.js Frontend

```env
NEXT_PUBLIC_API_BASE_URL=
NEXT_PUBLIC_FIREBASE_API_KEY=
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=
NEXT_PUBLIC_FIREBASE_PROJECT_ID=
NEXT_PUBLIC_FIREBASE_APP_ID=
```

### Android (Firebase client config — added with Epic 9)

The Android app authenticates through the Firebase **client** SDK (phone OTP + Google
Sign-In), configured by `android/app/google-services.json`. That file is **gitignored**;
the Google Services Gradle plugin is applied conditionally on its presence, so CI (unit
tests only) builds without it, but a runnable local/release build requires it:

1. In the Firebase console (same project as the backend's `FIREBASE_PROJECT_ID` — the
   client-issued ID tokens must verify against the Admin SDK's project), register an
   Android app with package name `com.spendwise`.
2. Add the signing certificate fingerprints: **SHA-1** (Google Sign-In) and **SHA-256**
   (Play Integrity, used by Firebase phone auth). The debug keystore's fingerprints are
   per-machine (`keytool -list -v -keystore ~/.android/debug.keystore -alias
   androiddebugkey -storepass android`); release-keystore fingerprints are added at
   Epic 12 alongside Firebase App Distribution.
3. Enable the **Phone** and **Google** sign-in providers (enabling Google creates the web
   OAuth client whose id ships inside `google-services.json` as `default_web_client_id`).
   For emulator QA, add a fictional test phone number under Phone → "Phone numbers for
   testing".
4. Download `google-services.json` (after step 3, so it includes the OAuth client) into
   `android/app/`. Do not commit it.

## Version Control & Branching

Solo project — work directly on `main`; feature branches and pull requests are not required.

- **Main branch**: `main` — the working branch; commit completed units of work directly to it
- **Optional branches**: `feature/<short-description>`, `fix/<short-description>`, `chore/<short-description>` — available for isolating a risky or experimental change, not part of the routine flow
- CI tests run on every push to `main` (GitHub Actions) and should be green before deploying
- **Branch protection (recommended for `main`)**: prevent force pushes and prevent branch deletion; do **not** require pull requests (they would only get in the way of a solo direct-to-`main` flow)

## CI/CD Pipeline

### CI (Continuous Integration)

Runs automatically on every push to `main` (and on any optional pull request):

- Spring Boot unit tests: `./gradlew test` (JUnit 5 + Spring Boot Test)
- Spring Boot integration tests: `./gradlew integrationTest` — requires Docker in the CI runner; Testcontainers provisions a PostgreSQL container automatically (GitHub Actions `ubuntu-latest` includes Docker by default)
- FastAPI: `pytest` (unit tests + evaluation script)
- Android: `./gradlew test` (Kotlin unit tests for parser)
- E2E: golden path test on staging environment

### CD (Continuous Deployment)

- **Manual** — developer reviews CI results and deploys manually
- No automatic deployment to production on merge
- Deployment steps per service documented below

### Deploying Spring Boot Backend

```bash
./gradlew build -x test       # build JAR
# Upload JAR or push to hosting platform via CLI
# Set environment variables in platform dashboard
# Restart service
```

### Deploying FastAPI ML Service

**Before first deploy and after each approved retraining cycle:** commit the updated model artifact to the repository:

```bash
# Train locally and export the artifact
python training/train.py --output models/
git add models/
git commit -m "chore: update model artifact"
```

**Deploy:**

```bash
pip install -r requirements.txt
uvicorn api.main:app --host 0.0.0.0 --port 8000
# Or via Docker if platform supports it
```

The service loads the model from `MODEL_PATH` at startup. Model artifacts are version-controlled alongside service code — no external object storage at MVP. Future versions may migrate to an external store or model registry.

### Deploying Frontend

Vercel auto-deploys from Git on push to `main`. No manual step required for frontend.

### Distributing Android APK

```bash
./gradlew assembleRelease
# Upload .apk to Firebase App Distribution via Firebase CLI:
firebase appdistribution:distribute app/release/app-release.apk \
  --app <FIREBASE_APP_ID> \
  --groups testers
```

## Backend Service Communication

Spring Boot calls the FastAPI ML service internally via HTTP:

```http
POST http://ml-service/predict
Content-Type: application/json
X-Internal-Key: ${ML_INTERNAL_KEY}

{
  "recipient_name": "Swiggy",
  "upi_id": "swiggy@okicici",
  "bank": "ICICI",
  "transaction_mode": "UPI",
  "amount": -350.0,
  "note": null
}
```

Response:

```json
{
  "category_id": 7,
  "category_name": "Food / Dine Out",
  "confidence": 0.94
}
```

## Monitoring Setup

After deployment, configure:

1. **Sentry**: Add DSN to both Spring Boot and FastAPI environment variables
2. **UptimeRobot**: Add monitor pointing to `GET /api/v1/health` — set email alert
3. **Supabase**: Enable query performance insights in dashboard

## Health Check Endpoint

Spring Boot exposes:

```text
GET /api/v1/health
→ 200 OK  { "status": "healthy", "db": "connected", "ml": "reachable" }
```

UptimeRobot pings this every 5 minutes. The response checks both the Supabase database connection and the FastAPI ML service reachability.

> This is an operational endpoint not listed in the API reference (`docs/spec/api.md`). It is unauthenticated and intended for infrastructure monitoring only.
