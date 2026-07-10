# Epic 12 — Deployment, Monitoring & Launch

**Working Milestone:** All four services run on free-tier hosting; `GET /api/v1/health`
is green and monitored; Sentry receives errors from both backend services; the Android
APK is distributed to testers via Firebase App Distribution; the E2E golden path test
passes against the deployed environment; the full security checklist has been verified
against the live system. This is the MVP launch.

Hosting **account creation** (not deployment) has no code dependency and can start as
early as Epic 0, in parallel with everything else — see `../DEPENDENCY-GRAPH.md`.

---

### E12-S1 — Hosting Setup

#### E12-S1-T1 — Deploy Spring Boot backend

- **Objective:** Get the backend running on a free-tier, always-on host.
- **Expected Deliverable:** Deployed Spring Boot service on the chosen platform
  (Render/Railway/Fly.io, finalized during setup per `docs/operations/deployment.md`), all env vars
  from the Spring Boot list set in the platform dashboard, `./gradlew build -x test` JAR deployed.
- **Definition of Done:** Service is reachable over HTTPS; `GET /api/v1/health` returns
  `200` with `db: connected`; platform is configured always-on, not spin-down-on-inactivity
  (required since background `@Scheduled` jobs must keep running).
- **Required Tests:** Manual verification: hit the deployed health endpoint; confirm a
  scheduled job (e.g., alert evaluator) actually fires by checking its effect after 30+ minutes idle.
- **Estimated Complexity:** Medium
- **Depends on:** all backend epics (1, 3, 4, 5, 6, 7, 8, 11) functionally complete
- **Grounded in:** `docs/operations/deployment.md` "Deploying Spring Boot Backend", "Architecture Overview" (always-on preference), env var list.

#### E12-S1-T2 — Deploy FastAPI ML service

- **Objective:** Get the ML service running with internal-only networking to the backend.
- **Expected Deliverable:** Deployed FastAPI service; `FASTAPI_ML_URL` on the backend
  pointed at its internal address; `X-Internal-Key` verified working across the real network boundary (not just in tests).
- **Definition of Done:** A real (non-test) `/predict` call from the deployed backend to
  the deployed ML service succeeds; the ML service is confirmed **not** publicly reachable
  (e.g., a direct external request without `X-Internal-Key` from outside the platform's
  private network is rejected or unroutable).
- **Required Tests:** Manual verification: attempt to hit the deployed ML service's
  `/predict` directly from a public network without the internal key/network path and confirm it fails.
- **Estimated Complexity:** Medium
- **Depends on:** E4-S1-T1, E4-S2-T2 (real trained model, not a placeholder), E12-S1-T1
- **Grounded in:** `docs/operations/deployment.md` "Deploying FastAPI ML Service", `FASTAPI_ML_URL` note; `docs/spec/architecture.md` "Internal access only" note.

#### E12-S1-T3 — Deploy Next.js frontend

- **Objective:** Get the web dashboard live on Vercel.
- **Expected Deliverable:** Vercel project connected to the repo, auto-deploying `main`;
  `NEXT_PUBLIC_*` env vars set in the Vercel dashboard.
- **Definition of Done:** Pushing to `main` triggers an automatic deploy with no manual step; the deployed site can log in against the deployed backend.
- **Required Tests:** Manual verification: push a trivial change, confirm auto-deploy, confirm login works end-to-end against production.
- **Estimated Complexity:** Small
- **Depends on:** Epic 10 functionally complete, E12-S1-T1
- **Grounded in:** `docs/operations/deployment.md` "Deploying Frontend" (Vercel auto-deploys, no manual step).

#### E12-S1-T4 — Finalize production Supabase project + RLS verification

- **Objective:** Move off any local/dev Supabase project onto the production one, with
  migrations applied and RLS verified in that environment specifically (not assumed carried
  over from dev).
- **Expected Deliverable:** Production Supabase project with all migrations from Epic 0 applied; RLS policies confirmed active. Also covers manually creating both non-superuser
  roles that `backend/db-init/*.sql` only provisions for local Docker Compose — `spendwise_app`
  (01, Epic 0) and, since Epic 4, `spendwise_jobs WITH BYPASSRLS`, `GRANT spendwise_app TO
  spendwise_jobs` (02, E4-S3-T3/T4) — against the real Supabase Postgres instance, since
  Supabase doesn't run `docker-entrypoint-initdb.d` scripts. See `docs/spec/security.md`
  "Cross-user reads for background jobs" for the exact role/grant statements.
- **Definition of Done:** The RLS cross-user isolation test from E0-S2-T6 is re-run (or
  re-verified) against the production project, not just the dev/CI one. `spendwise_jobs`
  confirmed to have `BYPASSRLS` and correctly read cross-user data in production (a targeted
  smoke test of the categorization retry/ML retraining jobs, not just the isolation test).
- **Required Tests:** Re-run of E0-S2-T6's RLS isolation test against the production Supabase project.
- **Estimated Complexity:** Medium
- **Depends on:** E0-S2-T6
- **Grounded in:** `docs/spec/security.md` Supabase Row-Level Security; `docs/operations/deployment.md` Supabase (free tier).

---

### E12-S2 — Observability

#### E12-S2-T1 — Sentry integration (both backend services)

- **Objective:** Get error tracking wired in before real users hit production.
- **Expected Deliverable:** `SENTRY_DSN` configured in both Spring Boot and FastAPI; a
  deliberate test exception in each confirmed to appear in the Sentry dashboard.
- **Definition of Done:** A manually-triggered test error in each service appears in Sentry within a few minutes.
- **Required Tests:** Manual verification via a temporary debug endpoint or forced exception, removed after confirming.
- **Estimated Complexity:** Small
- **Depends on:** E12-S1-T1, E12-S1-T2
- **Grounded in:** `docs/operations/deployment.md` Monitoring Setup (Sentry); env var lists (`SENTRY_DSN`).

#### E12-S2-T2 — `GET /api/v1/health` implementation

- **Objective:** Build the actual health-check logic (this is foundational plumbing but
  belongs here since it's only meaningful once real DB/ML dependencies exist to check).
- **Expected Deliverable:** Unauthenticated endpoint checking Supabase connectivity and
  FastAPI ML reachability, returning the exact shape from `docs/operations/deployment.md`.
- **Definition of Done:** Returns `200` with `{"status":"healthy","db":"connected","ml":"reachable"}` when both are up; reflects a degraded state accurately when either is down (document the chosen degraded-state status code/shape).
- **Required Tests:** Integration test: healthy case matches exact schema; DB-down and ML-down simulated cases reflect accurately.
- **Estimated Complexity:** Small
- **Depends on:** E0-S2-T1, E4-S1-T1
- **Grounded in:** `docs/operations/deployment.md` "Health Check Endpoint" (exact response shape, unauthenticated, not in `docs/spec/api.md`).

#### E12-S2-T3 — UptimeRobot monitor

- **Objective:** Get automated uptime alerting on the deployed health endpoint.
- **Expected Deliverable:** UptimeRobot monitor pointed at the deployed `GET /api/v1/health`, 5-minute interval, email alert configured.
- **Definition of Done:** A deliberate temporary service stop triggers an UptimeRobot alert email.
- **Required Tests:** Manual verification: stop the service briefly, confirm the alert fires, restart.
- **Estimated Complexity:** Small
- **Depends on:** E12-S2-T2, E12-S1-T1
- **Grounded in:** `docs/operations/deployment.md` Monitoring Setup (UptimeRobot) + Health Check Endpoint section.

---

### E12-S3 — Android Distribution

#### E12-S3-T1 — Firebase App Distribution release

- **Objective:** Get a release build into testers' hands without the Play Store.
- **Expected Deliverable:** `./gradlew assembleRelease` build, distributed via
  `firebase appdistribution:distribute` to a testers group.
- **Definition of Done:** At least one tester device (beyond the developer's) successfully
  installs and runs the app against the deployed backend.
- **Required Tests:** Manual verification: install on a second device, complete onboarding against production.
- **Estimated Complexity:** Medium
- **Depends on:** Epic 9 functionally complete, E12-S1-T1
- **Grounded in:** `docs/operations/deployment.md` "Distributing Android APK".

---

### E12-S4 — Launch Verification

#### E12-S4-T1 — E2E golden path against deployed environment

- **Objective:** Prove the full SMS → parse → ingest → categorize → store → analytics
  chain works against the real deployed stack, not just CI's local/test instance.
- **Expected Deliverable:** Run `tests/e2e/test_golden_path.py` with `TEST_API_URL`
  pointed at the deployed backend, using a real test user + device API key registered
  against the production Supabase project.
- **Definition of Done:** All assertions in the golden path test pass against production:
  transaction appears in `/transactions`, has an ML-assigned category, and is reflected in `/analytics/summary`.
- **Required Tests:** The golden path test itself, run against production as its final gate (not just CI).
- **Estimated Complexity:** Medium
- **Depends on:** E12-S1-T1, E12-S1-T2, E12-S1-T4
- **Grounded in:** `docs/operations/testing.md` §4 (full E2E section, env vars, "run before every push to `main`... after ML model retraining" — here applied as the pre-launch gate specifically).

#### E12-S4-T2 — Security checklist verification against production

- **Objective:** Confirm every item in the API Security Checklist holds true in the actual
  deployed environment, not just in unit/integration tests against a local DB.
- **Expected Deliverable:** A completed checklist (copy of `docs/spec/security.md`'s API
  Security Checklist) with each item verified against production: HTTPS enforced, JWT
  validation live, admin routes reject non-admin tokens, `/ingest` dual-auth live, rate
  limiting live, `sms_raw_text` exclusion live, no secrets in the deployed artifact/repo,
  RLS active in production (reuses E12-S1-T4), refresh token revocation live, ML service internal-only live.
- **Definition of Done:** Every checklist item has a documented verification step and result; any failing item blocks launch until fixed.
- **Required Tests:** Manual verification per checklist item (some reuse automated
  integration tests re-pointed at production, e.g. rate-limit and dual-auth checks).
- **Estimated Complexity:** Large
- **Depends on:** E12-S4-T1
- **Grounded in:** `docs/spec/security.md` API Security Checklist (verbatim list).

#### E12-S4-T3 — Go-live checklist & rollback plan

- **Objective:** Have a documented, deliberate launch decision rather than an implicit one, with a way back out if something's wrong post-launch.
- **Expected Deliverable:** A short go-live checklist (all epics' milestones demoed, E12-S4-T1
  and T2 passed) and a rollback plan (how to revert each of the 4 deployed services to the
  previous known-good state).
- **Definition of Done:** Checklist fully checked off; rollback plan names the concrete
  command/dashboard action for each of the 4 services.
- **Required Tests:** N/A (planning document) — verify by dry-running the rollback steps
  for at least the backend service in a non-production context.
- **Estimated Complexity:** Small
- **Depends on:** E12-S4-T2
- **Grounded in:** `docs/operations/deployment.md` CD section ("Manual — developer reviews CI results and deploys manually").

---

## Parallel Execution within Epic 12

- E12-S1-T1, T2, T3 (the three service deploys) can happen in parallel once their
  respective epics are functionally complete; T4 (Supabase finalize) should happen first
  since T1 depends on it for a real DB connection.
- E12-S2 (observability) tasks are independent of each other once their respective service is deployed.
- E12-S3 (Android distribution) is independent of E12-S1/S2 and can happen in parallel.
- E12-S4 (launch verification) is strictly last — it is the final gate, not parallelizable
  with anything meaningful before it.
