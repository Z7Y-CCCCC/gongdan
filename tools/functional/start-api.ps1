param(
    [ValidateRange(1024, 65535)][int] $Port = 64040,
    [string] $RuntimeFile = 'output/functional-api-runtime-round2.json'
)

# Isolated local acceptance server: never reads a production datasource or uploads.
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$backend = Join-Path $workspace 'backend'
$classes = Join-Path $backend 'target/classes'
$dependenciesFile = Join-Path $backend 'output/functional-classpath.txt'
if (-not (Test-Path -LiteralPath (Join-Path $classes 'com/heihu/workorder/WorkorderApplication.class'))) {
    throw 'Compile the backend before starting the isolated acceptance server.'
}
if (-not (Test-Path -LiteralPath $dependenciesFile)) {
    throw 'Generate backend/output/functional-classpath.txt using Maven dependency:build-classpath with test scope.'
}
$dependencies = ([IO.File]::ReadAllText($dependenciesFile)).Trim()
if ($dependencies -notmatch '(?i)[/\\]h2[/\\].*\.jar') { throw 'A test-scope H2 dependency is required.' }
foreach ($dependency in $dependencies.Split([IO.Path]::PathSeparator)) {
    if (-not (Test-Path -LiteralPath $dependency)) { throw "Missing dependency: $dependency" }
}
$listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $Port)
try { $listener.Start() } finally { $listener.Stop() }

# Oracle's Windows java shim can launch a second process. Resolve the real JVM
# executable so the recorded listener belongs to a single identifiable process.
$java = (Get-Command java -ErrorAction Stop).Source
$settings = (& $java -XshowSettings:properties -version 2>&1 | Out-String)
if ($settings -match 'java.home\s*=\s*([^\r\n]+)') {
    $binary = if ($IsWindows -or $env:OS -eq 'Windows_NT') { 'bin/java.exe' } else { 'bin/java' }
    $candidate = Join-Path $Matches[1].Trim() $binary
    if (Test-Path -LiteralPath $candidate) { $java = $candidate }
}
$temp = Join-Path ([IO.Path]::GetTempPath()) ('gongdan-functional-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $temp 'classes'), (Join-Path $temp 'config') | Out-Null
Copy-Item -Path (Join-Path $classes '*') -Destination (Join-Path $temp 'classes') -Recurse
Copy-Item -LiteralPath (Join-Path $backend 'src/test/resources/application-test.properties') -Destination (Join-Path $temp 'config/application-test.properties')
$classpath = @((Join-Path $temp 'classes'), (Join-Path $temp 'config'), $dependencies) -join [IO.Path]::PathSeparator
$metadata = [ordered]@{ tempRoot = $temp; classPath = $classpath; apiOrigin = "http://127.0.0.1:$Port"; java = $java; fixture = 'real-h2-api'; factoryCode = 'FUNCTIONALTEST' }
$metadataFile = if ([IO.Path]::IsPathRooted($RuntimeFile)) { $RuntimeFile } else { Join-Path $workspace $RuntimeFile }
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($metadataFile)) | Out-Null
[IO.File]::WriteAllText($metadataFile, ($metadata | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
$arguments = @(
    '-cp', $classpath, 'com.heihu.workorder.WorkorderApplication',
    '--spring.profiles.active=test', '--server.address=127.0.0.1', "--server.port=$Port",
    '--spring.datasource.url=jdbc:h2:mem:functional_round2;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;DATABASE_TO_LOWER=TRUE',
    '--spring.datasource.username=sa', '--spring.datasource.password=', '--spring.datasource.driver-class-name=org.h2.Driver',
    '--spring.jpa.hibernate.ddl-auto=create-drop', '--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect',
    '--spring.flyway.enabled=false', '--spring.cache.type=simple', '--spring.redis.port=1', '--management.health.redis.enabled=false',
    '--app.seed.factory-code=FUNCTIONALTEST', '--app.seed.admin-password=123456', '--app.wechat.mock-enabled=true',
    '--app.sms.sender=log', '--app.upload.storage-type=local',
    '--app.plugin.health-check-initial-delay-ms=86400000',
    '--app.plugin.gateway-secret=functional-loopback-gateway-round2',
    '--app.plugin.allowed-frontend-origins=http://127.0.0.1:9101,http://127.0.0.1:9102',
    '--app.message.allowed-target-hosts=127.0.0.1', '--app.message.allow-http=true',
    '--app.cors.allowed-origin-patterns=http://127.0.0.2:*,http://127.0.0.1:*',
    "--logging.file.name=$temp/api.log", "--app.upload.report-image-dir=$temp/uploads"
)
Push-Location -LiteralPath $temp
try {
    Write-Output "Isolated H2 API: http://127.0.0.1:$Port; frozen classes: $temp"
    & $java @arguments
    if ($LASTEXITCODE) { throw "Acceptance API exited with code $LASTEXITCODE" }
} finally { Pop-Location }
