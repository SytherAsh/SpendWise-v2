# Status

The living checklist for the whole MVP backlog. Check a box the moment a task is fully
done (see `../README.md` Definition of Done discipline — don't check early). This file is
the only place progress is recorded; epic files under `../epics/` are the specification,
not a log.

Work top-to-bottom within the constraints in `../DEPENDENCY-GRAPH.md` — this list is in
epic order, not strict execution order.

## Epic 0 — [Foundation & Project Scaffolding](../epics/epic-00-foundation.md)

- [x] E0-S1-T1 — Spring Boot project skeleton
- [x] E0-S1-T2 — Next.js project skeleton
- [x] E0-S1-T3 — Android project skeleton
- [x] E0-S1-T4 — FastAPI project skeleton
- [x] E0-S1-T5 — Root tooling audit (`.editorconfig` + `.gitignore` audit stand;
      the PR template this task originally created was deleted once the
      workflow moved to solo direct-to-`main` — see the Git workflow change
      note below)
- [x] E0-S2-T1 — Supabase project & migration tool setup
- [x] E0-S2-T2 — Migration: identity & session tables
- [x] E0-S2-T3 — Migration: transactions & categories
- [x] E0-S2-T4 — Migration: budgets, alerts, EMIs
- [x] E0-S2-T5 — Migration: ML, admin, and chatbot tables
- [x] E0-S2-T6 — Row-Level Security policies
- [x] E0-S3-T1 — CI: Spring Boot unit + integration tests (confirmed green
      on real GitHub Actions — see close-out note below)
- [x] E0-S3-T2 — CI: FastAPI pytest (confirmed green on real GitHub Actions)
- [x] E0-S3-T3 — CI: Android unit tests (confirmed green on real GitHub
      Actions — see close-out note below)
- [x] E0-S3-T4 — CI: frontend build/lint (confirmed green on real GitHub Actions)
- [x] E0-S3-T5 — Branch protection (solo direct-to-`main` guardrails)
      (**manual step for the repo owner** — see the "Branch protection" note
      in the close-out below. Revised from the original "mandatory PR"
      framing: this is now a solo project that works directly on `main`, so
      the rule should only prevent force-pushes and branch deletion and must
      **not** require pull requests. Configuring repo settings needs repo-admin
      access this session doesn't have, and per `CLAUDE.md` must not be changed
      without explicit approval regardless — hence it stays unchecked as an
      owner action, not an agent action.)

### Epic 0 close-out (post-merge, real GitHub verification)

Once a GitHub remote became available, the real (not `act`-simulated) CI run
on the direct push of the E0-S1/S2/S3 commits **failed** on exactly the two
jobs that shell out to a checked-in script (`backend`, `android`) — root
cause: `backend/gradlew` and `android/gradlew` were committed as mode
`100644` (non-executable), since they were originally added from Windows,
which doesn't track the Unix executable bit. Fixed in
[PR #1](https://github.com/SytherAsh/SpendWise-v2/pull/1)
(`fix/epic-0-ci-executable-bit`): `git update-index --chmod=+x` on both
files, plus a new root `.gitattributes` to stop a future contributor's
`core.autocrlf` setting from reintroducing a CRLF shebang line (a related
but distinct way the same class of script breaks on Linux). Verified fixed
on a genuine Linux filesystem (WSL2, not the `/mnt/c` Windows-mounted path,
which reports all files as executable regardless of git's real stored mode)
before pushing, and confirmed via the real GitHub Actions run on the PR and
again on the merge commit to `main` — all 4 jobs green both times.

This is the only defect the real-GitHub verification pass turned up; no
other Epic 0 technical debt was found on repository audit.

### Git workflow change (solo, direct-to-`main`)

The project's Git workflow was changed to a **solo, work-directly-on-`main`**
model (no mandatory feature branches or pull requests). `CLAUDE.md`,
`docs/development_guidelines.md`, `docs/operations/deployment.md`, `docs/operations/testing.md`, and
this file's E0-S3-T5 were updated to match. Branches and PRs remain *available*
(optional) but are no longer the default flow; commits go straight to `main`,
CI runs on each push, and the agent still asks before pushing.

Follow-up cleanup once the workflow changed: `.github/pull_request_template.md`
(from E0-S1-T5) was deleted — a PR template has no purpose when PRs aren't part
of the normal flow. The two feature branches used for PR-based merges before
the workflow change (`fix/epic-0-ci-executable-bit`, `docs/epic-0-closeout`)
were deleted, locally and on the remote, once confirmed fully merged into
`main` (`git merge-base --is-ancestor` — zero unique commits on either).

### Branch protection — exact settings (manual, repo owner)

E0-S3-T5's remaining piece is a one-time GitHub setting the owner applies at
**Settings → Branches → Add branch ruleset (or classic rule)** for `main`:

1. **Block force pushes** — enable. Matches "never force-push"; prevents history clobbering.
2. **Restrict deletions** — enable. Prevents `main` being deleted.
3. **Require status checks to pass** — *optional* for a solo dev. If enabled, it
   applies only when changes arrive via PR; with direct-to-`main` pushes it does
   not gate anything, so it's fine to leave off and rely on watching the CI run
   after each push. (Do **not** pair it with "Require a pull request before
   merging" — that would reintroduce the mandatory-PR flow we just removed.)
4. **Do NOT enable "Require a pull request before merging"** — this is the key
   difference from a team setup; leaving it off is what keeps direct pushes to
   `main` working.

With items 1–2 set, Epic 0's intent is fully satisfied for a solo workflow.

## Epic 1 — [Auth & User Onboarding](../epics/epic-01-auth-and-user.md)

- [x] E1-S1-T1 — Firebase Admin SDK integration
- [x] E1-S1-T2 — JWT issuance & refresh-token storage
- [x] E1-S1-T3 — `POST /auth/otp/send` + `/auth/otp/verify`
- [x] E1-S1-T4 — `POST /auth/google`
- [x] E1-S1-T5 — `POST /auth/token/refresh` with rotation + replay detection
- [x] E1-S1-T6 — `POST /auth/logout`
- [x] E1-S1-T7 — User JWT auth filter
- [x] E1-S2-T1 — Admin JWT filter (independent chain)
- [x] E1-S3-T1 — `GET/PUT /users/me`
- [x] E1-S3-T2 — `GET/PUT /users/me/preferences`
- [x] E1-S3-T3 — `POST /users/me/onboarding`
- [x] E1-S4-T1 — Device key hash-and-validate service

## Epic 2 — [Android SMS Parsing & Sync](../epics/epic-02-android-sms-parsing-sync.md)

- [x] E2-S1-T1 — Financial vs. non-financial keyword detector
- [x] E2-S2-T1 — SBI parser
- [x] E2-S2-T2 — Paytm parser
- [x] E2-S2-T3 — GPay parser
- [x] E2-S2-T4 — Unknown-sender fallback extractor
- [x] E2-S3-T1 — Synthesized `transaction_id` + on-device dedup
- [x] E2-S4-T1 — Room entities & DAOs for local sync queue
- [x] E2-S5-T1 — Batch sync client (retry/backoff, 409-as-success)
- [x] E2-S5-T2 — Real-time SMS capture (BroadcastReceiver + foreground service)
- [x] E2-S5-T3 — First-launch SMS inbox backfill

## Epic 3 — [Ingestion & Transaction Management](../epics/epic-03-ingestion-and-transactions.md)

- [x] E3-S1-T1 — Dual-auth guard for `/ingest/transactions`
- [x] E3-S1-T2 — Batch persistence with two-layer dedup
- [x] E3-S1-T3 — `sms_raw_text` response-exclusion enforcement (DTO layer landed here;
      the black-box integration test proving exclusion end-to-end is added with
      E3-S2-T1/T2, the first GET endpoints to test it against — see close-out note)
- [x] E3-S2-T1 — `GET /transactions` (cursor pagination + filters)
- [x] E3-S2-T2 — `GET /transactions/:id`
- [x] E3-S2-T3 — `POST /transactions` (manual entry)
- [x] E3-S2-T4 — `PUT /transactions/:id/category`
- [x] E3-S2-T5 — `GET /categories`
- [x] E3-S3-T1 — `GET/POST /emis`
- [x] E3-S3-T2 — `PUT/PATCH /emis/:id`

### Epic 3 close-out

All 10 tasks implemented against the frozen `docs/spec/api.md` contract (Android's
Epic 2 Sync module was treated as still "continuing in background" per
`DEPENDENCY-GRAPH.md`, not as a dependency to build against — see the E3-S1
commit body for the one place this mattered: the ingest response shape).
Unit tests (26 new, full backend suite green — 55 tests) were written and
run this session.

**Integration tests — verified green (2026-07-02), after fixing two
pre-existing Epic 1 bugs the first real run of `./gradlew integrationTest`
surfaced** (commit `c26175b`, pushed after the epic's own 4 story commits):
1. `RlsSession`'s three setters called `JdbcTemplate.update(...)` on
   `SELECT set_config(...)` — a query, not DML. PostgreSQL's JDBC driver
   rejects `executeUpdate()` against any statement returning a result set,
   so every RLS-scoped repository call across the whole app was broken (25
   of 48 integration tests failed, including pre-existing Epic 1 tests that
   had apparently never been run against real Docker/CI before). Fixed with
   `queryForObject`.
2. `RefreshTokenService.rotate()`'s replay-detection branch wrote
   `revokeAllForUser(...)` and then threw `InvalidRefreshTokenException` to
   reject the replay — but the method is `@Transactional`, and the default
   rollback-on-RuntimeException behavior silently undid the revocation. The
   replay itself was correctly rejected, but every *other* session for that
   user stayed valid, defeating docs/spec/security.md's "replay revokes all
   sessions" invariant. Fixed with `@Transactional(noRollbackFor = ...)`.

Confirmed via `./gradlew integrationTest` against real Docker (48/48 passing)
and on real GitHub Actions (`gh run watch` — all 4 CI jobs green, including
Backend's Integration tests step). Neither bug is Epic-3-specific; both were
pre-existing and only became visible once Epic 3's push was the first to
actually exercise `integrationTest` end-to-end.

## Epic 4 — [ML Categorization Service](../epics/epic-04-ml-categorization.md)

- [x] E4-S1-T1 — `X-Internal-Key` middleware
- [x] E4-S2-T1 — Preprocessing & feature extraction
- [x] E4-S2-T2 — Train baseline scikit-learn classifier
- [x] E4-S2-T3 — `POST /predict`
- [x] E4-S2-T4 — `POST /retrain`
- [x] E4-S2-T5 — `GET /evaluate` + evaluation script
- [x] E4-S3-T1 — Categorization service interface + FastAPI client
- [x] E4-S3-T2 — Wire Ingest → Categorization trigger
- [x] E4-S3-T3 — Categorization retry job (every 30 min)
- [x] E4-S3-T4 — ML retraining weekly job
- [x] E4-S3-T5 — Admin-triggered retrain + evaluate (service-interface only)

### Epic 4 close-out — cross-user RLS gap found and resolved mid-epic

E4-S3-T3 (categorization retry job) and E4-S3-T4 (ML retraining weekly job) both need a
`@Scheduled` job to read across *all* users' data (uncategorized transactions;
`ml_corrections`), which the schema had no mechanism for — every RLS-protected table has
`FORCE ROW LEVEL SECURITY`, and Spring Boot's primary connection (`spendwise_app`) has no
`BYPASSRLS`, so an unscoped query always returns zero rows. Not a gap introduced here —
`V5__row_level_security.sql`'s own header comment already flagged it as deferred to Epic
11. `docs/spec/architecture.md`'s Background Jobs table describes every scheduled job as
system-wide, so this also blocks Epic 5's alert evaluator and Epic 8's recommendation
generator, not just Epic 4 — a cross-cutting gap, first surfaced here because E4-S3-T3/T4
are the earliest scheduled, cross-user jobs in the build order.

Flagged to the project owner rather than inventing a fix unilaterally, since it's a
security-relevant schema decision. **Decision (2026-07-02): a dedicated system-jobs DB
role.** Implemented as:

- `backend/db-init/02-jobs-role.sql` — a new `spendwise_jobs` Postgres role with
  `BYPASSRLS`, granted membership in `spendwise_app` (inherits its table privileges,
  including on tables created by later migrations).
- `com.spendwise.common.db.JobsDataSourceConfig` — a second `DataSource`/`JdbcTemplate`
  bean pair, connected as `spendwise_jobs`. The default (unqualified) `DataSource`/
  `JdbcTemplate` — used everywhere else in the app — is completely unaffected and stays
  fully RLS-enforced; only `@Qualifier("jobsJdbcTemplate")` injections bypass RLS, and
  only two call sites do that (`TransactionRepository.findAllUncategorized`,
  `MlCorrectionRepository.findAllCorrections`), both exposed to Categorization through
  the normal `TransactionService` interface rather than a new cross-module dependency.
  Both DataSource beans derive their JDBC URL from the same `JdbcConnectionDetails` bean
  (not raw `spring.datasource.*` properties) so this stays correct under Testcontainers'
  `@ServiceConnection` in integration tests, not just local/prod.
- Full design rationale and the local-dev volume-recreation caveat: `docs/spec/security.md`
  "Cross-user reads for background jobs".

E4-S3-T5 landed in full this pass: `triggerRetrain()` (reads all `ml_corrections` via the
jobs role, POSTs to FastAPI `/retrain`) and `getAccuracyMetrics()` (calls `GET /evaluate`
— not blocked by any of the above, since it needs no per-user backend data) are both on
`CategorizationService` now, alongside the `CategorizationBoundaryTest` ArchUnit test from
the previous pass.

**Verification caveat:** unit tests are green (72/72). The new integration test
(`CategorizationJobsIntegrationTest`, using an embedded `HttpServer` stub in place of a
real FastAPI process, and bootstrapping `spendwise_jobs` directly since Testcontainers'
bare Postgres image doesn't run `db-init/`) compiles clean but is **unexecuted** — Docker
is unavailable in this environment, consistent with every other integration test in this
repo per Epic 3's close-out note above. The dual-DataSource wiring in particular has not
been verified against a real Postgres instance and should be smoke-tested (`./gradlew
integrationTest` or `./gradlew bootRun` against `docker-compose.yml`) before this is
considered production-ready.

A `spec-invariant-reviewer` pass on this diff caught one real bug before commit: the jobs
`HikariDataSource` validated a connection eagerly at Spring context startup (Hikari's
default `initializationFailTimeout`), which would have failed context load for **every
other** `@SpringBootTest` integration test in the repo the moment Docker became available
to run them — none of them bootstrap the `spendwise_jobs` role the way
`CategorizationJobsIntegrationTest` does. Fixed with
`HikariDataSource.setInitializationFailTimeout(-1)` on the jobs pool only (the primary
pool stays fail-fast) — a real connection is now only attempted, and can only fail, when
something actually queries via `jobsJdbcTemplate`. Everything else the review checked
(module boundaries, ArchUnit coverage, BYPASSRLS blast radius, SQL correctness, per-item
exception isolation) came back clean.

### Epic 4 post-push CI incident — unqualified JdbcTemplate silently became the BYPASSRLS pool (2026-07-02)

The verification caveat above turned out to matter: pushing to `main` and running the real
`integrationTest` job in CI (Docker, for the first time ever on this repo) failed every
integration test in the suite — not just the new `CategorizationJobsIntegrationTest`, but
`AuthControllerIntegrationTest`, `IngestControllerIntegrationTest`,
`EmiControllerIntegrationTest`, `TransactionControllerIntegrationTest`, and
`UserControllerIntegrationTest` too, none of which touch Categorization at all. Confirmed
via CI history that `integrationTest` had passed cleanly on the commit immediately before
this epic (`0b39d81`) — a genuine regression, not a pre-existing gap.

**Two wrong hypotheses first** (both plausible from timing alone — every failure was
~30 seconds apart, matching Hikari's default `connectionTimeout`), each pushed as a fix and
each failed to change the symptom:

1. `CategorizationRetryJob`'s `@Scheduled(fixedRate = ...)` had no `initialDelay`, firing
   immediately at every test context's startup against a role that doesn't exist there.
   Added an `initialDelay` — no change.
2. Hikari's `minimumIdle` (defaults to `maximumPoolSize`, 10) would make the jobs pool's
   background connection-adder retry forever against a nonexistent role, starving the CI
   runner. Set `minimumIdle(0)` + a small `maximumPoolSize` — no change.

Gradle's default test logging only prints failure stack traces, so neither attempt had
real application log output to work from. Adding explicit `testLogging` (full
stdout/stderr, `build.gradle.kts`) to the `integrationTest` task surfaced the actual error:

```text
org.postgresql.util.PSQLException: FATAL: password authentication failed for user "spendwise_jobs"
```

**Real root cause:** Spring Boot's auto-configured `JdbcTemplate` bean is conditional on
`@ConditionalOnMissingBean(JdbcOperations.class)`. `jobsJdbcTemplate` is itself a
`JdbcTemplate` (implements `JdbcOperations`), so its mere presence in the context silently
disabled the auto-configured default entirely — leaving `jobsJdbcTemplate` as the **only**
`JdbcTemplate` bean in the whole application. Every unqualified injection (every repository
that doesn't explicitly ask for `@Qualifier("jobsJdbcTemplate")`) silently received the
`BYPASSRLS`-enabled connection instead of the RLS-enforced one. This is more than a test
failure: in CI it failed loudly only because `spendwise_jobs` doesn't exist in the
Testcontainers image; in an environment where the role DOES exist (real Supabase, per
`db-init/02-jobs-role.sql`), the identical bug would have silently routed the **entire
application** through the RLS-bypassing role instead of failing at all.

**Fix:** define the primary `JdbcTemplate` bean explicitly (`@Primary`) in
`JobsDataSourceConfig` rather than relying on auto-configuration to keep deferring to "the
original" — plus a regression test
(`ApplicationContextIntegrationTest.unqualifiedJdbcTemplateIsNotTheJobsBypassRlsPool`)
asserting the unqualified `JdbcTemplate`'s `SELECT current_user` is never `spendwise_jobs`.
The `initialDelay`/`minimumIdle` tuning from the two wrong hypotheses was kept — still
correct, low-risk defensive practice, just not what actually fixed this. See
`docs/spec/security.md`'s "Cross-user reads for background jobs" incident note for the full
writeup — flagged there as a general lesson for any future second `DataSource`/
`JdbcTemplate`-family bean: always define and `@Primary`-mark the primary one explicitly,
never assume auto-configuration keeps deferring to it once a second bean of the same type
exists anywhere in the context.

## Epic 5 — [Budget & Alerts](../epics/epic-05-budget-and-alerts.md)

- [x] E5-S1-T1 — `POST /budgets` (idempotent upsert)
- [x] E5-S1-T2 — `GET /budgets`
- [x] E5-S1-T3 — `GET /budgets/progress`
- [x] E5-S1-T4 — `GET /budgets/suggestions`
- [x] E5-S2-T1 — Mid-month 50%-of-total-budget rule
- [x] E5-S2-T2 — Category 80%-approaching-limit rule
- [x] E5-S2-T3 — Category overspend rule
- [x] E5-S2-T4 — Alert evaluator scheduled job (every 30 min)
- [x] E5-S3-T1 — FCM push dispatch
- [x] E5-S3-T2 — SMTP email dispatch
- [x] E5-S4-T1 — `GET /alerts` (cursor pagination + `is_read` filter)
- [x] E5-S4-T2 — `PUT /alerts/:id/read`

### Epic 5 close-out

Implemented as specified (scheduled evaluator, not event-driven — see the handoff decision
below) across four story commits, plus one prep commit for two prerequisite gaps the handoff
review surfaced before any Epic 5 code was written:

**Prep (pre-existing gaps, not Epic 5 scope, but blocking it):**
1. **No FCM device-token storage anywhere in the schema.** `users`/`user_preferences`/
   `device_api_keys` had no column for a push-registration token, and no epic ever specified
   registering one. Flagged to the project owner; approved 2026-07-03 to add
   `user_preferences.fcm_token` (migration `V8__add_fcm_token.sql`) plus a new
   `PUT /users/me/fcm-token` endpoint (User module) — see `docs/spec/database.md`'s
   `user_preferences` addendum.
2. **Alerts had no path to User.** `docs/spec/architecture.md`'s module dependency table gave Alerts
   `Transaction` and `Budget` only, but the same document assigns Alerts "notification dispatch
   (push via FCM, email via SMTP)" as a responsibility — impossible without reading `email` and
   the new `fcm_token`. A gap in the original table, not a deliberate restriction (same category
   of issue as Epic 4's `spendwise_jobs` gap). Approved 2026-07-03; see `docs/spec/architecture.md`'s
   Alerts→User addendum for the read-only grant.

**E5-S1 (Budget module):** `com.spendwise.budget` built mirroring `com.spendwise.transaction`'s
Repository→Service→Controller shape. Per CLAUDE.md ("cross-module calls go through injected
service interfaces only") and `docs/spec/architecture.md`'s "Budget → Transaction (read-only)" row,
Budget never queries `transactions`/`transaction_categories` directly — three new read-only
methods were added to `TransactionService` instead (`sumSpendByCategoryForMonth`,
`historicalMonthlySpend`, plus the cross-user `findAllSpendForMonth` used by Alerts below).
`/budgets/suggestions` averages the trailing 3 calendar months of spend per category (not
specified in docs; a reasonable, undocumented default) and returns an explicit
`available: false` per category rather than omitting categories with no history, satisfying the
"no history → graceful, not an error" DoD at per-category granularity.

**E5-S2 (Alerts evaluation engine):** the three rules (`MidMonthBudgetRule`,
`CategoryApproachingLimitRule`, `CategoryOverspendRule`) are pure static functions, unit-tested
standalone per the epic's own parallelization note. `AlertEvaluatorJob` mirrors
`CategorizationRetryJob`'s cross-user pattern exactly: bulk reads via two new
`spendwise_jobs`-backed methods (`TransactionService.findAllSpendForMonth`,
`BudgetService.findAllForMonth`), grouped in-memory by user, then per-alert persistence goes
through the normal RLS-scoped path (each repository method sets `app.current_user_id` itself,
which works identically from a scheduled-job thread as from a request thread — traced every
`RlsSession.setCurrentUser` call site to confirm this before relying on it). The suppression
rule the epic mandates only for the overspend rule (E5-S2-T3) was applied uniformly to all
three rules — without it, mid-month/approaching-limit would re-fire every 30 minutes for the
rest of the month once crossed. Boundary rule (overspend takes precedence at ≥100%, per the
epic's own text) implemented via ordering: overspend checked first, approaching-limit only
checked if overspend didn't fire.

**Scheduled vs. event-driven (explicit handoff decision, not left ambiguous):** the project
owner asked mid-epic whether alert evaluation should be event-driven (fire on
transaction/budget change) instead of scheduled. Decision: implement exactly as specified
(scheduled, every 30 min) — the mid-month rule is inherently calendar-bound and cannot be
correctly triggered by a transaction event alone (it must fire on the 15th even with zero
transactions that day), the module dependency table gives Alerts no callable path from
Ingest/Transaction without introducing a cycle, and `docs/spec/requirements.md`'s 1-hour alert SLA
doesn't need event-driven latency. The owner asked for a design note proposing the
event-driven option as a **future, unimplemented** optimization — see
`docs/spec/decisions.md` ADR-011.

**E5-S3 (Notification dispatch):** FCM reuses the existing `firebase-admin` dependency (already
present for Auth's OTP/Google verification) via a second `FirebaseMessaging` bean — no new SDK.
SMTP added `spring-boot-starter-mail` (a library, not a hosted service — free-tier compatible).
`AlertDispatchServiceImpl` respects `user_preferences.alert_channels` (push/email toggles,
already existed for this exact purpose) and sets `delivered_at` **at most once**, regardless of
how many channels succeeded — sidesteps the "duplicate delivered_at update" ambiguity the
E5-S3-T2 DoD calls out for the both-channels-succeed case. **Removed `FCM_SERVER_KEY` from
`docs/operations/deployment.md`**: the epic's original text named it for a legacy FCM HTTP client, which
Google has deprecated in favor of the Admin SDK credential this project already uses — see the
deployment.md addendum.

**E5-S4 (Alerts API):** standard cursor pagination mirroring `/transactions`, default page size
20 (docs/spec/api.md's own Pagination-section example, not an explicit mandate elsewhere).

**Verification — confirmed green against real Docker (2026-07-03):** unit tests
(`./gradlew test`) and the full integration suite (`./gradlew integrationTest`, real
Testcontainers Postgres) both pass. `BudgetControllerIntegrationTest` (5/5) and
`AlertControllerIntegrationTest` (3/3) — this epic's new suites — passed on the first run.
The first full-suite run showed 2 unrelated failures (`AuthControllerIntegrationTest`,
`CategorizationJobsIntegrationTest`, both pre-existing from Epic 1/4), each a Testcontainers
`ContainerLaunchException: Timed out waiting for log output matching '.*database system is
ready to accept connections.*'` — container-startup flakiness from ~15 Postgres containers
launching back-to-back within 12.5 minutes, not a code defect. Re-running just those two
classes in isolation passed cleanly (9/9 and 2/2), confirming it was resource contention, not
a regression. Unlike every prior epic in this repo's history, Docker was available this
session, so this is the first epic verified against real Testcontainers end-to-end rather than
compiled-but-unexecuted.

**External-service verification honesty note (added on request before push, 2026-07-03):**
the above covers Postgres/schema/RLS and the HTTP API layer, both genuinely exercised. Two
features in this epic touch external services that were **not** verified end-to-end, and
this is called out explicitly so "tests pass" is never conflated with "verified against the
real service":

- **FCM push (E5-S3-T1)** and **SMTP email (E5-S3-T2)**: `AlertDispatchServiceImplTest`
  covers the dispatch *orchestration* logic (channel-enabled checks, token/email presence,
  `delivered_at` bookkeeping) against **mocked** `FcmClient`/`MailClient` interfaces.
  `FcmClientImpl` and `MailClientImpl` — the classes that actually call
  `FirebaseMessaging.send()` and `JavaMailSender.send()` — have **zero test coverage of any
  kind**, not unit, not integration, not even a stub server (contrast with
  `CategorizationJobsIntegrationTest`'s local `HttpServer` stub for FastAPI). No real
  Firebase project or SMTP credentials exist anywhere in this repo or were available this
  session (`FIREBASE_PROJECT_ID`/`EMAIL_SMTP_HOST` are empty in every profile;
  `.env.example` holds only placeholders). **Status: implemented, not end-to-end verified —
  real credentials required before this can be considered production-ready.**
- **Firebase Auth (OTP/Google verification)** — pre-existing since Epic 1, unchanged here:
  every integration test substitutes `FirebaseAuthTestConfig`, a fixed-token test double, for
  the real `FirebaseAuthService`. No test in this repo has ever run against a real Firebase
  project.
- **Postgres via Testcontainers vs. real Supabase**: schema/RLS/query correctness is
  genuinely verified against a real Postgres 16 engine, but this is not the actual hosted
  Supabase instance — `SUPABASE_URL`/`SUPABASE_KEY` and any free-tier-specific behavior
  (connection pooling limits, etc.) were never exercised.

## Epic 6 — [EMI & Recurring Payment Detection](../epics/epic-06-emi-and-recurring.md)

- [x] E6-S1-T1 — Rolling-window grouping + tolerance matching
- [x] E6-S2-T1 — Wire detection into the alert evaluator
- [x] E6-S2-T2 — Confirm-as-subscription / dismiss flow

### Epic 6 close-out

Implemented as specified across three story commits (E6-S1, E6-S2-T1, E6-S2-T2), following a
handoff review that resolved several spec ambiguities with the project owner before any code
was written:

**E6-S1 (`RecurringPaymentDetector`):** pure static logic in `com.spendwise.alerts`, no
Spring/DB dependency, unit-tested standalone per the epic's own parallelization note. Two rules
the epic explicitly left for implementation to define, resolved with the project owner
up front:
- **Amount tolerance** ("within ±10% of each other") is anchored to each cluster's own minimum
  (`max ≤ min × 1.10`), not pairwise-chained — chaining (100→110→121) would let the effective
  band drift past 10% across a long chain.
- **`emis` exclusion** uses the most conservative rule available: a transaction is excluded only
  if its id is an active `emis` row's `source_transaction_id` (exact match). No label/amount
  correlation is attempted for manually-entered EMIs — a fuzzy match risks silently hiding a
  legitimate alert, which is worse than an occasional redundant one. Per the project owner: "if
  confidence is insufficient, do not auto-classify a transaction as an EMI."

**E6-S2-T1 (wired into `AlertEvaluatorJob`):** reuses the existing 30-minute schedule (per the
epic's own text and `docs/spec/decisions.md` ADR-011's scheduled-over-event-driven reasoning) as a
second cross-user pass, independent of the budget-evaluation loop since it iterates a different
user set. Two new `spendwise_jobs`-backed bulk reads were added, mirroring
`findAllSpendForMonth`/`findAllUncategorized`: `TransactionService.findAllForRecurringDetection`
and `EmiService.findAllActiveSourceTransactionIds`. Suppression is **calendar-month scoped**
(not indefinite), keyed on merchant identity + an amount band rather than `category_id` — an
explicit project-owner decision so a still-recurring charge surfaces again in a later month even
if this month's alert was dismissed without being confirmed. Always `MEDIUM` priority — an
explicit project-owner decision (in-app only, never pushed/emailed), since `docs/spec/requirements.md`
never assigned this alert type a tier.

**E6-S2-T2 (confirm/dismiss):** `POST /alerts/:id/confirm` creates the linked EMI
(`EmiService.createFromDetection`) and marks the alert read; `due_day` is left `null` rather
than inferred from the transaction date (project-owner decision — the user sets it afterwards
via the existing `PUT /emis/:id`). Idempotent via a find-before-insert check plus a
`DuplicateKeyException` fallback for the race case, so a double-confirm returns the
already-linked EMI rather than violating `idx_emis_source_txn` or 500ing. Dismiss needed no new
endpoint — it reuses `PUT /alerts/:id/read` directly. This story extends Alerts→Transaction from
read-only to read/write (EMI creation); `docs/spec/architecture.md`'s dependency table was updated
*before* this code was written, per explicit project-owner sign-off, mirroring how Epic 5's own
table gaps were resolved.

**Verification honesty note:** the full unit suite (`./gradlew test`) passes, including all new
`RecurringPaymentDetectorTest` (7), `AlertEvaluatorJobTest` (+3), `AlertsServiceImplTest` (+5),
and `EmiServiceImplTest` (+3) cases. Three new integration tests were written
(`RecurringPaymentEvaluatorIntegrationTest`'s detect-then-rerun-no-duplicate case, and
`AlertControllerIntegrationTest`'s confirm/double-confirm and dismiss cases) and confirmed to
**compile** against the `integrationTest` source set, but — unlike Epic 5's session — Docker was
not available in this session, so `./gradlew integrationTest` itself was never run. **Status:
implemented and unit-verified; the three new integration tests are compiled-but-unexecuted and
should be run against real Testcontainers Postgres before this epic is considered fully
verified.**

**Update (2026-07-03, during the Epic 7 session):** Docker was reachable after all — the
earlier unavailability was a `$PATH` issue in that session's shell, not Docker Desktop being
down (see Epic 7's close-out note for the diagnosis). A full `./gradlew test integrationTest`
run confirms `RecurringPaymentEvaluatorIntegrationTest` and the new
`AlertControllerIntegrationTest` confirm/dismiss cases all pass against real Testcontainers
Postgres. This epic is now fully verified, not just unit-verified.

**Superseded (2026-07-11, ML strategy phase):** E6-S1's strict detection rule (3+, ±10%,
60-day window) is **no longer the production gate** — this is not a revert of the above, it's
a phase transition this epic's own scope predates. `RecurringPaymentDetector` now proposes
loosened candidates (2+, ±40%, 400-day window) that `CategorizationService#predictRecurring`
(a trained classifier) scores; only candidates the model judges recurring produce an alert.
The strict rule survives only as the classifier's bootstrap-label definition
(`ml/training/recurring_labels.py`). Full reasoning in ADR-012 (`docs/spec/decisions.md`);
schema in `docs/spec/database.md` (`emis.cadence`/`confidence_score`,
`recurring_corrections`); not itself a new Epic 6 story — tracked as ML-strategy-phase work,
not a backlog task. Also: this same testing pass found and fixed a real bug — confirming or
dismissing an alert created *before* this change (payload missing the new ML fields) threw a
`NullPointerException` in `AlertsServiceImpl#recordRecurringCorrection`; fixed to no-op the
correction write (confirm/dismiss itself still succeeds) when a payload predates this shape,
with two new tests covering it.

## Epic 7 — [Analytics & Export](../epics/epic-07-analytics-and-export.md)

- [x] E7-S1-T1 — `GET /analytics/summary`
- [x] E7-S1-T2 — `GET /analytics/categories`
- [x] E7-S1-T3 — `GET /analytics/comparison`
- [x] E7-S1-T4 — `GET /analytics/trends`
- [x] E7-S2-T1 — `GET /analytics/export/csv`
- [x] E7-S2-T2 — `GET /analytics/export/pdf`
- [x] E7-S3-T1 — Architecture test: Analytics is read-only

### Epic 7 close-out

Implemented as a single new module, `com.spendwise.analytics`, previously an empty
placeholder. A handoff review (dependency check against Epic 3/4 — both complete;
confirmed Epic 6 was mid-flight in a parallel session and not a hard dependency per
`DEPENDENCY-GRAPH.md`) preceded any code, resolving two architectural questions with
the project owner up front:

1. **Data access:** Analytics owns its own `AnalyticsRepository`, querying
   `transactions`/`transaction_categories`/`categories` directly via RLS-scoped
   `JdbcTemplate` — it never calls `TransactionService` or any other module's
   repository/service class. This matches `docs/spec/architecture.md`'s literal Analytics
   row ("reads from all modules," "contains no business logic") more directly than
   routing through Transaction's narrow read-method pattern (Budget's Epic 5
   precedent) would have, and lets `AnalyticsBoundaryTest` (E7-S3-T1) assert something
   stronger than "no write calls": no class in `com.spendwise.analytics` depends on
   any class in another module's package at all.
2. **PDF library:** [OpenPDF](https://github.com/LibrePDF/OpenPDF) 2.2.2 (LGPL/MPL,
   chosen over iText 7 specifically to avoid AGPL's network-use copyleft obligation for
   a hosted service) — a new `backend/build.gradle.kts` dependency, flagged the same
   way `spring-boot-starter-mail` was flagged in Epic 5 (a plain library, free-tier
   compatible, no hosted service).

**E7-S1 (aggregation queries):** `/summary`, `/categories`, `/trends` all require
explicit `from`+`to` (inclusive both ends, matching `TransactionRepository.listPage`'s
existing convention; 400 if either is missing). `/comparison` is the exception — no
`from`/`to`, just `granularity` (default `month`), anchored to *today* (server clock,
UTC): current calendar week/month/year vs. the immediately preceding one of the same
length. Not explicit in the epic text — flagged as an assumption during planning,
resolved against `docs/operations/user_flows.md`'s "compare this month vs. last" framing; see
`docs/spec/api.md`'s new Epic 7 addendum.

**E7-S2 (export):** CSV is hand-written (no library — ~15 flat columns, RFC4180-style
escaping); reuses the explicit-column-list discipline `TransactionRepository` uses
(never `SELECT *`) even though Analytics' own `AnalyticsExportRow` type has no
`sms_raw_text` field to begin with. `GET /analytics/export/pdf` accepts either
`from`+`to` or `financialYear=<YYYY>` (Indian financial year, April–March, matching the
product's India focus and the seed dataset's own April–March framing) — exactly one
must be present, 400 otherwise.

**E7-S3 (read-only enforcement):** `AnalyticsBoundaryTest` mirrors
`CategorizationBoundaryTest`'s ArchUnit pattern but is stronger per the data-access
decision above.

**Verification — confirmed green against real Docker (2026-07-03):** unit tests
(`./gradlew test`, including `AnalyticsBoundaryTest` and `AnalyticsServiceImplTest`)
and `AnalyticsControllerIntegrationTest` (8/8, real Testcontainers Postgres —
hand-computed totals for summary/categories/trends/comparison, CSV row/column
assertions, PDF magic-number + extracted-text assertions via OpenPDF's own
`PdfTextExtractor`) all pass. Docker was reachable this session but not on the
Bash tool's default `$PATH` — Docker Desktop's `resources/bin` directory has to be
added to `PATH` (or exported per-session); this is a one-time environment fix worth
making at the user-profile level so future sessions don't need to rediscover it.

**Incidental fix, separate commit (pre-existing Epic 5 bug, not Epic 7 scope):** the
full-suite run alongside this epic surfaced a real, pre-existing, order-dependent
flake in `AlertControllerIntegrationTest` — `markReadFlipsIsReadWithoutTouchingDeliveredAt`
and `confirmingANonRecurringPaymentAlertReturns400` both seeded a `CATEGORY_OVERSPEND`
alert for category id 5 under the class's one shared fixture user (every `loginPhone`
call in that class resolves to the same fixed test-auth token regardless of the phone
string passed), so Epic 5's own "don't re-alert the same thing within a month"
suppression logic made whichever method JUnit ran second throw
`NoSuchElementException` out of the test's `seedAlert` helper. Reproduced, fixed by
giving the two methods distinct category ids, and re-ran the class twice (opposite
method orderings both observed) to confirm the fix holds either way. This also
resolves Epic 6's own close-out caveat that its new integration tests
(`RecurringPaymentEvaluatorIntegrationTest`, `AlertControllerIntegrationTest`'s
confirm/dismiss cases) were "compiled but unexecuted" — Docker turned out to be
reachable this session (see the `$PATH` note above), and a full `./gradlew test
integrationTest` run confirms all of them pass alongside Epic 7's own suite.

## Epic 8 — [Recommendations & Chatbot](../epics/epic-08-recommendations-and-chatbot.md)

- [x] E8-S1-T1 — `LlmClient` interface + stub implementation
- [x] E8-S2-T1 — Recommendation generator job (every 6h, idempotent)
- [x] E8-S2-T2 — `GET /recommendations`, `PUT /recommendations/:id/dismiss`
- [x] E8-S3-T1 — Chatbot session endpoints
- [x] E8-S3-T2 — `POST /chatbot/message` with context injection

### Epic 8 close-out

Implemented as three new pieces — `com.spendwise.common.llm` (shared infra, not one of the 11
documented modules), `com.spendwise.recommendations`, `com.spendwise.chatbot` (both previously
empty placeholders) — following a handoff review (dependency check against Epic 3/Epic 7, both
complete; `git status`/`git branch -a` confirmed a clean `main` with no parallel-session work in
flight; schema check confirmed `recommendations`/`chatbot_sessions`/`chatbot_conversations` and
their RLS policies already existed from Epic 0 — no new migration needed) that resolved four
undocumented-default questions with the project owner before any code was written, the same
pattern Epics 5–7 used for their own gaps:

**E8-S1 (`LlmClient`):** CLAUDE.md's "no vendor has been selected... do not hardcode any LLM SDK
into business logic" has no prior art in this codebase (`MlClient` is a single concrete class
calling one already-known internal service, not an interface fronting a not-yet-chosen vendor).
Landed as `com.spendwise.common.llm.LlmClient` (interface) + `LlmConfig` (the config-driven
provider-selection point, `app.llm.provider`/`LLM_PROVIDER`, default and only value `stub`) +
`com.spendwise.common.llm.provider.StubLlmClient` (deterministic, no network call, no API key).
`LlmBoundaryTest` mirrors `CategorizationBoundaryTest`'s exact ArchUnit shape: no class outside
`com.spendwise.common.llm.provider` may depend on classes in that package — blocks
Recommendations/Chatbot from injecting `StubLlmClient` directly today, and transparently extends
to block a future vendor-SDK-backed implementation from leaking outside that same package later.
`StubLlmClient` renders `docs/spec/requirements.md`'s exact one-liner template for its one well-known
context shape (Recommendations' four keys) and falls back to a generic deterministic context echo
for any other shape (e.g. Chatbot's) — a real provider wouldn't need this special-casing at all.

**E8-S2 (Recommendations):** `RecommendationGeneratorJob` mirrors `AlertEvaluatorJob`'s exact
cross-user shape — bulk reads via the `spendwise_jobs` role, per-user/category persistence through
the normal RLS-enforced path. Its bulk read is a **new** `AnalyticsService.findAllCategorySpendForMonth`
(a new `AnalyticsRepository` method using its own `jobsJdbcTemplate`, added this epic) rather than
routing through the already-existing `TransactionService.findAllSpendForMonth` that Alerts already
uses for an identical query shape — a deliberate decision, confirmed with the project owner, to keep
both Analytics' Epic-7 "zero cross-module coupling" invariant and Recommendations' "may only call
Analytics" rule literally true, accepting ~15 lines of duplicated SQL as the cost. Two numbers
absent from any doc (`docs/spec/requirements.md`'s "38% more than last month" is illustrative only, not
a spec'd threshold) were confirmed as explicit defaults: **≥20% month-over-month increase on a
≥₹200 baseline** triggers a recommendation; **priority is always `medium`** (mirrors Epic 6's
identical precedent for recurring-payment alerts). Idempotency is a find-before-insert against
`idx_recs_user_category_active` (at most one active row per user+category) with a
`DuplicateKeyException` fallback for the race case — the same pattern `EmiService.createFromDetection`
already uses for its own confirm-flow idempotency.

**E8-S3 (Chatbot):** Session endpoints mirror existing module CRUD shapes; cross-user session
access 404s without leaking existence (RLS-scoped `findById` plus an explicit ownership check).
`POST /chatbot/message` reads Transaction history + Analytics summaries directly (both permitted
per docs/spec/architecture.md's Chatbot row; no new cross-user method needed here, since Chatbot is
request-scoped, not a background job) for a **fixed current + previous calendar month window on
every message, regardless of question wording** — confirmed with the project owner as the context
scope, since no NLU exists (or was built) to parse a date range out of the user's actual question;
this directly answers the epic's own milestone question ("How much did I spend on food last
month?") without any date-parsing logic.

**Verification — confirmed green against real Docker (2026-07-03):** unit tests (`./gradlew test`,
including `LlmBoundaryTest`, `StubLlmClientTest`, `RecommendationGeneratorJobTest`,
`RecommendationsServiceImplTest`, `ChatbotServiceImplTest`, and the new
`AnalyticsServiceImplTest`/`AnalyticsServiceImpl` case) and the full integration suite
(`./gradlew integrationTest`, real Testcontainers Postgres — `RecommendationControllerIntegrationTest`
(3/3: feed ordering, dismiss-then-refetch, cross-user 404), `RecommendationGeneratorJobIntegrationTest`
(1/1: a genuine crossing produces one recommendation, a re-run produces no duplicate), and
`ChatbotControllerIntegrationTest` (3/3: session list order, cross-user 404, message round-trip
persistence in chronological order)) all pass on the first run, alongside the full pre-existing
suite (20/20 integration test classes green). Docker required starting Docker Desktop manually
this session (it wasn't running at session start, distinct from Epic 7's `$PATH`-only issue) —
worth noting since two consecutive sessions have now hit a different Docker-availability snag.

## Epic 9 — [Android App UI](../epics/epic-09-android-app-ui.md)

- [x] E9-S1-T1 — Sign-up screen (Phone OTP / Google login)
- [x] E9-S1-T2 — Consent screen
- [x] E9-S1-T3 — SMS + notification permission flow
- [x] E9-S1-T4 — Onboarding questionnaire + optional bank statement upload
      (upload UI built against the documented contract; the backend endpoint
      does not exist — see close-out gap note)
- [x] E9-S1-T5 — Backfill trigger, foreground service start, land on dashboard
- [x] E9-S2-T1 — Dashboard screen
- [x] E9-S2-T2 — Transactions screen
- [x] E9-S2-T3 — Budget screen
- [x] E9-S2-T4 — EMI/Subscriptions screen
- [x] E9-S2-T5 — Chatbot screen
- [x] E9-S2-T6 — Settings screen

### Epic 9 close-out

Built as the first substantial work in `android/`'s UI module (until now a bare
`AppCompatActivity` placeholder), on top of Epic 2's headless SMS/Parser/Sync/Storage
packages, which were reused as-is (`SmsInboxBackfill`, `SmsForegroundService`,
`SyncScheduler`, `DeviceSessionStore`). A handoff review preceded any code: prerequisites
re-verified against git tags + this file (E1/E3/E5/E6/E7/E8 all complete, so every E9
story was unblocked per `DEPENDENCY-GRAPH.md`'s per-story table), and four
expensive-to-reverse architecture choices were resolved with the project owner up front:
**Jetpack Compose** (Material 3, bottom nav with 5 tabs + Settings via top bar),
**Retrofit + OkHttp + kotlinx.serialization**, **Hilt**, and
**EncryptedSharedPreferences** for token storage.

**Prerequisite gap found at handoff (same category as Epic 5's FCM-token gap):**
`POST /users/me/bank-statement` is documented in `docs/spec/api.md` and assumed by E9-S1-T4
("assumed available by this point"), but **was never implemented server-side** — no epic
owns it. Project-owner decision (2026-07-04): build the upload UI against the documented
contract with a soft-failure path ("upload isn't available yet — you can skip"), and
record the missing backend endpoint here as a known gap needing a home (it involves
server-side PDF parsing; roadmap/Epic-12 scoping call for the owner).

**Firebase (real project, first time in this repo's history):** the backend requires a
**Firebase ID token** for both auth paths (`OtpVerifyRequest(phone, idToken)` /
`GoogleLoginRequest(idToken)` — OTP SMS delivery itself happens client-side via the
Firebase SDK; `/auth/otp/send` is only the rate-limit backstop). The owner created project
`spendwise-21f03`, registered `com.spendwise` with the debug-keystore SHA-1/SHA-256, and
enabled Phone + Google providers. `android/app/google-services.json` is present locally
but **gitignored** (pre-existing `.gitignore` entry, respected) — so the Google Services
Gradle plugin is applied **conditionally on the file's presence**, and the
`default_web_client_id` resource is resolved by name at runtime rather than via `R.string`.
Both build paths verified: with the file (full local build) and without it (CI's checkout).
Release-keystore fingerprints are still to be added at Epic 12 (Firebase App Distribution).

**Toolchain (AGP 9.2.1 built-in Kotlin, embedded KGP 2.2.20):** Compose requires
`org.jetbrains.kotlin.plugin.compose` (version-locked to the embedded Kotlin, 2.2.20, as
is `plugin.serialization`); Hilt + KSP must be declared in the same Gradle scope
(dagger#3965) — KSP's version declaration moved to the root build file. `compileSdk` was
raised 36→37, required by androidx.hilt 1.4.0 / androidx.lifecycle 2.11.0 AAR metadata
(AGP auto-provisioned the platform).

**E9-S1 (onboarding wizard):** linear NavHost graph resuming at the first incomplete step
on relaunch (JWT → device key → SMS permission → questionnaire flag → backfill flag, each
persisted in `DeviceSessionStore`). Consent (ADR-005 all-or-nothing) renders the
`ConsentText` constants that also compose the `FULL_TEXT` snapshot POSTed to
`/users/me/onboarding` — single source of truth, pinned by unit test; the returned raw
device API key is stored immediately (its only occurrence, per docs/spec/api.md). SMS denial is
a hard-blocking screen with a settings deep link re-checked on resume; notification denial
proceeds (alerts in-app only). Backfill reuses `SmsInboxBackfill.create(context)` then
starts the foreground service + periodic sync and lands on the dashboard.

**E9-S2 (six screens):** Dashboard composes 4 endpoints with independently-failing
sections (alerts panel with dismiss + recurring-payment confirm-as-subscription,
recommendations feed with dismiss, tappable budget-progress rows drilling into the
category-filtered transaction list, and a hand-drawn Canvas 30-day trend line — no
charting library). Transactions: cursor pagination (server default 50/page) with
filter-generation guards against stale in-flight pages, category filter chips, detail
view, immediate-reflection category correction (optimistic update; corrections write to
`ml_corrections` server-side). Budget: progress bars + edit dialog pre-filled from
`/budgets/suggestions` when available. EMI: edit (PUT) / deactivate (PATCH, record
retained). Chatbot: session list ordered by `last_active_at`, thread with full history
reload (server owns persistence — survives app restarts by construction). Settings:
push/email alert-channel toggles (partial-update PUT), CSV/PDF export via the Analytics
endpoints downloaded to cache and handed to the share sheet (new `FileProvider`), privacy
policy link (placeholder URL constant — hosting it is a roadmap/E12 item), logout
(best-effort server revocation, local clear always).

**Token/session infra:** `DeviceSessionStore` migrated to `EncryptedSharedPreferences`
(one-time copy from Epic 2's plain prefs, then wiped; constructor accepts any
`SharedPreferences` so Robolectric tests — no Keystore — use a plain instance). A single
OkHttp client carries the SpendWise JWT (never the Firebase token — CLAUDE.md auth
pattern) via an interceptor, and a `TokenRefreshAuthenticator` handles 401 →
`/auth/token/refresh` → single retry, with cross-thread double-refresh protection; failed
rotation clears the session and routes back to sign-up.

**Verification:** full Android unit suite green — **59 tests, 0 failures** (37
pre-existing parser/sync/storage + 22 new: `DeviceSessionStoreTest` round-trips,
`ConsentTextTest` rendered-equals-persisted invariant, `TransactionsViewModelTest`
cursor threading/reset/hasMore-termination plus a genuine stale-drop race test (a gated
in-flight response from a superseded filter generation resolving late is discarded —
added after a `spec-invariant-reviewer` pass flagged the original claim as broader than
its coverage; everything else in that review came back clean), and
`TokenRefreshAuthenticatorTest` against MockWebServer — rotate-and-retry,
revoked-refresh session clear, single-retry cap). `assembleDebug` green both with and without `google-services.json`. **Honesty
note:** per `docs/operations/testing.md` (Espresso deferred), everything above is unit-level; the
epic's manual QA checklists — real OTP/Google sign-in on an emulator against a live
backend with the new Firebase project, seeded-SMS backfill run-through, screen-by-screen
QA against a seeded backend — have **not** been executed this session. The Working
Milestone (full onboarding-to-dashboard on an emulator) is implemented and compile/unit
verified, not yet demoed live. Note for that session: Firebase phone-auth on an emulator
needs a test phone number configured in the Firebase console, and the backend `.env`
needs `FIREBASE_PROJECT_ID`/`FIREBASE_PRIVATE_KEY` from `spendwise-21f03`.

**Cross-session note:** Epic 10 ran concurrently in this same working directory; its
`fa66884` commit incidentally included this epic's then-in-progress Gradle build files
(`android/build.gradle.kts`, `android/app/build.gradle.kts`) — harmless content-wise
(the same changes this epic needed, minus the later compileSdk bump), left as-is rather
than rewriting history.

## Epic 10 — [Web Dashboard](../epics/epic-10-web-dashboard.md)

- [x] E10-S1-T1 — Routes, layout, Firebase client login
- [x] E10-S1-T2 — Client-side token storage, refresh, protected routes
- [x] E10-S2-T1 — Dashboard page
- [x] E10-S2-T2 — Transactions page
- [x] E10-S2-T3 — Budget page
- [x] E10-S2-T4 — EMI/Subscriptions page
- [x] E10-S2-T5 — Chatbot page
- [x] E10-S2-T6 — Export page
- [x] E10-S2-T7 — Settings page
- [x] E10-S3-T1 — Client-side cache fallback with stale indicator

### Epic 10 close-out

Built as the first substantial work in `frontend/`, which until now was the Epic-0
skeleton (bare Next.js App Router, no styling/data/charts/test tooling). A handoff review
(dependency check confirmed E1/E3/E5/E6/E7/E8 all complete via git tags + STATUS, so every
E10 story was unblocked per `DEPENDENCY-GRAPH.md`'s per-story table; confirmed Epic 9 was
running concurrently in a separate session against `android/` — disjoint files, only
`STATUS.md` shared) preceded any code, resolving four tooling choices with the project
owner up front (expensive to reverse once 7 pages exist): **Tailwind CSS**, **SWR**,
**Recharts**, **Vitest + React Testing Library**.

**Foundational setup (new to the repo this epic):** Tailwind v4, SWR, Recharts, the
firebase client SDK, and a Vitest/RTL/jsdom test stack were installed and wired
(`postcss.config.mjs`, `vitest.config.mts`, `vitest.setup.ts`). Shared libs:
`lib/apiClient.ts` (fetch wrapper with a transparent 401→`/auth/token/refresh`→retry
interceptor, concurrent-401 coalescing so replay-detection isn't tripped, and a
`downloadFile` binary path for exports), `lib/auth.ts` (client token storage),
`lib/authApi.ts`, `lib/firebase.ts`/`firebaseLogin.ts`, `lib/useApi.ts` (SWR wrapper
exposing `isStale`), `lib/useCategories.ts`, `lib/format.ts`. **`docs/operations/testing.md` gained a
Next.js section** (it had none — a real doc gap this epic was the first to need) and the
frontend CI job gained an `npm test` step.

**Token storage decision (called out during the handoff, not guessed):** `docs/spec/architecture.md`
and `docs/operations/deployment.md` describe the frontend as a client app calling the Spring Boot API
directly (Vercel static hosting, no Next.js BFF/proxy layer). httpOnly cookies would need a
server layer that doesn't exist and is out of scope to invent, so secure client storage
(the epic explicitly allows either) is the only option consistent with the current
architecture.

**Pages (E10-S1 → E10-S3):** App Router route groups — `(auth)/login` public,
`(app)/*` behind an `AuthGuard` (uses `useSyncExternalStore` for the client-only token
check, avoiding a hydration mismatch and the new `react-hooks/set-state-in-effect` lint
rule). All seven E10-S2 pages built against the frozen `docs/spec/api.md` contract; two
contract details worth noting were honored exactly: the category-correction body is
snake_case `category_id` (the one endpoint with `@JsonProperty`, unlike the camelCase
budget/emi DTOs), and EMI deactivate is `PATCH` not `PUT`. Transactions uses
`useSWRInfinite` for cursor pagination (also the clean way to satisfy the setState-in-effect
rule) with optimistic local overrides for immediate category-correction reflection.

**E10-S3 (stale handling):** built on SWR's default keep-last-data-on-error behavior —
`useApi` derives `isStale`, and `DashboardView` shows a page-level `StaleBanner` (retry-all)
when any section is stale while every section keeps rendering its last-good data. Per the
epic's own note, the pattern is proven on the dashboard and documented for reuse on the
other pages rather than re-applied to each here.

**Verification:** full Vitest suite **12 files / 37 tests green** (`npm test`), `npm run
lint` clean, `npm run build` green (all 10 routes). Each story's "Required Tests" from the
epic file are covered. **Honesty note:** these are component/unit tests against a mocked
API plus a real production build — the app was **not** run end-to-end against a live
backend + real Firebase project this session (no Firebase project exists in this repo;
Firebase Auth has never been exercised against a real project — see Epic 5's close-out).
The Working Milestone's live browser demo (login → dashboard → kill-backend stale
fallback) is implemented and unit-verified but not yet manually smoke-tested against a
running backend; that remains a pre-launch step (Epic 12).

**Amended (2026-07-11, ML strategy phase):** the web Dashboard's Alerts card was
display-only at E10-S2-T1 close — no confirm/dismiss actions, unlike Android's E9-S2 spec
(`epic-09`), which explicitly built "alerts panel with dismiss + recurring-payment
confirm-as-subscription." Not a regression; E10-S2-T1's own scope never required it. Closed
during live end-to-end testing of the recurring-payment ML work (see Epic 6 close-out
addendum below): `AlertsSection`/`DashboardView` (`frontend/src/components/dashboard/`) now
call `POST /alerts/:id/confirm` and `PUT /alerts/:id/read` for unread alerts (Confirm shown
only for `recurring_payment`, matching the backend's own type check), and a new
`alertDetail()` helper surfaces merchant/amount/cadence (or the budget-alert equivalents)
from `payload` — previously every alert rendered as bare "Recurring Payment"/"Mid Month
Budget"/etc. with no distinguishing detail, via a dead `payload.message` check that never
matched any real payload shape. The notification bell (`NotificationsBell.tsx`) still has
neither improvement — flagged, not yet done.

## Epic 11 — [Admin Portal](../epics/epic-11-admin-portal.md)

- [x] E11-S1-T1 — Admin login issuing `ADMIN_JWT_SECRET`-signed tokens
- [x] E11-S2-T1 — `GET /admin/users`, `GET /admin/users/:id`
- [x] E11-S2-T2 — `GET /admin/analytics`, `GET /admin/analytics/comparison`
- [x] E11-S2-T3 — `GET /admin/logs`
- [x] E11-S2-T4 — `GET /admin/ml/accuracy`, `POST /admin/ml/retrain`
- [x] E11-S2-T5 — `DELETE /admin/users/:id` (full erasure, DPDP)
- [x] E11-S3-T1 — Minimal admin web screens

### Epic 11 close-out

Built as the first substantial work in `com.spendwise.admin` (previously an empty placeholder)
and the first `/admin` route anywhere in `frontend/`, following a handoff review that surfaced
one real gap before any code was written: **no admin login endpoint existed anywhere** —
`AdminJwtAuthFilter`/`SecurityConfig` (E1-S2-T1) validated an admin-signed token from day one, but
nothing ever issued one, and `docs/spec/api.md`'s `/admin` table never had a login row. Same category
of gap as Epic 5/8/9's FCM-token/bank-statement findings — resolved by building the missing piece
(`POST /admin/auth/login`) and documenting it, not by blocking.

**E11-S1 (admin login):** A single seeded admin credential (`ADMIN_USERNAME`/
`ADMIN_PASSWORD_HASH`, bcrypt-hashed, env-configured — never a regular user account with a role
claim, per CLAUDE.md) is checked in a new `AdminAuthController`, issuing a token via a new
`AdminJwtService` (mirrors `UserJwtService`, 24h TTL). `SecurityConfig` gained a fourth filter
chain, `adminLoginFilterChain` (`@Order(0)`), matching only `/api/v1/admin/auth/login` with no
auth filter at all — `AdminJwtAuthFilter`, unlike `UserJwtAuthFilter`, rejects outright when no
Bearer header is present rather than deferring to `authorizeHttpRequests`, so a bare `permitAll`
rule on the broader `/api/v1/admin/**` chain would never actually be reached for this path. A new
`AdminLoginRateLimiter` (copy of `OtpSendRateLimiter`'s shape, keyed by client IP) finally
implements docs/spec/security.md's long-documented-but-never-built "10 login attempts per IP per 15
min" policy.

**E11-S2 (API):** `AdminRepository` reads via the `spendwise_jobs` (BYPASSRLS) role for the two
things with no per-user anchor to scope RLS to — enumerating every user (`GET /admin/users`) and
the system-wide `admin_logs` table (`GET /admin/logs`) — broadening that role's sanctioned-use
list (`JobsDataSourceConfig`, `docs/spec/security.md`) beyond "`@Scheduled` jobs only" to include
Admin's request-scoped reads, a narrow documented exception, not a blanket bypass. Everything else
needed zero new bypass code: `GET /admin/users/:id`'s per-user detail calls
`TransactionService`/`BudgetService`/`AlertsService` with the target user's id, exactly the same
cross-module call shape every other module already uses (those services scope RLS to whatever id
is passed in, not the caller's own identity); `GET /admin/analytics(/comparison)` sums each user's
own `AnalyticsService.summary`/`.comparison` result rather than reimplementing Epic 7's queries;
`GET /admin/ml/accuracy` / `POST /admin/ml/retrain` delegate straight to
`CategorizationService` (already built for exactly this in E4-S3-T5) — the existing
`CategorizationBoundaryTest` ArchUnit rule already covers Admin's boundary, since it blocks any
package outside `com.spendwise.categorization` from touching `MlClient` directly.

**E11-S2-T5 (erasure, built and tested last per the epic's own note):** captures the target user's
phone/email/google_id and the ids of every `admin_logs` row referencing them *before* deleting,
then a single `DELETE FROM users WHERE id = ?` via the BYPASSRLS role cascades atomically to every
dependent table (confirmed `ON DELETE CASCADE` per docs/spec/database.md) and sets
`admin_logs.user_id = NULL` for the captured rows (`ON DELETE SET NULL`), then a follow-up pass
string-replaces the captured identifying strings out of those rows' `event_type`/`payload` with a
redaction marker — satisfying the DPDP erasure exception (null `user_id` isn't enough on its own).
Deliberately done entirely via the BYPASSRLS connection rather than the RLS session-variable path,
sidestepping an untested edge case (the `admin_logs` `SET NULL` cascade is an UPDATE, which would
need to satisfy a `WITH CHECK` clause against a post-update NULL `user_id` under the normal RLS
path). Honesty note: the cascade delete and the log-scrub pass are two separate statements, not
one Spring `@Transactional` unit (no second `PlatformTransactionManager` exists for the jobs
`DataSource` — matches the existing precedent that no `jobsJdbcTemplate` caller in this codebase
uses `@Transactional`); a DB failure between them is a narrow, accepted, documented risk.

**E11-S3 (frontend):** a separate route tree (`app/admin/(public)/login`,
`app/admin/(protected)/{users,users/[id],logs,ml}`) using route groups so the login page and the
token-gated pages get different layouts despite sharing the `/admin` path prefix — a plain
`AdminAuthGuard`/`adminApiClient`/`adminAuth.ts` trio mirrors the user-facing equivalents but
simpler (no refresh-token concept for the 24h admin session; a 401 just clears the token and lets
the guard redirect). `DeleteUserDialog` gates the irreversible delete behind typing the exact
user identifier first (epic's explicit DoD) — its Required Test and an integration-level test
through `UserDetailView` both cover the button staying disabled until the identifier matches
exactly.

**Verification — confirmed green (2026-07-05):** Backend: `./gradlew test integrationTest`,
**276/276 tests pass** (real Testcontainers Postgres, Docker Desktop confirmed working after an
earlier startup failure in this environment resolved itself). All 4 new admin integration test
classes needed a `@BeforeAll` bootstrap of the `spendwise_jobs` role, mirroring
`CategorizationJobsIntegrationTest`'s exact pattern — a bare Testcontainers Postgres image never
runs `db-init/`, so any test touching `AdminRepository` (including the login-flow smoke test,
which hits `/admin/logs`) needs this or fails with `password authentication failed for user
"spendwise_jobs"`. Two other test-only bugs surfaced and were fixed during this pass, neither a
production defect: (1) `AdminAuthControllerIntegrationTest` deserialized `/admin/logs`'s JSON
*array* response as a `Map`; (2) the erasure test's admin_logs payload was seeded with the test's
own requested phone number rather than the phone `FirebaseAuthTestConfig`'s fixed OTP-test-token
actually persists (identity comes from the token, not the request body — the same fact
`AuthControllerIntegrationTest` already documents) — fixed by fetching the real persisted phone
before seeding. Frontend: `npm test` **50/50 tests pass** (18 files), `npm run lint` clean,
`npm run build` clean (all 5 admin routes present: `/admin/login`, `/admin/users`,
`/admin/users/[id]`, `/admin/logs`, `/admin/ml`). Two frontend tests needed their timeout raised
from the 5s default to 15s after flaking under full-suite load on this machine — both confirmed to
pass reliably in isolation, a machine-load characteristic (this session also hit an unrelated
Docker Desktop startup failure requiring a manual retry), not a code defect.

**Not yet done, deliberately out of scope for this pass:** live manual smoke test against a real
running backend + browser (no Firebase project or live deployment available in this environment,
consistent with every prior epic's same caveat); the epic explicitly excludes Epic 12
(Deployment/Monitoring/Launch), which remains untouched.

## Epic 12 — [Deployment, Monitoring & Launch](../epics/epic-12-deployment-and-launch.md)

- [ ] E12-S1-T1 — Deploy Spring Boot backend
- [ ] E12-S1-T2 — Deploy FastAPI ML service
- [ ] E12-S1-T3 — Deploy Next.js frontend
- [ ] E12-S1-T4 — Finalize production Supabase project + RLS verification
- [ ] E12-S2-T1 — Sentry integration (both backend services)
- [ ] E12-S2-T2 — `GET /api/v1/health` implementation
- [ ] E12-S2-T3 — UptimeRobot monitor
- [ ] E12-S3-T1 — Firebase App Distribution release
- [ ] E12-S4-T1 — E2E golden path against deployed environment
- [ ] E12-S4-T2 — Security checklist verification against production
- [ ] E12-S4-T3 — Go-live checklist & rollback plan

---

**Progress: 114 / 125 tasks complete.** Update this line's count as you check items off (or
leave it — it's a convenience, not a requirement).

## Post-MVP: UI/UX Redesign (not backlog tasks)

A user-directed **whole-web-app visual redesign** is underway on top of the completed MVP. This
is not part of the Epic 0–12 backlog — it was done during `CLAUDE.md`'s former "UI/UX Refinement &
Product Polishing" phase (since superseded — `CLAUDE.md`'s Current Phase section has since moved
through "Feature Enhancement & Demo Data Design" to "ML Strategy & Model Design"). Recorded here
only so the redesign's cross-cutting changes aren't lost. Nothing below changes any Epic's
done/not-done state.

**Pages redesigned so far (updated 2026-07-09 — Contacts/Received/grouping, Profile/Settings split):**

- **Transactions** — category-summary tile grid (per-category spend + share of total, click a tile
  to filter the list, click again to clear; Uncategorized as a 13th tile), plus a trailing
  **Received** tile (every credit-direction transaction, any category — `GET /transactions?direction=credit`);
  a money-spent/money-received header, with a centered **month-stepper** (`MonthStepper`, shared
  with Analytics) alongside it. Selecting any tile now shows a **grouped, expandable list** instead
  of a flat table — transactions group by matched contact or fall back to the raw payee string, with
  summed totals per group; Transfers/Received additionally get **Family/Friend/Self/Settlement/Other**
  filter chips sourced from Contacts. Category-filtered (non-Received) views remain **debit-only**
  (money received never shows inside a category filter). Each transaction's category shows as a
  color swatch **box** next to its dropdown (not the earlier tiny circle). Spend renders red, credit
  renders green, wherever both directions appear together in one view (design-system.md §3.3/§6.4).
- **Analytics** — category deep-dive rework; a centered month-stepper in its header (same
  `MonthStepper` component now shared with Transactions, promoted from Analytics-only to
  `components/shared/`).
- **Planning / Budgets** — no-scroll category grid, per-category budget slider (₹100 steps, max
  scaled from the suggestion) pre-filled from a **6-month** suggestion average; read-only total-budget
  header; per-category drill-through to the filtered Transactions view.
- **Planning / EMIs & Subscriptions** — restyled to the shared card/component language (data +
  interactions unchanged).
- **Profile** (new page, `/profile`, reached from the avatar menu — not the top nav) —
  **Personal Info** (phone read-only, editable email, member-since) and **Contacts** (family/
  friend/self/settlement tagging for Transfer transactions, matched client-side against
  `recipient_name`/`upi_id` — ADR-010) tabs. Split out of the old Settings "Profile" tab, which no
  longer exists; `/profile` and `/settings` used to both route to the same place and are now distinct.
- **Settings** — now **Preferences / Appearance / Security / Privacy & Data / Export** tabs
  (Profile and Contacts moved to the new Profile page above). Appearance carries the real
  **Light/Dark/System theme toggle** (`next-themes`, `data-theme` seam), relocated from the old
  Profile tab. Security and Privacy & Data are **placeholders** — styled like a real settings page
  (login methods, 2FA, active sessions, data export, account deletion), not wired to any endpoint,
  deliberately deferred per the project owner's instruction. The standalone `/budget`, `/emis`,
  `/export` routes were removed (Planning + Settings tabs cover them).
- **Account menu** (avatar, top-right) — Profile · Settings · Help & assistant · **Upload statement**
  (new) · theme toggle · Log out. Upload statement opens a dialog with a PDF/CSV file-picker shape
  but is **UI-only**: `POST /users/me/bank-statement` is documented in `docs/spec/api.md` but has no
  server-side implementation at all (no controller, no PDF parser, no CSV parser) — an acknowledged,
  unscoped gap (same category as the earlier FCM-token gap this file already tracked), matching the
  soft-fail treatment the Android app already uses for this endpoint.

**Backend/API changes made in service of the above (all additive, docs/spec/api.md updated):**

- `GET /analytics/categories` now appends a synthetic **Uncategorized** row (`categoryId: null`),
  debit-only, only when such transactions exist in range. `/analytics/summary` + `/analytics/comparison`
  unchanged.
- `GET /transactions?category=` now accepts the literal `uncategorized` (no-category filter) in
  addition to a numeric id; and whenever a category filter is active, the list is debit-only.
- `GET /transactions` gained a `direction` query param (`credit`/`debit`), independent of `category`
  — backs the Received tile/view. Invalid values 400 with `INVALID_DIRECTION`.
- New `contacts` table (V10 migration, user-scoped RLS) + `GET/POST/PUT/DELETE /api/v1/contacts`
  CRUD, owned by the User module (`com.spendwise.user`) — per-user counterparty metadata (ADR-010).
  Never a new ML category; never joined onto `transactions`/`transaction_categories` server-side —
  matching happens client-side only.
- `/budgets/suggestions` trailing-average window changed 3 → 6 months.

**Shared frontend infra added:** category color/icon helpers (`lib/categories.ts`); the theme system
(`next-themes` + `styles/globals.css` `data-theme` tokens); the shared date-range context
(`lib/date-range.tsx` `DateRangeProvider`) plus `MonthStepper` (`components/shared/`); `lib/contacts.ts`
(contact matching/grouping helpers, `useContacts`); `PageHeader`'s optional `center` slot
(`components/shared/ui.tsx`), true-centered via absolute positioning independent of `action` width.

**Verification:** backend unit suite green as of 2026-07-09 (re-run after the `direction` filter and
Contacts changes above — includes new `ContactServiceTest` coverage). Frontend not rebuilt/retested
this round per `CLAUDE.md`'s Frontend Development Workflow policy (the dev server stays running and
the user verifies manually) — the last recorded frontend `npm test`/`npm run build` result predates
this round of changes and should not be treated as still current; re-run explicitly if you need a
fresh signal.
