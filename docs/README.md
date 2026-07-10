# SpendWise Documentation

This folder contains all SpendWise product specification, operational guides, design system, and demo materials. **Start with [../CLAUDE.md](../CLAUDE.md)** — it's the entry point for developers.

---

## Documentation Structure

The docs are organized into 5 categories for clarity and discoverability:

| Category | Purpose | When to Read |
|----------|---------|--------------|
| **[spec/](#spec)** | Core product spec — architecture, API, database, security decisions | Before proposing any architectural or API change |
| **[operations/](#operations)** | Development & deployment practices — testing, CI/CD, guidelines | When setting up dev environment or deploying |
| **[design/](#design)** | Visual identity, design system, brand tokens | When implementing UI or updating visual design |
| **[demo/](#demo)** | Demo account setup, feature completeness, deployment | When preparing demo or marketing materials |
| **[archive/](#archive)** | Deprecated docs (kept for reference only) | Historical reference only — not active |

**Root level:** [roadmap.md](roadmap.md) — post-MVP feature roadmap (kept at root for easy access)

---

## Spec — Product Definition

These documents define **what SpendWise is**. Changes to any one often require updates to the others.

| Document | Purpose | Last Touch | Status |
|----------|---------|-----------|--------|
| [spec/vision.md](spec/vision.md) | Product vision, target users, success criteria | ~2 months | FROZEN |
| [spec/requirements.md](spec/requirements.md) | Functional requirements: 12 categories, budgets, alerts, recurring detection | ~3 weeks | STABLE |
| [spec/architecture.md](spec/architecture.md) | System architecture: 11 Spring Boot modules, Android packages, data flow | 1 day | ACTIVE |
| [spec/api.md](spec/api.md) | REST API endpoint reference (all 11 groups, auth, demo endpoints) | Current | ACTIVE |
| [spec/database.md](spec/database.md) | PostgreSQL schema: 18 tables, RLS policies, migration history | ~3 weeks | ACTIVE |
| [spec/security.md](spec/security.md) | Security protocols, DPDP Act 2023 compliance, auth rules, encryption | ~4 weeks | STABLE |
| [spec/decisions.md](spec/decisions.md) | Architecture Decision Records (ADRs 1–10) | ~3 weeks | ACTIVE |

**Read first:** [spec/architecture.md](spec/architecture.md) and [spec/api.md](spec/api.md) for any cross-module change.

---

## Operations — Development & Deployment

These documents guide **how to build, test, and deploy** SpendWise.

| Document | Purpose | Last Touch | Status |
|----------|---------|-----------|--------|
| [operations/deployment.md](operations/deployment.md) | Infrastructure, hosting, CI/CD setup, env vars, Firebase config | ~3 weeks | ACTIVE |
| [operations/testing.md](operations/testing.md) | Testing strategy: JUnit 5, pytest, Kotlin, Next.js, E2E | ~3 weeks | ACTIVE |
| [operations/development_guidelines.md](operations/development_guidelines.md) | Coding standards, commit message format, pre-commit security checklist | ~3 weeks | STABLE |
| [operations/user_flows.md](operations/user_flows.md) | Onboarding flow, recurring user actions, error states | 1 day | ACTIVE |

**Read first:** [operations/development_guidelines.md](operations/development_guidelines.md) before committing.

---

## Design — Visual & Brand

These documents define **how SpendWise looks**.

| Document | Purpose | Last Touch | Status |
|----------|---------|-----------|--------|
| [design/design-system.md](design/design-system.md) | Emerald brand, color tokens, component specs, typography, spacing | 1 day | ACTIVE |
| [design/system-diagram.md](design/system-diagram.md) | Mermaid architecture diagram (visual representation of spec/architecture.md) | ~2 weeks | FROZEN |

---

## Demo — Marketing & Demo Mode

These documents specify **demo account setup** and pre-seeded data for marketing.

| Document | Purpose | Last Touch | Status |
|----------|---------|-----------|--------|
| [demo/demo-data.md](demo/demo-data.md) | 522 demo transactions spanning 1 year, realistic spending patterns | NEW | NEW |
| [demo/demo-feature-complete.md](demo/demo-feature-complete.md) | Demo feature checklist: what works in demo mode | NEW | NEW |
| [demo/demo-login-integration.md](demo/demo-login-integration.md) | Frontend demo login flow, no-auth landing page | NEW | NEW |
| [demo/demo-deployment-checklist.md](demo/demo-deployment-checklist.md) | Demo account seeding, pre-flight verification | NEW | NEW |

---

## Archive — Deprecated Docs

This folder holds deprecated documentation kept for historical reference. **Do not use these for current development.**

To revive an archived doc, move it back to its appropriate folder and remove the deprecation notice.

---

## Cross-References & Dependency Map

```
spec/
├─ architecture.md ←→ api.md ←→ database.md
│                          ↓
│                    security.md
│                          ↓
└─ decisions.md ←─────────┘

demo/
├─ demo-login-integration.md → spec/api.md (auth endpoints)
├─ demo-data.md → spec/requirements.md (categories)
└─ demo-deployment-checklist.md → spec/database.md (RLS)

operations/
├─ deployment.md → spec/security.md (env vars)
└─ testing.md → spec/architecture.md (test coverage targets)
```

---

## Quick Start by Role

**Backend engineer?**
1. Read [../CLAUDE.md](../CLAUDE.md) (module map, security invariants)
2. Read [spec/architecture.md](spec/architecture.md) (module boundaries)
3. Read [spec/api.md](spec/api.md) (endpoint contracts)
4. Read [operations/development_guidelines.md](operations/development_guidelines.md) (commit rules, security checklist)

**Frontend engineer?**
1. Read [../CLAUDE.md](../CLAUDE.md) (app shell, routing)
2. Read [design/design-system.md](design/design-system.md) (visual tokens, components)
3. Read [spec/api.md](spec/api.md) (endpoint contracts)
4. Read [operations/testing.md](operations/testing.md) (Next.js test setup)

**Android engineer?**
1. Read [../CLAUDE.md](../CLAUDE.md) (SMS parsing, sync flow)
2. Read [spec/architecture.md](spec/architecture.md) (Android modules)
3. Read [spec/api.md](spec/api.md) (`/ingest` contract, device API key)
4. Read [operations/deployment.md](operations/deployment.md) (APK release)

**DevOps / Deployment?**
1. Read [operations/deployment.md](operations/deployment.md) (infrastructure, CI/CD)
2. Read [spec/security.md](spec/security.md) (env vars, secrets)
3. Read [spec/database.md](spec/database.md) (schema migrations)

**Demo / Marketing?**
1. Read [demo/demo-data.md](demo/demo-data.md) (sample data)
2. Read [demo/demo-feature-complete.md](demo/demo-feature-complete.md) (what's in demo)
3. Read [demo/demo-deployment-checklist.md](demo/demo-deployment-checklist.md) (go-live checklist)

---

## Maintaining These Docs

**Docs go stale.** To keep them fresh:

- After landing a feature, update the relevant spec doc (e.g., new endpoint → update [spec/api.md](spec/api.md))
- After changing infrastructure, update [operations/deployment.md](operations/deployment.md)
- After design changes, update [design/design-system.md](design/design-system.md)
- **Weekly:** Call `/docs-curator audit` to identify stale content

See [../CLAUDE.md § Task Workflow](../CLAUDE.md#task-workflow-uiux-polish--remaining-launch-work) for the rule: *"If a change alters an API contract, data flow, schema, or user flow, update the relevant docs/ file in the same commit."*

---

Last updated: 2026-07-10
