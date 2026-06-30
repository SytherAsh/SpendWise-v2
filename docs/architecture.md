# Architecture

## Overview

SpendWise is built as a **modular monolith** for the MVP. The codebase is organized into 11 well-bounded modules. Each module exposes a service interface and communicates with other modules via direct calls — no internal event bus or message queues. Module boundaries are designed to allow future extraction into independent microservices if traffic demands it.

## System Components

```
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

## Spring Boot Modules

| Module | Package | Responsibility |
|---|---|---|
| **Auth** | `com.spendwise.auth` | Phone OTP, Google OAuth, JWT issuance and refresh |
| **User** | `com.spendwise.user` | User profiles, preferences, onboarding state, alert settings |
| **Ingest** | `com.spendwise.ingest` | Receives parsed transactions from Android app, deduplication |
| **Transaction Management** | `com.spendwise.transaction` | Storage, retrieval, filtering, pagination, validation, querying |
| **Categorization** | `com.spendwise.categorization` | Calls FastAPI ML service, stores predictions, handles corrections |
| **Budget** | `com.spendwise.budget` | Budget CRUD, progress calculation, business rules |
| **Alerts** | `com.spendwise.alerts` | Threshold evaluation, notification dispatch (push + email) |
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
|---|---|---|
| **Ingest** | Transaction (persist), Categorization (trigger) | Any other module |
| **Categorization** | Transaction (update category) | Any module except Transaction |
| **Budget** | *(no outbound calls — data is read from it)* | Any other module |
| **Alerts** | Transaction (read spend), Budget (read limits) | Recommendations, Chatbot, Ingest |
| **Recommendations** | Analytics (read aggregations) | Alerts, Chatbot, Ingest, Categorization |
| **Chatbot** | Transaction (read history), Analytics (read summaries) | Any module that writes data |
| **Analytics** | Reads from all modules *(read-only)* | *(must not call any write methods on any module)* |
| **Admin** | Reads from all; triggers Categorization (retrain) | — |
| **Auth / User** | *(no outbound calls — serve own domain only)* | Any other module |

Direction rule: data flows inward through the stack (Ingest → Transaction → Analytics). No module calls back up the ingestion chain.

## FastAPI ML Service

Separate Python process, called by the Spring Boot Categorization module via internal HTTP.

| Endpoint | Purpose |
|---|---|
| `POST /predict` | Accepts transaction features, returns predicted category + confidence score |
| `POST /retrain` | Triggers a batch retraining cycle using `ml_corrections` data |
| `GET /evaluate` | Runs accuracy evaluation against labeled dataset, returns metrics |

## Android App Modules

| Module | Package | Responsibility |
|---|---|---|
| **SMS** | `com.spendwise.sms` | BroadcastReceiver for incoming SMS, foreground service lifecycle |
| **Parser** | `com.spendwise.parser` | Regex rules (SBI, Paytm, GPay), keyword detector, field extractor |
| **Sync** | `com.spendwise.sync` | Local Room DB queue, batch HTTP upload, retry on reconnect |
| **UI** | `com.spendwise.ui` | Android screens: dashboard, transactions, chatbot, settings |
| **Storage** | `com.spendwise.storage` | Local Room database for offline transaction queue |

## SMS Ingestion Flow

```
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

## Background Jobs (inside Spring Boot process)

| Job | Schedule | What it does |
|---|---|---|
| Alert evaluator | Every 30 minutes | Checks mid-month budget thresholds and category overspend |
| Recommendation generator | Threshold-triggered | Calls LLM when a spending threshold is crossed |
| ML retraining | Weekly (configurable) | Sends `ml_corrections` data to FastAPI /retrain |
| Categorization retry | Every 30 minutes | Re-triggers ML categorization for transactions ingested but not yet categorized (e.g., FastAPI unavailable during ingest) |
