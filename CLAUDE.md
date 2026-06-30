# SpendWise — Claude Entry Point

This file is the entry point for Claude Code. Read this before working on any part of the project.

## Claude's Role

You are an implementation author for SpendWise. Write code, scaffold modules, and follow the patterns established in the docs below. Before writing code that touches **module boundaries, security surfaces, authentication, data access, or ML infrastructure**, read the relevant document from the index before proceeding.

**Project state:** Pre-implementation scaffold. No business logic exists yet. Module directories are empty placeholders (`src/main/java/com/spendwise/<module>/`). All implementation starts from scratch — there is no existing code to build on or extend.

## What is SpendWise

SpendWise is a centralized financial tracking product for Indian UPI/payment app users (Paytm, GPay, PhonePe, SBI). It solves the problem of fragmented transaction history across multiple payment platforms by aggregating all spending data into one dashboard.

**Portfolio project** — free for all users, designed to scale from personal/family use to ~20–30 early users.

## Documentation Index

| Document | Contents | Consult when |
|---|---|---|
| [docs/vision.md](./docs/vision.md) | Product vision, success criteria, target users | Defining user-facing features or evaluating scope |
| [docs/requirements.md](./docs/requirements.md) | Functional and non-functional requirements | Adding or changing any feature requirement |
| [docs/architecture.md](./docs/architecture.md) | System architecture, module breakdown, data flow | Any cross-module work, new module, or data flow change |
| [docs/api.md](./docs/api.md) | REST API endpoint reference (all 11 groups) | Adding or modifying any REST endpoint |
| [docs/database.md](./docs/database.md) | Database schema and design decisions | Any schema change, new table, or query design |
| [docs/user_flows.md](./docs/user_flows.md) | Onboarding and ongoing user flows | Implementing onboarding or any recurring user action |
| [docs/security.md](./docs/security.md) | Security protocols and DPDP Act 2023 compliance | Any auth change, security surface, or data access pattern |
| [docs/deployment.md](./docs/deployment.md) | Infrastructure, hosting, CI/CD | Build config, env vars, or hosting decisions |
| [docs/testing.md](./docs/testing.md) | Testing strategy across all four surfaces | Writing or updating tests for any component |
| [docs/roadmap.md](./docs/roadmap.md) | Post-MVP feature roadmap | Checking whether a feature is in MVP scope |
| [docs/decisions.md](./docs/decisions.md) | Architecture Decision Records (ADRs) | Before proposing a new architectural approach |
| [docs/development_guidelines.md](./docs/development_guidelines.md) | Branching, PR process, coding standards | Code style questions; before opening a PR |

## Tech Stack (at a glance)

- **Android**: Native Kotlin — SMS ingestion, background service, mobile UI
- **Backend**: Spring Boot **3.x** (Java 21) — modular monolith REST API on free-tier hosting
- **Frontend**: Next.js (React) — web dashboard on Vercel
- **ML**: FastAPI (Python) — **scikit-learn classifier**; adaptive supervised batch retraining (not deep learning, not online learning)
- **Database**: PostgreSQL via Supabase (free tier)
- **Auth**: Firebase Authentication (phone OTP + Google login)
- **LLM**: Provider intentionally abstracted — Recommendations and Chatbot modules call LLMs through an interface. No vendor has been selected. Do not hardcode any LLM SDK into business logic.

> **Auth pattern:** Firebase validates OTP / Google credential → Spring Boot issues its own JWT (7-day access + refresh token). Do not use Firebase ID tokens directly in backend API calls — the Spring Boot JWT is the authoritative session credential.

## Architecture in one paragraph

The Android app runs a Kotlin foreground service that captures SMS transaction alerts in real time, filters non-financial messages on-device, parses structured fields (`amount`, `recipient_name`, `upi_id`, `transaction_date`) using regex rules, and batches them to the backend every ~15–30 minutes. The Spring Boot monolith receives transactions via `/api/v1/ingest`, calls the FastAPI ML service for categorization, evaluates alert thresholds via a background scheduler, and serves the web dashboard and Android app via REST. All data lives in Supabase (PostgreSQL). The `/api/v1/ingest` endpoint requires both a valid user JWT **and** a device API key registered at onboarding — validate both, reject if either is missing or invalid.

## Key Decisions

1. **Modular monolith** (not microservices) — optimized for free-tier deployment and rapid development. Module boundaries allow future extraction.
2. **On-device SMS parsing** — raw SMS text never leaves the phone. Only structured fields are synced.
3. **Adaptive supervised learning** — user corrections are stored as labeled examples and used in periodic batch retraining. Not online/reinforcement learning.
4. **Server-side ML inference** — categorization runs in the FastAPI service, not on-device.
5. **DPDP Act 2023 compliant** — single consent screen at onboarding gates all data access.
6. **Java 21 for Spring Boot backend** — LTS release; virtual threads via Project Loom available in Spring Boot 3.2+. Android stays Kotlin. See [ADR-009](./docs/decisions.md).

See [docs/decisions.md](./docs/decisions.md) for full ADRs.

## Module Map (Spring Boot)

| Module | Package | Responsibility |
|---|---|---|
| Auth | `com.spendwise.auth` | OTP, Google OAuth, JWT |
| User | `com.spendwise.user` | Profiles, preferences |
| Ingest | `com.spendwise.ingest` | Receives parsed transactions from Android |
| Transaction | `com.spendwise.transaction` | Storage, retrieval, filtering |
| Categorization | `com.spendwise.categorization` | ML client, correction handling |
| Budget | `com.spendwise.budget` | Budget rules, progress |
| Alerts | `com.spendwise.alerts` | Evaluation engine, dispatch |
| Recommendations | `com.spendwise.recommendations` | LLM-powered one-liners |
| Chatbot | `com.spendwise.chatbot` | Context-aware LLM integration |
| Analytics | `com.spendwise.analytics` | Read-only aggregations, export |
| Admin | `com.spendwise.admin` | System monitoring, admin portal |

## Android Module Map

| Module | Package | Responsibility |
|---|---|---|
| **SMS** | `com.spendwise.sms` | BroadcastReceiver for incoming SMS; foreground service lifecycle |
| **Parser** | `com.spendwise.parser` | Regex rules (SBI, Paytm, GPay); keyword detector; field extractor |
| **Sync** | `com.spendwise.sync` | Local Room DB queue; batch HTTP upload to `/ingest`; retry on reconnect |
| **UI** | `com.spendwise.ui` | Android screens: dashboard, transactions, chatbot, settings |
| **Storage** | `com.spendwise.storage` | Room database definition and DAOs for offline transaction queue |

## Working in this codebase

- Read [docs/development_guidelines.md](./docs/development_guidelines.md) before opening a PR
- Never commit directly to `main` — use feature branches
- Run tests before marking a PR ready: see [docs/testing.md](./docs/testing.md)
- All API routes are prefixed `/api/v1/`
- JWT Bearer token required on all protected endpoints

### Security invariants

- `sms_raw_text` must never appear in any user-facing API response — enforce using response DTO classes that exclude this field; never return JPA entity objects directly from API controllers
- `/api/v1/ingest` requires both user JWT and device API key — reject if either is missing
- Admin authentication uses a separate JWT signed with `ADMIN_JWT_SECRET` — a completely different secret from `JWT_SECRET`; the admin auth filter validates only `ADMIN_JWT_SECRET`-signed tokens, and the user auth filter validates only `JWT_SECRET`-signed tokens; regular user tokens are rejected at admin routes at the route level
- Every user-data query must be scoped to the authenticated user: always include `WHERE user_id = ?` in the query **and** ensure a Supabase RLS policy exists for the table — RLS is a backstop, not a substitute for explicit query scoping
- Raw SMS content never travels over the network — on-device parsing only; only structured fields are posted to `/ingest`

### Architectural invariants

- Cross-module calls go through injected service interfaces only — never directly instantiate another module's implementation class
- The Analytics module is strictly read-only — it must not call write methods on any other module
- No circular dependencies between modules
- FastAPI is called only from the Categorization module — no other module calls `/predict` or `/retrain`

### Infrastructure constraints

- All services run on free-tier hosting — do not introduce any solution that requires a paid plan
- Do not add Redis, Kafka, RabbitMQ, Celery, or any external message queue or cache without explicit approval
- Background jobs run inside the Spring Boot process via Spring `@Scheduled` — no external job runner
