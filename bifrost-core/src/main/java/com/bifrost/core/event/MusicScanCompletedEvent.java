package com.bifrost.core.event;

/**
 * 音乐扫描完成事件（进程内，同步发布）。
 *
 * <p>订阅方：Subsonic 服务（封面缩略图缓存失效）、未来适配器。
 * 载荷：音乐目录（{@code LibraryRoot}）ID + 扫描统计（新增/更新/缺失/错误）。
 * 图书侧对应 {@code com.bifrost.core.book.event.BookScanCompletedEvent}（ADR-0004 物理隔开）。</p>
 */
public record MusicScanCompletedEvent(Long libraryRootId, ScanStats stats) {
}
