package com.bifrost.adapter.opds.service;

import com.bifrost.domain.entity.Book;
import com.bifrost.domain.repo.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * OPDS 端点的图书查询服务（M2-book，T2.1）。
 *
 * <p>不暴露 {@code isAvailable=false} 的记录；分页 1-based，count 由 Controller 钳到 10–200。</p>
 */
@Service
@RequiredArgsConstructor
public class OpdsBookQueryService {

    private final BookRepository bookRepository;

    /** 全部图书（按 id 升序，分页）。{@code page} 1-based。 */
    public Page<Book> findAll(int page, int count) {
        return bookRepository.findAll(
                PageRequest.of(page - 1, count, Sort.by(Sort.Direction.ASC, "id")));
    }

    /** 最近添加（按 createdAt 降序，分页）。 */
    public Page<Book> findRecent(int page, int count) {
        return bookRepository.findAll(
                PageRequest.of(page - 1, count, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /** 多字段搜索（title/authors/series/identifier/publisher/subject LIKE 任意命中）。 */
    public Page<Book> search(String q, int page, int count) {
        return bookRepository.searchByKeyword(q,
                PageRequest.of(page - 1, count));
    }

    /** 详情（不做 isAvailable 过滤，由调用方决定）。 */
    public Optional<Book> findById(Long id) {
        return bookRepository.findById(id);
    }
}