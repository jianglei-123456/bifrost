# 03 管理 REST（`/api/book-sync/**`）

> 阶段目标：让 Dashboard 能配置同步账号、看进度列表、处理孤儿、看设备列表。
> 前缀按 ADR-0005 媒体前缀约定使用 **`/api/book-sync/**`**（C2 决策；协议端点仍是挂根的 `/users/**`、`/syncs/**`、`/healthcheck`，两者互不影响）。
> **无需新增安全链**：`/api/**` 既有链（Basic 或 `u/t/s` 令牌）自动覆盖；沿用统一信封、分页与错误码约定。

---

## T3.1 约定

- 信封 `ApiResponse<T>`（`code=0` / `message="ok"` / `data`）、分页 `PageResult<T>(total, items)`、`page` **0-based**、`size` 默认 20 上限 200（与 `/api/books` 一致）。
- 错误码复用 `ErrorCodes`：`1000` 参数、`1001` 不存在、`1003` 禁止、`1004` 冲突；HTTP 映射沿用 `GlobalExceptionHandler`。
- DTO 一律 `com.bifrost.api.dto.booksync.*` 的 `record` + 静态 `of(...)` 工厂；`@JsonInclude(NON_NULL)` 按需。
- 写操作全部走 `bifrost-core` 的 `book.sync` 服务（`@Transactional` 在服务层）。

## T3.2 同步账号

### `GET /api/book-sync/account`

```json
{"code":0,"message":"ok","data":{
  "username":"reader",
  "password":"Xk7mQ2pRt9vB4nZs",          // 明文回显（R2a）
  "registrationEnabled":true,
  "autoScanOnUnmatched":true,
  "serverUrl":"http://192.168.1.10:18080", // baseUrlHint 优先，否则按 T3.2 规则拼
  "serverUrlHintConfigured":false
}}
```

- `password` 由 `PasswordCipher.decrypt` 还原（可逆存储是 R2a 的前提）。
- `serverUrl` 生成规则：配置 `bifrost.kosync.public-base-url` 非空则直接用它；否则 `http://<请求的 Host 主机名>:<server.port>`（`server.port` 由 `@Value("${server.port:8080}")` 注入）。**这是给用户抄进 KOReader 的字符串，务必带 `http://` 前缀**——KOReader 的自定义服务器输入框预填 `https://` 且**不会自动补 scheme**。

### `PUT /api/book-sync/account`

请求体 `{username?, password?}`（都可选，只改传了的字段）。

| 校验 | 失败 |
|---|---|
| `username` 非空、≤64、**不含 `:`** | `1000` |
| `password` 非空、≤128 | `1000` |
| **`password` 的 md5 与管理员口令的 md5 相同**（或明文相同） | `1000`「同步口令不能与管理员口令相同」（**R2c 强制**） |

- 改 `username` **不迁移进度**（进度按 `syncAccountId` 隔离，账号是同一行 → 进度保留；设备端需重新填用户名）。响应 200 + 更新后的账号对象。
- 改 `password` 后，已登录的设备下一次请求会拿到 **401**（客户端不会重投队列，用户需在设备上重新 Login）——管理端文案要说清楚。

### `POST /api/book-sync/account/password`

生成 16 位随机口令（`SecureRandom`，剔除易混字符 `0O1lI`），**落库并返回明文**（R2a 的"生成"路径）。响应同 `GET /account`。

### `DELETE /api/book-sync/account/password`？——不做

不做"清空口令"：协议必须有凭据，清空等于关掉同步而没有任何 UI 价值。

## T3.3 进度列表

### `GET /api/book-sync/progress`

查询参数：`page`（默认 0）、`size`（默认 20，1–200）、`title`（按书标题模糊）、`device`（按设备名模糊）、`libraryRootId`、`onlyOrphans`（默认 false）。

```json
{"total":12,"items":[{
  "id":31,"bookId":7,"bookTitle":"Pride and Prejudice","bookAuthors":"Jane Austen",
  "libraryRootId":2,"coverUrl":"/opds/v1.2/catalog/7/cover",
  "documentFingerprint":"59d481d168cca6267322f150c5f6a2a3",
  "percentage":0.4231,"progress":"/body/DocFragment[7]/body/p[3]/text().0",
  "device":"Kobo_nova","deviceId":"0f2c…","reportedAt":"2026-09-16T12:00:00Z",
  "matchSource":"AUTO","ignored":false}]}
```

- **时间字段一律 `Instant` → ISO-8601 字符串**（与 `BookDto` 的 `createdAt/updatedAt/fileLastModified` 完全一致，前端可直接用现有 `formatDateTime`）。**秒级 epoch 只出现在 KOSync 协议端点**的 `timestamp` 里（那是给 KOReader 的，管理端不接触）。
- `coverUrl` 复用 `BookDto` 的规则（`coverSource != null` 时给 `/opds/v1.2/catalog/{id}/cover`），复用同一段逻辑避免两处漂移。
- **附带改动（T4.3 的 hurl 依赖它）**：`BookDto` 增加 `partialMd5` 字段（`GET /api/books` 可见）。用途：① 契约测试从 API 捕获真实指纹而不是硬编码；② 管理端图书详情/孤儿绑定页展示"文档指纹"便于排障。它属于阅读进度域，与 `PATCH /api/books` 的可写白名单无关（仍不可写）。
- 排序：`updatedAt DESC`。
- `onlyOrphans=true` 等价于 `bookId is null`（孤儿页签也走这个接口，或直接用 T3.4 的专用接口——两者都提供，前端按需）。

### `DELETE /api/book-sync/progress/{id}`

- 删除该条进度（"我想从头再读一遍"）。删的是**服务端记录**；设备端本地仍持有自己的位置，下一次推送会重新建立记录——**管理端文案必须写明这一点**（否则用户会以为"重置"能让设备回到开头）。
- 不存在 → `1001`。

## T3.4 孤儿进度

### `GET /api/book-sync/orphans`

查询参数：`page`、`size`、`includeIgnored`（默认 false）。

```json
{"total":2,"items":[{
  "id":44,"documentFingerprint":"41cce710f34e5ec21315e19c99821415",
  "percentage":0.0812,"progress":"12","device":"KindlePaperWhite5","deviceId":"c81a…",
  "createdAt":"2026-09-17T03:06:40Z","scanAttemptedAt":"2026-09-17T03:11:40Z","ignored":false,
  "suggestedBookId":19,"suggestedBookTitle":"Some Book","suggestionReason":"FILENAME"}]}
```

- `suggestedBookId/suggestionReason` 由 `OrphanProgressService` 按需计算（T1.5）：候选来源 `md5(basename)`（`FILENAME`）或 `md5(title + "." + extension)`（`OPDS_NAME`）。**只建议不自动绑**。
- `scanAttemptedAt != null` 即"已结算的固定孤儿"；`null` 表示"自动扫描尚未结算"（正常是极短窗口，长挂说明自动扫描被跳过或失败——页面上用角标提示）。

### `POST /api/book-sync/orphans/{id}/bind`

请求体 `{"bookId": 19}`。校验书存在且 `isAvailable`；写入 `bookId`、`matchSource=MANUAL`、`scanAttemptedAt=now`（若为空则补）。返回更新后的进度视图。书不存在 → `1001`。

### `POST /api/book-sync/orphans/{id}/rematch`

人工重跑一次匹配（`ProgressBookMatcher.match`）：命中 → 等同 `bind`（`matchSource=AUTO`）；未命中 → **200** 且 `data.matched=false`（不是错误——"确实匹配不到"是正常结果）。用于"我后来把书放进库了"的场景。

### `POST /api/book-sync/orphans/{id}/ignore`

请求体 `{"ignored": true|false}`（默认 true）。忽略后从默认列表隐藏，可取消。

## T3.5 设备

### `GET /api/book-sync/devices`

不分页（家庭规模），按 `lastSeenAt DESC`：

```json
{"total":2,"items":[{"id":3,"deviceId":"0f2c…","deviceName":"Kobo_nova",
  "firstSeenAt":"2026-09-04T20:53:20Z","lastSeenAt":"2026-09-16T12:00:00Z","reportCount":128}]}
```

**不做"踢设备"**（Q5 决策）：协议层没有会话，凭据只有一对用户名/口令，封锁单个设备在协议层无法生效——做成按钮就是假能力。设备页只提供**信息**与"改口令"的跳转。

### `GET /api/book-sync/stats`

页面头部卡片用：`{progressCount, matchedCount, orphanCount, deviceCount, lastReportedAt}`（`lastReportedAt` 为 ISO-8601 字符串；无数据时 null）。

## T3.6 测试与契约

| 测试 | 类型 | 覆盖 |
|---|---|---|
| `BookSyncAccountIntegrationTest` | `@SpringBootTest` + MockMvc | 读账号回显明文；改口令成功；**同步口令与 admin 口令相同时 400/1000**；生成的随机口令可通过协议 `verify` |
| `BookSyncProgressIntegrationTest` | 集成 | 分页/过滤/封面链接；删除进度 404/1001；删除 Book 后进度变孤儿且 `scanAttemptedAt != null`（R5-c） |
| `BookSyncOrphanIntegrationTest` | 集成 | 绑定/忽略/`rematch` 命中与未命中；建议绑定的两类候选 |
| hurl `hurl/api/book-sync.hurl` | 契约 | 端点矩阵 + 信封/分页/错误码（04 阶段接入 `run.ps1`） |

完成后：`./mvnw -pl bifrost-api -am test` 通过 → 提交。
