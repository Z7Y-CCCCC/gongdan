param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$ReleaseDir = "",
    [switch]$SkipInstall,
    [switch]$SkipBackendTests,
    [switch]$SkipFrontendBuild
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

if (-not $SkipFrontendBuild) {
    $npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npmCommand) { $npmCommand = Get-Command npm.exe -ErrorAction SilentlyContinue }
    if (-not $npmCommand) { $npmCommand = Get-Command npm -ErrorAction SilentlyContinue }
    if (-not $npmCommand) { throw "npm was not found in PATH." }
    $npm = $npmCommand.Source
}
$maven = (Get-Command mvn -ErrorAction Stop).Source
$frontendDir = Join-Path $projectPath "frontend"
$mobileDir = Join-Path $projectPath "mobile"
$backendDir = Join-Path $projectPath "backend"

if (-not $SkipFrontendBuild -and -not $SkipInstall) {
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

if (-not $SkipFrontendBuild) {
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
} else {
    foreach ($requiredOutput in @(
        (Join-Path $frontendDir "dist\index.html"),
        (Join-Path $mobileDir "dist\build\h5\index.html")
    )) {
        if (-not (Test-Path -LiteralPath $requiredOutput)) { throw "Frontend build output is missing: $requiredOutput" }
    }
    Write-Host "Reusing previously verified frontend and mobile build outputs." -ForegroundColor Cyan
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
Copy-Item -LiteralPath (Join-Path $projectPath "deploy\windows\gongdan.prod.env.example") -Destination (Join-Path $configDir "gongdan.prod.env.example") -Force
Copy-Item -LiteralPath (Join-Path $projectPath "deploy\windows\nginx-server.conf") -Destination (Join-Path $nginxDir "gongdan-server.conf") -Force
Copy-Contents (Join-Path $projectPath "deploy\windows\scripts") $scriptsDir
Copy-Item -LiteralPath (Join-Path $projectPath "deploy\windows\README.md") -Destination (Join-Path $releasePath "README.md") -Force

$version = if (Test-Path -LiteralPath (Join-Path $projectPath "VERSION")) {
    (Get-Content -LiteralPath (Join-Path $projectPath "VERSION") -Raw).Trim()
} else { "0.0.0" }
$gitStatus = (& git -C $projectPath status --porcelain 2>$null)

$commit = (& git -C $projectPath rev-parse --short HEAD 2>$null)
@(
    "BuiltAt=$([DateTime]::Now.ToString('s'))"
    "Version=$version"
    "GitCommit=$commit"
) | Set-Content -LiteralPath (Join-Path $releasePath "BUILD-INFO.txt") -Encoding ascii

$manifestFiles = Get-ChildItem -LiteralPath $releasePath -File -Recurse |
    Where-Object {
        $_.FullName -notlike "*\config\gongdan.env" -and
        $_.Name -ne "RELEASE-MANIFEST.json" -and
        $_.FullName -notlike "*\data\*" -and
        $_.FullName -notlike "*\logs\*" -and
        $_.FullName -notlike "*\runtime\*" -and
        $_.FullName -notlike "*\tools\*"
    } |
    ForEach-Object {
        $relative = $_.FullName.Substring($releasePath.Length + 1).Replace('\', '/')
        [pscustomobject]@{
            path = $relative
            bytes = $_.Length
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
$manifest = [pscustomobject]@{
    version = $version
    gitCommit = $commit
    builtAt = (Get-Date).ToString("o")
    dirtySourceTree = [bool]$gitStatus
    files = $manifestFiles
}
$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $releasePath "RELEASE-MANIFEST.json") -Encoding UTF8

Write-Host "Windows release ready: $releasePath" -ForegroundColor Green
Write-Host "Copy it to C:\www\gongdan on the Windows Server." -ForegroundColor Cyan
