# Demo Feature — Deployed & Verified (Local, Backend + Frontend) ✅

## Summary

**Backend and frontend for the demo account feature are complete and verified end-to-end locally.**
Beyond the three build/wiring defects found during the initial deployment pass, this session also
found and fixed a real ML model-quality issue, added a frozen-month mechanism so budgets never go
blank, and seeded EMIs/alerts/recommendations through their real service methods — see "What Was
Actually Found & Fixed" below for the full list.

---

## What Was Actually Found & Fixed During Deployment

The demo code as originally written did **not** build or work as-is. Four real issues surfaced
during this deployment pass, plus additional feature gaps closed once the backend was verified
working:

1. **Lombok isn't a dependency in this project.** All three demo files (`CsvTransactionParser`,
   `DemoDataSeeder`, `DemoAuthController`) used `@Slf4j`/`@RequiredArgsConstructor`, but no other
   file in the codebase uses Lombok — the build failed with `package lombok does not exist`.
   Rewrote all three with explicit constructors and `LoggerFactory.getLogger(...)`, matching the
   codebase's existing pattern (see `DevAuthController` for the reference shape).

2. **`DemoDataSeeder` used raw `JdbcTemplate` inserts, bypassing every real service** —
   `UserAccountService`, `IngestService` (and therefore ML categorization), `BudgetService`,
   `ContactService`. This violated CLAUDE.md's "reuse, don't rewrite" principle and meant demo
   transactions never went through the real categorization pipeline at all. Rewrote it to call the
   actual service interfaces — demo transactions now go through the identical path a real Android
   device sync takes.

3. **`/api/v1/auth/demo/login` returned 403.** `SecurityConfig`'s `defaultFilterChain` didn't have
   the new demo routes in its `permitAll` list. Added `/api/v1/auth/demo/login` and
   `/api/v1/auth/demo/info` alongside the other public `/auth/*` routes.

4. **Live ML classifier bias** — not a code bug, a data/model quality finding (see
   [docs/demo/demo-feature-complete.md § Known Issue](docs/demo/demo-feature-complete.md#known-issue-live-ml-model-bias)):
   the trained classifier predicts "Transfers" for ~89% of demo transactions at barely-passing
   confidence. Fixed by overlaying the CSV's curated `category` column onto the seeded transactions
   via `TransactionService.correctCategory` — the same correction path a real user takes — after
   live ingest/categorization runs. This was a judgment call confirmed with the user rather than
   assumed.

5. **Budgets would go blank once real time drifted past the CSV's date range.** The demo CSV is
   static and never re-uploaded, but `BudgetServiceImpl` computed spend against `YearMonth.now()`
   like every real user. Added `demo.frozen-month` (config) + `common/demo/DemoUserRegistry`
   (a narrow, deliberate holder bean — see `docs/spec/architecture.md`'s module-communication
   addendum) so `BudgetServiceImpl.resolveMonth(userId)` pins the demo user's "current month" to a
   fixed value while every other user is unaffected (verified via a dev-login test user still
   resolving to `YearMonth.now()`).

6. **Alerts / Upcoming EMIs / Recommendations cards would be permanently blank in the demo.** The
   real recurring-payment detector and alert/recommendation evaluators are `@Scheduled` jobs keyed
   to the real wall clock — structurally incapable of ever firing against the static demo CSV.
   Seeded 5 EMIs, 4 alerts (one of every type, mixed read/unread), and 4 recommendations directly
   through `EmiService`, `AlertsService`, and `RecommendationsService`'s real methods — never raw
   SQL. This is demo-only seeding, parallel to (not a replacement for) the real, untouched
   recurring-detection/alert/recommendation systems, which continue to work automatically for real
   users.

7. **Frontend had no way to reach the demo account.** Added `demoLogin()` + `DEMO_PHONE` to
   `frontend/src/lib/authApi.ts`, "Try demo" buttons to `Landing.tsx`, a "Demo account" badge to
   `TopBar.tsx`, and a hardcoded `DEMO_RANGE` date-range default to `date-range.tsx` so the
   dashboard's default view lines up with the CSV's actual coverage instead of "this month."

---

## Verified State (2026-07-10, local — backend + browser)

```text
demo_user | transactions | budgets | contacts | emis | alerts | recommendations | categorized
        1 |          522 |       4 |        4 |    5 |      4 |                4 |         522
```

Category spread: Food 171, Travel 155, Groceries 91, Transfers 41, Shopping 23, Subscriptions 14,
Miscellaneous 13, Fees & Debt 12, Medical 2.

`POST /api/v1/auth/demo/login` → `200 OK`, tokens verified against `GET /api/v1/transactions`,
`GET /api/v1/budgets`, `GET /api/v1/contacts`. Browser click-through confirmed: landing page
"Try demo" → dashboard loads with non-blank budget progress, 5 upcoming EMIs, 4 alerts (3 unread,
1 read), 4 recommendations, and the "Demo account" badge in the top bar.

---

## What's Next

### QA

Note: [docs/demo/demo-deployment-checklist.md](docs/demo/demo-deployment-checklist.md) still
assumes a hardcoded demo user UUID and raw-SQL seeding from the original (pre-deployment) design —
both are no longer accurate and the checklist needs a follow-up pass before being used for QA.

### Production Deployment

- Confirm `FASTAPI_ML_URL` and `ML_INTERNAL_KEY` are set in the deployed environment — the demo
  seeder calls the real ML service on startup exactly like a production ingest would.
- Confirm `demo.frozen-month` (backend) is set and stays in sync with the frontend's `DEMO_RANGE`
  (`frontend/src/lib/date-range.tsx`) — both currently assume the CSV covers Jul 2025 – Jun 2026.

### Future Enhancements (see docs/demo/demo-feature-complete.md for the full list)

- Scheduled reset job, demo-to-real upgrade path, multiple demo personas
- Re-evaluate the curated-category overlay once the classifier is retrained

---

## Files Changed This Session

### Backend

- `backend/src/main/java/com/spendwise/transaction/util/CsvTransactionParser.java` — Lombok removed; added `parseCategoryOverrides()`
- `backend/src/main/java/com/spendwise/ingest/DemoDataSeeder.java` — rewritten to use real services; added EMI/alert/recommendation seeding
- `backend/src/main/java/com/spendwise/auth/DemoAuthController.java` — rewritten to match `DevAuthController`'s pattern
- `backend/src/main/java/com/spendwise/auth/SecurityConfig.java` — added demo routes to `permitAll`
- `backend/src/main/java/com/spendwise/common/demo/DemoUserRegistry.java` — new: narrow demo-user marker bean
- `backend/src/main/java/com/spendwise/budget/BudgetServiceImpl.java` — added `resolveMonth(userId)` frozen-month logic
- `backend/src/main/resources/application.yml` — added `demo.*` config block (`enabled`, `phone`, `email`, `frozen-month`)

### Frontend

- `frontend/src/lib/authApi.ts` — added `demoLogin()`, exported `DEMO_PHONE`
- `frontend/src/components/landing/Landing.tsx` — added "Try demo" buttons, removed redundant "Sign in" link
- `frontend/src/components/shared/TopBar.tsx` — added "Demo account" badge
- `frontend/src/lib/date-range.tsx` — added hardcoded `DEMO_RANGE` default for demo sessions

### Docs

- `docs/demo/demo-feature-complete.md` — rewritten to describe the actual (not aspirational) architecture, the frozen-month mechanism, EMI/alert/recommendation seeding, and the ML bias finding
- `docs/demo/demo-login-integration.md` — rewritten to document the real frontend implementation (previously generic/aspirational sample code)
- `docs/spec/architecture.md` — added a module-communication addendum documenting `DemoUserRegistry` as a deliberate, narrow exception
- `DEMO_STATUS.md` — this file

**Status:** 🟢 Backend and frontend verified — demo feature fully deployed locally
**Last Updated:** 2026-07-10
