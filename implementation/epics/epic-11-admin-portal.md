# Epic 11 — Admin Portal

**Working Milestone:** Admin logs in with separate credentials (never a user account),
views the user list and per-user detail, views cross-user analytics and comparison, views
system logs, checks ML accuracy, triggers a manual retrain, and deletes a test user —
after which a verification query confirms all of that user's data is purged (with
`admin_logs` rows scrubbed per the DPDP erasure rule) rather than just the `users` row.

---

### E11-S1 — Admin Auth

**Independently testable via:** curl against `/admin/*` with an admin-signed token.

#### E11-S1-T1 — Admin login issuing `ADMIN_JWT_SECRET`-signed tokens

- **Objective:** Provide a separate credential path for the admin/developer — never a
  regular user account with a role claim.
- **Expected Deliverable:** An admin login mechanism (e.g., a seeded admin credential
  checked against an env-var-configured value or a small dedicated admin-credentials table)
  issuing a JWT signed with `ADMIN_JWT_SECRET`.
- **Definition of Done:** The issued token is rejected by the user-JWT filter (E1-S1-T7) and
  accepted only by the admin filter (E1-S2-T1); no regular user, however privileged, can
  obtain this token through the normal `/auth/*` flow.
- **Required Tests:** Integration test: admin login succeeds and the resulting token works
  on an admin route and fails on a user route (and vice versa for a user token on an admin route).
- **Estimated Complexity:** Medium
- **Depends on:** E1-S2-T1
- **Grounded in:** `CLAUDE.md` "Admin authentication uses a separate JWT..."; `docs/operations/user_flows.md` Admin Flow ("Logs in with admin credentials (separate from any user account)").

---

### E11-S2 — Admin API

**Independently testable via:** curl/integration tests with an admin token.

#### E11-S2-T1 — `GET /admin/users`, `GET /admin/users/:id`

- **Objective:** List all users with stats; view full data for one user.
- **Expected Deliverable:** Both endpoints, admin-only, returning cross-user data (Admin
  reads from all modules, per the module dependency table).
- **Definition of Done:** List includes basic stats per user (e.g., transaction count, last
  activity); detail view includes full transaction/budget/alert data for that one user
  (still excluding `sms_raw_text`, since that invariant is universal — "never appear in any user-facing API response" — admin views are operator-facing but the field remains excluded per the response DTO layer built in Epic 3).
- **Required Tests:** Integration test verifying `sms_raw_text` absence even on the admin detail view; verifying non-admin tokens get 401/403.
- **Estimated Complexity:** Medium
- **Depends on:** E11-S1-T1, E3-S2-T1
- **Grounded in:** `docs/spec/api.md` `/admin` table; `docs/spec/requirements.md` Admin Access section; `CLAUDE.md` security invariant on `sms_raw_text` (applies universally, not just to non-admin routes).

#### E11-S2-T2 — `GET /admin/analytics`, `GET /admin/analytics/comparison`

- **Objective:** Aggregate stats and cross-user spending comparison.
- **Expected Deliverable:** Both endpoints, reusing Analytics module aggregation logic (Epic 7) rather than reimplementing it.
- **Definition of Done:** Numbers match the sum/comparison of individual users' Analytics
  results for a seeded multi-user fixture.
- **Required Tests:** Integration test comparing admin aggregate output against manually
  summed per-user `/analytics/summary` results for a fixed fixture.
- **Estimated Complexity:** Medium
- **Depends on:** E11-S1-T1, E7-S1-T1
- **Grounded in:** `docs/spec/api.md` `/admin` table; `docs/operations/user_flows.md` Admin Flow ("aggregate stats, cross-user spending comparison").

#### E11-S2-T3 — `GET /admin/logs`

- **Objective:** Surface parser failures, sync errors, and other system events.
- **Expected Deliverable:** Endpoint listing `admin_logs`, filterable by `event_type`, using `idx_admin_logs_event_type`.
- **Definition of Done:** Returns entries in recency order; supports filtering by event type.
- **Required Tests:** Integration test with seeded log rows of different types — filter returns only the matching type.
- **Estimated Complexity:** Small
- **Depends on:** E11-S1-T1, E0-S2-T5
- **Grounded in:** `docs/spec/api.md` `/admin` table; `docs/spec/database.md` `admin_logs`.

#### E11-S2-T4 — `GET /admin/ml/accuracy`, `POST /admin/ml/retrain`

- **Objective:** Surface ML accuracy metrics and allow manual retrain triggering — strictly
  through Categorization's service interface, never calling FastAPI directly.
- **Expected Deliverable:** Both endpoints delegating to the methods built in E4-S3-T5.
- **Definition of Done:** An architecture test (or reuse of E4-S3-T5's) confirms this
  controller has no direct reference to the FastAPI HTTP client.
- **Required Tests:** Integration test: retrain trigger results in a call recorded against
  the Categorization service interface (test double); accuracy endpoint returns the shape from `/evaluate`.
- **Estimated Complexity:** Small
- **Depends on:** E11-S1-T1, E4-S3-T5
- **Grounded in:** `docs/spec/api.md` `/admin` table; `docs/spec/architecture.md` "Admin calls Categorization's service interface... FastAPI is never called directly from Admin".

#### E11-S2-T5 — `DELETE /admin/users/:id` (full erasure, DPDP)

- **Objective:** Implement the right-to-erasure flow exactly as specified, including the
  `admin_logs` scrubbing exception.
- **Expected Deliverable:** Endpoint that hard-deletes the user row (cascading via `ON
  DELETE CASCADE` to `user_preferences`, `user_consent`, `refresh_tokens`, `transactions`,
  `budgets`, `alerts`, `emis`, `recommendations`, `chatbot_sessions`,
  `chatbot_conversations`, `device_api_keys` — confirm every FK is indeed `ON DELETE
  CASCADE` per `docs/spec/database.md`), and separately scrubs `admin_logs` rows referencing the
  deleted user: sets `user_id = NULL` (already `ON DELETE SET NULL`) **and** additionally
  scrubs/removes any identifying string in `event_type` or `payload` for that user's rows.
- **Definition of Done:**
  - Post-deletion, a query for any table with a FK to `users` for that `user_id` returns zero rows.
  - `admin_logs` rows originally tied to that user have `user_id = NULL` and no longer
    contain identifying strings (phone, email, name) in `event_type`/`payload`.
  - The operation is irreversible and requires the admin JWT (never accessible to a user token).
- **Required Tests:** Integration test: seed a user with rows in every dependent table plus
  an `admin_logs` row containing an identifying string in its payload; delete; assert every
  dependent table is empty for that user and the `admin_logs` row is scrubbed, not just null-`user_id`.
- **Estimated Complexity:** Large
- **Depends on:** E11-S1-T1, all of Epic 3, E0-S2-T5
- **Grounded in:** `docs/spec/api.md` `/admin` table; `docs/spec/security.md` Data Subject Rights "Deletion" (including the `admin_logs` exception clause verbatim); `docs/spec/requirements.md` Data Retention.

---

### E11-S3 — Admin UI

#### E11-S3-T1 — Minimal admin web screens

- **Objective:** Give the admin/developer a usable interface rather than requiring raw curl for day-to-day operation.
- **Expected Deliverable:** A small set of screens (can live under a `/admin` route in the
  same Next.js app, gated by the admin login — not the user auth guard) covering: user list
  + detail, logs viewer, ML accuracy + retrain trigger button, delete-user flow with an
  explicit confirmation step (irreversible action).
- **Definition of Done:** Delete-user flow requires a confirmation step naming the user
  before executing; all screens function against a seeded backend.
- **Required Tests:** Component test for the delete-user confirmation gate (cannot fire the
  delete request without confirming).
- **Estimated Complexity:** Large
- **Depends on:** E11-S2-T1, E11-S2-T2, E11-S2-T3, E11-S2-T4, E11-S2-T5
- **Grounded in:** `docs/operations/user_flows.md` Admin Flow (full flow); `docs/spec/requirements.md` Admin Access.

---

## Parallel Execution within Epic 11

- E11-S2-T1 through T4 are independent of each other once E11-S1-T1 lands.
- E11-S2-T5 (deletion) is the most sensitive task in this epic — build and test it last
  among the API tasks, once the full set of dependent tables (all of Epic 3, Epic 5, Epic
  6, Epic 8) actually exist, so the cascade/erasure test fixture is realistic.
