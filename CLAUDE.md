# SpendWise — Claude Entry Point

This file is the entry point for Claude Code. Read this before working on any part of the project.

## Claude's Role

You are an implementation author for SpendWise. Write code, scaffold modules, and follow the patterns established in the docs below. Before writing code that touches **module boundaries, security surfaces, authentication, data access, or ML infrastructure**, read the relevant document from the index before proceeding.

**Project state:** Epic 0 (Foundation & Project Scaffolding) is complete — see [implementation/tracking/STATUS.md](./implementation/tracking/STATUS.md) for the full task record. All four services (Spring Boot, FastAPI, Next.js, Android) have working skeletons; the database schema is fully migrated with Row-Level Security; CI (backend, ml, android, frontend) is verified green on GitHub Actions. No business logic exists yet — the 11 Spring Boot modules and Android's SMS/Parser/Sync/Storage packages (`backend/src/main/java/com/spendwise/<module>/`, `android/app/src/main/kotlin/com/spendwise/<module>/`) remain empty placeholders, ready for Epic 1 onward.

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
- All API routes are prefixed `/api/v1/`
- JWT Bearer token required on all protected endpoints
- See **Git & GitHub Workflow** below for branching, commit, and epic-completion process

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

## Git & GitHub Workflow

This is the authoritative process for how Claude Code operates on this repository day to day. Branch/commit-message conventions and code style live in [docs/development_guidelines.md](./docs/development_guidelines.md); test commands live in [docs/testing.md](./docs/testing.md); epic/task tracking lives in [implementation/](./implementation/README.md). This section governs *when* and *how* those are used — it does not restate them.

### Git Operations

- Always ask for explicit confirmation before pushing to GitHub — never push proactively.
- Never force-push (`--force`, `--force-with-lease`) under any circumstance.
- Never rewrite history (rebase, amend a pushed commit, squash) unless explicitly requested for the specific commit(s) in question.
- Never delete a local or remote branch without approval.
- Never modify GitHub repository settings (branch protection, webhooks, secrets, collaborators, Actions permissions) without approval.
- Verify the current branch (`git branch --show-current`) before making any commit — never assume.
- Keep the working tree clean: no stray debug files, commented-out code, or unrelated changes bundled into a commit.

### Commit Policy

- Commit only after completing a logical unit of work (a task, a story, a fix) — not mid-edit.
- Use the conventional-commits format defined in [docs/development_guidelines.md § Commit Messages](./docs/development_guidelines.md#commit-messages).
- Never commit code that fails to build or fails its test suite, unless the user explicitly asks for a checkpoint commit — say so in the message (e.g. `wip: checkpoint, tests not passing`).
- Run the relevant test suite(s) for whatever was touched (see [docs/testing.md](./docs/testing.md) and [docs/development_guidelines.md § Running Tests Locally](./docs/development_guidelines.md#running-tests-locally)) before recommending a commit.

### Branch Strategy

- Never commit directly to `main` — no exceptions, including trivial doc fixes. Always work on a branch named per [docs/development_guidelines.md § Branching Strategy](./docs/development_guidelines.md#branching-strategy) (`feature/`, `fix/`, `chore/`).
- One branch per feature, fix, or epic story — don't let a branch accumulate unrelated work. If a task grows past its expected size, stop and split it, per the complexity guidance in [implementation/README.md](./implementation/README.md).
- Merge to `main` only after tests pass and (per Git Operations above) the user has approved the push.

### Epic Workflow

Epics and tasks are tracked in [implementation/](./implementation/README.md) — read it first for how the workspace is organized (`ROADMAP.md`, `DEPENDENCY-GRAPH.md`, `epics/*.md`, `tracking/STATUS.md`). For every epic or standalone task:

1. Review the requirements — read the task's card in its epic file (`implementation/epics/epic-<NN>-*.md`) and the `docs/` source it's grounded in.
2. Verify dependencies — check [implementation/DEPENDENCY-GRAPH.md](./implementation/DEPENDENCY-GRAPH.md) to confirm prerequisite epics/tasks are actually done, not just assumed.
3. Implement the epic, respecting the security and architectural invariants above.
4. Run all tests named in the task's "Required Tests" and in [docs/testing.md](./docs/testing.md).
5. Fix any failures — do not proceed with red tests.
6. Commit the completed work per the Commit Policy above.
7. Ask for approval before pushing, per Git Operations above.
8. Once the epic is complete and pushed, recommend a Git tag (e.g. `epic-<NN>-<slug>-complete`, matching the epic's filename) — propose it, don't create it without confirmation.
9. Update [implementation/tracking/STATUS.md](./implementation/tracking/STATUS.md) (check off the task/epic) and any documentation the epic actually changed — e.g. [docs/roadmap.md](./docs/roadmap.md) if MVP scope shifted.

### Repository Safety

- Never commit secrets, API keys, credentials, or `.env` files — see [docs/development_guidelines.md § Security Checklist](./docs/development_guidelines.md#security-checklist-before-every-pr).
- Protect the existing project structure — don't reorganize directories as a side effect of unrelated work.
- Avoid unnecessary file moves or renames; if one is warranted, do it in its own commit.
- Prefer incremental, reviewable changes over large refactors.

### Communication

Before any potentially destructive or irreversible action — force-push, history rewrite, branch deletion, repo settings change, or anything else that can't be undone — stop and ask for confirmation first.
