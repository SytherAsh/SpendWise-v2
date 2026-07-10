# SpendWise — System Architecture Diagram

Canonical single-page visual reference. Reflects all approved documentation. Update this diagram whenever an architectural decision is changed and approved.

> Render in VS Code with the Mermaid Preview extension, or paste into [mermaid.live](https://mermaid.live).

```mermaid
flowchart TB

  %% ── ANDROID APP ──────────────────────────────────────────────────────────
  subgraph ANDROID["Android App  (Kotlin)"]
    direction TB
    A_SMS["BroadcastReceiver — real-time SMS\nContentResolver — first-launch backfill"]
    A_PARSE["Parser — keyword filter + regex\nSBI  ·  Paytm  ·  GPay"]
    A_ROOM["Room DB — local sync queue"]
    A_SYNC["Sync — batch HTTP · retry on reconnect"]
    A_UI["UI — dashboard · transactions · chat · settings"]
    A_SMS --> A_PARSE --> A_ROOM --> A_SYNC
  end

  %% ── WEB DASHBOARD ────────────────────────────────────────────────────────
  subgraph WEB["Web Dashboard  (Next.js · Vercel)"]
    W["React UI\nclient-side state cache on backend outage"]
  end

  %% ── SPRING BOOT MONOLITH ─────────────────────────────────────────────────
  subgraph SB["Spring Boot  (Java 21 · Modular Monolith)"]
    direction TB

    subgraph AU["Auth & User"]
      direction LR
      AUTH["Auth\nOTP · Google OAuth · JWT"]
      USERMOD["User\nprofile · preferences · onboarding"]
    end

    subgraph ING["Ingestion"]
      INGEST["Ingest\ndedup · persist · bank-stmt parse"]
    end

    subgraph CORE["Core"]
      direction LR
      TXN["Transaction\nstorage · retrieval · filter · pagination"]
      CAT["Categorization\nML client · correction handling"]
      BUDGET["Budget\nCRUD · progress · business rules"]
    end

    subgraph CONSUMER["Consumer"]
      direction LR
      ALERTS["Alerts\nthreshold eval · dispatch"]
      RECS["Recommendations\nLLM one-liners · dismissal"]
      CHAT["Chatbot\nLLM · session management"]
    end

    subgraph READLAYER["Read Layer"]
      ANALYTICS["Analytics\nread-only · chart data · PDF/CSV export"]
    end

    subgraph ADMINGRP["Admin"]
      ADMIN["Admin\nmonitoring · cross-user stats · retrain trigger"]
    end

    subgraph BGJOBS["Background Jobs  (Spring @Scheduled)"]
      direction LR
      J1["Alert Evaluator\nAlerts · every 30 min"]
      J2["Rec Generator\nRecommendations · every 6 h · idempotent\nchecks txn/budget timestamps since last run"]
      J3["ML Retraining\nCategorization · weekly"]
      J4["Cat Retry\nCategorization · every 30 min"]
    end
  end

  %% ── EXTERNAL SERVICES ────────────────────────────────────────────────────
  subgraph EXT["External Services"]
    direction LR
    DB[("Supabase  PostgreSQL\nRLS on all user_id tables")]
    FBAUTH["Firebase Auth\nOTP · Google OAuth"]
    MLSVC["FastAPI ML  (Python)\n/predict · /retrain · /evaluate\ninternal-only · X-Internal-Key header"]
    FCMSVC["FCM\npush notifications"]
    SMTPSVC["SMTP\nemail notifications"]
    LLMSVC["LLM Provider\nvendor-abstracted · TBD"]
  end

  %% ── CONNECTIONS ──────────────────────────────────────────────────────────

  %% Clients → API
  A_SYNC  -->|"POST /api/v1/ingest  |  JWT + Device API Key"| INGEST
  A_UI    -->|"HTTPS REST  |  JWT"| SB
  W       -->|"HTTPS REST  |  JWT"| SB

  %% Auth
  AUTH   <-->|"validate credential"| FBAUTH

  %% Ingest pipeline
  INGEST         -->|"persist"| TXN
  INGEST         -->|"trigger predict"| CAT
  USERMOD        -->|"bank stmt handoff"| INGEST

  %% Core
  CAT            -->|"update category"| TXN
  BUDGET         -->|"read spend (read-only)"| TXN

  %% Consumer ← Core
  ALERTS         -->|"read spend"| TXN
  ALERTS         -->|"read limits"| BUDGET
  RECS           -->|"read aggregations"| ANALYTICS
  CHAT           -->|"read history"| TXN
  CHAT           -->|"read summaries"| ANALYTICS

  %% Analytics (read-only)
  ANALYTICS      -. read-only .-> TXN
  ANALYTICS      -. read-only .-> BUDGET
  ANALYTICS      -. read-only .-> ALERTS

  %% Admin
  ADMIN          -->|"retrain + evaluate  (via service interface)"| CAT

  %% All modules → DB
  SB             -->|"all reads / writes"| DB

  %% FastAPI (Categorization only — CLAUDE.md invariant)
  CAT            -->|"HTTP · X-Internal-Key"| MLSVC

  %% Alerts dispatch
  ALERTS         --> FCMSVC
  ALERTS         --> SMTPSVC

  %% LLM calls
  RECS           --> LLMSVC
  CHAT           --> LLMSVC
```

---

## Legend

| Style | Meaning |
| --- | --- |
| Solid arrow `-->` | Direct call via injected service interface |
| Dashed arrow `-.->` | Read-only access (Analytics module) |
| `<-->` | Bidirectional — credential validation |
| Background Jobs box | Scheduled job owned by the named module; runs module logic on a timer |

---

## Module Dependency Rules (summary)

| Module | May call |
| --- | --- |
| Ingest | Transaction, Categorization |
| Categorization | Transaction |
| Alerts | Transaction, Budget |
| Recommendations | Analytics |
| Chatbot | Transaction, Analytics |
| Analytics | All modules — read-only only |
| Admin | All modules — read; Categorization — retrain + evaluate |
| User | Ingest — bank statement handoff only |
| Auth | (no outbound calls) |
| Budget | Transaction (read-only) |

No circular dependencies. No module calls back up the ingestion chain (Ingest → Transaction → Analytics).

---

## Security Invariants

- `/api/v1/ingest` requires **both** a user JWT and a Device API Key — reject if either is missing
- FastAPI ML is **internal-only** — called exclusively by the Categorization module via `X-Internal-Key`; no other module calls FastAPI
- Admin endpoints use `ADMIN_JWT_SECRET` — a completely separate secret from `JWT_SECRET`; regular user tokens are rejected at the route level
- Supabase RLS policies enforce user data isolation at the database level for every table with a `user_id` column
- `sms_raw_text` is **never** returned in any user-facing API response — enforced via response DTOs
- Raw SMS content never travels over the network — on-device parsing only; only structured fields reach `/ingest`
