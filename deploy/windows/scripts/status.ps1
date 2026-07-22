param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$envFile = Join-Path $ReleaseRoot "config\gongdan.env"
$port = "18080"
if (Test-Path -LiteralPath $envFile) {
    $portLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^SERVER_PORT=' } | Select-Object -Last 1
    if ($portLine) { $port = $portLine.Substring($portLine.IndexOf('=') + 1).Trim() }
}

$pidFile = Join-Path $ReleaseRoot "runtime\backend.pid"
$backendState = "stopped"
if (Test-Path -LiteralPath $pidFile) {
    $backendPid = [int](Get-Content -LiteralPath $pidFile -Raw)
    if (Get-Process -Id $backendPid -ErrorAction SilentlyContinue) {
        $backendState = "running (PID=$backendPid)"
    }
}

$healthState = "unreachable"
try {
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:$port/actuator/health/readiness" -TimeoutSec 3
    $healthState = $health.status
} catch {
}

Write-Host "Backend: $backendState"
Write-Host "Health:  $healthState"
Write-Host "Admin:   http://SERVER_IP:8088/"
Write-Host "Mobile:  http://SERVER_IP:8088/mobile/"
