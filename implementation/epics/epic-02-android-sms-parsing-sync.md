# Epic 2 — Android SMS Parsing & Sync (No UI)

**Working Milestone:** All Kotlin unit tests green for the parser (SBI/Paytm/GPay/unknown
sender/dedup/invalid formats — the exact cases enumerated in `docs/operations/testing.md` §3); an
instrumented test demonstrates a raw SMS string flowing through the keyword filter →
regex parser → dedup check → Room DB queue → a serialized batch payload that matches the
`POST /ingest/transactions` request schema in `docs/spec/api.md` byte-for-byte in shape. No
Android UI exists yet — this epic is pure SMS/Parser/Sync/Storage module work per
`CLAUDE.md`'s Android Module Map.

This epic has **zero dependency on the backend** — the JSON contract it targets is already
frozen in `docs/spec/api.md`. It can be built entirely in parallel with Epic 1.

---

### E2-S1 — Financial Keyword Filter

**Independently testable via:** Kotlin unit tests, no device/emulator needed.

#### E2-S1-T1 — Financial vs. non-financial keyword detector

- **Objective:** Filter incoming SMS to financial-transaction messages before any parsing
  work happens, discarding OTP/promotional/non-financial SMS immediately.
- **Expected Deliverable:** `com.spendwise.parser` keyword detector function taking raw SMS
  text + sender, returning a boolean.
- **Definition of Done:**
  - Random OTP SMS → `false`.
  - Promotional SMS → `false`.
  - A known financial SMS from an unrecognized sender → `true` (keyword-based, not sender-based).
- **Required Tests:** Unit tests for the three cases above, using realistic sample text (store samples under `android/app/src/test/kotlin/com/spendwise/parser/samples/`).
- **Estimated Complexity:** Medium
- **Depends on:** E0-S1-T3
- **Grounded in:** `docs/spec/architecture.md` SMS Ingestion Flow ("Financial keyword filter"); `docs/operations/testing.md` §3 "Invalid / Unknown SMS formats"; `docs/development_guidelines.md` "Adding a New SMS Sender".

---

### E2-S2 — Regex Parsers per Sender

**Independently testable via:** Kotlin unit tests against the sample SMS strings given in `docs/operations/testing.md`.

#### E2-S2-T1 — SBI parser

- **Objective:** Parse SBI debit/credit SMS formats into structured transaction fields.
- **Expected Deliverable:** Regex-based parser handling both sample formats in
  `docs/operations/testing.md` (debit with UPI ref, credit without full ref detail).
- **Definition of Done:** For every valid SBI sample, all of `transaction_date`, `amount`,
  `debit`, `credit`, `dr_cr_indicator`, `transaction_id` are non-null and DR/CR-consistent
  (`DR` ⇒ `amount<0, debit>0, credit=0`; `CR` ⇒ `amount>0, credit>0, debit=0`);
  `recipient_name`/`upi_id`/`bank` are present or null, never throwing.
- **Required Tests:** Exactly the two SBI sample strings from `docs/operations/testing.md` §3, plus one
  malformed/partial-data SBI-like string that must not crash the parser.
- **Estimated Complexity:** Medium
- **Depends on:** E2-S1-T1
- **Grounded in:** `docs/operations/testing.md` §3 SBI SMS formats + Field extraction assertions; `docs/spec/database.md` `transactions` schema (field types/nullability, `chk_dr_cr_consistency`).

#### E2-S2-T2 — Paytm parser

- **Objective:** Parse Paytm UPI SMS formats.
- **Expected Deliverable:** Regex-based parser for the Paytm sample format in `docs/operations/testing.md`.
- **Definition of Done:** Same field-extraction assertions as E2-S2-T1, applied to Paytm's format.
- **Required Tests:** The Paytm sample string from `docs/operations/testing.md` §3, plus one partial-data variant.
- **Estimated Complexity:** Medium
- **Depends on:** E2-S1-T1
- **Grounded in:** `docs/operations/testing.md` §3 Paytm SMS format; `docs/spec/database.md` `transactions` schema.

#### E2-S2-T3 — GPay parser

- **Objective:** Parse Google Pay UPI SMS formats.
- **Expected Deliverable:** Regex-based parser for the GPay sample format in `docs/operations/testing.md`.
- **Definition of Done:** Same field-extraction assertions as E2-S2-T1, applied to GPay's format.
- **Required Tests:** The GPay sample string from `docs/operations/testing.md` §3, plus one partial-data variant.
- **Estimated Complexity:** Medium
- **Depends on:** E2-S1-T1
- **Grounded in:** `docs/operations/testing.md` §3 GPay SMS format; `docs/spec/database.md` `transactions` schema.

#### E2-S2-T4 — Unknown-sender fallback extractor

- **Objective:** Best-effort field extraction for a financial SMS from a sender not covered
  by SBI/Paytm/GPay regex rules.
- **Expected Deliverable:** A keyword-based fallback extractor (amount/date heuristics) that
  never throws, filling unavailable fields with null.
- **Definition of Done:** Given a financial SMS from an unrecognized sender, the fallback
  returns a best-effort object rather than throwing; fields it cannot extract are null.
- **Required Tests:** Unit test with a synthetic "financial-looking" SMS from a fictitious
  sender — asserts no exception and at least `amount` and `dr_cr_indicator` are recoverable
  when present in text.
- **Estimated Complexity:** Medium
- **Depends on:** E2-S1-T1
- **Grounded in:** `docs/spec/architecture.md` SMS Ingestion Flow ("unknown sender: keyword-based field extraction"); `docs/operations/testing.md` §3 "Unknown sender financial SMS".

---

### E2-S3 — Deduplication & `transaction_id` Synthesis

**Independently testable via:** Kotlin unit tests.

#### E2-S3-T1 — Synthesized `transaction_id` + on-device dedup check

- **Objective:** Implement the exact synthesis rule for SMS lacking a bank reference number,
  and the on-device dedup check against the local Room queue.
- **Expected Deliverable:** A function computing
  `hex(SHA-256(user_id || upi_id_or_recipient_name || amount || date_trunc('minute', transaction_date)))`
  and a dedup check comparing against existing Room DB rows.
- **Definition of Done:**
  - Same bank-provided `transaction_id` twice → second call returns null (duplicate).
  - Same synthesized `transaction_id` twice → second call returns null (duplicate).
  - A genuinely different transaction → returns the parsed object.
- **Required Tests:** The three cases in `docs/operations/testing.md` §3 "Deduplication logic" exactly.
- **Estimated Complexity:** Medium
- **Depends on:** E2-S2-T1, E2-S2-T2, E2-S2-T3, E2-S2-T4
- **Grounded in:** `docs/spec/database.md` `transactions.transaction_id` column comment (synthesis rule); `docs/operations/testing.md` §3 Deduplication logic; `docs/spec/architecture.md` SMS Ingestion Flow "Deduplication check".

---

### E2-S4 — Storage (Room DB Queue)

**Independently testable via:** Android instrumented tests (Room requires an environment, but no network/emulator UI needed).

#### E2-S4-T1 — Room entities & DAOs for the local sync queue

- **Objective:** Build the offline queue that holds parsed-but-unsynced transactions.
- **Expected Deliverable:** `com.spendwise.storage` Room `@Entity` mirroring the ingest
  payload shape (`docs/spec/api.md` `POST /ingest/transactions` request schema) plus a `synced`
  flag, and a DAO with insert/query-unsynced/mark-synced/delete operations.
- **Definition of Done:** Insert → appears in unsynced query; mark-synced → excluded from
  unsynced query; entity fields map 1:1 to the ingest payload's transaction object fields.
- **Required Tests:** Room instrumented test: insert, query unsynced, mark synced, query again.
- **Estimated Complexity:** Medium
- **Depends on:** E2-S3-T1
- **Grounded in:** `CLAUDE.md` Android Module Map (Storage); `docs/spec/api.md` ingest request schema.

---

### E2-S5 — Sync Module

**Independently testable via:** instrumented tests with a mocked HTTP client (no real backend needed for this epic).

#### E2-S5-T1 — Batch sync client with retry/backoff and 409-as-success

- **Objective:** Build the component that batches queued transactions and POSTs them,
  treating `409` as a successful dequeue per the idempotency contract.
- **Expected Deliverable:** Sync client using WorkManager (or an equivalent periodic
  scheduler) at a ~15-30 minute interval, batching all unsynced rows, POSTing to
  `/api/v1/ingest/transactions`, and on a per-item basis: `2xx` or `409` → dequeue;
  other errors → retain in queue for the next attempt.
- **Definition of Done:**
  - A mocked `409` response for one item in a batch dequeues only that item and leaves
    the rest of the batch's outcomes independent (a `409` on one item doesn't fail the batch).
  - On simulated network failure, items remain queued and are retried on the next scheduled run.
- **Required Tests:** Instrumented/unit test with a mocked HTTP layer: mixed batch response
  (some 201, one 409, one 500) → queue ends up with only the 500 item still pending.
- **Estimated Complexity:** Large
- **Depends on:** E2-S4-T1
- **Grounded in:** `docs/spec/architecture.md` "Deduplication is two-layered" note; `docs/spec/api.md` `/ingest` idempotency note; `docs/spec/requirements.md` "Background sync interval: ~15-30 minutes"; `docs/operations/user_flows.md` "Backend Offline" edge case.

#### E2-S5-T2 — Real-time SMS capture (BroadcastReceiver + foreground service)

- **Objective:** Wire the SMS module's real-time capture path into the parser → dedup →
  Room queue pipeline built above.
- **Expected Deliverable:** `com.spendwise.sms` `BroadcastReceiver` for incoming SMS,
  foreground service lifecycle management, invoking E2-S1/E2-S2/E2-S3/E2-S4 in sequence.
- **Definition of Done:** A simulated incoming SMS broadcast results in a new Room DB row
  when financial, and no row when non-financial.
- **Required Tests:** Instrumented test broadcasting a mock SMS intent and asserting the Room DB state.
- **Estimated Complexity:** Large
- **Depends on:** E2-S4-T1
- **Grounded in:** `docs/spec/architecture.md` SMS Ingestion Flow (full diagram); `CLAUDE.md` Android Module Map (SMS).

#### E2-S5-T3 — First-launch SMS inbox backfill

- **Objective:** Implement the one-time bulk read of the existing SMS inbox at the end of
  onboarding, distinct from the ongoing foreground service.
- **Expected Deliverable:** A `ContentResolver`-based reader over `Telephony.Sms.CONTENT_URI`
  feeding the same filter → parser → dedup → Room queue pipeline, followed by an immediate
  first sync trigger, after which the foreground service (E2-S5-T2) starts.
- **Definition of Done:** Given a mocked SMS inbox content provider with a mix of financial
  and non-financial messages, the backfill enqueues only the financial ones with no
  duplicates, then triggers an immediate sync.
- **Required Tests:** Instrumented test using a fake `ContentProvider` with a fixed set of
  messages, asserting the resulting Room DB queue contents and that sync is triggered exactly once at the end.
- **Estimated Complexity:** Large
- **Depends on:** E2-S5-T1, E2-S5-T2
- **Grounded in:** `docs/spec/architecture.md` "First-Launch SMS Inbox Backfill" section (full flow); `docs/operations/user_flows.md` Onboarding step 8.

---

## Parallel Execution within Epic 2

- E2-S2-T1/T2/T3/T4 (the four parsers) are fully independent of each other once E2-S1-T1 lands — ideal for parallel work across multiple sessions.
- E2-S5-T1 (sync/retry) and E2-S5-T2 (real-time capture) both depend only on E2-S4-T1 and can be built in parallel; E2-S5-T3 (backfill) depends on both.
- The entire epic is parallelizable with Epic 1 (see `../DEPENDENCY-GRAPH.md`).
