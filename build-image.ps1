<#
.SYNOPSIS
  Bifrost 一条命令出镜像：管理端产物 → fat jar → 单镜像。

.DESCRIPTION
  前端（bifrost-dashboard/）与后端在同一个仓库，本脚本把它们的构建串成一条命令，
  并在打 jar 之前守住一个**生产正确性**前提：管理端产物必须是以 /admin/ 为基址构建的。

  这一步不能省。少了 /admin/ 基址，打进镜像的是一份"根路径 UI"——
  在后端托管的 /admin/ 下打开就是白屏，而且静态资源 404。基址守卫见下方 Guard。
  形态与取舍：docs/adr/0007-single-image-admin-under-admin.md、
             docs/adr/0010-frontend-merged-into-core-repo.md。

.EXAMPLE
  .\build-image.ps1                     # 版本取自 BifrostVersion.java
  .\build-image.ps1 -SkipImage          # 只到 jar（没装 Docker 时用）
  .\build-image.ps1 -AppVersion 1.1.0 -ImageName myrepo/bifrost
  .\build-image.ps1 -SkipFrontendTests  # 跳过 vitest（tsc + vite build 仍会跑）
#>
[CmdletBinding()]
param(
    # 留空 = 从 bifrost-common 的 BifrostVersion.VERSION 读取（版本号的唯一来源）
    [string]$AppVersion,

    [string]$ImageName = 'ghcr.io/jianglei-123456/bifrost',

    # 额外镜像 tag；默认同时打一个 :latest
    [string[]]$ExtraTags = @('latest'),

    # 跳过前端单测（仅调试用；正常发布不要跳）
    [switch]$SkipFrontendTests,

    # 跳过 docker build（没装 Docker、或只想验证到 jar）
    [switch]$SkipImage
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ── 路径与工具 ────────────────────────────────────────────────────────────────
$RepoRoot   = $PSScriptRoot
$Dashboard  = Join-Path $RepoRoot 'bifrost-dashboard'
$AdminTarget = Join-Path $RepoRoot 'bifrost-bootstrap\src\main\resources\static\admin'
$VersionFile = Join-Path $RepoRoot 'bifrost-common\src\main\java\com\bifrost\common\constant\BifrostVersion.java'

function Step([string]$Text) {
    Write-Host ''
    Write-Host "── $Text " -ForegroundColor Cyan -NoNewline
    Write-Host ('─' * [Math]::Max(0, 72 - $Text.Length)) -ForegroundColor DarkCyan
}

function Fail([string]$Text) {
    Write-Host ''
    Write-Host "✗ $Text" -ForegroundColor Red
    exit 1
}

function Get-Exe([string]$Name) {
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $cmd) { return $null }
    return $cmd.Source
}

# ── 0. 前置检查 ──────────────────────────────────────────────────────────────
if (-not (Test-Path $VersionFile)) { Fail "找不到版本文件：$VersionFile" }
if (-not (Test-Path (Join-Path $Dashboard 'package.json'))) { Fail "找不到管理端工程：$Dashboard" }

$pnpm = Get-Exe 'pnpm'
if (-not $pnpm) { Fail '找不到 pnpm。需要 Node 24 + pnpm 11（见 bifrost-dashboard/package.json 的 engines/packageManager）。' }
$node = Get-Exe 'node'
if (-not $node) { Fail '找不到 node。需要 Node 24（见 bifrost-dashboard/.node-version）。' }

$mvnw = Join-Path $RepoRoot 'mvnw.cmd'
if (-not (Test-Path $mvnw)) { Fail "找不到 Maven Wrapper：$mvnw" }

$docker = Get-Exe 'docker'
if (-not $SkipImage -and -not $docker) {
    Fail '找不到 docker。装 Docker Desktop，或加 -SkipImage 只构建到 jar。'
}

# ── 1. 版本号：唯一来源是 BifrostVersion.java ─────────────────────────────────
$sourceVersion = ([regex]::Match((Get-Content -Raw $VersionFile), 'VERSION\s*=\s*"([^"]+)"')).Groups[1].Value
if (-not $sourceVersion) { Fail "无法从 $VersionFile 解析 VERSION" }

if (-not $AppVersion) {
    $AppVersion = $sourceVersion
    Write-Host "版本号（取自 BifrostVersion.java）：$AppVersion" -ForegroundColor Green
} elseif ($AppVersion -ne $sourceVersion) {
    Fail "传入 -AppVersion $AppVersion 与 BifrostVersion.VERSION ($sourceVersion) 不一致。版本号只有一个来源，请先改 BifrostVersion.java（发版清单见操作手册 05 §8）。"
}

# 前端 package.json 的 version 应与后端同号（操作手册 05 §8 第 4 项）
$pkg = Get-Content -Raw (Join-Path $Dashboard 'package.json') | ConvertFrom-Json
if ($pkg.version -ne $AppVersion) {
    Write-Warning "bifrost-dashboard/package.json 的 version 是 $($pkg.version)，与后端 $AppVersion 不一致（发版清单要求同号）。本次继续，但请对齐。"
}

$gitRevision = 'unknown'
$git = Get-Exe 'git'
if ($git) {
    $rev = & $git -C $RepoRoot rev-parse --short HEAD 2>$null
    if ($LASTEXITCODE -eq 0 -and $rev) { $gitRevision = $rev.Trim() }
}

Write-Host "镜像：$ImageName`:$AppVersion（+ $($ExtraTags -join ', ')）  revision：$gitRevision" -ForegroundColor DarkGray

# ── 2. 管理端：装依赖、类型检查、单测、生产构建 ───────────────────────────────
Step '管理端 pnpm install'
Push-Location $Dashboard
try {
    & $pnpm install --frozen-lockfile
    if ($LASTEXITCODE -ne 0) { Fail 'pnpm install 失败' }

    Step '管理端类型检查（vue-tsc -b）'
    & $pnpm exec vue-tsc -b
    if ($LASTEXITCODE -ne 0) { Fail 'vue-tsc 类型检查失败' }

    if (-not $SkipFrontendTests) {
        Step '管理端单测（vitest run）'
        & $pnpm exec vitest run
        if ($LASTEXITCODE -ne 0) { Fail 'vitest 失败（要跳过用 -SkipFrontendTests）' }
    }

    Step '管理端生产构建（vite build --base=/admin/）'
    & $pnpm exec vite build --base=/admin/
    if ($LASTEXITCODE -ne 0) { Fail 'vite build 失败' }
} finally {
    Pop-Location
}

# ── 3. Guard：产物必须是以 /admin/ 为基址的，否则后面的镜像一定白屏 ───────────
Step 'Guard：校验产物基址是 /admin/'
$distIndex = Join-Path $Dashboard 'dist\index.html'
if (-not (Test-Path $distIndex)) { Fail "构建产物缺失：$distIndex" }

$html = Get-Content -Raw $distIndex
if ($html -notmatch '/admin/assets/') {
    Fail @'
dist/index.html 里没有 /admin/assets/ —— 这份产物不是 /admin/ 基址。
  后果：打进镜像后在 /admin/ 下打开是白屏、静态资源 404。
  排查：构建命令是否带了 --base=/admin/（见 ADR-0007 / ADR-0010）。
'@
}
Write-Host '✓ 产物基址正确（含 /admin/assets/）' -ForegroundColor Green

# ── 4. 拷进后端资源目录（先清空，避免上一版旧哈希文件残留进 jar）─────────────
Step "拷贝产物 → $AdminTarget"
if (Test-Path $AdminTarget) { Remove-Item -Recurse -Force $AdminTarget }
New-Item -ItemType Directory -Path $AdminTarget -Force | Out-Null
Copy-Item -Recurse -Force (Join-Path $Dashboard 'dist\*') $AdminTarget

$copied = (Get-ChildItem -Recurse -File $AdminTarget).Count
if (-not (Test-Path (Join-Path $AdminTarget 'index.html'))) { Fail '拷贝后 static/admin/index.html 不存在' }
Write-Host "✓ 拷入 $copied 个文件（该目录已 gitignore，只在构建期存在）" -ForegroundColor Green

# ── 5. 后端：clean verify（跑全部测试） ──────────────────────────────────────
Step '后端 mvnw clean verify'
Push-Location $RepoRoot
try {
    & $mvnw -B clean verify
    if ($LASTEXITCODE -ne 0) { Fail 'Maven 构建失败' }
} finally {
    Pop-Location
}

$jar = Get-ChildItem (Join-Path $RepoRoot 'bifrost-bootstrap\target') -Filter 'bifrost-bootstrap-*.jar' |
    Where-Object { $_.Name -notlike '*.original' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) { Fail '没找到 fat jar（bifrost-bootstrap/target/bifrost-bootstrap-*.jar）' }
Write-Host "✓ jar：$($jar.Name)（$([Math]::Round($jar.Length/1MB,1)) MB）" -ForegroundColor Green

# ── 6. 打镜像 ────────────────────────────────────────────────────────────────
if ($SkipImage) {
    Write-Host ''
    Write-Host '跳过 docker build（-SkipImage）。jar 已就绪。' -ForegroundColor Yellow
    exit 0
}

Step 'docker build'
$tags = @("$ImageName`:$AppVersion") + ($ExtraTags | Where-Object { $_ } | ForEach-Object { "$ImageName`:$_" })
$dockerArgs = @(
    'build', '-f', (Join-Path $RepoRoot 'docker\Dockerfile'),
    '--build-arg', "APP_VERSION=$AppVersion",
    '--build-arg', "GIT_REVISION=$gitRevision"
)
foreach ($t in $tags) { $dockerArgs += @('-t', $t) }
$dockerArgs += $RepoRoot

Push-Location $RepoRoot
try {
    & $docker $dockerArgs
    if ($LASTEXITCODE -ne 0) { Fail 'docker build 失败' }
} finally {
    Pop-Location
}

Write-Host ''
Write-Host "✓ 完成：$($tags -join '  ')" -ForegroundColor Green
Write-Host ''
Write-Host '冒烟（本地跑一次，强烈建议）：' -ForegroundColor DarkGray
Write-Host "  docker run --rm -d --name bifrost-smoke -e BIFROST_AUTH_INITIAL_PASSWORD=smoketest -e BIFROST_DATA_DIR=/data -p 18080:18080 $ImageName`:$AppVersion"
Write-Host '  curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:18080/'            # 期望 302
Write-Host '  curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:18080/admin/'       # 期望 200
Write-Host '  curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:18080/admin/books/12' # 期望 200（SPA 兜底）
Write-Host '  curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:18080/nosuch'      # 期望 404
Write-Host '  docker rm -f bifrost-smoke'
