package com.bifrost.api.dto.book;

import com.bifrost.domain.entity.Book;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 图书管理 DTO（M2-book，{@code doc/m2-book/task/03-管理REST.md} T3.1）。
 *
 * <p>字段集见 {@code 01-图书核心.md} Q24；Day-one 排除 {@code starredAt}/{@code rating}（Q24 锁定）。</p>
 *
 * <p>{@code partialMd5}（M3-sync T3.3 附带改动）：文档指纹——KOSync 用它标识"客户端手上那份文件"。
 * 管理端展示它便于排障，契约测试也从这里取真实值（避免在 hurl/测试里硬编码算法输出）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookDto(
        Long id,
        String title,
        String authors,
        String language,
        String publisher,
        Integer pubDate,
        String description,
        String subject,
        String identifier,
        String series,
        Double seriesIndex,
        String rights,
        String format,
        String extension,
        Long fileSize,
        Instant fileLastModified,
        String partialMd5,
        String coverSource,
        String coverUrl,
        Boolean isAvailable,
        Long libraryRootId,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 封面 URL 规则：当且仅当 {@code coverSource != null} 时挂载。
     *
     * <p>抽成静态方法是为了让 book-sync 的 DTO 复用同一条规则，避免两处漂移。</p>
     */
    public static String coverUrl(Book b) {
        return b.getCoverSource() != null ? "/opds/v1.2/catalog/" + b.getId() + "/cover" : null;
    }

    /** 实体 → DTO。 */
    public static BookDto of(Book b) {
        return new BookDto(
                b.getId(), b.getTitle(), b.getAuthors(), b.getLanguage(), b.getPublisher(),
                b.getPubDate(), b.getDescription(), b.getSubject(), b.getIdentifier(),
                b.getSeries(), b.getSeriesIndex(), b.getRights(), b.getFormat(), b.getExtension(),
                b.getFileSize(), b.getFileLastModified() == null ? null
                        : Instant.ofEpochMilli(b.getFileLastModified()),
                b.getPartialMd5(),
                b.getCoverSource(), coverUrl(b), b.getIsAvailable(), b.getLibraryRootId(),
                b.getCreatedAt(), b.getUpdatedAt());
    }
}
