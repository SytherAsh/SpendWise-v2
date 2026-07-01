# Implementation Workspace

This folder is the **execution planning workspace** for building SpendWise. It is separate
from the frozen specification in [`../docs/`](../docs/) and from `../CLAUDE.md`.

**Rule: this workspace never redefines product behavior.** If something here appears to
conflict with `docs/`, the spec in `docs/` wins — fix this workspace, not the spec. The
spec is frozen (see `../docs/decisions.md` and the CLAUDE.md doc index); this workspace is
not — it is expected to evolve daily as work proceeds.

## Why this exists

The spec (`docs/`) describes **what** SpendWise is. It does not describe **in what order**
to build it, **how big** each unit of work is, or **what "done" means** for a given slice.
This workspace answers those questions so that any coding session — today or six months
from now — can pick up a single task with zero ambiguity about scope.

## Folder Structure

```
implementation/
├── README.md                  — this file
├── ROADMAP.md                 — master index: epic list, sequencing, milestones, how to use this backlog
├── DEPENDENCY-GRAPH.md         — cross-epic dependency table + which tracks can run in parallel
├── TASK-TEMPLATE.md            — blank template for adding new tasks/stories/epics later
├── epics/
│   ├── epic-00-foundation.md
│   ├── epic-01-auth-and-user.md
│   ├── epic-02-android-sms-parsing-sync.md
│   ├── epic-03-ingestion-and-transactions.md
│   ├── epic-04-ml-categorization.md
│   ├── epic-05-budget-and-alerts.md
│   ├── epic-06-emi-and-recurring.md
│   ├── epic-07-analytics-and-export.md
│   ├── epic-08-recommendations-and-chatbot.md
│   ├── epic-09-android-app-ui.md
│   ├── epic-10-web-dashboard.md
│   ├── epic-11-admin-portal.md
│   └── epic-12-deployment-and-launch.md
└── tracking/
    └── STATUS.md               — living checklist of every task; the only file you edit day-to-day
```

## How to use this workspace

1. **Starting a coding session** — open `tracking/STATUS.md`, find the next unchecked task
   in epic order, then open that task's home epic file and jump to its heading (task IDs
   are unique, e.g. `E4-S2-T3`, and match 1:1 between `STATUS.md` and the epic files).
2. **Doing the task** — the task card gives you Objective, Expected Deliverable, Definition
   of Done, Required Tests, and Complexity. That's the full contract for the task — if
   something is ambiguous, check the referenced `docs/` file before improvising.
3. **Finishing a task** — check the box in `tracking/STATUS.md`. Do not edit the epic files
   to mark completion; they are the specification of the task, not a progress log.
4. **Picking parallel work** (e.g. two people, or two agent sessions) — check
   `DEPENDENCY-GRAPH.md` for which epics/tracks have no unmet dependencies right now.
5. **Adding new work later** (a task was missed, or scope grows) — copy `TASK-TEMPLATE.md`,
   assign the next free task ID within the right story, add it to the epic file and to
   `tracking/STATUS.md`.

## Conventions used throughout this workspace

- **IDs**: `E<epic>-S<story>-T<task>`, e.g. `E5-S2-T3` = Epic 5, Story 2, Task 3. IDs are
  stable once assigned — never renumber existing tasks, only append.
- **Complexity**: Small (~2h), Medium (~3h), Large (~4h). Nothing in this backlog should
  exceed a 4-hour session; if a task looks bigger than that while you're working it, stop
  and split it (update `STATUS.md` and the epic file with the new IDs).
- **Every task names the doc(s) it is grounded in** so you can re-read the exact
  requirement instead of relying on memory of this backlog.
- **No application code lives here.** This workspace is planning-only markdown.
