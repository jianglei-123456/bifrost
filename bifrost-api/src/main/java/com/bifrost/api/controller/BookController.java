package com.bifrost.api.controller;

import com.bifrost.api.dto.book.BookDto;
import com.bifrost.api.response.ApiResponse;
import com.bifrost.api.response.PageResult;
import com.bifrost.common.exception.BizException;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.repo.BookRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图书管理 REST（M2-book，{@code doc/m2-book/task/03-管理REST.md} T3.3）。
 *
 * <p>列表分页 0-based（与 music 一致）；page=1 不报错，自动 0-based 转换。
 * 字段过滤走 {@link Specification} 动态拼。
 * PATCH 用 {@link JsonNode} 处理 + 字段白名单（Q28，Day-one rating 写返回 400）。</p>
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    /** PATCH 可写字段白名单（Q28）；其他字段收到 → 400 */
    private static final Set<String> WRITABLE = Set.of(
            "title", "authors", "language", "publisher", "pubDate",
            "description", "subject", "identifier", "series", "seriesIndex", "rights");

    /** PATCH 完全拒绝字段（Day-one 锁定；rating/starredAt 留给未来 KOSync） */
    private static final Set<String> FORBIDDEN = Set.of(
            "id", "filePath", "fileSize", "fileLastModified", "fingerprint",
            "format", "extension", "libraryRootId", "isAvailable",
            "coverSource", "createdAt", "updatedAt", "starredAt", "rating");

    private final BookRepository bookRepository;
    private final ObjectMapper objectMapper;

    /**
     * 列表（分页 0-based；size 10-200；title/authors/series/libraryRootId/isAvailable 过滤）。
     * 返回 {@code {total, items}} 格式（与 music 列表一致）。
     */
    @GetMapping
    public ApiResponse<PageResult<BookDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) Long libraryRootId,
            @RequestParam(required = false) Boolean isAvailable) {
        if (page < 0) {
            throw BizException.paramError("page 必须 >= 0");
        }
        int s = size;
        if (s < 1) {
            s = 20;
        }
        if (s > 200) {
            s = 200;
        }
        Specification<Book> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (title != null && !title.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%"));
            }
            if (author != null && !author.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("authors")), "%" + author.toLowerCase() + "%"));
            }
            if (series != null && !series.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("series")), "%" + series.toLowerCase() + "%"));
            }
            if (libraryRootId != null) {
                ps.add(cb.equal(root.get("libraryRootId"), libraryRootId));
            }
            if (isAvailable != null) {
                ps.add(cb.equal(root.get("isAvailable"), isAvailable));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<Book> p = bookRepository.findAll(spec,
                PageRequest.of(page, s, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<BookDto> items = p.getContent().stream().map(BookDto::of).toList();
        return ApiResponse.ok(new PageResult<>(p.getTotalElements(), items));
    }

    /** 详情（不存在 → 1001） */
    @GetMapping("/{id}")
    public ApiResponse<BookDto> get(@PathVariable Long id) {
        return ApiResponse.ok(BookDto.of(requireBook(id)));
    }

    /**
     * 编辑元数据（白名单字段；不写回文件）。
     * authors / subject 支持 String 或 String[]（Q28 决策）。
     * Day-one rating 字段在白名单外，提交 → 400。
     */
    @PatchMapping("/{id}")
    public ApiResponse<BookDto> update(@PathVariable Long id, @RequestBody JsonNode body) {
        Book book = requireBook(id);

        // 先扫一遍：是否有 forbidden 字段
        Iterator<Map.Entry<String, JsonNode>> it = body.fields();
        while (it.hasNext()) {
            String key = it.next().getKey();
            if (FORBIDDEN.contains(key)) {
                throw BizException.paramError("字段不可写（Day-one 锁定）: " + key);
            }
            if (!WRITABLE.contains(key)) {
                throw BizException.paramError("未知字段: " + key);
            }
        }

        // 应用白名单
        for (String key : WRITABLE) {
            JsonNode v = body.get(key);
            if (v == null || v.isNull()) {
                continue;
            }
            switch (key) {
                case "title" -> book.setTitle(v.asText());
                case "authors" -> book.setAuthors(joinArrayOrText(v, " & "));
                case "subject" -> book.setSubject(joinArrayOrText(v, "; "));
                case "language" -> book.setLanguage(textOrNull(v));
                case "publisher" -> book.setPublisher(textOrNull(v));
                case "pubDate" -> book.setPubDate(v.asInt());
                case "description" -> book.setDescription(textOrNull(v));
                case "identifier" -> book.setIdentifier(textOrNull(v));
                case "series" -> book.setSeries(textOrNull(v));
                case "seriesIndex" -> book.setSeriesIndex(v.isNull() ? null : v.asDouble());
                case "rights" -> book.setRights(textOrNull(v));
                default -> { /* skip */ }
            }
        }
        return ApiResponse.ok(BookDto.of(bookRepository.save(book)));
    }

    /** 删 DB 行（不删文件，Q13 不级联） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        requireBook(id);
        bookRepository.deleteById(id);
        return ApiResponse.ok();
    }

    /** String 或 String[] → 用 separator 拼接；null/empty → null。 */
    private static String joinArrayOrText(JsonNode v, String separator) {
        if (v.isArray()) {
            List<String> parts = new ArrayList<>();
            v.forEach(n -> {
                if (!n.isNull()) {
                    String t = n.asText().trim();
                    if (!t.isEmpty()) {
                        parts.add(t);
                    }
                }
            });
            return parts.isEmpty() ? null : String.join(separator, parts);
        }
        return textOrNull(v);
    }

    private static String textOrNull(JsonNode v) {
        if (v.isNull()) {
            return null;
        }
        String t = v.asText().trim();
        return t.isEmpty() ? null : t;
    }

    private Book requireBook(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("图书不存在: " + id));
    }
}