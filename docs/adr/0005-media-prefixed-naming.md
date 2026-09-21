# 媒体专属命名：根与扫描端点、类名按媒体类型加前缀

音乐侧的「根 / 扫描」端点与类名一律加 `music-` 前缀，与图书侧既有的 `book-*` 对称：`/api/music-roots`（含 `/scan/all`、`/scan/status`）、`MusicRootController` / `MusicRootRequest` / `MusicRootDto`、`MusicScanService`、`MusicScanStatusView`、`MusicScanCompletedEvent`、`MusicScanStateResetRunner`。**共享持久层刻意继续用普适名**：`LibraryRoot` 实体、`library_root` 表、`LibraryRootRepository`、`Track/Book.libraryRootId`、`ScanStats`、`ScanStatus`、`MediaType`。v1 的 `/api/library-roots`、`/api/scan`、`/api/scan/status` **直接下线，不保留兼容别名**（Status: accepted）。

**背景**：v1 只有音乐，音乐侧顺手占用了普适名。v2 图书落地时按 ADR-0004 用 `book-*` 前缀物理隔开，于是同一张 `library_root` 表出现了两套命名口径：图书侧媒体专属、音乐侧普适。后果不只是"名字不好看"——`/api/library-roots` 事实上是个**混合控制器**：无参列表返回两种 mediaType 的根、`DELETE` 同时隐藏曲目与图书、`POST /{id}/scan` 按 mediaType 分发；而 `/api/scan` 号称"仅扫音乐"，实现却遍历全部根。

**理由**：
1. **命名要能自解释**：`/api/music-roots` 与 `/api/book-roots` 并列，边界一眼可见；`/api/library-roots` 与 `/api/book-roots` 并列，读者必须先搞清"library 是否包含 book"。
2. **媒体边界必须能被契约测试钉住**：端点按类型拆分后，"音乐端点绝不碰图书目录"成了可在 hurl 里断言的事实，而不是靠注释约定。
3. **共享层保持普适名是刻意的**：`LibraryRoot`/`library_root` 是 ADR-0004 明确的唯一共享点，"普适"在这里是正确描述而非历史包袱；给它加媒体前缀反而会撒谎。
4. **改动成本可控**：`/api/**` 的唯一消费者是本项目的 Vue Dashboard（兄弟目录 `../bifrost-dashboard`），两仓库可同批次上线。

**取舍**：破坏性 API 变更（旧路径 404，两仓库必须同步发布）。换来的是命名对称，以及**顺带收口媒体边界泄漏**——改动里共 11 处 mediaType 过滤/校验：音乐根列表与扫描状态只取 MUSIC（`MusicRootController`）、创建强制 MUSIC 且 PATCH 不接受改类型、删除只级联曲目、`MusicScanService.scanAll/scanRoot` 只处理或拒绝 MUSIC、`MusicScanStateResetRunner` 只重置 MUSIC，以及 Subsonic 的 `musicFolder`、用户 folder 列表、`lastModified`（含 `?musicFolderId=` 显式分支）与目录解析（`getMusicDirectory` 不再把图书目录当音乐目录打开）。

**DTO 说明**：音乐侧响应改为策展 DTO `MusicRootDto`（8 字段），不再直接序列化共享实体，因此 `createdAt`/`updatedAt` 不再出现在音乐根响应里（管理端未使用）。

**明确不做**：
- 不动共享实体、表、列与字段名——**零数据库迁移**。本项目 schema 演进是 `spring.jpa.hibernate.ddl-auto=update`，它从不重命名表/列；Java 侧单方面改名只会让 Hibernate 建新表、旧数据从应用视角消失。
- 不动 Subsonic / OPDS 的协议线名（`getMusicFolders`、`startScan`、`/opds/v1.2/...`），它们是外部契约。
- 不重命名音乐浏览端点（`/api/artists`、`/api/albums`、`/api/tracks`、`/api/playlists`、`/api/search`）与 `LibraryQueryService`：它们本身就是音乐概念，不存在与图书侧的歧义。

**遗留**：`LibraryRootMediaTypeBackfillRunner` 住在 `com.bifrost.core.book` 包却操作共享表，归属问题留给后续整理。
