# User Flows

## Onboarding (First Launch — Android App)

```
1. Open app for the first time
2. Sign up screen → choose Phone OTP or Google login
3. Consent screen (mandatory gate — cannot proceed without accepting):
   - SMS read access (required — app is non-functional without it)
   - Storage of transaction data on SpendWise servers
   - Use of anonymized data to improve the ML categorization model
   - Storage of raw SMS text for debugging
4. Grant SMS read permission → system prompt
   - If denied: app shows blocking screen explaining why it's required
5. Grant notification permission (for alerts) → system prompt
   - If denied: alerts will only appear in-app, no push/email
6. Onboarding questionnaire:
   a. Select payment apps used (Paytm, GPay, PhonePe, etc.)
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

```
User opens app / web dashboard
→ Dashboard shows: alerts panel (top), category summary, budget progress bars, trend charts
→ User taps a category → drilldown view with individual transactions
→ User can filter by: week / month / year, date range
→ User can compare: this month vs. last month, this year vs. last year
```

### Correcting a Category

```
User sees a transaction categorized incorrectly
→ Taps transaction → "Change Category" option
→ Selects correct category from list of 10
→ Correction saved to ml_corrections table
→ Next retraining cycle incorporates this as a labeled example
```

### Setting / Editing a Budget

```
User goes to Budget screen
→ Sees current budgets per category with progress bars
→ Taps a category → enters monthly limit (e.g., ₹2,000 for Travel)
→ If bank statement was uploaded: system shows suggested budget first, user can accept or edit
→ Budget saved; alerts will fire if 50% is reached at mid-month or category is exceeded
```

### Handling an Alert

```
Push notification arrives: "You've spent 50% of your monthly budget by mid-month"
→ User taps notification → opens app
→ Alert detail shows which category, current spend vs. budget
→ User dismisses alert or adjusts budget
→ Alert marked as delivered in database
```

### Chatbot Interaction

```
User opens chat tab
→ Types: "How much did I spend on food last month?"
→ System injects user's transaction data for the relevant period as context
→ LLM returns: "You spent ₹3,240 on Food & Dining in May 2026 across 14 transactions"
→ Conversation history preserved for the session
OR
→ System proactively pushes: "Your Swiggy orders are up 60% this month — consider cooking at home twice a week"
```

### Exporting a Report

```
User goes to Export screen (web dashboard or Android)
→ Selects date range: custom start/end date OR select full financial year
→ Selects format: PDF (formatted report) or CSV (raw data)
→ File downloaded or shared
```

### Recurring Payment Detection

```
System detects same merchant charged 2+ times at regular intervals
→ Alert: "Recurring payment detected: ₹199 to Netflix every month"
→ User can confirm it as a subscription (saved to emis table) or dismiss
→ Future recurring alerts reference this confirmed subscription
```

## Multi-Device Flow

```
Same user logs in on Android app AND web browser
→ Both sessions share the same JWT (7-day expiry)
→ Transactions synced to server reflect on both surfaces in near real-time
→ No data is device-local except the offline queue (unsynced transactions)
```

## Admin Flow (Developer)

```
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
