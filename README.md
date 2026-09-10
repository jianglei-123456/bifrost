# Bifrost

家庭媒体库管理平台：统一管理 **视频 / 音频（音乐）/ 电子书** 三类媒体资源。

**里程碑 M1 ✅**：音乐管理 + **Subsonic 兼容协议服务（Syrinx）**——Bifrost 自身即 Subsonic 服务端，Feishin、DSub、Symfonium、play:Sub、Supersonic 等客户端可直接连接浏览、搜索、播放与管理音乐。管理端前端（Vue Dashboard）在独立项目开发，消费 `/api/**` REST 契约。

**里程碑 M2-book ✅**：图书管理 + **OPDS 1.2 协议发布**——KOReader（真机/模拟器）和 Readest（桌面/Web）可通过标准 OPDS catalog 浏览、搜索、下载 EPUB/PDF；管理端 `/api/book-roots` + `/api/books` 提供 CRUD + 扫描 + 元数据编辑 + 封面上传。图书侧与音乐侧物理隔开（独立实体、独立扫描器、独立封面存储、独立过滤器链，见 [ADR-0004](doc/adr/0004-book-physical-isolation.md)）。

**里程碑 M3-sync ✅**：**阅读进度同步（KOSync）**——Bifrost 自己实现 KOReader 的进度同步协议（`/users/create`、`/users/auth`、`PUT /syncs/progress`、`GET /syncs/progress/:document`、`/healthcheck`，挂根路径），设备在 *Progress sync → Custom sync server* 填 `http://<host>:18080` 即可多端续读；服务端把客户端算出的**文档指纹**映射回库里的书，管理端 `/api/book-sync/**` 提供同步账号配置、进度列表、孤儿进度处理与设备列表。同步账号与管理员账号相互独立。设计见 [doc/m3-sync/](doc/m3-sync/task/00-总览.md) 与 [ADR-0006](doc/adr/0006-kosync-self-implemented.md)（自研而非集成官方 sync-server 的判断依据）。

**1.0.0 发布（当前版本）**：M1 + M2-book + M3-sync 三个里程碑的合集，交付形态是**一个镜像**——管理端（Vue Dashboard，独立仓库 `../bifrost-dashboard`）与后端同镜像、同源部署在 `/admin/`，一个进程一个端口 `18080` 同时服务管理端与三类协议客户端（根命名空间完整留给 `/rest`、`/opds`、`/users`、`/syncs`、`/healthcheck`）。版本号唯一来源 `BifrostVersion`，`GET /api/version` 可查；形态与取舍见 [ADR-0007](doc/adr/0007-single-image-admin-under-admin.md)。

## 技术栈

- Java 21
- Spring Boot 3.5.x（Spring Framework 6.2.x）
- Maven 多模块
- 持久化：SQLite（Spring Data JPA + Hibernate SQLite dialect）
- 标签解析：jaudiotagger 3.0.1（MP3/FLAC/M4A，ADR-0002）
- **电子书元数据**：epublib-core 3.1（EPUB / KEPUB），PDFBox 3.0.5 + xmpbox（PDF XMP + InfoDict）
- 拼音索引：jpinyin（中文艺术家首字母分组）
- Subsonic 输出：jackson-dataformat-xml（XML/JSON 双格式）

## 模块结构

```
bifrost (父级 POM，统一 Spring Boot 与依赖版本)
├── bifrost-common        # 工具包（字符串、日期、文件 IO、自定义异常）
├── bifrost-domain        # 核心实体（Entity）、DTO、枚举，以及 Repository 接口
├── bifrost-core          # 核心业务逻辑（Service 层）
│   ├── video/            # 包隔离：视频扫描、索引逻辑（后续里程碑）
│   ├── audio/            # 包隔离：音乐扫描、标签解析、聚合索引
│   └── book/             # 包隔离：电子书元数据解析（后续里程碑）
├── bifrost-adapter       # 对接外部客户端的具体实现（聚合 POM）
│   ├── syrinx            # 【音乐网关】
│   │   └── subsonic-api  # Subsonic 协议服务端（/rest/**，客户端直连）
│   ├── jellyfin-client   # 占位：向 Jellyfin 推送 / 同步（待定）
│   └── opds-publisher    # 图书 OPDS 1.2 流发布（/opds/**，KOReader/Readest 直连）
├── bifrost-api           # 管理 RESTful Controller 层（/api/**，供 Vue Dashboard 调用）
└── bifrost-bootstrap     # Spring Boot 启动类，存放 application.yml 配置文件
```

## 快速开始

```bash
# 使用 Maven Wrapper 构建（首次会自动下载指定 Maven 版本）
./mvnw clean verify

# 启动应用（首次启动必须设置初始管理员密码，见《通用功能说明》§2.3）
BIFROST_AUTH_INITIAL_PASSWORD=yourpass ./mvnw -pl bifrost-bootstrap -am spring-boot:run
```

应用默认监听 `http://localhost:18080`：

```bash
curl http://localhost:18080/api/ping          # → pong
curl -u admin:<口令> http://localhost:18080/api/version                                   # → {"code":0,...,"version":"1.0.0"}
curl "http://localhost:18080/rest/ping.view?u=admin&t=<token>&s=<salt>&v=1.16.1&c=test"   # → subsonic-response ok
curl http://localhost:18080/opds/v1.2/catalog # → Atom OPDS navigation feed（图书）
```

管理端（Vue Dashboard）在浏览器里的地址是 **`http://localhost:18080/admin/`**（`/` 会 302 过去）。
开发时也可以单独跑 `../bifrost-dashboard` 的 `pnpm dev`（5173，代理 `/api`、`/rest`、`/opds` 到 18080）。

数据目录（SQLite 数据库、封面缓存、日志、密钥）默认位于 `./data`，由 `bifrost.data.dir` 一个键统一决定
（容器里用 `BIFROST_DATA_DIR=/data`），见[操作手册 05](doc/操作手册/05-Docker部署.md)。

### 图书库使用流程

1. 在管理 REST 创建图书目录（`POST /api/book-roots`，`mediaType=BOOK`）；
2. 触发扫描（`POST /api/book-roots/{id}/scan`），从 EPUB/PDF 解析元数据入库；
3. KOReader 添加 OPDS catalog：`http://<host>:18080/opds/v1.2/catalog`；Readest 同上（**填完整路径**，不是 `/opds`；分客户端步骤见操作手册 [04](doc/操作手册/04-连接阅读器客户端.md)）；
4. 客户端可浏览 / 搜索 / 下载（KOReader EPUBC；Readest EPUB + PDF）；
5. **可选鉴权**：在 `application.yml` 设置 `bifrost.opds.require-auth: true` 强制 Basic 鉴权（默认匿名）。
6. **阅读进度同步（可选）**：管理端「阅读进度」页拿同步账号（地址/用户名/口令），在 KOReader 的 *Progress sync → Custom sync server* 填 `http://<host>:18080`（**要手动把预填的 `https://` 改成 `http://`**）后 Register/Login；多设备共用同一账号即可互相续读。详见操作手册 [04](doc/操作手册/04-连接阅读器客户端.md) §7。

## 测试

```bash
./mvnw clean verify                        # 单元 + 集成测试
powershell -File hurl\run.ps1              # hurl 端点契约测试（需 ffmpeg 与 hurl CLI，见 hurl\README.md）
```

## Docker 部署（单镜像：后端 + 管理端）

1.0.0 起前后端打进**一个镜像**：一个进程、一个端口 `18080`；`/`（302 → `/admin/`）是管理端，
`/api/**`、`/rest/**`、`/opds/**` 与 KOSync 端点原样。设计与被否决的方案见 [ADR-0007](doc/adr/0007-single-image-admin-under-admin.md)。

```bash
# 1) 构建：先出管理端产物、再出 jar、最后打镜像（完整命令见操作手册 05 §1）
./mvnw clean verify
docker build -f docker/Dockerfile -t ghcr.io/jianglei-123456/bifrost:1.0.0 .

# 2) 运行（首次启动必须给初始管理员口令）
docker run -d --name bifrost \
  -e BIFROST_AUTH_INITIAL_PASSWORD=yourpass \
  -e BIFROST_DATA_DIR=/data \
  -v /srv/bifrost/data:/data \
  -v /srv/bifrost/music:/media/music:ro \
  -v /srv/bifrost/books:/media/books:ro \
  -p 18080:18080 ghcr.io/jianglei-123456/bifrost:1.0.0
```

- 数据全在 `/data` 一个卷里（SQLite 库、封面缓存、日志、`secret.key`），**备份该卷即完成数据备份**；
- 媒体目录**只读**挂载即可（Bifrost 只解析、不写回），挂完到管理端「音乐库 / 图书库」按**容器内路径**添加库根；
- 配置覆盖：挂载 `/data/application.yml` 并设 `SPRING_CONFIG_ADDITIONAL_LOCATION`；
- 部署机侧（Debian + compose + 升级 + FAQ）的命令清单：[操作手册 05](doc/操作手册/05-Docker部署.md)。

## 文档

| 文档 | 说明 |
| --- | --- |
| [操作手册 · 总览](doc/操作手册/00-总览.md) | 手册目录与三分钟快速上手 |
| [操作手册 · 安装与启动](doc/操作手册/01-安装与启动.md) | 构建启动、初始密码、配置音乐目录与扫描、备份 |
| [操作手册 · 连接 Subsonic 客户端](doc/操作手册/02-连接Subsonic客户端.md) | **客户端连接指南：服务器地址 / 用户名 / 密码怎么填**（Feishin、DSub、Symfonium 等） |
| [操作手册 · 连接阅读器客户端](doc/操作手册/04-连接阅读器客户端.md) | **阅读器（Readest / KOReader）经 OPDS 连接 Bifrost 书库**：建图书目录与扫描、地址怎么填、分客户端步骤 |
| [操作手册 · 常见问题](doc/操作手册/03-常见问题.md) | 连不上、认证失败 40、密码忘记、OPDS 404 等 FAQ |
| [操作手册 · Docker 部署](doc/操作手册/05-Docker部署.md) | **单镜像构建/推送/部署的命令清单**：宿主构建、ghcr 推送、compose 起容器、首配库根、备份升级 |
| [ADR-0007 单镜像](doc/adr/0007-single-image-admin-under-admin.md) | 为什么管理端挂 `/admin/` 由后端托管、为什么镜像只打包不构建 |
| [项目整体功能说明](doc/功能设计/项目整体功能说明.md) | 产品定位、场景、功能地图、里程碑 |
| [通用功能说明](doc/功能设计/通用功能说明.md) | 配置/日志/认证/持久化/REST 约定等公共能力 |
| [音乐管理功能说明](doc/功能设计/音乐管理功能说明.md) | 音乐管理与 Subsonic 服务能力、客户端兼容矩阵 |
| [整体技术架构](doc/技术设计/整体技术架构.md) | 模块架构、技术栈、存储、配置、Docker |
| [音乐管理技术设计](doc/技术设计/音乐管理技术设计.md) | 领域模型、扫描算法、Subsonic 端点实现设计 |
| [阅读进度同步（M3-sync）](doc/m3-sync/task/00-总览.md) | KOSync 协议实现 + 文档指纹→图书映射 + 孤儿进度 + `/api/book-sync/**`（含 [ADR-0006](doc/adr/0006-kosync-self-implemented.md) 与[协议实证档](doc/m3-sync/调研/01-KOSync协议实证.md)） |
| [Subsonic API 参考](doc/协议参考/Subsonic_API_参考.md) | 协议规范事实：端点、认证、错误码、客户端生态 |
