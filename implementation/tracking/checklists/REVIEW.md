# SpendWise — End‑to‑End Local Review

**Date:** 2026‑07‑05
**Scope:** Full local stack (Next.js frontend → Spring Boot backend → Postgres → Firebase auth → CORS → REST). Investigation focused on the reported blocking symptom: *login succeeds, but the frontend then shows only failed requests and nothing works.*

**Method:** Read the documentation first (CLAUDE.md, `docs/spec/architecture.md`, `docs/spec/api.md`, `docs/spec/security.md`, `docs/operations/deployment.md`, `README.md`) as the source of truth, then traced the runtime path the browser actually exercises after login: `LoginForm` → `authApi` → `apiClient` → Spring Security filter chains → controllers.

---

## Executive summary

The blocking symptom has a **single root cause**: CORS is configured only at the Spring **MVC** layer (`CorsConfig implements WebMvcConfigurer`) and was **never wired into any of the four Spring Security `SecurityFilterChain`s** — none of them call `http.cors(...)`.

In Spring Security 6, the browser's **CORS preflight (`OPTIONS`)** request for an authenticated endpoint carries no `Authorization` header, so a chain whose rule is `.anyRequest().authenticated()` rejects the preflight with **401 before it ever reaches the MVC CORS handler**. The browser then blocks the real request and surfaces a CORS/"Failed to fetch" error.

Login *appears* to work because every login endpoint (`/api/v1/auth/**`) is `permitAll`, so *its* preflight passes through Security to the MVC layer where the WebMvcConfigurer CORS mapping adds the headers. The moment the app redirects to `/dashboard`, every data call (`/categories`, `/alerts`, `/recommendations`, `/budgets/progress`, `/analytics/*`) is an authenticated endpoint — all of their preflights are blocked. Same wall applies to the admin portal's `/api/v1/admin/**` calls.

Fixing this one issue restores the entire authenticated surface. The remaining findings are minor drift, not blockers.

---

## Issues

### ISSUE‑1 — CORS not integrated into Spring Security → all authenticated browser calls fail *(CRITICAL — this is the blocker)*

- **Symptom:** Login works; immediately afterward every dashboard/admin API call fails in the browser with a CORS / "Failed to fetch" error. `useApi`/SWR reports `error` for every section.
- **Root cause:** `backend/.../auth/CorsConfig.java` registers CORS via `WebMvcConfigurer.addCorsMappings`, which only takes effect at the DispatcherServlet (MVC) layer — *after* the Spring Security filter chain. None of the four `SecurityFilterChain`s (`defaultFilterChain`, `adminFilterChain`, `adminLoginFilterChain`, `ingestFilterChain`) enable CORS via `http.cors(...)`, and there is no `CorsConfigurationSource` bean. Consequently, preflight `OPTIONS` requests to any `.authenticated()` route are rejected by Security (401) before the MVC CORS handler runs. Public `permitAll` routes (`/auth/**`) are the *only* ones whose preflight reaches the MVC layer — which is exactly why login is the one thing that works.
- **Why login masks it:** `defaultFilterChain` lists `/api/v1/auth/otp/verify`, `/auth/google`, `/auth/token/refresh` as `permitAll`; the dashboard's endpoints are all `.anyRequest().authenticated()`.
- **Files involved:**
  - `backend/src/main/java/com/spendwise/auth/CorsConfig.java` (MVC‑only CORS)
  - `backend/src/main/java/com/spendwise/auth/SecurityConfig.java` (`defaultFilterChain`, `adminFilterChain`, `adminLoginFilterChain` — no `.cors()`)
  - `backend/src/main/java/com/spendwise/ingest/IngestSecurityConfig.java` (`ingestFilterChain` — no `.cors()`)
  - Frontend consumers that hit the wall: `frontend/src/lib/apiClient.ts`, `frontend/src/lib/adminApiClient.ts`, `frontend/src/components/dashboard/DashboardView.tsx`, `frontend/src/lib/useCategories.ts`
- **Fix:** Expose a single `CorsConfigurationSource` bean (same allowed origins/methods/headers the documented `CorsConfig` already used — `http://localhost:3000`, `http://127.0.0.1:3000`) and enable `http.cors(Customizer.withDefaults())` on each security filter chain so Spring Security handles preflight and delegates to that source. This preserves the documented CORS policy; it only moves enforcement to the layer that actually guards the requests.
- **Scope of impact:** user dashboard (all pages) **and** admin portal (all protected pages). Android ingest is unaffected (native app, no browser CORS) but the chain gets the same harmless treatment for consistency.

### ISSUE‑2 — `permitAll` references `/api/v1/health` but no such endpoint exists *(LOW — not a blocker)*

- **Root cause:** `defaultFilterChain` permits `/api/v1/health`, but no controller maps that path (no `HealthController`; Spring Boot Actuator, if present, would expose `/actuator/health`). The permit rule is harmless but dead; a caller hitting `/api/v1/health` gets 404, not a health payload.
- **Files involved:** `backend/src/main/java/com/spendwise/auth/SecurityConfig.java`.
- **Impact:** None on the login/dashboard flow. Only matters for external uptime checks / the release‑readiness auditor. **Left as‑is** unless documentation designates a canonical health path — changing it would be undocumented behavior, outside this task's mandate.

### ISSUE‑3 — Admin portal shares ISSUE‑1's failure mode *(folded into ISSUE‑1)*

- **Root cause:** `adminApiClient.ts` calls `/api/v1/admin/**` (authenticated, `adminFilterChain`) from the browser; admin login (`/api/v1/admin/auth/login`) is `permitAll` on `adminLoginFilterChain`. Identical pattern to ISSUE‑1: admin login works, every subsequent admin call's preflight is blocked.
- **Fix:** Covered by ISSUE‑1's fix (applying `.cors(...)` to `adminFilterChain` and `adminLoginFilterChain`).

---

## Dependencies between issues

- **ISSUE‑3 depends entirely on ISSUE‑1** — one fix (enable CORS on all security chains) resolves both. No ordering concern.
- **ISSUE‑2 is independent** and non‑blocking; intentionally not changed (no documented health contract to satisfy).

## Prioritized fix order

1. **ISSUE‑1 (CRITICAL)** — Wire CORS into the Spring Security filter chains. Unblocks the entire authenticated surface (user dashboard + admin portal). *(This is the only code change required to make the app work end‑to‑end.)*
2. **ISSUE‑2 (LOW)** — No action (documentation‑gated). Recorded for visibility only.

---

## Verification plan (proves the fix)

1. Rebuild + restart backend (`.\run-local.ps1`) — Java has no hot reload.
2. Simulate the browser preflight the dashboard sends to an authenticated endpoint and confirm the CORS headers are now present:
   ```
   curl -i -X OPTIONS http://localhost:8080/api/v1/categories \
     -H "Origin: http://localhost:3000" \
     -H "Access-Control-Request-Method: GET" \
     -H "Access-Control-Request-Headers: authorization"
   ```
   Expect `HTTP/1.1 200`, `Access-Control-Allow-Origin: http://localhost:3000`, `Access-Control-Allow-Methods: ...GET...`.
   (Before the fix this returns 401 with **no** `Access-Control-Allow-Origin`.)
3. Confirm login endpoint preflight still works (regression check): same `curl` against `/api/v1/auth/otp/verify` with `Access-Control-Request-Method: POST`.
4. Full browser check: log in at `localhost:3000/login`, land on `/dashboard`, confirm the network tab shows the data calls succeed (200, empty‑state data for a new user) instead of failing preflights.

---

## Verification — Results (2026‑07‑05, backend booted locally against Docker Postgres)

Backend booted clean (`Started SpendwiseApplication`, Flyway schema v8 up to date). Preflight (`OPTIONS`) and actual requests exercised with `Origin: http://localhost:3000`:

| Request | Before fix (expected) | After fix (observed) |
|---|---|---|
| `OPTIONS /api/v1/categories` (authenticated) | 401, no `Allow-Origin` | **200**, `Allow-Origin: http://localhost:3000`, `Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS`, `Allow-Credentials: true` |
| `OPTIONS /api/v1/analytics/summary` | 401, no header | **200** + CORS headers |
| `OPTIONS /api/v1/alerts` | 401, no header | **200** + CORS headers |
| `OPTIONS /api/v1/budgets/progress` | 401, no header | **200** + CORS headers |
| `OPTIONS /api/v1/admin/users` (admin) | 401, no header | **200** + CORS headers |
| `OPTIONS /api/v1/auth/otp/verify` (login, regression) | 200 | **200** + CORS headers (unchanged) |
| `GET /api/v1/categories` with invalid bearer | preflight blocked → never sent | **401 with** `Allow-Origin: http://localhost:3000` (browser can now read the response) |
| `OPTIONS /api/v1/categories` from `Origin: http://evil.example.com` | — | **403, no `Allow-Origin`** (policy still restrictive — not opened to all origins) |

`./gradlew test --tests "com.spendwise.auth.*"` → **BUILD SUCCESSFUL** (no regression from adding `.cors(...)` to the chains). `./gradlew compileJava` → clean.

**Conclusion:** the authenticated browser surface (user dashboard + admin portal) is unblocked; the documented CORS policy (localhost:3000 / 127.0.0.1:3000 only) is preserved and correctly rejects other origins.
