# 图书（BOOK）媒体表与扫描逻辑物理隔开

M2-book mini-milestone 设计决定：**图书侧不复用音乐侧任何实体（Track/Album/Artist/AlbumKey/UnknownAlbum/UnknownArtist），扫描器、互斥锁、状态机、事件、端点全部独立**；唯一共享是 `LibraryRoot` 表（加 `mediaType` 列做隔离维度）。这是与项目精神"不做提前抽象"的一次明确对齐（Status: accepted）。

**理由**：
1. 聚合维度不同：音乐是 Track→Album→Artist 三层聚合（同名合并、专辑键归一），图书的"书"是基本单位，作者/出版社/系列是修饰语字段，**不应作为聚合维度**。强行复用会在 Service 层制造 if-MediaType 分支，破坏聚合内聚性。
2. 元数据模型不同：音乐标签来自 jaudiotagger 抽象的统一 `TagResult`，图书元数据按格式分派（EPUB 走 epublib、PDF 走 PDFBox），且字段集（多作者/系列/出版日期/标识符/语言）远比音乐复杂，**复用 Track 实体意味着塞 30+ 列与"播放统计"等音乐专属字段并存的怪物**。
3. 扫描器差异：音乐扫描涉及播放计数刷新、歌单引用级联、封面抽取的多格式兜底；图书扫描仅需 cover 抽取（EPUB/PDF 内嵌不同）+ 文件级 fingerprint 比较，**混用会污染对方的事务边界和批大小调优**。
4. 视频（VIDEO）也是同理——M2 视频若启动，应同样物理隔开。**今天的决策为视频落地预演了路径**。

**取舍**：少量样板代码（Book/BookFile 实体、BookScanService、BookParser 注册表）换取边界清晰、未来可对称扩展。**Project 原则「不做提前抽象」**反过来要求：落地时再做物理隔开，而不是抽出公共 `MediaEntity` 基类。

**唯一共享点**：`LibraryRoot` 实体（新增 `@Enumerated(STRING) mediaType` 列）+ `Fingerprint` 工具（path+size+mtime，与音乐完全相同）+ `BifrostProperties` 配置（新增 `bifrost.book.*` 段）。
