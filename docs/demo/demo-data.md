# Demo Data & Demo Account

## Overview

SpendWise includes a pre-configured demo account with 1 year of realistic transaction data. This allows new users and stakeholders to explore the full application without creating an account or entering real financial data.

**Demo User Credentials:**
- Phone: `+919876543210`
- Email: `demo@spendwise.local`
- User ID: `12345678-1234-1234-1234-123456789abc`

## Demo Data Structure

### Transactions (522 total)

The demo CSV (`data/demo-transactions.csv`) contains 1 year of realistic transactions with the following patterns:

#### Fixed Recurring Transactions
- **Salary**: ₹75,000 every month on the 25th (credit, bank transfer)
- **Car EMI**: ₹5,000 every month on the 28th (debit, loan payment)
- **Spotify**: ₹69 on the 27th every month (subscription)
- **Light Bill**: ₹1,500–2,000 between 26–30th each month (utility)
- **Gas Bill**: ₹1,000–1,200 alternate months (utility)
- **Netflix**: ₹199 once every 3 months (subscription)

#### Variable Daily Transactions
- **Food/Dining**: Swiggy, Zomato, Dominos, local restaurants (₹50–500, 3–4 times/week)
- **Travel**: Rapido, Uber, Ola, fuel, flights (₹30–300, 2–3 times/week)
- **Groceries**: BigBasket, Blinkit, DMart, vendors (₹200–800, 1–2 times/week)
- **Shopping**: Amazon, Flipkart, Myntra, etc. (₹500–3,000, 1–2 times/month)
- **Transfers**: Money sent to/received from family and friends (₹500–5,000, 1–2/month)
- **Miscellaneous**: Pharmacies, salons, bookstores, etc. (₹100–1,500, sparse)

#### Miscategorized Transactions
A handful of transactions are intentionally left uncategorized or marked as "Miscellaneous" to demonstrate how users can correct categorization in the app.

### Categories

All transactions are tagged with one of 12 categories:
1. Shopping
2. Entertainment
3. Sports & Fitness
4. Groceries
5. Travel
6. Miscellaneous
7. Food / Dine Out
8. Cosmetics
9. Subscriptions
10. Transfers
11. Medical
12. Fees & Debt

### Budgets (Pre-configured for current month)

| Category | Limit |
|----------|-------|
| Food / Dine Out | ₹10,000 |
| Travel | ₹7,000 |
| Transfers | ₹10,000 |
| Shopping | ₹5,000 |

### Contacts (Family & Friends)

Four pre-registered contacts for grouping transfers:
- **Rahul Sharma** (Friend) — UPI: `rahul.sharma@upi`
- **Priya Verma** (Family) — UPI: `priya.v@okaxis`
- **Amit Kumar** (Friend) — UPI: `amit.k@ibl`
- **Shreya Patel** (Family) — UPI: `shreya.p@upi`

## Backend Setup

### Automatic Seeding

The demo account is **automatically created on application startup** if it doesn't already exist. This is handled by the `DemoDataSeeder` component (`com.spendwise.ingest.DemoDataSeeder`).

**What happens on startup:**
1. Check if demo user (ID: `12345678-1234-1234-1234-123456789abc`) exists
2. If not, create the demo user account with phone and email
3. Load transactions from `data/demo-transactions.csv`
4. Parse CSV and insert 522 transactions into the database
5. Set up pre-configured budgets and contacts
6. Transactions are inserted with `source='bank_statement'`

### Configuration

Enable/disable demo seeding via application properties:

```properties
# application.yml
demo:
  enabled: true                    # Enable demo data seeding on startup
  phone: +919876543210            # Demo user phone number
  email: demo@spendwise.local      # Demo user email
```

To disable demo data (e.g., in production):
```properties
demo.enabled: false
```

### CSV Parser

The `CsvTransactionParser` component (in `com.spendwise.transaction.util`) handles CSV parsing:
- Supports the demo transaction CSV format
- **Reusable for future user-uploaded bank statements**
- Validates required fields (transaction_date, amount, dr_cr_indicator, transaction_id)
- Handles quoted CSV fields and empty values
- Converts CSV rows to `IngestTransactionItem` objects ready for ingest

**CSV Format:**
```csv
transaction_date,debit,credit,amount,dr_cr_indicator,transaction_id,recipient_name,upi_id,bank,transaction_mode,note,source,category
2025-07-01T14:01:00Z,482,0,-482,DR,txn_818144230c132431,Zomato,zomato@icic,AXIS,UPI,,bank_statement,7
...
```

### Device API Key

The demo account currently has no device API key registered (demo is for web/browser only). If Android demo access is needed, a device key should be registered:

```sql
INSERT INTO device_api_keys (id, user_id, key_hash, registered_at, is_active)
VALUES (gen_random_uuid(), '12345678-1234-1234-1234-123456789abc', '<bcrypt_hash_of_demo_key>', NOW(), TRUE);
```

## Frontend Setup

### Demo Login Flow

**Option A: Pre-set Demo User (Recommended)**
- Frontend hardcodes the demo phone number
- User clicks "Demo" → calls `/auth/otp/send` with `+919876543210`
- No OTP required (or use a fixed OTP like `000000`)
- User is logged in and sees pre-populated demo dashboard

**Option B: Special Demo Login Endpoint**
- Backend provides `/auth/demo/login` endpoint that bypasses OTP
- Frontend calls this endpoint directly
- Returns access + refresh token for demo user

**Implementation needed:**
1. Decide which approach (A or B) and implement in frontend
2. Frontend should detect demo user and show appropriate messaging (e.g., "Demo Account" badge)
3. Prevent demo user from editing account details or sensitive settings

### Frontend Considerations

- Demo transactions are already categorized (via backend seeding)
- Demo budgets are pre-set
- Demo contacts are pre-registered
- All features (filtering, grouping, analytics, budgets, alerts) work normally
- Demo user cannot upload real bank statements (optional: disable this UI)
- Demo data persists across sessions (normal login behavior)

## Future: User CSV Upload

This demo data architecture is designed to support a **future user CSV upload feature**:

1. **Backend CSV parser is already reusable** — `CsvTransactionParser` can parse any properly-formatted CSV
2. **Ingest endpoint already exists** — users' CSV data flows through the same `/ingest` → categorization → storage pipeline
3. **Database schema ready** — `source='bank_statement'` already tags CSV-sourced transactions

To implement user CSV upload:
1. Create a `/users/me/bank-statement/upload` endpoint that accepts file uploads
2. Validate and parse CSV using `CsvTransactionParser`
3. Call `/ingest` endpoint to load transactions
4. Same validation, deduplication, and categorization as demo data

## Monitoring & Debugging

### Check if demo data was seeded:

```sql
SELECT COUNT(*) FROM transactions WHERE user_id = '12345678-1234-1234-1234-123456789abc';
-- Expected: ~522 rows
```

### Check demo user account:

```sql
SELECT * FROM users WHERE id = '12345678-1234-1234-1234-123456789abc';
```

### Check demo budgets:

```sql
SELECT b.*, c.name FROM budgets b
JOIN categories c ON b.category_id = c.id
WHERE b.user_id = '12345678-1234-1234-1234-123456789abc'
ORDER BY b.month DESC, b.category_id;
```

### Check demo contacts:

```sql
SELECT name, relationship_type, upi_id FROM contacts
WHERE user_id = '12345678-1234-1234-1234-123456789abc'
ORDER BY created_at;
```

### Seeding logs:

Look for logs during application startup:
```
INFO  DemoDataSeeder - Starting demo data seeding...
INFO  DemoDataSeeder - Creating demo user with phone: +919876543210
INFO  DemoDataSeeder - Loading demo transactions from CSV: data/demo-transactions.csv
INFO  DemoDataSeeder - Parsed 522 transactions from CSV
INFO  DemoDataSeeder - Inserted 522 transactions for demo user
INFO  DemoDataSeeder - Setting up demo user budgets
INFO  DemoDataSeeder - Setting up demo user contacts
INFO  DemoDataSeeder - Demo data seeding completed successfully
```

## CSV Column Reference

| Column | Type | Required | Format | Notes |
|--------|------|----------|--------|-------|
| `transaction_date` | Instant | ✓ | ISO 8601 (e.g., `2025-07-01T14:01:00Z`) | Transaction timestamp |
| `debit` | Decimal | ✓ | Positive number or 0 | Amount debited (outgoing) |
| `credit` | Decimal | ✓ | Positive number or 0 | Amount credited (incoming) |
| `amount` | Decimal | ✓ | Signed (negative=debit, positive=credit) | Canonical amount field |
| `dr_cr_indicator` | String | ✓ | `DR` or `CR` | Debit/Credit indicator |
| `transaction_id` | String | ✓ | Unique per user | Bank reference or synthetic ID |
| `recipient_name` | String | Optional | Any text | Payee/payer name |
| `upi_id` | String | Optional | Format: `xxx@bank` | UPI identifier |
| `bank` | String | Optional | Bank code (e.g., `SBIN`, `HDFC`) | Bank name/code |
| `transaction_mode` | String | Optional | `UPI`, `INB`, `IMPS`, `NEFT` | Payment mode |
| `note` | String | Optional | Any text | Transaction description |
| `source` | String | Optional | `sms`, `bank_statement`, `manual` | Forced to `bank_statement` by ingest |
| `category` | Integer | Optional | Category ID (1–12) | For reference; not sent to ingest |

