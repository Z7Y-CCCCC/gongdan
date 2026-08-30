param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $EnvFile) { $EnvFile = Join-Path $ReleaseRoot "config\gongdan.env" }

function Read-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Missing config file: $Path" }
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

function Get-Value([hashtable]$Config, [string]$Name, [string]$Default = "") {
    if ($Config.ContainsKey($Name) -and $null -ne $Config[$Name]) { return [string]$Config[$Name] }
    return $Default
}

function Test-HttpEndpoint([string]$Name, [string]$Url) {
    $started = Get-Date
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 8
        return [pscustomobject]@{
            name = $Name
            status = if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) { "UP" } else { "DOWN" }
            detail = "HTTP $($response.StatusCode)"
            durationMs = [int]((Get-Date) - $started).TotalMilliseconds
        }
    } catch {
        return [pscustomobject]@{
            name = $Name
            status = "DOWN"
            detail = $_.Exception.Message
            durationMs = [int]((Get-Date) - $started).TotalMilliseconds
        }
    }
}

$config = Read-DotEnv $EnvFile
$runtimeDir = Join-Path $ReleaseRoot "runtime"
$logsDir = Join-Path $ReleaseRoot "logs"
New-Item -ItemType Directory -Path $runtimeDir, $logsDir -Force | Out-Null
$checks = New-Object System.Collections.Generic.List[object]

$checks.Add((Test-HttpEndpoint "frontend" (Get-Value $config "MONITOR_FRONTEND_URL" "http://127.0.0.1:8088/")))
$checks.Add((Test-HttpEndpoint "mobile" (Get-Value $config "MONITOR_MOBILE_URL" "http://127.0.0.1:8088/mobile/")))
$checks.Add((Test-HttpEndpoint "backend-readiness" (Get-Value $config "MONITOR_HEALTH_URL" "http://127.0.0.1:8088/health")))

$mysqlServiceName = Get-Value $config "MYSQL_SERVICE_NAME" ""
if ($mysqlServiceName) {
    $mysqlService = Get-Service -Name $mysqlServiceName -ErrorAction SilentlyContinue
    $checks.Add([pscustomobject]@{
        name = "mysql-service"
        status = if ($mysqlService -and $mysqlService.Status -eq "Running") { "UP" } else { "DOWN" }
        detail = if ($mysqlService) { "$($mysqlService.Status) ($mysqlServiceName)" } else { "service not found ($mysqlServiceName)" }
        durationMs = 0
    })
}

$backupDirValue = Get-Value $config "BACKUP_DIR" (Join-Path $ReleaseRoot "data\backups")
$backupDir = if ([System.IO.Path]::IsPathRooted($backupDirValue)) { [System.IO.Path]::GetFullPath($backupDirValue) } else { [System.IO.Path]::GetFullPath((Join-Path $ReleaseRoot $backupDirValue)) }
$maxBackupAgeHours = [Math]::Max(1, [int](Get-Value $config "MONITOR_BACKUP_MAX_AGE_HOURS" "30"))
$backupStatusPath = Join-Path $backupDir "backup-status.json"
if (-not (Test-Path -LiteralPath $backupStatusPath)) {
    $checks.Add([pscustomobject]@{ name = "database-backup"; status = "DOWN"; detail = "backup status missing: $backupStatusPath"; durationMs = 0 })
} else {
    try {
        $statusExpectedHash = ((Get-Content -LiteralPath "$backupStatusPath.sha256" -Raw).Split(' ')[0]).Trim().ToLowerInvariant()
        $statusActualHash = (Get-FileHash -LiteralPath $backupStatusPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($statusExpectedHash -ne $statusActualHash) { throw "status checksum failed" }
        $backupStatus = Get-Content -LiteralPath $backupStatusPath -Raw | ConvertFrom-Json
        $databasePath = Join-Path $backupDir ([string]$backupStatus.databaseFile)
        if (-not (Test-Path -LiteralPath $databasePath)) { throw "database file missing: $databasePath" }
        $expectedHash = ((Get-Content -LiteralPath "$databasePath.sha256" -Raw).Split(' ')[0]).Trim().ToLowerInvariant()
        $actualHash = (Get-FileHash -LiteralPath $databasePath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($expectedHash -ne $actualHash) { throw "database checksum failed" }
        $backupAgeHours = [Math]::Round(((Get-Date) - [datetime]$backupStatus.lastSuccessAt).TotalHours, 2)
        $checks.Add([pscustomobject]@{
            name = "database-backup"
            status = if ($backupAgeHours -le $maxBackupAgeHours) { "UP" } else { "DOWN" }
            detail = "$($backupStatus.databaseFile); ageHours=$backupAgeHours; checksum=ok; secondary=$($backupStatus.secondaryConfigured); remote=$($backupStatus.remoteConfigured)"
            durationMs = 0
        })
    } catch {
        $checks.Add([pscustomobject]@{ name = "database-backup"; status = "DOWN"; detail = $_.Exception.Message; durationMs = 0 })
    }
}

if ((Get-Value $config "BACKUP_REQUIRE_PITR" "true").Trim().ToLowerInvariant() -eq "true") {
    $binlogDirValue = Get-Value $config "BINLOG_ARCHIVE_DIR" (Join-Path $backupDir "binlogs")
    $binlogDir = if ([System.IO.Path]::IsPathRooted($binlogDirValue)) { [System.IO.Path]::GetFullPath($binlogDirValue) } else { [System.IO.Path]::GetFullPath((Join-Path $ReleaseRoot $binlogDirValue)) }
    $binlogStatusPath = Join-Path $binlogDir "binlog-archive-status.json"
    $maxBinlogAgeMinutes = [Math]::Max(1, [int](Get-Value $config "BINLOG_ARCHIVE_MAX_AGE_MINUTES" "10"))
    if (-not (Test-Path -LiteralPath $binlogStatusPath)) {
        $checks.Add([pscustomobject]@{ name = "binlog-archive"; status = "DOWN"; detail = "binlog status missing: $binlogStatusPath"; durationMs = 0 })
    } else {
        try {
            $expectedStatusHash = ((Get-Content -LiteralPath "$binlogStatusPath.sha256" -Raw).Split(' ')[0]).Trim().ToLowerInvariant()
            $actualStatusHash = (Get-FileHash -LiteralPath $binlogStatusPath -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($expectedStatusHash -ne $actualStatusHash) { throw "binlog status checksum failed" }
            $binlogStatus = Get-Content -LiteralPath $binlogStatusPath -Raw | ConvertFrom-Json
            $latestBinlogPath = Join-Path $binlogDir ([string]$binlogStatus.latestArchivedFile)
            if (-not (Test-Path -LiteralPath $latestBinlogPath)) { throw "latest binlog missing: $latestBinlogPath" }
            $binlogExpectedHash = ((Get-Content -LiteralPath "$latestBinlogPath.sha256" -Raw).Split(' ')[0]).Trim().ToLowerInvariant()
            $binlogActualHash = (Get-FileHash -LiteralPath $latestBinlogPath -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($binlogExpectedHash -ne $binlogActualHash) { throw "latest binlog checksum failed" }
            $binlogAgeMinutes = [Math]::Round(((Get-Date) - [datetime]$binlogStatus.archivedAt).TotalMinutes, 2)
            $checks.Add([pscustomobject]@{
                name = "binlog-archive"
                status = if ($binlogAgeMinutes -le $maxBinlogAgeMinutes) { "UP" } else { "DOWN" }
                detail = "$($binlogStatus.latestArchivedFile); ageMinutes=$binlogAgeMinutes; checksum=ok"
                durationMs = 0
            })
        } catch {
            $checks.Add([pscustomobject]@{ name = "binlog-archive"; status = "DOWN"; detail = $_.Exception.Message; durationMs = 0 })
        }
    }
}

$root = [System.IO.Path]::GetPathRoot([System.IO.Path]::GetFullPath($ReleaseRoot))
$minFreeGb = [Math]::Max(1, [int](Get-Value $config "MONITOR_MIN_FREE_GB" "5"))
foreach ($diskItem in @(@("disk-space", $root), @("backup-disk-space", [System.IO.Path]::GetPathRoot($backupDir)))) {
    $driveInfo = New-Object System.IO.DriveInfo($diskItem[1])
    if ($driveInfo.IsReady) {
        # DriveInfo also works for SYSTEM scheduled tasks where PowerShell PSDrive mappings differ.
        $freeGb = [Math]::Round($driveInfo.AvailableFreeSpace / 1073741824, 2)
        $checks.Add([pscustomobject]@{
            name = $diskItem[0]
            status = if ($freeGb -ge $minFreeGb) { "UP" } else { "DOWN" }
            detail = "drive=$($diskItem[1]); freeGb=$freeGb; minimumGb=$minFreeGb"
            durationMs = 0
        })
    }
}

$failedChecks = @($checks | Where-Object { $_.status -ne "UP" })
$result = [pscustomobject]@{
    status = if ($failedChecks.Count -eq 0) { "UP" } else { "DOWN" }
    checkedAt = (Get-Date).ToString("o")
    host = $env:COMPUTERNAME
    checks = $checks
}
$statusPath = Join-Path $runtimeDir "monitor-status.json"
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $statusPath -Encoding UTF8

if ($failedChecks.Count -gt 0) {
    $message = "Gongdan monitor DOWN on $($env:COMPUTERNAME): " + (($failedChecks | ForEach-Object { $_.name }) -join ", ")
    Add-Content -LiteralPath (Join-Path $logsDir "maintenance.log") -Value ("{0} {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $message) -Encoding UTF8

    $webhook = Get-Value $config "ALERT_WEBHOOK_URL" ""
    $cooldownMinutes = [Math]::Max(1, [int](Get-Value $config "MONITOR_ALERT_COOLDOWN_MINUTES" "30"))
    $alertStatePath = Join-Path $runtimeDir "monitor-last-alert.json"
    $canAlert = $true
    if (Test-Path -LiteralPath $alertStatePath) {
        try {
            $lastAlert = Get-Content -LiteralPath $alertStatePath -Raw | ConvertFrom-Json
            $canAlert = ((Get-Date) - [datetime]$lastAlert.sentAt).TotalMinutes -ge $cooldownMinutes
        } catch {
            $canAlert = $true
        }
    }
    if ($webhook -and $canAlert) {
        try {
            Invoke-RestMethod -Uri $webhook -Method Post -ContentType "application/json" -Body (@{ text = $message } | ConvertTo-Json -Compress) -TimeoutSec 8 | Out-Null
            @{ sentAt = (Get-Date).ToString("o"); message = $message } | ConvertTo-Json | Set-Content -LiteralPath $alertStatePath -Encoding UTF8
        } catch {
            Add-Content -LiteralPath (Join-Path $logsDir "maintenance.log") -Value ("{0} Monitor webhook failed: {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $_.Exception.Message) -Encoding UTF8
        }
    }
    Write-Host $message -ForegroundColor Red
    exit 1
}

Write-Host "Gongdan monitor passed. Evidence: $statusPath" -ForegroundColor Green
