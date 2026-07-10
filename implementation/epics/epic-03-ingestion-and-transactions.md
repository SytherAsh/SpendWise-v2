# Epic 3 — Ingestion & Transaction Management (Backend)

**Working Milestone:** POST a batch to `/ingest/transactions` with a valid JWT + device
key and see rows land in `transactions`; re-POST the same batch and see `409`s with no
duplicate rows; list them via `GET /transactions` with pagination and filters; fetch one
by id; correct its category and see both `transaction_categories` and `ml_corrections`
updated atomically; create and manage an EMI. All demoed via Testcontainers integration
tests and curl — no ML categorization yet (that's Epic 4; transactions land uncategorized
here).

---

### E3-S1 — Ingest Endpoint

**Independently testable via:** curl with a valid JWT + `X-Device-Key` header.

#### E3-S1-T1 — Dual-auth guard for `/ingest/transactions`

- **Objective:** Enforce that both a valid user JWT and a valid device API key are present
  before any ingestion logic runs — the single most important security invariant in the
  whole system per `CLAUDE.md`.
- **Expected Deliverable:** A guard (filter/interceptor/annotation) on `POST /ingest/transactions`
  composing the user-JWT filter (E1-S1-T7) with the device-key validation service (E1-S4-T1).
- **Definition of Done:** All 4 cases from `docs/operations/testing.md` pass: valid JWT + valid device
  key → proceeds; missing JWT → `401`; missing device key → `401`; device key not matching
  the JWT's `user_id` → `401`; inactive device key → `401`.
- **Required Tests:** Integration tests for exactly the 4+1 cases listed above.
- **Estimated Complexity:** Medium
- **Depends on:** E1-S1-T7, E1-S4-T1
- **Grounded in:** `CLAUDE.md` security invariants; `docs/spec/api.md` `/ingest` section; `docs/operations/testing.md` Ingest unit tests (dual-auth validation).

#### E3-S1-T2 — Batch persistence with two-layer dedup

- **Objective:** Persist a batch of parsed transactions, enforcing both the primary
  (`transaction_id`) and secondary (`upi_id`, `amount`, `transaction_date`) dedup checks,
  with per-item `409` on conflict that does not fail the rest of the batch.
- **Expected Deliverable:** Batch-insert logic that: checks primary dedup (relies on the DB
  unique index as the authoritative guard, per `docs/spec/database.md`), falls back to the
  secondary check only when `upi_id` is present, and returns a per-item result so a single
  `409` doesn't abort sibling inserts.
- **Definition of Done:**
  - POSTing the same transaction twice → second returns `409`, no duplicate row.
  - A batch of 3 items where item 2 is a duplicate → items 1 and 3 persist, item 2 returns `409`.
  - `source` column correctly set to `'sms'` for ingest-path transactions.
- **Required Tests:** Integration tests: duplicate single item → 409, no DB duplicate;
  mixed-batch partial failure → 2 of 3 persist with correct per-item statuses.
- **Estimated Complexity:** Large
- **Depends on:** E3-S1-T1, E0-S2-T3
- **Grounded in:** `docs/spec/database.md` Deduplication Strategy + `idx_transactions_unique_dedup`; `docs/spec/api.md` `/ingest` idempotency note; `docs/operations/testing.md` Ingest dedup unit tests + integration test "POST same transaction twice".

#### E3-S1-T3 — `sms_raw_text` response-exclusion enforcement

- **Objective:** Guarantee `sms_raw_text` can never leak through any user-facing response,
  even though the Android client never sends it today — this is a defense-in-depth
  invariant, not a behavior contingent on current client behavior.
- **Expected Deliverable:** A response DTO layer used by every Transaction-module
  controller (not raw JPA entities returned anywhere), with a test that fails the build if
  a new endpoint accidentally serializes the entity directly.
- **Definition of Done:** An integration test asserting `sms_raw_text` is absent from the
  JSON body of every Transaction-module endpoint response, including when the field is
  deliberately populated in the test fixture.
- **Required Tests:** Integration test per `docs/operations/testing.md`: populate `sms_raw_text` on a
  fixture row, call each transaction-returning endpoint, assert the key is absent from the response.
- **Estimated Complexity:** Medium
- **Depends on:** E3-S1-T2
- **Grounded in:** `CLAUDE.md` Security invariants (first bullet); `docs/spec/database.md` `transactions.sms_raw_text` comment; `docs/operations/testing.md` integration tests list.

---

### E3-S2 — Transaction CRUD & Query

**Independently testable via:** curl/integration tests against a seeded DB.

#### E3-S2-T1 — `GET /transactions` (cursor pagination + filters)

- **Objective:** List transactions with cursor-based pagination and optional `category`/`from`/`to` filters.
- **Expected Deliverable:** Endpoint returning the `{data, nextCursor, hasMore}` shape from `docs/spec/api.md`, default page size 50.
- **Definition of Done:** Pagination is stable under concurrent inserts (cursor uses `id`,
  not offset, per ADR-008); filters compose correctly (category + date range together).
- **Required Tests:** Integration test verifying cursor pagination returns consistent,
  non-duplicated results across pages even when new rows are inserted between page fetches
  (per `docs/operations/testing.md` "Pagination" integration test).
- **Estimated Complexity:** Medium
- **Depends on:** E3-S1-T3
- **Grounded in:** `docs/spec/api.md` `/transactions` table + Pagination section; `docs/spec/decisions.md` ADR-008.

#### E3-S2-T2 — `GET /transactions/:id`

- **Objective:** Fetch a single transaction, scoped to the authenticated user.
- **Expected Deliverable:** Endpoint returning 404 for a nonexistent id and for another user's transaction id (not 403 — avoid leaking existence).
- **Definition of Done:** Requesting another user's transaction id returns 404, not their data.
- **Required Tests:** Integration test: user A cannot fetch user B's transaction by id.
- **Estimated Complexity:** Small
- **Depends on:** E3-S2-T1
- **Grounded in:** `docs/spec/api.md` `/transactions` table; `CLAUDE.md` security invariants (query scoping).

#### E3-S2-T3 — `POST /transactions` (manual entry)

- **Objective:** Allow a user to manually add a transaction (`source = 'manual'`).
- **Expected Deliverable:** Endpoint validating required fields and persisting with `source = 'manual'`.
- **Definition of Done:** Manually created transactions are indistinguishable in shape from
  SMS-ingested ones except for `source`; validation rejects missing required fields with `400`.
- **Required Tests:** Integration test: create, then confirm it appears in `GET /transactions` with `source: "manual"`.
- **Estimated Complexity:** Small
- **Depends on:** E3-S2-T1
- **Grounded in:** `docs/spec/api.md` `/transactions` table; `docs/spec/database.md` `transaction_source` enum.

#### E3-S2-T4 — `PUT /transactions/:id/category` (correction + `ml_corrections` write)

- **Objective:** Let a user correct a transaction's category, atomically updating
  `transaction_categories` and inserting the labeled example into `ml_corrections`, with no
  cross-module call to Categorization (per the acyclic dependency rule).
- **Expected Deliverable:** Endpoint performing both writes in a single transaction;
  returns `404` if the transaction doesn't exist, `400` if `category_id` doesn't exist.
- **Definition of Done:**
  - Both writes commit atomically (rollback together on failure).
  - `ml_corrections.old_category_id` reflects the prior assignment; a correction that would
    set old = new is either a no-op or rejected consistent with `chk_correction_different_category`.
- **Required Tests:** Integration test per `docs/operations/testing.md`: verify both tables update
  atomically; test `404` for missing transaction; test `400` for invalid `category_id`.
- **Estimated Complexity:** Medium
- **Depends on:** E3-S2-T1, E0-S2-T5
- **Grounded in:** `docs/spec/api.md` `PUT /transactions/:id/category` section (ownership note) + request schema; `docs/spec/database.md` `ml_corrections`; `docs/operations/testing.md` Transaction Management unit tests.

#### E3-S2-T5 — `GET /categories`

- **Objective:** Serve the 10 predefined categories.
- **Expected Deliverable:** Endpoint returning all rows from the `categories` table, served by the Transaction module (not Categorization).
- **Definition of Done:** Returns exactly the 10 seeded categories with correct `id`/`name`/`icon`.
- **Required Tests:** Integration test asserting response matches the seed table in `docs/spec/database.md`.
- **Estimated Complexity:** Small
- **Depends on:** E0-S2-T3
- **Grounded in:** `docs/spec/api.md` `/categories` section (module ownership note); `docs/spec/requirements.md` Transaction Categories list.
- **Amended (2026-07-11):** `categories` extended from 10 to 12 rows by migration `V7__add_medical_and_fees_categories.sql` (Medical, Fees & Debt), landed 2026-07-02. This task's original Objective/DoD (V2, 10 rows) is unchanged as a historical record; `GET /categories` now returns 12 rows — see `docs/spec/database.md`.

---

### E3-S3 — EMI CRUD (owned by the Transaction module)

**Independently testable via:** curl/integration tests.

#### E3-S3-T1 — `GET /emis`, `POST /emis`

- **Objective:** List active EMIs and support manual EMI creation.
- **Expected Deliverable:** Both endpoints against the `emis` table.
- **Definition of Done:** Manually created EMI has `detected_from_sms = false`,
  `source_transaction_id = null`; `chk_emi_amount_positive` enforced with `400` on violation.
- **Required Tests:** Integration test: create manual EMI, list it, confirm fields.
- **Estimated Complexity:** Small
- **Depends on:** E0-S2-T4
- **Grounded in:** `docs/spec/api.md` `/emis` table; `docs/spec/database.md` `emis` schema.

#### E3-S3-T2 — `PUT /emis/:id`, `PATCH /emis/:id` (deactivate)

- **Objective:** Support editing EMI details and deactivating (not deleting) an EMI.
- **Expected Deliverable:** `PUT` updates `label`/`amount`/`due_day`; `PATCH` sets `is_active = false` and retains the row.
- **Definition of Done:** A deactivated EMI still appears in a "show all" admin-style query
  but not in the default active-EMI list; the row is never hard-deleted by this endpoint.
- **Required Tests:** Integration test: deactivate, confirm `is_active = false` and row still exists; confirm it's excluded from `GET /emis`.
- **Estimated Complexity:** Small
- **Depends on:** E3-S3-T1
- **Grounded in:** `docs/spec/api.md` `/emis` table; `docs/operations/user_flows.md` EMI/Subscriptions Management flow.

---

## Parallel Execution within Epic 3

- E3-S2-T1 through T5 are largely independent of each other once E3-S1 lands — different
  endpoints, same module, minimal file overlap if organized by controller method.
- E3-S3 (EMI) has no dependency on E3-S2 and can be built in parallel with it.
- Per `../DEPENDENCY-GRAPH.md`, E4-S1/S2 (the FastAPI service itself) can be developed in
  parallel with all of Epic 3 — only E4-S3 needs this epic finished.
