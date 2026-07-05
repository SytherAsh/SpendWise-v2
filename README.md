# SpendWise

A centralized financial tracking application for Indian UPI/payment app users. SpendWise aggregates transaction data from Paytm, GPay, PhonePe, and SBI into a single dashboard — solving the problem of fragmented spending history across multiple payment platforms.

## What it does

- Reads SMS transaction alerts from your phone (SBI, Paytm, GPay) and syncs them to a unified dashboard
- Auto-categorizes every transaction using an ML model trained on real spending data
- Tracks budgets per category with visual progress bars and threshold alerts
- Generates personalized, data-driven savings recommendations via LLM
- Provides an interactive financial analytics dashboard with week/month/year comparisons
- Includes a context-aware AI chatbot that knows your full transaction history

## Tech Stack

| Layer | Technology |
|---|---|
| Android App | Kotlin (native) |
| Web Dashboard | Next.js (React) — hosted on Vercel |
| Backend API | Spring Boot (Java 21) — modular monolith |
| ML Service | FastAPI (Python) |
| Database | PostgreSQL via Supabase |
| Auth | Firebase Authentication (OTP + Google) |
| Error Tracking | Sentry |
| Uptime Monitoring | UptimeRobot |

## Repository Structure

```
├── backend/        Spring Boot REST API (modular monolith)
├── frontend/       Next.js web dashboard
├── android/        Kotlin Android app (SMS ingestion + mobile UI)
├── ml/             FastAPI ML categorization service
├── infra/          Infrastructure and hosting configuration
├── scripts/        Utility scripts (data import, DB seed)
├── tests/          End-to-end golden path tests
└── docs/           Full project documentation
```

## Local Development — Running the Stack

The app is **four services on four ports**, started in **four terminals**. Only Postgres runs
in Docker; the other three run as plain processes. Ports are fixed — the frontend expects the
backend on `8080`, the backend expects Postgres on `5433` and the ML service on `8000`.

| # | Service | Port | Runtime | Start in | Command | Needs venv? |
|---|---|---|---|---|---|---|
| 1 | **Postgres** (DB) | `5433` | Docker | `backend/` | `docker compose up -d` | No |
| 2 | **Backend** (Spring Boot) | `8080` | Java process | `backend/` | `.\run-local.ps1` (loads `.env`, then bootRun) | No |
| 3 | **ML service** (FastAPI) | `8000` | Python process | `ml/` | `uvicorn api.main:app --reload --port 8000` | **Yes** |
| 4 | **Frontend** (Next.js) | `3000` | Node process | `frontend/` | `npm run dev` | No |

> **venv:** only the ML service (Python) needs it. Activate before running uvicorn:
> `.venv\Scripts\activate` (PowerShell) → your prompt shows `(.venv)`. Backend (Java/Gradle)
> and frontend (Node/npm) never touch Python.

### Start everything (cold start)

Open four terminals. Start them in order — the backend runs Flyway migrations against Postgres
on boot, so Postgres must be up first.

```bash
# Terminal 1 — Postgres (Docker). Leave running; safe to keep up between sessions.
cd backend
docker compose up -d

# Terminal 2 — Backend. Wait for "Started SpendwiseApplication" before using the app.
cd backend
.\run-local.ps1             # loads backend/.env, then runs bootRun (Flyway auto-migrates on boot)
# Plain `./gradlew bootRun` also boots, but WITHOUT backend/.env loaded — the DB works
# (it has local defaults) but auth does NOT: web login 403s and admin login 401s, because
# FIREBASE_PROJECT_ID / ADMIN_* are never read. Use run-local.ps1 for a working login.

# Terminal 3 — ML service. Activate the venv FIRST.
cd ml
.venv\Scripts\activate       # PowerShell/CMD on Windows;  source .venv/bin/activate on macOS/Linux
uvicorn api.main:app --reload --port 8000

# Terminal 4 — Frontend.
cd frontend
npm run dev                  # serves http://localhost:3000 ; reads config from frontend/.env.local
```

Then open the web app at [localhost:3000/login](http://localhost:3000/login).

### Stop everything

- **Backend / ML / Frontend** — press `Ctrl+C` in each terminal.
- **Postgres** — `cd backend && docker compose down` (data survives in a Docker volume). Or just
  leave it running.

### If I change code, what needs to be (re)started?

| I changed… | Restart | Everything else |
|---|---|---|
| **Frontend** (`frontend/**`) | Nothing — `npm run dev` hot-reloads | keep running |
| **ML service** (`ml/**`) | Nothing — `uvicorn --reload` auto-restarts | keep running |
| **Backend** (`backend/src/**`) | **Restart** Terminal 2 (`Ctrl+C`, `.\run-local.ps1`) — no hot reload for Java | keep running |
| **A DB migration** (`backend/src/main/resources/db/migration/**`) | **Restart the backend** — Flyway applies new migrations on boot | keep Postgres up |
| **`frontend/.env.local`** | **Restart** the frontend (`npm run dev`) — env vars load at startup | keep running |
| **`backend/.env` / a Spring `application-*.yml`** | **Restart the backend** via `.\run-local.ps1` (re-reads `.env`) | keep running |
| **The DB schema/data directly** (psql) | Nothing to restart | — |

**Rule of thumb:** frontend and ML hot-reload themselves; **the backend never does** — any Java
or migration change means a backend restart.

### Local logins (dev only)

- **Web app** (`/login`): Firebase **test phone** `+911234567890`, OTP code `123456`
  (configured in the Firebase console for project `spendwise-21f03`).
- **Admin portal** (`/admin/login`): username `admin`, password `Admin@Local2026`.

### Common gotchas

- **`Failed to fetch` / CORS error on login** → the backend isn't running (Terminal 2), or was
  started before the CORS config existed. Restart the backend.
- **Web login returns 403, or admin login returns 401 with correct credentials** → the backend
  was started with plain `./gradlew bootRun` instead of `.\run-local.ps1`, so `backend/.env`
  (Firebase project ID, admin creds) never loaded. Stop it and restart with `.\run-local.ps1`.
- **Backend won't boot / migration errors** → make sure Postgres (Terminal 1) is up first:
  `docker compose ps` in `backend/` should show `backend-postgres-1` as `Up`.
- **Port already in use** → an old process is still bound. Find it with
  `netstat -ano | findstr :8080` (swap in the port) and stop that PID.
- **ML predictions fail** → confirm the venv is activated and `uvicorn` is on `8000`
  (`curl http://localhost:8000/health`).

## Documentation

See [CLAUDE.md](./CLAUDE.md) for the complete documentation index.

## Project Status

MVP — in active development. Portfolio project by Yash S.
