# Infrastructure

Configuration and setup notes for SpendWise hosting infrastructure.

## Services

| Service | Provider | URL |
|---|---|---|
| Web Dashboard | Vercel | Auto-deploy from `main` branch |
| Spring Boot Backend | Render / Railway / Fly.io | TBD during setup |
| FastAPI ML Service | Render / Railway / Fly.io | TBD during setup |
| Database | Supabase | PostgreSQL, free tier |
| Auth | Firebase | OTP + Google login |
| Error Tracking | Sentry | Integrated in Spring Boot + FastAPI |
| Uptime Monitoring | UptimeRobot | Pings `/api/v1/health` every 5 minutes |
| Android Distribution | Firebase App Distribution | APK distribution to testers |

## Setup Checklist

### Supabase

- [ ] Create project
- [ ] Run schema migrations (SQL from `docs/database.md`)
- [ ] Enable Row-Level Security on all tables with `user_id`
- [ ] Copy connection string to `SUPABASE_URL` and `SUPABASE_KEY` env vars

### Firebase

- [ ] Create Firebase project
- [ ] Enable Phone Authentication (OTP)
- [ ] Enable Google Sign-In
- [ ] Download `google-services.json` for Android (gitignored)
- [ ] Download Firebase Admin SDK JSON for Spring Boot (gitignored)

### Sentry

- [ ] Create project for Spring Boot (Java)
- [ ] Create project for FastAPI (Python)
- [ ] Add DSN to environment variables

### UptimeRobot

- [ ] Add HTTP monitor for `GET https://<backend-host>/api/v1/health`
- [ ] Set email alert to developer email

## Environment Variables

All environment variable names are documented in [docs/deployment.md](../docs/deployment.md).

## Backend Hosting Selection

When setting up, choose a free-tier platform that keeps the service **always-on** (not spin-down on inactivity). Background jobs (alert evaluation, recommendation generation) run on a schedule and cannot tolerate cold starts.

Evaluated options:
- **Fly.io** — free allowance, always-on, supports Java + Python
- **Railway** — monthly free credit, always-on
- **Render** — free tier spins down after 15 min inactivity (not suitable without paid plan)
