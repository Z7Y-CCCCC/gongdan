param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$ReleaseDir = "",
    [switch]$SkipInstall,
    [switch]$SkipBackendTests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $ReleaseDir) {
    $ReleaseDir = Join-Path $ProjectRoot "release-windows"
}

$projectPath = [System.IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\')
$releasePath = [System.IO.Path]::GetFullPath($ReleaseDir).TrimEnd('\')
if (-not $releasePath.StartsWith($projectPath + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "ReleaseDir must stay inside the project: $releasePath"
}

function Invoke-Checked([string]$Command, [string[]]$Arguments, [string]$FailureMessage) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw $FailureMessage }
}

function Reset-Directory([string]$Path) {
    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
}

function Copy-Contents([string]$Source, [string]$Destination) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    Copy-Item -Path (Join-Path $Source "*") -Destination $Destination -Recurse -Force
}

$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$maven = (Get-Command mvn -ErrorAction Stop).Source
$frontendDir = Join-Path $projectPath "frontend"
$mobileDir = Join-Path $projectPath "mobile"
$backendDir = Join-Path $projectPath "backend"

if (-not $SkipInstall) {
    Push-Location $frontendDir
    try { Invoke-Checked $npm @("ci") "Frontend npm ci failed." } finally { Pop-Location }
    Push-Location $mobileDir
    try { Invoke-Checked $npm @("ci") "Mobile npm ci failed." } finally { Pop-Location }
}

$savedEnvironment = @{}
$buildEnvironment = @{
    VITE_API_BASE_URL = "/api"
    VITE_MOBILE_BASE_URL = "/mobile"
    VITE_WECHAT_MOCK_ENABLED = "true"
    VITE_PUBLIC_BASE_URL = "/mobile/"
}
foreach ($name in $buildEnvironment.Keys) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

try {
    [Environment]::SetEnvironmentVariable("VITE_API_BASE_URL", "/api", "Process")
    [Environment]::SetEnvironmentVariable("VITE_MOBILE_BASE_URL", "/mobile", "Process")
    Push-Location $frontendDir
    try { Invoke-Checked $npm @("run", "build") "Frontend build failed." } finally { Pop-Location }

    [Environment]::SetEnvironmentVariable("VITE_WECHAT_MOCK_ENABLED", "true", "Process")
    [Environment]::SetEnvironmentVariable("VITE_PUBLIC_BASE_URL", "/mobile/", "Process")
    Push-Location $mobileDir
    try { Invoke-Checked $npm @("run", "build") "Mobile build failed." } finally { Pop-Location }
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], "Process")
    }
}

$mavenArgs = if ($SkipBackendTests) { @("-DskipTests", "package") } else { @("test", "package") }
Push-Location $backendDir
try { Invoke-Checked $maven $mavenArgs "Backend build failed." } finally { Pop-Location }

$jar = Get-ChildItem -LiteralPath (Join-Path $backendDir "target") -Filter "workorder-*.jar" -File |
    Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1
if (-not $jar) { throw "Backend jar was not produced." }

Reset-Directory $releasePath
$wwwDir = Join-Path $releasePath "www"
$mobileOutputDir = Join-Path $wwwDir "mobile"
$serverDir = Join-Path $releasePath "server"
$configDir = Join-Path $releasePath "config"
$nginxDir = Join-Path $releasePath "nginx"
$scriptsDir = Join-Path $releasePath "scripts"

Copy-Contents (Join-Path $frontendDir "dist") $wwwDir
Copy-Contents (Join-Path $mobileDir "dist\build\h5") $mobileOutputDir
New-Item -ItemType Directory -Path $serverDir, $configDir, $nginxDir, $scriptsDir, `
    (Join-Path $releasePath "data\uploads"), (Join-Path $releasePath "data\backups"), `
    (Join-Path $releasePath "logs"), (Join-Path $releasePath "runtime"), `
    (Join-Path $releasePath "tools") -Force | Out-Null
Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $serverDir "app.jar") -Force
Copy-Item -LiteralPath (Join-Path $projectPath "deploy\windows\gongdan.env.example") -Destination (Join-Path $configDir "gongdan.env") -Force
Copy-Item -LiteralPath (Join-Path $projectPath "deploy\windows\gongdan.env.example") -Destination (Join-Path $configDir "gongdan.env.example") -Force
Copy-Item -LiteralPath (Join-Path $projectPath "deploy\windows\nginx-server.conf") -Destination (Join-Path $nginxDir "gongdan-server.conf") -Force
Copy-Contents (Join-Path $projectPath "deploy\windows\scripts") $scriptsDir
Copy-Item -LiteralPath (Join-Path $projectPath "deploy\windows\README.md") -Destination (Join-Path $releasePath "README.md") -Force

$commit = (& git -C $projectPath rev-parse --short HEAD 2>$null)
@(
    "BuiltAt=$([DateTime]::Now.ToString('s'))"
    "GitCommit=$commit"
) | Set-Content -LiteralPath (Join-Path $releasePath "BUILD-INFO.txt") -Encoding ascii

Write-Host "Windows release ready: $releasePath" -ForegroundColor Green
Write-Host "Copy it to C:\www\gongdan on the Windows Server." -ForegroundColor Cyan
