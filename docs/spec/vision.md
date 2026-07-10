# Vision & Target Users

## Problem

Indian UPI users typically transact across 2–3 payment apps (Paytm, GPay) and one or more bank accounts (SBI and others). Each app has its own dashboard. At the end of the month, there is no single place to review total spending — users have to manually check multiple apps, and most give up.

## Solution

SpendWise reads SMS transaction alerts sent by banks and payment apps, parses them into structured data, and presents everything in one unified dashboard. Users get a complete picture of their finances without switching between apps.

## Success Criteria

- Developer can demo the product confidently as a portfolio/resume project
- Works reliably for personal use and immediate family (~5–10 users)
- Architecture supports growth to ~20–30 early users without infrastructure changes
- Core flow (SMS → parse → categorize → dashboard) works end-to-end

## Target Users

- **Age**: 20–40 years old
- **Profile**: Active everyday spenders (food, subscriptions, bills, travel, entertainment) who want visibility into where their money goes
- **Geography**: India
- **Platform**: Android phone users
- **Language**: English (MVP); Hindi and Marathi planned post-MVP

## What success looks like for a user

1. Download the app, grant SMS permission
2. See all available SMS transaction history appear automatically (up to several years, depending on what is stored on the device)
3. Transactions are categorized correctly most of the time
4. Get an alert when they're overspending on food this month
5. Open the web dashboard and see a clear picture of their spending patterns

## Non-goals (MVP)

- iOS support
- International users
- Monetization
- Social or shared finance features
- Investment/stock tracking (post-MVP)
- PhonePe SMS parsing (post-MVP — deferred to roadmap Phase 2)
