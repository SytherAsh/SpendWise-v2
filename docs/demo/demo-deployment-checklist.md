# Demo Feature — Deployment & Verification Checklist

## Pre-Deployment Verification

### Backend Code

- [ ] `backend/src/main/java/com/spendwise/transaction/util/CsvTransactionParser.java` — CSV parser component
- [ ] `backend/src/main/java/com/spendwise/ingest/DemoDataSeeder.java` — Demo data seeding component
- [ ] `backend/src/main/java/com/spendwise/auth/DemoAuthController.java` — Demo login endpoint
- [ ] All three files compile without errors
- [ ] No import errors or missing dependencies

### Backend Resources

- [ ] `backend/src/main/resources/data/demo-transactions.csv` exists
- [ ] CSV file contains 522 rows (plus header)
- [ ] CSV is readable and well-formed

### Database

- [ ] PostgreSQL/Supabase is accessible
- [ ] Row-Level Security (RLS) is configured per `docs/database.md`
- [ ] Demo user does NOT already exist (check below)

**Check if demo user exists:**
```sql
SELECT COUNT(*) FROM users WHERE id = '12345678-1234-1234-1234-123456789abc';
-- Expected: 0 (before seeding) or 1 (after seeding)
```

### Configuration

- [ ] `application.yml` has demo properties:
  ```yaml
  demo:
    enabled: true
    phone: +919876543210
    email: demo@spendwise.local
  ```
- [ ] `JWT_SECRET` environment variable is set
- [ ] Database connection is configured

---

## Deployment Steps

### Step 1: Build Backend

```bash
cd backend
./mvnw clean package -DskipTests
# or
gradle build -x test
```

**Verify:**
- [ ] Build succeeds without errors
- [ ] All three new components compile
- [ ] No "dependency not found" errors

### Step 2: Start Backend Application

```bash
java -jar backend/target/spendwise-*.jar
# or via IDE: Run main class com.spendwise.SpendwiseApplication
```

**Wait for startup to complete. Check logs for:**

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

**If you see these logs, seeding succeeded!** ✅

---

## Post-Deployment Verification

### 1. Database Verification

**Check demo user exists:**
```sql
SELECT id, phone, email, created_at FROM users 
WHERE id = '12345678-1234-1234-1234-123456789abc';
```

**Expected result:**
```
id: 12345678-1234-1234-1234-123456789abc
phone: +919876543210
email: demo@spendwise.local
created_at: <current timestamp>
```

**Check transaction count:**
```sql
SELECT COUNT(*) as transaction_count FROM transactions 
WHERE user_id = '12345678-1234-1234-1234-123456789abc';
```

**Expected result:**
```
transaction_count: 522
```

**Check budgets:**
```sql
SELECT b.id, c.name, b.monthly_limit, b.month, b.year 
FROM budgets b
JOIN categories c ON b.category_id = c.id
WHERE b.user_id = '12345678-1234-1234-1234-123456789abc'
ORDER BY c.name;
```

**Expected result (4 rows):**
```
Food / Dine Out: 10000
Shopping: 5000
Transfers: 10000
Travel: 7000
```

**Check contacts:**
```sql
SELECT name, relationship_type, upi_id FROM contacts 
WHERE user_id = '12345678-1234-1234-1234-123456789abc'
ORDER BY created_at;
```

**Expected result (4 rows):**
```
Rahul Sharma (friend): rahul.sharma@upi
Priya Verma (family): priya.v@okaxis
Amit Kumar (friend): amit.k@ibl
Shreya Patel (family): shreya.p@upi
```

### 2. API Endpoint Verification

**Test demo login endpoint:**

```bash
curl -X POST http://localhost:8080/api/v1/auth/demo/login \
  -H "Content-Type: application/json"
```

**Expected response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 604800,
  "user": {
    "id": "12345678-1234-1234-1234-123456789abc",
    "phone": "+919876543210",
    "email": null
  }
}
```

**Verify access token is valid JWT:**
- Copy the `accessToken` value
- Go to https://jwt.io
- Paste the token
- Verify it decodes with subject: `12345678-1234-1234-1234-123456789abc`
- Verify it has 7-day expiration (604800 seconds)

**Test demo info endpoint:**

```bash
curl -X POST http://localhost:8080/api/v1/auth/demo/info \
  -H "Content-Type: application/json"
```

**Expected response (200 OK):**
```json
{
  "title": "SpendWise Demo Account",
  "subtitle": "Explore SpendWise with realistic sample data",
  "description": "12-month transaction history...",
  "budgets": "Food ₹10k, Travel ₹7k...",
  "features": "All features enabled..."
}
```

### 3. Authenticated API Verification

**Using the access token from step 2.1, test authenticated endpoints:**

```bash
# Get transactions
curl -X GET "http://localhost:8080/api/v1/transactions?limit=10" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"

# Expected: 200 OK with 10 transactions

# Get budgets
curl -X GET "http://localhost:8080/api/v1/budgets" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"

# Expected: 200 OK with 4 budgets (Food, Travel, Transfer, Shopping)

# Get contacts
curl -X GET "http://localhost:8080/api/v1/contacts" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"

# Expected: 200 OK with 4 contacts
```

---

## Frontend Verification

### 1. Add Demo Button to Landing Page

Add to your landing page component:

```tsx
<button 
  onClick={async () => {
    const response = await fetch('/api/v1/auth/demo/login', { method: 'POST' });
    const data = await response.json();
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);
    router.push('/dashboard');
  }}
  className="btn btn-primary"
>
  Try Demo Account
</button>
```

### 2. Manual End-to-End Test

1. **Start frontend dev server**
   ```bash
   cd frontend
   npm run dev
   ```

2. **Go to landing page**
   - Open http://localhost:3000 (or your dev URL)

3. **Click "Try Demo" button**
   - ✅ Should redirect to `/dashboard` WITHOUT any login form
   - ✅ Dashboard should load immediately

4. **Verify demo data appears**
   - ✅ See "Demo Account" badge (if implemented)
   - ✅ Transactions page shows transactions (scroll down, should see 100+ rows)
   - ✅ Should see salary credits (₹75,000)
   - ✅ Should see EMI debits (₹5,000)
   - ✅ Should see food/dining transactions
   - ✅ Should see travel transactions

5. **Test dashboard features**
   - ✅ **Transactions page**: Filter by category, see grouped transactions
   - ✅ **Analytics page**: View spending trends, category breakdown
   - ✅ **Planning/Budgets**: See 4 pre-configured budgets
   - ✅ **Contacts**: See 4 family members registered
   - ✅ **Edit a transaction category**: Click transaction → change category → verify it updates
   - ✅ **Export**: Download transactions as CSV/PDF

6. **Test logout and re-login**
   - ✅ Click Logout
   - ✅ Redirected to login page
   - ✅ Click "Try Demo" again
   - ✅ Dashboard loads again (data persists)

---

## Troubleshooting

### Issue: "Demo data seeding is disabled"

**Symptom:** Log says `Demo data seeding is disabled (demo.enabled=false)`

**Fix:** 
- Check `application.yml` or environment variable
- Ensure `demo.enabled: true` is set
- Restart backend

### Issue: "Failed to seed demo data"

**Symptom:** Log shows error during seeding

**Check:**
1. Is `/data/demo-transactions.csv` in `backend/src/main/resources/data/`?
2. Is the CSV file readable and well-formed?
3. Is the database connection working?
4. Check full error in logs for specific issue

**Solution:**
- Copy CSV to resources: `cp data/demo-transactions.csv backend/src/main/resources/data/`
- Restart backend

### Issue: 422 transactions instead of 522

**Symptom:** Database shows 422 transactions instead of 522

**Check:**
- Some transactions may fail to insert due to duplicate transaction_ids
- This is expected if demo data was seeded before
- If first seeding, check logs for INSERT errors

**Solution:**
- Clear demo user data and reseed:
  ```sql
  DELETE FROM transactions WHERE user_id = '12345678-1234-1234-1234-123456789abc';
  DELETE FROM budgets WHERE user_id = '12345678-1234-1234-1234-123456789abc';
  DELETE FROM contacts WHERE user_id = '12345678-1234-1234-1234-123456789abc';
  DELETE FROM users WHERE id = '12345678-1234-1234-1234-123456789abc';
  ```
- Restart backend to reseed

### Issue: Demo login returns 500 error

**Symptom:** `POST /api/v1/auth/demo/login` returns 500

**Check logs for:**
- JWT generation failure
- Refresh token service error
- Missing dependencies

**Solution:**
- Verify `UserJwtService` and `RefreshTokenService` are injected correctly
- Check JWT_SECRET is set
- Verify JWT signing key is valid

### Issue: Frontend demo button doesn't work

**Symptom:** Button click does nothing or 404 error

**Check:**
1. Is backend running? (`curl http://localhost:8080/actuator/health`)
2. Is endpoint correct? (`POST /api/v1/auth/demo/login`, not `/demo/login`)
3. Are CORS headers configured? (should be via Spring Security)
4. Check browser console for network errors

**Solution:**
- Verify backend is running and accessible
- Check CORS config in `CorsConfig.java` includes demo endpoint
- Restart backend

---

## Success Criteria

All items below must pass for demo feature to be production-ready:

- [ ] Backend builds and starts without errors
- [ ] Seeding logs show "Demo data seeding completed successfully"
- [ ] Database has 522 transactions for demo user
- [ ] Database has 4 budgets for demo user
- [ ] Database has 4 contacts for demo user
- [ ] `POST /api/v1/auth/demo/login` returns valid JWT tokens
- [ ] Demo tokens are accepted by protected endpoints
- [ ] Frontend "Try Demo" button redirects to dashboard
- [ ] Dashboard displays all 522 demo transactions
- [ ] All dashboard features work with demo data
- [ ] Demo badge shows (optional)
- [ ] Logout and re-login works

---

## Rollback Instructions

If something goes wrong and you need to disable demo:

**Option 1: Disable seeding (keep demo data in DB)**
```yaml
# application.yml
demo:
  enabled: false
```

**Option 2: Remove demo endpoints (can still access via API if token exists)**
- Comment out `@RestController` in `DemoAuthController.java`
- Rebuild and restart

**Option 3: Clean demo data from database**
```sql
DELETE FROM transactions WHERE user_id = '12345678-1234-1234-1234-123456789abc';
DELETE FROM budgets WHERE user_id = '12345678-1234-1234-1234-123456789abc';
DELETE FROM contacts WHERE user_id = '12345678-1234-1234-1234-123456789abc';
DELETE FROM users WHERE id = '12345678-1234-1234-1234-123456789abc';
```

---

## Next Steps After Verification

1. **Production Deployment:**
   - Set `demo.enabled: true` in production config (marketing feature)
   - Or set to `false` if demo should be dev-only

2. **Documentation:**
   - Add "Try Demo" link to landing page
   - Add "Demo Account" section to help/FAQ

3. **Monitoring:**
   - Track demo login frequency (GET `/analytics` for demo user)
   - Monitor seeding performance (log entry time)

4. **Future Enhancements:**
   - Reset demo data daily/weekly (scheduled cleanup)
   - User CSV upload feature (reuse `CsvTransactionParser`)
   - Multiple demo scenarios

---

## Questions?

Refer to:
- `docs/demo-data.md` — Data structure and specifications
- `docs/demo-login-integration.md` — Frontend integration guide
- `docs/demo-feature-complete.md` — Complete implementation overview
