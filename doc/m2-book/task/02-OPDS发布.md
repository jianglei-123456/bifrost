# 02 OPDS 发布（端点 + Atom 模板 + 认证）

> 范围：M2-book mini-milestone 第 2 阶段。复用 `bifrost-adapter/opds-publisher` 占位模块；端点路径严格按 OPDS 1.2 标准。

## 阶段目标

- 7 个 OPDS 端点全部上线（Q19-C 简化集）；
- Atom XML 模板（含 OSDD）通过 KOReader + Readest 验证；
- 默认匿名 + 配置项 `bifrost.opds.require-auth`；
- `bifrost-bootstrap` 跨模块扫描自动注册 Controller。

## 端点清单

| 路径 | 方法 | 类型 | 行为 |
|---|---|---|---|
| `/opds/v1.2/catalog` | GET | navigation feed | 目录根，2 个子条目（all + recent） |
| `/opds/v1.2/catalog/all` | GET | acquisition feed | 全部图书分页（`page` 1-based，`count` 默认 50，10–200） |
| `/opds/v1.2/catalog/recent` | GET | acquisition feed | 最近添加（`ORDER BY createdAt DESC`），发 `rel="http://opds-spec.org/sort/new"` |
| `/opds/v1.2/search.xml` | GET | OSDD | OpenSearch 描述文档 |
| `/opds/v1.2/search` | GET | acquisition feed | 搜索结果，匹配 title/authors/series/identifier/publisher/subject LIKE |
| `/opds/v1.2/catalog/{bookId}/file` | GET | binary | 整本下载（epub 或 pdf），**支持 HTTP Range（206）** |
| `/opds/v1.2/catalog/{bookId}/cover` | GET | image/jpeg | 封面图（`?size=` 缩略图，无则原图） |

## Atom XML 模板

### catalog 根（navigation feed）

```xml
<feed xmlns="http://www.w3.org/2005/Atom"
      xmlns:opds="http://opds-spec.org/2010/catalog"
      xmlns:dc="http://purl.org/dc/elements/1.1/"
      xmlns:opensearch="http://www.openarchives.org/OAI/2.0/opensearch/1.1/">
  <id>urn:bifrost:opds:catalog</id>
  <title>Bifrost 图书库</title>
  <updated>${SERVER_TIME}</updated>
  <link rel="self" href="/opds/v1.2/catalog" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
  <link rel="start" href="/opds/v1.2/catalog" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
  <link rel="search" href="/opds/v1.2/search.xml" type="application/opensearchdescription+xml"/>
  <entry>
    <id>urn:bifrost:opds:nav:all</id>
    <title>全部图书</title>
    <updated>${SERVER_TIME}</updated>
    <link rel="subsection" href="/opds/v1.2/catalog/all"
          type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
  </entry>
  <entry>
    <id>urn:bifrost:opds:nav:recent</id>
    <title>最近添加</title>
    <updated>${SERVER_TIME}</updated>
    <link rel="subsection" href="/opds/v1.2/catalog/recent"
          type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
  </entry>
</feed>
```

### all / recent / search（acquisition feed）

```xml
<feed xmlns="http://www.w3.org/2005/Atom"
      xmlns:opds="http://opds-spec.org/2010/catalog"
      xmlns:dc="http://purl.org/dc/elements/1.1/"
      xmlns:opensearch="http://www.openarchives.org/OAI/2.0/opensearch/1.1/">
  <id>urn:bifrost:opds:all:page:${PAGE}</id>
  <title>全部图书</title>
  <updated>${SERVER_TIME}</updated>
  <opensearch:totalResults>${TOTAL}</opensearch:totalResults>
  <opensearch:startIndex>${START}</opensearch:startIndex>
  <opensearch:itemsPerPage>${COUNT}</opensearch:itemsPerPage>
  <link rel="self" href="/opds/v1.2/catalog/all?page=${PAGE}&amp;count=${COUNT}"
        type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
  ${NEXT_LINK}
  ${PREV_LINK}
  <entry>
    <id>urn:bifrost:book:${BOOK_ID}</id>
    <title>${TITLE_ESCAPED}</title>
    <updated>${UPDATED_AT}</updated>
    <author><name>${AUTHOR_1_ESCAPED}</name></author>
    <author><name>${AUTHOR_2_ESCAPED}</name></author>
    <dc:language>${LANG}</dc:language>
    <dc:identifier>${IDENTIFIER_ESCAPED}</dc:identifier>
    ${COVER_LINK}
    <link rel="http://opds-spec.org/acquisition"
          href="/opds/v1.2/catalog/${BOOK_ID}/file"
          type="${MIME}" length="${FILE_SIZE}"/>
  </entry>
</feed>
```

- **多作者**：按 ` & ` split → 每个 trim 非空 → 一个 `<author><name>` 元素
- **XML 转义**：`title` / `authors` / `description` / `publisher` 等所有用户可见字段都需 `StringEscapeUtils.escapeXml`（` & ` 中的 `&` → `&amp;`）
- **cover link**：`coverSource != null` 时输出 `<link rel="http://opds-spec.org/image" .../>` + `<link rel="http://opds-spec.org/image/thumbnail" .../>`；**否则不输出**（PDF 无 cover 时不挂 link，避免 Readest 拿 404）
- **recent** 在 feed 顶多挂一个 `<link rel="http://opds-spec.org/sort/new" href="/opds/v1.2/catalog/recent" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>`

### OSDD（search.xml）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
  <ShortName>Bifrost 图书搜索</ShortName>
  <Description>搜索 Bifrost 图书库</Description>
  <Url type="application/atom+xml;profile=opds-catalog;kind=acquisition"
       template="/opds/v1.2/search?q={searchTerms}&amp;page={page?}&amp;count={count?}"/>
</OpenSearchDescription>
```

`{searchTerms}` URL 编码后替换；`{page?}` / `{count?}` KOReader 会替换，其他客户端可能原样发回（容错：默认值 1/50）。

## Controller 设计

`bifrost-adapter/opds-publisher/src/main/java/com/bifrost/adapter/opds/`

```
controller/
  OpdsController.java              # 7 个 @GetMapping
service/
  OpdsFeedBuilder.java              # Atom XML string template
  OpdsBookQueryService.java         # 查 BookRepository，构建 entry 数据
dto/
  OpdsEntryDto.java
  OpdsPageDto.java
security/
  OpdsAuthenticationFilter.java     # 可选 Basic（仅 require-auth=true 时挂）
  OpdsSecurityConfig.java
```

`OpdsFeedBuilder` 用 `String.format` + `MessageFormat` 拼模板（**不引入** ROME 等第三方 Atom 库，理由：依赖最小化、模板简单可控、XML 转义自己写）。所有用户输入字段统一过 `XmlEscapers.xmlContentEscaper().escape(str)`（Guava）或 `org.apache.commons.text.StringEscapeUtils.escapeXml11(str)`（Apache Commons Text，已在项目依赖中或可加）。

## 搜索实现

- 接收 `?q=keyword&page=1&count=50`
- `keyword` trim 后非空 → 多字段 LIKE（`title` / `authors` / `series` / `identifier` / `publisher` / `subject`）OR 查询
- 关键词中 `'` / `%` / `_` LIKE 注入转义（`likeEscape`）
- 空查询 → 400 / 重定向到 `/opds/v1.2/catalog/all?page=1`（任选其一，统一用 400 简单）
- 结果按 `createdAt DESC` 排序

## 二进制端点

- `/opds/v1.2/catalog/{bookId}/file`：
  - `bookId` 不存在或 `isAvailable=false` → 404
  - `Content-Type` = `MIME` 表（`EPUB` → `application/epub+zip`，`PDF` → `application/pdf`）
  - `Content-Disposition: attachment; filename="<原文件名>"; filename*=UTF-8''<encoded>`（RFC 5987）
  - **支持 HTTP Range**（用 `ResourceHttpRequestHandler` 或手写 `Files.newInputStream` + `LimitedInputStream`，与 music `/stream` 同模式）
  - 响应 `Content-Length`、`Last-Modified`（`fileLastModified`）、`Accept-Ranges: bytes`
- `/opds/v1.2/catalog/{bookId}/cover`：
  - 无封面 → 404（不返回 200 + 透明 PNG，避免 Readest 误显示占位）
  - `Content-Type: image/jpeg`
  - `?size=` 接受 `64` / `200` / 不传（默认 200，Day-one 简化）
  - `Cache-Control: public, max-age=86400`（封面在 day-one 不变更）

## 认证（Q6-C）

- `bifrost.opds.require-auth: false`（默认）
  - 所有 `/opds/**` 路径**不挂** Spring Security 过滤器
  - `OpdsController` 自由访问
- `bifrost.opds.require-auth: true`
  - 走 `OpdsSecurityConfig`：`/opds/**` 启用 HTTP Basic，复用 admin 账密（与 `/api/**` 共享 `UserRepository`）
  - 401 响应 `WWW-Authenticate: Basic realm="Bifrost OPDS"`
  - KOReader 输账密即可
- 配置变更需重启生效（不实现动态 reload，Day-one 简化）

## 任务清单

### T2.0 — 依赖补全

`bifrost-adapter/opds-publisher/pom.xml` 追加：
- `bifrost-core`（已有）
- `bifrost-domain`（已有）
- `spring-boot-starter-web`（已有）
- `org.apache.commons:commons-text`（**新**，仅用于 `StringEscapeUtils.escapeXml11`）

`pom.xml`（根）的 `<dependencyManagement>` 锁定 commons-text 版本（用 BOM 或显式 1.13.x）。

### T2.1 — OpdsBookQueryService

依赖：`BookRepository`、`BookCoverService`（用于判断 `hasCover`）。
方法：
```java
Page<Book> findAll(int page, int count);
Page<Book> findRecent(int page, int count);
Page<Book> search(String q, int page, int count);
Optional<Book> findById(Long id);
```

### T2.2 — OpdsFeedBuilder

方法：
```java
String buildCatalogRoot(String baseUrl, Instant serverTime);
String buildAcquisitionFeed(String baseUrl, String feedId, String title,
                            Page<Book> page, boolean isRecent);
String buildOsdd(String baseUrl);
```

模板存在 `classpath:opds/*.xml` 或 inline string（推荐 inline，少 IO）。所有用户字段过 `escapeXml11`。

### T2.3 — OpdsController

```java
@RestController
@RequestMapping("/opds/v1.2")
class OpdsController {
    @GetMapping(value = "/catalog", produces = "application/atom+xml;profile=opds-catalog;kind=navigation")
    String catalog() { ... }

    @GetMapping(value = "/catalog/all", produces = "application/atom+xml;profile=opds-catalog;kind=acquisition")
    String all(@RequestParam(defaultValue = "1") int page,
               @RequestParam(defaultValue = "50") int count) { ... }

    // /catalog/recent /search.xml /search 同模式
    // /catalog/{bookId}/file 和 /cover 返回 ResponseEntity<byte[]> / Resource
}
```

`page` 校验：`< 1` → 400；`count` 钳到 10–200（小于 10 视为 10，大于 200 视为 200）。

### T2.4 — 二进制端点

```java
@GetMapping("/catalog/{bookId}/file")
ResponseEntity<Resource> file(@PathVariable Long bookId, @RequestHeader HttpHeaders headers) {
    Book book = queryService.findById(bookId).filter(Book::getIsAvailable)
            .orElseThrow(() -> new BookNotFoundException(bookId));
    // ... Resource + ContentType + Range 处理
}

@GetMapping("/catalog/{bookId}/cover")
ResponseEntity<byte[]> cover(@PathVariable Long bookId,
                              @RequestParam(required = false) Integer size) {
    Book book = queryService.findById(bookId).orElseThrow(...);
    if (!bookCoverService.hasCover(book)) {
        return ResponseEntity.notFound().build();
    }
    byte[] bytes = bookCoverService.coverBytes(bookId, size == null ? 200 : size);
    return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
            .body(bytes);
}
```

### T2.5 — OpdsExceptionHandler

`@RestControllerAdvice(basePackages = "com.bifrost.adapter.opds")`：
- `BookNotFoundException` → 404（XML body：`<error>...</error>`，简化为 `<error><code>404</code><message>book not found</message></error>`）
- `IllegalArgumentException` → 400
- 其他 → 500

**注意**：OPDS 端点的错误响应**不是 Atom**——客户端不强求格式，简单 XML/空 200 即可。

### T2.6 — Security 集成

- `bifrost-bootstrap/src/main/java/com/bifrost/bootstrap/security/` 下追加 `OpdsSecurityConfig`（`@ConditionalOnProperty("bifrost.opds.require-auth=true")`）
- 仅当配置为 true 时挂载到 `/opds/**`

### T2.7 — hurl 契约测试

- `hurl/opds/catalog.hurl`：根 feed 结构断言（`<title>=Bifrost 图书库`、2 个 `<entry>`）
- `hurl/opds/all.hurl`：分页 + entry 数 + rel 类型
- `hurl/opds/recent.hurl`：`rel="http://opds-spec.org/sort/new"` 必须存在
- `hurl/opds/search.hurl`：`?q=keyword` 返回 acquisition feed
- `hurl/opds/file.hurl`：Range 206 + Content-Type 正确
- `hurl/opds/cover.hurl`：jpeg magic bytes 验证
- `hurl/opds/auth.hurl`：`require-auth=true` 时未带 Basic → 401

## 完成标准

```bash
./mvnw -pl bifrost-adapter/opds-publisher,bifrost-bootstrap -am clean test
hurl --test hurl/opds/*.hurl
```

人工验收：
1. KOReader（真机或模拟器）添加 catalog `http://<host>:8080/opds/v1.2/catalog`，浏览 → 看到 "全部图书" / "最近添加" 入口；点进去能看到所有 book；下载一个 EPUB 能在 KOReader 打开；封面能显示。
2. Readest（桌面版）同样测试；额外验证 "Auto-download new items" 打开后能订阅 `/opds/v1.2/catalog/recent`。
