# Bifrost

家庭媒体库管理平台：统一管理 **视频 / 音频（音乐）/ 电子书** 三类媒体资源。

**里程碑 M1 ✅**：音乐管理 + **Subsonic 兼容协议服务（Syrinx）**——Bifrost 自身即 Subsonic 服务端，Feishin、DSub、Symfonium、play:Sub、Supersonic 等客户端可直接连接浏览、搜索、播放与管理音乐。管理端前端（Vue Dashboard）在独立项目开发，消费 `/api/**` REST 契约。

**里程碑 M2-book ✅**：图书管理 + **OPDS 1.2 协议发布**——KOReader（真机/模拟器）和 Readest（桌面/Web）可通过标准 OPDS catalog 浏览、搜索、下载 EPUB/PDF；管理端 `/api/book-roots` + `/api/books` 提供 CRUD + 扫描 + 元数据编辑 + 封面上传。图书侧与音乐侧物理隔开（独立实体、独立扫描器、独立封面存储、独立过滤器链，见 [ADR-0004](doc/adr/0004-book-physical-isolation.md)）。进度同步（KOSync/WebDAV/PSE）延后到后续里程碑（见 [doc/m2-book/task/05-延后项.md](doc/m2-book/task/05-延后项.md)）。

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

应用默认监听 `http://localhost:8080`：

```bash
curl http://localhost:8080/api/ping          # → pong
curl "http://localhost:8080/rest/ping.view?u=admin&t=<token>&s=<salt>&v=1.16.1&c=test"   # → subsonic-response ok
curl http://localhost:8080/opds/v1.2/catalog # → Atom OPDS navigation feed（图书）
```

数据目录（SQLite 数据库、封面缓存）默认位于 `./data`，可通过配置与环境变量调整。

### 图书库使用流程

1. 在管理 REST 创建 BOOK 根（`POST /api/book-roots`，`mediaType=BOOK`）；
2. 触发扫描（`POST /api/book-roots/{id}/scan`），从 EPUB/PDF 解析元数据入库；
3. KOReader 添加 OPDS catalog：`http://<host>:8080/opds/v1.2/catalog`；Readest 同上；
4. 客户端可浏览 / 搜索 / 下载（KOReader EPUBC；Readest EPUB + PDF）；
5. **可选鉴权**：在 `application.yml` 设置 `bifrost.opds.require-auth: true` 强制 Basic 鉴权（默认匿名）。

## 测试

```bash
./mvnw clean verify                        # 单元 + 集成测试
powershell -File hurl\run.ps1              # hurl 端点契约测试（需 ffmpeg 与 hurl CLI，见 hurl\README.md）
```

## Docker 部署（延后，代码稳定后补充）

```bash
# 规划形态（M1 延期交付，见 doc/m1/task/07-延后项.md；代码稳定后提供 docker/Dockerfile 与 docker-compose.yml）
BIFROST_AUTH_INITIAL_PASSWORD=yourpass docker compose up -d
```

- 卷挂载 `./data:/data`：SQLite 文件、封面缓存、密钥均在其中，**备份该目录即完成数据备份**。
- 配置覆盖：挂载 `/data/application.yml`（`SPRING_CONFIG_ADDITIONAL_LOCATION`）。
- 部署细节见《整体技术架构》第 9.1 节。

## 文档

| 文档 | 说明 |
| --- | --- |
| [操作手册 · 总览](doc/操作手册/00-总览.md) | 手册目录与三分钟快速上手 |
| [操作手册 · 安装与启动](doc/操作手册/01-安装与启动.md) | 构建启动、初始密码、配置库根与扫描、备份 |
| [操作手册 · 连接 Subsonic 客户端](doc/操作手册/02-连接Subsonic客户端.md) | **客户端连接指南：服务器地址 / 用户名 / 密码怎么填**（Feishin、DSub、Symfonium 等） |
| [操作手册 · 常见问题](doc/操作手册/03-常见问题.md) | 连不上、认证失败 40、密码忘记等 FAQ |
| [项目整体功能说明](doc/功能设计/项目整体功能说明.md) | 产品定位、场景、功能地图、里程碑 |
| [通用功能说明](doc/功能设计/通用功能说明.md) | 配置/日志/认证/持久化/REST 约定等公共能力 |
| [音乐管理功能说明](doc/功能设计/音乐管理功能说明.md) | 音乐管理与 Subsonic 服务能力、客户端兼容矩阵 |
| [整体技术架构](doc/技术设计/整体技术架构.md) | 模块架构、技术栈、存储、配置、Docker |
| [音乐管理技术设计](doc/技术设计/音乐管理技术设计.md) | 领域模型、扫描算法、Subsonic 端点实现设计 |
| [Subsonic API 参考](doc/协议参考/Subsonic_API_参考.md) | 协议规范事实：端点、认证、错误码、客户端生态 |
