<#
    SpendWise — stop the full local stack.

    - Stops the backend (:8080), ML service (:8000) and frontend (:3000) by
      killing whatever process is listening on each port. This works even
      though each runs in its own window (no need to Ctrl+C each one).
    - Stops the Postgres container with `docker compose down`
      (data survives in the Docker volume).

    Usage:
        .\stop.ps1
        .\stop.ps1 -KeepDb     # leave Postgres running, only stop the 3 app processes
#>

param(
    [switch]$KeepDb
)

$root = $PSScriptRoot

$services = @(
    @{ Name = 'Backend  (Spring Boot)'; Port = 8080 },
    @{ Name = 'ML       (FastAPI)';     Port = 8000 },
    @{ Name = 'Frontend (Next.js)';     Port = 3000 }
)

foreach ($svc in $services) {
    $conns = Get-NetTCPConnection -LocalPort $svc.Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) {
        Write-Host "[$($svc.Name)] nothing listening on $($svc.Port)" -ForegroundColor DarkGray
        continue
    }
    $pids = $conns.OwningProcess | Sort-Object -Unique
    foreach ($procId in $pids) {
        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            Write-Host "[$($svc.Name)] stopped PID $procId on port $($svc.Port)" -ForegroundColor Green
        } catch {
            Write-Host "[$($svc.Name)] could not stop PID $procId : $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

# --- Postgres (Docker) -----------------------------------------------------
if ($KeepDb) {
    Write-Host "[Postgres] left running (-KeepDb)" -ForegroundColor Yellow
} else {
    Write-Host "[Postgres] docker compose down" -ForegroundColor Green
    Push-Location "$root\backend"
    try {
        docker compose down
    } finally {
        Pop-Location
    }
}

Write-Host ""
Write-Host "SpendWise stack stopped. The four terminal windows can be closed." -ForegroundColor Cyan
