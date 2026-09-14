param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot 'backend-process.ps1')

$pidFile = Join-Path $ReleaseRoot "runtime\backend.pid"
if (-not (Test-Path -LiteralPath $pidFile)) {
    Write-Host "Backend is not running (PID file not found)." -ForegroundColor Yellow
    exit 0
}

$backendPid = [int](Get-Content -LiteralPath $pidFile -Raw)
$process = Get-VerifiedBackendProcess $backendPid (Join-Path $ReleaseRoot 'server\app.jar')
if ($process) {
    Stop-Process -InputObject $process -Force -ErrorAction Stop
} else {
    Write-Warning 'The recorded PID is absent or belongs to another application; no process was stopped.'
}
Remove-Item -LiteralPath $pidFile -Force
Write-Host "Backend stopped." -ForegroundColor Green
