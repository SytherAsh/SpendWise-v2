# SpendWise — Claude Entry Point

This file is the entry point for Claude Code. Read this before working on any part of the project.

## Claude's Role

You are a **UI/UX refinement and product-polishing author** for SpendWise. The core product is built; your work now is to improve the frontend experience, implement custom design, and make the targeted backend/API changes those improvements require — **reusing the existing services and business logic rather than rewriting them**, and keeping every change compatible with the established architecture. Before writing code that touches **module boundaries, security surfaces, authentication, data access, or ML infrastructure**, read the relevant document from the index below before proceeding. See **[Current Phase — UI/UX Refinement & Product Polishing](#current-phase--uiux-refinement--product-polishing)** below for how to prioritize this phase's work.

**Project state:** Feature implementation is complete. Epics 0–11 have landed across all four surfaces — Auth/User, Android SMS parsing & sync, ingestion & transactions, ML categorization, budgets & alerts, EMI/recurring detection, analytics & export, recommendations & chatbot, the Android app UI, the web dashboard, and the admin portal — see [implementation/tracking/STATUS.md](./implementation/tracking/STATUS.md) for the full task record (114/125 tasks done). All 11 Spring Boot modules and the Android SMS/Parser/Sync/Storage/UI packages carry working business logic; the Next.js web app and admin portal are built and have an established design system and app shell; the database schema is fully migrated with Row-Level Security; CI (backend, ml, android, frontend) is green on GitHub Actions. **The project has now transitioned from feature implementation to UI/UX refinement and product polishing.** The one remaining backlog epic — Epic 12 (Deployment, Monitoring & Launch) — is not yet done; treat it as the launch track that runs alongside the polish work, not as new feature development.

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

- Read [docs/development_guidelines.md](./docs/development_guidelines.md) for coding standards and the pre-commit security checklist
- All API routes are prefixed `/api/v1/`
- JWT Bearer token required on all protected endpoints
- The current focus is UI/UX polish — read **[Current Phase](#current-phase--uiux-refinement--product-polishing)** below before starting work
- See **Git & GitHub Workflow** below for the commit and push process

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

## Current Phase — UI/UX Refinement & Product Polishing

Feature implementation is done (Epics 0–11). Day-to-day work now is **polishing the product and building out a custom, production-quality UI/UX** — primarily on the Next.js web app, with the Android app and supporting backend following as needed. Prioritize work in this order:

> **Redesign status (living note).** A user-directed **whole-web-app visual redesign** is the active track. Pages already reworked under this effort: **Transactions** (category-summary tile grid with per-category spend/share, click-to-filter, debit-only category views), **Planning** (Budgets tab with a no-scroll category grid + per-category budget slider off a 6-month suggestion; EMIs tab restyled), and **Settings** (Profile / Preferences / Export tabs, plus a real Light/Dark/System **theme toggle** via `next-themes`). Supporting infra added: shared category color/icon helpers (`lib/categories.ts`) and the theme system (`data-theme` attribute + tokens in `styles/globals.css`). IA consolidation done: `/budget` + `/emis` → **Planning** tabs, `/export` → a **Settings** tab; those standalone routes were removed. Keep this note current as more pages land.

1. **Frontend UI/UX first.** The web dashboard is the primary surface. A **full visual redesign is sanctioned** (user-directed) — the established design system, app shell, and page IA are being deliberately overhauled, not preserved for their own sake. But once a new visual direction is set, apply it **consistently** across pages rather than leaving a patchwork, and keep reusing the shared *component*, *data-fetching*, and *auth* infrastructure (see item 6) even as the visual layer changes. Apply the same care to the Android UI where a change is warranted.
2. **Backend/API changes only in service of the frontend.** When a UI improvement needs data, a field, or an endpoint shape that doesn't exist yet, make the *minimal* corresponding backend/API change to support it. Don't build backend features the frontend doesn't need.
3. **Reuse, don't rewrite.** The 11 Spring Boot modules and their service interfaces already implement the business logic; the frontend already has shared infrastructure. Call existing services and endpoints, and extend them narrowly if required — do **not** reimplement categorization, budgets, alerts, analytics, auth, etc. Rewriting working business logic is out of scope unless explicitly requested.
4. **Stay compatible with the existing architecture.** Every change must respect the **Security, Architectural, and Infrastructure invariants above** — module boundaries, RLS scoping, the dual-auth `/ingest` rule, the read-only Analytics module, free-tier constraints, and the ADRs in [docs/decisions.md](./docs/decisions.md). UI polish is never a license to bypass an invariant.
5. **Keep changes modular and production-ready.** Componentize UI, keep concerns separated, and match the structure already in the codebase. No throwaway or prototype-quality code on `main`.
6. **Preserve existing coding standards and conventions.** Follow [docs/development_guidelines.md](./docs/development_guidelines.md) and the patterns already present. The *visual* layer (design tokens, layouts, component styling) is in flux during the redesign, but the **infrastructure** patterns are not — keep using them: on the frontend, `lib/apiClient.ts` (auth + 401-refresh wrapper), SWR via `lib/useApi.ts` (with its `isStale` handling), the `AuthGuard`/route-group structure, the `components/ui/` primitive + `components/shared/` app-shell layering, Tailwind with the `styles/globals.css` design tokens, the `next-themes` `data-theme` theming seam, shared category color/icon helpers in `lib/categories.ts`, and Recharts + `lib/chart-theme.ts`; on the backend, the Repository→Service→Controller module shape. Client-only hydration guards use `useSyncExternalStore` (as in `AuthGuard`), never `useEffect`+`setState` (the lint rule `react-hooks/set-state-in-effect` forbids it).
7. **Update documentation whenever functionality or architecture changes.** If a change alters an API contract, a data flow, a schema, or a user flow, update the relevant `docs/` file *in the same change*, and reflect it in [implementation/tracking/STATUS.md](./implementation/tracking/STATUS.md) if it touches tracked work. Keep `docs/` the source of truth (see the frozen-spec rule in [implementation/README.md](./implementation/README.md)).

**When a design decision is genuinely open** (visual direction, a new interaction pattern, a UX trade-off with no obvious default), ask before committing to it rather than guessing.

**Not in scope by default:** new post-MVP features (see [docs/roadmap.md](./docs/roadmap.md)), microservices extraction, changing the ML approach, or adding paid infrastructure. Epic 12 (deployment/monitoring/launch) remains the separate, still-open launch track — see [implementation/tracking/STATUS.md](./implementation/tracking/STATUS.md).

## Frontend Development Workflow

The frontend dev server is already running locally and stays open in the browser while Claude works. This section governs when Claude may start, build, restart, or test it — it is a standing preference, not a one-off instruction, and applies to every future task in this repository.

**Do not, by default:**
- Run `npm run dev`, `pnpm dev`, `yarn dev`, `bun dev`, or start any other development server.
- Build the project or run its test suite.
- Repeatedly build, restart, or relaunch the frontend after every change.

**Claude's responsibility on a frontend change:**
- Make the required code changes.
- Check for obvious mistakes without starting the dev server — syntax errors, type errors, import issues, lint issues (via static reading, not by executing `next lint`/`tsc`, unless asked), and logical inconsistencies.
- Update all affected files, keeping the implementation consistent with the project architecture and this file.
- Leave layout, styling, responsiveness, spacing, and animation verification to the user — they test manually in the already-open browser and will describe exactly what to change if something looks wrong.

**Only build, run, or test when explicitly asked** — e.g. "build the project," "run the frontend," "start the backend," "run tests," "check the production build." Absent one of those explicit requests, assume the frontend is already running, the user is testing manually, and Claude's job is limited to editing code.

**Interaction with the rest of this file:** for routine frontend/UI work, this default takes precedence over the general "run the relevant test suite(s) before recommending a commit" guidance in **Commit Policy** and **Task Workflow** below — commit without starting the dev server, building, or running tests unless the user has explicitly asked for one of those, and say so when recommending the commit. Backend, Android, and ML changes, and any case where the user does ask for a build/run/test, are unaffected — follow the existing Commit Policy and Task Workflow as written.

## Git & GitHub Workflow

This is the authoritative process for how Claude Code operates on this repository day to day. **SpendWise is a solo project — the normal workflow is to work directly on `main`; feature branches and pull requests are not required.** Commit-message conventions and code style live in [docs/development_guidelines.md](./docs/development_guidelines.md); test commands live in [docs/testing.md](./docs/testing.md); epic/task tracking lives in [implementation/](./implementation/README.md). This section governs *when* and *how* those are used — it does not restate them.

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
- Run the relevant test suite(s) for whatever was touched (see [docs/testing.md](./docs/testing.md) and [docs/development_guidelines.md § Running Tests Locally](./docs/development_guidelines.md#running-tests-locally)) before recommending a commit or a push. If any test fails, explain the failure and its cause before committing or pushing — do not proceed silently.

### Working on `main`

- Solo project — **work directly on `main`.** Feature branches and pull requests are **not** part of the normal workflow; do not create them by default.
- Commit each completed unit of work straight to `main` (per the Commit Policy above), then ask before pushing (per Git Operations above).
- Branches remain available if *you or the user* deliberately choose to isolate a risky or experimental change — but they are optional, never the default. Don't open one without a specific reason, and don't require one for routine work, including epics.
- If a task grows past its expected 2–4 hour size, stop and split it per the complexity guidance in [implementation/README.md](./implementation/README.md) — this is about task sizing, not branching.

### Task Workflow (UI/UX polish & remaining launch work)

The MVP backlog is essentially complete — Epics 0–11 have landed. The [implementation/](./implementation/README.md) workspace (`ROADMAP.md`, `DEPENDENCY-GRAPH.md`, `epics/*.md`, `tracking/STATUS.md`) remains the record of that work and the spec for the one epic still open, **Epic 12 (Deployment, Monitoring & Launch)**. Most current work is UI/UX polish that isn't a backlog task; for those follow the **[Current Phase](#current-phase--uiux-refinement--product-polishing)** priorities above. For any change — polish or a remaining Epic 12 task:

1. Ground the change in the docs — read the relevant `docs/` file(s) before touching an API contract, data flow, schema, security surface, or module boundary. For an Epic 12 task, also read its card in `implementation/epics/epic-12-*.md`.
2. Make the change respecting the Security, Architectural, and Infrastructure invariants above, reusing existing services and business logic (don't rewrite them).
3. Run the relevant test suite(s) for whatever was touched (see [docs/testing.md](./docs/testing.md)); add or update tests when behavior changes.
4. Fix any failures — do not proceed with red tests.
5. Update any documentation the change affects (`docs/*` for contract/architecture/flow changes) and, if it touches tracked work, [implementation/tracking/STATUS.md](./implementation/tracking/STATUS.md).
6. Commit the completed work per the Commit Policy above.
7. Ask for approval before pushing, per Git Operations above.

### Repository Safety

- Never commit secrets, API keys, credentials, or `.env` files — see [docs/development_guidelines.md § Security Checklist](./docs/development_guidelines.md#security-checklist-before-every-commit-to-main).
- Protect the existing project structure — don't reorganize directories as a side effect of unrelated work.
- Avoid unnecessary file moves or renames; if one is warranted, do it in its own commit.
- Prefer incremental, reviewable changes over large refactors.

### Communication

Before any potentially destructive or irreversible action — force-push, history rewrite, branch deletion, repo settings change, or anything else that can't be undone — stop and ask for confirmation first.
