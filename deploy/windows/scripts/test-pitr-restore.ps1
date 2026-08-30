param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = "",
    [string]$BackupPath = "",
    [string]$StopDateTime = ""
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
    if ($Config.ContainsKey($Name)) { return [string]$Config[$Name] }
    return $Default
}

function ConvertTo-MySqlOptionValue([string]$Value) {
    $escaped = $Value.Replace('\', '\\').Replace('"', '\"').Replace("`r", '\r').Replace("`n", '\n')
    return '"' + $escaped + '"'
}

function Find-Executable([string]$Name, [string]$ConfiguredPath, [string[]]$Candidates) {
    if ($ConfiguredPath) {
        if (-not (Test-Path -LiteralPath $ConfiguredPath)) { throw "$Name was not found: $ConfiguredPath" }
        return (Resolve-Path -LiteralPath $ConfiguredPath).Path
    }
    foreach ($candidate in $Candidates) { if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate } }
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw "$Name was not found"
}

$config = Read-DotEnv $EnvFile
$backupDirValue = Get-Value $config "BACKUP_DIR" (Join-Path $ReleaseRoot "data\backups")
$backupDir = [System.IO.Path]::GetFullPath($backupDirValue)
if (-not $BackupPath) {
    $latest = Get-ChildItem -LiteralPath $backupDir -Filter "mysql_*.sql" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latest) { throw "No database backup was found in $backupDir" }
    $BackupPath = $latest.FullName
}
if (-not (Test-Path -LiteralPath $BackupPath -PathType Leaf)) { throw "Backup was not found: $BackupPath" }
$expectedHash = ((Get-Content -LiteralPath "$BackupPath.sha256" -Raw).Split(' ')[0]).Trim().ToLowerInvariant()
$actualHash = (Get-FileHash -LiteralPath $BackupPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($expectedHash -ne $actualHash) { throw "Backup checksum verification failed: $BackupPath" }

$coordinateLine = Select-String -LiteralPath $BackupPath -Pattern '^-- CHANGE (REPLICATION SOURCE|MASTER) TO ' | Select-Object -First 1
if (-not $coordinateLine) { throw "Backup does not contain binary log coordinates. Create a new backup with --source-data=2." }
$fileMatch = [regex]::Match($coordinateLine.Line, "(?:SOURCE_LOG_FILE|MASTER_LOG_FILE)='(?<file>[^']+)'")
$positionMatch = [regex]::Match($coordinateLine.Line, '(?:SOURCE_LOG_POS|MASTER_LOG_POS)=(?<position>\d+)')
if (-not $fileMatch.Success -or -not $positionMatch.Success) { throw "Unable to parse binary log coordinates" }
$startFile = $fileMatch.Groups["file"].Value
$startPosition = [long]$positionMatch.Groups["position"].Value
if ($startFile -notmatch '^[A-Za-z0-9_.-]+\.\d+$') { throw "Unsafe binary log name: $startFile" }

$jdbcUrl = Get-Value $config "SPRING_DATASOURCE_URL" ""
$jdbcMatch = [regex]::Match($jdbcUrl, '^jdbc:mysql://(?<host>[^:/?]+)(:(?<port>\d+))?/(?<database>[^?]+)')
if (-not $jdbcMatch.Success) { throw "SPRING_DATASOURCE_URL is not a supported MySQL JDBC URL: $jdbcUrl" }
$dbHost = $jdbcMatch.Groups["host"].Value
$dbPort = if ($jdbcMatch.Groups["port"].Success) { $jdbcMatch.Groups["port"].Value } else { "3306" }
$database = $jdbcMatch.Groups["database"].Value
$dbUser = Get-Value $config "RESTORE_MYSQL_USER" ""
$dbPassword = Get-Value $config "RESTORE_MYSQL_PASSWORD" ""
if (-not $dbUser -or -not $dbPassword) { throw "RESTORE_MYSQL_USER and RESTORE_MYSQL_PASSWORD are required" }

$dumpPath = Get-Value $config "BACKUP_MYSQL_EXE" ""
$binDir = if ($dumpPath) { Split-Path -Parent $dumpPath } else { "" }
$mysql = Find-Executable "mysql.exe" (Get-Value $config "BACKUP_MYSQL_CLIENT_EXE" "") @(
    $(if ($binDir) { Join-Path $binDir "mysql.exe" }),
    "C:\Program Files\MySQL\MySQL Server 9.2\bin\mysql.exe",
    "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
)
$mysqlbinlog = Find-Executable "mysqlbinlog.exe" (Get-Value $config "BACKUP_MYSQL_BINLOG_EXE" "") @(
    $(if ($binDir) { Join-Path $binDir "mysqlbinlog.exe" }),
    "C:\Program Files\MySQL\MySQL Server 9.2\bin\mysqlbinlog.exe",
    "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqlbinlog.exe"
)

$archiveDirValue = Get-Value $config "BINLOG_ARCHIVE_DIR" (Join-Path $backupDir "binlogs")
$archiveDir = [System.IO.Path]::GetFullPath($archiveDirValue)
$logs = @(Get-ChildItem -LiteralPath $archiveDir -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^[A-Za-z0-9_.-]+\.\d+$' } |
    Sort-Object Name)
$startIndex = -1
for ($index = 0; $index -lt $logs.Count; $index++) { if ($logs[$index].Name -eq $startFile) { $startIndex = $index; break } }
if ($startIndex -lt 0) { throw "Required start binlog was not found: $startFile" }

$tempDatabase = "gongdan_pitr_drill_{0}" -f (Get-Date -Format "yyyyMMddHHmmss")
$optionPath = Join-Path ([System.IO.Path]::GetTempPath()) "gongdan-pitr-$([guid]::NewGuid().ToString('N')).cnf"
$sqlReplayPath = Join-Path ([System.IO.Path]::GetTempPath()) "gongdan-pitr-$([guid]::NewGuid().ToString('N')).sql"
$errorPath = Join-Path ([System.IO.Path]::GetTempPath()) "gongdan-pitr-$([guid]::NewGuid().ToString('N')).err"
$created = $false
$started = Get-Date
$normalizedStopDateTime = ""
if ($StopDateTime) {
    $parsedStopDateTime = [datetime]::MinValue
    if (-not [datetime]::TryParse($StopDateTime, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::AllowWhiteSpaces, [ref]$parsedStopDateTime)) {
        throw "StopDateTime must be a valid date and time: $StopDateTime"
    }
    $normalizedStopDateTime = $parsedStopDateTime.ToString("yyyy-MM-ddTHH:mm:ss", [Globalization.CultureInfo]::InvariantCulture)
}

try {
    $optionContent = "[client]`r`nprotocol=tcp`r`nhost=$dbHost`r`nport=$dbPort`r`nuser=$(ConvertTo-MySqlOptionValue $dbUser)`r`npassword=$(ConvertTo-MySqlOptionValue $dbPassword)`r`n"
    [System.IO.File]::WriteAllText($optionPath, $optionContent, (New-Object System.Text.UTF8Encoding($false)))
    $baseArguments = @("--defaults-extra-file=$optionPath")
    & $mysql @baseArguments "--execute=CREATE DATABASE ``$tempDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    if ($LASTEXITCODE -ne 0) { throw "Unable to create PITR drill database" }
    $created = $true

    $restore = Start-Process -FilePath $mysql -ArgumentList @("--defaults-extra-file=$optionPath", "--database=$tempDatabase") -RedirectStandardInput $BackupPath -RedirectStandardError $errorPath -Wait -PassThru -NoNewWindow
    if ($restore.ExitCode -ne 0) { throw "Unable to restore the base backup" }

    $replayed = 0
    for ($index = $startIndex; $index -lt $logs.Count; $index++) {
        $log = $logs[$index]
        $logExpectedHash = ((Get-Content -LiteralPath "$($log.FullName).sha256" -Raw).Split(' ')[0]).Trim().ToLowerInvariant()
        $logActualHash = (Get-FileHash -LiteralPath $log.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($logExpectedHash -ne $logActualHash) { throw "Binlog checksum failed: $($log.FullName)" }

        $arguments = @("--skip-gtids=true", "--database=$database", "--rewrite-db=$database->$tempDatabase")
        if ($replayed -eq 0) { $arguments += "--start-position=$startPosition" }
        if ($normalizedStopDateTime) { $arguments += "--stop-datetime=$normalizedStopDateTime" }
        $arguments += $log.FullName
        $decode = Start-Process -FilePath $mysqlbinlog -ArgumentList $arguments -RedirectStandardOutput $sqlReplayPath -RedirectStandardError $errorPath -Wait -PassThru -NoNewWindow
        if ($decode.ExitCode -ne 0) { throw "mysqlbinlog failed for $($log.Name)" }
        $replay = Start-Process -FilePath $mysql -ArgumentList @("--defaults-extra-file=$optionPath", "--init-command=SET SESSION sql_log_bin=0", "--database=$tempDatabase") -RedirectStandardInput $sqlReplayPath -RedirectStandardError $errorPath -Wait -PassThru -NoNewWindow
        if ($replay.ExitCode -ne 0) { throw "Binlog replay failed for $($log.Name)" }
        $replayed++
    }

    $criticalText = & $mysql @baseArguments "--database=$tempDatabase" "--batch" "--skip-column-names" "--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('factories','factory_users','work_orders')"
    if ($LASTEXITCODE -ne 0 -or [int]$criticalText -ne 3) { throw "PITR critical table validation failed" }
    $duration = [int]((Get-Date) - $started).TotalSeconds
    $drillLog = Join-Path $backupDir "restore-drills.log"
    Add-Content -LiteralPath $drillLog -Value ("{0} pitr_drill=passed backup={1} start={2}:{3} replayed={4} stop={5} duration_seconds={6}" -f (Get-Date -Format "o"), $BackupPath, $startFile, $startPosition, $replayed, $(if ($StopDateTime) { $StopDateTime } else { "latest" }), $duration) -Encoding UTF8
    Write-Host "PITR drill passed: start=$startFile`:$startPosition replayed=$replayed duration=${duration}s" -ForegroundColor Green
} finally {
    if ($created) { & $mysql "--defaults-extra-file=$optionPath" "--execute=DROP DATABASE IF EXISTS ``$tempDatabase``" | Out-Null }
    Remove-Item -LiteralPath $optionPath, $sqlReplayPath, $errorPath -Force -ErrorAction SilentlyContinue
}
