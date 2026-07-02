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
run this session. Integration tests (Testcontainers/Docker-based, per
`docs/testing.md`) were written for every story to the Required Tests spec
and compile clean, but **could not be executed** — Docker is unavailable in
this environment — so their assertions are unverified pending a run of
`./gradlew integrationTest` on a machine with Docker.

## Epic 4 — [ML Categorization Service](../epics/epic-04-ml-categorization.md)

- [ ] E4-S1-T1 — `X-Internal-Key` middleware
- [ ] E4-S2-T1 — Preprocessing & feature extraction
- [ ] E4-S2-T2 — Train baseline scikit-learn classifier
- [ ] E4-S2-T3 — `POST /predict`
- [ ] E4-S2-T4 — `POST /retrain`
- [ ] E4-S2-T5 — `GET /evaluate` + evaluation script
- [ ] E4-S3-T1 — Categorization service interface + FastAPI client
- [ ] E4-S3-T2 — Wire Ingest → Categorization trigger
- [ ] E4-S3-T3 — Categorization retry job (every 30 min)
- [ ] E4-S3-T4 — ML retraining weekly job
- [ ] E4-S3-T5 — Admin-triggered retrain + evaluate (service-interface only)

## Epic 5 — [Budget & Alerts](../epics/epic-05-budget-and-alerts.md)

- [ ] E5-S1-T1 — `POST /budgets` (idempotent upsert)
- [ ] E5-S1-T2 — `GET /budgets`
- [ ] E5-S1-T3 — `GET /budgets/progress`
- [ ] E5-S1-T4 — `GET /budgets/suggestions`
- [ ] E5-S2-T1 — Mid-month 50%-of-total-budget rule
- [ ] E5-S2-T2 — Category 80%-approaching-limit rule
- [ ] E5-S2-T3 — Category overspend rule
- [ ] E5-S2-T4 — Alert evaluator scheduled job (every 30 min)
- [ ] E5-S3-T1 — FCM push dispatch
- [ ] E5-S3-T2 — SMTP email dispatch
- [ ] E5-S4-T1 — `GET /alerts` (cursor pagination + `is_read` filter)
- [ ] E5-S4-T2 — `PUT /alerts/:id/read`

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

**Progress: 15 / 125 tasks complete.** Update this line's count as you check items off (or
leave it — it's a convenience, not a requirement).
