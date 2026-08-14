package com.bifrost.domain.enums;

/**
 * 媒体类型（扫描框架预留扩展位）。
 *
 * <p>MUSIC 为本轮交付；VIDEO / BOOK 为后续里程碑预留（《通用功能说明》§6.6）。
 * 本轮不做提前抽象，仅保证枚举存在。</p>
 */
public enum MediaType {
    /** 音频（音乐） */
    MUSIC,
    /** 视频（预留） */
    VIDEO,
    /** 电子书（预留） */
    BOOK
}
