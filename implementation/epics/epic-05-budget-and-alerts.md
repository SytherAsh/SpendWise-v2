# Epic 5 — Budget & Alerts

**Working Milestone:** Set a monthly budget for a category, then simulate spend crossing
each of the three thresholds (mid-month 50% of total budget, 80% category approaching-limit,
full category overspend) and observe the correct `alerts` row created with the correct
`priority`, and the correct delivery channel invoked (push via FCM for high-priority, none
beyond in-app for medium/low) — verified via integration tests and a local
SMTP/FCM sandbox.

---

### E5-S1 — Budget Module

**Independently testable via:** curl/integration tests.

#### E5-S1-T1 — `POST /budgets` (idempotent upsert)

- **Objective:** Create or replace a category's monthly budget.
- **Expected Deliverable:** Endpoint upserting on `(user_id, category_id, month, year)`.
- **Definition of Done:** Repeated calls with identical parameters are safe (no duplicate
  rows, no error); `chk_budget_limit_positive` enforced with `400` on a zero/negative limit.
- **Required Tests:** Integration test: call twice with the same params → one row, second
  call's response reflects the (possibly updated) value; call with `monthly_limit <= 0` → `400`.
- **Estimated Complexity:** Small
- **Depends on:** E0-S2-T4, E1-S1-T7
- **Grounded in:** `docs/api.md` "Budget upsert" note; `docs/database.md` `budgets` schema + `chk_budget_limit_positive` comment.

#### E5-S1-T2 — `GET /budgets`

- **Objective:** List all budgets for the current month.
- **Expected Deliverable:** Endpoint returning all `budgets` rows for `user_id` where `month`/`year` = current.
- **Definition of Done:** Only current-month rows returned; scoped to the authenticated user.
- **Required Tests:** Integration test with budgets across two different months — only current month returned.
- **Estimated Complexity:** Small
- **Depends on:** E5-S1-T1
- **Grounded in:** `docs/api.md` `/budgets` table.

#### E5-S1-T3 — `GET /budgets/progress`

- **Objective:** Return budget-vs-actual spend per category for the current month.
- **Expected Deliverable:** Endpoint joining `budgets` with a read-only aggregation over
  `transactions`/`transaction_categories` (Budget module's only permitted read: Transaction, read-only, per `docs/architecture.md`).
- **Definition of Done:** For a seeded set of transactions and a budget, the returned
  percent-spent matches a hand-computed value exactly.
- **Required Tests:** Integration test per `docs/testing.md` Budget unit tests: progress
  calculation (% spent) matches expected for a fixed fixture.
- **Estimated Complexity:** Medium
- **Depends on:** E5-S1-T1, E3-S2-T4
- **Grounded in:** `docs/api.md` `/budgets` table; `docs/architecture.md` Budget module dependency row ("Transaction (read-only)"); `docs/testing.md` Budget unit tests.

#### E5-S1-T4 — `GET /budgets/suggestions`

- **Objective:** Suggest a starting budget per category based on historical spend (from
  bank statement upload or accumulated SMS history).
- **Expected Deliverable:** Endpoint computing a suggested monthly limit per category from
  historical transaction data; returns an empty/low-confidence result gracefully when no
  history exists (per the "First-Time User" edge case).
- **Definition of Done:** Given 2+ months of seeded historical spend in a category, the
  suggestion is a reasonable derived value (e.g., average or median monthly spend);
  given no history, returns an explicit "no suggestion available" response rather than erroring.
- **Required Tests:** Integration test: seeded history → non-null suggestion; no history → graceful empty response, not a 500.
- **Estimated Complexity:** Medium
- **Depends on:** E5-S1-T1, E3-S2-T4
- **Grounded in:** `docs/api.md` `/budgets/suggestions`; `docs/requirements.md` Budget section ("system suggests a starting budget"); `docs/user_flows.md` "First-Time User" edge case.

---

### E5-S2 — Alerts Evaluation Engine

**Independently testable via:** unit tests against the evaluation logic with synthetic spend/budget fixtures (no scheduler needed at this stage).

#### E5-S2-T1 — Mid-month 50%-of-total-budget rule

- **Objective:** Detect when a user has spent 50% of their *total* monthly budget by mid-month.
- **Expected Deliverable:** Evaluation function producing a `mid_month_budget` alert with `priority = 'high'` when triggered.
- **Definition of Done:** Fixture at exactly 50% by the 15th triggers; fixture below 50% does not.
- **Required Tests:** Unit tests per `docs/testing.md` Budget unit tests: mid-month 50% total-budget threshold (high priority).
- **Estimated Complexity:** Medium
- **Depends on:** E5-S1-T3
- **Grounded in:** `docs/requirements.md` Alerts table (Mid-month budget alert); `docs/database.md` `alert_type` enum.

#### E5-S2-T2 — Category 80%-approaching-limit rule

- **Objective:** Detect when a specific category budget hits 80% spent.
- **Expected Deliverable:** Evaluation function producing a `category_approaching_limit` alert with `priority = 'medium'`.
- **Definition of Done:** Fixture at 80%+ but below 100% triggers; below 80% does not; at/above 100% is handled by E5-S2-T3 instead (no double-firing both rules for the same state — document the boundary rule, e.g. overspend rule takes precedence once ≥100%).
- **Required Tests:** Unit test per `docs/testing.md`: 80% per-category threshold (medium priority, in-app only).
- **Estimated Complexity:** Medium
- **Depends on:** E5-S1-T3
- **Grounded in:** `docs/requirements.md` Alerts table (Category overspending) + priority table (Medium — 80% — in-app only).

#### E5-S2-T3 — Category overspend rule

- **Objective:** Detect when a category budget is fully exceeded.
- **Expected Deliverable:** Evaluation function producing a `category_overspend` alert with `priority = 'high'`.
- **Definition of Done:** Fixture at ≥100% of category budget triggers exactly once (no duplicate alert on subsequent evaluator runs for the same month/category once already triggered — define and implement the suppression rule).
- **Required Tests:** Unit test: ≥100% triggers; running the evaluator twice against the same state does not create a second alert.
- **Estimated Complexity:** Medium
- **Depends on:** E5-S1-T3
- **Grounded in:** `docs/requirements.md` Alerts table + priority table (High — budget exceeded — push + email); `docs/testing.md` Alerts evaluation engine unit tests.

#### E5-S2-T4 — Alert evaluator scheduled job (every 30 minutes)

- **Objective:** Run all three rules above across all users on a schedule.
- **Expected Deliverable:** `@Scheduled` job in the Alerts module iterating users, calling
  each rule, persisting resulting `alerts` rows, then invoking dispatch (E5-S3).
- **Definition of Done:** Manually invoking the job against a multi-user fixture produces
  the correct alert set for each user independently; job only reads Transaction (spend) and
  Budget (limits) per the module dependency table — never Recommendations/Chatbot/Ingest.
- **Required Tests:** Integration test with 2+ seeded users in different threshold states —
  assert each user's `alerts` rows are correct and isolated from each other.
- **Estimated Complexity:** Large
- **Depends on:** E5-S2-T1, E5-S2-T2, E5-S2-T3
- **Grounded in:** `docs/architecture.md` Background Jobs table (Alert evaluator, every 30 min) + Alerts module dependency row.

---

### E5-S3 — Notification Dispatch

**Independently testable via:** unit tests against dispatch logic with a mocked FCM/SMTP client; a local SMTP sandbox (e.g., MailHog) for manual verification.

#### E5-S3-T1 — FCM push dispatch

- **Objective:** Deliver high-priority alerts as push notifications.
- **Expected Deliverable:** FCM client wrapper using `FCM_SERVER_KEY`, invoked for `priority = 'high'` alerts (per the priority/delivery table); sets `delivered_at` on confirmed delivery.
- **Definition of Done:** A mocked successful FCM call sets `alerts.delivered_at`; a failed call leaves it null without crashing the evaluator job.
- **Required Tests:** Unit test: success sets `delivered_at`; failure is handled gracefully (logged, not thrown).
- **Estimated Complexity:** Medium
- **Depends on:** E5-S2-T4
- **Grounded in:** `docs/requirements.md` Alerts priority/delivery table; `docs/deployment.md` `FCM_SERVER_KEY`; `docs/user_flows.md` "Handling an Alert" (`delivered_at` set automatically by server on FCM/SMTP confirmation).

#### E5-S3-T2 — SMTP email dispatch

- **Objective:** Deliver high-priority alerts via email.
- **Expected Deliverable:** SMTP client wrapper using `EMAIL_SMTP_*` env vars, invoked alongside FCM for `priority = 'high'` alerts.
- **Definition of Done:** A mocked successful SMTP send doesn't duplicate the `delivered_at` update logic incorrectly when both channels succeed/fail independently.
- **Required Tests:** Unit test: success and failure paths, mirroring E5-S3-T1's structure.
- **Estimated Complexity:** Medium
- **Depends on:** E5-S2-T4
- **Grounded in:** `docs/requirements.md` "Default delivery: push notification + email"; `docs/deployment.md` `EMAIL_SMTP_*` env vars.

---

### E5-S4 — Alerts API

**Independently testable via:** curl/integration tests.

#### E5-S4-T1 — `GET /alerts` (cursor pagination + `is_read` filter)

- **Objective:** List a user's alerts, newest first, with an optional unread filter.
- **Expected Deliverable:** Endpoint using the same cursor-pagination shape as `/transactions`.
- **Definition of Done:** `is_read=false` filter uses the partial index path (`idx_alerts_unread`) correctly (verify via query plan or just correctness of results — the index is a DB-level optimization, not a correctness requirement, but the filter behavior must be correct).
- **Required Tests:** Integration test: mixed read/unread alerts, filter returns only unread when requested.
- **Estimated Complexity:** Small
- **Depends on:** E5-S2-T4
- **Grounded in:** `docs/api.md` `/alerts` table; `docs/database.md` `idx_alerts_unread`.

#### E5-S4-T2 — `PUT /alerts/:id/read`

- **Objective:** Mark an alert as read.
- **Expected Deliverable:** Endpoint setting `is_read = true`; does not touch `delivered_at` (a separate, earlier, server-set event).
- **Definition of Done:** `delivered_at` is unaffected by this call; `is_read` flips correctly and only for the requesting user's own alert.
- **Required Tests:** Integration test: mark read, confirm `is_read=true` and `delivered_at` unchanged; user B cannot mark user A's alert as read.
- **Estimated Complexity:** Small
- **Depends on:** E5-S4-T1
- **Grounded in:** `docs/api.md` `/alerts` table; `docs/user_flows.md` "Handling an Alert" flow note on `delivered_at`.

---

## Parallel Execution within Epic 5

- E5-S1-T2/T3/T4 are independent of each other once E5-S1-T1 lands.
- E5-S2-T1/T2/T3 (the three threshold rules) are fully independent unit-testable functions
  — build in parallel, then combine in E5-S2-T4.
- E5-S3-T1 and E5-S3-T2 (FCM vs. SMTP) are independent of each other.
- Per `../DEPENDENCY-GRAPH.md`, E6-S1 (recurring-detection algorithm, pure logic) can be
  developed in parallel with all of Epic 5.
