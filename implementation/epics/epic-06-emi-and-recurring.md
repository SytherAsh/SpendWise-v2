# Epic 6 — EMI & Recurring Payment Detection

**Working Milestone:** Feed a synthetic set of 3+ transactions from the same
`upi_id`/`recipient_name` within a 60-day window, amounts within ±10% of each other, not
already tracked in `emis` — the recurring-detection job produces a `recurring_payment`
alert. Confirming that alert creates a correctly-linked `emis` row
(`source_transaction_id` pointing at the representative transaction); dismissing it does not.

---

### E6-S1 — Recurring Detection Algorithm

**Independently testable via:** unit tests with synthetic transaction fixtures — no
scheduler or alert pipeline required. Per `../DEPENDENCY-GRAPH.md`, this story can be
built in parallel with Epic 5.

#### E6-S1-T1 — Rolling-window grouping + tolerance matching

- **Objective:** Implement the exact recurring-charge detection rule.
- **Expected Deliverable:** A function that, given a user's transactions, groups by
  `upi_id` (falling back to `recipient_name` when `upi_id` is null) within a rolling 60-day
  window, flags groups of 3+ transactions with amounts within ±10% of each other, and
  excludes any group already represented by an active row in `emis`
  (matched via `source_transaction_id` or by label/amount correlation — define and document
  the exclusion-matching rule precisely, since `emis` doesn't store `upi_id` directly).
- **Definition of Done:**
  - 3 matching transactions within 60 days, amounts within ±10% → flagged.
  - Only 2 matching transactions → not flagged.
  - 3 matching transactions but spanning >60 days → not flagged.
  - 3 matching transactions but one already linked to an active `emis` row → not flagged again.
- **Required Tests:** Unit tests for exactly the 4 cases above, using synthetic fixture data (per `docs/testing.md` Alerts unit tests — recurring-payment detection).
- **Estimated Complexity:** Large
- **Depends on:** E3-S1-T2 (needs the `transactions`/`emis` shape to exist, not a live service)
- **Grounded in:** `docs/requirements.md` "Recurring payment detection rule"; `docs/user_flows.md` "Recurring Payment Detection" flow; `docs/testing.md` Alerts unit tests.

---

### E6-S2 — Recurring Payment Alert & Confirmation Flow

**Independently testable via:** integration tests once Epic 5's alert pipeline exists.

#### E6-S2-T1 — Wire detection into the alert evaluator

- **Objective:** Produce a `recurring_payment` alert when E6-S1's detector flags a group.
- **Expected Deliverable:** The detector runs as part of (or alongside) the Alerts
  evaluator job (E5-S2-T4) — document the chosen cadence (reusing the existing 30-minute
  schedule is the simplest option, since recurring detection is not time-critical per
  `docs/requirements.md`'s "Alert SLA: within 1 hour").
- **Definition of Done:** Given a fixture with a newly-qualifying recurring group, the next
  evaluator run produces exactly one `recurring_payment` alert with a `payload` identifying
  the merchant and amount.
- **Required Tests:** Integration test: seed a qualifying group, run the job, assert exactly
  one `recurring_payment` alert exists with the expected payload; run the job again and
  confirm no duplicate alert is created for the same still-unconfirmed group.
- **Estimated Complexity:** Medium
- **Depends on:** E6-S1-T1, E5-S2-T4
- **Grounded in:** `docs/requirements.md` Alerts table (Recurring payment alert trigger); `docs/architecture.md` Background Jobs (Alert evaluator).

#### E6-S2-T2 — Confirm-as-subscription / dismiss flow

- **Objective:** Let a user turn a detected recurring alert into a tracked `emis` row, or dismiss it.
- **Expected Deliverable:** A confirm action creating an `emis` row with
  `detected_from_sms = true`, `source_transaction_id` set to the representative
  transaction, `is_active = true`; a dismiss action that marks the alert read without
  creating an EMI (and does not re-suppress future genuinely-new recurring groups for the same merchant permanently — only suppresses re-alerting on the same unconfirmed group).
- **Definition of Done:** Confirm → `emis` row created and linked; `idx_emis_source_txn`
  uniqueness holds; dismiss → no `emis` row, alert marked read.
- **Required Tests:** Integration test: confirm path creates the correctly-linked EMI;
  dismiss path creates no EMI; attempting to confirm the same detected group twice does not
  violate `idx_emis_source_txn` (second confirm is a no-op or clear error, not a 500).
- **Estimated Complexity:** Medium
- **Depends on:** E6-S2-T1, E3-S3-T1
- **Grounded in:** `docs/user_flows.md` "Recurring Payment Detection" flow (confirm/dismiss); `docs/database.md` `emis.source_transaction_id` + `idx_emis_source_txn`.

---

## Parallel Execution within Epic 6

- E6-S1-T1 has no dependency on Epic 5 and should be started as early as possible (see
  `../DEPENDENCY-GRAPH.md` "No-go" section for why E6-S2 specifically cannot land early).
- E6-S2-T1 and E6-S2-T2 are sequential (T2 needs the alert from T1 to exist).
