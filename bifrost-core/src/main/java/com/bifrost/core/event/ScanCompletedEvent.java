package com.bifrost.core.event;

/**
 * 扫描完成事件（进程内，同步发布）。
 *
 * <p>订阅方：Subsonic 服务（缓存失效）、未来适配器。
 * 载荷：库根 ID + 扫描统计（新增/更新/缺失/错误）。</p>
 */
public record ScanCompletedEvent(Long libraryRootId, ScanStats stats) {
}
