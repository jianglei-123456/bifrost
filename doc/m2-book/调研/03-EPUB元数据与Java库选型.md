# 03 EPUB 元数据与 Java 库选型

> 调研日期：2026-09-03。
> 一手来源：EPUB 2.0.1 规范、EPUB 3.3 规范、epublib 源码、Maven Central。
> 调研 subagent 报告：`a48106da`（EPUB 多作者规范 + epublib API）。
> 本文是该 subagent 报告的**精炼版 + 项目化标注**。

---

## 1. EPUB 元数据规范

### 1.1 容器结构

```
<book>.epub (ZIP)
└── META-INF/
    └── container.xml          ← 指向 package document
└── OEBPS/                     ← 典型目录
    ├── content.opf            ← package document（metadata 在这里）
    ├── nav.xhtml              ← EPUB 3 导航
    ├── toc.ncx                ← EPUB 2 navigation
    └── ... (章节、图等)
```

### 1.2 OPF `<metadata>` 元素

| 字段 | OPF 元素 | 规范出处 | 备注 |
|---|---|---|---|
| 标题 | `<dc:title>` | EPUB 2/3 强制 | 可重复（主标题 + 副标题） |
| **作者** | `<dc:creator>` | EPUB 2/3 强制 | **多作者 = 多兄弟元素**，非分隔符串 |
| 贡献者 | `<dc:contributor>` | 选填 | 编辑/译者/插画师 |
| 语言 | `<dc:language>` | 选填（推荐） | RFC 5646，可重复 |
| 标识符 | `<dc:identifier>` | 强制 | ISBN / UUID / URL |
| 出版社 | `<dc:publisher>` | 选填 | |
| 日期 | `<dc:date>` | 选填 | 完整日期；本项目只取年份 |
| 描述 | `<dc:description>` | 选填 | 内容简介 |
| 主题 | `<dc:subject>` | 选填 | 可重复，标签/分类 |
| 版权 | `<dc:rights>` | 选填 | 许可证 |
| 系列 | `calibre:series` 或 EPUB 3 `belongs-to-collection` | 非标准 | Calibre 扩展 |
| 系列索引 | `calibre:series_index` 或 `group-position` | 非标准 | Calibre 扩展 |
| 修改时间 | EPUB 3 `<meta property="dcterms:modified">` | EPUB 3 强制 | |

### 1.3 多作者规范

EPUB 2.0.1 OPF §2.2.2：
> "Publications with multiple co-authors should provide **multiple creator elements, each containing one author**. The order of creator elements is presumed to define the order…"

EPUB 3.3 §5.5.3.2.3：
> "If an EPUB publication has more than one creator, EPUB creators should specify each in a separate `dc:creator` element. The document order of `dc:creator` elements… determines the display priority, where the first `dc:creator` element encountered is the primary creator."

**真实 EPUB 实例**（Project Gutenberg #61 Marx & Engels）：
```xml
<dc:creator opf:file-as="Marx, Karl">Karl Marx</dc:creator>
<dc:creator opf:file-as="Engels, Friedrich">Friedrich Engels</dc:creator>
```

### 1.4 封面定位优先级

1. EPUB 3 manifest `<item properties="cover-image">`（首选）
2. EPUB 2 `<meta name="cover" content="[id]"/>` + manifest 中对应 id
3. 旧式 `<guide><reference type="cover"/></guide>`（EPUB 2 早期）
4. 文件名约定 `cover.jpg` / `cover.jpeg` / `cover.png` 在 OPF 目录或上层

epublib 一行 `book.getCoverImage().getData()` 即可拿到 bytes。

---

## 2. Java 库选型

### 2.1 候选库对比

| 库 | Maven 坐标 | 版本 | 状态 | 适用 |
|---|---|---|---|---|
| **epublib 经典版** | `nl.siegmann.epublib:epublib-core` | 3.1 (2017) | **不在 Maven Central** | — |
| **epublib 镜像（推荐）** | `com.positiondev.epublib:epublib-core` | 3.1 | Central 可达；API 同经典版 | **EPUB 元数据 + cover** |
| **epublib 现代 fork** | `io.documentnode:epublib` | 1.1 (2024) | 活跃；API 兼容 | 备选 |
| Apache Tika | `org.apache.tika:tika-core` + `tika-parsers-standard` | 3.2.x | 活跃；但 EPUB 解析内部委托给 epublib | 重量级，不引入 |
| JSoup | `org.jsoup:jsoup` | 1.21.1 | 活跃；通用 XML/HTML 解析器 | 自写 OPF 解析时可用 |
| PDFBox | `org.apache.pdfbox:pdfbox` | 3.0.x | 活跃 | **PDF 元数据 + 渲染** |
| EPUBCheck | `org.w3c:epubcheck` | 5.2.1 | 验证器，不抽元数据 | Day-one 不引入 |

### 2.2 选定方案

**EPUB**：`com.positiondev.epublib:epublib-core:3.1`
- 解析 OPF/NCX/cover 一次到位
- 零依赖（纯 Java）
- 经典 API 稳定（2017 至今够用）

**PDF**：`org.apache.pdfbox:pdfbox:3.0.x`
- 读 InfoDict：`PDDocument.getDocumentInformation()`
- 读 XMP：`PDDocumentCatalog.loadXmpMetadata()`
- 提取元数据优先 XMP，回退 InfoDict
- **Day-one 不做 PDF 首页渲染**（耗时 +50-200ms/文件；留给后续里程碑）

### 2.3 epublib API 关键方法

```java
EpubReader reader = new EpubReader();
Book book = reader.readEpub(file);          // 永不抛异常？包内捕获 IOException

Metadata meta = book.getMetadata();
List<Author> authors  = meta.getAuthors();          // List<Author>，不是 String
List<Author> contribs = meta.getContributors();     // 同样 List<Author>
List<String> titles   = meta.getTitles();
List<String> languages = meta.getLanguages();
List<String> publishers = meta.getPublishers();
List<String> descriptions = meta.getDescriptions();
List<String> rights    = meta.getRights();
List<String> identifiers = meta.getIdentifiers();
String date = meta.getDates().isEmpty() ? null : meta.getDates().get(0);  // dc:date

Resource coverRes = book.getCoverImage();    // 可能为 null
byte[] coverBytes = coverRes == null ? null : coverRes.getData();
```

**多作者 → 字符串拼接**（本项目 Q18 决策）：
```java
String authors = meta.getAuthors().stream()
    .map(BookParser::formatAuthor)
    .filter(Objects::nonNull)
    .collect(Collectors.joining(" & "));
```

`formatAuthor`：`firstname + " " + lastname`，trim 后空字段跳过；如 `Author("Karl", "Marx")` → `"Karl Marx"`；`Author("", "Engels")` → `"Engels"`。

---

## 3. KEPUB 简述

- **结构上是 EPUB**，同样的 `content.opf`，同样的 Dublin Core 元数据
- 文件名后缀 `.kepub.epub` 让 Kobo firmware 走 Kobo 渲染引擎
- Kobo 特有标记在 XHTML 章节内容里（`<span class="koboSpan">`），**不是 OPF 元数据**
- 元数据提取上**完全按 EPUB 处理**；仅扩展名 `.kepub.epub` 在路由时识别
- **KEPUB 在 Book.format 字段 = "EPUB"**（按 MIME 分类），extension = `"kepub.epub"`

---

## 4. 关键决策（本项目应用）

| 决策点 | 选定值 | 理由 |
|---|---|---|
| EPUB parser | `com.positiondev.epublib:epublib-core:3.1` | Central 可达；零依赖；一行取 cover |
| PDF parser | `org.apache.pdfbox:pdfbox:3.0.x` | 行业标准；InfoDict + XMP |
| 多作者分隔符 | ` & `（Calibre 同款） | Calibre 生态约定；XML 输出转义 `&amp;` |
| 多主题（subjects）分隔符 | `; ` | 不冲突（主题名很少有 `&`） |
| 系列 | `calibre:series` 优先，EPUB 3 `belongs-to-collection` fallback | 兼容性 |
| 标识符 | 取 `dc:identifier` 第一个（多数 EPUB 只有一个） | 简化 |
| 出版日期 | 仅取 `dc:date` 年份（Integer） | DB 索引小；UI 一致 |
| KEPUB | 当 EPUB 处理；`format="EPUB"`, `extension="kepub.epub"` | 元数据无差异 |
| PDF cover | **Day-one 不渲染首页**；无 XMP 缩略图时 `embeddedCover=null` | 保持"不复杂化"原则 |

---

## 5. PDF 元数据提取的最小集

PDF XMP 优先，回退 InfoDict；都不存在 → filename fallback（`parseError=true`）：

| 字段 | XMP | InfoDict |
|---|---|---|
| title | `dc:title` | `Title` |
| author(s) | `dc:creator`（可多值） | `Author`（`; ` / `, ` 分隔需 split） |
| subject(s) | `dc:subject` | `Keywords` |
| description | `dc:description` | `Subject` |
| language | `dc:language` | — |
| publisher | `dc:publisher` | — |
| date | `dc:date` | `CreationDate` / `ModDate` |
| identifier | `dc:identifier` | — |
| rights | `dc:rights` | — |

PDF 多作者常见格式：`"Smith, John; Doe, Jane"`（分号分隔），split 后逐个 trim。

---

## 关键来源

- [EPUB 3.3 W3C 规范](https://www.w3.org/TR/epub-33/#sec-package-metadata)
- [EPUB 2.0.1 OPF 规范](http://www.idpf.org/epub/20/spec/OPF_2.0.1_draft.htm)
- [Project Gutenberg #61 (Marx & Engels EPUB)](https://www.gutenberg.org/ebooks/61)
- [Standard Ebooks Manual of Style §9](https://standardebooks.org/manual/latest/9-metadata)
- [positiondev/epublib Metadata.java](https://github.com/positiondev/epublib/blob/master/epublib-core/src/main/java/nl/siegmann/epublib/domain/Metadata.java)
- [positiondev/epublib Author.java](https://github.com/positiondev/epublib/blob/master/epublib-core/src/main/java/nl/siegmann/epublib/domain/Author.java)
- [com.positiondev.epublib:epublib-core:3.1 on Maven Central](https://mvnrepository.com/artifact/com.positiondev.epublib/epublib-core/3.1)
- [calibredb ` & ` author syntax fix](https://lists.gnu.org/archive/html/emacs-elpa-diffs/2023-05/msg01013.html)
- [Ubuntu/calibre bug: treat `;` as author separator](https://bugs.launchpad.net/ubuntu/+source/calibre/+bug/2037070)
