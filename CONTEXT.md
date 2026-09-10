# Bifrost

Bifrost 是家庭媒体库管理平台：统一管理音频（音乐）/视频/电子书三类媒体，并自建 Subsonic 兼容协议服务供客户端直连。

## 媒体与索引

**媒体类型（MediaType）**:
媒体资源的分类：MUSIC（v1 交付）、BOOK（v2 mini-milestone 交付）、VIDEO（预留）。
_Avoid_: 媒体格式

**库根（LibraryRoot）**:
共享持久化概念：`library_root` 表的一行 = 一个挂载进媒体库的顶层目录，对应 Subsonic 的 musicFolder / OPDS 的 book 集合；可启用/禁用，禁用不参与扫描且其媒体对客户端隐藏；带 `mediaType` 列隔离 MUSIC/BOOK 两种类型的根（v1 历史数据默认 MUSIC，v2 启动时一次性 UPDATE 回填）。管理面按媒体类型称呼为**音乐目录** / **图书目录**；`LibraryRoot` 实体与 `library_root` 表**刻意保留普适名**——它是两种媒体唯一共享的持久化类型（ADR-0004、ADR-0005）。
_Avoid_: 音乐库根、图书库根、音乐文件夹、路径配置

**音乐目录（MusicRoot）**:
管理面对「MUSIC 类型库根」的称呼：`/api/music-roots` 管理的一个挂载目录（名称 + 绝对路径 + 启停），其下的音频文件由 `MusicScanService` 扫描为曲目。管理页面叫「音乐库」。
_Avoid_: 音乐库根、音乐文件夹

**图书目录（BookRoot）**:
管理面对「BOOK 类型库根」的称呼：`/api/book-roots` 管理的一个挂载目录，其下的 EPUB/PDF 由 `BookScanService` 扫描为图书。管理页面叫「图书库」。
_Avoid_: 图书库根、图书文件夹

**图书（Book）**:
一个电子文件及其解析出的元数据记录；归属某个图书目录（BOOK 类型的库根）；每书一文件（`book.filePath` unique），不复用音乐侧 Track/Album/Artist 三层聚合（见 ADR-0004）。
_Avoid_: 图书文件（图书指库内记录，文件指磁盘对象）

**Book 字段集（v2 Day-one）**: title / authors（多作者用 " & " 拼接，Calibre 同款）/ language / publisher / pubDate（仅年份）/ description / subject（"; " 拼接）/ identifier / series / seriesIndex / rights / format（EPUB|PDF）/ extension（epub|pdf|kepub.epub）/ filePath / fileSize / fileLastModified / fingerprint（path+size+mtime）/ coverSource（EMBEDDED|UPLOADED|null）/ isAvailable / libraryRootId / starredAt（v2 字段保留，REST 暂不暴露写入）/ rating（同上）。**没有** Author 实体、Series 实体、BookFile 子表。
_Avoid_: 音频曲目、BookFile（不存在）

**曲目（Track）**:
一个媒体文件及其解析出的元数据记录；归属某个音乐目录（MUSIC 类型的库根）与至多一个专辑。
_Avoid_: 歌曲文件（曲目指库内记录，文件指磁盘对象）

**艺术家（Artist）**:
曲目/专辑的创作署名实体，全局唯一（同名合并）；无署名曲目归入"未知艺术家"虚拟分组。
_Avoid_: 歌手、表演者

**专辑（Album）**:
曲目的聚合实体，由专辑键（规范化专辑艺术家 + 规范化标题）确定；跨音乐目录全局合并，数据库不设唯一约束，由扫描逻辑保证不重复。
_Avoid_: 唱片、唱片集

**未知艺术家（Unknown Artist）**:
无艺术家署名的曲目在展示层的虚拟分组，不建立 Artist 实体。
_Avoid_: 空艺术家、佚名

**未知专辑（Unknown Album）**:
无专辑标签的曲目聚到的兜底专辑（标题固定为"未知专辑"），提供稳定的聚合键；与"未知艺术家"对称。
_Avoid_: 空专辑

**专辑键（Album Key）**:
专辑聚合的规范化键：trim + 大小写折叠后的「专辑艺术家 | 专辑标题」；albumArtist 缺省时回退曲目艺术家。
_Avoid_: 专辑 ID

**聚合（Aggregation）**:
扫描时把曲目归并为艺术家/专辑实体的过程；全局聚合指同名实体跨库根合并为同一个。
_Avoid_: 索引（索引指字母分组）

**索引分组（indexLetter）**:
艺术家按展示首字母分组（拉丁 A–Z、中文拼音首字母、其余 #），扫描时计算并持久化于艺术家实体。
_Avoid_: 拼音索引（拼音只是计算手段）

## 扫描与数据生命周期

**扫描（Scan）**:
遍历音乐目录 / 图书目录的文件系统、解析元数据、构建/更新媒体索引的过程；**每个媒体类型独立**——`MusicScanService`（MUSIC）与 `BookScanService`（BOOK）各持独立 ReentrantLock，互不干扰；同一类型内全局同一时刻只允许一个扫描运行。
_Avoid_: 刷新、重建

**图书扫描器（BookScanService）**:
v2 mini-milestone 引入，独立于 `MusicScanService`；路由表 = `BookParserRegistry`（按扩展名 `epub|kepub.epub|pdf` 派发 `EpubBookParser` / `PdfBookParser`）；批大小 200（`TransactionTemplate`）；增量按 fingerprint 跳过；缺失文件 `isAvailable=false`（保留记录，文件丢失）；`force=true` 时缺失的旧 Book → DELETE（**不级联引用，Day-one 一书一文件无引用关系**）；完成后发 `BookScanCompletedEvent`（预留 opds-publisher 缓存失效钩子，Day-one 无消费者）。

**指纹（Fingerprint）**:
文件变更检测标记 = 路径 + 文件大小 + 最后修改时间；指纹一致则跳过解析。音乐与图书共用同一 `Fingerprint.of(path, size, mtime)`。
_Avoid_: 校验和、内容哈希

**缺失文件（Missing Track / Missing Book）**:
扫描发现库内记录对应的文件已消失，标记 isAvailable=false：对客户端隐藏，但保留记录（音乐侧保留歌单引用；图书侧无引用关系，保留 DB 行供元数据查询）。
_Avoid_: 删除（删除是管理端的显式清理动作）

**封面（Cover）**:
- 音乐（v1）：专辑的展示图片；来源优先级：内嵌图 > 目录图（cover.jpg/folder.jpg）；布局 `data/covers/cover-source/al-<id>.jpg` + `cover-cache/al-<id>-<N>.jpg`。
- 图书（v2）：从 EPUB（epublib `book.getCoverImage()`）或 PDF（XMP 缩略图，**Day-one 不渲染首页**）抽取；管理端可 POST 替换（`coverSource=UPLOADED`，扫描时不被覆盖）；布局 `data/covers/cover-source/book-<id>.jpg`（与音乐同目录不同前缀）；缩略图 `cover-cache/book-<id>-<N>.jpg`（Day-one 仅 200 一档）。
_Avoid_: 专辑图

**收藏（Starred）**:
- 音乐（v1）：用户对曲目/专辑/艺术家三类的收藏标记（三态收藏，Subsonic 协议要求）。
- 图书（v2）：`book.starredAt` / `book.rating` 字段**保留**（Day-one REST 暂不暴露写入），为未来对接 KOSync 用户态/Readest 收藏同步预留。
_Avoid_: 喜欢、加星

## 协议与对外

**Subsonic API**:
音乐流媒体服务器生态的 REST 协议（基线 1.16.1）；Bifrost 作为服务端实现之。

**OpenSubsonic**:
社区对 Subsonic API 的开放演进规范（澄清/扩展/新增错误码 41–44）。

**Syrinx**:
Bifrost 的音乐网关模块，内含 subsonic-api 子模块（/rest/**）。
_Avoid_: sonic（已废弃的外部推送方向）

**OPDS 1.2（v2）**:
Open Publication Distribution System，Atom 1.0 细化（RFC 4287）；Bifrost 作为服务端实现于 `/opds/v1.2/...`，供 KOReader（Android/iOS/墨水屏）和 Readest（PC/Web）直连浏览/搜索/下载。**Day-one 仅 OPDS 1.x Atom；OPDS 2.0 JSON 不实现**。路径固定 `/opds/v1.2/catalog/...`（OPDS 1.2 标准路径）；端点集 = `{catalog, catalog/all, catalog/recent, search.xml, search, catalog/{bookId}/file, catalog/{bookId}/cover}`（共 7 个）；HTTP Basic 可选（`bifrost.opds.require-auth` 配置项，默认 false = 匿名）；**不支持 Digest**（KOReader 限制）。

**OPDS 端点 ID 形态**:
OPDS feed/entry id 用 `urn:bifrost:...` 形式（`urn:bifrost:opds:catalog` / `urn:bifrost:opds:all:page:N` / `urn:bifrost:book:<numericId>`）；**不沿用** Subsonic 的 `ar-`/`al-`/`tr-`/`pl-` 前缀。

**Opedia Publisher（opds-publisher 模块）**:
v2 mini-milestone 在 `bifrost-adapter/opds-publisher`（v1 时为占位）实装；Controller `OpdsController`（`@RequestMapping("/opds/v1.2")`）走 Spring MVC，XML 模板用 string template 手拼（**不引入** ROME 等 Atom 库），用户输入字段统一 `StringEscapeUtils.escapeXml11`。

**KOSync（M3-sync 实装）**:
KOReader 阅读进度同步协议（`/users/create`、`/users/auth`、`PUT /syncs/progress`、`GET /syncs/progress/:document`、`/healthcheck` + `x-auth-user` / `x-auth-key` 头 + `Accept: application/vnd.koreader.v1+json`）；**本里程碑实装**，模块 `bifrost-adapter/kosync-server`，端点挂根路径。协议只认**文档指纹**、**不认"书"**——Bifrost 用指纹→书映射把它接回图书管理。跨端新旧判断在**客户端**（比对响应 `timestamp`，缺字段时退化为百分比比较），服务端**无条件后写覆盖**：**不返回 202**（调研档 `doc/调研/KOReader图书管理后台调研.md` 里"202 = 旧进度拒绝覆盖"是官方 2016 年已删除的旧行为，客户端收到 202 会判失败并把推送丢回离线队列）。同步内容只有"最后阅读位置"，**不含书签 / 高亮 / 笔记 / 阅读时长**。
_Avoid_: 图书同步（会被读成同步图书文件，那是 OPDS / WebDAV 的事）、进度同步（裸词——"阅读进度"才是模型名词）

**ID 前缀（Subsonic 端）**:
ar-（艺术家）/ al-（专辑）/ tr-（曲目）/ pl-（歌单）+ 数字主键。

**管理 REST（/api/**）**:
管理契约端点集，供另一项目的 Vue Dashboard 消费；**根与扫描端点按媒体类型独立路径**（ADR-0005）：音乐侧 `/api/music-roots`（含 `/scan/all`、`/scan/status`），图书侧 `/api/book-roots`（含 `/scan/all`、`/scan/status`，详见 `doc/m2-book/task/03-管理REST.md`）；音乐浏览端点（`/api/artists` / `/api/albums` / `/api/tracks` / `/api/playlists` / `/api/search`）与图书浏览端点（`/api/books`）各自独立、互不混用。两侧共享的库根行由 `mediaType` 列隔离。

**管理端工程（Vue Dashboard）**:
管理端（登录页/登录态/过期时间等前端逻辑）位于本仓库的**兄弟目录** `../bifrost-dashboard`（相对本仓库根目录，勿记绝对路径）；后端仅提供 `/api/**` 契约，登录过期等需求改动落在该工程（见其 `docs/adr/0002-auth-model.md`）。

## 阅读进度同步

**阅读进度（Reading Progress）**:
某个**同步账号**在某本书上的最后阅读位置：百分比 + 位置字符串（重排版书的位置串是阅读器私有形态，跨阅读器不可移植；只有百分比可移植）+ 设备标识 + 服务端记录时间。**只有"最后位置"**，不含书签 / 高亮 / 笔记 / 阅读时长。
_Avoid_: 书籍进度、图书同步、同步记录（记录是存储形态，不是模型名词）

**文档指纹（Document Fingerprint）**:
客户端为"我手上这份文件"算出的 32 位十六进制标识，KOSync 用它代替书目身份（默认对文件内容非均匀采样取 MD5，可切换为对文件名取 MD5）。与**指纹**（`path + size + mtime`，扫描侧的变更检测标记）是**两套完全不同的东西**，不可互相替代。
_Avoid_: 指纹（那是扫描侧概念）、哈希、MD5、checksum

**同步账号（Sync Account）**:
阅读进度同步的凭据主体：用户名 + 口令；口令在客户端侧先做 MD5 再当凭据传输，**与管理员账号相互独立**（管理员口令不交给阅读设备）。阅读进度按同步账号隔离。
_Avoid_: KOSync 用户（协议层叫法）、设备（设备不是凭据主体，多台设备共用一个同步账号）

**孤儿进度（Orphan Progress）**:
收到了阅读进度、但其**文档指纹**匹配不到任何图书的记录（侧载文件、重打包、未入库文件等）。首次遇到未匹配的指纹会**触发一次图书扫描**（给"书还没入库"留一次机会）；扫描后仍未匹配即**固定为孤儿**——不再自动重试匹配，**保留不丢**，管理端可见，只能人工绑定到某本书或忽略。
_Avoid_: 未知进度、无效进度（孤儿是正常状态，不是错误）

**设备（Sync Device）**:
携带**同步账号**凭据上报阅读进度的阅读器实例，由客户端自己生成的 `device_id` 标识（同名不合并）；设备名是客户端可改的自由文本。协议层没有设备注册，只有随每次上报捎带的标识——管理端的设备列表是"见到过并记录下来的设备"。
_Avoid_: 客户端（太泛）、终端、阅读器（设备指实例，不是机型）

## 账号

**管理员（admin）**:
v1 唯一账号（User 表预留多用户），口令以 AES-GCM 可逆加密存储，以便 Subsonic 令牌校验还原明文。
_Avoid_: 用户体系（v1 无多用户）
