package com.bifrost.api.dto.booksync;

import com.bifrost.api.dto.book.BookDto;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.entity.ReadingProgress;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 阅读进度视图（M3-sync T3.3）。
 *
 * <p>时间字段是 {@link Instant} → ISO-8601 字符串，与 {@code /api/books} 的既有 DTO 一致；
 * <b>秒级 epoch 只出现在 KOSync 协议端点</b>的 {@code timestamp} 里。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadingProgressView(
        Long id,
        Long bookId,
        String bookTitle,
        String bookAuthors,
        Long libraryRootId,
        String coverUrl,
        String documentFingerprint,
        Double percentage,
        String progress,
        String device,
        String deviceId,
        Instant reportedAt,
        String matchSource,
        Boolean ignored) {

    /** {@code book} 可为 null（孤儿进度）。 */
    public static ReadingProgressView of(ReadingProgress progress, Book book) {
        return new ReadingProgressView(
                progress.getId(),
                progress.getBookId(),
                book == null ? null : book.getTitle(),
                book == null ? null : book.getAuthors(),
                book == null ? null : book.getLibraryRootId(),
                book == null ? null : BookDto.coverUrl(book),
                progress.getDocumentFingerprint(),
                progress.getPercentage(),
                progress.getProgress(),
                progress.getDevice(),
                progress.getDeviceId(),
                progress.getReportedAt(),
                progress.getMatchSource() == null ? null : progress.getMatchSource().name(),
                progress.getIgnored());
    }
}
