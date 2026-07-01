# Development Guidelines

## Branching Strategy

- `main` — production state. **Never commit directly to main.**
- `feature/<short-name>` — all new features (e.g., `feature/sms-parser-sbi`)
- `fix/<short-name>` — bug fixes (e.g., `fix/duplicate-transaction-check`)
- `chore/<short-name>` — tooling, deps, config (e.g., `chore/update-gradle`)

### Workflow

```
1. Pull latest main
2. Create feature branch from main
3. Develop and commit to feature branch
4. Open PR against main
5. CI must pass (tests green)
6. Self-review or peer review
7. Merge to main
8. Deploy manually after verification
```

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

## Security Checklist (before every PR)

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

All four test suites must pass before a PR can be merged.

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

1. Add the category row to the `categories` table seed script
2. Update `docs/requirements.md` with the new category name
3. Update the ML training pipeline to include the new label
4. Re-run model evaluation to confirm accuracy is not degraded
5. Update UI category list in both Android and Next.js
