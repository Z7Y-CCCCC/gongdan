param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$TaskName = "Gongdan Startup Recovery"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$startScript = Join-Path $ReleaseRoot "scripts\start-stack.ps1"
if (-not (Test-Path -LiteralPath $startScript)) {
    throw "Stack start script not found: $startScript"
}

$argument = "-NoProfile -ExecutionPolicy Bypass -File `"$startScript`""
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $argument -WorkingDirectory $ReleaseRoot
$trigger = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
Write-Host "Registered stack recovery task: $TaskName" -ForegroundColor Green
Write-Host "The task waits for MySQL, starts the backend, checks the nginx health endpoint, and retries after a power loss." -ForegroundColor Cyan
Write-Host "Set JAVA_EXE and MYSQL_SERVICE_NAME in config\gongdan.env when the defaults do not match the server." -ForegroundColor Yellow
