# Requirements

## Functional Requirements

### Transaction Categories (10 predefined)

1. Shopping
2. Entertainment
3. Sports & Fitness
4. Groceries
5. Travel
6. Miscellaneous
7. Food / Dine Out *(Swiggy, Zomato, restaurants — separate from Groceries)*
8. Cosmetics
9. Subscriptions
10. *(reserved for future addition)*

### Categorization

- ML model auto-assigns a category to every transaction
- User can override any ML assignment
- User corrections stored as labeled examples → incorporated in next retraining cycle
- Recurring unmatched transaction → user prompted to assign a category
- One-off unmatched transaction → defaults to Miscellaneous

### Budget

- User manually sets a monthly budget per category (e.g., ₹2,000 Travel, ₹1,000 Food/Dine Out)
- If historical data exists (bank statement upload), system suggests a starting budget — user can accept or edit
- Budget progress bars shown per category on dashboard

### Alerts

All alerts are user-configurable. Default delivery: push notification + email.

| Alert | Trigger |
|---|---|
| Mid-month budget alert | User has spent 50% of total monthly budget by mid-month |
| Category overspending alert | User exceeds their self-set budget for a specific category |
| Recurring payment alert | System detects a recurring charge (subscription, EMI, etc.) |

Alert priority levels:

| Priority | Delivery |
|---|---|
| High (budget exceeded, critically low balance) | Push notification + email |
| Medium (approaching limit, unusual spending spike) | In-app pop-up only |
| Low (general savings insight) | Passive dashboard feed only |

### EMI / Loan / Debt Tracking

- Detected automatically from SMS (primary)
- Manual entry fallback if not captured by SMS

### AI Chatbot

- Dual mode: proactive advisor (system pushes insights) + interactive (user types questions)
- Has full read access to the user's transaction history
- Powered by a low-cost or open-source LLM
- Secondary feature — not the core product

### Savings Recommendations

- LLM-generated, personalized one-liners based on real spending data
- Fired only when a spending threshold is crossed (not on every transaction)
- Examples: *"You spent ₹3,200 on Food this month — 38% more than last month"*

### Export

- PDF (formatted monthly/date-range report)
- CSV (raw transaction data)
- User selects custom date range or full financial year at export time

### Admin Access

- Developer/admin has full access to all user data, system health, parser logs, ML model accuracy
- Accessible via a separate admin portal with dedicated credentials

---

## Non-Functional Requirements

### Performance

- Accuracy is the priority over speed
- Users review spending at end of day — near-real-time is not required
- Alert SLA: within 1 hour of a transaction being processed
- Background sync interval: ~15–30 minutes

### Availability

- No high-availability requirement at MVP
- Android app: local transaction queue when offline, auto-retry on reconnect — no data loss
- Web dashboard: caching layer serves stale data when backend is unavailable

### Data Retention

- All transaction data kept indefinitely — it is a valuable ML training asset
- Account deletion purges all user data on admin request (DPDP requirement)

### Scale

- MVP: personal + family (~5–10 users)
- Phase 2: ~20–30 early users
- Infrastructure: free-tier hosting throughout MVP

### Compliance

- DPDP Act 2023 (India) — full compliance required from day one
- Explicit user consent at onboarding for all data access purposes
- Privacy policy linked in consent screen
