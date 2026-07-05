# run-local.ps1 - start the SpendWise backend with backend/.env loaded.
#
# Spring Boot / Gradle do NOT read .env files automatically (there is no dotenv
# plugin in build.gradle.kts). "./gradlew bootRun" on its own boots the app but
# leaves FIREBASE_PROJECT_ID, ADMIN_USERNAME, ADMIN_PASSWORD_HASH, etc. empty -
# so web OTP login returns 403 and admin login returns 401. This script loads
# every KEY=VALUE from backend/.env into the process environment first, then
# runs bootRun, so auth actually works locally.
#
# Usage (from the backend/ directory):
#   .\run-local.ps1
#
# Postgres must already be up:  docker compose up -d

$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot ".env"
$dquote = [char]34
$squote = [char]39

if (Test-Path $envFile) {
    Write-Host "Loading environment from $envFile" -ForegroundColor Cyan
    foreach ($raw in Get-Content $envFile) {
        $line = $raw.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { continue }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { continue }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        $val = $val.Trim($dquote).Trim($squote)
        Set-Item -Path ("Env:" + $key) -Value $val
        Write-Host ("  set " + $key)
    }
} else {
    Write-Warning "No .env file at $envFile - booting with defaults (auth will not work)."
}

Write-Host "Starting backend (./gradlew bootRun)..." -ForegroundColor Green
& (Join-Path $PSScriptRoot "gradlew.bat") bootRun
