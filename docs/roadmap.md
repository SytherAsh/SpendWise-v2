# Product Roadmap

## Current Phase: MVP

**Goal**: Working end-to-end product for personal use + family (~5–10 users).

Core features (must be complete before any post-MVP work):

- SMS ingestion (SBI, Paytm, GPay)
- Bank statement upload (historical transaction seed + budget suggestions)
- ML categorization with correction feedback loop
- Budget tracking and alerts
- EMI and subscription tracking (automatic recurring payment detection)
- Web dashboard with interactive charts
- Android app with background SMS monitoring
- Savings recommendations (proactive LLM-powered spend insights, threshold-triggered)
- AI chatbot (interactive financial assistant — user-initiated, session-based)
- PDF and CSV export
- Admin portal

---

## Post-MVP Roadmap (Priority Order)

### Phase 1: Language Expansion

#### Hindi and Marathi support

- UI localisation: translate all screens, labels, and alerts
- SMS parsing: SBI, Paytm, GPay send SMS in Hindi for some users — parser must handle Devanagari script amounts and keywords
- Chatbot: LLM should respond in the user's preferred language

### Phase 2: Multi-Bank SMS Expansion

#### Add parser rules for more senders

Priority order based on Indian market share:

1. HDFC Bank
2. ICICI Bank
3. Axis Bank
4. PhonePe
5. Amazon Pay
6. Kotak Mahindra Bank

Each new sender requires:

- Analyzing real SMS formats (3–5 samples per sender)
- Adding regex rules to the Android parser
- Releasing an updated APK

### Phase 3: Stock & Investment Portfolio Tracking

#### Track equity and mutual fund holdings alongside spending

- Integrate with a market data API (BSE/NSE) for live prices
- Manual entry for holdings (SIP amounts, folio numbers)
- Portfolio value vs. spending trend on the same dashboard
- Net worth view: assets (investments) minus liabilities (loans/EMIs)

### Phase 4: Per-User Personalised ML Model

#### Shift from global model to user-specific models

- Current: all users use the same global model trained on aggregate data
- Future: once a user has accumulated enough correction data (threshold TBD), switch to a per-user fine-tuned model
- Challenge: cold-start for new users; global model remains fallback
- Infrastructure impact: model storage per user in Supabase or a separate object store

### Phase 5: Google Play Store Distribution

#### Replace Firebase App Distribution with public Play Store listing

Requirements:

- Google Play Developer account ($25 one-time)
- Privacy policy hosted at a public URL
- App content rating questionnaire
- Target API level compliance (Android's latest requirements)

### Phase 6: Espresso UI Test Suite (Android)

#### Automated UI testing for the Android app

- Espresso tests for critical user flows: onboarding, transaction list, category correction, budget screen
- Added when the Android UI stabilises (reduces refactoring churn)

### Phase 7: Commercialise Categorisation Model

#### B2B API: expose the trained categorisation model to other fintech apps

- The aggregate categorisation model (trained on all SpendWise user data) becomes a licensable asset
- Expose as a standalone REST API with API key billing
- Pricing model: per-prediction or monthly subscription (B2B, not B2C)
- Legal: review data usage consent to ensure aggregate model licensing is covered

### Phase 8: Microservices Extraction

#### Extract high-load modules when traffic demands it

Candidates for extraction (in order of likelihood):

1. ML Service (FastAPI) — already separate, just needs independent scaling
2. Ingest module — high write volume as users scale
3. Analytics module — heavy read queries during peak usage

Trigger: when free-tier limits are consistently hit and paid infrastructure becomes justified.

### Phase 9: Counterparty Metadata Enrichment (Analytics/Dashboard)

#### Let users view Transfers broken down by who it's to/from, without expanding the ML category set

- During Epic 4 training-data labeling, a Payee Knowledge Base was built that already
  knows which recipients are friends, family, self-transfer accounts, merchants,
  employers, or subscriptions (`ml/labeling/knowledge_base/`)
- Proposed: a post-ML-prediction enrichment step (read-only, Analytics-layer) attaches
  this counterparty metadata to a transaction for display/filtering purposes — the ML
  model itself keeps predicting only the 12 categories in `docs/requirements.md`,
  unchanged
- Design sketch: `docs/architecture.md` "Future Enhancement: Counterparty Metadata
  Enrichment"; rationale for keeping it out of the ML problem: ADR-010 in
  `docs/decisions.md`
- Natural landing point: Epic 7 (Analytics) once prioritized, or a dedicated future
  epic — not scoped into any current epic
- Would need: porting `merchant_rules.csv`-style rules into a queryable table, a
  per-user extension mechanism (new contacts aren't in the training-time knowledge
  base), and Analytics-module query support — all additive, no ML retraining involved

---

## Deferred Indefinitely

- iOS support — Android-only was an explicit decision; iOS adds significant development overhead
- Real-time streaming of transactions — batch sync is sufficient; streaming adds infrastructure cost
- Social / shared finance features — out of scope for this product's vision
