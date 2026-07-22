param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$pidFile = Join-Path $ReleaseRoot "runtime\backend.pid"
if (-not (Test-Path -LiteralPath $pidFile)) {
    Write-Host "Backend is not running (PID file not found)." -ForegroundColor Yellow
    exit 0
}

$backendPid = [int](Get-Content -LiteralPath $pidFile -Raw)
$process = Get-Process -Id $backendPid -ErrorAction SilentlyContinue
if ($process) {
    & taskkill.exe /PID $backendPid /T /F | Out-Null
}
Remove-Item -LiteralPath $pidFile -Force
Write-Host "Backend stopped." -ForegroundColor Green
