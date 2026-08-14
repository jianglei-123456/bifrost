package com.bifrost.core.audio;

/**
 * 未知艺术家虚拟分组常量。
 *
 * <p>无艺术家署名的曲目不建立 Artist 实体（Q9），展示层聚到该虚拟分组（Q16 归入 '#' 组）。</p>
 */
public final class UnknownArtist {

    /** 展示名称 */
    public static final String NAME = "未知艺术家";

    /** 分组字母 */
    public static final String LETTER = PinyinIndex.FALLBACK_LETTER;

    private UnknownArtist() {
    }
}
