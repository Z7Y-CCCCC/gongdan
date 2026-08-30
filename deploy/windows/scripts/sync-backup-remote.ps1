param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = "",
    [string[]]$Paths = @(),
    [string]$RelativePrefix = "full",
    [switch]$VerifyOnly
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

function Test-True([string]$Value) {
    return @("1", "true", "yes", "on") -contains $Value.Trim().ToLowerInvariant()
}

function Find-AwsCli {
    $command = Get-Command aws.exe -ErrorAction SilentlyContinue
    if (-not $command) { $command = Get-Command aws -ErrorAction SilentlyContinue }
    if (-not $command) { throw "AWS CLI was not found. Install AWS CLI v2 for immutable remote backups." }
    return $command.Source
}

function Invoke-Aws([string]$Aws, [string[]]$Arguments, [string]$Endpoint) {
    $allArguments = @()
    if ($Endpoint) { $allArguments += @("--endpoint-url", $Endpoint) }
    $allArguments += $Arguments
    $output = & $Aws @allArguments
    if ($LASTEXITCODE -ne 0) { throw "AWS CLI failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')" }
    return $output
}

$config = Read-DotEnv $EnvFile
$remoteUri = Get-Value $config "BACKUP_REMOTE_URI" ""
if (-not $remoteUri) { return }
if ($remoteUri -notmatch '^s3://(?<bucket>[^/]+)(/.*)?$') { throw "BACKUP_REMOTE_URI must use s3://bucket/prefix syntax" }
$bucket = $Matches["bucket"]
$endpoint = Get-Value $config "BACKUP_REMOTE_ENDPOINT_URL" ""
$aws = Find-AwsCli

$savedEnvironment = @{}
foreach ($name in @("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN", "AWS_DEFAULT_REGION", "AWS_PAGER")) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

try {
    $accessKey = Get-Value $config "BACKUP_REMOTE_ACCESS_KEY" $savedEnvironment["AWS_ACCESS_KEY_ID"]
    $secretKey = Get-Value $config "BACKUP_REMOTE_SECRET_KEY" $savedEnvironment["AWS_SECRET_ACCESS_KEY"]
    $sessionToken = Get-Value $config "BACKUP_REMOTE_SESSION_TOKEN" $savedEnvironment["AWS_SESSION_TOKEN"]
    $region = Get-Value $config "BACKUP_REMOTE_REGION" "us-east-1"
    if ($accessKey) { [Environment]::SetEnvironmentVariable("AWS_ACCESS_KEY_ID", $accessKey, "Process") }
    if ($secretKey) { [Environment]::SetEnvironmentVariable("AWS_SECRET_ACCESS_KEY", $secretKey, "Process") }
    if ($sessionToken) { [Environment]::SetEnvironmentVariable("AWS_SESSION_TOKEN", $sessionToken, "Process") }
    [Environment]::SetEnvironmentVariable("AWS_DEFAULT_REGION", $region, "Process")
    [Environment]::SetEnvironmentVariable("AWS_PAGER", "", "Process")

    if (Test-True (Get-Value $config "BACKUP_VERIFY_REMOTE_POLICY" "true")) {
        $versioning = (Invoke-Aws $aws @("s3api", "get-bucket-versioning", "--bucket", $bucket, "--query", "Status", "--output", "text") $endpoint | Out-String).Trim()
        if ($versioning -ne "Enabled") { throw "Backup bucket versioning is not enabled: $bucket" }
        if (Test-True (Get-Value $config "BACKUP_REQUIRE_IMMUTABLE_REMOTE" "true")) {
            $lockConfiguration = (Invoke-Aws $aws @("s3api", "get-object-lock-configuration", "--bucket", $bucket, "--output", "json") $endpoint | Out-String) | ConvertFrom-Json
            if ($lockConfiguration.ObjectLockConfiguration.ObjectLockEnabled -ne "Enabled") { throw "Backup bucket Object Lock is not enabled: $bucket" }
            $defaultRetention = $lockConfiguration.ObjectLockConfiguration.Rule.DefaultRetention
            $requiredMode = (Get-Value $config "BACKUP_REMOTE_REQUIRED_LOCK_MODE" "COMPLIANCE").Trim().ToUpperInvariant()
            if ($defaultRetention.Mode -ne $requiredMode) {
                throw "Backup bucket default Object Lock mode must be ${requiredMode}: bucket=$bucket actual=$($defaultRetention.Mode)"
            }
            $minimumRetentionDays = 0
            if (-not [int]::TryParse((Get-Value $config "BACKUP_REMOTE_MIN_RETENTION_DAYS" "35"), [ref]$minimumRetentionDays) -or $minimumRetentionDays -lt 1) {
                throw "BACKUP_REMOTE_MIN_RETENTION_DAYS must be a positive integer"
            }
            $effectiveRetentionDays = if ($defaultRetention.Days) { [int]$defaultRetention.Days } elseif ($defaultRetention.Years) { [int]$defaultRetention.Years * 365 } else { 0 }
            if ($effectiveRetentionDays -lt $minimumRetentionDays) {
                throw "Backup bucket default Object Lock retention is too short: bucket=$bucket actualDays=$effectiveRetentionDays minimumDays=$minimumRetentionDays"
            }
        }
    }

    if ($VerifyOnly) {
        Write-Host "Remote backup policy passed: $remoteUri" -ForegroundColor Green
        return
    }
    if ($Paths.Count -eq 0) { throw "At least one backup artifact path is required." }

    $sse = Get-Value $config "BACKUP_REMOTE_SSE" ""
    $kmsKey = Get-Value $config "BACKUP_REMOTE_KMS_KEY_ID" ""
    foreach ($path in $Paths) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Backup artifact was not found: $path" }
        $destination = "{0}/{1}/{2}" -f $remoteUri.TrimEnd('/'), $RelativePrefix.Trim('/'), (Split-Path -Leaf $path)
        $arguments = @("s3", "cp", $path, $destination, "--only-show-errors")
        if ($sse) { $arguments += @("--sse", $sse) }
        if ($kmsKey) { $arguments += @("--sse-kms-key-id", $kmsKey) }
        Invoke-Aws $aws $arguments $endpoint | Out-Null
    }
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], "Process")
    }
}

Write-Host "Remote backup upload passed: $remoteUri/$RelativePrefix" -ForegroundColor Green
