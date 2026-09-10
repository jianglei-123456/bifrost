package com.bifrost.api.controller;

import com.bifrost.api.dto.music.MusicRootDto;
import com.bifrost.api.dto.music.MusicScanStatusView;
import com.bifrost.api.response.ApiResponse;
import com.bifrost.common.exception.BizException;
import com.bifrost.common.util.Strings;
import com.bifrost.core.audio.MusicScanService;
import com.bifrost.core.event.ScanStats;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import com.bifrost.domain.enums.ScanStatus;
import com.bifrost.domain.repo.LibraryRootRepository;
import com.bifrost.domain.repo.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 音乐目录与音乐扫描管理（《通用功能说明》§9.2）。
 *
 * <p>端点前缀 {@code /api/music-roots}，镜像图书侧 {@code /api/book-roots}（ADR-0005 媒体前缀命名）：
 * 只处理 {@link MediaType#MUSIC} 的音乐目录，扫描走 {@link MusicScanService}；图书目录由
 * {@code BookRootController} 独立处理（ADR-0004 物理隔开）。两侧的根行仍共享 {@code library_root}
 * 表，由 {@code mediaType} 列隔离。</p>
 */
@RestController
@RequestMapping("/api/music-roots")
@RequiredArgsConstructor
public class MusicRootController {

    /** 音乐目录创建/部分更新请求体（mediaType 恒为 MUSIC，不接受覆盖） */
    public record MusicRootRequest(String name, String path, Boolean enabled) {
    }

    private final LibraryRootRepository libraryRootRepository;
    private final TrackRepository trackRepository;
    private final MusicScanService musicScanService;

    /**
     * 列出音乐目录（含停用，ID 升序）。
     *
     * <p>停用根一并返回：管理端列表需展示「启用」开关，停用后可再启用；扫描/状态读只取启用根。</p>
     */
    @GetMapping
    public ApiResponse<List<MusicRootDto>> list() {
        List<MusicRootDto> items = libraryRootRepository.findByMediaTypeOrderByIdAsc(MediaType.MUSIC)
                .stream()
                .map(MusicRootDto::of)
                .toList();
        return ApiResponse.ok(items);
    }

    /** 新增音乐目录 */
    @PostMapping
    public ApiResponse<MusicRootDto> create(@RequestBody MusicRootRequest request) {
        String name = Strings.trimToNull(request.name());
        String path = Strings.trimToNull(request.path());
        if (name == null || path == null) {
            throw BizException.paramError("音乐目录名称与路径不能为空");
        }
        if (libraryRootRepository.findByPath(path).isPresent()) {
            throw BizException.conflict("路径已被其他目录占用: " + path);
        }
        LibraryRoot root = new LibraryRoot();
        root.setName(name);
        root.setPath(path);
        root.setEnabled(request.enabled() == null || request.enabled());
        root.setMediaType(MediaType.MUSIC);
        return ApiResponse.ok(MusicRootDto.of(libraryRootRepository.save(root)));
    }

    /** 音乐目录详情 */
    @GetMapping("/{id}")
    public ApiResponse<MusicRootDto> get(@PathVariable Long id) {
        return ApiResponse.ok(MusicRootDto.of(requireMusicRoot(id)));
    }

    /** 编辑音乐目录（部分更新；路径变更下一轮扫描生效） */
    @PatchMapping("/{id}")
    public ApiResponse<MusicRootDto> update(@PathVariable Long id, @RequestBody MusicRootRequest request) {
        LibraryRoot root = requireMusicRoot(id);
        if (request.name() != null && !request.name().isBlank()) {
            root.setName(request.name().trim());
        }
        if (request.path() != null && !request.path().isBlank()) {
            String newPath = request.path().trim();
            libraryRootRepository.findByPath(newPath)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw BizException.conflict("路径已被其他目录占用: " + newPath);
                    });
            root.setPath(newPath);
        }
        if (request.enabled() != null) {
            root.setEnabled(request.enabled());
        }
        return ApiResponse.ok(MusicRootDto.of(libraryRootRepository.save(root)));
    }

    /** 删除音乐目录 → 级联隐藏其曲目（不物理删除记录，标记 isAvailable=false，见《音乐管理技术设计》§1.7） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        LibraryRoot root = requireMusicRoot(id);
        for (var track : trackRepository.findByLibraryRootId(id)) {
            track.setIsAvailable(false);
            trackRepository.save(track);
        }
        libraryRootRepository.delete(root);
        return ApiResponse.ok();
    }

    /** 触发单根扫描（同步，扫描进行中 → 1100）；fullScan=true 强制全量重解析（回填歌词等标签字段） */
    @PostMapping("/{id}/scan")
    public ApiResponse<ScanStats> scanRoot(@PathVariable Long id,
                                           @RequestParam(defaultValue = "false") boolean fullScan) {
        requireMusicRoot(id);
        return ApiResponse.ok(musicScanService.scanRoot(id, fullScan));
    }

    /** 触发全部音乐目录扫描（同步、串行，仅 MUSIC）；fullScan=true 强制全量重解析 */
    @PostMapping("/scan/all")
    public ApiResponse<ScanStats> scanAll(@RequestParam(defaultValue = "false") boolean fullScan) {
        return ApiResponse.ok(musicScanService.scanAll(fullScan));
    }

    /** 音乐扫描状态（仅启用的音乐目录；图书扫描状态读 {@code /api/book-roots/scan/status}） */
    @GetMapping("/scan/status")
    public ApiResponse<MusicScanStatusView> scanStatus() {
        List<MusicRootDto> roots = libraryRootRepository
                .findByMediaTypeAndEnabledTrueOrderByIdAsc(MediaType.MUSIC)
                .stream()
                .map(MusicRootDto::of)
                .toList();
        boolean scanning = roots.stream().anyMatch(r -> r.scanStatus() == ScanStatus.SCANNING);
        return ApiResponse.ok(new MusicScanStatusView(scanning, roots));
    }

    /** 取音乐目录；不存在或非 MUSIC 类型均拒绝（图书目录不得经此端点操作） */
    private LibraryRoot requireMusicRoot(Long id) {
        LibraryRoot root = libraryRootRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("音乐目录不存在: " + id));
        if (root.getMediaType() != MediaType.MUSIC) {
            throw BizException.paramError("该目录非音乐类型: " + root.getName());
        }
        return root;
    }
}
