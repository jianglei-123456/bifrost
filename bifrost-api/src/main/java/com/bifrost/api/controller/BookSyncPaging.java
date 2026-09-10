package com.bifrost.api.controller;

import com.bifrost.common.exception.BizException;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.entity.ReadingProgress;
import com.bifrost.domain.repo.BookRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * book-sync 管理端公共小工具（分页钳制 + 批量取书，M3-sync T3.3/T3.4）。
 *
 * <p>分页规则与 {@code /api/books} 完全一致：{@code page} <b>0-based</b>，{@code size} 默认 20、钳到 1–200。</p>
 */
final class BookSyncPaging {

    private BookSyncPaging() {
    }

    /** 0-based 分页 + 按 updatedAt 倒序（与 /api/books 的约定一致）。 */
    static Pageable of(int page, int size) {
        if (page < 0) {
            throw BizException.paramError("page 必须 >= 0");
        }
        int clamped = Math.min(200, Math.max(1, size));
        return PageRequest.of(page, clamped, Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    /** 批量取进度关联的书（避免逐行查库；孤儿没有书 → 不进 map）。 */
    static Map<Long, Book> loadBooks(BookRepository bookRepository, List<ReadingProgress> rows) {
        return loadBooks(bookRepository, rows.stream().map(ReadingProgress::getBookId).toList());
    }

    static Map<Long, Book> loadBooks(BookRepository bookRepository, Collection<Long> bookIds) {
        Set<Long> ids = bookIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return bookRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));
    }
}
