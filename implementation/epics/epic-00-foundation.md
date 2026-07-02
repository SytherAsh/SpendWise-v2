# Epic 0 — Foundation & Project Scaffolding

**Working Milestone:** All four services boot locally and respond to a health check;
`./gradlew test` passes on an empty Spring Boot skeleton; the Next.js app renders a
placeholder page; the Android app builds and installs a blank launcher screen; FastAPI
`/health` returns 200; the full DB schema is migrated on a Supabase project with RLS
policies active on every `user_id` table; CI runs all four test jobs on a push to `main`
and reports green (trivially, since there are no tests yet beyond scaffolding smoke tests).

No business logic is written in this epic — it is purely project setup so that every
later epic has a working, testable base to build on.

---

### E0-S1 — Repo & Tooling Baseline

Stand up the four runnable service skeletons matching the module maps and tech stack
already fixed in `CLAUDE.md` and each service's `README.md`.

**Independently testable via:** each service starts locally and serves a trivial route/screen.

#### E0-S1-T1 — Spring Boot project skeleton

- **Objective:** Stand up the Spring Boot 3.x / Java 21 application shell that every later
  backend module plugs into.
- **Expected Deliverable:** `backend/build.gradle(.kts)`, Gradle wrapper, `Application.java`
  main class, `application.yml` with `local`/`dev`/`prod` Spring profiles, `spring.threads.virtual.enabled=true`
  (ADR-009), and an `.env.example` matching the Spring Boot env var list in `docs/deployment.md`.
- **Definition of Done:**
  - `./gradlew bootRun` starts the app on port 8080 with no errors against a local profile.
  - Package layout matches `CLAUDE.md`'s Module Map (`com.spendwise.<module>` packages already exist as empty dirs — confirm they're on the Gradle source set).
  - No secrets committed; `.env` is gitignored, `.env.example` has empty values only.
- **Required Tests:** A single smoke test (`ApplicationContextTests`) asserting the Spring context loads.
- **Estimated Complexity:** Medium
- **Depends on:** —
- **Grounded in:** `CLAUDE.md` (Tech Stack, Module Map), `docs/decisions.md` ADR-009, `docs/deployment.md` env vars.

#### E0-S1-T2 — Next.js project skeleton

- **Objective:** Stand up the Next.js/React web dashboard shell.
- **Expected Deliverable:** Next.js app (App Router) under `frontend/`, strict TypeScript
  mode, ESLint config per `docs/development_guidelines.md`, `.env.example` matching the
  frontend env vars in `docs/deployment.md`.
- **Definition of Done:**
  - `npm run dev` serves a placeholder home page at `localhost:3000`.
  - Folder structure matches `frontend/README.md` (`src/app`, `src/components/{dashboard,transactions,chatbot,charts,shared}`, `src/lib`, `src/styles`).
  - `tsconfig.json` has `strict: true`.
- **Required Tests:** `npm run build` completes with zero type errors.
- **Estimated Complexity:** Small
- **Depends on:** —
- **Grounded in:** `CLAUDE.md` Tech Stack, `docs/deployment.md` Next.js env vars, `docs/development_guidelines.md` TypeScript/React conventions.

#### E0-S1-T3 — Android project skeleton

- **Objective:** Stand up the Android Gradle project shell with the five module packages.
- **Expected Deliverable:** `android/app/build.gradle`, `AndroidManifest.xml`, base
  `Application` class, empty package dirs for `com.spendwise.{sms,parser,sync,ui,storage}`.
- **Definition of Done:**
  - `./gradlew assembleDebug` builds successfully.
  - App installs on an emulator and shows a blank/placeholder launcher screen.
  - Kotlin, 4-space indentation, lint config per `docs/development_guidelines.md`.
- **Required Tests:** Default `./gradlew test` runs (no assertions yet, just proves the test task is wired).
- **Estimated Complexity:** Medium
- **Depends on:** —
- **Grounded in:** `CLAUDE.md` Android Module Map, `docs/development_guidelines.md` Kotlin conventions.

#### E0-S1-T4 — FastAPI project skeleton

- **Objective:** Stand up the FastAPI ML service shell.
- **Expected Deliverable:** `ml/api/main.py`, `requirements.txt`, Pydantic settings class
  reading the FastAPI env vars from `docs/deployment.md`, `GET /health` route.
- **Definition of Done:**
  - `uvicorn api.main:app` serves `GET /health` → `200`.
  - `black`-formatted, type hints on all signatures per `docs/development_guidelines.md`.
  - `.env.example` present under `ml/`.
- **Required Tests:** `pytest` smoke test hitting `/health` via `TestClient`.
- **Estimated Complexity:** Small
- **Depends on:** —
- **Grounded in:** `docs/deployment.md` FastAPI env vars, `docs/architecture.md` FastAPI ML Service section.

#### E0-S1-T5 — Root tooling audit

- **Objective:** Ensure repo-wide tooling is consistent before real code lands.
- **Expected Deliverable:** `.editorconfig` (4-space indentation across languages), a
  repo-root `.gitignore` audit covering all four services' build artifacts and `.env` files,
  a PR template referencing the security checklist in `docs/development_guidelines.md`.
- **Definition of Done:**
  - No `.env`, build output, or IDE folder is trackable by git (`git status` clean after a
    full local build of all 4 services).
  - PR template includes the security checklist items verbatim from `docs/development_guidelines.md`.
- **Required Tests:** N/A (config-only) — verify by running a full build of all 4 services and confirming `git status` shows no unintended files.
- **Estimated Complexity:** Small
- **Depends on:** E0-S1-T1, E0-S1-T2, E0-S1-T3, E0-S1-T4
- **Grounded in:** `docs/development_guidelines.md`.

---

### E0-S2 — Database Foundation

Stand up the Supabase Postgres schema exactly as specified in `docs/database.md`, including
every index and constraint called out there, plus RLS.

**Independently testable via:** running the migration tool against a fresh Postgres
instance (local Docker or Supabase) and querying `information_schema` for expected tables/indexes.

#### E0-S2-T1 — Supabase project & migration tool setup

- **Objective:** Provision the Supabase project and pick/configure a migration tool so
  schema changes are versioned, not applied by hand.
- **Expected Deliverable:** A Supabase project (dev), a chosen migration tool wired into
  the Spring Boot build (Flyway is the natural fit for Spring Boot — `db/migration/` SQL
  files under `backend/src/main/resources/`), and connection config reading `SUPABASE_URL`/`SUPABASE_KEY`.
- **Definition of Done:**
  - `./gradlew bootRun` against the dev Supabase project applies migrations automatically on startup with zero manual SQL steps.
  - Migration tool choice and rationale documented in a short comment at the top of the first migration file.
- **Required Tests:** A migration-runner smoke test confirming the `flyway_schema_history` (or equivalent) table exists after startup.
- **Estimated Complexity:** Medium
- **Depends on:** E0-S1-T1
- **Grounded in:** `docs/deployment.md` (Supabase, env vars), `docs/database.md` (System section).

#### E0-S2-T2 — Migration: identity & session tables

- **Objective:** Create the tables that back authentication and onboarding.
- **Expected Deliverable:** Migration script creating `users`, `user_preferences`,
  `user_consent`, `refresh_tokens`, `device_api_keys` — verbatim schema, constraints, and
  indexes from `docs/database.md`.
- **Definition of Done:**
  - All 5 tables, their CHECK constraints, and all listed indexes (`idx_users_unique_phone`,
    `idx_users_unique_google_id`, `idx_refresh_tokens_hash`, `idx_refresh_tokens_user`,
    `idx_device_keys_user`) exist after migration.
  - `chk_user_identifier` constraint verified to reject a row with both `phone` and `google_id` NULL.
- **Required Tests:** SQL-level test (or a Testcontainers-backed integration test) inserting a violating row and asserting the constraint fires.
- **Estimated Complexity:** Medium
- **Depends on:** E0-S2-T1
- **Grounded in:** `docs/database.md` `users`, `user_preferences`, `user_consent`, `refresh_tokens`, `device_api_keys` sections.

#### E0-S2-T3 — Migration: transactions & categories

- **Objective:** Create the core transaction schema and seed the 10 predefined categories.
- **Expected Deliverable:** Migration creating `transaction_source` enum, `transactions`,
  `categories` (with the 10-row seed data), `transaction_categories`, `assigned_by_type` enum,
  plus all listed indexes.
- **Definition of Done:**
  - `chk_dr_cr_consistency` constraint verified against both a DR and a CR case (positive and negative test).
  - `idx_transactions_unique_dedup` verified to reject a duplicate `(user_id, transaction_id)` insert.
  - Seed query returns exactly the 10 categories with the exact names/icons from the table in `docs/database.md`.
- **Required Tests:** Testcontainers integration test asserting seed row count = 10 and exact `(id, name, icon)` tuples; constraint-violation tests for `chk_dr_cr_consistency` and the dedup unique index.
- **Estimated Complexity:** Medium
- **Depends on:** E0-S2-T2
- **Grounded in:** `docs/database.md` `transactions`, `categories`, `transaction_categories` sections; `docs/requirements.md` Transaction Categories list.
- **Amended (2026-07-02):** `categories` extended from 10 to 12 rows by migration `V7__add_medical_and_fees_categories.sql` (Medical, Fees & Debt). This task's original deliverable (V2, 10 rows) is unchanged as a historical record; current seed count is 12 — see `docs/database.md`.

#### E0-S2-T4 — Migration: budgets, alerts, EMIs

- **Objective:** Create the budgeting and alerting schema.
- **Expected Deliverable:** Migration creating `budgets`, `alert_type` enum, `alerts`, `emis`
  — verbatim schema/constraints/indexes from `docs/database.md`.
- **Definition of Done:**
  - `chk_budget_limit_positive` and `chk_emi_amount_positive` verified to reject non-positive values.
  - `idx_emis_source_txn` verified to reject two EMIs pointing at the same `source_transaction_id`.
  - Unique constraint `(user_id, category_id, month, year)` on `budgets` verified.
- **Required Tests:** Constraint-violation integration tests for each CHECK/UNIQUE listed above.
- **Estimated Complexity:** Medium
- **Depends on:** E0-S2-T3
- **Grounded in:** `docs/database.md` `budgets`, `alerts`, `emis` sections.

#### E0-S2-T5 — Migration: ML, admin, and chatbot tables

- **Objective:** Create the remaining schema: recommendations, ML training data, admin
  audit log, chatbot persistence.
- **Expected Deliverable:** Migration creating `recommendations`, `ml_corrections`,
  `admin_logs`, `chatbot_sessions`, `chat_role` enum, `chatbot_conversations` — verbatim
  schema/constraints/indexes.
- **Definition of Done:**
  - `chk_correction_different_category` verified (including the `IS DISTINCT FROM` NULL case where `old_category_id` is null).
  - `idx_recs_user_category_active` verified to reject a second active recommendation for the same `(user_id, category_id)`.
  - All 5 tables and their indexes present.
- **Required Tests:** Constraint-violation tests for `chk_correction_different_category` (including the null-old-category case) and the recommendations partial unique index.
- **Estimated Complexity:** Medium
- **Depends on:** E0-S2-T4
- **Grounded in:** `docs/database.md` `recommendations`, `ml_corrections`, `admin_logs`, `chatbot_sessions`, `chatbot_conversations` sections.

#### E0-S2-T6 — Row-Level Security policies

- **Objective:** Enable RLS as the database-level backstop described in `docs/security.md`,
  including the session-variable mechanism needed because Spring Boot connects with a
  service-role key.
- **Expected Deliverable:** Migration enabling RLS on every table with a `user_id` column,
  plus join-based policies for `transaction_categories` and `ml_corrections`, plus the
  `set_config('app.current_user_id', ..., true)` call wired into a Spring Boot
  request-scoped interceptor/aspect that runs before every user-scoped query.
- **Definition of Done:**
  - Every table listed in `docs/security.md`'s Supabase RLS section has an active policy.
  - A query executed without `set_config` first returns zero rows (safe-fail deny), proven by a test.
  - A query executed with `set_config` set to user A's UUID cannot see user B's rows, proven by a test.
- **Required Tests:** Integration test: (1) no session var set → RLS-protected query returns empty; (2) session var set to user A → only user A's rows returned even though rows for user B exist in the same table.
- **Estimated Complexity:** Large
- **Depends on:** E0-S2-T5
- **Grounded in:** `docs/security.md` Supabase Row-Level Security section (full detail, including the `current_setting(..., true)` safe-fail behavior); `CLAUDE.md` security invariants.

---

### E0-S3 — CI Skeleton

Wire up the four CI jobs described in `docs/deployment.md` and `docs/testing.md` so every
later epic's tests run automatically on every push to `main` (and on any optional PR).

**Independently testable via:** pushing to `main` and watching all 4 jobs go green.

#### E0-S3-T1 — CI: Spring Boot unit + integration tests

- **Objective:** Automate `./gradlew test` and `./gradlew integrationTest` on every push/PR.
- **Expected Deliverable:** GitHub Actions workflow job(s) running both Gradle tasks;
  integration test job runs on `ubuntu-latest` (Docker available by default) so Testcontainers
  can provision Postgres.
- **Definition of Done:** Both jobs run and pass against the Epic 0 skeleton (smoke tests only so far).
- **Required Tests:** N/A — the deliverable *is* the test runner; verify by triggering the workflow on a throwaway commit.
- **Estimated Complexity:** Medium
- **Depends on:** E0-S1-T1, E0-S2-T6
- **Grounded in:** `docs/deployment.md` CI section, `docs/testing.md` "Why Testcontainers".

#### E0-S3-T2 — CI: FastAPI pytest

- **Objective:** Automate `pytest` for the ML service on every push/PR.
- **Expected Deliverable:** GitHub Actions job running `pytest tests/ -v` under `ml/`.
- **Definition of Done:** Job passes against the Epic 0 `/health` smoke test.
- **Required Tests:** N/A — verify by triggering the workflow.
- **Estimated Complexity:** Small
- **Depends on:** E0-S1-T4
- **Grounded in:** `docs/deployment.md` CI section, `docs/testing.md` §2.

#### E0-S3-T3 — CI: Android unit tests

- **Objective:** Automate `./gradlew test` for the Android Kotlin unit tests.
- **Expected Deliverable:** GitHub Actions job running Android Gradle tests (no emulator required — unit tests only, per `docs/testing.md`).
- **Definition of Done:** Job passes against the Epic 0 skeleton.
- **Required Tests:** N/A — verify by triggering the workflow.
- **Estimated Complexity:** Small
- **Depends on:** E0-S1-T3
- **Grounded in:** `docs/deployment.md` CI section, `docs/testing.md` §3.

#### E0-S3-T4 — CI: frontend build/lint

- **Objective:** Automate `npm run build` and lint for the Next.js app on every push/PR (not
  explicitly listed as a named CI step in `docs/deployment.md`, but required so frontend
  regressions are caught before the E2E/manual QA stage).
- **Expected Deliverable:** GitHub Actions job running `npm ci && npm run lint && npm run build`.
- **Definition of Done:** Job passes against the Epic 0 placeholder page.
- **Required Tests:** N/A — verify by triggering the workflow.
- **Estimated Complexity:** Small
- **Depends on:** E0-S1-T2
- **Grounded in:** `docs/deployment.md` CI section (extended to frontend for parity — see task note above), `docs/development_guidelines.md` TypeScript conventions.

#### E0-S3-T5 — Branch protection (solo direct-to-`main` guardrails)

- **Objective:** Add the *safety* guardrails to `main` at the GitHub repo-settings level,
  without imposing a pull-request workflow — this is a solo project that works directly on
  `main` (see `docs/development_guidelines.md` Git Workflow and `CLAUDE.md` Working on `main`).
- **Expected Deliverable:** Branch protection rule on `main` that (a) prevents force pushes,
  (b) prevents branch deletion, and (c) leaves direct pushes to `main` allowed — i.e. does
  **not** require pull requests or reviews. Keep GitHub Actions CI running on every push to
  `main` (already configured in E0-S3-T1..T4) as the validation signal.
- **Definition of Done:** A force-push to `main` is rejected and `main` cannot be deleted;
  a normal direct push to `main` still succeeds; CI runs on that push.
- **Required Tests:** N/A (repo settings) — verify by confirming a normal push to `main`
  succeeds and CI triggers, and that a force-push is rejected.
- **Estimated Complexity:** Small
- **Depends on:** E0-S3-T1, E0-S3-T2, E0-S3-T3, E0-S3-T4
- **Grounded in:** `docs/development_guidelines.md` Git Workflow; `docs/deployment.md`
  Version Control & Branching (branch-protection recommendation); `CLAUDE.md` Git & GitHub Workflow.
- **Note:** Configuring GitHub repo settings requires repo-admin access and, per `CLAUDE.md`,
  explicit user approval — so this is a **manual step for the repo owner**, not something the
  agent performs. The exact settings and rationale are recorded in `tracking/STATUS.md`.

---

## Parallel Execution within Epic 0

- E0-S1-T1 through T4 (the four service skeletons) are fully independent — build all four
  in parallel.
- E0-S2 (database) only depends on E0-S1-T1 (Spring Boot skeleton, for the migration
  runner) and proceeds strictly sequentially internally (each migration builds on the last).
- E0-S3 (CI) tasks are independent of each other once their respective service skeleton exists.
