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

## Documentation

See [CLAUDE.md](./CLAUDE.md) for the complete documentation index.

## Project Status

MVP — in active development. Portfolio project by Yash S.
