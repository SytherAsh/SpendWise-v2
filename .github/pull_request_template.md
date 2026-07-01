## Summary

<!-- What does this PR do, and why? Link the task ID(s) from implementation/tracking/STATUS.md if applicable. -->

## Test Plan

<!-- How was this verified? Which test suites were run? -->

- [ ] `cd backend && ./gradlew test && ./gradlew integrationTest`
- [ ] `cd ml && pytest tests/ -v`
- [ ] `cd android && ./gradlew test`
- [ ] `cd frontend && npm run lint && npm run build`
- [ ] `cd tests/e2e && pytest test_golden_path.py -v` (if this PR touches ingest, categorization, or analytics)

## Security Checklist (before every PR)

<!-- Verbatim from docs/development_guidelines.md -->

- [ ] No secrets or API keys in code or comments
- [ ] New endpoints have JWT auth guard applied
- [ ] Admin endpoints validate `ADMIN_JWT_SECRET`-signed tokens only (separate auth filter — not a role claim on the user JWT)
- [ ] User data queries include `WHERE user_id = ?` **and** a Supabase RLS policy exists for the table
- [ ] Input validated at API boundary (not assumed valid downstream)
