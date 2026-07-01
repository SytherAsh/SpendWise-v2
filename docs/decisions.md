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
