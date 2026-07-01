# Epic 7 — Analytics & Export

**Working Milestone:** For a seeded test user with categorized transactions across
several months, `/analytics/summary`, `/analytics/categories`, `/analytics/comparison`,
and `/analytics/trends` all return correct, hand-verifiable aggregates; `/analytics/export/csv`
and `/analytics/export/pdf` produce correct downloadable files for a selected date range.
An architecture-level test proves Analytics never writes to any other module.

---

### E7-S1 — Aggregation Queries

**Independently testable via:** integration tests against a seeded, categorized fixture dataset.

#### E7-S1-T1 — `GET /analytics/summary`

- **Objective:** Return total spend/income and category breakdown for a date range.
- **Expected Deliverable:** Endpoint accepting `from`/`to` query params, aggregating over
  `transactions` joined to `transaction_categories`, read-only.
- **Definition of Done:** Totals match a hand-computed value against a fixed fixture; full
  computed result returned (not paginated, per `docs/api.md`'s Pagination note).
- **Required Tests:** Integration test with a fixed multi-category, multi-month fixture — assert exact totals.
- **Estimated Complexity:** Medium
- **Depends on:** E3-S2-T1, E4-S3-T2
- **Grounded in:** `docs/api.md` `/analytics` table + query params; `docs/architecture.md` Analytics module (read-only, no business logic).

#### E7-S1-T2 — `GET /analytics/categories`

- **Objective:** Per-category breakdown with drilldown detail.
- **Expected Deliverable:** Endpoint returning per-category totals plus enough detail to
  support a drilldown-to-transactions UI (e.g., category id/name/total/transaction count).
- **Definition of Done:** Matches hand-computed per-category totals against the fixture.
- **Required Tests:** Integration test asserting per-category totals and counts.
- **Estimated Complexity:** Medium
- **Depends on:** E7-S1-T1
- **Grounded in:** `docs/api.md` `/analytics` table.

#### E7-S1-T3 — `GET /analytics/comparison`

- **Objective:** Week/month/year comparison view (this period vs. last).
- **Expected Deliverable:** Endpoint accepting `granularity` (week/month/year) and
  returning current-vs-previous-period totals, overall and per category.
- **Definition of Done:** For a fixture spanning two consecutive months, the month-granularity comparison matches hand-computed values for both periods.
- **Required Tests:** Integration test for each granularity value against a fixture spanning enough time to exercise it.
- **Estimated Complexity:** Medium
- **Depends on:** E7-S1-T1
- **Grounded in:** `docs/api.md` `/analytics` table + query params (`granularity`); `docs/user_flows.md` "Reviewing Transactions" (compare this month vs. last, this year vs. last).

#### E7-S1-T4 — `GET /analytics/trends`

- **Objective:** Spending trend over time as line-chart-ready data.
- **Expected Deliverable:** Endpoint returning a time-bucketed series (per `granularity`) of totals, optionally filtered by `category`.
- **Definition of Done:** Series buckets match hand-computed totals per bucket against the fixture.
- **Required Tests:** Integration test asserting bucketed totals for a known fixture.
- **Estimated Complexity:** Medium
- **Depends on:** E7-S1-T1
- **Grounded in:** `docs/api.md` `/analytics` table.

---

### E7-S2 — Export

**Independently testable via:** integration tests asserting file content/structure.

#### E7-S2-T1 — `GET /analytics/export/csv`

- **Objective:** Export raw transaction data for a selected date range as CSV.
- **Expected Deliverable:** Endpoint streaming/returning a CSV with one row per transaction
  in the range, columns matching the user-facing transaction fields (never `sms_raw_text`).
- **Definition of Done:** CSV parses back to the exact expected row count and values for a fixture; `sms_raw_text` column absent.
- **Required Tests:** Integration test: parse the returned CSV and assert row count/values; assert no `sms_raw_text` column.
- **Estimated Complexity:** Medium
- **Depends on:** E7-S1-T1
- **Grounded in:** `docs/requirements.md` Export section (CSV — raw transaction data); `CLAUDE.md` security invariant on `sms_raw_text`.

#### E7-S2-T2 — `GET /analytics/export/pdf`

- **Objective:** Export a formatted report for a selected date range or full financial year.
- **Expected Deliverable:** Endpoint generating a formatted PDF (choose and document a
  free/open-source PDF generation library compatible with free-tier hosting memory limits)
  summarizing totals, category breakdown, and a simple chart or table.
- **Definition of Done:** PDF is generated without error for both a custom date range and a
  "full financial year" selection; content reflects the same totals as `/analytics/summary` for that range.
- **Required Tests:** Integration test: generate PDF, assert non-empty valid PDF bytes/magic
  number, and that a parsed text extraction contains the expected total figure.
- **Estimated Complexity:** Large
- **Depends on:** E7-S1-T1
- **Grounded in:** `docs/requirements.md` Export section (PDF — formatted monthly/date-range report; custom date range or full financial year).

---

### E7-S3 — Read-Only Enforcement

**Independently testable via:** a static architecture test, no runtime fixture needed.

#### E7-S3-T1 — Architecture test: Analytics is read-only

- **Objective:** Make the "Analytics must not call write methods on any other module" rule
  a build-breaking check, not just a convention.
- **Expected Deliverable:** An ArchUnit (or equivalent) rule asserting no class in
  `com.spendwise.analytics` calls a method on another module's service interface that is
  documented/annotated as a write operation.
- **Definition of Done:** The rule passes on the current codebase and fails if a
  deliberately-introduced write call is added in a throwaway test commit (prove this once, then revert).
- **Required Tests:** The ArchUnit rule itself, run as part of `./gradlew test`.
- **Estimated Complexity:** Medium
- **Depends on:** E7-S1-T1
- **Grounded in:** `CLAUDE.md` Architectural invariants ("The Analytics module is strictly read-only"); `docs/architecture.md` Analytics module dependency row.

---

## Parallel Execution within Epic 7

- E7-S1-T2/T3/T4 are independent of each other once E7-S1-T1's shared aggregation
  primitives exist.
- E7-S2 (export) is independent of E7-S1-T2/T3/T4 — only needs T1's summary primitives.
- Per `../DEPENDENCY-GRAPH.md`, all of Epic 7 is parallelizable with Epic 5 and Epic 6
  (disjoint read vs. write paths).
