package com.bifrost.api.controller;

import com.bifrost.api.dto.book.BookRootDto;
import com.bifrost.api.response.ApiResponse;
import com.bifrost.common.exception.BizException;
import com.bifrost.common.util.Strings;
import com.bifrost.core.book.BookScanService;
import com.bifrost.core.event.ScanStats;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import com.bifrost.domain.enums.ScanStatus;
import com.bifrost.domain.repo.BookRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 图书目录管理 REST（M2-book，{@code doc/m2-book/task/03-管理REST.md} T3.2）。
 *
 * <p>物理隔开自 {@code MusicRootController}：仅处理 {@link MediaType#BOOK} 图书目录；
 * 扫描触发走 {@link BookScanService}（独立 ReentrantLock）；删除时级联隐藏 {@link Book} 记录。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/book-roots")
@RequiredArgsConstructor
public class BookRootController {

    /** 新建/部分更新请求体（PATCH 字段均可选） */
    public record BookRootRequest(String name, String path, Boolean enabled, MediaType mediaType) {
    }

    private final LibraryRootRepository libraryRootRepository;
    private final BookRepository bookRepository;
    private final BookScanService bookScanService;

    /**
     * 列出图书目录（含停用，ID 升序）。
     *
     * <p>停用根一并返回：管理端列表需展示"启用"开关（Q25），停用后可再启用；
     * 扫描/状态读仍只取启用根（{@code EnabledTrue}），此处不设 enabled 过滤。</p>
     */
    @GetMapping
    public ApiResponse<List<BookRootDto>> list() {
        // 强制 BOOK 过滤；不允许通过 URL 改查 music（与 /api/music-roots 物理隔开）
        List<BookRootDto> items = libraryRootRepository.findByMediaTypeOrderByIdAsc(MediaType.BOOK)
                .stream()
                .map(BookRootDto::of)
                .toList();
        return ApiResponse.ok(items);
    }

    /** 新建图书目录 */
    @PostMapping
    public ApiResponse<BookRootDto> create(@RequestBody BookRootRequest req) {
        String name = Strings.trimToNull(req.name());
        String path = Strings.trimToNull(req.path());
        if (name == null || path == null) {
            throw BizException.paramError("图书目录名称与路径不能为空");
        }
        if (libraryRootRepository.findByPath(path).isPresent()) {
            throw BizException.conflict("路径已被其他目录占用: " + path);
        }
        LibraryRoot root = new LibraryRoot();
        root.setName(name);
        root.setPath(path);
        root.setEnabled(req.enabled() == null || req.enabled());
        root.setMediaType(MediaType.BOOK);
        return ApiResponse.ok(BookRootDto.of(libraryRootRepository.save(root)));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public ApiResponse<BookRootDto> get(@PathVariable Long id) {
        return ApiResponse.ok(BookRootDto.of(requireBookRoot(id)));
    }

    /** 部分更新（name/path/enabled 均可选） */
    @PatchMapping("/{id}")
    public ApiResponse<BookRootDto> update(@PathVariable Long id, @RequestBody BookRootRequest req) {
        LibraryRoot root = requireBookRoot(id);
        if (req.name() != null && !req.name().isBlank()) {
            root.setName(req.name().trim());
        }
        if (req.path() != null && !req.path().isBlank()) {
            String newPath = req.path().trim();
            libraryRootRepository.findByPath(newPath)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw BizException.conflict("路径已被其他目录占用: " + newPath);
                    });
            root.setPath(newPath);
        }
        if (req.enabled() != null) {
            root.setEnabled(req.enabled());
        }
        return ApiResponse.ok(BookRootDto.of(libraryRootRepository.save(root)));
    }

    /** 删除（级联隐藏该根下所有 Book 记录，物理删除 root） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        LibraryRoot root = requireBookRoot(id);
        for (var book : bookRepository.findByLibraryRootId(id)) {
            book.setIsAvailable(false);
            bookRepository.save(book);
        }
        libraryRootRepository.delete(root);
        return ApiResponse.ok();
    }

    /**
     * 触发单根扫描。立即返回，扫描在独立线程异步执行；
     * 客户端轮询 {@code GET /api/book-roots/scan/status} 观察状态。
     */
    @PostMapping("/{id}/scan")
    public ApiResponse<ScanTriggerView> scan(@PathVariable Long id,
                                              @RequestParam(defaultValue = "false") boolean force) {
        requireBookRoot(id);
        // BookScanService 内部 tryLock 失败 → BizException 1100 抛回客户端
        // 成功获取锁后立即返回 SCANNING，扫描在 CompletableFuture 中继续
        CompletableFuture.runAsync(() -> {
            try {
                ScanStats stats = bookScanService.scanRoot(id, force);
                log.info("图书扫描完成（异步）: root={} {}", id, stats);
            } catch (Exception e) {
                log.warn("图书扫描失败: root={}", id, e);
            }
        });
        return ApiResponse.ok(new ScanTriggerView("SCANNING", id, "图书扫描已启动"));
    }

    /**
     * 触发所有 BOOK 根扫描（串行）。返回 SCANNING 状态。
     */
    @PostMapping("/scan/all")
    public ApiResponse<ScanTriggerView> scanAll(@RequestParam(defaultValue = "false") boolean force) {
        List<LibraryRoot> roots = libraryRootRepository.findByMediaTypeAndEnabledTrueOrderByIdAsc(MediaType.BOOK);
        CompletableFuture.runAsync(() -> {
            for (LibraryRoot r : roots) {
                try {
                    bookScanService.scanRoot(r.getId(), force);
                } catch (Exception e) {
                    log.warn("图书根扫描失败: root={}", r.getId(), e);
                }
            }
        });
        return ApiResponse.ok(new ScanTriggerView("SCANNING", null,
                "已启动 " + roots.size() + " 个图书目录扫描"));
    }

    /**
     * 全局扫描状态（用于 Vue Dashboard 轮询）。
     */
    @GetMapping("/scan/status")
    public ApiResponse<BookScanStatusView> status() {
        List<LibraryRoot> roots = libraryRootRepository.findByMediaTypeAndEnabledTrueOrderByIdAsc(MediaType.BOOK);
        LibraryRoot current = roots.stream()
                .filter(r -> r.getScanStatus() == ScanStatus.SCANNING)
                .findFirst()
                .orElse(null);
        LibraryRoot lastDone = roots.stream()
                .filter(r -> r.getLastScanAt() != null)
                .max((a, b) -> a.getLastScanAt().compareTo(b.getLastScanAt()))
                .orElse(null);
        return ApiResponse.ok(new BookScanStatusView(
                current != null ? "SCANNING" : "IDLE",
                current == null ? null : current.getId(),
                current == null ? null : Instant.now(),
                lastDone == null ? null : lastDone.getLastScanAt(),
                lastDone == null ? null : lastDone.getLastScanStats()));
    }

    private LibraryRoot requireBookRoot(Long id) {
        LibraryRoot r = libraryRootRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("图书目录不存在: " + id));
        if (r.getMediaType() != MediaType.BOOK) {
            throw BizException.paramError("该目录非图书类型: " + r.getName());
        }
        return r;
    }

    /** 扫描触发返回 */
    public record ScanTriggerView(String scanStatus, Long rootId, String message) {
    }

    /** 扫描状态读 */
    public record BookScanStatusView(String scanStatus, Long currentRootId,
                                     Instant startedAt, Instant lastScanAt, String lastStats) {
    }
}