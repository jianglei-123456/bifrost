# Bifrost 管理端契约速查（前端实现须知）

> **这不是契约的权威来源，权威来源是源码。** 端点/字段清单请直接读
> `bifrost-api/src/main/java/com/bifrost/api/controller/*`，可执行契约是 `hurl/api/*.hurl`（`powershell -File hurl\run.ps1`）。
> 本文件只记录**读源码不易看出**的东西：信封与错误码约定、认证行为、以及若干"代码行为与直觉/注释/旧文档不符"的坑。
> 原为前端仓库的事实表 `docs/bifrost-core-api-facts.md`（2026-09 由跨仓库勘察得出），工程并入本仓库后按 [ADR-0010](../../docs/adr/0010-frontend-merged-into-core-repo.md) 降级重写。

最后核对：2026-09（迁移时逐条回源码复核过下文各条"坑"）。

## 1. 基址与认证

- 基址 `http://<host>:18080`，无 context-path。管理端在 `/api/**`；图书订阅/下载/封面在 `/opds/v1.2/**`；音乐封面在 `/rest/getCoverArt.view`。
- **没有登录端点、没有服务端会话、没有 cookie**。`/api/**` 只认两种凭据：`Authorization: Basic base64(user:pass)` 或 `?u=&t=&s=`（`t = md5(password + salt)` 小写 hex）。
- **唯一豁免认证的 URI 是 `GET /api/ping`**，且它返回裸字符串 `pong`——**不走信封**，是唯一有这待遇的端点。
- 认证失败由过滤器**内联**写出：HTTP 401 + `{"code":1002,"message":"未认证","data":null}`（不经过 `@RestControllerAdvice`）。
- `/opds/**` 默认**匿名**；只有 `bifrost.opds.require-auth=true` 时才挂上 Basic 链。所以前端拿封面时，图书走匿名 OPDS、音乐走带令牌的 Subsonic——两条完全不同的鉴权路径（见 [ADR-0008](../../docs/adr/0008-cover-art-via-subsonic.md)）。
- 登录态本身怎么维持见 [ADR-0009](../../docs/adr/0009-auth-model.md)。

## 2. 信封、分页、错误码

- 信封 `{code, message, data}`：成功 `code:0, message:"ok"`。错误**同时**带真实 HTTP 状态码（400/401/403/404/409/500），不是"永远 200"。
- 分页 `data: {total, items}`，`page` **0-based**、`size` 默认 20、上限 200。
  - **各端点对非法入参的处理并不一致**，别假设统一：`/api/books` 对 `page < 0` 直接 400/1000，`/api/artists`、`/api/tracks` 把 `page < 0` 当作 0；`size > 200` 是被**静默截断**（不报错）。
  - OPDS 侧是另一套：`/opds/v1.2/catalog/all` 用 **1-based** `page` + `count`（默认 50，夹在 10–200），`page=0` 直接 400。
- 错误码（`bifrost-common/.../constant/ErrorCodes.java`）：`0` 成功、`1000` 参数错、`1001` 不存在、`1002` 未认证、`1003` 无权限、`1004` 冲突（如目录路径重复）、`1100` 扫描进行中、`1200` 内部错误；2000+ 音乐域、3000+ 影视/图书域保留。
- **`404` 在本项目有语义，不是 bug**：`/rest`、`/opds`、`/users`、`/syncs`、`/healthcheck` 占着根命名空间，根路径的未知路径**刻意** 404；SPA 兜底只作用于 `/admin/**`。客户端把地址拼错会明确拿到 404，而不是一页"看起来成功"的 HTML（[ADR-0007](../../docs/adr/0007-single-image-admin-under-admin.md)）。
- `/api/**` 与 `/opds/**` 的错误体**不是一套**：OPDS 出错返回 `application/xml` 的 `<error><code>…</code></error>`，其中 `<code>` 是**HTTP 状态码**（404/400/500），不是 1000 系列业务码。别用解析 `/api` 信封的代码去解析 OPDS。

## 3. 扫描语义：音乐同步、图书异步（且图书的错误会被吞）

- **音乐扫描是同步的**：`POST /api/music-roots/{id}/scan` 阻塞到扫描结束，响应里就是最终 `ScanStats`（`{added,updated,missing,error}`）。并发触发 → 409/1100；目录被禁用 → 400/1000。
- **图书扫描是异步的**：`POST /api/book-roots/{id}/scan` 立即返回 `{scanStatus:"SCANNING", rootId, message}`，真实进度只能轮询 `GET /api/book-roots/scan/status`。
- ⚠️ **坑：图书扫描端点会把所有错误吞掉，永远返回 200 + SCANNING**。控制器把 `bookScanService.scanRoot(...)` 包在 `CompletableFuture.runAsync` 里、**自带 `try/catch` + `log.warn`**，所以"正在扫描 → 1100"、"目录已禁用 → 1000"、"目录不存在 → 1001"**都不会到达客户端**。
  - 这与该端点的行内注释（"`BookScanService` 内部 tryLock 失败 → `BizException` 1100 抛回客户端"）以及 `doc/m2-book/task/06-前端对接.md` 里把 1100 列为图书扫描错误**相矛盾**——以代码为准。
  - 前端因此**不能**靠这个端点的响应判断是否真的启动了扫描，只能轮询 status（或看服务端日志）。`POST /api/book-roots/scan/all` 同理。
- ⚠️ **坑：`BookScanStatusView.startedAt` 不是真正的开始时间**。它取的是**读 status 那一刻的 `Instant.now()`**，不是扫描启动时刻——**不能**拿它算已耗时或作进度基准。
- ⚠️ **坑：`BookRootDto` 没有 `lastScanStats` 字段**（`MusicRootDto` 有，且是**JSON 字符串** `{"added":3,...}` 需前端二次 parse）。图书的"上次统计"只在 `BookScanStatusView.lastStats` 里。
- 音乐、图书各持**独立** `ReentrantLock`，互不干扰；同一类型内全局只允许一个扫描。
- **定时扫描已接线**：`bifrost-core/.../scan/ScanScheduler.java` 有 `@Scheduled(cron="${bifrost.scan.cron}")`，`BifrostApplication` 带 `@EnableScheduling`；默认 cron `0 0 3 * * *`（6 段含秒，每天 03:00），**留空 = 关闭**。（旧事实表"无 `@EnableScheduling` / `@Scheduled`、定时扫描未接线"的说法已过时。）

## 4. 逐端点行为坑（源码可查字段，此处只列反直觉处）

- `GET /api/music-roots` / `GET /api/book-roots` 都返回**全部**目录（含已禁用）；`scan/status` 却只含**已启用**的——同一个界面里两个端点的集合不一样。
- `GET /api/book-roots?mediaType=VIDEO` **忽略该参数**，照样返回 BOOK 列表（过滤在代码里写死，无校验）；`POST` 时 body 里的 `mediaType` 同样被忽略并强制为 BOOK。
- **`PUT` 更新目录已不存在**，只有 `PATCH`，且是**部分更新**（null/空白字段跳过），**无法把字段清空为 null**。改 `path` 要等下次扫描才生效。
- 目录 `path` 是**全局唯一**列（不带 mediaType 过滤）：把一个已被**图书**目录占用的路径拿去建**音乐**目录，会得到 400/1004——跨媒体类型也会撞。
- `PATCH /api/books/{id}` 走**白名单**：只认 `title/authors/language/publisher/pubDate/description/subject/identifier/series/seriesIndex/rights`；`authors`/`subject` 可接受字符串**或**字符串数组（数组按 `" & "` / `"; "` 拼接）；想写 `rating`/`starredAt` 等"Day-one 锁定"字段会得到 400/1000。元数据改动**只改库、绝不回写磁盘文件**。
- `BookDto` 带 `@JsonInclude(NON_NULL)`——**null 字段不出现在 JSON 里**，前端要把每个字段都当可选；它**故意不暴露** `filePath`/`fingerprint`/`rating`/`starredAt`。
- `BookDto.coverUrl` 是**相对路径** `/opds/v1.2/catalog/{id}/cover`，只在 `coverSource != null` 时出现，渲染前要自己拼 origin。
- `DELETE /api/books/{id}` **只删库行，不删磁盘文件**，也不级联；`DELETE /api/music-roots/{id}` 会把该根下曲目标成 `isAvailable=false` 后再删根行。
- 目录列表 `name`/`path` 会 trim，空值 → 400/1000。

## 5. 本机运行的相关配置（`bifrost-bootstrap/src/main/resources/application.yml`）

```yaml
server.port: 18080                    # 无 context-path
bifrost:
  scan.cron: "0 0 3 * * *"            # 留空 = 关闭定时扫描
  scan.batch-size: 200
  auth.initial-username: admin
  request-log.enabled: true           # 每个请求打一行原始 URL；凭据参数 p/t/s 记为 ***
```

- 仅存于 `BifrostProperties` Java 默认值（yml 里没有，可被 yml/env/args 覆盖）：`bifrost.cors.allowed-origins`（默认 `["*"]`，**空列表 = 关闭 CORS**）、`bifrost.opds.require-auth`（默认 `false`）。
- CORS **已配置**（`/api/**` 与 `/rest/**` 两条链都 `.cors(withDefaults())`），方法含 `PATCH`。但管理端走同源（`/admin/`），**正常不触发预检**——CORS 是给外部 Web 客户端留的。
- 配置覆盖优先级：env/启动参数 > `SPRING_CONFIG_ADDITIONAL_LOCATION` 外部 yml > 内置 `application.yml`。

## 6. 相关文档

| 主题 | 去处 |
| --- | --- |
| 图书里程碑前端对接契约（端点表 + 字段样例） | [`doc/m2-book/task/06-前端对接.md`](../../doc/m2-book/task/06-前端对接.md)（注意 §1.3 那条 1100 的说法已被上文 §3 更正） |
| 阅读进度同步前端设计 | [`docs/book-sync-design.md`](book-sync-design.md) |
| 管理端设计系统与文案 | [`docs/design.md`](design.md) |
| 领域词汇 | 根 [`CONTEXT.md`](../../CONTEXT.md)（共享词）+ 其 §管理端（前端） |
| 认证模型 / 封面取法 | [ADR-0009](../../docs/adr/0009-auth-model.md)、[ADR-0008](../../docs/adr/0008-cover-art-via-subsonic.md) |
