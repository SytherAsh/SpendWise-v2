# Demo Login Integration (Frontend)

## Overview

The backend provides a simple `/api/v1/auth/demo/login` endpoint that returns JWT tokens for the pre-seeded demo account. No OTP, no credentials required.

**Endpoint:** `POST /api/v1/auth/demo/login`  
**Auth:** Public (no authentication required)  
**Response:** JWT access token + refresh token

## Frontend Implementation

### 1. Add "Demo" Button to Landing Page

Place a button on your landing page (e.g., in the sign-up/login section):

```tsx
<button 
  onClick={handleDemoLogin}
  className="btn btn-primary"
>
  Try Demo Account
</button>
```

### 2. Implement Demo Login Handler

```tsx
// lib/authClient.ts or similar

export async function loginAsDemo() {
  try {
    const response = await fetch(`${API_BASE_URL}/auth/demo/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      throw new Error('Demo login failed');
    }

    const data = await response.json();
    
    // Store tokens (same as regular login)
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);
    
    return data;
  } catch (error) {
    console.error('Demo login error:', error);
    throw error;
  }
}
```

### 3. Update Login Flow

In your login/auth page component:

```tsx
async function handleDemoLogin() {
  try {
    const response = await loginAsDemo();
    
    // Redirect to dashboard
    router.push('/dashboard');
    
    // Optional: Show toast "Welcome to Demo Account"
    toast.success('Demo account loaded');
  } catch (error) {
    toast.error('Failed to load demo account');
  }
}
```

### 4. Frontend Handling: Demo User Badge (Optional)

Once logged in, detect demo user and show a badge:

```tsx
// Check if user is demo account
const isDemoUser = user?.id === '12345678-1234-1234-1234-123456789abc';

{isDemoUser && (
  <div className="badge badge-info">Demo Account</div>
)}
```

Optionally, disable certain actions for demo user (e.g., uploading real bank statements):

```tsx
if (isDemoUser) {
  return <div>Bank statement upload not available in demo mode</div>;
}
```

## Demo Login Flow Diagram

```
Landing Page
    ↓
[Try Demo] Button
    ↓
POST /api/v1/auth/demo/login
    ↓
Backend creates JWT tokens
    ↓
Frontend stores tokens (localStorage)
    ↓
Redirect to /dashboard
    ↓
Dashboard displays demo data (522 transactions, budgets, contacts)
```

## What's Pre-loaded in Demo Account

### Transactions
- **522 realistic transactions** spanning 1 year (July 2025 — June 2026)
- Monthly salary, EMI, subscriptions, daily spending, transfers
- Mix of food, travel, shopping, groceries, etc.
- A few intentionally miscategorized items (to show correction feature)

### Budgets (Current Month)
- Food / Dine Out: ₹10,000
- Travel: ₹7,000
- Transfers: ₹10,000
- Shopping: ₹5,000

### Contacts (Family & Friends)
- Rahul Sharma (Friend)
- Priya Verma (Family)
- Amit Kumar (Friend)
- Shreya Patel (Family)

### Features Available
- ✅ View all transactions (categorized, grouped, filtered)
- ✅ Correct miscategorized transactions
- ✅ View budget progress vs. actuals
- ✅ Analytics (spending trends, category breakdown)
- ✅ Export transactions as CSV/PDF
- ✅ Chat with chatbot (using demo data context)
- ✅ View alerts and notifications
- ✅ View recommendations
- ✅ Manage EMI/recurring payments
- ✅ Edit contacts

### Restrictions (Optional - Implement if Desired)
- ❌ Don't allow upload of real bank statements (show "Demo Mode" message)
- ❌ Don't allow sharing of demo data externally
- ❌ Show "This is demo data" notice in relevant places

## Error Handling

If `/auth/demo/login` fails (unlikely), show user-friendly message:

```tsx
try {
  await loginAsDemo();
} catch (error) {
  toast.error('Demo account unavailable. Please try again or sign up for a regular account.');
}
```

## Testing

### Manual Test Checklist

1. ✅ Click "Demo" button on landing page
2. ✅ Redirected to dashboard without entering any credentials
3. ✅ Transactions are visible (should see ~522 rows)
4. ✅ Budgets are visible and pre-configured
5. ✅ Contacts are visible
6. ✅ Can view analytics/trends
7. ✅ Can correct a transaction category
8. ✅ Can export data
9. ✅ Demo badge shows (if implemented)
10. ✅ Logging out and logging back in works normally

### API Test

```bash
curl -X POST http://localhost:8080/api/v1/auth/demo/login \
  -H "Content-Type: application/json"
```

Expected response:
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

## Disabling Demo (Production)

If you want to disable demo login in production, configure the backend:

```properties
# application-prod.yml
demo.enabled: false
```

This will:
1. Skip demo data seeding on startup
2. Not create demo user (if it doesn't already exist)
3. Demo endpoint will still exist but return 404 or 403

To completely remove demo endpoints, comment out `DemoAuthController` or add a feature flag.

## FAQ

**Q: Can demo users edit data?**  
A: Yes, they can edit everything (correct categories, update contacts, etc.) just like real users. This lets them explore the full app.

**Q: Does demo data persist?**  
A: Yes, until explicitly deleted. If you want demo data to reset daily/weekly, you'd need a scheduled cleanup job.

**Q: Can I customize demo data?**  
A: Yes. Regenerate `data/demo-transactions.csv` using `data/generate_demo_csv.py` with different patterns, then redeploy backend.

**Q: What if someone wants to keep exploring after demo?**  
A: They can sign up for a real account. Alternatively, you could auto-upgrade demo account to real account if they add a phone number.
