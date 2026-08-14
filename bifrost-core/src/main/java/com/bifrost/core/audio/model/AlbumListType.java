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
    BY_GENRE
}
