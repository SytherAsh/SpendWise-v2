<#
    SpendWise — start the full local stack.

    Opens four separate PowerShell terminal windows, one per service, exactly
    like the manual flow in README.md:

        1. Postgres   (Docker)     backend/  -> docker compose up -d + logs
        2. Backend    (Spring Boot) backend/ -> .\run-local.ps1   (waits for Postgres first)
        3. ML service (FastAPI)     ml/       -> activates .venv, then uvicorn on :8000
        4. Frontend   (Next.js)     frontend/ -> npm run dev on :3000

    Usage (from anywhere):
        powershell -ExecutionPolicy Bypass -File .\start.ps1
    Or from the repo root:
        .\start.ps1
#>

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

Write-Host "Starting SpendWise stack from $root" -ForegroundColor Cyan

# --- 1. Postgres (Docker) --------------------------------------------------
# Detached compose start, then follow logs so the window stays useful.
Start-Process powershell -ArgumentList @(
    '-NoExit', '-Command',
    "Set-Location '$root\backend'; " +
    "Write-Host '[Postgres] docker compose up -d' -ForegroundColor Green; " +
    "docker compose up -d; docker compose logs -f"
)

# --- 2. Backend (Spring Boot) ----------------------------------------------
# Wait for Postgres to accept connections on 5433 before booting, because
# Flyway migrations run against it on startup.
$backendCmd =
    "Set-Location '$root\backend'; " +
    "Write-Host '[Backend] waiting for Postgres on 5433...' -ForegroundColor Yellow; " +
    "while (-not (Test-NetConnection -ComputerName localhost -Port 5433 -InformationLevel Quiet -WarningAction SilentlyContinue)) { Start-Sleep -Seconds 1 }; " +
    "Write-Host '[Backend] Postgres is up. Running run-local.ps1' -ForegroundColor Green; " +
    ".\run-local.ps1"
Start-Process powershell -ArgumentList @('-NoExit', '-Command', $backendCmd)

# --- 3. ML service (FastAPI) -----------------------------------------------
$mlCmd =
    "Set-Location '$root\ml'; " +
    "Write-Host '[ML] activating .venv' -ForegroundColor Green; " +
    ".\.venv\Scripts\Activate.ps1; " +
    "uvicorn api.main:app --reload --port 8000"
Start-Process powershell -ArgumentList @('-NoExit', '-Command', $mlCmd)

# --- 4. Frontend (Next.js) -------------------------------------------------
$frontendCmd =
    "Set-Location '$root\frontend'; " +
    "Write-Host '[Frontend] npm run dev' -ForegroundColor Green; " +
    "npm run dev"
Start-Process powershell -ArgumentList @('-NoExit', '-Command', $frontendCmd)

Write-Host ""
Write-Host "Four terminals launched. Wait for 'Started SpendwiseApplication' in the Backend window," -ForegroundColor Cyan
Write-Host "then open http://localhost:3000/login" -ForegroundColor Cyan
