# Architecture Decision Records (ADRs)

This document records the key architectural decisions made during the SpendWise planning phase, including the context, options considered, and the rationale for each decision.

---

## ADR-001: Modular Monolith over Microservices

**Status**: Accepted

**Context**: SpendWise has 11 logical modules (Auth, User, Ingest, Transaction Management, Categorization, Budget, Alerts, Recommendations, Chatbot, Analytics, Admin). A microservices architecture was initially considered to support future scaling.

**Options considered**:
1. Microservices from day one (separate deployable units per module)
2. Modular monolith (single deployable, bounded module interfaces)
3. Pure monolith (no internal module boundaries)

**Decision**: Modular monolith.

**Rationale**:
- Free-tier hosting (Render/Railway/Fly.io) typically allows 1–2 always-on instances per account. Running 11 microservices would immediately exceed free limits.
- This is a portfolio project — rapid development and operational simplicity matter more than operational isolation.
- Module boundaries with well-defined service interfaces allow future extraction into microservices without a full rewrite.
- Option 3 (pure monolith) was rejected because it would make future scaling unnecessarily painful.

---

## ADR-002: On-Device SMS Parsing (not server-side)

**Status**: Accepted

**Context**: SMS messages contain the raw transaction data. Parsing can happen either on the Android device before sending to the server, or raw SMS text can be sent to the server for parsing.

**Options considered**:
1. Parse on-device; send structured fields only
2. Send raw SMS to server; parse server-side

**Decision**: Parse on-device.

**Rationale**:
- Raw SMS content often contains personal messages unrelated to transactions. Sending all SMS to a server creates significant privacy risk and complicates DPDP compliance.
- On-device parsing reduces bandwidth (structured JSON vs. raw text).
- The trade-off: parser logic updates require a new APK release. This is acceptable given the small user base (20–30 users) and infrequent format changes.

---

## ADR-003: Adaptive Supervised Learning (not Reinforcement Learning)

**Status**: Accepted

**Context**: User corrections to ML category assignments need to feed back into the model. Early planning described this as "reinforcement learning."

**Decision**: Adaptive supervised learning pipeline.

**Rationale**:
- User corrections are stored as labeled examples in the `ml_corrections` table.
- Periodically (weekly by default), the model is retrained on the baseline dataset plus all accumulated corrections.
- This is standard supervised learning with periodic retraining — not reinforcement learning in the academic sense (no reward signal, no agent-environment loop).
- The distinction matters for implementation: we do not need an RL framework, just a standard scikit-learn or similar classifier with a retraining pipeline.

---

## ADR-004: Server-Side ML Inference (not On-Device)

**Status**: Accepted

**Context**: After on-device parsing, the structured transaction must be categorized. Categorization could run on the device or on the server.

**Options considered**:
1. On-device inference (model bundled in APK)
2. Server-side inference via FastAPI ML service

**Decision**: Server-side inference.

**Rationale**:
- ML inference is compute-intensive and not appropriate for a mobile device running as a background service.
- Server-side inference allows model updates without requiring APK releases.
- The Android app already batches and syncs transactions — categorization naturally fits as a server-side step after ingestion.
- FastAPI (Python) is the right environment for the ML model (scikit-learn, pandas, numpy ecosystem).

---

## ADR-005: Single Consent Screen at Onboarding

**Status**: Accepted

**Context**: DPDP Act 2023 requires explicit user consent for collecting personal data. Multiple consent purposes need to be covered (SMS access, server storage, ML training).

**Options considered**:
1. Granular toggles per purpose (user can opt out of individual purposes)
2. Single consent screen covering all purposes (all-or-nothing)

**Decision**: Single consent screen.

**Rationale**:
- SMS access and server-side storage are non-negotiable — the app cannot function without them.
- The ML training purpose is also core to the product's improvement loop.
- Allowing users to opt out of individual purposes would create complex partial-consent states that are difficult to handle correctly.
- The simpler approach: one consent screen explaining all purposes clearly. If the user does not consent, they cannot use the app.

---

## ADR-006: Shared Database (not Database-per-Module)

**Status**: Accepted

**Context**: In a proper microservices architecture, each service owns its database. In a modular monolith, there is a choice between a shared database and per-module schemas.

**Decision**: Single shared PostgreSQL database on Supabase.

**Rationale**:
- Per-module databases add significant operational complexity for free-tier hosting.
- The modular monolith already enforces module boundaries through service interfaces — cross-module data access is controlled at the code level, not the database level.
- Supabase's Row-Level Security (RLS) enforces user data isolation at the database level regardless of schema structure.
- If modules are later extracted into microservices, the database can be split at that point.

---

## ADR-007: Firebase Authentication

**Status**: Accepted

**Context**: SpendWise needs phone OTP login and Google social login. These can be built from scratch or delegated to an auth provider.

**Decision**: Firebase Authentication.

**Rationale**:
- Handles both phone OTP (via SMS) and Google OAuth in a single SDK.
- Free tier is generous (up to 10,000 SMS verifications/month).
- Eliminates the need to build OTP delivery infrastructure (Twilio, AWS SNS, etc.).
- Well-documented SDKs for Kotlin (Android) and Java (Spring Boot backend).

---

## ADR-008: Cursor-Based Pagination for Transactions

**Status**: Accepted

**Context**: A user's transaction history can grow to thousands of records over time. The API needs a strategy for returning large lists.

**Options considered**:
1. Offset-based pagination (`?page=2&limit=50`)
2. Cursor-based pagination (`?cursor=<last_id>&limit=50`)

**Decision**: Cursor-based pagination.

**Rationale**:
- Offset pagination degrades in performance as the offset grows (database must scan and skip rows).
- Cursor pagination uses the last seen `id` as a reference point — consistent O(1) query performance regardless of how many records exist.
- More reliable when records are being inserted concurrently (offset pagination can skip or duplicate records when new rows are added between page fetches).

---

## ADR-009: Java 21 for Spring Boot Backend

**Status**: Accepted

**Context**: The Spring Boot backend language was initially planned as Kotlin. The decision was revisited during project initialization.

**Options considered**:
1. Kotlin — concise syntax, null safety built in, coroutines for async
2. Java 21 — mature ecosystem, virtual threads (Project Loom), wider hiring pool, well-established Spring Boot integration

**Decision**: Java 21.

**Rationale**:
- Java 21 introduces virtual threads (Project Loom), enabled in Spring Boot 3.2+ via the `spring.threads.virtual.enabled=true` property, providing non-blocking I/O without coroutines.
- The Spring Boot ecosystem has first-class Java support; Java records (since Java 16) cover most of Kotlin's data class use cases for DTOs.
- Java 21 is an LTS release — stable and widely supported by hosting platforms.
- Android app remains Kotlin (unchanged) — the language split is Android = Kotlin, backend = Java 21.

---

## ADR-010: Counterparty Metadata Is Not an ML Category

**Status**: Accepted

**Context**: While preparing ML training data for Epic 4 (`ml/labeling/`), a Payee
Knowledge Base was built that records, per recipient, information beyond spending
category — whether the recipient is a friend, family member, self-transfer account,
merchant, employer, or subscription service. Because ~50% of the labeled dataset is the
`Transfers` category, and this counterparty information was sitting right there in the
knowledge base used to produce it, the question arose: should the ML model predict this
too, e.g. by splitting `Transfers` into `Transfers-Family`, `Transfers-Friend`,
`Transfers-Self`, etc.?

**Options considered**:
1. Expand the category set so ML predicts counterparty type as part of (or instead of)
   the spending category — e.g. more/split Transfer categories.
2. Keep the ML model's label set exactly the 12 categories in `docs/spec/requirements.md`;
   treat counterparty type as separate metadata, sourced from the Payee Knowledge Base,
   attached to a transaction in a later, independent enrichment step (Analytics-layer,
   post-MVP — see `docs/spec/architecture.md` "Future Enhancement: Counterparty Metadata
   Enrichment").

**Decision**: Option 2. The classifier continues to predict only the 12 transaction
categories. Counterparty type is deliberately kept out of the ML problem entirely.

**Rationale**:
- **Model simplicity**: transaction category and counterparty type are different
  questions answered by different signals. Category depends on *what* was purchased;
  counterparty type depends on *who* the recipient is to this specific user. Folding a
  personal/relationship classification into a spending-category classifier conflates two
  problems the model doesn't need to solve jointly, and would fragment the already
  Transfers-heavy class distribution (see `ml/labeling/tracking/LABELING_STATUS.md`
  category distribution — Sports & Fitness already has zero training examples; splitting
  Transfers further would create several more near-empty classes).
- **Maintainability**: the Payee Knowledge Base (who's family/friend/merchant) is
  fundamentally per-user and per-relationship — it doesn't generalize across users the
  way "Swiggy → Food / Dine Out" does. Baking it into a shared, retrained ML model would
  mean either training a model that overfits to one user's contacts, or building
  per-user models far earlier than the per-user personalization already planned in
  `docs/roadmap.md` Phase 4. A simple knowledge-base lookup has no such constraint.
- **Extensibility**: `docs/spec/requirements.md`'s 12 categories are meant to stay stable —
  `CATEGORY_GUIDELINES.md` explicitly says don't add/split categories without updating
  that doc. Counterparty types (friend/family/merchant/employer/subscription/settlement)
  are a fundamentally open-ended, growable list with different governance than the
  spending category taxonomy; they shouldn't be forced through the same frozen list.
- **Future analytics value**: kept as a separate, additive metadata layer, counterparty
  type can be joined onto transactions by the read-only Analytics module without
  touching the ML pipeline, the `categories` table, or the `transaction_categories`
  assignment — no retraining required to change how counterparties are grouped or
  labeled.
- Consistent with the existing architectural boundary in `docs/spec/architecture.md`: the
  Analytics module is strictly read-only and must not call write methods on any other
  module. A counterparty-enrichment feature is a natural fit for that boundary if it's
  read-only lookup/display, not a fit for the ML training loop.

**Consequence**: Epic 4 trains and evaluates against exactly the 12 categories in
`docs/spec/requirements.md`, no more. Counterparty metadata enrichment is deferred to a
post-MVP enhancement (`docs/roadmap.md` Phase 9), most naturally landing in Epic 7
(Analytics) or a dedicated future epic once prioritized — not built or scoped now.

**Status update (2026-07-09, UI/UX polish phase):** the deferred sketch above has now
been built, per this ADR's own Option 2 shape — see `docs/spec/database.md`'s `contacts`
table and `docs/spec/api.md`'s `/contacts` endpoint group. It landed as a plain per-user
CRUD table owned by the User module, matched against `transactions.recipient_name`/
`upi_id` client-side by the frontend — never as an ML category, never joined onto
`transactions`/`transaction_categories` server-side, and with no dependency added from
the Transaction or Categorization module onto it. This ADR's decision and rationale
remain the governing record for *why* it's shaped this way; only its "not built" status
is now out of date.

---

## ADR-011: Alert Evaluation Stays Scheduled — Event-Driven Is a Deferred, Unimplemented Optimization

**Status**: Accepted (scheduled evaluation implemented in Epic 5); event-driven variant
explicitly deferred, not built.

**Context**: While planning Epic 5 (Budget & Alerts), before any code was written, the
project owner asked whether alert evaluation should be split into two execution models:
an **event-driven** path (re-evaluate a user's alerts immediately after a transaction is
created, updated, deleted, or recategorized) for the three real alert rules
(`mid_month_budget`, `category_overspend`, `category_approaching_limit`), versus keeping
everything on the `@Scheduled` evaluator the epic's own text specifies. The proposal also
grouped a few adjacent-but-different things under "scheduled" (spending insights, budget
suggestions, historical analytics, periodic summaries) that turned out to belong to
other epics (Recommendations/Epic 8, Analytics/Epic 7) or aren't background jobs at all
(`/budgets/progress` and `/budgets/suggestions` are pull endpoints, not alerts) — that
scoping mismatch is noted here for the record but isn't this ADR's subject.

**Options considered**:
1. **Event-driven only** — Transaction/Ingest publishes a change event; Alerts reacts
   immediately.
2. **Scheduled only, exactly as specified** — the single `@Scheduled` (every 30 minutes)
   evaluator from `implementation/epics/epic-05-budget-and-alerts.md` E5-S2-T4, reading
   all users' spend/budget state in bulk via the `spendwise_jobs` role (same mechanism
   Epic 4 built for the categorization retry/retrain jobs).
3. **Hybrid** — keep the scheduled evaluator as the authoritative backstop (and the only
   driver of the mid-month rule, see below), and additively wire an async Spring
   `ApplicationEvent`/`@EventListener` nudge that re-runs the overspend/approaching-limit
   rules for one user immediately after their own transaction data changes.

**Decision**: Option 2 for this epic. Option 3 is a legitimate, spec-compliant future
optimization — sketched below for a later epic to pick up — but was not implemented now.

**Rationale**:
- **The mid-month rule cannot be event-driven at all, not even partially.** E5-S2-T1 is
  "spent 50% of total budget **by mid-month**" (epic DoD: "fixture at exactly 50% by the
  15th triggers"). Consider a user who crosses 50% on the 5th and makes no further
  transactions. A purely event-driven design fires nothing on the 15th, because no
  transaction event occurs that day — the rule's own semantics require a calendar tick
  that no transaction event can provide. Any design must keep a scheduled component for
  this rule regardless of what happens with the other two.
- **Module boundaries don't have a call path for it.** `docs/spec/architecture.md`'s "Allowed
  module dependencies" table lists Ingest's callable modules as "Transaction
  (persist), Categorization (trigger), User (device API key validation only), Auth
  (session validation only) | Must not call: Any other module" — Ingest → Alerts is
  explicitly forbidden. Transaction already has Alerts as a caller of *it*
  ("Alerts → Transaction (read spend)"), so a direct Transaction → Alerts call the other
  direction would be a circular dependency, which CLAUDE.md's architectural invariants
  prohibit. A decoupled `ApplicationEvent`/`@EventListener` (Option 3) sidesteps the
  compile-time dependency and avoids the cycle, but is still a new mechanism this
  document didn't previously describe.
- **The product doesn't need the responsiveness.** `docs/spec/requirements.md`: "Alert SLA:
  within 1 hour of a transaction being processed" and "Users review spending at end of
  day — near-real-time is not required." A 30-minute sweep meets this SLA with margin,
  for a portfolio-scale product (20–30 users).
- **Simplicity.** One evaluation path is easier to reason about and test than two paths
  that both need to agree on suppression state (`alerts` table dedup-per-month) and
  rule outcomes. The scheduled evaluator already reuses Epic 4's proven cross-user-read
  pattern (`spendwise_jobs` role via `JobsDataSourceConfig`) rather than introducing a
  new one.

**What the event-driven option would look like, if built later:** keep the `@Scheduled`
evaluator as the sole driver of `mid_month_budget` (for the reason above) and as the
correctness backstop for the other two rules; additionally, have `TransactionService`
publish a `TransactionsChangedEvent` (Spring `ApplicationEventPublisher`, in-process, no
new infrastructure — CLAUDE.md forbids adding Kafka/RabbitMQ/Redis without approval) after
`persistFromIngest`/`createManual`/`correctCategory`; an `@EventListener` (`@Async`) in the
Alerts module re-runs `CategoryOverspendRule`/`CategoryApproachingLimitRule` for that one
user only, calling the same `AlertsService.recordIfNotAlreadyTriggeredThisMonth` and
`AlertDispatchService` the scheduled job already uses — no duplicated rule logic. This
would need its own ADR amendment to `docs/spec/architecture.md`'s module table (an event
publisher/listener pair isn't a "call" in the existing table's sense, but should still be
documented) before implementation, and is only worth the added complexity if a future
epic's requirements actually demand near-real-time alerts — nothing in the current MVP
scope (`docs/roadmap.md`) does.

**Consequence**: Epic 5 ships with exactly the scheduled evaluator the epic specified.
Nothing event-driven was built. This ADR exists so the option isn't silently lost or
re-litigated from scratch if a future epic's requirements change.

---

## ADR-012: Categorization Is the ML Gateway for Every Module, Not Just Itself

**Status**: Accepted

**Context**: The ML strategy phase's first extension beyond transaction categorization
is a recurring-payment classifier (`ml/api/predict-recurring`, `ml/api/retrain-recurring`)
that replaces E6-S1-T1's exact-match rule as the production gate for `recurring_payment`
alerts. CLAUDE.md's invariant is "FastAPI is called only from the Categorization module,"
enforced by `CategorizationBoundaryTest` (an ArchUnit rule: no class outside
`com.spendwise.categorization` may depend on `MlClient`). The Alerts module needs a
recurring-payment prediction for every candidate group `RecurringPaymentDetector`
proposes — this is the "architecture questions... are open questions for this phase, not
settled decisions" CLAUDE.md's Current Phase section flagged before any of this was built.

**Options considered**:
1. **Grant Alerts direct FastAPI access** — add a documented exception to the invariant,
   analogous to the existing "Allowed module dependencies" addenda in
   `docs/spec/architecture.md` (Ingest/User, Alerts/User). Alerts would call FastAPI
   directly for `/predict-recurring`, bypassing Categorization entirely.
2. **Categorization becomes the ML gateway for every capability, not just its own** —
   Alerts calls `CategorizationService#predictRecurring` (a new method on the *existing*
   service interface it's already permitted to depend on transitively), which internally
   proxies to `MlClient#predictRecurring`. The invariant itself doesn't change — no new
   class outside `com.spendwise.categorization` touches `MlClient` — Categorization's role
   just widens from "the categorization module" to "the module that talks to FastAPI."
3. **Run the recurring classifier inside Spring Boot, not FastAPI** — a small in-process
   Java ML library (e.g. Smile, Tribuo), sidestepping the invariant question entirely.

**Decision**: Option 2.

**Rationale**:
- **No new exception to police.** Option 1 would be the third dependency-table addendum
  of its kind (after Ingest/User and Alerts/User), but those addenda grant read-only
  access to plain data (`users.email`, device keys) — granting a second module direct
  access to the *single internal-only ML service* multiplies the number of call paths
  `api/security.py`'s `X-Internal-Key` check has to be trusted from from one to two, for
  no benefit Option 2 doesn't already provide.
- **`CategorizationBoundaryTest` keeps working unmodified.** `MlClient` still has exactly
  one caller-module. Nothing about the ArchUnit rule's own definition needed to change —
  only `CategorizationService`'s interface grew a method, which is exactly the kind of
  change cross-module service interfaces are meant to absorb.
- **Ruled out Option 3 for the same reason ADR-004 ruled out on-device inference**: the
  team's ML tooling, training pipeline, and retraining cadence all already live in
  Python/scikit-learn (`ml/training/`). Splitting a second model into a different language
  and runtime doubles the operational surface for no accuracy or latency benefit at this
  scale (~20–30 users), and forfeits the shared `HierarchicalCategoryModel`-style
  patterns and adaptive-retraining precedent (ADR-003) the categorization model already
  established.
- **Precedent already existed, just not exercised.** `CategorizationService` already
  serves Admin (`triggerRetrain`, `getAccuracyMetrics`) in addition to Ingest
  (`categorize`) — it was already a gateway for more than one caller before this change;
  `predictRecurring` is the same pattern for a third caller and a second underlying
  capability.

**Consequence**: `docs/spec/architecture.md`'s "Allowed module dependencies" table gets a
new addendum (Alerts → Categorization, read-only/proxy-only — Alerts never writes through
Categorization) rather than a new FastAPI-access exception. Any future ML capability
(alerts/overspend anomaly detection, a trained recommendations model) should default to
the same shape — a new `CategorizationService` method — unless a specific reason emerges
to reopen this ADR.

## ADR-013: Recipient-Name Canonicalization Is a Batch Job Behind the Categorization Gateway

**Status**: Accepted

**Context**: Raw `recipient_name` from SMS/bank-statement parsing renders a single real
payee several ways across transactions — case differences ("SWIGGY" vs "Swiggy"), split or
truncated names, and shared-then-diverging strings. This fragments two things: recurring-
payment detection (`RecurringPaymentDetector` grouped on the raw `upi_id`/`recipient_name`,
so spelling variants splinter into separate merchant keys and each falls below the
occurrence threshold on its own), and any payee-level UI/analytics aggregation. A validated
two-tier normalization algorithm (exact UPI-ID grouping → rapidfuzz `token_sort_ratio` +
scipy complete-linkage hierarchical clustering, with a first-name-conflict guard and a
truncation-prefix merge pass) already existed in the offline labeling pipeline that produced
the `Recipient_Canonical` column of the training dataset, but nothing equivalent ran in the
live product.

**Options considered**:
1. **Reimplement the clustering in Java, run it per-transaction at ingest.** Keeps
   everything in the JVM, no network hop.
2. **Port the algorithm near-verbatim into the FastAPI service, expose it as a new endpoint,
   and call it from a weekly per-user batch job through the Categorization gateway** (the
   ADR-012 pattern) — `CategorizationService#normalizeRecipients` → `MlClient` →
   `POST /normalize-recipients`, driven by `RecipientCanonicalizationJob`, writing the
   result to a new nullable `transactions.recipient_canonical` column.
3. **Do nothing; keep matching on raw names.**

**Decision**: Option 2.

**Rationale**:
- **Per-transaction is the wrong shape for this algorithm.** Canonicalization is inherently
  a whole-set operation — the cluster a name belongs to is only defined relative to a user's
  entire recipient history, and hierarchical clustering compares every name against every
  other. There is no meaningful "canonical name for one transaction in isolation," so an
  ingest-time hook (Option 1's implied trigger) can't produce a stable result. A scheduled
  batch that recomputes over the full history — the same adaptive-batch precedent as the
  categorization retrain (ADR-003) — is the natural fit.
- **Algorithmic fidelity.** The thresholds (UPI-similarity 55 vs fuzzy 90), complete-vs-
  single linkage, and the first-name-conflict short-circuit are precision-sensitive and were
  validated against real data offline. Re-deriving them in a Java reimplementation (Option 1)
  risks silent drift from the version that produced the training labels; a near-verbatim
  Python port keeps the live result identical to the offline one and reuses the existing
  `rapidfuzz`/`scipy` stack rather than adding Java fuzzy-clustering dependencies.
- **No new invariant exception.** Exactly as in ADR-012, this rides the Categorization ML
  gateway — `MlClient` gains a caller-method, not a caller-module, so
  `CategorizationBoundaryTest` is untouched. This is the "any future ML capability should
  default to the same shape" consequence ADR-012 anticipated, now exercised for a third
  capability.
- **Additive and reversible.** `recipient_canonical` is nullable and never mutates the raw
  `recipient_name`; every read site (`RecurringPaymentDetector`, the frontend payee labels
  and analytics grouping) falls back to the raw name when it is null, so nothing depends on
  the job having run and the column can be dropped without data loss.

**Consequence**: A new nullable column (`V13__add_canonical_recipient_name.sql`), a new
FastAPI endpoint (`POST /normalize-recipients`, added to `api/security.py`'s protected
paths), a new weekly `RecipientCanonicalizationJob` in the Background Jobs table, and a
third `CategorizationService` gateway method. Recurring detection and payee-level display/
analytics prefer the canonical name when present. The salary/recurring-credit and the
persistent recipient→category override-rule work discussed in the same phase are deliberately
**not** included here and remain open. Retraining/refresh cadence matches the weekly
categorization retrain; there is no online/incremental update (consistent with ADR-003).

## ADR-014: Recipient-Canonicalization Corrections Are a User-Owned Permanent Override, Not a Retrainable Label

**Status**: Accepted

**Context**: ADR-013's clustering algorithm (fuzzy similarity + hierarchical clustering, fixed
thresholds tuned against the offline demo dataset) will occasionally merge or fail to merge
payees incorrectly once it runs against real users' bank/UPI naming conventions it wasn't tuned
on. Unlike categorization (`ml_corrections`) or recurring detection (`recurring_corrections`),
canonicalization has no trainable model behind it — `basic_normalize`/`cluster_by_fuzzy_name`/
`merge_prefix_chains` are deterministic string-similarity code with fixed constants. There is
nothing to retrain from a correction; a correction can only ever mean "pin this identity's
displayed name and stop letting the algorithm recompute it."

**Options considered**:
1. **No correction mechanism** — treat canonicalization as fully automated, revisit thresholds
   manually if enough complaints accumulate.
2. **A permanent per-identity override table**, written directly by the Transaction module
   (mirroring `ml_corrections`' precedent: `TransactionService#correctCategory` writes
   `ml_corrections` itself, no cross-module call to Categorization) and re-applied on every
   subsequent `RecipientCanonicalizationSweep` run so it can never be silently overwritten by a
   later resweep.
3. **A one-time admin-triggered fix** with no persistence — reapply manually after every sweep.

**Decision**: Option 2.

**Rationale**:
- **Same module-ownership precedent as `ml_corrections`.** `recipient_canonicalization_overrides`
  is owned and written by the Transaction module via a new `TransactionService#correctPayeeName`,
  exactly like category corrections — no new cross-module call, no exception to "Categorization
  may call: Transaction (update category)" needed in either direction.
  `RecipientCanonicalizationSweep` (Categorization module) reads it back cross-user via the
  `spendwise_jobs` role, the same read-a-correction-table shape `MlRetrainingJob` already uses.
- **A correction is a pin, not a label.** Because there is no model to retrain, the override is
  applied as the final step of `canonicalizeUser` — it wins over whatever the ML clustering
  response says for that identity, forever, until the user changes it again or removes it. This
  keeps the fix permanent across the weekly resweep instead of the algorithm quietly reverting it.
- **Granularity matches the existing identity key.** The override keys on
  `(user_id, recipient_name, upi_id)` — the same natural key `RecipientIdentity` and
  `updateCanonicalForIdentity` already use. There is no finer-grained key available (raw
  transactions carry no stable per-payee id), so two transactions that happen to share the exact
  same raw `recipient_name`/`upi_id` cannot be split into different payees; this is a known,
  accepted limitation, not a gap to solve here.
- **Doubles as the recalibration signal discussed for ADR-013's threshold risk.** Because
  overrides are durable and queryable, reviewing them periodically (manually, not automated)
  shows whether real-user drift is "algorithm merges too eagerly" (splits) or "algorithm merges
  too conservatively" (renames with no splits) — the same diagnostic role `ml_corrections` plays
  for spotting categorization drift, without requiring separate telemetry.
- **User-facing entry point.** The Transactions page exposes this per-transaction (rename/split
  one payee) and per-group (rename every transaction currently grouped together at once, behind a
  confirm step since it's a wider-blast-radius action than the per-row control).

**Consequence**: A new table (`V14__recipient_canonicalization_overrides.sql`, RLS-scoped
per-user, no unique constraint on the nullable identity columns — Postgres treats NULLs as
distinct by default, so upsert semantics are enforced in
`RecipientCanonicalOverrideRepository` via an explicit `IS NOT DISTINCT FROM` delete-then-insert
rather than `ON CONFLICT`), a new `PUT /transactions/:id/payee` endpoint, and
`RecipientCanonicalizationSweep` consulting the override map before writing the ML service's
answer for each identity. No new module-dependency exception. A user renaming or splitting a
payee sees the change reflected immediately (not after the next weekly sweep), since
`correctPayeeName` updates `recipient_canonical` directly in the same call that writes the
override row.

## ADR-015: Merge Payees Is a Human-Review Queue Over the Clustering Algorithm's Own Discarded Ambiguity

**Status**: Accepted

**Context**: ADR-013's clustering pipeline has three guards that deliberately leave real matches
unmerged rather than risk merging two different people: `_first_names_conflict` (forces two
names apart on a raw fuzzy score alone), the fuzzy-clustering `threshold` boundary itself (a
near-miss just below 90 stays split), and `merge_prefix_chains`'s "more than one competing
maximal target" case (e.g. a bare first name that could belong to either of two fuller names —
observed on real account data: `SAMEER` ambiguous between `SAMEER SAWANT` and `SAMEER BALIRAM
SAWA`). Every one of these cases already computes the exact evidence needed to ask a human — a
candidate pair and a reason — and then discards it, leaving the user with no way to resolve a
splintered payee short of manually renaming one row at a time on the Transactions page. Merge
Payees is a lightweight, gamified review queue: the user is shown an anchor identity plus its
still-ambiguous candidates and taps same/different, one small group at a time.

**Options considered** (four independent sub-decisions, each with a real alternative):

1. **Candidate persistence** — one status-tracked table (`recipient_merge_suggestions`, lifecycle
   `PENDING → CONFIRMED_SAME | CONFIRMED_DIFFERENT`) vs. two tables (a pending queue plus a
   separate "confirmed different" record).
2. **Module placement** — extend the Transaction module (which already owns the entire payee-
   identity domain: `RecipientIdentity`, `recipient_canonical`,
   `recipient_canonicalization_overrides`, `correctPayeeName`) vs. a new dedicated module.
3. **Ambiguity-data transport** — extend `POST /normalize-recipients`'s existing response with an
   `ambiguous_groups` field (same clustering pass) vs. a second FastAPI endpoint.
4. **Immediate re-evaluation trigger** — after a merge is confirmed, recurring-payment detection
   and budget alerts should reflect the corrected identity right away, not after the next
   scheduled `AlertEvaluatorJob` run. Transaction has no existing path to call into Alerts (only
   the reverse, Alerts → Transaction, is an allowed dependency); considered (a) a new direct
   Transaction → Alerts dependency, (b) a Spring domain event, (c) the frontend sequencing two
   already-authenticated per-user API calls (confirm the merge, then trigger re-evaluation).

**Decision**: One table (1); extend Transaction (2); extend the existing response (3); frontend-
sequenced calls, no new backend dependency (4).

**Rationale**:
- **One table, not two.** The sweep's dedup check ("has this exact pair already been suggested or
  resolved?") has to look across every status regardless of how many tables exist, so a second
  table buys no isolation, only a second existence check to keep in sync. Dedup compares the pair
  as an *unordered* set (`UnorderedPairKey`) — the algorithm can flip which identity it calls
  "anchor" between resweeps as name frequencies shift, so matching only on
  `(anchor, candidate)` literally would let a rejected pair resurface forever on a role swap.
- **Extend Transaction, not a new module.** Same reasoning ADR-014 already established for
  `recipient_canonicalization_overrides`: this feature is entirely about the payee-identity
  domain Transaction already owns end to end. A new module would need its own new dependency-
  table row just to re-reach behavior that already lives here — pure duplication, no isolation
  benefit. The "confirm same" write path reuses `correctPayeeName`'s core directly (extracted
  into `correctPayeeIdentity(userId, recipientName, upiId, canonicalName)`, since the queue
  resolves an identity, not a specific transaction id) rather than reimplementing the override
  upsert + immediate `recipient_canonical` update a second time.
- **Extend the response, not a second endpoint.** `ambiguous_groups` is a byproduct of the exact
  clustering pass that already produces `canonical_names` — a second call would re-derive the
  same clusters twice per user (doubling FastAPI cost) and risks the two answers disagreeing if
  anything about the entries changed between calls. `RecipientCanonicalizationSweep` already
  calls `/normalize-recipients` once per user on the existing schedule; the same call now also
  populates the review queue, with zero new job and zero new network round trip.
- **No new module dependency for the re-evaluation trigger.** A direct Transaction → Alerts call
  would be the *first* reverse edge in the whole dependency graph ("data flows inward," per
  `docs/spec/architecture.md`) and needs the same explicit sign-off as any other invariant
  exception; a domain event avoids the hard dependency but introduces an event-based
  communication pattern this codebase doesn't use anywhere else. Sequencing two already-
  authenticated per-user calls client-side (`POST /payee-merge-queue/resolve` then
  `POST /alerts/reevaluate`) needs neither — it reuses the same "extract the job's logic into a
  directly-callable method" idiom `CategorizationServiceImpl#triggerCanonicalizationSweep`
  already established for the admin manual-trigger endpoints, applied to a new
  `AlertEvaluatorJob#runForUser`. If the second call fails, the merge itself has already been
  saved and the next scheduled `AlertEvaluatorJob` run still catches it — the same
  graceful-degradation contract every background job in this codebase already follows.
- **A candidate identity already pinned by an override is never re-suggested.** If a user has
  already confirmed identity X is the same as some other anchor, X is excluded from any *new*
  suggestion the sweep would otherwise generate against a different anchor — otherwise a name
  ambiguous against two or more targets (the exact `SAMEER` case above) could re-litigate an
  already-settled question indefinitely.

**Consequence**: A new table (`V15__recipient_merge_suggestions.sql`), a new
`ambiguous_groups` field on `/normalize-recipients`'s response (default empty, so an older ML
service response still deserializes), two new Transaction-module endpoints
(`GET /payee-merge-queue`, `POST /payee-merge-queue/resolve`), a new
`POST /alerts/reevaluate` endpoint on the existing `AlertController` (backed by
`AlertEvaluatorJob#runForUser`, a genuinely new per-user-scoped code path alongside the existing
cross-user `run()`), and a new user-facing page (`/merge-payees`). No new module-dependency
exception, no new architecture-table entry. `review_floor` (the fuzzy near-miss band's lower
bound, currently 78) is a starting value, not empirically validated against real data the way
ADR-013's `threshold`/`min_upi_name_similarity` were — expect to recalibrate from real usage,
the same posture ADR-014 already established for tuning after the fact.

## ADR-016: Admin Can Manually Trigger Every Scheduled Job, via a Shared Marker Interface for the Jobs That Can't Route Through Their Own Service

**Status**: Accepted

**Context**: Before this ADR, only two of the app's five `@Scheduled` jobs were admin-triggerable
on demand (`MlRetrainingJob`, `RecipientCanonicalizationJob` — both via
`CategorizationService#triggerRetrain`/`#triggerCanonicalizationSweep`, the established "job
delegates to its owning module's service method, both the schedule and the manual trigger call
the same method" shape). The other three (`CategorizationRetryJob`, `AlertEvaluatorJob`,
`RecommendationGeneratorJob`) had no manual trigger at all — a real gap surfaced directly by a
production bug this same phase: the recipient-canonicalization sweep silently failed for a week
(a missing `@Transactional` on an RLS-scoped read — see the `getMergeQueueSnapshot` fix in the
same session) with no way to force a re-run or see that it had failed, short of waiting for the
next Sunday cron and watching a console at the right moment.

**Options considered**:
1. **Extend each job's owning-module service interface** (`AlertsService#triggerFullEvaluation`,
   `RecommendationsService#triggerGeneration`, `CategorizationService#triggerCategorizationRetry`)
   and move each job's orchestration logic into that service implementation — full consistency
   with the existing retrain/canonicalize shape.
2. **A shared `ManuallyTriggerableJob` marker interface** (`common.job`, one method `runNow()`)
   implemented directly by each job class, injected into `AdminServiceImpl` by qualifier —
   Admin depends on an interface (satisfying "cross-module calls go through injected service
   interfaces only") without either the job's internals or its owning module's service moving.
3. **Inject the job classes directly into `AdminServiceImpl`** — simplest code, but a literal
   violation of the "never depend on another module's implementation class" invariant.

**Decision**: Option 2, applied uniformly to all three jobs — even though option 1 would have
worked cleanly for `CategorizationRetryJob` and `RecommendationGeneratorJob` in isolation.

**Rationale**:
- **`AlertEvaluatorJob` rules out option 1 on its own.** Moving its orchestration into
  `AlertsServiceImpl` would require injecting `BudgetService`, `TransactionService`,
  `EmiService`, `AlertDispatchService`, and `CategorizationService` there too.
  `AlertDispatchServiceImpl` already depends on `AlertsService` (to look up dispatch
  preferences) — so `AlertsServiceImpl` depending on `AlertDispatchService` back would be a real
  circular bean dependency, not a style objection. Verified directly (`grep` confirmed
  `AlertDispatchServiceImpl`'s constructor already takes `AlertsService`) before deciding, not
  assumed.
- **One pattern beats two.** With `AlertEvaluatorJob` forced onto option 2 regardless, applying
  option 1 to only the other two jobs would leave three different wiring styles for what is
  conceptually one feature — harder to explain, and the next person adding a fourth
  manually-triggerable job would have no single obvious pattern to follow.
- **Zero risk to existing, working, well-tested job internals.** `AlertEvaluatorJobTest` alone
  was 300+ lines of existing coverage; option 1 would have required moving that logic (and either
  duplicating or relocating its tests) for a feature that is, at its core, "let admin call a
  method that already exists." Option 2 adds one method (`runNow() { run(); }`) per job with zero
  changes to any existing method's behavior.
- **Still satisfies the module-boundary invariant's actual intent**, not just its letter: Admin
  depends on an interface type it can be handed any implementation of, not a concrete class —
  the interface just happens to be narrower (one method) than a full domain service.
- **Run outcomes are visible without a return value.** Every job already follows the
  "never throw, log and move on" contract so the scheduler thread survives a bad run — meaning
  `runNow()` returning normally doesn't imply success. Each job now calls the same
  `com.spendwise.common.db.AdminEventLog` (new this phase — previously nothing in the app wrote
  to `admin_logs` at all, despite the table and its admin-portal reader existing since Epic 11)
  at both its top-level bulk-lookup failure points and its successful-completion point, so the
  admin Logs page shows real run history (`canonicalization_run`/`_failure`,
  `categorization_retry_run`, `alert_evaluation_run`, `recommendation_generation_run`) — not just
  "the button was pressed."

**Consequence**: A new interface (`com.spendwise.common.job.ManuallyTriggerableJob`), three new
`POST /admin/*` endpoints (`/categorization/retry`, `/alerts/evaluate`,
`/recommendations/generate` — `/ml/canonicalize-recipients` already existed but had no admin-UI
button until now), a new admin page (`/admin/ops`, "Scheduled Jobs"), and five new `admin_logs`
event types. No change to any job's scheduled behavior or existing business logic — every change
is additive (a new interface implementation, a new logging call, a new constructor parameter for
`AdminEventLog`). `RecommendationGeneratorJob`'s manual trigger makes the same real, billed LLM
call a scheduled run would — flagged in the admin UI, not gated behind a confirmation step.

## ADR-017: The Labeled Dataset File Is Discovered by Directory Convention, Never Hardcoded

**Status**: Accepted

**Context**: `training/train.py`, `training/train_recurring.py`, `evaluation/evaluate.py`,
`evaluation/evaluate_recurring.py`, `api/retrain.py`, and `api/retrain_recurring.py` each had
their own `DEFAULT_DATA_PATH = .../data/spendwise_labeled.xlsx` constant (three independent
copies of the same hardcoded filename). When the actual file on disk was replaced with a
differently-named, updated export (`SpendWise_Final_Labeled.xlsx`), `GET /evaluate` started
throwing `FileNotFoundError` on every call — which, one layer up, is the second instance this
same session of an unhandled backend exception turning into an unhelpful, bodiless 403 on the
Spring Boot side (`UserJwtAuthFilter`/`AdminJwtAuthFilter`, both `OncePerRequestFilter`s, don't
re-run on the `/error` forward dispatch a thrown exception triggers — see the
`getMergeQueueSnapshot` RLS fix earlier this session for the first instance of the same shape).
Separately, the project owner expects to work with multiple labeled-dataset exports over time
(more CSVs as more real transaction history gets labeled), not one permanent file.

**Options considered**:
1. **Rename the constant's target to match the new file** — a one-line fix, but recreates the
   exact same failure mode the next time the file is replaced or renamed.
2. **Directory-convention discovery**: a new `training/dataset_locator.py` scans `ml/data/`
   for `.csv`/`.xlsx` files and returns whichever was modified most recently; every training/
   evaluation/retraining call site defaults to this instead of a fixed path, while still
   accepting an explicit `--data`/`data_path` override for one-off runs against a specific file.
3. **A real upload feature** — an admin-portal page to upload a dataset file through the
   browser, POSTed to a new backend endpoint, stored server-side. Considered and explicitly
   declined by the project owner in favor of option 2 for now (confirmed via clarifying
   questions before implementation, per this phase's interview-first working principle) — the
   existing workflow is already "place a file in `ml/data/`," and that workflow keeps working
   unchanged under option 2 with zero new endpoint/storage/security surface.

**Decision**: Option 2.

**Rationale**:
- **Matches the actual failure mode.** The bug was never "the wrong filename" in isolation —
  it was that *any* filename hardcoded in six places would eventually go stale again the next
  time the dataset export changes. Discovery-by-convention is the only option that doesn't
  recreate the same class of bug on the next file swap.
- **"Most recently modified" needs no manifest or naming scheme.** Re-running
  `labeling/scripts/merge_datasets.py`, or manually dropping in a newer CSV export, always
  produces a fresher mtime than whatever was there before — the discovery rule falls out of
  normal filesystem behavior, not a convention the user has to remember to follow (no required
  prefix, suffix, or exact name).
- **One shared implementation, not three copies.** All three previous `DEFAULT_DATA_PATH`
  constants (`train.py`, `train_recurring.py`, and a re-export in `evaluate_recurring.py`) are
  replaced by one call to `find_latest_dataset_file()`, resolved lazily inside
  `load_labeled_dataset()` at call time — not a module-level constant resolved once at import
  time, which would go stale for the lifetime of a running `--reload` process the moment a file
  is swapped while it's up.
- **Both formats supported, not a forced migration.** The current file is `.xlsx`; CSVs are
  expected going forward. `load_labeled_dataset` dispatches on file extension
  (`pd.read_csv`/`pd.read_excel`), so today's file keeps working unchanged and a future CSV
  just works too — no re-export step required.
- **Column names are normalized, not assumed.** Fixing discovery alone surfaced a second,
  independent mismatch: the actual current file's headers are PascalCase
  (`Transaction_Date`, `Recipient_Name`, `UPI_ID`, ...) while `build_feature_frame`/
  `train_model`/`train_recurring` all expect lowercase snake_case, and reading it raised
  `KeyError: 'transaction_date'` — the exact same masked-403 shape, one column-name away from
  the first bug this ADR exists to prevent. `load_labeled_dataset` now lowercases and
  underscore-normalizes every column name after reading (case/whitespace only — it does not
  invent a semantic mapping for a differently-*named*, not just differently-*cased*, column),
  so this file and any future differently-cased export both work without a code change either.
- **A missing dataset now fails clearly, not silently as an opaque 500.** `FileNotFoundError`
  from `pandas.read_excel` on a nonexistent path (the original bug) surfaces no useful
  information. `NoLabeledDatasetFoundError` names the directory it searched and what it
  expects to find there, and a new FastAPI exception handler (`api/main.py`) turns it into a
  clean `503` with that message in the body, instead of the default unhandled-exception `500`
  — this doesn't fix the separate Spring-side masked-403 mechanism (out of scope here), but it
  does mean the *next* debugging session starts from a real error message instead of rediscovering
  the same investigation this one required.

**Consequence**: A new module (`training/dataset_locator.py`, with its own test coverage,
`tests/test_dataset_locator.py`), six call sites simplified to a single shared discovery path,
a new FastAPI exception handler, and four existing "skip if no real dataset present" test
guards switched from `DEFAULT_DATA_PATH.exists()` to the same discovery function. No schema
change, no new endpoint, no new module dependency. `ml/data/`'s `.gitignore` entries
(`*.xlsx`, `*.csv`) are unchanged — the real labeled dataset stays out of version control
exactly as before; only how it's *located* changed.

## ADR-018: Every Background Job's Schedule Is Admin-Configurable at Runtime, via a Dynamic Spring `Trigger` Over a `job_schedules` Table

**Status**: Accepted

**Context**: All five background jobs (`MlRetrainingJob`, `RecipientCanonicalizationJob`,
`CategorizationRetryJob`, `AlertEvaluatorJob`, `RecommendationGeneratorJob`) had their schedule
fixed at compile/deploy time: two via `@Scheduled(cron = "${app.ml.*-cron}")` (env-var
overridable, but still requires a redeploy to change), three via a literal
`@Scheduled(fixedRate = N, ...)` in the annotation itself — not even externalized to a property.
One of those three, `AlertEvaluatorJob`, had been shrunk from 30 minutes to 1 minute during local
model verification on 2026-07-18 with a comment reading "TESTING ONLY... restore to 30 before any
real use" that was never reverted — exactly the kind of drift a fixed, redeploy-only schedule
makes easy to miss and hard to catch, and the project owner explicitly flagged it as a live
concern while asking for this feature. The ask: an admin dashboard page to view and edit every
job's schedule directly, with changes taking effect without a redeploy.

**Options considered**:
1. **Keep `@Scheduled`, make the cron string admin-editable but effective only on next restart.**
   Smallest change — an admin-editable settings row Spring re-reads at startup — but doesn't meet
   "I will adjust the time through admin dashboard" as actually asked; a change silently doesn't
   apply until whenever the process next restarts.
2. **Dynamic scheduling via a custom `Trigger`, read from a new `job_schedules` table, registered
   through `TaskScheduler.schedule(Runnable, Trigger)` instead of `@Scheduled`** — the standard
   Spring mechanism for a schedule that can change at runtime. `Trigger#nextExecution` is called
   fresh each time Spring needs to know when to fire next, so it can read current DB state instead
   of a value baked in at startup.
3. **A general-purpose job-scheduling library (e.g. Quartz)** with a persistent job store. Full
   job-scheduling feature set (misfire policies, clustering, job history) SpendWise doesn't need
   at 20–30 users, and a new dependency + its own schema, contrary to CLAUDE.md's "do not add
   [infrastructure] without explicit approval" posture for anything beyond what's asked.

**Decision**: Option 2, with one addition beyond the textbook pattern: option 2 alone (a
declarative `SchedulingConfigurer` registering `Trigger`s once at startup) still has a real gap —
once Spring computes and locks in a concrete next-fire instant via the underlying
`ScheduledExecutorService`, it does not ask the `Trigger` again until that instant arrives.
Updating the DB row alone would only apply "starting from whichever run was already scheduled
under the old value" — for a weekly job, that could mean up to a week's delay, which does not
meet "I will adjust the time... through admin dashboard" as an immediate control. `DynamicJobScheduler`
therefore keeps a `Map<String, ScheduledFuture<?>>` and exposes `reschedule(jobKey)`, called
right after `AdminServiceImpl#updateJobSchedule` persists a change: it cancels the job's
currently-pending future (`false` — let an in-flight run finish, only cancel the *next* one) and
re-registers it, forcing an immediate re-read of the just-saved schedule.

**Rationale**:
- **One schedule representation, not two.** Every row — `INTERVAL` (the three frequent jobs) or
  `WEEKLY` (the two ML batch jobs) — is converted to a six-field Spring cron expression
  (`JobSchedule#toCronExpression`) before `JobScheduleTrigger` ever touches it, so there is exactly
  one `nextExecution` code path (`CronExpression.parse(...).next(...)`) regardless of which kind of
  row is read. `INTERVAL` schedules become step expressions (30 minutes → the cron equivalent of
  "every 30 minutes"), which is wall-clock-aligned (fires at :00/:30 past the hour) rather than
  N-minutes-after-the-previous-run the way `fixedRate` was — a small, deliberate semantic shift,
  judged acceptable since none of these five jobs are timing-critical at sub-hour precision.
- **`ManuallyTriggerableJob` (already built earlier this same session for Admin's manual "run
  now" buttons) turns out to be exactly the right seam for this too.** `DynamicJobScheduler`
  needs some way to invoke each job from outside its own module without Admin (or this scheduler,
  which — like `ManuallyTriggerableJob` itself — lives in `common.schedule` rather than any single
  job's module) depending on a concrete job class, which CLAUDE.md's "cross-module calls go
  through injected service interfaces only" forbids. The two jobs that didn't already implement it
  (`RecipientCanonicalizationJob`, `MlRetrainingJob` — their existing manual trigger routes through
  `CategorizationService` instead) now do too, purely so `DynamicJobScheduler` has one uniform way
  to call all five; their existing `CategorizationService`-routed manual-trigger endpoints are
  untouched.
- **Re-reading the schedule inside `Trigger#nextExecution` (not caching it) is what makes
  `reschedule` correct, not just `reschedule` calling it.** If the trigger cached the schedule at
  construction, cancel-and-re-register would just re-schedule the *same* stale value. Every call
  to `JobScheduleTrigger#nextExecution` does a fresh `JobScheduleRepository#findByJobKey` — cheap
  (single-row primary-key lookup), and the only way "admin's edit takes effect" is actually true
  rather than true-until-the-next-server-restart.
- **No new dependency, no new schema surface beyond one table.** `job_schedules` follows the same
  no-RLS precedent as `categories` (system-wide, no `user_id`), read through the same
  `spendwise_jobs` pool `AdminEventLog`/`AdminRepository` already use for the same reason (no
  per-request RLS session exists on a `TaskScheduler` thread). `TaskScheduler` itself is Spring
  Boot's own auto-configured bean (available because `@EnableScheduling` was already present) —
  nothing new to wire up.

**Consequence**: A new table (`V16__job_schedules.sql`, seeded with all five jobs' previous
values — with `alert_evaluation` corrected to 30 minutes, not 1), a new package
(`com.spendwise.common.schedule`: `JobSchedule`, `JobScheduleRepository`, `JobScheduleTrigger`,
`DynamicJobScheduler`, plus `JobScheduleNotFoundException`/`InvalidJobScheduleException`), two new
admin endpoints (`GET`/`PUT /admin/job-schedules`), and a new admin page (`/admin/schedules`).
Every `@Scheduled` annotation in the app is gone; `app.ml.retrain-cron`/`app.ml.canonicalization-cron`
and their `ML_RETRAIN_CRON`/`ML_CANONICALIZATION_CRON` env vars are removed as dead configuration.
Admin's existing manual "run now" triggers (`ManuallyTriggerableJob`, ADR from earlier this same
session) are unaffected — scheduling and manual triggering are two independent paths to the same
`runNow()`.

## ADR-019: Payee Matching Evolves From Feature-Rich Rules to a Merge-Queue-Trained Classifier, in Two Phases

**Status**: Accepted (Phase A implemented; Phase B deferred until labels accumulate)

**Context**: ADR-013's canonicalization clustering (fuzzy `token_sort_ratio` + hierarchical
clustering, plus the `merge_prefix_chains` truncation tier) and ADR-015's Merge Payees queue
together decide which payee spelling variants are the same entity. Against real account data, the
hand-tuned tiers systematically miss whole families of same-payee pairs that a human recognizes
instantly:

- **Name-derived UPI handles** — `AASHAYJ2` (first name + surname initial + a disambiguating
  digit) for `AASHAY MAKRAND JADHAV`, `VIHAANSHINDE18` for `VIHAAN SACHIN`. A handle interleaves
  the name with initials/digits, so it is neither a literal prefix of the spelled name nor a high
  `token_sort_ratio` match — both existing tiers score it as unrelated.
- **Sub-`min_len` truncations** — `ALP` → `ALPHA VIJAY RANE`, `YASH` → `YASH SAMEER SAWANT`,
  `VIHAA` → `VIHAAN …`. `merge_prefix_chains`/`find_prefix_ambiguities` have a `min_len=6` floor
  (short prefixes are too collision-prone to *auto-merge*), so a 3–5 char bare first name is never
  even considered, not even for review.

The user asked explicitly for a *generalizable* fix — one that learns the pattern rather than
hardcoding these specific strings — usable for model training, designed for real production data
(the ML-strategy phase's mandate), not the frozen demo CSV.

**Options considered**:

1. **Per-case patches** — lower `min_len` to 3, special-case trailing-digit handles, etc. Fixes
   these exact strings, overfits to them, and adds false-positive auto-merges for every other user.
2. **Feature-rich rule scoring now, learned pairwise classifier later** — replace the single-metric
   threshold logic with a set of *generalizable* pairwise features (multi-metric string similarity,
   any-length prefix relationship, UPI-handle stem decomposition, UPI local-part similarity), used
   two ways: **(A)** immediately, as additional *review-only* ambiguity detectors that surface the
   missed families into the Merge Payees queue without changing any auto-merge behavior; **(B)**
   later, as the feature vector of a lightweight pairwise same/different classifier trained on the
   queue's own `CONFIRMED_SAME`/`CONFIRMED_DIFFERENT` decisions, replacing the hand-tuned
   `78`/`90`/`min_len` thresholds with a learned decision boundary.
3. **Jump straight to the learned classifier** — skip Phase A, build the model now.

**Decision**: Option 2, phased.

**Rationale**:

- **The learned classifier is the long-run target, but it cannot be trained yet.** A pairwise
  matcher needs labeled `(same | different)` pairs. Those labels are produced *only* by users
  resolving the Merge Payees queue (ADR-015) — and the queue was, until now, largely empty for
  exactly the hard cases, because the tiers that feed it discarded them. **Phase A is what
  generates Phase B's training data.** Building the model before any labels exist would mean
  guessing weights with nothing to validate against — the opposite of this phase's "design for real
  data" principle.
- **Same adaptive-supervised precedent the rest of SpendWise already uses.** Phase B is the exact
  `features + user-outcome → periodic batch retrain` shape of categorization (`ml_corrections`) and
  recurring detection (`RecurringCorrection`, ADR-003). The queue's `recipient_merge_suggestions`
  rows already store the pair, its features' inputs (`anchor_name`/`candidate_name`/UPI ids), and
  the human outcome — so the label source needs no new table, just a retrain job reading resolved
  rows, pooled across all users (a payee-name pattern is user-agnostic; `RAJ2`→`RAJESH KUMAR`
  generalizes from another user's `AASHAYJ2`→`AASHAY …`).
- **No contradiction with ADR-014.** ADR-014 said a canonicalization *correction* is a permanent
  name **pin**, not a retrainable label, because there is no model behind the *name assignment*.
  That still holds. Phase B trains a different decision — *whether two identities are the same
  entity* — whose labels are the queue's same/different resolutions, not the name pins. The pin and
  the match verdict are distinct signals; ADR-019 adds a model to the second, leaves the first as-is.
- **Precision-first, human-in-the-loop for both phases.** A wrong auto-merge silently fuses two real
  people's money — far worse than a missed merge. Phase A's new detectors therefore *only surface
  review candidates*; they never touch `canonical_names`. Phase B keeps a conservative auto-merge
  band (`P(same) > high` auto-merges, `[low, high]` routes to review, `< low` stays apart), so
  uncertainty always reaches a human.
- **Clean seam, no tier overlap.** Phase A's short-prefix detector is bounded to lengths 3–5 —
  exactly the sub-6 gap `merge_prefix_chains` cannot touch — so the two never double-surface or
  disagree. The handle detector requires an anchoring first token of length ≥ 5, so a coincidental
  short collision doesn't fire it.
- **UPI local-part similarity is deferred to Phase B, not shipped as a Phase-A rule.** It is a
  genuine signal (same handle, different bank PSP → tier-1's exact-full-UPI match never fires), but
  as a standalone hard threshold it blows up on real data: generic/gateway handles (`family`,
  `paytmqr…`) fan a single local part across dozens of unrelated payees — the same shared-code
  problem `group_by_upi_id`'s `max_distinct_names` guard already documents. It belongs in Phase B,
  where "how many names share this handle" is one weighted feature among many, not a queue-flooding
  rule. Validated on the labeled set: with UPI-similarity dropped, the two name detectors surface a
  healthy 55 candidates (44 handle + 11 short-prefix) across 911 identities, largest group 4 — vs.
  a near-fully-connected component when UPI-similarity ran unguarded.

- **Architecture: entirely within the existing FastAPI gateway path — no invariant change for
  Phase A.** Both new detectors live inside `training/merchant_normalizer.py`'s existing
  `canonicalize_with_ambiguities` pass and ride the *same* `POST /normalize-recipients` response's
  `ambiguous_groups` field (ADR-015), called only by `RecipientCanonicalizationJob` via the
  Categorization gateway (ADR-012/013). No new endpoint, no new job, no new network round trip, no
  new module dependency. Phase B adds only a retrain step + model artifact alongside the
  categorization/recurring ones (same free-tier artifact pattern) — still behind the Categorization
  gateway, so it too needs no new module-dependency exception when it lands.

**Consequence** (Phase A, now): two new pure functions in `merchant_normalizer.py`
(`find_upi_handle_pairs`, `find_short_prefix_pairs`) plus a `_squash` helper, wired into
`canonicalize_with_ambiguities` as additional pre-oriented `extra_pairs` (filtered so a pair tier-3
already auto-merged is never re-surfaced) fed to `_build_ambiguous_groups`. Two new `reason` values
on the queue — `upi_handle_variant`, `short_prefix` — carried end to end as free-form strings
(FastAPI `AmbiguousCandidate.reason`, Java `MlAmbiguousCandidate`, the nullable-free
`recipient_merge_suggestions.reason` column, the frontend `MergeCandidate.reason` which does not
switch on it), so no schema, DTO, or migration change is required and an older ML response still
deserializes. No auto-merge behavior changes; `canonical_names` output is byte-for-byte unchanged.
**Phase B (deferred)**: a `payee_match` classifier trained from resolved `recipient_merge_suggestions`
rows on the existing weekly cadence, cold-starting behind the Phase-A rules until enough labeled
pairs accumulate — to be specified in its own follow-up once the queue has produced real labels.
