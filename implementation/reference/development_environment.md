# Development Environment — Source of Truth

This document records the local development environment used for SpendWise
implementation. It is the single reference for installed tooling and required
framework/SDK versions — consult it instead of re-deriving version information
in future sessions. Update it only when the actual local setup changes (new
install, version bump); it is a record of environment state, not a spec
document.

Last verified: 2026-07-01 (Docker Desktop, Engine, and WSL2 backend confirmed fully operational).

**Status: frozen.** This document reflects a fully verified local environment.
Do not edit it again unless the user explicitly reports a change to their
local machine (new install, version change, OS change, etc.).

---

## 1. Local machine snapshot

| Tool | Installed version | Path |
| --- | --- | --- |
| OS | Windows 11 Home Single Language, build 10.0.26200 | — |
| Java (JDK) | 21.0.9 LTS (HotSpot) | `C:\Program Files\Java\jdk-21` (`JAVA_HOME` set) |
| Node.js | v22.12.0 | on PATH |
| npm | 10.9.0 | bundled with Node |
| Python | 3.11.4 (primary) | `C:\Users\yashs\AppData\Local\Programs\Python\Python311\python.exe` |
| Python (secondary) | 3.11.9 | `C:\Users\yashs\AppData\Local\Microsoft\WindowsApps\python.exe` — Windows Store alias, resolves after the primary install in PATH order |
| pip | 23.1.2 | tied to the Python 3.11.4 (Python311) install |
| Git | 2.45.0.windows.1 | on PATH |
| Android Studio | 2025.3.4 (build `AI-253.32098.37.2534.15232325`) | `C:\Program Files\Android\Android Studio` |
| Android SDK | Installed, `ANDROID_HOME` = `C:\Users\yashs\AppData\Local\Android\Sdk` | env var set correctly |
| Android platform | `android-36.1` (API 36) | `%ANDROID_HOME%\platforms` |
| Android build-tools | 36.0.0, 36.1.0, 37.0.0 | `%ANDROID_HOME%\build-tools` |
| `adb` | 1.0.41 (platform-tools 37.0.0-14910828) | on PATH via `%ANDROID_HOME%\platform-tools` |
| AVD (emulator image) | None created yet | `~/.android/avd` is empty |
| Gradle | Not installed globally (by design) | provided via Gradle Wrapper — see §3 |
| Docker Desktop | **29.6.1** (build `8900f1d`) — Engine running, CLI/`docker info`/`docker ps` verified working | `C:\Program Files\Docker\Docker`, on system PATH |
| WSL2 backend | Verified — `Ubuntu-24.04`, WSL version 2 | used by Docker Desktop on Windows |

### Notes on the current snapshot

- **Python 3.11.4 (`Python311` install) is the primary interpreter.** A
  second Python (3.11.9, Windows Store/WindowsApps alias) is also on PATH
  after the primary in resolution order; `pip` correctly targets the
  primary `Python311` install. Both satisfy the project's `Python 3.10+`
  requirement. **Always create virtual environments for `ml/` using the
  primary interpreter explicitly**
  (`Python311\python.exe -m venv .venv`) rather than relying on whichever
  `python` resolves to first, so dependency installs are never split across
  the two interpreters.
- **No AVD (Android Virtual Device) exists yet.** The emulator binary and SDK
  platform/build-tools are present. Creating one via Android Studio's Device
  Manager (or connecting a physical device via `adb devices`) is
  **recommended before Android implementation work begins** (E0-S1-T3's
  "installs on an emulator" verification step), but it is not required to
  start Epic 0 more broadly.
- **Docker Desktop, Docker Engine, and the WSL2 backend are fully
  operational.** `docker --version`, `docker info`, and `docker ps` all
  succeed. See §4 — Testcontainers-backed integration tests are ready to
  use with no further setup.

---

## 2. Required framework/SDK versions (per finalized spec)

These are the versions fixed by `CLAUDE.md` and `docs/decisions.md` — treat
them as constraints, not preferences. Do not upgrade or downgrade without an
ADR update. Entries marked **(placeholder)** have no exact version pinned in
the docs yet — record the actual version once the corresponding service is
scaffolded, and do not invent a number before then.

| Component | Required version | Source |
| --- | --- | --- |
| Java | 21 (LTS) | `CLAUDE.md` Tech Stack; [ADR-009](../docs/decisions.md) |
| Spring Boot | 3.x, 3.2+ for virtual threads **(placeholder — record exact version at E0-S1-T1)** | `CLAUDE.md` Tech Stack; `spring.threads.virtual.enabled=true` per ADR-009 |
| Gradle | Whatever version the Gradle Wrapper pins (no global install) **(placeholder — record wrapper version at E0-S1-T1/T3)** | `implementation/epics/epic-00-foundation.md` E0-S1-T1 |
| Node.js | 18.17+ / 20+ recommended for Next.js App Router (no exact pin in docs) | `frontend/README.md` |
| Next.js / React | App Router, strict TypeScript **(placeholder — record exact version at E0-S1-T2)** | `frontend/README.md`; `docs/development_guidelines.md` |
| Kotlin | Bundled with the Android Gradle Plugin **(placeholder — record exact version at E0-S1-T3)** | `android/README.md` |
| Android min SDK | TBD during implementation — priority is SMS API compatibility; run/test target is **API 26+** | `android/README.md` |
| Android compile/target SDK (installed) | API 36 (`android-36.1`) | local SDK snapshot, §1 |
| Python | 3.10+ | `ml/README.md` |
| FastAPI | **(placeholder — record exact version at E0-S1-T4)** | `ml/README.md` |
| scikit-learn / pandas / numpy | Exact model TBD during training **(placeholder)** | `ml/README.md`; `docs/decisions.md` |
| PostgreSQL | Hosted via Supabase (no local server needed); Testcontainers provisions a real Postgres container for integration tests | `docs/deployment.md`; `docs/testing.md` "Why Testcontainers" |
| Firebase Authentication | Phone OTP + Google login, backend-issued JWT is authoritative (not Firebase ID tokens) | `CLAUDE.md` Auth pattern |

---

## 3. Tools provided by the project (do not install globally)

- **Gradle** — delivered as the Gradle Wrapper (`gradlew` / `gradlew.bat`) for
  both `backend/` (E0-S1-T1) and `android/` (E0-S1-T3). Always invoke via
  `./gradlew ...` so the pinned wrapper version is used consistently across
  machines. Confirmed no global Gradle is on PATH — correct, by design.
- **Flyway** (DB migrations) — wired into the Spring Boot Gradle build
  (E0-S2-T1); runs automatically on `./gradlew bootRun`, no separate CLI.
- **ESLint / TypeScript / Next.js CLI** — installed into `frontend/` via
  `npm install` against `package.json` once E0-S1-T2 lands.
- **black / pytest / FastAPI deps** — installed into `ml/` via
  `pip install -r requirements.txt` once E0-S1-T4 lands.
- **PostgreSQL** — no local server required; Supabase hosts dev/prod, and
  Testcontainers provisions ephemeral containers for integration tests.
- **Supabase CLI** — not required by the spec; the project connects via
  `SUPABASE_URL` / `SUPABASE_KEY` env vars against a project provisioned in
  the Supabase dashboard.

---

## 4. Docker — standard local container runtime (fully operational)

Docker Desktop **29.6.1**, Docker Engine, and the WSL2 backend
(`Ubuntu-24.04`, WSL version 2) are installed, running, and verified on this
machine. `docker --version`, `docker info`, and `docker ps` all succeed.
This is now the standard local container runtime for the project:

- **Testcontainers are fully supported** for Spring Boot integration tests
  (E0-S2-T2 through E0-S2-T6, and any later module's integration test
  suite per `docs/testing.md`) — no further setup required.
- **Local PostgreSQL integration testing is supported** — Testcontainers
  spins up a real ephemeral Postgres container per test run, matching the
  PostgreSQL-specific features the schema relies on (ENUM types, JSONB,
  `gen_random_uuid()`, `set_config()` for RLS session variables), exactly
  as described in `docs/testing.md`'s "Why Testcontainers" section.
- **CI parity**: GitHub Actions' `ubuntu-latest` runner already has Docker
  preinstalled for E0-S3-T1, so local and CI integration test behavior
  match.

There are no outstanding action items for Docker.

---

## 5. Compatibility notes to keep consistent throughout the project

- **Auth tokens**: Firebase validates OTP/Google credentials; the Spring
  Boot-issued JWT (7-day access + refresh) is the only credential accepted
  by backend APIs. Never accept a raw Firebase ID token at an API boundary.
- **Two distinct JWT secrets**: `JWT_SECRET` (user auth) and
  `ADMIN_JWT_SECRET` (admin auth) must never be interchangeable — each auth
  filter validates only its own secret.
- **`FASTAPI_ML_URL` uses plain HTTP** intentionally (internal private
  network between hosting platform services), authenticated at the
  application layer via `X-Internal-Key` / `ML_INTERNAL_KEY` — this is not
  a TLS regression, don't "fix" it to HTTPS without updating the docs.
- **Raw SMS text** never leaves the Android device and never appears in any
  API response — enforced via response DTOs, not entity serialization.
- **ML model artifacts** are committed to the repo at `MODEL_PATH`
  (`ml/models/`), not stored externally — do not introduce S3/GCS/model
  registry storage without an ADR update.
- **No paid-tier infrastructure**: all four services target free-tier
  hosting; no Redis/Kafka/RabbitMQ/Celery/external queue without explicit
  approval; background jobs run via Spring `@Scheduled` in-process.
- **Android min SDK is still TBD** — do not lock this in casually; it's
  driven by SMS API compatibility research, not a default Android Studio
  value.

### Version Upgrade Policy

- Frameworks and dependencies remain fixed throughout MVP development.
- Any upgrade of Java, Spring Boot, Gradle, React, Next.js, Android SDK,
  Python, FastAPI, PostgreSQL, or major libraries must first be evaluated
  for compatibility with the existing implementation before adoption.
- Avoid unnecessary upgrades during active feature development.

---

## 6. Development Readiness Report

### Ready ✔

- Java 21.0.9 LTS, `JAVA_HOME` correctly set
- Node.js v22.12.0 / npm 10.9.0
- Python 3.11.4 (primary interpreter) with matching pip 23.1.2 — satisfies `3.10+`
- Git 2.45.0
- Android Studio 2025.3.4 installed
- Android SDK installed, `ANDROID_HOME`/`ANDROID_SDK_ROOT` correctly set
- Android platform `android-36.1` and build-tools 36.0.0/36.1.0/37.0.0 present
- `adb` 1.0.41 on PATH and functional
- Gradle correctly absent globally — wrapper-only, per policy
- Docker Desktop 29.6.1, Docker Engine, and WSL2 backend (`Ubuntu-24.04`, v2) — all installed, running, and verified (`docker --version`, `docker info`, `docker ps` all succeed)
- Testcontainers-backed integration testing is fully supported, matching CI

### Needs Attention ⚠

- **Two Python interpreters on PATH** (3.11.4 primary vs. 3.11.9 WindowsApps alias). Not a functional blocker — always create the `ml/` virtualenv with the primary interpreter's explicit path to avoid drift.
- **No Android AVD created yet.** Recommended (not required) before Android implementation work begins — create one via Android Studio Device Manager, or plan to use a physical device instead.

### Missing ✖

- None. All dependencies required to start Epic 0 are present and verified.

---

## 7. Environment risks to watch for later

- **PATH conflicts (Python)**: two Python 3.11 installs on PATH in different
  order across tools (`python` vs `python3` vs IDE-configured interpreter)
  could cause `pip install` to land packages in the wrong environment.
  Pin the interpreter explicitly in any `ml/` tooling (venv, IDE run
  config, CI) rather than relying on bare `python`.
- **Docker Desktop / WSL2 resource contention**: WSL2 file I/O across the
  Windows/Linux boundary is slower than native Linux — if the Testcontainers
  Postgres container is ever bind-mounted to a Windows path, expect slower
  test runs than on native Linux CI. Keep test data ephemeral inside the
  container rather than bind-mounting from Windows where possible.
- **Docker Desktop requires a manual launch each session**: unlike a Linux
  CI runner, Docker Desktop on Windows doesn't always auto-start with the
  OS — if it's ever closed, Testcontainers-based tests will fail with a
  connection error rather than a clear "Docker not found" error. A quick
  `docker info` sanity check at the start of any session touching
  `backend/` integration tests avoids confusing failures.
- **Android SDK/platform mismatch**: only API 36 is installed locally, but
  Android min SDK is still TBD and could land anywhere from API 26 up.
  Once the min/target/compile SDK versions are decided in E0-S1-T3, verify
  the corresponding platform and build-tools are installed via SDK Manager
  — don't assume API 36 covers whatever `compileSdk` gets chosen later.
- **No AVD configured**: if an AVD is created later with a system image
  that doesn't match the eventual `targetSdk`, emulator behavior (especially
  around SMS broadcast permissions, since this app depends on
  `RECEIVE_SMS`) may not reflect real device behavior. Prefer creating the
  AVD with a system image matching the chosen target SDK once that's fixed,
  rather than defaulting to the newest available image.
- **Java version drift risk**: `JAVA_HOME` points at JDK 21 correctly today,
  but this machine's PATH also contains an Oracle Java entry
  (`Common Files\Oracle\Java\javapath`) ahead of some other paths — if a
  different JDK is ever installed for another project, re-verify
  `JAVA_HOME` and `java -version` still resolve to 21 before running
  `./gradlew bootRun`, since Gradle respects `JAVA_HOME`/PATH resolution
  order, not just whichever JDK was installed last.
