# Dependency Graph & Parallel Tracks

## Epic-level dependency table

| Epic | Hard dependency | Can start in parallel with |
|---|---|---|
| E0 Foundation | — | — (must go first) |
| E1 Auth & User | E0 | **E2** (Android parsing has zero backend dependency) |
| E2 Android SMS Parsing & Sync | E0 | **E1** (no shared code — parser is pure Kotlin, only shares the JSON contract already frozen in `docs/api.md`) |
| E3 Ingestion & Transactions | E0, E1 | E2 continuing in background; E4-S1/S2 (FastAPI service itself, see below) |
| E4 ML Categorization | E3 (only for S3 backend integration) | **E4-S1/S2** (FastAPI app + model training are a standalone Python service — start these the moment E0 lands, don't wait for E3) |
| E5 Budget & Alerts | E3, E4 | E6-S1 (recurring-detection algorithm is pure logic against synthetic data, doesn't need E5 wired yet) |
| E6 EMI & Recurring | E3, E5 | — |
| E7 Analytics & Export | E3, E4 | E5, E6 (different read paths, no shared write surface) |
| E8 Recommendations & Chatbot | E3, E7 | — |
| E9 Android App UI | incremental — see per-story table below | E10 (different platform, same backend) |
| E10 Web Dashboard | incremental — see per-story table below | E9 |
| E11 Admin Portal | E1, E3, E4, E5, E7 | E8, E9, E10 |
| E12 Deployment & Launch | all epics complete | E12-S1 hosting **account creation** (not deploys) can happen as early as E0 — provisioning Render/Railway/Fly.io/Vercel/Supabase/Firebase/Sentry/UptimeRobot accounts has no code dependency |

## Rule of thumb for parallel execution

Two tasks can run in parallel if neither's "Depends on" (in the epic files) references a
task ID the other one produces, and they don't touch the same files. The most valuable
parallel tracks in this backlog:

1. **Backend core vs. Android parser** (E1 + E3 vs. E2) — completely disjoint codebases
   sharing only a JSON contract that is already frozen in `docs/api.md`. Ideal for two
   people or two agent sessions from day one.
2. **FastAPI ML service vs. Spring Boot** (E4-S1/S2 vs. E3) — the ML service and its model
   training are a standalone Python process. Only E4-S3 (Categorization module's HTTP
   client) needs E3's Transaction module to exist.
3. **Android UI vs. Web dashboard** (E9 vs. E10) — once a backend epic (E5, E6, E7, E8)
   lands, its corresponding screen can be built on both platforms simultaneously by
   different people without either blocking the other.
4. **Analytics vs. Budget/Alerts/EMI** (E7 vs. E5/E6) — Analytics is strictly read-only
   (`docs/architecture.md` module dependency rules) and reads from Transaction/Categorization
   only, so it never contends with Budget/Alerts/EMI's write paths.

## E9 (Android UI) per-story dependency

| Story | Depends on |
|---|---|
| E9-S1 Onboarding UI | E1 (auth+onboarding endpoints), E2 (parser/sync/backfill) |
| E9-S2-T1 Dashboard screen | E5 (budget progress), E7 (summary/trends), E8 (recs feed) |
| E9-S2-T2 Transactions screen | E3 |
| E9-S2-T3 Budget screen | E5 |
| E9-S2-T4 EMI/Subscriptions screen | E6 |
| E9-S2-T5 Chatbot screen | E8 |
| E9-S2-T6 Settings screen | E1 |

## E10 (Web Dashboard) per-story dependency

| Story | Depends on |
|---|---|
| E10-S1 App shell & auth | E1 |
| E10-S2-T1 Dashboard page | E5, E7, E8 |
| E10-S2-T2 Transactions page | E3 |
| E10-S2-T3 Budget page | E5 |
| E10-S2-T4 EMI/Subscriptions page | E6 |
| E10-S2-T5 Chatbot page | E8 |
| E10-S2-T6 Export page | E7 |
| E10-S2-T7 Settings page | E1 |
| E10-S3 Offline/stale handling | E10-S2-T1 |

## No-go: things that look parallelizable but aren't

- **E4-S3 (backend Categorization job wiring) before E3-S1 (Ingest endpoint) exists** —
  the retry job and ingest-trigger wiring need the Ingest→Transaction persistence path in
  place first, even though the FastAPI side (E4-S1/S2) doesn't.
- **E6 (recurring detection) before E5 (Alerts pipeline)** — the recurring-payment alert
  type reuses the same evaluator/dispatch infrastructure built in E5; the detection
  *algorithm itself* (E6-S1) can be written and unit-tested standalone, but wiring it into
  a live alert (E6-S2) cannot land before E5 does.
- **E11 (Admin) before E7 (Analytics)** — `/admin/analytics` and `/admin/analytics/comparison`
  reuse Analytics module aggregation logic; building Admin's analytics views first means
  redoing them once Analytics exists.
