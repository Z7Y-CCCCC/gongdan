param(
    [string]$ReleaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$EnvFile = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $EnvFile) {
    $EnvFile = Join-Path $ReleaseRoot "config\gongdan.env"
}

function Import-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing config file: $Path"
    }
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) { continue }
        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) { continue }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Require-Configured([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if (-not $value -or $value.Contains("CHANGE_ME")) {
        throw "$Name must be configured in $EnvFile"
    }
}

Import-DotEnv $EnvFile
Require-Configured "SPRING_DATASOURCE_PASSWORD"
Require-Configured "APP_SEED_ADMIN_PASSWORD"
Require-Configured "APP_SEED_SUPER_ADMIN_PASSWORD"

$serverDir = Join-Path $ReleaseRoot "server"
$jarPath = Join-Path $serverDir "app.jar"
$runtimeDir = Join-Path $ReleaseRoot "runtime"
$logDir = Join-Path $ReleaseRoot "logs"
$pidFile = Join-Path $runtimeDir "backend.pid"

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Backend jar not found: $jarPath"
}

New-Item -ItemType Directory -Path $runtimeDir, $logDir -Force | Out-Null

if (Test-Path -LiteralPath $pidFile) {
    $existingPid = [int](Get-Content -LiteralPath $pidFile -Raw)
    if (Get-Process -Id $existingPid -ErrorAction SilentlyContinue) {
        Write-Host "Backend is already running. PID=$existingPid" -ForegroundColor Yellow
        exit 0
    }
    Remove-Item -LiteralPath $pidFile -Force
}

$configuredJava = [Environment]::GetEnvironmentVariable("JAVA_EXE", "Process")
$javaExe = if ($configuredJava) { $configuredJava } else { (Get-Command java -ErrorAction Stop).Source }
$logStamp = Get-Date -Format "yyyyMMdd_HHmmss"
$stdoutLog = Join-Path $logDir "backend_$logStamp.out.log"
$stderrLog = Join-Path $logDir "backend_$logStamp.err.log"
$profile = [Environment]::GetEnvironmentVariable("SPRING_PROFILES_ACTIVE", "Process")
if (-not $profile) { $profile = "demo" }
$arguments = @(
    "-Xms256m",
    "-Xmx768m",
    "-XX:+UseG1GC",
    "-jar",
    "`"$jarPath`"",
    "--spring.profiles.active=$profile"
)

$process = Start-Process -FilePath $javaExe -ArgumentList $arguments -WorkingDirectory $serverDir `
    -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru
Set-Content -LiteralPath $pidFile -Value $process.Id -Encoding ascii

$port = [Environment]::GetEnvironmentVariable("SERVER_PORT", "Process")
if (-not $port) { $port = "18080" }
$healthUrl = "http://127.0.0.1:$port/actuator/health/readiness"

for ($attempt = 1; $attempt -le 60; $attempt++) {
    if ($process.HasExited) {
        throw "Backend exited during startup. Check $stderrLog"
    }
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
        if ($health.status -eq "UP") {
            Write-Host "Backend is ready: $healthUrl (PID=$($process.Id))" -ForegroundColor Green
            exit 0
        }
    } catch {
    }
    Start-Sleep -Seconds 1
}

throw "Backend did not become ready within 60 seconds. Check $stderrLog"
