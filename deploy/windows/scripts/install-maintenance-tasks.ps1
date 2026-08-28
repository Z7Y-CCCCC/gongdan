param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = "",
    [string]$StartupTaskName = "Gongdan Startup Recovery",
    [string]$BackupTaskName = "Gongdan Database Backup",
    [string]$CleanupTaskName = "Gongdan Log Cleanup",
    [string]$MonitorTaskName = "Gongdan Health Monitor",
    [switch]$WhatIf
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $EnvFile) {
    $EnvFile = Join-Path $ReleaseRoot "config\gongdan.env"
}
if (-not (Test-Path -LiteralPath $EnvFile)) { throw "Missing config file: $EnvFile" }

function Read-DotEnv([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) { continue }
        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) { continue }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$name] = $value
    }
    return $values
}

function Get-ConfigValue([hashtable]$Config, [string]$Name, [string]$Default = "") {
    if ($Config.ContainsKey($Name) -and $null -ne $Config[$Name]) { return [string]$Config[$Name] }
    return $Default
}

function New-ScriptTaskAction([string]$ScriptPath) {
    $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
    $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$ScriptPath`" -ReleaseRoot `"$ReleaseRoot`" -EnvFile `"$EnvFile`""
    return New-ScheduledTaskAction -Execute $powershell -Argument $arguments -WorkingDirectory $ReleaseRoot
}

function Register-GongdanTask([string]$Name, $Trigger, $Action) {
    if ($WhatIf) {
        Write-Host "Would register scheduled task: $Name" -ForegroundColor DarkYellow
        return
    }
    $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 5 -RestartInterval (New-TimeSpan -Minutes 2)
    Register-ScheduledTask -TaskName $Name -Action $Action -Trigger $Trigger -Principal $principal -Settings $settings -Force | Out-Null
    Write-Host "Registered scheduled task: $Name" -ForegroundColor Green
}

$config = Read-DotEnv $EnvFile
$startupScript = Join-Path $ReleaseRoot "scripts\start-stack.ps1"
$backupScript = Join-Path $ReleaseRoot "scripts\backup-database.ps1"
$cleanupScript = Join-Path $ReleaseRoot "scripts\cleanup-logs.ps1"
$monitorScript = Join-Path $ReleaseRoot "scripts\monitor.ps1"
foreach ($scriptPath in @($startupScript, $backupScript, $cleanupScript, $monitorScript)) {
    if (-not (Test-Path -LiteralPath $scriptPath)) { throw "Required maintenance script was not found: $scriptPath" }
}

$mysqlServiceName = Get-ConfigValue $config "MYSQL_SERVICE_NAME" ""
if ($mysqlServiceName) {
    $mysqlService = Get-Service -Name $mysqlServiceName -ErrorAction SilentlyContinue
    if (-not $mysqlService) {
        Write-Warning "MySQL service was not found: $mysqlServiceName. The startup task will fail until the name is corrected."
    } elseif (-not $WhatIf) {
        Set-Service -Name $mysqlServiceName -StartupType Automatic
        Write-Host "MySQL service set to Automatic: $mysqlServiceName" -ForegroundColor Cyan
    } else {
        Write-Host "Would set MySQL service to Automatic: $mysqlServiceName" -ForegroundColor DarkYellow
    }
}

$nginxServiceName = Get-ConfigValue $config "NGINX_SERVICE_NAME" ""
if ($nginxServiceName) {
    $nginxService = Get-Service -Name $nginxServiceName -ErrorAction SilentlyContinue
    if ($nginxService -and -not $WhatIf) {
        Set-Service -Name $nginxServiceName -StartupType Automatic
        Write-Host "nginx service set to Automatic: $nginxServiceName" -ForegroundColor Cyan
    } elseif ($nginxService) {
        Write-Host "Would set nginx service to Automatic: $nginxServiceName" -ForegroundColor DarkYellow
    } else {
        Write-Warning "nginx service was not found: $nginxServiceName"
    }
}

$startupTrigger = New-ScheduledTaskTrigger -AtStartup
$backupTrigger = New-ScheduledTaskTrigger -Daily -At (Get-Date -Hour 2 -Minute 0 -Second 0)
$cleanupTrigger = New-ScheduledTaskTrigger -Daily -At (Get-Date -Hour 4 -Minute 0 -Second 0)
$monitorTrigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) `
    -RepetitionInterval (New-TimeSpan -Minutes 5) -RepetitionDuration (New-TimeSpan -Days 3650)

Register-GongdanTask $StartupTaskName $startupTrigger (New-ScriptTaskAction $startupScript)
Register-GongdanTask $BackupTaskName $backupTrigger (New-ScriptTaskAction $backupScript)
Register-GongdanTask $CleanupTaskName $cleanupTrigger (New-ScriptTaskAction $cleanupScript)
Register-GongdanTask $MonitorTaskName $monitorTrigger (New-ScriptTaskAction $monitorScript)

if ($WhatIf) {
    Write-Host "Maintenance task preview completed; no system changes were made." -ForegroundColor Cyan
} else {
    Write-Host "Startup recovery, daily database backup, daily log cleanup, and five-minute monitoring are installed." -ForegroundColor Green
}
