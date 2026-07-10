# Demo Login Integration (Frontend)

## Status: IMPLEMENTED & VERIFIED

This document originally described a generic/aspirational integration plan. It now documents the
**actual, shipped implementation** — see file references throughout.

## Overview

The backend provides `POST /api/v1/auth/demo/login`, which returns real JWT tokens for the
pre-seeded demo account. No OTP, no credentials required.

**Endpoint:** `POST /api/v1/auth/demo/login`
**Auth:** Public (no authentication required)
**Response:** JWT access token + refresh token, same shape as every other login endpoint

## Frontend Implementation (as shipped)

### 1. `demoLogin()` helper

**File:** [`frontend/src/lib/authApi.ts`](../../frontend/src/lib/authApi.ts)

Mirrors the existing `devLogin()` shortcut, which mints a session for a local-dev seeded user. Same
shape, but hits the demo endpoint (which works in every environment where `demo.enabled=true`, not
just local dev) and reuses the same `setTokens` helper every other login path uses:

```ts
export const DEMO_PHONE = "+919876543210";

export async function demoLogin(): Promise<AuthTokenResponse> {
  const res = await apiClient.post<AuthTokenResponse>("/auth/demo/login", undefined, { auth: false });
  setTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
  return res;
}
```

`DEMO_PHONE` is exported from the same file and reused wherever the frontend needs to detect a demo
session client-side (the top bar badge, the demo date-range default) — see §3 and §4 below.

### 2. "Try demo" buttons on the Landing page

**File:** [`frontend/src/components/landing/Landing.tsx`](../../frontend/src/components/landing/Landing.tsx)

Two entry points, both calling the same handler:

- Header: a "Try demo" button (`PlayCircle` icon), which replaced the header's previous "Sign in"
  link — that link pointed at the same `/login` page as the adjacent "Get started" button, so it
  was redundant.
- Hero CTA row: a "Try the demo" button alongside the existing "Get started" call to action.

```tsx
const [demoBusy, setDemoBusy] = useState(false);
const [demoError, setDemoError] = useState<string | null>(null);

async function onTryDemo() {
  setDemoError(null);
  setDemoBusy(true);
  try {
    await demoLogin();
    router.replace("/dashboard");
  } catch (err) {
    setDemoError(demoErrorMessage(err));
    setDemoBusy(false);
  }
}
```

Both buttons disable themselves while `demoBusy` is true (label switches to "Loading…" / "Loading
demo…") and a dismissible inline error banner renders below the hero CTA row on failure:
`"Demo account unavailable. Please try again or sign up for a regular account."`

### 3. Demo badge in the top bar

**File:** [`frontend/src/components/shared/TopBar.tsx`](../../frontend/src/components/shared/TopBar.tsx)

Once logged in, the top bar shows a persistent "Demo account" badge whenever the session belongs to
the demo user — detected by comparing the logged-in user's `phone` (from the already-fetched
`/users/me` profile) against `DEMO_PHONE`:

```tsx
const { data: profile } = useApi<Profile>("/users/me");
const isDemo = profile?.phone === DEMO_PHONE;

{isDemo && (
  <span className="...">
    <PlayCircle className="size-3.5" /> Demo account
  </span>
)}
```

This shares the SWR cache key with `UserMenu`'s own `/users/me` fetch, so the badge costs no extra
network request.

### 4. Demo date-range default

**File:** [`frontend/src/lib/date-range.tsx`](../../frontend/src/lib/date-range.tsx)

The demo CSV is static (July 2025 – June 2026) and never re-uploaded, so the default "this month"
range would eventually show a blank dashboard once real wall-clock time drifts past the CSV's last
month. `DateRangeProvider` detects a demo session (again via `/users/me`'s `phone`) and applies a
hardcoded `DEMO_RANGE` exactly once, via a `useEffect` guarded by a `useRef` so it never fights the
user's own range selection after the first load:

```tsx
const DEMO_RANGE: DateRange = {
  preset: "custom",
  from: "2025-07-01",
  to: "2026-06-30",
  label: "Jul 2025 – Jun 2026",
};

useEffect(() => {
  if (!appliedDemoDefault.current && profile?.phone === DEMO_PHONE) {
    appliedDemoDefault.current = true;
    setRange(DEMO_RANGE);
  }
}, [profile]);
```

**Keep in sync:** `DEMO_RANGE` must match the backend's `demo.frozen-month`
(`backend/src/main/resources/application.yml`), which pins `BudgetServiceImpl`'s notion of "current
month" for the demo user to the CSV's last populated month. If the CSV is ever regenerated with a
different date range, update both together — see
[demo-feature-complete.md § Frozen-Month Budgets](./demo-feature-complete.md#5-frozen-month-budgets-keeping-a-static-csvs-budgets-non-blank).

Real users are unaffected in every case above: `demoLogin()` is an isolated function nobody else
calls, the top-bar badge and the date-range effect both gate on `profile?.phone === DEMO_PHONE`,
which is never true for a real account.

## Demo Login Flow Diagram

```
Landing Page
    │
    ▼
[Try demo] button (header or hero CTA)
    │
    ▼
demoLogin() → POST /api/v1/auth/demo/login (no body, auth: false)
    │
    ▼
Backend: DemoAuthController → UserAccountService.findOrCreateByPhone
    → UserJwtService + RefreshTokenService → tokens
    │
    ▼
setTokens() stores accessToken + refreshToken (same as every other login path)
    │
    ▼
router.replace("/dashboard")
    │
    ▼
DateRangeProvider detects demo phone → applies DEMO_RANGE once
TopBar detects demo phone → shows "Demo account" badge
    │
    ▼
Dashboard displays seeded demo data (522 transactions, 4 budgets with non-blank
progress via the frozen-month mechanism, 4 contacts, 5 upcoming EMIs, 4 alerts,
4 recommendations)
```

## What's Pre-loaded in Demo Account

See [demo-feature-complete.md](./demo-feature-complete.md) for the authoritative, verified list —
summary:

### Transactions

- **522 realistic transactions** spanning July 2025 – June 2026
- Monthly salary, EMI, subscriptions, daily spending, transfers
- Mix of food, travel, shopping, groceries, etc.
- Categorized via a curated-category overlay (see "Known Issue: Live ML Model Bias" in
  demo-feature-complete.md) rather than live ML output alone

### Budgets (frozen to the CSV's last month, not the real calendar month)

- Food / Dine Out: ₹10,000
- Travel: ₹7,000
- Transfers: ₹10,000
- Shopping: ₹5,000

### Contacts (Family & Friends)

- Rahul Sharma (Friend)
- Priya Verma (Family)
- Amit Kumar (Friend)
- Shreya Patel (Family)

### EMIs (5, with due days so they surface on the dashboard)

Car Loan EMI, Spotify Premium, Netflix Subscription, Electricity Bill, Gas Bill.

### Alerts (4, one of every type used in the UI)

`CATEGORY_APPROACHING_LIMIT` (Shopping), `CATEGORY_OVERSPEND` (Food), `MID_MONTH_BUDGET` (already
read), `RECURRING_PAYMENT` (Spotify — "Confirm" creates a real EMI).

### Recommendations (4)

Three category-specific (Shopping, Travel, Food), one global (salary savings suggestion).

### Features Available

- View all transactions (categorized, grouped, filtered)
- Correct miscategorized transactions
- View budget progress vs. actuals (non-blank regardless of when the demo is viewed)
- Analytics (spending trends, category breakdown)
- Export transactions as CSV/PDF
- Chat with chatbot (using demo data context)
- View alerts and notifications (mix of read/unread)
- View recommendations
- Manage EMI/recurring payments
- Edit contacts

## Error Handling

`onTryDemo()` in `Landing.tsx` catches any failure from `demoLogin()` and shows an inline error:
`"Demo account unavailable. Please try again or sign up for a regular account."` (see
`demoErrorMessage()` in `Landing.tsx`).

## Testing

### Manual Test Checklist (verified 2026-07-10)

1. Click "Try demo" on the landing page (header or hero)
2. Redirected to `/dashboard` without entering any credentials
3. Transactions are visible (522 rows across the demo's date range)
4. Budgets are visible with non-blank progress
5. Contacts are visible
6. Upcoming EMIs card shows 5 EMIs
7. Alerts bell shows a mix of read/unread states
8. Recommendations are visible
9. Can view analytics/trends
10. Can correct a transaction category
11. Can export data
12. "Demo account" badge shows in the top bar
13. Logging out and logging back in works normally

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
    "id": "<dynamically-generated-uuid>",
    "phone": "+919876543210",
    "email": "demo@spendwise.local"
  }
}
```

> The demo user's `id` is generated dynamically by `findOrCreateByPhone` on first creation — never a
> fixed constant. Don't hardcode it anywhere; resolve by phone if you need it in a script or query.

## Disabling Demo (Production)

To disable demo login in production, configure the backend:

```yaml
# application.yml or env var
demo:
  enabled: false  # or DEMO_ENABLED=false
```

This skips demo data seeding on startup and stops `DemoAuthController` from creating the demo user
on demand. The endpoint routes still exist (they're compiled in) but calling `/auth/demo/login`
would create-or-find a "demo" user even with seeding disabled, since the controller's
`findOrCreateByPhone` call doesn't check `demo.enabled` itself — if fully disabling the login
endpoint (not just seeding) is needed, that would require an explicit guard in
`DemoAuthController`, which does not currently exist.

## FAQ

**Q: Can demo users edit data?**
A: Yes, they can edit everything (correct categories, update contacts, etc.) just like real users.
This lets them explore the full app.

**Q: Does demo data persist?**
A: Yes, until explicitly deleted. Seeding is idempotent — it only runs once, the first time the demo
user doesn't exist. No scheduled reset job exists yet (see demo-feature-complete.md's Future
Enhancements).

**Q: Can I customize demo data?**
A: Yes. Regenerate `data/demo-transactions.csv` with different patterns, then redeploy the backend.
If the date range changes, update `demo.frozen-month` (backend `application.yml`) and `DEMO_RANGE`
(`frontend/src/lib/date-range.tsx`) together, and revisit the hand-picked EMI/alert/recommendation
values in `DemoDataSeeder`, which reference specific CSV merchants and figures.

**Q: What if someone wants to keep exploring after demo?**
A: They can sign up for a real account. No demo-to-real upgrade path exists yet (tracked as a future
enhancement in demo-feature-complete.md).
