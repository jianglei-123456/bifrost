# 标签解析采用 jaudiotagger 3.0.1（文档钦定 2.0.3 不存在于 Maven Central）

《整体技术架构》§2 与《音乐管理功能说明》§2.3 固定 `net.jthink:jaudiotagger:2.0.3`，但该版本及 2.0.4 在 Maven Central 均不存在（404）；中央仓库仅有 2.2.5 / 3.0.0 / 3.0.1。已决定采用 **3.0.1**（最新稳定版，读取 API 与 2.x 兼容：AudioFileIO.read → Tag.getFirst(FieldKey)），并将两处文档版本号修正为 3.0.1（Status: accepted）。
