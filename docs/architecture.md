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
| **Ingest** | Transaction (persist), Categorization (trigger) | Any other module |
| **Categorization** | Transaction (update category) | Any module except Transaction |
| **Budget** | *(no outbound calls — data is read from it)* | Any other module |
| **Alerts** | Transaction (read spend; read EMIs for recurring-payment detection), Budget (read limits) | Recommendations, Chatbot, Ingest |
| **Recommendations** | Analytics (read aggregations) | Alerts, Chatbot, Ingest, Categorization |
| **Chatbot** | Transaction (read history), Analytics (read summaries) | Any module that writes data |
| **Analytics** | Reads from all modules *(read-only)* | *(must not call any write methods on any module)* |
| **Admin** | Reads from all; triggers Categorization (retrain + evaluate) | — |
| **Auth / User** | User → Ingest (bank statement handoff only) | Any module except Ingest |

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
| Alert evaluator | Alerts | Every 30 minutes | Checks mid-month budget thresholds and category overspend for all users |
| Recommendation generator | Recommendations | Every 6 hours | Reads spending aggregations from Analytics; generates recommendations where a threshold has been crossed since the last generation for that user and category. Idempotent — suppresses duplicates by checking `generated_at` on the most recent record per user per category. May also be triggered after significant user events (bank statement import, budget creation). |
| ML retraining | Categorization | Weekly (configurable) | Sends `ml_corrections` data to FastAPI /retrain |
| Categorization retry | Categorization | Every 30 minutes | Re-triggers ML categorization for transactions ingested but not yet categorized (e.g., FastAPI unavailable during ingest) |
