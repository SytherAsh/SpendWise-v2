# Development Guidelines

## Git Workflow

SpendWise is a **solo project** — the normal workflow is to work directly on `main`. Feature branches and pull requests are **not** required.

- `main` — the working branch. Commit completed units of work directly to it.
- Optional branches (`feature/<short-name>`, `fix/<short-name>`, `chore/<short-name>`) remain available for isolating a risky or experimental change, but are not part of the routine flow — don't create one without a specific reason.

### Workflow

```
1. Pull latest main
2. Develop and commit each completed unit of work directly to main
3. Run the relevant test suites (all four must be green — see Running Tests Locally)
4. Ask for confirmation, then push to main
5. GitHub Actions CI runs automatically on the push
6. Deploy manually after verification
```

Do not force-push, rewrite pushed history, or delete branches without explicit approval. Always ask before pushing to GitHub.

## Commit Messages

Use conventional commits format:

```
feat: add SBI SMS regex parser
fix: resolve duplicate transaction on backfill
chore: upgrade Spring Boot to 3.3.0
docs: update API endpoint reference
test: add parser unit tests for Paytm format
```

## Code Style

### Java 21 (Spring Boot Backend)

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- 4-space indentation
- No unused imports
- Prefer `Optional` over returning `null` from service methods
- Use records for simple DTOs where applicable (Java 16+)
- Annotations drive configuration — minimize XML

### Kotlin (Android)

- Follow official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- 4-space indentation
- No unused imports
- Null safety: avoid `!!` operator — use safe calls and elvis operator
- Coroutines for async operations

### Python (FastAPI ML)

- Follow PEP 8
- Type hints on all function signatures
- 4-space indentation
- Format with `black` before committing

### TypeScript / React (Next.js)

- Strict TypeScript mode enabled
- Functional components only (no class components)
- Named exports over default exports
- Component files: PascalCase (e.g., `TransactionCard.tsx`)
- Utility files: camelCase (e.g., `formatCurrency.ts`)

## Module Rules (Spring Boot)

- Every module must expose its functionality via a **service interface**
- No module should directly instantiate or import classes from another module's internal implementation
- Cross-module calls go through the service interface only
- The Analytics module is **read-only** — it must not call write methods on any other module

## API Rules

- All routes prefixed `/api/v1/`
- Endpoints that modify data use POST/PUT/DELETE — never GET for mutations
- All responses include consistent error shape: `{ error, message, status }`
- `sms_raw_text` must never appear in any user-facing API response — add a filter at the serialization layer

## Security Checklist (before every commit to main)

- [ ] No secrets or API keys in code or comments
- [ ] New endpoints have JWT auth guard applied
- [ ] Admin endpoints validate `ADMIN_JWT_SECRET`-signed tokens only (separate auth filter — not a role claim on the user JWT)
- [ ] User data queries include `WHERE user_id = ?` **and** a Supabase RLS policy exists for the table
- [ ] Input validated at API boundary (not assumed valid downstream)

## Running Tests Locally

```bash
# Spring Boot
cd backend && ./gradlew test

# FastAPI
cd ml && pytest tests/ -v

# Android
cd android && ./gradlew test

# E2E
cd tests/e2e && pytest test_golden_path.py -v
```

All four test suites must pass before pushing to `main`.

## Environment Setup

See [docs/deployment.md](./deployment.md) for the full list of required environment variables.

Copy `.env.example` files (where present) to `.env` and fill in values. Never commit `.env` files.

## Adding a New SMS Sender

When adding parser support for a new bank or payment app:

1. Collect 5+ real SMS samples in `android/app/src/test/kotlin/com/spendwise/parser/samples/`
2. Add regex rules in `android/app/src/main/kotlin/com/spendwise/parser/`
3. Add unit tests covering: valid formats, edge cases (partial data), invalid formats
4. Test against the historical SMS inbox of a real device before releasing

## Adding a New Category

1. Add the category via an additive Flyway migration (don't edit an already-run migration's seed data)
2. Update `docs/requirements.md`, `docs/database.md`, and `docs/api.md` with the new category
3. Update the ML training pipeline (`ml/api/categories.py` and `ml/labeling/`) to include the new label
4. Re-run model evaluation to confirm accuracy is not degraded
5. Update UI category list in both Android and Next.js
6. Update backend tests that assert the category count or seed list (schema integration tests, `GET /categories` tests)
7. Follow the canonical-value-change process below to catch anything item 1-6 missed

## Changing a Canonical/Shared Value

Applies to any change to a value referenced in more than one place: a category count, an enum's member list, a shared constant, a module boundary, a secret name — not just categories.

1. Before marking the task done, grep the **whole repo** (backend, ml, android, frontend, docs, implementation/ — not just the files you remember touching) for the old value or name.
2. Classify every hit: fix code and tests directly; for a frozen doc, propose the edit and get explicit approval before touching it; for a historical/completed record (e.g. a finished epic task's Definition of Done), append a dated amendment note rather than rewriting the original text.
3. Run the test suite(s) that reference the changed value locally before committing — don't rely on CI to surface it first.
