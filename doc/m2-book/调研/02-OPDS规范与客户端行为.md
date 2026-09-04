# 02 OPDS 规范与客户端行为

> 调研日期：2026-09-03。
> 一手来源：OPDS 1.2 规范（specs.opds.io）、KOReader 源码（koreader/koreader）、Readest 源码（readest/readest）、Calibre 源码（kovidgoyal/calibre）。
> 调研 subagent 报告：`0ebc7cb1`（OPDS/EPUB 协议层面）+ `bc026b27`（客户端行为 + 封面端点）。
> 本文是上述两份 subagent 报告的**精炼版 + 项目化标注**，非原文搬运。

---

## 1. OPDS 1.2 规范要点

来源：<https://specs.opds.io/opds-1.2>（"OPDS Catalog 1.2"，2017 年发布）

### 1.1 文档模型

- OPDS 是 Atom 1.0（RFC 4287）的细化。根 `<feed xmlns="http://www.w3.org/2005/Atom">`。
- 规范例子同时声明：`xmlns:opds="http://opds-spec.org/2010/catalog"` 和 `xmlns:dc="http://purl.org/dc/elements/1.1/"`。
- 三种 feed：
  - **Catalog Root**（§2.1）：单入口 acquisition feed 或导航树根。
  - **Navigation Feeds**（§2.2）：条目用 `link rel="subsection"` 指向子 feed，子 feed 标 `type="application/atom+xml;profile=opds-catalog;kind=navigation"`。
  - **Acquisition Feeds**（§2.3）：条目 = 图书，含 `link rel="http://opds-spec.org/acquisition"`，feed 标 `type="application/atom+xml;profile=opds-catalog;kind=acquisition"`。
  - **Partial**（§2.4，分页）+ **Complete**（§2.5，标 `fh:complete` 除非分页）。

### 1.2 图书条目（Entry）的最小结构

```
<entry>
  <id>...</id>                          <!-- Atom 强制，stable id -->
  <title>...</title>                    <!-- Atom 强制 -->
  <updated>...</updated>                <!-- ISO-8601，Atom 强制 -->
  <author><name>...</name></author>     <!-- 可重复；多作者各一个 <author> -->
  <dc:identifier>urn:isbn:...</dc:identifier>
  <link rel="http://opds-spec.org/acquisition"
        type="application/epub+zip"
        href="..." />
  <link rel="http://opds-spec.org/image"
        type="image/jpeg"
        href="..." />
  <summary>...</summary>                <!-- 可选 -->
</entry>
```

### 1.3 关键 rel 值

| rel | 用途 | 客户端识别 |
|---|---|---|
| `self` / `start` | feed 自身 / 目录根 | KOReader + Readest |
| `next` / `previous` | 分页 | KOReader + Readest |
| `subsection` | 导航条目 | KOReader + Readest |
| `http://opds-spec.org/acquisition` | 自由下载（opds 1.x）/ 通用下载（opds 2.0） | **KOReader 仅 `acquisition` 和 `acquisition/open-access` 触发下载**；`acquisition/borrow` 走借阅流；`acquisition/buy|subscribe|sample` 不直接下载 |
| `http://opds-spec.org/image` / `/image/thumbnail` | 封面 / 缩略图 | KOReader + Readest |
| `http://opds-spec.org/sort/new` | "新书"feed 钩子 | **Readest "Auto-download new items" 专门看这个 rel** |
| `search` | OpenSearch 描述文件 | KOReader + Readest |

### 1.4 图片 link 的硬性要求

- href 形式：**无限制**（绝对 / 根相对 / 同 server 动态端点 / `data:` URL 都允许）
- `type` 必须为 `image/gif` / `image/jpeg` / `image/png`（§5.2.2）
- 规范自己的例子用根相对 href（如 `/covers/4561.lrg.png`），所以"动态端点"完全符合规范
- href 语义回退到 Atom/RFC 4287：IRI 引用，相对/绝对都允许，按 feed URL 解析

### 1.5 OpenSearch 1.1（搜索协议）

- OpenSearch 描述文档（OSDD）MIME：`application/opensearchdescription+xml`
- 在任意 OPDS feed 里用 `<link rel="search" type="application/opensearchdescription+xml" href="search.xml"/>` 声明
- OSDD 内部 `Url` 元素 `type` 必须是 OPDS acquisition feed MIME：

```xml
<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
  <Url type="application/atom+xml;profile=opds-catalog;kind=acquisition"
       template="/opds/v1.2/search?q={searchTerms}&amp;page={page?}&amp;count={count?}"/>
</OpenSearchDescription>
```

- 客户端替换 `{searchTerms}`（必选）/ `{page?}` / `{count?}` / `{atom:author}` / `{atom:title}` / `{atom:contributor}`
- KOReader 实际支持的占位符范围（源码 `opdsbrowser.lua`）：`{searchTerms}` + 可选的 `{count?}` / `{page?}`

---

## 2. KOReader 客户端行为（源码级）

来源：`koreader/koreader` `plugins/opds.koplugin/opdsbrowser.lua` + `opdspse.lua`（commit `e33266915cf83c46f644f49cb81cd765927debe3`）

### 2.1 协议支持

- HTTP Accept 头：`application/opds+json, application/atom+xml;profile=opds-catalog, */*`
- `parseFeed` 先尝试 JSON（OPDS 2.0），回退 XML（OPDS 1.x）
- **Day-one 1.x Atom 完全够用，2.0 是加分项**

### 2.2 哪些 rel 触发下载

`download_rel` 表（opdsbrowser.lua）：
- `http://opds-spec.org/acquisition`
- `http://opds-spec.org/acquisition/open-access`
- OPDS 2.0 风格的 `download` / `publication`

`borrow` / `buy` / `subscribe` / `sample` **不触发直接下载**（borrow 走借阅流）。

### 2.3 认证

- **仅 HTTP Basic**（不支持 Digest，wiki 明确警告 + issue #3953）
- 自签证书 KOReader 接受

### 2.4 图片 / 封面

- `image_rel` 表：`http://opds-spec.org/image` + `/image/thumbnail` + 几个 legacy rel
- href 解析：相对/绝对一视同仁（`url.absolute(item_url, link.href)`，opdsbrowser.lua:773-775）
- 渲染：用户选 "Book cover" → `OPDSPSE:streamPages(cover_link, ...)`（opdspse.lua:95-148）→ 走 HTTP GET 拿 bytes → `RenderImage:renderImageData`
- 唯一约束：协议必须是 `http`/`https`

### 2.5 OPDS-PSE（按页流式阅读）

- 用于漫画 / 图像书按页流式
- rel: `http://vaemendis.net/opds-pse/stream`
- 服务端需声明 `xmlns:pse="http://vaemendis.net/opds-pse/ns"`
- link 必须带 `pse:count`（页数）和 `href` 含 `{pageNumber}`
- **Day-one 不需要**（用户本轮目标：整本 EPUB 下载可读）

---

## 3. Readest 客户端行为（源码级）

来源：`readest/readest`（commit `049ed2cda634d94c5df8797218a63f92799777a4`），OPDS 解析器来自 foliate-js（gitlink `f71a084e609aaaa6c354aab2ee9eff6bf01b587a`）

### 3.1 图片 link 处理

- `REL.COVER`（apps/readest-app/src/types/opds.ts:8-19）：
  - `http://opds-spec.org/image`
  - `http://opds-spec.org/cover`
  - `x-stanza-cover-image`
- 解析器（foliate `opds.js`）把这些 rel 收集到 `publication.images`
- href **按字面取**（`link.getAttribute('href')`），不做静态/动态假设
- **统一通过 `resolveURL()` 解析相对 URL**（`apps/readest-app/src/app/opds/utils/opdsUtils.ts:208-243`）
- 同一 host 的 `http://` 在 HTTPS feed 下会被升级到 `https://`（issue #5300）

### 3.2 客户端测试覆盖

- `src/__tests__/services/opds-cover.test.ts:52-87` 测试**相对路径**封面 href（如 `/cwa/opds/cover/572`）
- 这是"动态端点可被消费"的测试级证据

### 3.3 桌面 / Web 区别

- **Tauri 桌面**：原生 HTTP，**无 CORS**，**接受自签证书**（`opdsReq.ts:259-264, 380-386`，`cover.ts:110`）
- **Hosted Web**：通过同源代理 `/api/opds/proxy`，**生产环境有 SSRF 保护**——私有/LAN host（如 `bifrost.local`）被阻止（`apps/readest-app/src/app/api/opds/proxy/route.ts:12-13, 119-125`）
  - 开发模式（`NODE_ENV === 'development'`）放行
  - 桌面端始终放行
  - **本项目 Day-one 验证推荐用桌面端或 dev 模式**

---

## 4. Calibre content server（行业对照）

来源：`kovidgoyal/calibre` `src/calibre/srv/opds.py`（commit `335bd69984529a80db528a493754003e4f210acc`）

每本书发 4 个 cover link（opds.py:297-300）：

```python
rel="http://opds-spec.org/cover"        # legacy
rel="http://opds-spec.org/thumbnail"    # legacy
rel="http://opds-spec.org/image"        # spec
rel="http://opds-spec.org/image/thumbnail"  # spec
type='image/jpeg'
```

href 全部指向 **`/get?book_id=...&library_id=...&what=cover|thumb`**——**动态端点**，与本设计模式**完全一致**。

→ **行业背书**：KOReader/Readest 已经在 Calibre content server 上验证过"动态封面端点"可行。

---

## 5. 客户端 MimeType 识别

| 格式 | MIME |
|---|---|
| EPUB / KEPUB | `application/epub+zip`（KEPUB 同 MIME，KOReader 按 `.kepub.epub` 扩展名识别） |
| PDF | `application/pdf` |
| MOBI / AZW3 | `application/x-mobipocket-ebook`（de facto） |
| FB2 | `application/x-fb2` 或 `application/fb2+zip` |
| DJVU | `application/djvu` 或 `application/octet-stream` |
| CBZ / CBR | `application/vnd.comicbook+zip` / `application/vnd.comicbook-rar` |

---

## 6. 关键决策（本项目应用）

| 决策点 | 选定值 | 理由 |
|---|---|---|
| OPDS 路径 | `/opds/v1.2/catalog/...` | OPDS 1.2 标准路径 |
| Feed 类型 | 严格按 `application/atom+xml;profile=opds-catalog;kind={navigation\|acquisition}` | 规范明确要求 |
| 多作者输出 | 每个作者一个 `<author><name>` | OPDS 规范；和 epublib `getAuthors()` 返回 `List<Author>` 一致 |
| 封面 link | 动态端点 `/opds/v1.2/catalog/{bookId}/cover` | 已验证 Readest/KOReader/Calibre 兼容 |
| 搜索 | OSDD + `/opds/v1.2/search` | 规范路径 |
| 最近添加 | `/opds/v1.2/catalog/recent` + `rel="http://opds-spec.org/sort/new"` | Readest "Auto-download new items" 钩子 |
| 认证 | HTTP Basic（可选开启） | KOReader 唯一支持；规范推荐 |
| OPDS-PSE | Day-one 不实现 | 用户目标是整本下载可读 |

---

## 关键来源

- [OPDS 1.2 spec](https://specs.opds.io/opds-1.2)
- [koreader/koreader opdsbrowser.lua](https://github.com/koreader/koreader/blob/master/plugins/opds.koplugin/opdsbrowser.lua)
- [koreader/koreader opdspse.lua](https://github.com/koreader/koreader/blob/master/plugins/opds.koplugin/opdspse.lua)
- [readest/readest main](https://github.com/readest/readest) (commit `049ed2c`)
- [kovidgoyal/calibre opds.py](https://github.com/kovidgoyal/calibre/blob/master/src/calibre/srv/opds.py)
- [OPDS-PSE spec](https://vaemendis.net/opds-pse/)
- [OPDS-support wiki](https://github.com/koreader/koreader/wiki/OPDS-support)
- [Readest issue #5300 (http→https upgrade)](https://github.com/readest/readest/issues/5300)
- [Readest issue #5270 (cover)](https://github.com/readest/readest/issues/5270)
