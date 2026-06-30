# Frontend — Next.js (React)

The web dashboard for SpendWise. Interactive financial analytics with Excel-style data exploration.

## Tech

- **Framework**: Next.js (React) — App Router
- **Language**: TypeScript
- **Hosting**: Vercel (free tier, auto-deploy from main)
- **Charts**: React-based interactive library (Recharts / Nivo / Chart.js — TBD during implementation)
- **Auth**: Firebase Authentication (client-side)

## Structure

```
src/
├── app/                    Next.js App Router pages
├── components/
│   ├── dashboard/          Main dashboard, alerts panel, budget progress bars
│   ├── charts/             Interactive chart components (donut, line, bar, comparison)
│   ├── transactions/       Transaction list, filters, category correction UI
│   ├── chatbot/            Chat interface (proactive + interactive)
│   └── shared/             Shared UI components, design system tokens
├── lib/                    API client, auth helpers, date utilities, formatCurrency
└── styles/                 Global styles, theme (color palette, typography)
```

## Running Locally

```bash
npm install
cp .env.local.example .env.local  # fill in API base URL and Firebase config
npm run dev
```

Dashboard at `http://localhost:3000`

## Key Features

- **Interactive charts**: hover tooltips, click-to-drilldown, period comparison (week/month/year)
- **Budget progress bars**: visual per-category tracking vs. monthly limit
- **Alerts panel**: prioritized at top of dashboard
- **Export**: PDF and CSV with custom date range selector
- **Admin portal**: separate route `/admin` with cross-user analytics

## Design Consistency

The Android app and web dashboard share the same visual language — same color palette, typography, and component naming conventions. Design tokens are defined in `src/styles/tokens.css`.
