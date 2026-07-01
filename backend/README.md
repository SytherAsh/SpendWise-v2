# Backend — Spring Boot (Java 21)

The main REST API for SpendWise. Modular monolith with 11 well-bounded modules.

## Tech

- **Language**: Java 21
- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL via Supabase (JDBC)
- **Auth**: Firebase Admin SDK (JWT validation)
- **Build**: Gradle
- **Tests**: JUnit 5 + Spring Boot Test

## Module Structure

```
src/main/java/com/spendwise/
├── auth/               OTP login, Google OAuth, JWT issuance and refresh
├── user/               User profiles, preferences, onboarding
├── ingest/             Receives parsed transactions from Android app
├── transaction/        Storage, retrieval, filtering, pagination
├── categorization/     Calls FastAPI ML service, handles corrections
├── budget/             Budget CRUD, progress tracking, threshold rules
├── alerts/             Alert evaluation engine, push + email dispatch
├── recommendations/    LLM-powered spending one-liners
├── chatbot/            Context-aware LLM integration, session management
├── analytics/          Read-only aggregations, PDF/CSV export
└── admin/              Admin portal endpoints, system monitoring
```

## Running Locally

```bash
# Start the local Postgres substitute (see docker-compose.yml for why this
# isn't the real Supabase project — no live project exists yet)
docker compose up -d

# Set environment variables (copy from docs/deployment.md); defaults already
# point at the local Postgres above, so this step is optional for local dev
cp .env.example .env

# Build and run — Flyway migrates the schema automatically on startup
./gradlew bootRun
```

API available at `http://localhost:8080/api/v1/`

Local Postgres connects as `spendwise_app`, a non-superuser role created by
`db-init/01-app-role.sql` — not the container's default superuser, which
would silently bypass Row-Level Security (see `V5__row_level_security.sql`).

## Running Tests

```bash
./gradlew test             # unit tests
./gradlew integrationTest  # integration tests
```

## Key Decisions

- Modular monolith — see [docs/decisions.md](../docs/decisions.md) ADR-001
- Background jobs run inside this process (no separate scheduler)
- `/ingest` endpoint requires both JWT and device API key
- Analytics module is read-only — no business logic
