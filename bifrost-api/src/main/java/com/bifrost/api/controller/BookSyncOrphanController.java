package com.bifrost.api.controller;

import com.bifrost.api.dto.booksync.OrphanProgressView;
import com.bifrost.api.dto.booksync.ReadingProgressView;
import com.bifrost.api.dto.booksync.RematchView;
import com.bifrost.api.response.ApiResponse;
import com.bifrost.api.response.PageResult;
import com.bifrost.common.exception.BizException;
import com.bifrost.core.book.sync.OrphanProgressService;
import com.bifrost.core.book.sync.ProgressBookMatcher;
import com.bifrost.core.book.sync.SyncAccountService;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.entity.ReadingProgress;
import com.bifrost.domain.entity.SyncAccount;
import com.bifrost.domain.repo.BookRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 孤儿进度管理（M3-sync T3.4）。
 *
 * <p>规则（R5/C1）：未匹配的新指纹会触发<b>一次</b>自动扫描；扫描后仍未命中即固定为孤儿——
 * 此后只能人工处理：<b>绑定</b>（指定书）、<b>重新匹配</b>（"我后来把书放进库了"）、<b>忽略</b>。</p>
 */
@RestController
@RequestMapping("/api/book-sync/orphans")
@RequiredArgsConstructor
public class BookSyncOrphanController {

    private final SyncAccountService syncAccountService;
    private final OrphanProgressService orphanProgressService;
    private final ProgressBookMatcher matcher;
    private final BookRepository bookRepository;

    /** 孤儿列表（含"建议绑定"；默认隐藏已忽略项）。 */
    @GetMapping
    public ApiResponse<PageResult<OrphanProgressView>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeIgnored) {
        SyncAccount account = account();
        Page<ReadingProgress> rows = orphanProgressService.listOrphans(account.getId(), includeIgnored,
                BookSyncPaging.of(page, size));
        // 整页一次算建议（内部只加载一次图书列表），避免逐行 O(N) 遍历
        Map<String, ProgressBookMatcher.Suggestion> suggestions = matcher.suggestAll(
                rows.getContent().stream().map(ReadingProgress::getDocumentFingerprint).toList());
        List<OrphanProgressView> items = rows.getContent().stream()
                .map(row -> OrphanProgressView.of(row, suggestions.get(row.getDocumentFingerprint())))
                .toList();
        return ApiResponse.ok(new PageResult<>(rows.getTotalElements(), items));
    }

    /** 人工绑定到指定图书。 */
    @PostMapping("/{id}/bind")
    public ApiResponse<ReadingProgressView> bind(@PathVariable Long id,
                                                 @RequestBody(required = false) JsonNode body) {
        Long bookId = body == null || body.get("bookId") == null || body.get("bookId").isNull()
                ? null : body.get("bookId").asLong();
        if (bookId == null) {
            throw BizException.paramError("bookId 不能为空");
        }
        ReadingProgress row = orphanProgressService.bind(id, bookId);
        Book book = bookRepository.findById(bookId).orElse(null);
        return ApiResponse.ok(ReadingProgressView.of(row, book));
    }

    /** 人工重新匹配一次；未命中返回 {@code matched=false}（正常结果，不是错误）。 */
    @PostMapping("/{id}/rematch")
    public ApiResponse<RematchView> rematch(@PathVariable Long id) {
        OrphanProgressService.RematchResult result = orphanProgressService.rematch(id);
        if (!result.matched()) {
            return ApiResponse.ok(new RematchView(false, null));
        }
        Book book = bookRepository.findById(result.progress().getBookId()).orElse(null);
        return ApiResponse.ok(new RematchView(true, ReadingProgressView.of(result.progress(), book)));
    }

    /** 忽略 / 取消忽略（默认 {@code ignored=true}）。 */
    @PostMapping("/{id}/ignore")
    public ApiResponse<Void> ignore(@PathVariable Long id,
                                    @RequestBody(required = false) JsonNode body) {
        boolean ignored = body == null || body.get("ignored") == null || body.get("ignored").isNull()
                || body.get("ignored").asBoolean(true);
        orphanProgressService.setIgnored(id, ignored);
        return ApiResponse.ok();
    }

    private SyncAccount account() {
        return syncAccountService.current()
                .orElseThrow(() -> BizException.notFound("同步账号尚未初始化"));
    }
}
