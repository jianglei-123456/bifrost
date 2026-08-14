package com.bifrost.core.audio.model;

/**
 * 专辑列表类型（getAlbumList / getAlbumList2 的 type 参数，Q17 全实现）。
 *
 * <p>见《音乐管理功能说明》§8.2；byYear 需 fromYear/toYear、byGenre 需 genre（缺参 → 协议错误 10）。</p>
 */
public enum AlbumListType {
    /** 随机 */
    RANDOM,
    /** 最新（按年份） */
    NEWEST,
    /** 评分最高 */
    HIGHEST,
    /** 播放最多 */
    FREQUENT,
    /** 最近播放 */
    RECENT,
    /** 按名称字母 */
    ALPHABETICAL_BY_NAME,
    /** 按艺术家字母 */
    ALPHABETICAL_BY_ARTIST,
    /** 收藏 */
    STARRED,
    /** 按年份区间 */
    BY_YEAR,
    /** 按流派 */
    BY_GENRE;

    /**
     * 按 Subsonic 协议 type 参数解析（camelCase，如 alphabeticalByName）。
     *
     * @return 枚举值；无法识别返回 null
     */
    public static AlbumListType fromProtocol(String type) {
        if (type == null) {
            return null;
        }
        String normalized = switch (type.trim().toLowerCase()) {
            case "random" -> "RANDOM";
            case "newest" -> "NEWEST";
            case "highest" -> "HIGHEST";
            case "frequent" -> "FREQUENT";
            case "recent" -> "RECENT";
            case "alphabeticalbyname" -> "ALPHABETICAL_BY_NAME";
            case "alphabeticalbyartist" -> "ALPHABETICAL_BY_ARTIST";
            case "starred" -> "STARRED";
            case "byyear" -> "BY_YEAR";
            case "bygenre" -> "BY_GENRE";
            default -> null;
        };
        return normalized == null ? null : valueOf(normalized);
    }
}
