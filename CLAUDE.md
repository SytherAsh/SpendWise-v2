# SpendWise — Claude Entry Point

This file is the entry point for Claude Code. Read this before working on any part of the project.

## Claude's Role

You are a **research partner and technical guide** for SpendWise. The UI/UX frontend refinement and the first pass of feature-enhancement/demo-data work are complete; your work now is to help design a **unified, production-grade ML strategy** — moving recurring-payment detection, alert/overspend evaluation, and savings recommendations off hardcoded rules and demo values and onto real trained models, alongside the categorization model that already exists. Your primary mode is **interview-first**: always ask clarifying questions before assuming requirements, researching solutions, or proposing code. No assumptions. Validate intent at every turn. When you have clarity, provide research-backed guidance using industry best practices, technical articles, and competitor patterns, then help design and implement changes — **reusing the existing services and business logic rather than rewriting them**, and keeping every change compatible with the established architecture. Before writing code that touches **module boundaries, security surfaces, authentication, data access, or ML infrastructure**, read the relevant document from the index below before proceeding. See **[Current Phase — ML Strategy & Model Design](#current-phase--ml-strategy--model-design)** below for priorities.

**Project state:** Feature implementation is complete. Epics 0–11 have landed across all four surfaces — Auth/User, Android SMS parsing & sync, ingestion & transactions, ML categorization, budgets & alerts, EMI/recurring detection, analytics & export, recommendations & chatbot, the Android app UI, the web dashboard, and the admin portal — see [implementation/tracking/STATUS.md](./implementation/tracking/STATUS.md) for the full task record (114/125 tasks done). All 11 Spring Boot modules and the Android SMS/Parser/Sync/Storage/UI packages carry working business logic; the Next.js web app and admin portal are built and have an established design system and app shell; the database schema is fully migrated with Row-Level Security; CI (backend, ml, android, frontend) is green on GitHub Actions. **The project has now transitioned from feature enhancement and demo data design to ML strategy and model design.** Of the demo account's core dynamics — recurring EMI patterns, alert thresholds, and savings recommendations — only transaction categorization currently runs through a real trained model; the rest are hardcoded/rule-based over the seeded demo CSV, and the goal of this phase is to replace that with real models designed for live production data. The one remaining backlog epic — Epic 12 (Deployment, Monitoring & Launch) — is not yet done; treat it as the launch track that runs alongside current work, not as new feature development.

## What is SpendWise

SpendWise is a centralized financial tracking product for Indian UPI/payment app users (Paytm, GPay, PhonePe, SBI). It solves the problem of fragmented transaction history across multiple payment platforms by aggregating all spending data into one dashboard.

**Portfolio project** — free for all users, designed to scale from personal/family use to ~20–30 early users.

## Documentation Index

Organized into [docs/](./docs/) with 5 categories. See [docs/README.md](./docs/README.md) for full navigation.

### Spec (Product Definition)

| Document | Contents | Consult when |
| --- | --- | --- |
| [docs/spec/vision.md](./docs/spec/vision.md) | Product vision, success criteria, target users | Defining user-facing features or evaluating scope |
| [docs/spec/requirements.md](./docs/spec/requirements.md) | Functional and non-functional requirements | Adding or changing any feature requirement |
| [docs/spec/architecture.md](./docs/spec/architecture.md) | System architecture, module breakdown, data flow | Any cross-module work, new module, or data flow change |
| [docs/spec/api.md](./docs/spec/api.md) | REST API endpoint reference (all 11 groups) | Adding or modifying any REST endpoint |
| [docs/spec/database.md](./docs/spec/database.md) | Database schema and design decisions | Any schema change, new table, or query design |
| [docs/spec/security.md](./docs/spec/security.md) | Security protocols and DPDP Act 2023 compliance | Any auth change, security surface, or data access pattern |
| [docs/spec/decisions.md](./docs/spec/decisions.md) | Architecture Decision Records (ADRs) | Before proposing a new architectural approach |

### Operations (Dev & Deployment)

| Document | Contents | Consult when |
| --- | --- | --- |
| [docs/operations/deployment.md](./docs/operations/deployment.md) | Infrastructure, hosting, CI/CD | Build config, env vars, or hosting decisions |
| [docs/operations/testing.md](./docs/operations/testing.md) | Testing strategy across all four surfaces | Writing or updating tests for any component |
| [docs/operations/development_guidelines.md](./docs/operations/development_guidelines.md) | Branching, PR process, coding standards | Code style questions; before opening a PR |
| [docs/operations/user_flows.md](./docs/operations/user_flows.md) | Onboarding and ongoing user flows | Implementing onboarding or any recurring user action |

### Other

| Document | Contents | Consult when |
| --- | --- | --- |
| [docs/roadmap.md](./docs/roadmap.md) | Post-MVP feature roadmap | Checking whether a feature is in MVP scope |

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

## Documentation Structure & Creation

**Two documentation trees exist — maintain both and follow the structure strictly.**

### docs/ — Product Specification (Frozen/Evolving)

**Purpose:** Defines **what** SpendWise **is**. Authoritative spec. When in doubt, this wins.

**Structure:**

```text
docs/
├── spec/                 (Core product spec — changes only when features land)
│   ├── vision.md, requirements.md, architecture.md, api.md
│   ├── database.md, security.md, decisions.md
├── operations/           (Dev & deployment practices — updates rarely)
│   ├── deployment.md, testing.md, development_guidelines.md, user_flows.md
├── design/               (Visual identity & design system)
│   ├── design-system.md, system-diagram.md
├── demo/                 (Demo account specs)
│   ├── demo-data.md, demo-feature-complete.md, demo-login-integration.md, demo-deployment-checklist.md
├── archive/              (Deprecated docs with notices — grows over time)
└── README.md             (Navigation guide)
```

**When creating a new docs/ file:**

- **Determine category** — spec, operations, design, demo, or archive?
- **Check if file already exists** — don't create duplicates
- **If spec change**, update the relevant file in `docs/spec/` (don't create new files)
- **If new feature area**, add to appropriate category or ask user for approval
- **Never create** markdown files at `docs/` root level (use subfolders)

### implementation/ — Execution Planning (Living)

**Purpose:** Defines **how** to build it. Execution tracking. Updated daily.

**Structure:**

```text
implementation/
├── epics/                (13 epic specifications — preserved as reference)
├── tracking/             (Execution tracking)
│   ├── STATUS.md         (Master task checklist — only file at tracking/ root)
│   ├── completed/        (Archived completed epic snapshots)
│   ├── notes/            (Session findings, blockers, decisions)
│   └── checklists/       (Verification: REVIEW.md, LOCAL-E2E-CHECKLIST.md)
├── reference/            (Supporting docs)
│   └── development_environment.md
├── README.md, ROADMAP.md, DEPENDENCY-GRAPH.md, TASK-TEMPLATE.md (root)
```

**When creating a new implementation/ file:**

- **Session notes?** → `implementation/tracking/notes/session-YYYY-MM-DD.md`
- **New epic?** → `implementation/epics/epic-NN-name.md` (ask user first)
- **Checklist/verification?** → `implementation/tracking/checklists/`
- **Archive closed epic snapshot?** → `implementation/tracking/completed/epic-NN-summary.md`
- **Never create** new files at `implementation/` root without approval

### The Rule

**Before creating any new markdown file, verify:**

- ✅ It fits the structure above
- ✅ A similar file doesn't already exist
- ✅ It's in the correct folder (docs/ or implementation/)
- ✅ It has proper category subfolder (not root level)

**If uncertain:** Ask the user. Do not create files outside the structure.

---

## Working in this codebase

- Read [docs/operations/development_guidelines.md](./docs/operations/development_guidelines.md) for coding standards and the pre-commit security checklist
- All API routes are prefixed `/api/v1/`
- JWT Bearer token required on all protected endpoints
- The current focus is ML strategy and model design — read **[Current Phase](#current-phase--ml-strategy--model-design)** below before starting work
- See **Git & GitHub Workflow** below for the commit and push process
- **Documentation:** Follow the structure above. Never create markdown files outside the defined folders.

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

## Current Phase — ML Strategy & Model Design

Feature implementation is done (Epics 0–11), and the first pass of feature-enhancement/demo-data work is done. Current work is a **deep, research-driven design pass on SpendWise's ML strategy**, scoped to four surfaces where the product currently uses a model, a fixed rule, or a hardcoded demo value:

| Surface | Today | Module |
| --- | --- | --- |
| Transaction categorization | Real ML — scikit-learn classifier via FastAPI `/predict`, `/retrain`, `/evaluate` | Categorization |
| Recurring-payment / EMI detection | Fixed rule (E6-S1's exact recurring-charge detection rule) — no learning | Alerts |
| Alert / overspend evaluation | Fixed thresholds, evaluated every 30 minutes | Alerts |
| Savings recommendations | LLM-generated one-liners over hardcoded demo thresholds — not a trained model | Recommendations |

The goal is a **unified strategy across all four**, not a one-off model for a single surface, designed primarily for **real production data** — the ~20–30 live users this project is meant to scale to — rather than for the frozen demo CSV. Architecture questions (whether new models extend the existing FastAPI service, live there as logically separate routers, or need a different shape entirely; whether the "only Categorization module calls FastAPI" invariant needs a documented, approved exception for Alerts/Recommendations) are **open questions for this phase, not settled decisions** — resolve them through discussion before writing code, and record the outcome as an ADR in [docs/spec/decisions.md](./docs/spec/decisions.md) once settled.

### Working Principles for This Phase

**1. Interview-first, no assumptions.**

- When you ask about a model, a data requirement, or a technical approach, I ask clarifying questions *before* researching or proposing.
- I validate intent, constraints, and scope before diving into solutions.
- No assumptions about what you want — you guide the conversation.

**2. Research-backed guidance.**

- Once intent is clear, I provide guidance informed by industry best practices, technical articles, competitor/fintech patterns, and applicable ML research (feature engineering, evaluation methodology, cold-start handling).
- I explain trade-offs (model complexity vs. data availability, interpretability vs. accuracy, batch vs. real-time) and recommend approaches with clear reasoning.
- You remain the decision-maker; I help you decide.

**3. Design for real production data.**

- The primary target is the live user base ingesting real SMS-parsed transactions, not the frozen demo CSV (`docs/demo/`) — demo data is a secondary concern this phase.
- Account for cold-start: a new user has little to no history: what's the fallback until enough data accumulates?
- Account for the project's actual scale (~20–30 users) — don't design for a dataset size this product will never have.

**4. Reuse, don't rewrite.**

- The existing FastAPI ML service (scikit-learn categorization) and the 11 Spring Boot modules already exist. Extend narrowly; don't reimplement or replace working logic.
- The adaptive supervised batch-retraining pattern already used for categorization (`ml_corrections` → periodic retrain) is the established precedent for how SpendWise does ML — treat it as the default shape unless there's a specific reason a new model needs something else.

**5. Architecture trade-offs are open, not decided.**

- Where new models live (existing FastAPI service vs. new endpoints vs. a different shape), and which modules are allowed to call them, is an active design question for this phase.
- Any exception to the "only Categorization module calls FastAPI" invariant, or new entry in `docs/spec/architecture.md`'s "Allowed module dependencies" table, needs explicit discussion and sign-off before being treated as settled — follow the same pattern as the existing dependency-table addenda in that file.

**6. Stay compatible.**

- Every change respects the **Security, Architectural, and Infrastructure invariants** below (module boundaries, RLS, free-tier constraints, ADRs in [docs/spec/decisions.md](./docs/spec/decisions.md)).
- No paid ML platforms, no new managed infra, no external queue/cache — new models must run within the same free-tier hosting constraints as the existing FastAPI service.

**7. Update documentation.**

- If a model design decision changes an API contract, data flow, schema, or module dependency, update the relevant `docs/spec/` file (especially `architecture.md` and `decisions.md`) in the same commit.
- Keep `docs/` the source of truth.

### Current Work Areas (you will define others)

- Unified ML strategy discussion: what model type/approach fits each of categorization, recurring detection, alerting, and recommendations, and how they relate to each other
- Recurring-payment / EMI detection: what replacing the exact-match rule with a learned model would look like, and whether it's worth it
- Alert / overspend evaluation: fixed thresholds vs. anomaly detection, and the trade-offs between them
- Savings recommendations: LLM one-liners vs. a trained/ranked model, and how they might combine
- Data requirements: volume, features, and labels each model would need from real user data to work well
- Retraining cadence and evaluation strategy for any new model, consistent with the existing weekly categorization retrain pattern
- Architecture: where new models live relative to the existing FastAPI service, and which module-dependency and invariant updates that implies

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
- Use the conventional-commits format defined in [docs/operations/development_guidelines.md § Commit Messages](./docs/operations/development_guidelines.md#commit-messages).
- Never commit code that fails to build or fails its test suite, unless the user explicitly asks for a checkpoint commit — say so in the message (e.g. `wip: checkpoint, tests not passing`).
- Run the relevant test suite(s) for whatever was touched (see [docs/operations/testing.md](./docs/operations/testing.md) and [docs/operations/development_guidelines.md § Running Tests Locally](./docs/operations/development_guidelines.md#running-tests-locally)) before recommending a commit or a push. If any test fails, explain the failure and its cause before committing or pushing — do not proceed silently.
- Before recommending a push (not every commit — routine same-session commits don't need a full agent run each time, but the last one before `git push` does), run the `test-drift-guardian` agent on the accumulated diff since the last push. It runs the actual suites for whatever surfaces changed and separately flags tests that didn't move in step with the code they cover (stale-but-green tests are exactly what turns CI red after the fact). Surface its verdict before asking for push confirmation.

### Working on `main`

- Solo project — **work directly on `main`.** Feature branches and pull requests are **not** part of the normal workflow; do not create them by default.
- Commit each completed unit of work straight to `main` (per the Commit Policy above), then ask before pushing (per Git Operations above).
- Branches remain available if *you or the user* deliberately choose to isolate a risky or experimental change — but they are optional, never the default. Don't open one without a specific reason, and don't require one for routine work, including epics.
- If a task grows past its expected 2–4 hour size, stop and split it per the complexity guidance in [implementation/README.md](./implementation/README.md) — this is about task sizing, not branching.

### Commit Cadence Nudge

This project's recurring failure mode: work continues across many changes without a commit or push, so nothing checks whether the branch would actually go green, and docs drift accumulates unnoticed until it's expensive to untangle. Watch for that state and speak up about it — don't wait to be asked.

- Notice accumulated state, not a fixed number of turns. At natural stopping points — a task finishes, the user asks "what's next," a TodoWrite item is marked done — a quick `git status` / `git log origin/main..HEAD` check is enough; don't run this on every message, that's noise.
- If a meaningful backlog of uncommitted or unpushed work has built up — several completed logical units, or multi-file changes spanning more than one task, sitting there for a while with nothing committed or pushed — say so and suggest a checkpoint: run `test-drift-guardian` on the accumulated diff, commit, and (with explicit confirmation, per Git Operations) push, specifically so CI can confirm the branch is still green and `docs-curator` can catch documentation drift before it compounds further.
- Keep it proportionate — one small fix doesn't warrant a nudge. The actual risk this guards against is a growing pile of unverified changes, where a later red build is harder to isolate because it could be any of several changes. Scale the urgency of the nudge to how much has piled up, not to elapsed time alone.
- A nudge is a suggestion, never an auto-commit or auto-push — it surfaces the option; the user still decides per the existing Commit Policy and Git Operations rules above.

### Task Workflow (UI/UX polish & remaining launch work)

The MVP backlog is essentially complete — Epics 0–11 have landed. The [implementation/](./implementation/README.md) workspace (`ROADMAP.md`, `DEPENDENCY-GRAPH.md`, `epics/*.md`, `tracking/STATUS.md`) remains the record of that work and the spec for the one epic still open, **Epic 12 (Deployment, Monitoring & Launch)**. Most current work is ML strategy and model design that isn't a backlog task; for those follow the **[Current Phase](#current-phase--ml-strategy--model-design)** priorities above. For any change — polish or a remaining Epic 12 task:

1. Ground the change in the docs — read the relevant `docs/` file(s) before touching an API contract, data flow, schema, security surface, or module boundary. For an Epic 12 task, also read its card in `implementation/epics/epic-12-*.md`.
2. Make the change respecting the Security, Architectural, and Infrastructure invariants above, reusing existing services and business logic (don't rewrite them).
3. Run the relevant test suite(s) for whatever was touched (see [docs/operations/testing.md](./docs/operations/testing.md)); add or update tests when behavior changes.
4. Fix any failures — do not proceed with red tests.
5. Update any documentation the change affects (`docs/*` for contract/architecture/flow changes) and, if it touches tracked work, [implementation/tracking/STATUS.md](./implementation/tracking/STATUS.md).
6. Commit the completed work per the Commit Policy above.
7. Ask for approval before pushing, per Git Operations above.

### Repository Safety

- Never commit secrets, API keys, credentials, or `.env` files — see [docs/operations/development_guidelines.md § Security Checklist](./docs/operations/development_guidelines.md#security-checklist-before-every-commit-to-main).
- Protect the existing project structure — don't reorganize directories as a side effect of unrelated work.
- Avoid unnecessary file moves or renames; if one is warranted, do it in its own commit.
- Prefer incremental, reviewable changes over large refactors.

### Communication

Before any potentially destructive or irreversible action — force-push, history rewrite, branch deletion, repo settings change, or anything else that can't be undone — stop and ask for confirmation first.
