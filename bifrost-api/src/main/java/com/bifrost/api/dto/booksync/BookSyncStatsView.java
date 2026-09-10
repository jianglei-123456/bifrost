package com.bifrost.api.dto.booksync;

import com.bifrost.core.book.sync.ReadingProgressService;

import java.time.Instant;

/**
 * 阅读进度同步概览（M3-sync T3.5，页面头部卡片用）。
 */
public record BookSyncStatsView(
        long progressCount,
        long matchedCount,
        long orphanCount,
        long deviceCount,
        Instant lastReportedAt) {

    public static BookSyncStatsView of(ReadingProgressService.Stats stats, long deviceCount) {
        return new BookSyncStatsView(stats.progressCount(), stats.matchedCount(), stats.orphanCount(),
                deviceCount, stats.lastReportedAt());
    }
}
