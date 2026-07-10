# Epic 9 — Android App UI

**Working Milestone:** The full onboarding-to-dashboard flow runs on an emulator/device
against a running backend: sign up → consent → permissions → questionnaire → optional bank
statement upload → SMS backfill → dashboard, followed by working Transactions, Budget,
EMI/Subscriptions, Chatbot, and Settings screens.

Unlike earlier epics, this epic's stories are **not gated as one block** — each story only
depends on its corresponding backend epic being demoable. Start a story the moment its
dependency lands; see the per-story table in `../DEPENDENCY-GRAPH.md`.

---

### E9-S1 — Onboarding UI

**Depends on:** Epic 1 (auth + onboarding endpoints), Epic 2 (parser/sync/backfill already built headless in this codebase).

**Independently testable via:** manual run-through on an emulator plus instrumented tests where feasible.

#### E9-S1-T1 — Sign-up screen (Phone OTP / Google login)

- **Objective:** Let a new user authenticate via either method.
- **Expected Deliverable:** UI screen calling `/auth/otp/send` + `/auth/otp/verify` or
  `/auth/google`, storing the returned access/refresh tokens securely on-device.
- **Definition of Done:** Both paths result in a stored valid session; invalid OTP shows an inline error, not a crash.
- **Required Tests:** Manual QA checklist (Espresso deferred per `docs/operations/testing.md`); a
  unit test for the token-storage helper (e.g., writes to `EncryptedSharedPreferences` or equivalent).
- **Estimated Complexity:** Medium
- **Depends on:** E1-S1-T3, E1-S1-T4
- **Grounded in:** `docs/operations/user_flows.md` Onboarding step 2; `docs/operations/testing.md` "Android UI (Espresso) — Deferred".

#### E9-S1-T2 — Consent screen (single mandatory gate)

- **Objective:** Present the single all-or-nothing DPDP consent screen; block progress without acceptance.
- **Expected Deliverable:** Screen listing all 3 consent purposes (SMS read, server storage,
  ML training), a link to the privacy policy, and a submit action calling
  `/users/me/onboarding` with the consent text snapshot.
- **Definition of Done:** Declining exits/blocks the flow (app is non-functional without
  consent, per ADR-005); accepting proceeds and the exact consent text shown is what's persisted server-side.
- **Required Tests:** Manual QA checklist; unit test confirming the consent text constant sent to the API matches what's rendered.
- **Estimated Complexity:** Medium
- **Depends on:** E9-S1-T1, E1-S3-T3
- **Grounded in:** `docs/operations/user_flows.md` Onboarding step 3; `docs/spec/decisions.md` ADR-005; `docs/spec/security.md` Consent at Onboarding.

#### E9-S1-T3 — SMS + notification permission flow

- **Objective:** Request `READ_SMS` and notification permissions with the correct denial behavior.
- **Expected Deliverable:** Permission request screens; a blocking screen with a deep link
  to Android settings if SMS is denied (app cannot proceed); if notifications are denied,
  proceed but note that alerts will be in-app only.
- **Definition of Done:** Denying SMS shows the blocking screen and cannot be bypassed;
  denying notifications allows continuing.
- **Required Tests:** Manual QA checklist covering both denial paths.
- **Estimated Complexity:** Medium
- **Depends on:** E9-S1-T2
- **Grounded in:** `docs/operations/user_flows.md` Onboarding steps 4-5 + "SMS Permission Denied" edge case.

#### E9-S1-T4 — Onboarding questionnaire + optional bank statement upload

- **Objective:** Collect apps/banks/spend estimate and offer an optional bank statement upload.
- **Expected Deliverable:** Questionnaire UI calling `/users/me/preferences`; file-picker
  upload calling `POST /users/me/bank-statement` (skippable).
- **Definition of Done:** Skip path proceeds cleanly; upload path shows a success state once
  the backend confirms ingestion (backend PDF-parsing itself is out of this epic's scope —
  covered by the Ingest module in Epic 3/CLAUDE.md's bank-statement flow, assumed available by this point).
- **Required Tests:** Manual QA checklist for both skip and upload paths.
- **Estimated Complexity:** Medium
- **Depends on:** E9-S1-T3
- **Grounded in:** `docs/operations/user_flows.md` Onboarding steps 6-7; `docs/spec/api.md` `/users/me/bank-statement`.

#### E9-S1-T5 — Backfill trigger, foreground service start, land on dashboard

- **Objective:** Trigger the first-launch SMS backfill (built headless in Epic 2), then
  start the foreground monitoring service, then navigate to the dashboard.
- **Expected Deliverable:** Wiring that calls Epic 2's backfill entry point after
  permissions are granted, shows a brief progress indicator, then transitions.
- **Definition of Done:** On a device/emulator with seeded test SMS messages, the backfill
  visibly completes and the user lands on the dashboard with the foreground service running.
- **Required Tests:** Manual QA checklist on an emulator with seeded SMS via `adb emu sms send`.
- **Estimated Complexity:** Medium
- **Depends on:** E9-S1-T4, E2-S5-T3
- **Grounded in:** `docs/operations/user_flows.md` Onboarding steps 8-10; `docs/spec/architecture.md` First-Launch SMS Inbox Backfill.

---

### E9-S2 — Core Screens

Each task below is independently gated by its own backend epic — see
`../DEPENDENCY-GRAPH.md`'s "E9 per-story dependency" table.

#### E9-S2-T1 — Dashboard screen

- **Objective:** Show the primary landing view: alerts panel, recommendations feed, category summary, budget progress bars, trend chart.
- **Expected Deliverable:** Screen composing `/alerts`, `/recommendations`, `/budgets/progress`, `/analytics/trends`.
- **Definition of Done:** All four sections render from live data against a seeded backend; tapping a category navigates to a drilldown (wired to E9-S2-T2).
- **Required Tests:** Manual QA checklist against a seeded backend.
- **Estimated Complexity:** Large
- **Depends on:** E5-S1-T3 (progress), E7-S1-T4 (trends), E8-S2-T2 (recs), E5-S4-T1 (alerts)
- **Grounded in:** `docs/operations/user_flows.md` "Reviewing Transactions" flow.

#### E9-S2-T2 — Transactions screen

- **Objective:** Browse, filter, and correct transaction categories.
- **Expected Deliverable:** Paginated list (`GET /transactions`), filter controls
  (date range, category), detail view (`GET /transactions/:id`), "Change Category" action
  (`PUT /transactions/:id/category`).
- **Definition of Done:** Pagination loads more on scroll; category correction reflects immediately in the list.
- **Required Tests:** Manual QA checklist; unit test for the pagination cursor-handling logic in the view model.
- **Estimated Complexity:** Large
- **Depends on:** E3-S2-T1, E3-S2-T2, E3-S2-T4
- **Grounded in:** `docs/operations/user_flows.md` "Browsing the Transaction List" + "Correcting a Category".

#### E9-S2-T3 — Budget screen

- **Objective:** View and edit monthly budgets, with suggestions when available.
- **Expected Deliverable:** Screen listing budgets with progress bars, an edit flow calling
  `POST /budgets`, and a suggestion prompt sourced from `GET /budgets/suggestions`.
- **Definition of Done:** Accepting a suggestion pre-fills the edit form; saving persists and updates the progress bar immediately.
- **Required Tests:** Manual QA checklist.
- **Estimated Complexity:** Medium
- **Depends on:** E5-S1-T1, E5-S1-T4
- **Grounded in:** `docs/operations/user_flows.md` "Setting / Editing a Budget".

#### E9-S2-T4 — EMI/Subscriptions screen

- **Objective:** View and manage tracked EMIs and subscriptions.
- **Expected Deliverable:** Screen listing active EMIs (`GET /emis`), edit action (`PUT /emis/:id`), deactivate action (`PATCH /emis/:id`).
- **Definition of Done:** Deactivated entries disappear from the active list without deletion.
- **Required Tests:** Manual QA checklist.
- **Estimated Complexity:** Medium
- **Depends on:** E3-S3-T1, E3-S3-T2
- **Grounded in:** `docs/operations/user_flows.md` "EMI / Subscriptions Management".

#### E9-S2-T5 — Chatbot screen

- **Objective:** New/resume chat sessions with the AI assistant.
- **Expected Deliverable:** Session list (`GET /chatbot/sessions`), new-session action,
  message thread UI calling `POST /chatbot/message`.
- **Definition of Done:** Resuming a session loads full prior history; new messages append
  correctly and persist across app restarts.
- **Required Tests:** Manual QA checklist.
- **Estimated Complexity:** Large
- **Depends on:** E8-S3-T1, E8-S3-T2
- **Grounded in:** `docs/operations/user_flows.md` "Chatbot Interaction".

#### E9-S2-T6 — Settings screen

- **Objective:** Manage preferences, trigger export, log out.
- **Expected Deliverable:** Screen for alert-channel preferences (`PUT /users/me/preferences`),
  export action (date-range picker + PDF/CSV choice hitting the Analytics export endpoints),
  logout (`POST /auth/logout`), and a link to the privacy policy (accessible any time, per DPDP requirement).
- **Definition of Done:** Export downloads/shares a file; logout clears the local session and returns to sign-up.
- **Required Tests:** Manual QA checklist.
- **Estimated Complexity:** Medium
- **Depends on:** E1-S3-T2, E1-S1-T6, E7-S2-T1, E7-S2-T2
- **Grounded in:** `docs/operations/user_flows.md` "Exporting a Report"; `docs/spec/security.md` Privacy Policy requirement.

---

## Parallel Execution within Epic 9

- E9-S1's 5 tasks are mostly sequential (onboarding is a linear wizard) — limited internal parallelism.
- Once E9-S1 lands, all of E9-S2's six screens are independent of each other (different
  screens, different backend epics) and can be built by different people/sessions simultaneously.
- The whole epic is parallelizable with Epic 10 (different platform, same backend contracts).
