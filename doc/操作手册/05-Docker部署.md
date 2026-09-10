# 操作手册 05 · Docker 部署（单镜像：后端 + 管理端）

> Bifrost 1.0.0 起，**前后端打进同一个镜像**：一个 JVM 进程、一个端口 `18080`，
> 同时提供管理端页面与 Subsonic / OPDS / KOSync 协议服务。
> 形态与取舍见 [ADR-0007](../adr/0007-single-image-admin-under-admin.md)。

本文是**命令清单**：镜像不在 CI 之外的任何地方自动构建，按 §1 依次执行即可。
两条路线二选一或都走：

- **本地构建**（§1，Windows + Docker Desktop）：命令可见、产物可查，适合自己用；
- **GitHub Actions**（§2）：打 `v1.0.0` tag 自动构建、冒烟并推送到 ghcr.io，适合发版/分发。

## 0. 前置

| 项 | 要求 |
| --- | --- |
| 仓库布局 | `bifrost-core` 与 `bifrost-dashboard` 必须是**兄弟目录**（`../bifrost-dashboard`），管理端产物从那里来 |
| 构建机 | Windows + WSL2 + Docker Desktop（本手册按 PowerShell 写）；JDK 21、Node 24、pnpm 11 |
| 部署机 | Debian x86_64 + Docker Engine + compose 插件（`docker compose version` 可用） |
| 端口 | 宿主 `18080` 不被占用（端口是契约：三类客户端的连接地址都按 18080 写） |

## 1. 本地构建一份镜像（PowerShell）

### 1.1 管理端产物（在 `bifrost-dashboard` 目录）

```powershell
cd E:\Dev\jianglei\bifrost-dashboard

pnpm install                                   # 首次或依赖变更时
pnpm exec vue-tsc -b                           # 类型检查（构建的一部分）
pnpm exec vite build --base=/admin/            # 基址必须是 /admin/（见 ADR-0007）

# 守卫：确认产物是 /admin/ 基址，不是就停下（否则打进镜像的是一份根路径 UI，打开就白屏）
if (-not (Select-String -Path .\dist\index.html -Pattern '/admin/assets/' -Quiet)) {
    throw 'dist/index.html 里没有 /admin/assets/ —— 这份产物不是 /admin/ 基址，检查 vite build 参数'
}

# 拷进后端资源目录：先清空再拷，避免上一版的旧哈希文件残留进 jar
$target = 'E:\Dev\jianglei\bifrost-core\bifrost-bootstrap\src\main\resources\static\admin'
Remove-Item -Recurse -Force $target -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $target | Out-Null
Copy-Item -Recurse -Force .\dist\* $target
```

> `static/admin/` 已在 `.gitignore` 里（产物有 100+ 个带哈希的文件）。
> 平时开发前端不用走这一步：`pnpm dev` 起 5173，代理 `/api`、`/rest`、`/opds` 到 18080。

### 1.2 后端 jar（在 `bifrost-core` 目录）

```powershell
cd E:\Dev\jianglei\bifrost-core
.\mvnw.cmd clean verify                        # 跑全部测试；产物：bifrost-bootstrap\target\bifrost-bootstrap-1.0.0.jar
```

确认管理端确实进了 jar（可选但便宜）：

```powershell
jar tf bifrost-bootstrap\target\bifrost-bootstrap-1.0.0.jar | Select-String 'static/admin/index.html'
```

### 1.3 打镜像（在 `bifrost-core` 目录）

```powershell
docker build -f docker/Dockerfile `
  --build-arg APP_VERSION=1.0.0 `
  --build-arg GIT_REVISION=$(git rev-parse --short HEAD) `
  -t ghcr.io/jianglei-123456/bifrost:1.0.0 `
  -t ghcr.io/jianglei-123456/bifrost:latest .
```

`GIT_REVISION` 进 OCI label：1.0.0 起 master 常驻发布号，本地构建与发布产物**同名**，
要靠 `org.opencontainers.image.revision` 区分"这份镜像对应哪个提交"：

```powershell
docker inspect ghcr.io/jianglei-123456/bifrost:1.0.0 --format '{{json .Config.Labels}}'
```

### 1.4 本地冒烟（强烈建议）

```powershell
docker run --rm -d --name bifrost-smoke `
  -e BIFROST_AUTH_INITIAL_PASSWORD=smoketest `
  -e BIFROST_DATA_DIR=/data `
  -p 18080:18080 ghcr.io/jianglei-123456/bifrost:1.0.0

curl.exe -s http://localhost:18080/api/ping                                          # pong
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:18080/                        # 302（→ /admin/）
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:18080/admin/                  # 200
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:18080/admin/books/12          # 200（SPA 兜底）
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:18080/nosuch                  # 404（不被 SPA 吃掉）
curl.exe -s "http://localhost:18080/rest/ping.view?u=admin&p=smoketest&v=1.16.1&c=smoke&f=json"
curl.exe -s -u admin:smoketest http://localhost:18080/api/version                     # {"code":0,...,"version":"1.0.0"}

docker logs -f bifrost-smoke        # 看启动日志
docker rm -f bifrost-smoke          # 冒烟完销毁（数据在匿名卷里，不留痕）
```

## 2. 用 GitHub Actions 构建并推送（可选）

`.github/workflows/release.yml`：推 `v*` tag（或手动 `workflow_dispatch`）时，
checkout 两个仓库 → 构建管理端产物 → `mvnw clean verify` → `docker build` → **容器冒烟**
→ 推 `ghcr.io/jianglei-123456/bifrost:1.0.0` 与 `:latest`。

```powershell
cd E:\Dev\jianglei\bifrost-dashboard ; git tag v1.0.0 ; git push origin v1.0.0
cd E:\Dev\jianglei\bifrost-core      ; git tag v1.0.0 ; git push origin v1.0.0
```

**首次推送后做一次可见性设置**（Ghcr 默认私有；要让部署机免登录 `docker compose pull`，
就到 GitHub → 个人头像 → Packages → `bifrost` → Package settings → Change visibility → Public）：

- 选 **Public**：部署机 `docker compose pull` 免登录，镜像里不含任何凭据（只有 jar 与 dist）；
- 选 **Private**：部署机先 `docker login ghcr.io`（用带 `read:packages` 的 PAT）。

> 部署机拉 ghcr.io 若很慢/不通：换一条路——`docker save ghcr.io/...:1.0.0 -o bifrost-1.0.0.tar`，
> `scp` 到宿主后 `docker load -i bifrost-1.0.0.tar`。compose 里的 `image:` 名不用改。

## 3. 部署到 Debian 宿主

```bash
# 3.1 建目录（数据卷属主必须是容器内的 uid 1000，否则启动就写不了库）
sudo mkdir -p /srv/bifrost/{data,music,books}
sudo chown -R 1000:1000 /srv/bifrost/data

# 3.2 取 compose 文件（从仓库拷，或 scp docker/docker-compose.yml）
sudo install -o "$USER" -m 644 docker-compose.yml /srv/bifrost/docker-compose.yml

# 3.3 拉镜像并起容器（首次必须先给初始管理员口令）
cd /srv/bifrost
export BIFROST_AUTH_INITIAL_PASSWORD='你的管理员口令'      # 仅首次启动必需
docker compose pull
docker compose up -d
docker compose ps
docker compose logs -f --tail=50
```

### 3.4 验证

```bash
curl -s http://localhost:18080/api/ping                       # pong
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:18080/         # 302
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:18080/admin/   # 200
# 浏览器打开 http://<宿主IP>:18080/  → 自动进 /admin/ → 用上一步的口令登录
```

### 3.5 首次配置（三步，都在浏览器里）

1. **加库根**：管理端「音乐库」→ 新建音乐目录，路径填**容器内路径** `/media/music`；
   「图书库」→ 新建图书目录，路径填 `/media/books`。
   （`bifrost.library.roots` 配置键是预留未实现的，库根只能在这里加，见 FAQ）
2. **触发扫描**：音乐库 / 图书库页面点「扫描」（此后每天 03:00 由定时扫描自动增量进库，
   时区由 compose 的 `TZ` 决定）。
3. **阅读进度同步**（可选）：管理端「阅读进度」→ 拿同步账号与地址，
   在 KOReader 的 *Progress sync → Custom sync server* 填 `http://<宿主IP>:18080`
   （**手动把预填的 https 改成 http**）。

## 4. 数据、备份与升级

| 项 | 位置 |
| --- | --- |
| 数据卷（宿主） | `/srv/bifrost/data` → 容器 `/data` |
| SQLite 库 | `/data/bifrost.db`（+ `-wal` / `-shm`） |
| 封面缓存 | `/data/covers/cover-source`、`/data/covers/cover-cache` |
| 日志 | `/data/logs/bifrost.log`（按日滚动，保留 30 天 / 100MB） |
| 密钥 | `/data/secret.key`（自动生成；**丢了它已加密的管理员口令就解不开**） |

**备份 = 复制整个 `/srv/bifrost/data`**（建议先 `docker compose stop`，或至少确认没有扫描在跑）。
媒体目录不用备份——那是你的原始文件。

升级：

```bash
cd /srv/bifrost
# 改 docker-compose.yml 里的 image tag（如 1.0.0 → 1.1.0），然后：
docker compose pull && docker compose up -d
docker image prune -f          # 清旧镜像（可选）
```

数据库随启动自动迁移（`ddl-auto: update`）。**升级前先备份 `/srv/bifrost/data`**。

## 5. 环境变量与配置覆盖

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `BIFROST_DATA_DIR` | `/data`（镜像内已设） | 数据目录根：db / 封面 / 日志都由它派生 |
| `BIFROST_AUTH_INITIAL_PASSWORD` | 无 | **仅首启需要**（库里已有用户时忽略） |
| `BIFROST_AUTH_SECRET` | 无 | 指定则用它派生加密密钥，不生成 `secret.key`（迁移/多实例时用） |
| `BIFROST_KOSYNC_PUBLIC_BASE_URL` | 请求 Host 兜底 | 展示给阅读设备的同步地址；反代/改端口时**必须显式设** |
| `TZ` | `Asia/Shanghai`（镜像内已设） | 定时扫描 cron 与日志时间戳的时区 |
| `SPRING_CONFIG_ADDITIONAL_LOCATION` | 无 | 指向外部覆盖 yml，例如 `/data/application.yml` |

其它配置键（`bifrost.*`）都能用环境变量覆盖，写法是大写下划线：`bifrost.scan.cron`
→ `BIFROST_SCAN_CRON`。完整键表见《整体技术架构》§6。

## 6. 常见问题

| 现象 | 原因 / 处理 |
| --- | --- |
| 构建报 `groupadd: GID '1000' already exists` | 基底 `eclipse-temurin:21-jre`（Ubuntu）**自带 `ubuntu:ubuntu = 1000`**。`docker/Dockerfile` 已改成幂等（已有就复用、没有才建）并用数字 `USER 1000:1000`；如果你用的是更早版本或自己改过的 Dockerfile，照仓库里这份更新 |
| 启动即退出，日志说"首次启动必须设置初始管理员密码" | 首次启动没给 `BIFROST_AUTH_INITIAL_PASSWORD`；`export` 后重启 |
| `Permission denied` 写库失败 | `/srv/bifrost/data` 属主不是 1000：`sudo chown -R 1000:1000 /srv/bifrost/data` |
| 访问 `/` 停在 404 | 这份 jar **没打包管理端产物**（例如直接 `mvnw package` 后 `java -jar`）：`/` 会 302 到 `/admin/` 而那里没有 `index.html`。按 §1.1 重做产物再打包，或忽略（协议服务不受影响） |
| 库根加好了但扫描结果为空 | 宿主媒体目录不存在时 Docker 会自动建空目录；确认 `/srv/bifrost/music`、`/srv/bifrost/books` 里有文件 |
| 阅读设备连不上同步 | 管理端"阅读进度"页显示的地址是否等于设备可达的地址；不对就设 `BIFROST_KOSYNC_PUBLIC_BASE_URL` |
| 端口被占用 | `sudo ss -ltnp \| grep 18080`；宿主的 18080 让出来（改映射会让客户端连接地址与文档不一致） |
| 想看实时日志 | `docker compose logs -f`，或看卷里 `/srv/bifrost/data/logs/bifrost.log` |
| 容器时间不对 | 检查 compose 的 `TZ`（镜像已装 tzdata） |

## 7. 不用 Docker 的等价形态

`java -jar bifrost-bootstrap-1.0.0.jar` 仍然可用（老的运行方式不变），只是：

- 管理端要么用 `pnpm dev`（开发）、要么这份 jar 里恰好带了产物（§1.1 做过就有）；
- 数据目录默认 `./data`，可用 `BIFROST_DATA_DIR` / `--bifrost.db.path` 指定；
- 协议端点（`/rest`、`/opds`、KOSync）行为完全一致。

## 8. 发版清单（改版本号要动的地方）

1. `bifrost-core`：`./mvnw versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false`（12 个 pom）；
2. `bifrost-common/.../constant/BifrostVersion.java` 的 `VERSION`；
3. `hurl/rest/system.hurl` 里 `serverVersion` 断言；
4. `bifrost-dashboard/package.json` 的 `version`；
5. `.github/workflows/release.yml` 的 `APP_VERSION`、`docker/docker-compose.yml` 的 `image:` tag、`docker/Dockerfile` 的 `APP_VERSION` 默认值；
6. 文档里带版本号的坐标：`doc/技术设计/整体技术架构.md`；
7. 两个仓库一起 `git tag vX.Y.Z && git push origin vX.Y.Z`。
