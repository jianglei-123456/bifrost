# Artist 实体增加收藏/评分/播放统计字段

《音乐管理技术设计》§1.1 的 Artist 实体未定义 starred/rating/playCount/lastPlayed 字段，与《音乐管理功能说明》§7「曲目/专辑/艺术家三态收藏」及 Subsonic 协议 star/unstar/getStarred2 的要求矛盾。已决定：给 Artist 增加 starred/rating/playCount/lastPlayed 字段，实现完整三态收藏与艺术家级播放统计，并同步修正技术设计文档（Status: accepted）。
