# Epic 10 — Web Dashboard (Next.js)

**Working Milestone:** Full dashboard demoable in a browser end-to-end against the
backend — login, dashboard, transactions, budget, EMI/subscriptions, chatbot, export,
settings — including the stale-data fallback behavior demoed by killing the backend mid-session.

Like Epic 9, this epic's stories are gated per-story by their backend counterpart, not as
one block. See `../DEPENDENCY-GRAPH.md`'s "E10 per-story dependency" table.

---

### E10-S1 — App Shell & Auth

**Depends on:** Epic 1.

#### E10-S1-T1 — Routes, layout, Firebase client login

- **Objective:** Stand up the app's route structure and wire Firebase client SDK for phone
  OTP / Google login.
- **Expected Deliverable:** Next.js App Router layout with routes for dashboard,
  transactions, budget, EMI, chatbot, export, settings; a login page using
  `NEXT_PUBLIC_FIREBASE_*` env vars, exchanging the Firebase credential for a SpendWise JWT
  via `/auth/otp/verify` or `/auth/google` (never using the Firebase ID token directly as
  the backend session, per `CLAUDE.md`'s Auth pattern note).
- **Definition of Done:** Successful login stores the SpendWise access+refresh token and
  redirects to the dashboard; failed login shows an inline error.
- **Required Tests:** Component test for the login form's success/error states (mocked API).
- **Estimated Complexity:** Large
- **Depends on:** E1-S1-T3, E1-S1-T4
- **Grounded in:** `CLAUDE.md` Auth pattern; `docs/operations/deployment.md` Next.js env vars.

#### E10-S1-T2 — Client-side token storage, refresh, protected routes

- **Objective:** Keep the session alive across page loads and protect authenticated routes.
- **Expected Deliverable:** Token storage (e.g., httpOnly-cookie-backed or secure client
  storage), a request wrapper that refreshes via `/auth/token/refresh` on 401, and a
  route guard redirecting unauthenticated users to login.
- **Definition of Done:** A token nearing/at expiry is silently refreshed without the user noticing; an unauthenticated direct navigation to `/dashboard` redirects to login.
- **Required Tests:** Component/unit test for the refresh-on-401 interceptor logic.
- **Estimated Complexity:** Medium
- **Depends on:** E10-S1-T1
- **Grounded in:** `docs/spec/security.md` Authentication & Authorization (rotation); `docs/operations/user_flows.md` Multi-Device Flow.

---

### E10-S2 — Dashboard Views

Each task is independently gated by its own backend epic — see `../DEPENDENCY-GRAPH.md`.

#### E10-S2-T1 — Dashboard page

- **Objective:** Primary landing view mirroring Android's dashboard (alerts, recs, category summary, budget bars, trend charts).
- **Expected Deliverable:** Page composing `/alerts`, `/recommendations`, `/budgets/progress`, `/analytics/trends`, using a charting library under `src/components/charts`.
- **Definition of Done:** All sections render from live data; charts render correctly for at least line (trend) and bar/progress (budget) types.
- **Required Tests:** Component tests for each dashboard section with mocked API responses.
- **Estimated Complexity:** Large
- **Depends on:** E5-S1-T3, E7-S1-T4, E8-S2-T2, E5-S4-T1
- **Grounded in:** `docs/operations/user_flows.md` "Reviewing Transactions".

#### E10-S2-T2 — Transactions page

- **Objective:** Browse, filter, and correct transaction categories on the web.
- **Expected Deliverable:** Paginated table/list, filters, detail view, category-correction control.
- **Definition of Done:** Pagination, filtering, and correction all reflect immediately without a full page reload.
- **Required Tests:** Component tests for pagination and filter state; one test for the category-correction flow (mocked API).
- **Estimated Complexity:** Large
- **Depends on:** E3-S2-T1, E3-S2-T2, E3-S2-T4
- **Grounded in:** `docs/operations/user_flows.md` "Browsing the Transaction List" + "Correcting a Category".

#### E10-S2-T3 — Budget page

- **Objective:** View/edit budgets with suggestions, mirroring Android's budget screen.
- **Expected Deliverable:** Budget list with progress bars, edit form, suggestion prompt.
- **Definition of Done:** Same as Android's E9-S2-T3 Definition of Done, web equivalent.
- **Required Tests:** Component test for the edit form and suggestion-accept flow.
- **Estimated Complexity:** Medium
- **Depends on:** E5-S1-T1, E5-S1-T4
- **Grounded in:** `docs/operations/user_flows.md` "Setting / Editing a Budget".

#### E10-S2-T4 — EMI/Subscriptions page

- **Objective:** View/manage EMIs and subscriptions on the web.
- **Expected Deliverable:** List, edit, deactivate actions against `/emis`.
- **Definition of Done:** Same as Android's E9-S2-T4, web equivalent.
- **Required Tests:** Component test for deactivate flow.
- **Estimated Complexity:** Medium
- **Depends on:** E3-S3-T1, E3-S3-T2
- **Grounded in:** `docs/operations/user_flows.md` "EMI / Subscriptions Management".

#### E10-S2-T5 — Chatbot page

- **Objective:** New/resume chat sessions on the web.
- **Expected Deliverable:** Session list/new-session/message thread UI under `src/components/chatbot`.
- **Definition of Done:** Same as Android's E9-S2-T5, web equivalent.
- **Required Tests:** Component test for sending a message and rendering the response; test for resuming a session's history.
- **Estimated Complexity:** Large
- **Depends on:** E8-S3-T1, E8-S3-T2
- **Grounded in:** `docs/operations/user_flows.md` "Chatbot Interaction".

#### E10-S2-T6 — Export page

- **Objective:** Let a user export PDF/CSV reports for a custom range or full financial year.
- **Expected Deliverable:** Date-range picker + format selector, triggering a download from `/analytics/export/{pdf,csv}`.
- **Definition of Done:** Both formats download correctly for both a custom range and a full financial year selection.
- **Required Tests:** Component test for the range-picker validation (end date before start date rejected, etc.).
- **Estimated Complexity:** Medium
- **Depends on:** E7-S2-T1, E7-S2-T2
- **Grounded in:** `docs/operations/user_flows.md` "Exporting a Report".

#### E10-S2-T7 — Settings page

- **Objective:** Manage preferences and account actions on the web.
- **Expected Deliverable:** Preferences form, logout action, privacy policy link.
- **Definition of Done:** Same as Android's E9-S2-T6, web equivalent.
- **Required Tests:** Component test for the preferences form save flow.
- **Estimated Complexity:** Small
- **Depends on:** E1-S3-T2, E1-S1-T6
- **Grounded in:** `docs/spec/security.md` Privacy Policy requirement.

---

### E10-S3 — Offline/Stale Handling

#### E10-S3-T1 — Client-side cache fallback with stale indicator

- **Objective:** Serve last-fetched data with a visible stale indicator when the backend is unreachable — no server-side cache, per the architecture note.
- **Expected Deliverable:** A client-side caching layer (browser storage / in-memory React
  state, per `docs/spec/architecture.md`'s explicit note that no server-side cache is used) that
  intercepts failed requests and falls back to the last successful response, rendering a stale-data banner.
- **Definition of Done:** Killing the backend mid-session on the dashboard page continues to
  show the last-loaded data with a visible "stale" indicator, rather than an error screen or blank page.
- **Required Tests:** Component test simulating a failed fetch after a successful one — asserts stale data + indicator both render.
- **Estimated Complexity:** Medium
- **Depends on:** E10-S2-T1
- **Grounded in:** `docs/spec/architecture.md` "Web dashboard offline behavior" note; `docs/spec/requirements.md` Availability ("Web dashboard: caching layer serves stale data when backend is unavailable").

---

## Parallel Execution within Epic 10

- E10-S2's seven pages are independent of each other once E10-S1 lands — ideal for parallel work.
- E10-S3 depends only on E10-S2-T1 (dashboard) existing as the primary place stale-data
  matters most, though the same pattern should be reused across other pages once proven.
- The whole epic is parallelizable with Epic 9.
