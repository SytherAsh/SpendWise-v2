# Epic 4 — ML Categorization Service

**Working Milestone:** An ingested transaction is automatically categorized end-to-end:
Ingest persists it → Categorization module calls FastAPI `/predict` → the predicted
`category_id` + `confidence_score` land in `transaction_categories` with
`assigned_by = 'ml'`. A manual retrain can be triggered (via Admin's service interface,
built out fully in Epic 11) and produces an updated model artifact; `/evaluate` produces
an accuracy report against the labeled dataset.

Stories S1-S2 (the FastAPI service itself) have **no dependency on the Spring Boot
backend** and should start as soon as Epic 0 lands, in parallel with Epic 3. Only S3
(backend integration) depends on Epic 3.

---

### E4-S1 — FastAPI Service Skeleton & Internal Auth

**Independently testable via:** `pytest` + `TestClient`, no Spring Boot needed.

#### E4-S1-T1 — `X-Internal-Key` middleware

- **Objective:** Ensure the ML service can never be called by anything except the
  Categorization module, per the internal-only invariant.
- **Expected Deliverable:** FastAPI middleware rejecting any request to `/predict`,
  `/retrain`, `/evaluate` that lacks a matching `X-Internal-Key` header (compared against `ML_INTERNAL_KEY`).
- **Definition of Done:** Request without the header, or with a wrong value, → `401`; `/health` remains exempt (used by uptime monitoring, unauthenticated per `docs/operations/deployment.md`).
- **Required Tests:** `pytest`: missing header → 401; wrong value → 401; correct value → passes through to the route handler.
- **Estimated Complexity:** Small
- **Depends on:** E0-S1-T4
- **Grounded in:** `docs/spec/architecture.md` FastAPI ML Service "Internal access only" note; `docs/spec/security.md` API Security Checklist last item; `docs/operations/deployment.md` `ML_INTERNAL_KEY`.

---

### E4-S2 — Feature Pipeline & Baseline Model

**Independently testable via:** `pytest` unit tests + the evaluation script against the provided dataset.

#### E4-S2-T1 — Preprocessing & feature extraction

- **Objective:** Convert a raw transaction dict into a feature vector for the classifier.
- **Expected Deliverable:** A preprocessing module extracting features from
  `recipient_name`, `upi_id`, `bank`, `transaction_mode`, `amount`, `note` — handling nulls
  gracefully (per the real-data nullability notes in `docs/spec/database.md`).
- **Definition of Done:** Given a transaction dict with all fields null except `amount`, the
  pipeline produces a valid feature vector without throwing.
- **Required Tests:** Unit tests per `docs/operations/testing.md` §2: each field extracted correctly;
  null `note`/`bank` handled; empty-ish transaction handled.
- **Estimated Complexity:** Medium
- **Depends on:** E4-S1-T1
- **Grounded in:** `docs/operations/testing.md` §2 Preprocessing pipeline / Feature extraction; `docs/spec/database.md` "Notes from Real Data" nullability facts.

#### E4-S2-T2 — Train baseline scikit-learn classifier

- **Objective:** Produce the initial model artifact trained on the 3-year labeled bank
  statement dataset, per ADR-003/ADR-004 (supervised, server-side, scikit-learn — not deep
  learning, not on-device).
- **Expected Deliverable:** `ml/training/train.py` script; committed model artifact at
  `MODEL_PATH` (per `docs/operations/deployment.md` — model is version-controlled, no external store at MVP).
- **Definition of Done:** Running `python training/train.py --output models/` reproducibly
  produces a loadable model artifact; the 12 categories from `docs/spec/requirements.md` are the
  full label set.
- **Required Tests:** A test that loads the committed artifact and confirms it predicts one
  of the 12 valid `category_id`s for a known-good sample input.
- **Estimated Complexity:** Large
- **Depends on:** E4-S2-T1
- **Grounded in:** `docs/spec/decisions.md` ADR-003, ADR-004; `docs/operations/deployment.md` "Model artifacts" note; `docs/spec/requirements.md` Transaction Categories.

#### E4-S2-T3 — `POST /predict`

- **Objective:** Serve category predictions.
- **Expected Deliverable:** Endpoint accepting the feature payload shown in
  `docs/operations/deployment.md`'s Backend Service Communication example, returning
  `{category_id, category_name, confidence}`.
- **Definition of Done:** Response schema matches exactly; confidence is a float in `[0,1]`.
- **Required Tests:** `pytest` with a mocked model: verify response schema per `docs/operations/testing.md` §2.
- **Estimated Complexity:** Small
- **Depends on:** E4-S2-T2
- **Grounded in:** `docs/operations/deployment.md` "Backend Service Communication" example request/response; `docs/spec/architecture.md` FastAPI endpoint table.

#### E4-S2-T4 — `POST /retrain`

- **Objective:** Trigger a batch retraining cycle using accumulated corrections.
- **Expected Deliverable:** Endpoint accepting a corrections dataset (baseline + `ml_corrections`
  rows, supplied by the Spring Boot caller — see E4-S3-T4) and retraining the model, replacing the artifact.
- **Definition of Done:** Endpoint runs the training pipeline without error and returns a
  success indicator; the new artifact is loadable afterward.
- **Required Tests:** `pytest` per `docs/operations/testing.md` §2: verify it loads corrections data and
  triggers training without error (mocked training call is acceptable at unit level; a
  slower end-to-end training test may be marked slow/optional).
- **Estimated Complexity:** Medium
- **Depends on:** E4-S2-T2
- **Grounded in:** `docs/spec/architecture.md` FastAPI endpoint table; `docs/spec/decisions.md` ADR-003; `docs/operations/testing.md` §2 Retrain endpoint.

#### E4-S2-T5 — `GET /evaluate` + evaluation script

- **Objective:** Produce accuracy metrics against the labeled dataset, re-runnable after
  every retrain.
- **Expected Deliverable:** `/evaluate` endpoint plus `ml/evaluation/evaluate.py` CLI script
  producing overall accuracy, per-category precision/recall/F1, a 12×12 confusion matrix,
  and confidence score distribution, saved as a timestamped report under `ml/evaluation/reports/`.
- **Definition of Done:** Running `python evaluation/evaluate.py --data data/spendwise_labeled.xlsx`
  produces a report file; `/evaluate` returns the same metrics as JSON.
- **Required Tests:** `pytest` asserting the script produces a report file with all required
  metric sections present.
- **Estimated Complexity:** Medium
- **Depends on:** E4-S2-T2
- **Grounded in:** `docs/operations/testing.md` §2 Model Evaluation Script (exact command + output metrics + report location).

---

### E4-S3 — Backend Categorization Module Integration

**Independently testable via:** integration tests against a running (or test-doubled) FastAPI instance.

#### E4-S3-T1 — Categorization service interface + FastAPI HTTP client

- **Objective:** Build the Spring Boot side that calls FastAPI `/predict` and stores the
  result — the only module in the whole system permitted to call FastAPI.
- **Expected Deliverable:** `com.spendwise.categorization` service interface + HTTP client
  setting `X-Internal-Key` on every outbound call, writing the result to
  `transaction_categories` with `assigned_by = 'ml'` and the returned `confidence`.
- **Definition of Done:** A successful `/predict` call results in a `transaction_categories`
  row; a low-confidence or failed call is handled per E4-S3-T3's retry path rather than
  crashing the ingest flow.
- **Required Tests:** Unit test mocking the ML HTTP call: success path writes the row;
  failure path does not throw uncaught, leaves the transaction uncategorized for retry.
- **Estimated Complexity:** Medium
- **Depends on:** E4-S2-T3, E3-S1-T2
- **Grounded in:** `docs/spec/architecture.md` module dependency table ("Categorization → Transaction (update category)"); `CLAUDE.md` invariant "FastAPI is called only from the Categorization module"; `docs/operations/testing.md` Categorization unit tests.

#### E4-S3-T2 — Wire Ingest → Categorization trigger

- **Objective:** Call the Categorization service immediately after a transaction is
  persisted during ingest.
- **Expected Deliverable:** Ingest module invokes Categorization's service interface (not FastAPI directly) after each successful insert.
- **Definition of Done:** A transaction ingested via `/ingest/transactions` ends up with a
  `transaction_categories` row without any additional manual trigger.
- **Required Tests:** Integration test: POST to `/ingest/transactions`, then assert
  `transaction_categories` has a matching row (using a real or test-double FastAPI instance).
- **Estimated Complexity:** Small
- **Depends on:** E4-S3-T1
- **Grounded in:** `docs/spec/architecture.md` module dependency table ("Ingest may call: Transaction, Categorization"); SMS Ingestion Flow diagram.

#### E4-S3-T3 — Categorization retry job (every 30 minutes)

- **Objective:** Re-trigger categorization for transactions that were ingested while
  FastAPI was unavailable.
- **Expected Deliverable:** A `@Scheduled` job querying for transactions with no
  `transaction_categories` row and re-invoking the Categorization service.
- **Definition of Done:** A transaction inserted with a simulated FastAPI outage gets
  categorized once the job runs after FastAPI recovers.
- **Required Tests:** Integration test: insert an uncategorized transaction directly, invoke
  the job method manually (not waiting for the real 30-minute schedule), assert it becomes categorized.
- **Estimated Complexity:** Medium
- **Depends on:** E4-S3-T1
- **Grounded in:** `docs/spec/architecture.md` Background Jobs table ("Categorization retry — every 30 minutes").

#### E4-S3-T4 — ML retraining weekly job

- **Objective:** Periodically send `ml_corrections` data to FastAPI `/retrain`.
- **Expected Deliverable:** A `@Scheduled` weekly job (configurable per `docs/spec/architecture.md`)
  that reads `ml_corrections` and calls the Categorization service's retrain method.
- **Definition of Done:** Manually invoking the job method triggers a real `/retrain` call
  against a running FastAPI test instance and completes without error.
- **Required Tests:** Integration test invoking the job method directly and asserting the
  FastAPI `/retrain` endpoint was called (via a test double or a real call against E4-S2-T4).
- **Estimated Complexity:** Medium
- **Depends on:** E4-S3-T1, E4-S2-T4
- **Grounded in:** `docs/spec/architecture.md` Background Jobs table ("ML retraining — Weekly (configurable)").

#### E4-S3-T5 — Admin-triggered retrain + evaluate (service-interface only)

- **Objective:** Expose retrain/evaluate to the (future) Admin module strictly through
  Categorization's service interface — Admin must never call FastAPI directly.
- **Expected Deliverable:** Public methods on the Categorization service interface:
  `triggerRetrain()`, `getAccuracyMetrics()`, callable by Admin in Epic 11.
- **Definition of Done:** A unit/architecture test confirms no class outside
  `com.spendwise.categorization` holds a reference to the FastAPI HTTP client.
- **Required Tests:** An ArchUnit-style test (or equivalent) asserting only the
  Categorization package depends on the FastAPI client class.
- **Estimated Complexity:** Small
- **Depends on:** E4-S3-T1
- **Grounded in:** `docs/spec/architecture.md` "Admin calls Categorization's service interface... FastAPI is never called directly from Admin"; `CLAUDE.md` architectural invariants.

---

## Parallel Execution within Epic 4

- E4-S1 and all of E4-S2 (T1-T5) are a standalone Python service — build entirely in
  parallel with Epic 3.
- Within E4-S2, T3/T4/T5 (`/predict`, `/retrain`, `/evaluate`) are independent once T1/T2 land.
- E4-S3 requires Epic 3 (Ingest/Transaction) to exist; within E4-S3, T3 and T4 (the two
  scheduled jobs) are independent of each other once T1/T2 land.
