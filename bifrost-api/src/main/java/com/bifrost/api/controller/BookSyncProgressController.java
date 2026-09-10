package com.bifrost.api.controller;

import com.bifrost.api.dto.booksync.BookSyncStatsView;
import com.bifrost.api.dto.booksync.ReadingProgressView;
import com.bifrost.api.response.ApiResponse;
import com.bifrost.api.response.PageResult;
import com.bifrost.common.exception.BizException;
import com.bifrost.core.book.sync.ReadingProgressService;
import com.bifrost.core.book.sync.SyncAccountService;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.entity.ReadingProgress;
import com.bifrost.domain.entity.SyncAccount;
import com.bifrost.domain.repo.BookRepository;
import com.bifrost.domain.repo.SyncDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 阅读进度管理（M3-sync T3.3/T3.5，Dashboard 进度列表 + 概览）。
 */
@RestController
@RequestMapping("/api/book-sync")
@RequiredArgsConstructor
public class BookSyncProgressController {

    private final SyncAccountService syncAccountService;
    private final ReadingProgressService progressService;
    private final SyncDeviceRepository syncDeviceRepository;
    private final BookRepository bookRepository;

    /** 进度列表（分页 0-based；title/device/libraryRootId 过滤；onlyOrphans 只看孤儿）。 */
    @GetMapping("/progress")
    public ApiResponse<PageResult<ReadingProgressView>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String device,
            @RequestParam(required = false) Long libraryRootId,
            @RequestParam(defaultValue = "false") boolean onlyOrphans) {
        SyncAccount account = account();
        Page<ReadingProgress> rows = progressService.search(account.getId(), title, device, libraryRootId,
                onlyOrphans, BookSyncPaging.of(page, size));
        Map<Long, Book> books = BookSyncPaging.loadBooks(bookRepository, rows.getContent());
        List<ReadingProgressView> items = rows.getContent().stream()
                .map(row -> ReadingProgressView.of(row, books.get(row.getBookId())))
                .toList();
        return ApiResponse.ok(new PageResult<>(rows.getTotalElements(), items));
    }

    /**
     * 删除（重置）单条进度。
     *
     * <p>只删服务端记录：设备本地位置不变，设备下次推送会重新建立记录——管理端文案必须写明这点，
     * 否则用户会以为"重置"能让设备回到开头。</p>
     */
    @DeleteMapping("/progress/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        progressService.delete(id);
        return ApiResponse.ok();
    }

    /** 概览统计（页面头部卡片）。 */
    @GetMapping("/stats")
    public ApiResponse<BookSyncStatsView> stats() {
        SyncAccount account = account();
        return ApiResponse.ok(BookSyncStatsView.of(
                progressService.stats(account.getId()),
                syncDeviceRepository.countBySyncAccountId(account.getId())));
    }

    private SyncAccount account() {
        return syncAccountService.current()
                .orElseThrow(() -> BizException.notFound("同步账号尚未初始化"));
    }
}
