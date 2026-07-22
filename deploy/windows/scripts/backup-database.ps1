param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = "",
    [string]$BackupDir = "",
    [int]$RetentionDays = 0,
    [string]$MysqlDumpExe = "",
    [string]$SecondaryDir = "",
    [switch]$SkipUploads
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $EnvFile) {
    $EnvFile = Join-Path $ReleaseRoot "config\gongdan.env"
}

function Read-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing config file: $Path"
    }

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
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
    $processValue = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ($null -ne $processValue -and $processValue -ne "") {
        return $processValue
    }
    if ($Config.ContainsKey($Name) -and $null -ne $Config[$Name]) {
        return [string]$Config[$Name]
    }
    return $Default
}

function Get-AbsolutePath([string]$Path) {
    return [System.IO.Path]::GetFullPath($Path)
}

function Write-MaintenanceLog([string]$Path, [string]$Message) {
    $line = "{0} {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Add-Content -LiteralPath $Path -Value $line -Encoding UTF8
}

function Find-Mysqldump([string]$ConfiguredPath) {
    if ($ConfiguredPath) {
        if (-not (Test-Path -LiteralPath $ConfiguredPath)) {
            throw "Configured mysqldump executable was not found: $ConfiguredPath"
        }
        return (Resolve-Path -LiteralPath $ConfiguredPath).Path
    }

    $candidates = @(
        "C:\Program Files\MySQL\MySQL Server 9.2\bin\mysqldump.exe",
        "C:\Program Files\MySQL\MySQL Server 9.1\bin\mysqldump.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }

    $command = Get-Command mysqldump.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    throw "mysqldump.exe was not found. Set BACKUP_MYSQL_EXE or pass -MysqlDumpExe."
}

$config = Read-DotEnv $EnvFile
$backupPath = if ($BackupDir) { $BackupDir } else { Get-ConfigValue $config "BACKUP_DIR" (Join-Path $ReleaseRoot "data\backups") }
$backupPath = Get-AbsolutePath $backupPath
$releasePath = Get-AbsolutePath $ReleaseRoot
$rootPath = [System.IO.Path]::GetPathRoot($backupPath)
if ($backupPath.TrimEnd('\') -eq $rootPath.TrimEnd('\')) {
    throw "Backup directory must not be a filesystem root: $backupPath"
}
New-Item -ItemType Directory -Path $backupPath -Force | Out-Null

$retention = if ($RetentionDays -gt 0) { $RetentionDays } else { [int](Get-ConfigValue $config "BACKUP_RETENTION_DAYS" "30") }
$retention = [Math]::Max(1, $retention)
$secondary = if ($SecondaryDir) { $SecondaryDir } else { Get-ConfigValue $config "BACKUP_SECONDARY_DIR" "" }
if ($secondary) {
    $secondary = Get-AbsolutePath $secondary
    if ($secondary.TrimEnd('\') -eq $backupPath.TrimEnd('\')) {
        throw "BACKUP_SECONDARY_DIR must be different from BACKUP_DIR"
    }
    New-Item -ItemType Directory -Path $secondary -Force | Out-Null
}

$logsDir = Join-Path $ReleaseRoot "logs"
New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
$maintenanceLog = Join-Path $logsDir "maintenance.log"
$mutex = New-Object System.Threading.Mutex($false, "Local\GongdanDatabaseBackup")
$lockAcquired = $false
$createdPaths = @()
$localBackupCommitted = $false

try {
    try {
        $lockAcquired = $mutex.WaitOne(0)
    } catch [System.Threading.AbandonedMutexException] {
        $lockAcquired = $true
    }
    if (-not $lockAcquired) {
        throw "Another database backup is already running."
    }

    $jdbcUrl = Get-ConfigValue $config "SPRING_DATASOURCE_URL" ""
    $jdbcMatch = [regex]::Match($jdbcUrl, '^jdbc:mysql://(?<host>[^:/?]+)(:(?<port>\d+))?/(?<database>[^?]+)')
    if (-not $jdbcMatch.Success) {
        throw "SPRING_DATASOURCE_URL is not a supported MySQL JDBC URL: $jdbcUrl"
    }

    $dbHost = $jdbcMatch.Groups["host"].Value
    $dbPort = if ($jdbcMatch.Groups["port"].Success) { $jdbcMatch.Groups["port"].Value } else { "3306" }
    $database = Get-ConfigValue $config "MYSQL_DATABASE" $jdbcMatch.Groups["database"].Value
    $dbUser = Get-ConfigValue $config "SPRING_DATASOURCE_USERNAME" (Get-ConfigValue $config "MYSQL_USER" "")
    $dbPassword = Get-ConfigValue $config "SPRING_DATASOURCE_PASSWORD" (Get-ConfigValue $config "MYSQL_PASSWORD" "")
    if (-not $dbUser) { throw "SPRING_DATASOURCE_USERNAME or MYSQL_USER is required." }
    if ($null -eq $dbPassword) { throw "SPRING_DATASOURCE_PASSWORD or MYSQL_PASSWORD is required." }
    if ($dbPassword.Contains("`n") -or $dbPassword.Contains("`r")) { throw "Database password must not contain a line break." }

    $configuredDumpExe = if ($MysqlDumpExe) { $MysqlDumpExe } else { Get-ConfigValue $config "BACKUP_MYSQL_EXE" "" }
    $dumpExe = Find-Mysqldump $configuredDumpExe
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $sqlPath = Join-Path $backupPath "mysql_$stamp.sql"
    $sqlTempPath = "$sqlPath.tmp"
    $hashPath = "$sqlPath.sha256"
    $optionPath = Join-Path ([System.IO.Path]::GetTempPath()) "gongdan-mysql-$([guid]::NewGuid().ToString('N')).cnf"
    $errorPath = Join-Path ([System.IO.Path]::GetTempPath()) "gongdan-mysqldump-$([guid]::NewGuid().ToString('N')).err"
    $files = @()

    try {
        $optionContent = "[client]`r`nprotocol=tcp`r`nhost=$dbHost`r`nport=$dbPort`r`nuser=$dbUser`r`npassword=$dbPassword`r`n"
        [System.IO.File]::WriteAllText($optionPath, $optionContent, (New-Object System.Text.UTF8Encoding($false)))
        $dumpArguments = @(
            "--defaults-extra-file=$optionPath",
            "--single-transaction",
            "--routines",
            "--triggers",
            "--hex-blob",
            "--set-gtid-purged=OFF",
            "--result-file=$sqlTempPath",
            $database
        )
        & $dumpExe @dumpArguments 2> $errorPath
        $dumpExitCode = $LASTEXITCODE
        if ($dumpExitCode -ne 0) {
            $details = if (Test-Path -LiteralPath $errorPath) { (Get-Content -LiteralPath $errorPath -Raw).Trim() } else { "" }
            throw "mysqldump failed with exit code $dumpExitCode. $details"
        }
        if (-not (Test-Path -LiteralPath $sqlTempPath)) { throw "mysqldump did not create a dump file." }
        $dumpInfo = Get-Item -LiteralPath $sqlTempPath
        if ($dumpInfo.Length -lt 128) { throw "The database dump is unexpectedly small: $($dumpInfo.Length) bytes." }
        Move-Item -LiteralPath $sqlTempPath -Destination $sqlPath -Force
        $createdPaths += $sqlPath
    } finally {
        Remove-Item -LiteralPath $optionPath, $errorPath, $sqlTempPath -Force -ErrorAction SilentlyContinue
    }

    $sqlHash = (Get-FileHash -LiteralPath $sqlPath -Algorithm SHA256).Hash.ToLowerInvariant()
    "$sqlHash *$(Split-Path -Leaf $sqlPath)" | Set-Content -LiteralPath $hashPath -Encoding ascii
    $createdPaths += $hashPath
    $files += [pscustomobject]@{ type = "database"; path = (Split-Path -Leaf $sqlPath); sha256 = $sqlHash; bytes = (Get-Item $sqlPath).Length }

    $uploadRoot = Get-ConfigValue $config "APP_UPLOAD_REPORT_IMAGE_DIR" (Join-Path $ReleaseRoot "data\uploads")
    if (-not [System.IO.Path]::IsPathRooted($uploadRoot)) { $uploadRoot = Join-Path $ReleaseRoot $uploadRoot }
    $uploadRoot = Get-AbsolutePath $uploadRoot
    if (-not $SkipUploads -and (Test-Path -LiteralPath $uploadRoot) -and @(Get-ChildItem -LiteralPath $uploadRoot -Force).Count -gt 0) {
        $uploadPath = Join-Path $backupPath "uploads_$stamp.zip"
        $uploadTempPath = Join-Path $backupPath "uploads_$stamp.tmp.zip"
        try {
            Compress-Archive -Path (Join-Path $uploadRoot "*") -DestinationPath $uploadTempPath -Force
            Move-Item -LiteralPath $uploadTempPath -Destination $uploadPath -Force
            $createdPaths += $uploadPath
        } finally {
            Remove-Item -LiteralPath $uploadTempPath -Force -ErrorAction SilentlyContinue
        }
        $uploadHash = (Get-FileHash -LiteralPath $uploadPath -Algorithm SHA256).Hash.ToLowerInvariant()
        "$uploadHash *$(Split-Path -Leaf $uploadPath)" | Set-Content -LiteralPath "$uploadPath.sha256" -Encoding ascii
        $createdPaths += "$uploadPath.sha256"
        $files += [pscustomobject]@{ type = "uploads"; path = (Split-Path -Leaf $uploadPath); sha256 = $uploadHash; bytes = (Get-Item $uploadPath).Length }
    }

    $cutoff = (Get-Date).AddDays(-$retention)
    $patterns = @("mysql_*.sql", "mysql_*.sql.sha256", "uploads_*.zip", "uploads_*.zip.sha256", "manifest_*.json")
    $removed = 0
    foreach ($pattern in $patterns) {
        foreach ($oldFile in Get-ChildItem -LiteralPath $backupPath -Filter $pattern -File -ErrorAction SilentlyContinue) {
            if ($oldFile.LastWriteTime -lt $cutoff) {
                Remove-Item -LiteralPath $oldFile.FullName -Force
                $removed++
            }
        }
    }

    $manifestPath = Join-Path $backupPath "manifest_$stamp.json"
    $manifest = [pscustomobject]@{
        createdAt = (Get-Date).ToString("o")
        database = $database
        host = $dbHost
        port = [int]$dbPort
        retentionDays = $retention
        files = $files
        expiredFilesRemoved = $removed
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    $createdPaths += $manifestPath
    $localBackupCommitted = $true

    if ($secondary) {
        foreach ($sourcePath in $createdPaths) {
            $destinationPath = Join-Path $secondary (Split-Path -Leaf $sourcePath)
            $temporaryDestination = "$destinationPath.tmp"
            try {
                Copy-Item -LiteralPath $sourcePath -Destination $temporaryDestination -Force
                Move-Item -LiteralPath $temporaryDestination -Destination $destinationPath -Force
            } finally {
                Remove-Item -LiteralPath $temporaryDestination -Force -ErrorAction SilentlyContinue
            }
        }
    }

    Write-MaintenanceLog $maintenanceLog "Database backup succeeded: database=$database, files=$($files.Count), expiredRemoved=$removed"
    Write-Host "Database backup completed: $sqlPath" -ForegroundColor Green
    Write-Host "SHA-256: $sqlHash" -ForegroundColor Green
    if ($secondary) { Write-Host "Copied backup to: $secondary" -ForegroundColor Cyan }
} catch {
    if (-not $localBackupCommitted) {
        foreach ($createdPath in $createdPaths) {
            Remove-Item -LiteralPath $createdPath -Force -ErrorAction SilentlyContinue
        }
    }
    $preserved = if ($localBackupCommitted) { " Local backup was preserved." } else { "" }
    Write-MaintenanceLog $maintenanceLog "Database backup FAILED: $($_.Exception.Message)$preserved"
    throw
} finally {
    if ($lockAcquired) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
