package com.bifrost.core.audio;

/**
 * 未知专辑兜底常量。
 *
 * <p>无专辑标签的曲目聚到"未知专辑"（与"未知艺术家"对称，Navidrome 同款行为），
 * 避免空标题违反非空约束，并为无署名曲目提供稳定的聚合键。</p>
 */
public final class UnknownAlbum {

    /** 展示名称（也是聚合标题兜底值） */
    public static final String NAME = "未知专辑";

    private UnknownAlbum() {
    }
}
