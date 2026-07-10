# Epic 8 — Recommendations & Chatbot

**Working Milestone:** A synthetic threshold-crossing scenario (e.g., this month's Food
spend 38% above last month) produces a dismissible recommendation card via
`GET /recommendations`; dismissing it removes it from the active feed. A new chatbot
session answers "How much did I spend on food last month?" grounded in seeded transaction
data; resuming a prior session shows full history.

---

### E8-S1 — LLM Provider Abstraction

**Independently testable via:** unit tests against the interface with a stub implementation — no real LLM vendor call needed for tests.

#### E8-S1-T1 — `LlmClient` interface + stub implementation

- **Objective:** Define a vendor-neutral LLM interface so Recommendations and Chatbot never
  hardcode a specific SDK, per `CLAUDE.md`'s explicit instruction.
- **Expected Deliverable:** An interface (e.g., `LlmClient.complete(prompt, context) -> response`)
  with a config-driven provider selection point (env-var-based), and a deterministic stub/mock
  implementation used in local dev and all automated tests.
- **Definition of Done:** No class outside this abstraction layer imports a vendor-specific
  LLM SDK; swapping the stub for a real provider requires touching only this layer's
  implementation, not Recommendations/Chatbot business logic.
- **Required Tests:** An architecture-style test asserting no vendor SDK import exists outside the designated package; unit test exercising the stub's deterministic response shape.
- **Estimated Complexity:** Medium
- **Depends on:** E0-S1-T1
- **Grounded in:** `CLAUDE.md` "LLM: Provider intentionally abstracted... Do not hardcode any LLM SDK into business logic"; `docs/spec/requirements.md` AI Chatbot section; `docs/system-diagram.md` `LLMSVC` (vendor-abstracted, TBD).

---

### E8-S2 — Recommendations

**Independently testable via:** integration tests invoking the job method directly against a seeded fixture.

#### E8-S2-T1 — Recommendation generator job (every 6 hours, idempotent)

- **Objective:** Generate LLM one-liners only when a spending threshold has been crossed
  since the last generation for that user+category, without duplicating.
- **Expected Deliverable:** `@Scheduled` job reading Analytics aggregations (Recommendations
  may only call Analytics, per the module dependency table), comparing transaction/budget
  timestamps against the module's own last-run time, generating a recommendation via
  `LlmClient`, and suppressing duplicates by checking `generated_at` on the most recent
  active record per user per category (backed by `idx_recs_user_category_active`).
- **Definition of Done:**
  - A fixture with a genuine new threshold-crossing produces exactly one new active
    recommendation for that user+category.
  - Running the job again with no new threshold-crossing produces no duplicate.
  - The job never calls Alerts, Chatbot, Ingest, or Categorization (module dependency rule).
- **Required Tests:** Integration test: threshold-crossing fixture → one recommendation
  created; re-run with no change → no duplicate (per `docs/spec/architecture.md`'s idempotency
  description); unit test asserting no forbidden module calls occur (mock verification).
- **Estimated Complexity:** Large
- **Depends on:** E8-S1-T1, E7-S1-T1
- **Grounded in:** `docs/spec/architecture.md` Background Jobs table (Recommendation generator, full idempotency description) + module dependency table (Recommendations: may call Analytics; must not call Alerts/Chatbot/Ingest/Categorization); `docs/spec/database.md` `recommendations` + `idx_recs_user_category_active`.

#### E8-S2-T2 — `GET /recommendations`, `PUT /recommendations/:id/dismiss`

- **Objective:** Serve the active recommendations feed and support dismissal.
- **Expected Deliverable:** Both endpoints against the `recommendations` table.
- **Definition of Done:** Feed ordered by `generated_at DESC` (using `idx_recs_user_active`);
  dismiss sets `is_dismissed = true` and removes it from the default feed query.
- **Required Tests:** Integration test: dismiss then re-fetch feed excludes it; feed ordering matches expected recency order.
- **Estimated Complexity:** Small
- **Depends on:** E8-S2-T1
- **Grounded in:** `docs/spec/api.md` `/recommendations` table; `docs/operations/user_flows.md` "Viewing Savings Recommendations" flow.

---

### E8-S3 — Chatbot

**Independently testable via:** integration tests against a seeded conversation fixture.

#### E8-S3-T1 — Session endpoints

- **Objective:** Support creating, listing, and fetching chat sessions.
- **Expected Deliverable:** `POST /chatbot/sessions`, `GET /chatbot/sessions` (ordered by
  `last_active_at DESC`), `GET /chatbot/sessions/:id` (full history).
- **Definition of Done:** New session appears first in the list; fetching a session's
  history returns messages in chronological order; a user cannot fetch another user's session.
- **Required Tests:** Integration test: create 2 sessions, confirm list order; confirm cross-user access is rejected (404, not leaking existence).
- **Estimated Complexity:** Small
- **Depends on:** E0-S2-T5, E1-S1-T7
- **Grounded in:** `docs/spec/api.md` `/chatbot` table + "Session lifecycle" note; `docs/spec/database.md` `chatbot_sessions`.

#### E8-S3-T2 — `POST /chatbot/message` with context injection

- **Objective:** Answer a user's question grounded in their actual transaction history.
- **Expected Deliverable:** Endpoint that, given `sessionId` + message, reads relevant
  Transaction history and Analytics summaries (Chatbot's only permitted reads, per the
  module dependency table — never a module that writes data), builds a context payload,
  calls `LlmClient`, and persists both the user message and assistant response to
  `chatbot_conversations`.
- **Definition of Done:** For a seeded fixture ("₹3,240 on Food in May across 14 transactions"-style data), the stub `LlmClient`'s response (deterministic in tests) demonstrably received the correct grounding context; both messages persist with correct `role` values and survive re-fetching the session.
- **Required Tests:** Integration test: send a message, assert both `user` and `assistant`
  rows exist in `chatbot_conversations` in the right order; assert the context payload
  passed to `LlmClient` contains the expected transaction data (via a test double that
  captures its input).
- **Estimated Complexity:** Large
- **Depends on:** E8-S3-T1, E8-S1-T1, E3-S2-T1, E7-S1-T1
- **Grounded in:** `docs/spec/api.md` `/chatbot` table; `docs/spec/architecture.md` Chatbot module dependency row; `docs/spec/database.md` `chatbot_conversations`; `docs/operations/user_flows.md` Chatbot Interaction flow.

---

## Parallel Execution within Epic 8

- E8-S1-T1 has no dependency beyond Epic 0 and should be built first, in parallel with
  Epic 7 if capacity allows.
- E8-S2 (Recommendations) and E8-S3 (Chatbot) are independent of each other once E8-S1-T1 lands.
