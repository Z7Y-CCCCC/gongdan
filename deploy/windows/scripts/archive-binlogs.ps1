param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = "",
    [string]$ArchiveDir = "",
    [int]$RetentionDays = 0,
    [string]$MysqlExe = ""
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
    $processValue = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ($null -ne $processValue) { return $processValue }
    if ($Config.ContainsKey($Name)) { return [string]$Config[$Name] }
    return $Default
}

function ConvertTo-MySqlOptionValue([string]$Value) {
    $escaped = $Value.Replace('\', '\\').Replace('"', '\"').Replace("`r", '\r').Replace("`n", '\n')
    return '"' + $escaped + '"'
}

function Find-MySqlClient([string]$ConfiguredPath, [string]$DumpPath) {
    if ($ConfiguredPath) {
        if (-not (Test-Path -LiteralPath $ConfiguredPath)) { throw "Configured mysql.exe was not found: $ConfiguredPath" }
        return (Resolve-Path -LiteralPath $ConfiguredPath).Path
    }
    if ($DumpPath) {
        $sibling = Join-Path (Split-Path -Parent $DumpPath) "mysql.exe"
        if (Test-Path -LiteralPath $sibling) { return $sibling }
    }
    foreach ($candidate in @(
        "C:\Program Files\MySQL\MySQL Server 9.2\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 9.1\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
    )) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    $command = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw "mysql.exe was not found. Set BACKUP_MYSQL_CLIENT_EXE."
}

function Copy-Atomic([string]$Source, [string]$DestinationDirectory) {
    New-Item -ItemType Directory -Path $DestinationDirectory -Force | Out-Null
    $destination = Join-Path $DestinationDirectory (Split-Path -Leaf $Source)
    $temporary = "$destination.tmp"
    try {
        Copy-Item -LiteralPath $Source -Destination $temporary -Force
        Move-Item -LiteralPath $temporary -Destination $destination -Force
    } finally {
        Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    }
}

$config = Read-DotEnv $EnvFile
$archivePathValue = if ($ArchiveDir) { $ArchiveDir } else { Get-Value $config "BINLOG_ARCHIVE_DIR" (Join-Path $ReleaseRoot "data\backups\binlogs") }
$archivePath = [System.IO.Path]::GetFullPath($archivePathValue)
$rootPath = [System.IO.Path]::GetPathRoot($archivePath)
if ($archivePath.TrimEnd('\') -eq $rootPath.TrimEnd('\')) { throw "BINLOG_ARCHIVE_DIR must not be a filesystem root" }
New-Item -ItemType Directory -Path $archivePath -Force | Out-Null

$retention = if ($RetentionDays -gt 0) { $RetentionDays } else { [int](Get-Value $config "BINLOG_ARCHIVE_RETENTION_DAYS" "35") }
if ($retention -lt 7) { throw "BINLOG_ARCHIVE_RETENTION_DAYS must be at least 7" }
$secondaryRoot = Get-Value $config "BACKUP_SECONDARY_DIR" ""
$remoteUri = Get-Value $config "BACKUP_REMOTE_URI" ""
if (-not $secondaryRoot -and -not $remoteUri) { throw "Binlog archive requires BACKUP_SECONDARY_DIR or BACKUP_REMOTE_URI" }

$jdbcUrl = Get-Value $config "SPRING_DATASOURCE_URL" ""
$jdbcMatch = [regex]::Match($jdbcUrl, '^jdbc:mysql://(?<host>[^:/?]+)(:(?<port>\d+))?/(?<database>[^?]+)')
if (-not $jdbcMatch.Success) { throw "SPRING_DATASOURCE_URL is not a supported MySQL JDBC URL: $jdbcUrl" }
$dbHost = $jdbcMatch.Groups["host"].Value
$dbPort = if ($jdbcMatch.Groups["port"].Success) { $jdbcMatch.Groups["port"].Value } else { "3306" }
$dbUser = Get-Value $config "BACKUP_MYSQL_USER" ""
$dbPassword = Get-Value $config "BACKUP_MYSQL_PASSWORD" ""
if (-not $dbUser -or -not $dbPassword) { throw "BACKUP_MYSQL_USER and BACKUP_MYSQL_PASSWORD are required" }
if ($dbPassword.Contains("`n") -or $dbPassword.Contains("`r")) { throw "Backup database password must not contain a line break" }

$dumpPath = Get-Value $config "BACKUP_MYSQL_EXE" ""
$configuredClient = if ($MysqlExe) { $MysqlExe } else { Get-Value $config "BACKUP_MYSQL_CLIENT_EXE" "" }
$mysql = Find-MySqlClient $configuredClient $dumpPath
$optionPath = Join-Path ([System.IO.Path]::GetTempPath()) "gongdan-binlog-$([guid]::NewGuid().ToString('N')).cnf"
$mutex = New-Object System.Threading.Mutex($false, "Local\GongdanBinlogArchive")
$lockAcquired = $false

try {
    try { $lockAcquired = $mutex.WaitOne(0) } catch [System.Threading.AbandonedMutexException] { $lockAcquired = $true }
    if (-not $lockAcquired) { throw "Another binlog archive is already running" }

    $optionContent = "[client]`r`nprotocol=tcp`r`nhost=$dbHost`r`nport=$dbPort`r`nuser=$(ConvertTo-MySqlOptionValue $dbUser)`r`npassword=$(ConvertTo-MySqlOptionValue $dbPassword)`r`n"
    [System.IO.File]::WriteAllText($optionPath, $optionContent, (New-Object System.Text.UTF8Encoding($false)))
    $baseArguments = @("--defaults-extra-file=$optionPath", "--batch", "--skip-column-names")

    $logEnabled = (& $mysql @baseArguments "--execute=SELECT @@log_bin").Trim()
    if ($LASTEXITCODE -ne 0 -or $logEnabled -ne "1") { throw "MySQL binary logging is not enabled" }
    $serverUuid = (& $mysql @baseArguments "--execute=SELECT @@server_uuid").Trim()
    if ($LASTEXITCODE -ne 0 -or $serverUuid -notmatch '^[A-Za-z0-9-]+$') { throw "Unable to read a valid MySQL server UUID" }
    $dataDirectory = (& $mysql @baseArguments "--execute=SELECT @@datadir").Trim()
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $dataDirectory -PathType Container)) {
        throw "MySQL data directory is not accessible to the maintenance account: $dataDirectory"
    }

    $logRows = @(& $mysql @baseArguments "--execute=FLUSH BINARY LOGS; SHOW BINARY LOGS")
    if ($LASTEXITCODE -ne 0 -or $logRows.Count -lt 2) { throw "Unable to rotate and enumerate MySQL binary logs" }
    $parsedRows = @($logRows | ForEach-Object {
        $parts = $_ -split "`t"
        if ($parts.Count -lt 2 -or $parts[0] -notmatch '^[A-Za-z0-9_.-]+\.\d+$') { throw "Unexpected binary log row: $_" }
        [pscustomobject]@{ Name = $parts[0]; Size = [long]$parts[1] }
    })
    $currentFile = $parsedRows[-1].Name
    $latestArchived = ""
    $secondaryDir = if ($secondaryRoot) { Join-Path ([System.IO.Path]::GetFullPath($secondaryRoot)) "binlogs\$serverUuid" } else { "" }
    $remoteScript = Join-Path $PSScriptRoot "sync-backup-remote.ps1"

    for ($rowIndex = 0; $rowIndex -lt ($parsedRows.Count - 1); $rowIndex++) {
        $row = $parsedRows[$rowIndex]
        $sourcePath = Join-Path $dataDirectory $row.Name
        $destinationPath = Join-Path $archivePath $row.Name
        if (-not (Test-Path -LiteralPath $destinationPath)) {
            $temporaryPath = "$destinationPath.tmp"
            try {
                Copy-Item -LiteralPath $sourcePath -Destination $temporaryPath -Force
                if ((Get-Item -LiteralPath $temporaryPath).Length -ne $row.Size) {
                    throw "Binary log size mismatch for $($row.Name)"
                }
                Move-Item -LiteralPath $temporaryPath -Destination $destinationPath -Force
            } finally {
                Remove-Item -LiteralPath $temporaryPath -Force -ErrorAction SilentlyContinue
            }
            $hash = (Get-FileHash -LiteralPath $destinationPath -Algorithm SHA256).Hash.ToLowerInvariant()
            "$hash *$($row.Name)" | Set-Content -LiteralPath "$destinationPath.sha256" -Encoding ascii
        } else {
            $expectedHash = ((Get-Content -LiteralPath "$destinationPath.sha256" -Raw).Split(' ')[0]).Trim().ToLowerInvariant()
            $actualHash = (Get-FileHash -LiteralPath $destinationPath -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($expectedHash -ne $actualHash) { throw "Binary log checksum failed: $destinationPath" }
        }

        if ($secondaryDir) {
            Copy-Atomic $destinationPath $secondaryDir
            Copy-Atomic "$destinationPath.sha256" $secondaryDir
        }
        $remoteMarker = "$destinationPath.remote-ok"
        if ($remoteUri -and -not (Test-Path -LiteralPath $remoteMarker)) {
            & $remoteScript -ReleaseRoot $ReleaseRoot -EnvFile $EnvFile -Paths @($destinationPath, "$destinationPath.sha256") -RelativePrefix "binlog/$serverUuid"
            New-Item -ItemType File -Path $remoteMarker -Force | Out-Null
        }
        $latestArchived = $row.Name
    }

    if (-not $latestArchived) { throw "No closed binary log was archived" }
    $statusPath = Join-Path $archivePath "binlog-archive-status.json"
    [pscustomobject]@{
        serverUuid = $serverUuid
        archivedAt = (Get-Date).ToString("o")
        archivedEpoch = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        currentFile = $currentFile
        latestArchivedFile = $latestArchived
    } | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $statusPath -Encoding UTF8
    $statusHash = (Get-FileHash -LiteralPath $statusPath -Algorithm SHA256).Hash.ToLowerInvariant()
    "$statusHash *$(Split-Path -Leaf $statusPath)" | Set-Content -LiteralPath "$statusPath.sha256" -Encoding ascii
    if ($secondaryDir) {
        Copy-Atomic $statusPath $secondaryDir
        Copy-Atomic "$statusPath.sha256" $secondaryDir
    }
    if ($remoteUri) {
        & $remoteScript -ReleaseRoot $ReleaseRoot -EnvFile $EnvFile -Paths @($statusPath, "$statusPath.sha256") -RelativePrefix "binlog/$serverUuid"
    }

    $cutoff = (Get-Date).AddDays(-$retention)
    Get-ChildItem -LiteralPath $archivePath -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^[A-Za-z0-9_.-]+\.\d+(\.sha256|\.remote-ok)?$' -and $_.LastWriteTime -lt $cutoff } |
        Remove-Item -Force

    Write-Host "Binlog archive passed: server=$serverUuid latest=$latestArchived current=$currentFile" -ForegroundColor Green
} finally {
    Remove-Item -LiteralPath $optionPath -Force -ErrorAction SilentlyContinue
    if ($lockAcquired) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
