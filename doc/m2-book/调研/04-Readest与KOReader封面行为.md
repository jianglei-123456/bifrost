# 04 Readest 与 KOReader 封面行为（动态端点验证）

> 调研日期：2026-09-04。
> 目的：确认 OPDS `rel="http://opds-spec.org/image"` 的 href 指向**同 server 动态抽取端点**（如 `/opds/v1.2/catalog/{bookId}/cover`）时，Readest 和 KOReader 是否能正确消费。
> 一手来源：Readest 源码（commit `049ed2c`）、KOReader 源码（commit `e3326691`）、Calibre 源码（commit `335bd699`）、OPDS 1.2 规范。
> 调研 subagent 报告：`bc026b27`。

---

## 结论（一句话）

**Bifrost 模式（OPDS image link 指向同 server 动态抽取端点）完全合规，两端都正确处理**——Readest 和 KOReader 都不对 href 形式做"必须是静态文件/绝对 URL"的假设；OPDS 1.2 规范自己的例子就使用根相对 href；Calibre content server 自身就发**完全相同的模式**（`/get?what=cover` 动态端点）。

---

## 1. Readest 行为

来源：`readest/readest`（commit `049ed2cda634d94c5df8797218a63f92799777a4`），OPDS 解析器来自 foliate-js（gitlink `f71a084e609aaaa6c354aab2ee9eff6bf01b587a`）

### 1.1 图片 rel 识别

文件：`apps/readest-app/src/types/opds.ts:8-19`
```typescript
REL.COVER = ['http://opds-spec.org/image', 'http://opds-spec.org/cover', 'x-stanza-cover-image']
```

foliate `opds.js`（f71a084 lines 21-28, 233-234）把这些 rel 收集到 `publication.images`。

### 1.2 href 处理

- `link.getAttribute('href')`（line 177）—— **href 按字面取**
- `resolveURL()`（`apps/readest-app/src/app/opds/utils/opdsUtils.ts:208-243`）：
  - 相对/绝对 URL 统一处理（`new URL(url, relativeTo)`，line 217）
  - 同 host 的 `http://` 在 HTTPS feed 下升级到 `https://`（lines 226-232，issue #5300）
  - 透传 web 代理 URL

### 1.3 渲染

- Catalog 卡片：`PublicationCard.tsx:23-38, 55-63` → `CachedImage src={resolveURL(imageLink.href, baseURL)}`
- 详情页：`PublicationView.tsx:93-97` 同样
- 导入时的封面：`applyOPDSCover` in `apps/readest-app/src/services/opds/cover.ts:75-132` → `getOPDSCoverHref` lines 33-44 返回 href 原样

### 1.4 测试覆盖

`src/__tests__/services/opds-cover.test.ts:52-87` 显式测试**相对路径封面 href**（如 `/cwa/opds/cover/572`）—— 这是"动态端点可被消费"的**测试级证据**。

### 1.5 桌面 / Web 区别

| 环境 | 行为 |
|---|---|
| **Tauri 桌面** | 原生 HTTP，**无 CORS**，**接受自签证书**（`opdsReq.ts:259-264, 380-386`，`cover.ts:110` `skipSslVerification: true`） |
| **Hosted Web** | 通过同源代理 `/api/opds/proxy`（`opdsReq.ts:45-47, 89-112`）；**生产环境有 SSRF 保护**——私有/LAN host 被阻止（`apps/readest-app/src/app/api/opds/proxy/route.ts:12-13, 119-125`）；**开发模式（`NODE_ENV === 'development'`）放行** |

→ **本项目 Day-one 验证推荐用桌面端或 dev 模式**（直连 `http://192.168.x.x:8080/opds/v1.2/catalog` 不踩 SSRF 雷）。

---

## 2. KOReader 行为

来源：`koreader/koreader`（commit `e33266915cf83c46f644f49cb81cd765927debe3`）`plugins/opds.koplugin/`

### 2.1 图片 rel 识别

`opdsbrowser.lua:61-70`（`image_rel` / `thumbnail_rel` 表）：
- `http://opds-spec.org/image`
- `http://opds-spec.org/image/thumbnail`
- 几个 legacy rel

### 2.2 href 处理

- `url.absolute(item_url, link.href)`（opdsbrowser.lua:773-775 `build_href`）
- 相对/绝对一视同仁
- 应用在 OPDS 1.x 路径（lines 820, 861-864）和 OPDS 2.0 路径（lines 698-710）

### 2.3 渲染

- 列表行只显示文本（`text = title .. " - " .. author`，line 750）
- 用户选 "Book cover"（lines 1180-1187）→ `OPDSPSE:streamPages(cover_link, ...)` → `opdspse.lua:95-148`：
  - 走 `http.request` GET 拿 bytes
  - `RenderImage:renderImageData` 渲染
  - `ImageViewer` 显示
- 唯一约束：协议必须是 `http`/`https`（lines 120, 132-136，"Invalid protocol"）

→ KOReader 对**同 server 动态端点**的态度：**完全无障碍**。

---

## 3. OPDS 1.2 规范

来源：<https://specs.opds.io/opds-1.2>

### 3.1 图片 link 硬性要求（§5.2.2）

- href 形式：**无限制**（绝对 / 根相对 / 同 server 动态端点 / `data:` URL 都允许）
- `type` 必须为 `image/gif` / `image/jpeg` / `image/png`（line 789）
- 规范**自己的例子**用根相对 href（如 `href="/covers/4561.lrg.png"`，lines 406-411, 675-680）
- 显式允许非 HTTP `data:` URL 做缩略图（line 795）
- href 语义回退到 Atom/RFC 4287（line 1082）：IRI 引用，相对/绝对都允许

### 3.2 结论

"动态同 server 端点" 是**完全符合规范**的 OPDS 实现。规范自己用根相对 + 显式允许 data: URL = 设计意图就是**最大化客户端对 href 形式的容忍度**。

---

## 4. Calibre content server（行业对照）

来源：`kovidgoyal/calibre`（commit `335bd699`）`src/calibre/srv/opds.py`

每本书发 4 个 cover link（opds.py:297-300）：
```python
rel="http://opds-spec.org/cover"             # legacy
rel="http://opds-spec.org/thumbnail"         # legacy
rel="http://opds-spec.org/image"             # spec
rel="http://opds-spec.org/image/thumbnail"   # spec
type='image/jpeg'
```

href 全部指向 **`/get?book_id=...&library_id=...&what=cover|thumb`**：
- `url_for('/get', book_id=..., library_id=..., what='cover'|'thumb')`（opds.py:282-283）
- 同一 `/get` 端点也处理格式下载（`what=fmt`，line 291）
- 即"cover / format 都动态生成/服务"，**不是静态文件**

→ **行业背书**：KOReader 和 Readest 已经在 Calibre content server 上验证过"动态封面端点"可行。**Calibre 的 OPDS 输出就是本项目的设计参考模板**。

---

## 5. 关键来源

- [readest/readest commit 049ed2c](https://github.com/readest/readest)
- [koreader/koreader commit e3326691](https://github.com/koreader/koreader)
- [kovidgoyal/calibre commit 335bd699](https://github.com/kovidgoyal/calibre)
- [OPDS 1.2 spec](https://specs.opds.io/opds-1.2)
- [Readest issue #5300 (http→https upgrade)](https://github.com/readest/readest/issues/5300)
- [Readest issue #5270 (cover)](https://github.com/readest/readest/issues/5270)
- [foliate-js opds.js](https://github.com/johnfactotum/foliate-js/blob/main/opds.js)
