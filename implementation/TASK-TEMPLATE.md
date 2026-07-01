# Task Template

Copy this block when adding a new story or task to an epic file. Keep the same heading
levels so the file's table of contents stays navigable.

## Adding a new story

```
### E<epic>-S<n> — <Story name>

<One sentence: what user-visible or system capability this story delivers.>

**Independently testable via:** <how you'd verify this story in isolation, e.g. "curl
against a running instance", "a unit test class", "an emulator screen">
```

## Adding a new task

```
#### E<epic>-S<story>-T<n> — <short task title>

- **Objective:** <what this task accomplishes and why, one or two sentences>
- **Expected Deliverable:** <the concrete artifact — a file, an endpoint, a passing test
  suite, a migration>
- **Definition of Done:**
  - <bullet — concrete, checkable condition>
  - <bullet>
- **Required Tests:** <specific test cases / test class names, not just "add tests">
- **Estimated Complexity:** Small | Medium | Large
- **Depends on:** <task ID(s), or "—" if none>
- **Grounded in:** <path to the docs/ file(s) and section this task implements>
```

## Rules when adding new tasks

- Never reuse or renumber an existing ID. Append the next free number in that story.
- If a task can't be described in a 2-4 hour session, split it into two tasks before
  adding it — don't add an oversized task and hope to shrink it later.
- Add the new task's ID to `tracking/STATUS.md` in the matching epic section, in ID order.
- If the new task changes what's parallelizable, update `DEPENDENCY-GRAPH.md` too.
