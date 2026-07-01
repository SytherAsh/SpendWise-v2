# SpendWise Implementation Roadmap

Master index of the MVP build. Scope is exactly the MVP feature list in
[`../docs/roadmap.md`](../docs/roadmap.md) ("Current Phase: MVP") — nothing from
"Post-MVP Roadmap" in that document is included here. If you find yourself building
Hindi/Marathi support, PhonePe parsing, investment tracking, per-user ML models, Play
Store distribution, Espresso UI tests, or microservices extraction — stop, that is
post-MVP and out of scope for this backlog.

## Epic Sequencing & Milestones

Epics are ordered so that **every epic ends with something you can actually run and show**
— not just code that compiles. The milestone column is the demo script for that epic.

| # | Epic | Depends on | Working Milestone (the demo) |
|---|---|---|---|
| 0 | [Foundation & Project Scaffolding](epics/epic-00-foundation.md) | — | All 4 services (Spring Boot, FastAPI, Next.js, Android) boot locally, health checks pass, DB schema fully migrated with RLS, CI green on 4 pipelines. |
| 1 | [Auth & User Onboarding (Backend)](epics/epic-01-auth-and-user.md) | 0 | Full OTP/Google login → JWT → onboarding → device API key → profile fetch, demoed via curl/Postman. Admin/user JWT isolation proven. |
| 2 | [Android SMS Parsing & Sync](epics/epic-02-android-sms-parsing-sync.md) | 0 | All parser unit tests green (SBI/Paytm/GPay/unknown/dedup); instrumented test shows SMS → Room queue → a batch payload matching the `/ingest` contract. No UI yet. |
| 3 | [Ingestion & Transaction Management](epics/epic-03-ingestion-and-transactions.md) | 0, 1 | POST a batch to `/ingest/transactions`, list it via `/transactions`, correct its category, manage an EMI — all via integration tests + curl. |
| 4 | [ML Categorization Service](epics/epic-04-ml-categorization.md) | 3 | An ingested transaction is auto-categorized end-to-end (Ingest → FastAPI `/predict` → `transaction_categories`); manual retrain + evaluate work via Admin's service interface. |
| 5 | [Budget & Alerts](epics/epic-05-budget-and-alerts.md) | 3, 4 | Set a budget, simulate spend, watch each of the 3 alert thresholds fire with correct priority and delivery channel. |
| 6 | [EMI & Recurring Payment Detection](epics/epic-06-emi-and-recurring.md) | 3, 5 | Synthetic 3+ matching transactions produce a `recurring_payment` alert; confirming it creates a correctly-linked `emis` row. |
| 7 | [Analytics & Export](epics/epic-07-analytics-and-export.md) | 3, 4 | Summary/category/comparison/trend endpoints return correct aggregates for a seeded user; CSV and PDF export download correctly. |
| 8 | [Recommendations & Chatbot](epics/epic-08-recommendations-and-chatbot.md) | 3, 7 | A recommendation card is generated on a synthetic threshold-crossing; the chatbot answers a spend question grounded in seeded data across a resumed session. |
| 9 | [Android App UI](epics/epic-09-android-app-ui.md) | 1, 2, 3, 5, 6, 7, 8 (per-screen, see epic) | Full onboarding → dashboard flow runs on an emulator/device against a running backend. |
| 10 | [Web Dashboard](epics/epic-10-web-dashboard.md) | 1, 3, 5, 6, 7, 8 (per-screen, see epic) | Full dashboard demoable in a browser end-to-end, including the stale-data fallback when the backend is killed. |
| 11 | [Admin Portal](epics/epic-11-admin-portal.md) | 1, 3, 4, 5, 7 | Admin logs in separately, views cross-user stats/logs, triggers a retrain, deletes a test user with a verified full data purge. |
| 12 | [Deployment, Monitoring & Launch](epics/epic-12-deployment-and-launch.md) | all above | All 4 services live on free-tier hosting, health check green, E2E golden path test passes against the deployed environment. MVP launch. |

Epics 9 and 10 (Android UI, Web dashboard) are **not** single blocking gates — each of their
stories only depends on the corresponding backend epic having landed, so UI work should
start incrementally as soon as its backend counterpart is demoable, not after everything
else is finished. See `DEPENDENCY-GRAPH.md` for the concrete parallel tracks.

## What "done" means for the MVP

The MVP is complete when every epic's Working Milestone above has been demonstrated **and**
Epic 12's launch verification (E2E golden path passing against the deployed environment,
full security checklist pass) is checked off. At that point the product matches the
"Current Phase: MVP" feature list in `docs/roadmap.md` in full.

## Where the detail lives

- Task-level detail (Objective / Deliverable / Definition of Done / Required Tests /
  Complexity / Depends on) is in each epic's file under `epics/`.
- Day-to-day progress tracking is in `tracking/STATUS.md` — that is the file to update as
  you complete work, not this one or the epic files.
- Cross-epic and within-epic parallelization guidance is in `DEPENDENCY-GRAPH.md`.
