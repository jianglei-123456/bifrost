package com.bifrost.api.controller;

import com.bifrost.api.dto.ScanStatusView;
import com.bifrost.api.response.ApiResponse;
import com.bifrost.common.exception.BizException;
import com.bifrost.common.util.Strings;
import com.bifrost.core.audio.ScanService;
import com.bifrost.core.book.BookScanService;
import com.bifrost.core.event.ScanStats;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import com.bifrost.domain.enums.ScanStatus;
import com.bifrost.domain.repo.BookRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
import com.bifrost.domain.repo.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库根与扫描管理（《通用功能说明》§9.2：/api/library-roots*、/api/scan*）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LibraryRootController {

    /** 库根创建/编辑请求体 */
    public record LibraryRootRequest(String name, String path, Boolean enabled, MediaType mediaType) {
    }

    private final LibraryRootRepository libraryRootRepository;
    private final TrackRepository trackRepository;
    private final BookRepository bookRepository;
    private final ScanService scanService;
    private final BookScanService bookScanService;

    /** 库根列表（含扫描状态与上次统计） */
    @GetMapping("/library-roots")
    public ApiResponse<List<LibraryRoot>> list(
            @RequestParam(required = false) MediaType mediaType) {
        List<LibraryRoot> roots = (mediaType == null)
                ? libraryRootRepository.findAllByOrderByIdAsc()
                : libraryRootRepository.findByMediaTypeAndEnabledTrueOrderByIdAsc(mediaType);
        return ApiResponse.ok(roots);
    }

    /** 新增库根 */
    @PostMapping("/library-roots")
    public ApiResponse<LibraryRoot> create(@RequestBody LibraryRootRequest request) {
        String name = Strings.trimToNull(request.name());
        String path = Strings.trimToNull(request.path());
        if (name == null || path == null) {
            throw BizException.paramError("库根名称与路径不能为空");
        }
        if (libraryRootRepository.findByPath(path).isPresent()) {
            throw BizException.conflict("库根路径已存在: " + path);
        }
        LibraryRoot root = new LibraryRoot();
        root.setName(name);
        root.setPath(path);
        root.setEnabled(request.enabled() == null || request.enabled());
        root.setMediaType(request.mediaType() == null ? MediaType.MUSIC : request.mediaType());
        return ApiResponse.ok(libraryRootRepository.save(root));
    }

    /** 库根详情 */
    @GetMapping("/library-roots/{id}")
    public ApiResponse<LibraryRoot> get(@PathVariable Long id) {
        return ApiResponse.ok(requireRoot(id));
    }

    /** 编辑库根（路径变更下一轮扫描生效） */
    @PutMapping("/library-roots/{id}")
    public ApiResponse<LibraryRoot> update(@PathVariable Long id, @RequestBody LibraryRootRequest request) {
        LibraryRoot root = requireRoot(id);
        if (request.name() != null && !request.name().isBlank()) {
            root.setName(request.name().trim());
        }
        if (request.path() != null && !request.path().isBlank()) {
            String newPath = request.path().trim();
            libraryRootRepository.findByPath(newPath)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw BizException.conflict("库根路径已存在: " + newPath);
                    });
            root.setPath(newPath);
        }
        if (request.enabled() != null) {
            root.setEnabled(request.enabled());
        }
        if (request.mediaType() != null) {
            root.setMediaType(request.mediaType());
        }
        return ApiResponse.ok(libraryRootRepository.save(root));
    }

    /** 删除库根 → 级联隐藏其曲目/图书（不物理删除记录，标记 isAvailable=false，见《音乐管理技术设计》§1.7） */
    @DeleteMapping("/library-roots/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        LibraryRoot root = requireRoot(id);
        for (var track : trackRepository.findByLibraryRootId(id)) {
            track.setIsAvailable(false);
            trackRepository.save(track);
        }
        // 图书 isAvailable 也置 false（不删除物理记录，Q5 B 同款）
        for (var book : bookRepository.findByLibraryRootId(id)) {
            book.setIsAvailable(false);
            bookRepository.save(book);
        }
        libraryRootRepository.delete(root);
        return ApiResponse.ok();
    }

    /** 触发单根扫描（扫描进行中 → 1100）；fullScan=true 强制全量重解析（回填歌词等标签字段） */
    @PostMapping("/library-roots/{id}/scan")
    public ApiResponse<ScanStats> scanRoot(@PathVariable Long id,
                                           @RequestParam(defaultValue = "false") boolean fullScan) {
        // 按 mediaType 路由到对应扫描器（ADR-0004 物理隔开）
        LibraryRoot root = requireRoot(id);
        if (root.getMediaType() == MediaType.BOOK) {
            return ApiResponse.ok(bookScanService.scanRoot(id, fullScan));
        }
        return ApiResponse.ok(scanService.scanRoot(id, fullScan));
    }

    /** 触发全量扫描（全部启用库根，串行）；fullScan=true 强制全量重解析
     *  Day-one 仅扫音乐库根；图书库根按需触发（Q3 B：独立调度） */
    @PostMapping("/scan")
    public ApiResponse<ScanStats> scanAll(@RequestParam(defaultValue = "false") boolean fullScan) {
        return ApiResponse.ok(scanService.scanAll(fullScan));
    }

    /** 扫描状态（MUSIC 根；BOOK 根扫描状态读 {@code /api/book-roots/scan/status}） */
    @GetMapping("/scan/status")
    public ApiResponse<ScanStatusView> scanStatus() {
        List<LibraryRoot> roots = libraryRootRepository.findByMediaTypeAndEnabledTrueOrderByIdAsc(MediaType.MUSIC);
        boolean scanning = roots.stream().anyMatch(r -> r.getScanStatus() == ScanStatus.SCANNING);
        return ApiResponse.ok(new ScanStatusView(scanning, roots));
    }

    private LibraryRoot requireRoot(Long id) {
        return libraryRootRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("库根不存在: " + id));
    }
}
