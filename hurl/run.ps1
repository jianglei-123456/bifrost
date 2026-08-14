# Bifrost hurl contract test runner:
# package -> generate sample music -> start app (clean hurl db) -> run all assertions -> stop
param([int]$Port = 18080)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Set-Location $repo

# 1) package jar if missing
$jar = Join-Path $repo 'bifrost-bootstrap\target\bifrost-bootstrap-1.0.0-SNAPSHOT.jar'
if (-not (Test-Path $jar)) {
    Write-Host '==> packaging jar...'
    & .\mvnw.cmd -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'package failed' }
}

# 2) sample music
& pwsh -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'gen-sample-music.ps1')

# 3) clean db + start app
Remove-Item -Force (Join-Path $repo 'data\hurl.db'), (Join-Path $repo 'data\hurl.db-wal'), (Join-Path $repo 'data\hurl.db-shm') -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force (Join-Path $repo 'data\hurl-covers') -ErrorAction SilentlyContinue
$env:BIFROST_AUTH_INITIAL_PASSWORD = 'testpass'
$env:BIFROST_AUTH_SECRET = 'hurl-test-secret'
Write-Host "==> starting app (port $Port)..."
$app = Start-Process -FilePath 'java' -WorkingDirectory $repo -PassThru -WindowStyle Hidden -ArgumentList @(
    '-jar', $jar, "--server.port=$Port",
    '--bifrost.db.path=./data/hurl.db',
    '--bifrost.media.cover-cache-dir=./data/hurl-covers')
try {
    # wait until ready
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 1
        $code = & curl.exe -s --noproxy '*' -o NUL -w '%{http_code}' "http://localhost:$Port/api/ping" 2>$null
        if ($code -eq '200') { $ready = $true; break }
        if ($app.HasExited) { throw "app failed to start, exit code $($app.ExitCode)" }
    }
    if (-not $ready) { throw 'app not ready' }
    Write-Host '==> app ready, running hurl...'
    $base = "http://localhost:$Port"
    & hurl --test --jobs 1 --variable "base_url=$base" (Join-Path $PSScriptRoot 'setup.hurl')
    if ($LASTEXITCODE -ne 0) { throw 'setup.hurl failed' }
    & hurl --test --jobs 1 --variable "base_url=$base" (Join-Path $PSScriptRoot 'api') (Join-Path $PSScriptRoot 'rest')
    if ($LASTEXITCODE -ne 0) { throw 'contract tests failed' }
    Write-Host '==> all hurl contract tests passed'
} finally {
    if (-not $app.HasExited) { Stop-Process -Id $app.Id -Force }
}
