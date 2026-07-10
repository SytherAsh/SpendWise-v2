# Implementation Workspace

This folder is the **execution planning workspace** for building SpendWise. It is separate from the frozen specification in [`../docs/`](../docs/) and from [`../CLAUDE.md`](../CLAUDE.md).

**Golden Rule: this workspace never redefines product behavior.** If something here conflicts with `docs/`, the spec in `docs/` wins — fix this workspace, not the spec. The spec is frozen; this workspace evolves daily as work proceeds.

---

## Folder Structure

```text
implementation/
├── README.md                      ← You are here
├── ROADMAP.md                     Master index, epic sequencing, milestones
├── DEPENDENCY-GRAPH.md            Cross-epic dependency table
├── TASK-TEMPLATE.md               Blank template for new tasks
│
├── epics/                         (13 specs: completed and active)
│   ├── epic-00-foundation.md      ✅ CLOSED
│   ├── epic-01-auth-and-user.md   ✅ CLOSED
│   ├── ... (epic-02 through epic-11 all CLOSED)
│   └── epic-12-deployment-and-launch.md  🔄 IN PROGRESS
│
├── tracking/                      Execution tracking
│   ├── STATUS.md                  Master checklist (done/in-progress per task ID)
│   ├── completed/                 Archived snapshots of finished epics
│   ├── notes/                     Session findings, blockers, decisions
│   └── checklists/                Verification checklists
│       ├── REVIEW.md              Code review notes & findings
│       └── LOCAL-E2E-CHECKLIST.md End-to-end test verification
│
└── reference/                     Supporting docs
    └── development_environment.md Local dev setup guide
```

---

## How to Use This Workspace

### Starting a Coding Session

1. Open [`tracking/STATUS.md`](tracking/STATUS.md) — the **single source of truth** for task progress
2. Find the next unchecked task in epic order (Epics 0–11 are mostly done; Epic 12 is active)
3. Open that epic's file in [`epics/`](epics/) and jump to the task heading (task IDs like `E4-S2-T3` match between STATUS.md and epic files)
4. Read the **Objective**, **Expected Deliverable**, **Definition of Done**, and **Required Tests**
5. That's the full contract — code to satisfy it

### Marking Work Complete

When a task is done:
1. Update [`tracking/STATUS.md`](tracking/STATUS.md) — mark it ✅ DONE with commit hash
2. If the epic is now fully complete, mark the entire epic as CLOSED in STATUS.md
3. No need to move epic files — they stay in [`epics/`](epics/) as permanent reference

### Recording Findings & Blockers

Session notes, discoveries, and blockers go in [`tracking/notes/`](tracking/notes/):
- Create a new file per session: `session-2026-07-10.md`
- Include: what was learned, what blocked progress, what should be revisited
- These are NOT part of the spec — they're working notes for continuity

### Verification Before Launch

Before any release:
1. Review [`tracking/checklists/LOCAL-E2E-CHECKLIST.md`](tracking/checklists/LOCAL-E2E-CHECKLIST.md) — all golden-path flows green?
2. Review [`tracking/checklists/REVIEW.md`](tracking/checklists/REVIEW.md) — any architectural violations?
3. Update STATUS.md with final status
4. Archive completed epic snapshots to [`tracking/completed/`](tracking/completed/)

---

## Key Relationships

### Epics to Spec

Each epic references the spec doc(s) it implements:

- **Epic 00** → No spec dependency (foundational setup)
- **Epic 01** → [`docs/spec/security.md`](../docs/spec/security.md), [`docs/spec/api.md`](../docs/spec/api.md)
- **Epic 03** → [`docs/spec/architecture.md`](../docs/spec/architecture.md), [`docs/spec/api.md`](../docs/spec/api.md), [`docs/spec/database.md`](../docs/spec/database.md)
- **Epic 07** → [`docs/spec/api.md`](../docs/spec/api.md) (Analytics endpoints)
- ... (see each epic file for its spec links)

### Cross-Epic Dependencies

See [`DEPENDENCY-GRAPH.md`](DEPENDENCY-GRAPH.md) for which epics must complete before others can start.

---

## Epic Status at a Glance

| Epic | Title | Status | Tasks | Closed | Last Update |
|------|-------|--------|-------|--------|-------------|
| E00 | Foundation | ✅ CLOSED | 4 | 4 | 2026-07-05 |
| E01 | Auth & User | ✅ CLOSED | 6 | 6 | 2026-07-05 |
| E02 | Android SMS Parsing & Sync | ✅ CLOSED | 8 | 8 | 2026-07-05 |
| E03 | Ingestion & Transactions | ✅ CLOSED | 7 | 7 | 2026-07-05 |
| E04 | ML Categorization | ✅ CLOSED | 8 | 8 | 2026-07-05 |
| E05 | Budget & Alerts | ✅ CLOSED | 10 | 10 | 2026-07-05 |
| E06 | EMI & Recurring | ✅ CLOSED | 8 | 8 | 2026-07-05 |
| E07 | Analytics & Export | ✅ CLOSED | 7 | 7 | 2026-07-03 |
| E08 | Recommendations & Chatbot | ✅ CLOSED | 8 | 8 | 2026-07-03 |
| E09 | Android App UI | ✅ CLOSED | 6 | 6 | 2026-07-04 |
| E10 | Web Dashboard | ✅ CLOSED | 12 | 12 | 2026-07-04 |
| E11 | Admin Portal | ✅ CLOSED | 6 | 6 | 2026-07-05 |
| E12 | Deployment, Monitoring & Launch | 🔄 IN PROGRESS | 7 | 0 | 2026-07-10 |

**Total:** 114/125 tasks done. Epic 12 is the launch track.

---

## Maintenance & Archive

### When an Epic is Closed

1. All tasks in the epic are ✅ DONE
2. Code has landed in `main` and is deployed
3. Actions:
   - Mark epic status as ✅ CLOSED in STATUS.md
   - Create snapshot: `tracking/completed/epic-XX-summary.md` with:
     - Final task count
     - What was delivered
     - Key commits (git log link)
     - Links to relevant `docs/` specs
   - Leave original epic file in [`epics/`](epics/) (never delete — reference forever)

### Keeping Notes Fresh

Session notes in [`tracking/notes/`](tracking/notes/) should:
- Be dated (e.g., `session-2026-07-10.md`)
- Capture blockers, discoveries, decisions
- Be archived or resolved periodically
- Not stay stale — old blockers that are resolved should be removed or marked RESOLVED

### Using the Docs-Curator Agent

Call [`/docs-curator`](../.claude/agents/docs-curator.md) to:
- Audit both `docs/` and `implementation/` structures
- Identify stale epic descriptions
- Verify cross-references between STATUS.md and epics
- Suggest archival of completed epics

```bash
/docs-curator audit          # Scan for issues (no changes)
/docs-curator update         # Auto-update stale content
/docs-curator check epic-12  # Audit just Epic 12
```

---

## Quick Links

- **Spec:** Read [../docs/README.md](../docs/README.md) for product spec navigation
- **Current Work:** Open [`tracking/STATUS.md`](tracking/STATUS.md)
- **Next Epic:** See [`ROADMAP.md`](ROADMAP.md)
- **Dependencies:** See [`DEPENDENCY-GRAPH.md`](DEPENDENCY-GRAPH.md)
- **Dev Setup:** See [`reference/development_environment.md`](reference/development_environment.md)
- **Code Review:** See [`tracking/checklists/REVIEW.md`](tracking/checklists/REVIEW.md)

---

Last updated: 2026-07-10
