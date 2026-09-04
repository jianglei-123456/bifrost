# 03 管理 REST（`/api/books` + `/api/book-roots`）

> 范围：M2-book mini-milestone 第 3 阶段。复用 `bifrost-api` 模块；管理端 Vue Dashboard（在 `../bifrost-dashboard` 兄弟项目）按 `CONTEXT.md` 约定消费 `/api/**` 契约。

## 阶段目标

- `/api/book-roots` 完整 CRUD + 扫描触发 + 扫描状态；
- `/api/books` 列表（分页 + 过滤）+ 详情 + 元数据 PATCH（不写回文件）+ 删除；
- `/api/books/{id}/cover` POST（上传替换）/ DELETE（删除回退 coverSource=null）；
- 复用既有 `GlobalExceptionHandler` + 信封格式（`{code,message,data}` / `{total,items}`）；
- 复用 Spring Security `/api/**` 认证（admin 账密）。

## 端点清单

| 路径 | 方法 | 用途 | 入参 | 出参 |
|---|---|---|---|---|
| `/api/book-roots` | GET | 列出 BOOK 类型库根（`?mediaType=BOOK` 默认） | — | `{total, items: [...]}` |
| `/api/book-roots` | POST | 新建 BOOK 根 | `{name, path, enabled=true}` | `BookRoot` |
| `/api/book-roots/{id}` | GET | 详情 | — | `BookRoot` |
| `/api/book-roots/{id}` | PATCH | 部分更新 | `{name?, path?, enabled?, mediaType?}` | `BookRoot` |
| `/api/book-roots/{id}` | DELETE | 删除 | — | 204 |
| `/api/book-roots/{id}/scan` | POST | 触发该根扫描 | `{force?: false}` | `{scanStatus: "SCANNING", rootId, message}` |
| `/api/book-roots/scan/all` | POST | 触发所有 BOOK 根扫描 | `{force?: false}` | 同上 |
| `/api/book-roots/scan/status` | GET | 全局 BOOK 扫描状态 | — | `{scanStatus, currentRootId?, startedAt?, lastScanAt?, stats?}` |
| `/api/books` | GET | 列表 | `?page=1&size=20&title=&author=&series=&libraryRootId=&isAvailable=true` | `{total, items: [...]}` |
| `/api/books/{id}` | GET | 详情 | — | `Book`（含 `coverUrl` 绝对路径或相对路径） |
| `/api/books/{id}` | PATCH | 编辑元数据 | 见下文 PATCH 白名单 | `Book` |
| `/api/books/{id}` | DELETE | 删 DB 行 | — | 204（**文件保留**） |
| `/api/books/{id}/cover` | POST | 上传替换 | `multipart/form-data; file=@...` | 204 |
| `/api/books/{id}/cover` | DELETE | 删除封面 | — | 204（coverSource=null） |

## Controller 设计

`bifrost-api/src/main/java/com/bifrost/api/controller/`

```
BookRootController.java
BookController.java
BookCoverController.java
BookScanController.java              # 仅扫描状态（与 BookRootController 拆开避免混）
```

依赖：
- `BookRepository`
- `LibraryRootRepository`
- `BookScanService`（仅扫描触发 + 状态读取）
- `BookCoverService`（上传/删除）

## PATCH `/api/books/{id}` 字段白名单（Q28）

**可写**：
- `title` (String)
- `authors` (String **或** List<String>) — 数组用 `String.join(" & ", list)` 拼接
- `language` (String)
- `publisher` (String)
- `pubDate` (Integer, 1–9999)
- `description` (String)
- `subject` (String **或** List<String>) — 数组用 `String.join("; ", list)`
- `identifier` (String)
- `series` (String)
- `seriesIndex` (Double)
- `rights` (String)
- `rating` (Integer 0–5) — Day-one **不暴露**（Q24 决定 REST 暂时 404 写入）

**不可写**（收到 → 400 / 404）：
- `id` / `filePath` / `fileSize` / `fileLastModified` / `fingerprint` / `format` / `extension` / `libraryRootId` / `isAvailable` / `coverSource` / `createdAt` / `updatedAt` / `starredAt`

PATCH 内部流程：
1. JSON 反序列化为 `JsonNode`（不直接绑 record）
2. 遍历可写字段 set 到 Book 实体
3. `bookRepository.save(book)`（JPA dirty checking）
4. 返回更新后的 `Book` DTO

## 扫描触发行为

- `POST /api/book-roots/{id}/scan`：
  - 调 `BookScanService.scanRoot(id, force)` —— **同步等待锁失败立即返回**
  - 锁失败 → `BizException(BIZ_SCAN_IN_PROGRESS, 1100)`
  - 锁成功 → 在 `CompletableFuture.runAsync(...)` 中执行（**与 music `/startScan` 同模式**），Controller 立即返回 `{scanStatus: "SCANNING", rootId}`
  - 客户端轮询 `GET /api/book-roots/scan/status`
- `POST /api/book-roots/scan/all`：遍历所有 BOOK enabled 根串行扫描（**不并行**——磁盘 IO 互斥）
- 互斥锁由 `BookScanService` 内部 ReentrantLock 保证，**Controller 不感知**

## 上传封面（Q24 / Q29）

`POST /api/books/{id}/cover`（multipart）：
- 接收 `file` 字段（二进制）
- **类型校验**：`Content-Type` 必须在 `image/jpeg|image/png|image/gif` 内
- **大小限制**：5 MB（`@RequestPart("file")` + Spring 自动 + 业务层校验）
- **处理**：
  - 读 bytes
  - 调 `bookCoverService.storeUploadedCover(book, bytes)`
  - 返回 204
- `coverSource` 标 `"UPLOADED"`——下次扫描遇到 EMBEDDED 抽取**不覆盖**（Q24 决策）

`DELETE /api/books/{id}/cover`：
- 调 `bookCoverService.deleteCover(book)` —— 删文件 + `coverSource=null`
- 返回 204
- 扫描再次遇到该文件时**自动**重新 EMBEDDED 抽取（实现见 `BookScanService`：book 行的 coverSource 变 null 后重新 parse 时会调 `storeEmbeddedCover`）

## 任务清单

### T3.1 — DTO 定义

`bifrost-api/src/main/java/com/bifrost/api/dto/book/`

```java
record BookDto(Long id, String title, String authors, String language, String publisher,
               Integer pubDate, String description, String subject, String identifier,
               String series, Double seriesIndex, String rights, String format, String extension,
               Long fileSize, Instant fileLastModified, String coverSource, String coverUrl,
               Boolean isAvailable, Long libraryRootId,
               Instant createdAt, Instant updatedAt) {}

record BookRootDto(Long id, String name, String path, Boolean enabled, MediaType mediaType,
                   Instant lastScanAt, ScanStatus scanStatus) {}
```

`coverUrl`：当 `coverSource != null` 时 = `/opds/v1.2/catalog/{id}/cover`（**绝对路径**由 Vue Dashboard 拼接 origin），否则 = null。

### T3.2 — BookRootController

按 `LibraryRootController` 同模式实现，复用 `BifrostProperties` / `LibraryRootRepository` / `BookScanService`。

### T3.3 — BookController

- `GET /api/books`：`Pageable` + `Specification<Book>` 动态拼过滤（title/authors/series LIKE；libraryRootId =；isAvailable =）
- `GET /api/books/{id}`：详情；不存在 → 1001
- `PATCH /api/books/{id}`：JsonNode 处理 + 白名单
- `DELETE /api/books/{id}`：删 DB 行（`bookRepository.deleteById(id)`），**不删文件**（Q13 不级联；文件在 LibraryRoot 路径下，物理保留）

### T3.4 — BookScanController

仅扫描状态读：
```java
@GetMapping("/api/book-roots/scan/status")
BookScanStatusDto getStatus() {
    return bookScanService.getStatus();
}
```

扫描触发合并到 `BookRootController`（`POST /{id}/scan` 和 `POST /scan/all`）。

### T3.5 — BookCoverController

- `POST /api/books/{id}/cover`（`@RequestPart("file") MultipartFile file`）
- `DELETE /api/books/{id}/cover`

### T3.6 — hurl 契约测试

- `hurl/api/book-roots.hurl`：CRUD + scan trigger + scan status
- `hurl/api/books.hurl`：列表分页 + 过滤 + 详情 + PATCH 白名单
- `hurl/api/books-cover.hurl`：upload + delete

### T3.7 — Swagger / OpenAPI

复用 `/v3/api-docs` SpringDoc（**若**项目已启用；如未启用，Day-one 不引入，contract 仅靠 hurl 文档化）。

## 完成标准

```bash
./mvnw -pl bifrost-api,bifrost-bootstrap -am clean test
hurl --test hurl/api/book-roots.hurl hurl/api/books.hurl hurl/api/books-cover.hurl
```

人工验收：
1. 浏览器 `http://localhost:8080/api/book-roots`（带 admin Basic）→ 返回 200 + JSON 列表
2. `curl -X POST -H "Content-Type: application/json" -d '{"name":"testbook","path":"E:/testbook","mediaType":"BOOK"}' ...` → 201 + 新建根
3. `curl -X POST .../api/book-roots/{id}/scan` → 立即返回 SCANNING；`E:\testbook` 目录文件被解析入 DB
4. `curl -X PATCH .../api/books/{id} -d '{"authors":"A & B & C","rating":3}'` → 200（authors 更新，rating 因 Day-one 锁定 → 400 拒）
5. `curl -X POST .../api/books/{id}/cover -F "file=@/tmp/cover.jpg"` → 204，OPDS feed 中该 book 出现 image link
