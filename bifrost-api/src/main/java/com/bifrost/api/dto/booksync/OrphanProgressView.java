package com.bifrost.api.dto.booksync;

import com.bifrost.core.book.sync.ProgressBookMatcher;
import com.bifrost.domain.entity.ReadingProgress;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 孤儿进度视图（M3-sync T3.4）。
 *
 * <p>{@code scanAttemptedAt == null} 表示"自动扫描尚未结算"（正常是极短窗口；长挂说明自动扫描被跳过
 * 或失败）。{@code suggestedBookId} 是<b>建议</b>绑定目标（按 basename / OPDS 下发文件名猜的），
 * 只是提示，不会自动绑。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrphanProgressView(
        Long id,
        String documentFingerprint,
        Double percentage,
        String progress,
        String device,
        String deviceId,
        Instant createdAt,
        Instant scanAttemptedAt,
        Boolean ignored,
        Long suggestedBookId,
        String suggestedBookTitle,
        String suggestionReason) {

    public static OrphanProgressView of(ReadingProgress progress, ProgressBookMatcher.Suggestion suggestion) {
        return new OrphanProgressView(
                progress.getId(),
                progress.getDocumentFingerprint(),
                progress.getPercentage(),
                progress.getProgress(),
                progress.getDevice(),
                progress.getDeviceId(),
                progress.getCreatedAt(),
                progress.getScanAttemptedAt(),
                progress.getIgnored(),
                suggestion == null ? null : suggestion.bookId(),
                suggestion == null ? null : suggestion.title(),
                suggestion == null ? null : suggestion.reason());
    }
}
