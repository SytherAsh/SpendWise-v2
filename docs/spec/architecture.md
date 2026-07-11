# Architecture

## Overview

SpendWise is built as a **modular monolith** for the MVP. The codebase is organized into 11 well-bounded modules. Each module exposes a service interface and communicates with other modules via direct calls — no internal event bus or message queues. Module boundaries are designed to allow future extraction into independent microservices if traffic demands it.

## System Components

```text
┌─────────────────┐     SMS alerts      ┌──────────────────────┐
│   Android App   │──── (on-device) ───►│  SMS Parser (Kotlin) │
│   (Kotlin)      │                     │  Regex + keyword      │
│                 │                     │  detection            │
│  Foreground     │◄─── structured ─────┤                      │
│  Service        │     fields only     └──────────────────────┘
│                 │
│  Local queue    │──── HTTPS POST ────►┌──────────────────────┐
│  (batch sync)   │    /api/v1/ingest   │   Spring Boot API    │
└─────────────────┘                     │  (Java 21 Monolith)  │
                                        │                      │
┌─────────────────┐                     │  ┌────────────────┐  │
│  Next.js Web    │──── HTTPS REST ────►│  │ 11 Modules     │  │
│  Dashboard      │◄─── responses ─────│  │ (see below)    │  │
│  (Vercel)       │                     │  └────────────────┘  │
└─────────────────┘                     │          │           │
                                        │          ▼           │
                                        │  ┌────────────────┐  │
                                        │  │ Background Jobs│  │
                                        │  │ (cron/threads) │  │
                                        │  └────────────────┘  │
                                        └──────────┬───────────┘
                                                   │
                              ┌────────────────────┼─────────────────┐
                              ▼                    ▼                 ▼
                    ┌─────────────────┐  ┌──────────────┐  ┌──────────────┐
                    │   Supabase      │  │  FastAPI ML  │  │  Firebase    │
                    │  (PostgreSQL)   │  │  (Python)    │  │  Auth        │
                    └─────────────────┘  └──────────────┘  └──────────────┘
```

> **Web dashboard offline behavior:** The Next.js frontend uses client-side state (browser storage / in-memory React state) to serve the last-fetched data when the backend is unreachable. No server-side cache or cache server is required or deployed.

<!-- -->

> **Web dashboard structure & IA (UI/UX redesign, updated 2026-07-09 — Profile/Settings split):** The app is a client-rendered Next.js App Router SPA calling the Spring Boot REST API directly (no BFF/proxy layer). Route groups: `(auth)/login` (public) and `(app)/*` behind a client-side `AuthGuard`, plus a separate `/admin` tree. The four primary top-nav destinations are **Dashboard**, **Transactions**, **Analytics**, **Planning** (`lib/shell.tsx` `PRIMARY_NAV`); **Profile** and **Settings** are reached from the avatar menu instead of the top nav, but are still real routes tracked in `NAV_ITEMS` (findable via the command palette). **Planning** consolidates the former Budget + EMIs/Subscriptions screens into two tabs. **Profile** (`/profile`) holds personal identity and counterparty data: **Personal Info** (phone, email, member-since) and **Contacts** (family/friend/self/settlement tagging for Transfer transactions — ADR-010). **Settings** (`/settings`) is app-wide configuration: **Preferences**, **Appearance** (theme), **Security** and **Privacy & Data** (placeholders, not wired to any endpoint), and **Export**. The former standalone `/budget`, `/emis`, and `/export` routes were removed once these consolidations landed. The account menu (avatar, top-right) also has an **Upload statement** entry — UI only, since `POST /users/me/bank-statement` has no server-side implementation yet (see `implementation/tracking/STATUS.md`). Shared frontend infrastructure (kept stable across the visual redesign): `lib/apiClient.ts` (auth + transparent 401→refresh retry), SWR via `lib/useApi.ts` (exposes `isStale`), design tokens + a light/dark theme seam in `styles/globals.css` driven by a `data-theme` attribute via **`next-themes`** (Light/Dark/System, a client-only preference never sent to the backend), category color/icon helpers in `lib/categories.ts`, `lib/date-range.tsx`'s shared date-range context (`DateRangeProvider`, backing both the topbar `DateRangePicker` and the `MonthStepper` control used on Transactions/Analytics), and Recharts theming in `lib/chart-theme.ts`.

## Spring Boot Modules

| Module | Package | Responsibility |
| --- | --- | --- |
| **Auth** | `com.spendwise.auth` | Phone OTP, Google OAuth, JWT issuance and refresh |
| **User** | `com.spendwise.user` | User profiles, preferences, onboarding state, alert settings |
| **Ingest** | `com.spendwise.ingest` | Receives parsed transactions from Android app, deduplication |
| **Transaction** | `com.spendwise.transaction` | Storage, retrieval, filtering, pagination, validation, querying; EMI tracking (lifecycle, detection status, manual entry, deactivation); category listing (`GET /categories`); category correction writes (updates `transaction_categories` and records to `ml_corrections` — no cross-module call required) |
| **Categorization** | `com.spendwise.categorization` | Calls FastAPI ML service, stores ML predictions in `transaction_categories`; reads `ml_corrections` as training data for batch retraining |
| **Budget** | `com.spendwise.budget` | Budget CRUD, progress calculation, business rules |
| **Alerts** | `com.spendwise.alerts` | Threshold evaluation, notification dispatch (push via FCM, email via SMTP) |
| **Recommendations** | `com.spendwise.recommendations` | LLM-generated one-liners, priority assignment, dismissal |
| **Chatbot** | `com.spendwise.chatbot` | LLM integration, session management, data-aware context injection |
| **Analytics** | `com.spendwise.analytics` | Read-only aggregations, chart data, PDF/CSV export |
| **Admin** | `com.spendwise.admin` | System monitoring, parser health, cross-user analytics, model retrain trigger |

### Module communication rules

- Modules call each other via injected service interfaces (not direct class dependencies)
- No circular dependencies between modules
- Analytics module has read-only access to data from all other modules — it contains no business logic
- All modules share a single Supabase PostgreSQL database (one schema)

### Allowed module dependencies

| Module | May call | Must not call |
| --- | --- | --- |
| **Ingest** | Transaction (persist), Categorization (trigger), User (device API key validation only), Auth (reuses the user-JWT filter/service for session validation only) | Any other module |
| **Categorization** | Transaction (update category) | Any module except Transaction |
| **Budget** | Transaction (read-only — spend data for progress/suggestions) | Any other module |
| **Alerts** | Transaction (read spend; read EMIs for recurring-payment detection; **write** — create an EMI from a confirmed recurring-payment alert, E6-S2-T2), Budget (read limits), User (read-only: `email`, `fcm_token`, `alert_channels` preference — dispatch target lookup), Categorization (predict-only — recurring-payment confidence/cadence for a candidate group; never triggers a retrain, never writes through it) | Recommendations, Chatbot, Ingest |
| **Recommendations** | Analytics (read aggregations) | Alerts, Chatbot, Ingest, Categorization |
| **Chatbot** | Transaction (read history), Analytics (read summaries) | Any module that writes data |
| **Analytics** | Reads from all modules *(read-only)* | *(must not call any write methods on any module)* |
| **Admin** | Reads from all; triggers Categorization (retrain + evaluate) | — |
| **Auth / User** | User → Ingest (bank statement handoff only) | Any module except Ingest |

> **Ingest's User/Auth calls (added during Epic 3 implementation):** `/api/v1/ingest/transactions`
> requires both a user JWT and a device API key (CLAUDE.md security invariants), so the Ingest
> module's dual-auth guard necessarily reuses the Auth module's `UserJwtAuthFilter`/`UserJwtService`
> (rather than reimplementing JWT validation) and calls the User module's `DeviceApiKeyService`
> through its injected interface (per CLAUDE.md, cross-module calls go through injected service
> interfaces only) — `DeviceApiKeyService`'s own Javadoc anticipated this consumer from E1-S4-T1.
> Both are read-only/validation-only calls, not writes, and don't create a cycle: User and Auth
> never call back into Ingest. Approved by project owner 2026-07-02 as a deviation from this
> document's original dependency table, analogous to docs/spec/database.md's V6 RLS addendum.

> **Alerts' User dependency (added during Epic 5 implementation):** the module table's original
> "May call" list for Alerts (Transaction, Budget) had no path to the contact info notification
> dispatch actually needs — `users.email` for SMTP, `user_preferences.fcm_token`/`alert_channels`
> for FCM — even though this same document's module table already assigns Alerts "notification
> dispatch (push via FCM, email via SMTP)" as a responsibility. This was a gap in the original
> table, not a deliberate restriction (`docs/spec/api.md`/`docs/spec/requirements.md` never suggested Alerts
> should resolve dispatch targets any other way). Read-only, single-direction (User never calls
> Alerts), no cycle. Approved by project owner 2026-07-03 as a deviation from this document's
> original dependency table, same pattern as the two addenda above.

> **Budget's demo-only `DemoUserRegistry` dependency (added during demo feature deployment,
> 2026-07-10):** the demo account's transactions come from a static CSV that is never re-uploaded
> (see `docs/demo/demo-feature-complete.md`), so `BudgetServiceImpl`'s `resolveMonth(userId)` pins
> the demo user's notion of "current month" to a fixed `demo.frozen-month` config value instead of
> `YearMonth.now()`, so budget progress never reads as "₹0 spent" once real time drifts past the
> CSV's last covered month. Answering "is this the demo user" requires *some* signal from outside
> Budget's own module, but a full cross-module dependency on User's or Ingest's service interfaces
> would be disproportionate to one boolean check. `com.spendwise.common.demo.DemoUserRegistry` is
> the deliberately narrow channel for that signal instead: a `@Component` holder bean with a single
> `volatile UUID` field and `register`/`isDemoUser` methods, carrying no business logic and
> referencing no other module's domain types. `DemoDataSeeder` (Ingest) registers the demo user's ID
> on every application startup; `BudgetServiceImpl` reads it. This is a one-way, read-only signal
> (Budget never calls back into Ingest), returns `YearMonth.now()` unconditionally for every
> non-demo user, and does not fit the "May call" / "Must must not call" table above cleanly because
> it isn't a service-interface call at all — it's a shared, module-agnostic marker bean, structurally
> similar to a feature flag. Treat it as a deliberate, narrow exception to "cross-module calls go
> through injected service interfaces only," not a violation to flag — see
> `DemoUserRegistry`'s own class-level Javadoc for the same reasoning in code. Unlike the two
> addenda above, this one has not been separately confirmed with the project owner as a dependency-
> table amendment; flag for explicit sign-off if the demo feature's scope changes.

> **Alerts' Categorization dependency (added during the ML strategy phase, 2026-07-11):**
> `AlertEvaluatorJob` calls `CategorizationService#predictRecurring` for every candidate group
> `RecurringPaymentDetector` proposes, replacing E6-S1-T1's exact-match rule as the production
> gate for `recurring_payment` alerts — see ADR-012 in `docs/spec/decisions.md` for the full
> reasoning (why this widens Categorization's role instead of granting Alerts its own FastAPI
> access, or an exception to "FastAPI is called only from the Categorization module").
> Predict-only: Alerts never calls `triggerRetrain`, and `CategorizationService#predictRecurring`
> itself never writes to the database — it's a pure proxy to FastAPI `/predict-recurring`. No
> cycle: Categorization never calls back into Alerts.

Direction rule: data flows inward through the stack (Ingest → Transaction → Analytics). No module calls back up the ingestion chain.

> Admin calls Categorization's service interface for both retraining and accuracy evaluation — FastAPI is never called directly from Admin (CLAUDE.md invariant: only the Categorization module calls FastAPI).

## FastAPI ML Service

Separate Python process, called by the Spring Boot Categorization module via internal HTTP.

| Endpoint | Purpose |
| --- | --- |
| `POST /predict` | Accepts transaction features, returns predicted category + confidence score |
| `POST /retrain` | Triggers a batch retraining cycle using `ml_corrections` data |
| `GET /evaluate` | Runs accuracy evaluation against labeled dataset, returns metrics |

> **Internal access only.** The FastAPI service must not be publicly reachable. Enforce via same-platform internal networking where available, or a shared secret header (`X-Internal-Key`) validated on every request, with the secret injected via `ML_INTERNAL_KEY`. Spring Boot sets this header on all outbound ML calls.

## Android App Modules

| Module | Package | Responsibility |
| --- | --- | --- |
| **SMS** | `com.spendwise.sms` | BroadcastReceiver for incoming SMS, foreground service lifecycle |
| **Parser** | `com.spendwise.parser` | Regex rules (SBI, Paytm, GPay), keyword detector, field extractor |
| **Sync** | `com.spendwise.sync` | Local Room DB queue, batch HTTP upload, retry on reconnect |
| **UI** | `com.spendwise.ui` | Android screens: dashboard, transactions, chatbot, settings |
| **Storage** | `com.spendwise.storage` | Local Room database for offline transaction queue |

## SMS Ingestion Flow

```text
SMS arrives on device
    │
    ▼
BroadcastReceiver / SmsObserver
    │
    ▼
Financial keyword filter (on-device)
    │ non-financial: discard immediately
    │ financial: proceed
    ▼
Regex parser (SBI / Paytm / GPay rules)
    │ unknown sender: keyword-based field extraction
    ▼
Deduplication check
    │ duplicate: discard
    │ new: proceed
    ▼
Store in local Room DB queue
    │
    ▼ (every ~15–30 min or when queue grows large)
Batch HTTP POST to /api/v1/ingest
    │
    ▼
Spring Boot Ingest module validates + persists
    │
    ▼
Categorization module calls FastAPI /predict
    │
    ▼
Transaction stored with category in Supabase
    │
    ▼
Alert evaluation runs (background job)
    │
    ▼
Dashboard reflects updated data
```

> **Deduplication is two-layered:** on-device dedup (before Room DB storage) filters already-queued transactions from the local queue. Server-side dedup is enforced by the Ingest module (returns 409 for a duplicate `transaction_id` per user) and backed by a DB-level unique index. The Android Sync module treats a 409 as a successful acknowledgment and removes the item from the queue.

## First-Launch SMS Inbox Backfill

Performed once at the end of onboarding, after the user grants consent and `READ_SMS` permission. This is a one-time bulk read of the existing SMS inbox — distinct from the ongoing foreground service, which only captures new incoming messages.

```text
Onboarding consent accepted + READ_SMS permission granted
    │
    ▼
Android ContentResolver reads existing SMS inbox
(Telephony.Sms.CONTENT_URI — iterates stored messages)
    │
    ▼
Financial keyword filter (same rules as real-time flow)
    │ non-financial: skip
    │ financial: proceed
    ▼
Regex parser (same rules as real-time flow)
    │
    ▼
Deduplication against local Room DB
    │ duplicate: skip
    │ new: enqueue
    ▼
Store in local Room DB sync queue
    │
    ▼
First sync triggered immediately on completion
Batch HTTP POST to /api/v1/ingest (same endpoint as real-time)
```

The foreground SMS monitoring service starts after the backfill completes. Both paths converge at the Room DB sync queue and use the same `/ingest` endpoint.

## Bank Statement Upload Flow

An optional step during or after onboarding. The Android app or web dashboard uploads the file; all parsing happens server-side.

```text
User selects bank statement PDF (Android or web dashboard)
    │
    ▼
POST /users/me/bank-statement → User module receives PDF
    │
    ▼
User module hands off to Ingest module
(User → Ingest is permitted for this specific operation — see module dependencies)
    │
    ▼
Ingest module parses transactions from PDF (SBI format, MVP)
PDF discarded immediately — not stored permanently
    │
    ▼
Transactions stored with source = 'bank_statement'
    │
    ▼
Categorization triggered per transaction (same as SMS path)
    │
    ▼
Dashboard and budget suggestions reflect imported data
```

## Background Jobs (inside Spring Boot process)

| Job | Owner | Schedule | What it does |
| --- | --- | --- | --- |
| Alert evaluator | Alerts | Every 30 minutes | Checks mid-month budget thresholds and category overspend for all users; also runs recurring-payment detection — proposes loosened candidates (`RecurringPaymentDetector`), then gates each on `CategorizationService#predictRecurring` (V11, ADR-012) — reuses this same cadence since detection isn't time-critical (`docs/spec/decisions.md` ADR-011's scheduled-over-event-driven reasoning applies here too) |
| Recommendation generator | Recommendations | Every 6 hours | Reads spending aggregations from Analytics; generates recommendations where a threshold has been crossed since the last generation for that user and category, determined by comparing transaction and budget timestamps against the time of its own last run. Idempotent — suppresses duplicates by checking `generated_at` on the most recent record per user per category. |
| ML retraining | Categorization | Weekly (configurable) | Sends `ml_corrections` data to FastAPI /retrain |
| Categorization retry | Categorization | Every 30 minutes | Re-triggers ML categorization for transactions ingested but not yet categorized (e.g., FastAPI unavailable during ingest) |

## Counterparty Metadata Enrichment (built 2026-07-09, UI/UX polish phase)

**Built, per the sketch below.** Originally recorded here as a not-yet-built design
sketch — see `docs/roadmap.md` Phase 9 and ADR-010 in `docs/spec/decisions.md` for the
product framing, rationale, and build status. The sketch is left in place because the
shipped implementation follows it closely: a `contacts` table (`docs/spec/database.md`)
owned by the User module, served by `/api/v1/contacts` (`docs/spec/api.md`), matched against
`transactions.recipient_name`/`upi_id` **client-side by the frontend** rather than by a
server-side join — the Analytics module's read-only join described just below remains
unbuilt and out of scope for this slice (Transactions-page-only for v1).

**Problem:** the 12 ML transaction categories (`docs/spec/requirements.md`) intentionally
don't distinguish *who* a Transfer went to or came from — Transfers is one ML class
covering family, friends, self-transfers, and settlements alike. During ML training-data
labeling (`ml/labeling/`), a Payee Knowledge Base was built that already captures this:
which recipients are friends, family, self-transfer accounts, merchants, employers, or
subscriptions (`ml/labeling/knowledge_base/merchant_rules.csv`).

**Proposed flow**, added as a step *after* — never instead of — ML categorization:

```text
Ingest → Transaction stored → Categorization calls FastAPI /predict → category_id stored
                                                                            │
                                                                            ▼
                                          (NEW, post-MVP) Analytics/enrichment step:
                                          look up recipient_name/upi_id against a
                                          Payee Knowledge Base table → attach
                                          counterparty_type metadata (non-authoritative,
                                          display-only)
```

Sketch of the shape this would take, **not a committed schema**:
- A `counterparty_metadata` table (or a `counterparty_type` column on a per-user contacts
  table), separate from `categories`/`transaction_categories` — never a new ML class, and
  never a new `categories` row.
- Populated by porting `ml/labeling/knowledge_base/counterparty_knowledge.csv` (a
  first-pass extraction of this reasoning, done during Epic 4 prep — see its README)
  into a queryable table, extended per-user over time (mirrors how `ml_corrections`
  grows the ML model, but this loop never touches the model). That CSV is raw material
  only — re-verify its `medium`-confidence rows before treating it as ground truth.
- Read-only consumer: the Analytics module would join on this table the same way it
  joins on `transaction_categories` today — Analytics remains strictly read-only per its
  existing architectural invariant.
- UI use case: group/filter the Transfers category by counterparty type ("Family",
  "Friends", "Self", "Settlements") without inflating the ML label set.

**Why this is deliberately not in Epic 4 or Epic 7's current scope:** see ADR-010.
