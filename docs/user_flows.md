# User Flows

## Onboarding (First Launch — Android App)

```text
1. Open app for the first time
2. Sign up screen → choose Phone OTP or Google login
3. Consent screen (mandatory gate — cannot proceed without accepting):
   - SMS read access (required — app is non-functional without it)
   - Storage of transaction data on SpendWise servers
   - Use of anonymized data to improve the ML categorization model
4. Grant SMS read permission → system prompt
   - If denied: app shows blocking screen explaining why it's required
5. Grant notification permission (for alerts) → system prompt
   - If denied: alerts will only appear in-app, no push/email
6. Onboarding questionnaire:
   a. Select payment apps used (Paytm, GPay)
   b. Select bank(s) used (SBI, HDFC, etc.)
   c. Estimate monthly spending (slider or text input)
7. Optional: Upload bank statement PDF
   - System parses it to seed historical transactions
   - Suggests initial budgets per category based on history
   - User can skip this step
8. App reads full SMS inbox (backfills all historical transaction SMS)
9. Land on personalized dashboard
10. Background SMS monitoring starts (foreground service active)
```

## Ongoing User Flows

### Reviewing Transactions

```text
User opens app / web dashboard
→ Dashboard shows: alerts panel (top), savings recommendations feed, category summary, budget progress bars, trend charts
→ User taps a category → drilldown view with individual transactions
→ User can filter by: week / month / year, date range
→ User can compare: this month vs. last month, this year vs. last year
```

### Browsing the Transaction List

```text
User opens the Transactions screen
→ Paginated list loads (GET /transactions — cursor-based, 50/page)
→ User applies optional filters: date range, category
→ User taps a transaction → detail view (GET /transactions/:id)
→ "Change Category" option available in detail view (see Correcting a Category)
```

### Correcting a Category

```text
User sees a transaction categorized incorrectly
→ Taps transaction → "Change Category" option
→ Selects correct category from list of 10
→ Correction saved to ml_corrections table
→ Next retraining cycle incorporates this as a labeled example
```

### Setting / Editing a Budget

```text
User goes to Budget screen
→ Sees current budgets per category with progress bars
→ Taps a category → enters monthly limit (e.g., ₹2,000 for Travel)
→ If bank statement was uploaded: system shows suggested budget first, user can accept or edit
→ Budget saved; three alert thresholds apply:
   - Mid-month: 50% of total monthly budget spent by mid-month → high priority (push + email)
   - Approaching limit: 80% of a specific category budget spent → medium priority (in-app only)
   - Overspending: category budget fully exceeded → high priority (push + email)
```

### Viewing Savings Recommendations

```text
Dismissable insight cards appear in the savings recommendations feed on the dashboard
→ Cards are generated when a spending threshold is crossed — not on every transaction
→ Example: "You spent ₹3,200 on Food this month — 38% more than last month"
→ User taps Dismiss → PUT /recommendations/:id/dismiss → card removed from feed
→ Next recommendation generated on the following threshold-crossing event
```

### Handling an Alert

```text
Push notification arrives: "You've spent 50% of your monthly budget by mid-month"
→ User taps notification → opens app
→ Alert detail shows which category, current spend vs. budget
→ User dismisses alert or adjusts budget
→ Alert marked as read (PUT /alerts/:id/read; is_read = true)
   Note: delivered_at is set automatically by the server when FCM/SMTP confirms push delivery — that is a separate, earlier event independent of user action
```

### Chatbot Interaction

```text
New conversation:
→ User opens chat tab → taps "New Chat" → POST /chatbot/sessions creates a new session
→ Types: "How much did I spend on food last month?"
→ System injects user's transaction data for the relevant period as context
→ LLM returns: "You spent ₹3,240 on Food & Dining in May 2026 across 14 transactions"
→ Messages stored per session in chatbot_conversations

Resume previous conversation:
→ User opens chat tab → past sessions listed (GET /chatbot/sessions, ordered by last_active_at)
→ Taps a session → full history loads (GET /chatbot/sessions/:id)
→ User continues typing in the existing session context
```

### Exporting a Report

```text
User goes to the Export tab within Settings (web dashboard) or the Export screen (Android)
→ Selects date range: custom start/end date OR select full financial year
→ Selects format: PDF (formatted report) or CSV (raw data)
→ File downloaded or shared
```

> **Web IA note (UI/UX redesign):** On the web dashboard, Export is a **tab within Settings**
> (`/settings` → Export), not a standalone `/export` route. Settings is organized into three
> tabs — **Profile** (phone read-only, editable email, member-since, appearance/theme toggle,
> privacy policy link), **Preferences** (alert channels, payment apps, banks, monthly spend
> estimate), and **Export**. The former standalone `/budget`, `/emis`, and `/export` routes were
> removed once Planning (Budgets + EMIs tabs) and Settings (Export tab) fully absorbed them.

### Appearance / Theme (web dashboard)

```text
User opens Settings → Profile → Appearance
→ Chooses Light, Dark, or System (default: System, follows the OS preference)
→ Choice persists across sessions (stored client-side) and applies app-wide immediately
```

> Client-only preference (no backend field) — the web app drives Tailwind's `dark:` variant from
> a `data-theme` attribute via `next-themes`; nothing about the theme is sent to or stored by the
> backend.

### Recurring Payment Detection

```text
System detects 3+ transactions from the same merchant (UPI ID or recipient name) within a rolling 60-day window, with amounts within ±10% of each other, not already tracked as an EMI
→ Alert: "Recurring payment detected: ₹199 to Netflix every month"
→ User can confirm it as a subscription (saved to emis table) or dismiss
→ Future recurring alerts reference this confirmed subscription
```

### EMI / Subscriptions Management

```text
User navigates to Subscriptions screen
→ Active tracked EMIs and subscriptions listed (GET /emis)
→ User taps an entry to edit label, amount, or due day (PUT /emis/:id)
→ User deactivates a cancelled subscription → PATCH /emis/:id → is_active = false; record retained for history
```

## Multi-Device Flow

```text
Same user logs in on Android app AND web browser
→ Each login creates an independent refresh token (separate row in refresh_tokens)
→ Both sessions authenticate as the same user but hold independent tokens
→ Logout on one device revokes only that device's refresh token — the other session is unaffected
→ Transactions synced to server reflect on both surfaces in near real-time
→ No data is device-local except the offline queue (unsynced transactions)
```

## Admin Flow (Developer)

```text
Admin navigates to admin portal URL (separate from user app)
→ Logs in with admin credentials (separate from any user account)
→ Sees: user list, aggregate stats, cross-user spending comparison
→ Can view full transaction data for any user
→ Can view: admin_logs (parser failures, sync errors), ML model accuracy trends
→ Can trigger: manual model retraining
→ Can delete: a specific user's account and all data on request
```

## Edge Cases

### SMS Permission Denied

- App shows a blocking screen explaining that SMS access is the core feature
- Deep link to Android settings to grant the permission
- App cannot proceed past this screen without permission

### Backend Offline

- Android app queues transactions locally in Room DB
- Retry logic attempts sync every 15–30 minutes
- No transactions are lost
- Web dashboard shows last-cached data with a stale indicator

### First-Time User (No Historical Data, No Bank Statement)

- ML model uses global baseline model (trained on 3-year bank statement)
- User is prompted to set budgets manually (no suggestions available)
- First 2–4 weeks of real transactions provide enough data for budget suggestions
