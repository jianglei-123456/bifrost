package com.bifrost.core.book.event;

import com.bifrost.core.event.ScanStats;

/**
 * 图书扫描完成事件（进程内同步发布；与 {@code MusicScanCompletedEvent} 独立，ADR-0004）。
 *
 * <p>订阅方预留：opds-publisher 缓存失效（Day-one 无消费者，T1.3 决定先 publish）。
 * 载荷：图书目录 ID（{@code null} = 全局统计）+ 扫描统计。</p>
 */
public record BookScanCompletedEvent(Long libraryRootId, ScanStats stats) {
}