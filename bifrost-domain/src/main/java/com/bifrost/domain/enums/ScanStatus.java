package com.bifrost.domain.enums;

/**
 * 库根扫描状态。
 *
 * <p>见《音乐管理技术设计》§1.4；进程被杀后残留的 SCANNING 在启动时重置为 IDLE。</p>
 */
public enum ScanStatus {
    /** 空闲 */
    IDLE,
    /** 扫描中 */
    SCANNING
}
