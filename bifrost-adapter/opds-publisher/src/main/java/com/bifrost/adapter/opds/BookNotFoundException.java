package com.bifrost.adapter.opds;

/**
 * 图书不存在（OPDS 端点 404 信号）。
 */
public class BookNotFoundException extends RuntimeException {
    private final Long bookId;

    public BookNotFoundException(Long bookId) {
        super("book not found: " + bookId);
        this.bookId = bookId;
    }

    public Long bookId() {
        return bookId;
    }
}