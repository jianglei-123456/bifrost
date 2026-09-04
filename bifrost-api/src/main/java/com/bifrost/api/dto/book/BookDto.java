package com.bifrost.api.dto.book;

import com.bifrost.domain.entity.Book;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 图书管理 DTO（M2-book，{@code doc/m2-book/task/03-管理REST.md} T3.1）。
 *
 * <p>字段集见 {@code 01-图书核心.md} Q24；Day-one 排除 {@code starredAt}/{@code rating}（Q24 锁定）。</p>
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
        String coverSource,
        String coverUrl,
        Boolean isAvailable,
        Long libraryRootId,
        Instant createdAt,
        Instant updatedAt) {

    /** 实体 → DTO；{@code coverUrl} 当且仅当 {@code coverSource != null} 时挂载。 */
    public static BookDto of(Book b) {
        String coverUrl = b.getCoverSource() != null
                ? "/opds/v1.2/catalog/" + b.getId() + "/cover"
                : null;
        return new BookDto(
                b.getId(), b.getTitle(), b.getAuthors(), b.getLanguage(), b.getPublisher(),
                b.getPubDate(), b.getDescription(), b.getSubject(), b.getIdentifier(),
                b.getSeries(), b.getSeriesIndex(), b.getRights(), b.getFormat(), b.getExtension(),
                b.getFileSize(), b.getFileLastModified() == null ? null
                        : Instant.ofEpochMilli(b.getFileLastModified()),
                b.getCoverSource(), coverUrl, b.getIsAvailable(), b.getLibraryRootId(),
                b.getCreatedAt(), b.getUpdatedAt());
    }
}