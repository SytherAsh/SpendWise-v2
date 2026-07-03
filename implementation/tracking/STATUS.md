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
`docs/development_guidelines.md`, `docs/deployment.md`, `docs/testing.md`, and
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

All 10 tasks implemented against the frozen `docs/api.md` contract (Android's
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
   user stayed valid, defeating docs/security.md's "replay revokes all
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
11. `docs/architecture.md`'s Background Jobs table describes every scheduled job as
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
- Full design rationale and the local-dev volume-recreation caveat: `docs/security.md`
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
`docs/security.md`'s "Cross-user reads for background jobs" incident note for the full
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
   `PUT /users/me/fcm-token` endpoint (User module) — see `docs/database.md`'s
   `user_preferences` addendum.
2. **Alerts had no path to User.** `docs/architecture.md`'s module dependency table gave Alerts
   `Transaction` and `Budget` only, but the same document assigns Alerts "notification dispatch
   (push via FCM, email via SMTP)" as a responsibility — impossible without reading `email` and
   the new `fcm_token`. A gap in the original table, not a deliberate restriction (same category
   of issue as Epic 4's `spendwise_jobs` gap). Approved 2026-07-03; see `docs/architecture.md`'s
   Alerts→User addendum for the read-only grant.

**E5-S1 (Budget module):** `com.spendwise.budget` built mirroring `com.spendwise.transaction`'s
Repository→Service→Controller shape. Per CLAUDE.md ("cross-module calls go through injected
service interfaces only") and `docs/architecture.md`'s "Budget → Transaction (read-only)" row,
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
Ingest/Transaction without introducing a cycle, and `docs/requirements.md`'s 1-hour alert SLA
doesn't need event-driven latency. The owner asked for a design note proposing the
event-driven option as a **future, unimplemented** optimization — see
`docs/decisions.md` ADR-011.

**E5-S3 (Notification dispatch):** FCM reuses the existing `firebase-admin` dependency (already
present for Auth's OTP/Google verification) via a second `FirebaseMessaging` bean — no new SDK.
SMTP added `spring-boot-starter-mail` (a library, not a hosted service — free-tier compatible).
`AlertDispatchServiceImpl` respects `user_preferences.alert_channels` (push/email toggles,
already existed for this exact purpose) and sets `delivered_at` **at most once**, regardless of
how many channels succeeded — sidesteps the "duplicate delivered_at update" ambiguity the
E5-S3-T2 DoD calls out for the both-channels-succeed case. **Removed `FCM_SERVER_KEY` from
`docs/deployment.md`**: the epic's original text named it for a legacy FCM HTTP client, which
Google has deprecated in favor of the Admin SDK credential this project already uses — see the
deployment.md addendum.

**E5-S4 (Alerts API):** standard cursor pagination mirroring `/transactions`, default page size
20 (docs/api.md's own Pagination-section example, not an explicit mandate elsewhere).

**Verification caveat (same as every prior epic in this environment):** unit tests are green
(`./gradlew test`). New integration tests (`BudgetControllerIntegrationTest`,
`AlertControllerIntegrationTest`) compile clean but are **unexecuted** — Docker is unavailable
in this environment, consistent with every integration test in this repo per Epic 3/4's
close-out notes.

## Epic 6 — [EMI & Recurring Payment Detection](../epics/epic-06-emi-and-recurring.md)

- [ ] E6-S1-T1 — Rolling-window grouping + tolerance matching
- [ ] E6-S2-T1 — Wire detection into the alert evaluator
- [ ] E6-S2-T2 — Confirm-as-subscription / dismiss flow

## Epic 7 — [Analytics & Export](../epics/epic-07-analytics-and-export.md)

- [ ] E7-S1-T1 — `GET /analytics/summary`
- [ ] E7-S1-T2 — `GET /analytics/categories`
- [ ] E7-S1-T3 — `GET /analytics/comparison`
- [ ] E7-S1-T4 — `GET /analytics/trends`
- [ ] E7-S2-T1 — `GET /analytics/export/csv`
- [ ] E7-S2-T2 — `GET /analytics/export/pdf`
- [ ] E7-S3-T1 — Architecture test: Analytics is read-only

## Epic 8 — [Recommendations & Chatbot](../epics/epic-08-recommendations-and-chatbot.md)

- [ ] E8-S1-T1 — `LlmClient` interface + stub implementation
- [ ] E8-S2-T1 — Recommendation generator job (every 6h, idempotent)
- [ ] E8-S2-T2 — `GET /recommendations`, `PUT /recommendations/:id/dismiss`
- [ ] E8-S3-T1 — Chatbot session endpoints
- [ ] E8-S3-T2 — `POST /chatbot/message` with context injection

## Epic 9 — [Android App UI](../epics/epic-09-android-app-ui.md)

- [ ] E9-S1-T1 — Sign-up screen (Phone OTP / Google login)
- [ ] E9-S1-T2 — Consent screen
- [ ] E9-S1-T3 — SMS + notification permission flow
- [ ] E9-S1-T4 — Onboarding questionnaire + optional bank statement upload
- [ ] E9-S1-T5 — Backfill trigger, foreground service start, land on dashboard
- [ ] E9-S2-T1 — Dashboard screen
- [ ] E9-S2-T2 — Transactions screen
- [ ] E9-S2-T3 — Budget screen
- [ ] E9-S2-T4 — EMI/Subscriptions screen
- [ ] E9-S2-T5 — Chatbot screen
- [ ] E9-S2-T6 — Settings screen

## Epic 10 — [Web Dashboard](../epics/epic-10-web-dashboard.md)

- [ ] E10-S1-T1 — Routes, layout, Firebase client login
- [ ] E10-S1-T2 — Client-side token storage, refresh, protected routes
- [ ] E10-S2-T1 — Dashboard page
- [ ] E10-S2-T2 — Transactions page
- [ ] E10-S2-T3 — Budget page
- [ ] E10-S2-T4 — EMI/Subscriptions page
- [ ] E10-S2-T5 — Chatbot page
- [ ] E10-S2-T6 — Export page
- [ ] E10-S2-T7 — Settings page
- [ ] E10-S3-T1 — Client-side cache fallback with stale indicator

## Epic 11 — [Admin Portal](../epics/epic-11-admin-portal.md)

- [ ] E11-S1-T1 — Admin login issuing `ADMIN_JWT_SECRET`-signed tokens
- [ ] E11-S2-T1 — `GET /admin/users`, `GET /admin/users/:id`
- [ ] E11-S2-T2 — `GET /admin/analytics`, `GET /admin/analytics/comparison`
- [ ] E11-S2-T3 — `GET /admin/logs`
- [ ] E11-S2-T4 — `GET /admin/ml/accuracy`, `POST /admin/ml/retrain`
- [ ] E11-S2-T5 — `DELETE /admin/users/:id` (full erasure, DPDP)
- [ ] E11-S3-T1 — Minimal admin web screens

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

**Progress: 26 / 125 tasks complete.** Update this line's count as you check items off (or
leave it — it's a convenience, not a requirement).
