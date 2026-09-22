package com.bifrost.api.controller;

import com.bifrost.api.dto.PlaylistDetailView;
import com.bifrost.api.response.ApiResponse;
import com.bifrost.api.response.PageResult;
import com.bifrost.common.exception.BizException;
import com.bifrost.common.util.Strings;
import com.bifrost.core.audio.service.PlaylistService;
import com.bifrost.domain.entity.Playlist;
import com.bifrost.domain.entity.PlaylistEntry;
import com.bifrost.domain.entity.Track;
import com.bifrost.domain.entity.User;
import com.bifrost.domain.repo.PlaylistEntryRepository;
import com.bifrost.domain.repo.TrackRepository;
import com.bifrost.domain.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 歌单管理（《通用功能说明》§9.2：/api/playlists*）。
 */
@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistsController {

    private static final int MAX_PAGE_SIZE = 200;

    private final PlaylistService playlistService;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;
    private final PlaylistEntryRepository entryRepository;

    /** 歌单列表 */
    @GetMapping
    public ApiResponse<List<Playlist>> list() {
        return ApiResponse.ok(playlistService.list());
    }

    /** 新建歌单 {name, comment} */
    @PostMapping
    public ApiResponse<Playlist> create(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(playlistService.create(body.get("name"), body.get("comment"), currentOwnerId()));
    }

    /** 歌单详情（含条目与曲目） */
    @GetMapping("/{id}")
    public ApiResponse<PlaylistDetailView> get(@PathVariable Long id) {
        PlaylistService.PlaylistWithEntries view = playlistService.get(id);
        List<PlaylistDetailView.PlaylistEntryView> entries = view.entries().stream()
                .map(e -> new PlaylistDetailView.PlaylistEntryView(e.getId(), e.getPosition(),
                        trackRepository.findById(e.getTrackId()).orElse(null)))
                .toList();
        return ApiResponse.ok(new PlaylistDetailView(view.playlist(), entries));
    }

    /**
     * 候选曲目（分页）：可加入本歌单的曲目——排除已在歌单的、排除文件缺失的。
     *
     * <p>排序固定 {@code createdAt desc, id desc}。第二排序键 id 是必需的：扫描按批插入，
     * 同批曲目的 createdAt 完全相同是常态，只按 createdAt 排序时翻页会重复或漏行。</p>
     *
     * <p>{@code q} 为空即全量分页（弹窗默认列表），非空即按标题/艺术家模糊过滤——默认列表与
     * 搜索是同一个端点换参数，因此两者排序一致。</p>
     */
    @GetMapping("/{id}/candidate-tracks")
    public ApiResponse<PageResult<Track>> candidateTracks(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q) {
        playlistService.get(id);
        List<Long> excluded = entryRepository.findByPlaylistId(id).stream()
                .map(PlaylistEntry::getTrackId)
                .toList();
        int pageSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(0, page), pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Track> result = trackRepository.findCandidates(excluded, Strings.trimToNull(q), pageable);
        return ApiResponse.ok(new PageResult<>(result.getTotalElements(), result.getContent()));
    }

    /** 更新 {name, comment} */
    @PutMapping("/{id}")
    public ApiResponse<Playlist> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(playlistService.update(id, body.get("name"), body.get("comment")));
    }

    /** 删除歌单 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        playlistService.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 追加曲目 {@code {trackIds: [...]}}（position 依次追加在末尾）。
     *
     * <p>破坏性变更：原为单首 {@code {trackId}}（弹窗改多选后由本端点一次提交整批）。
     * 入参在此归一化（丢弃 null、按首次出现去重）；这是入参归一化，不是对越权调用方的兜底
     * ——绕过本端点（或重复调用本端点）仍可能写入重复条目，见 spec「进一步说明」。</p>
     */
    @PostMapping("/{id}/entries")
    public ApiResponse<Integer> addEntries(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> raw = body.get("trackIds");
        if (raw == null || raw.isEmpty()) {
            throw BizException.paramError("trackIds 不能为空");
        }
        List<Long> trackIds = new ArrayList<>(new LinkedHashSet<>(
                raw.stream().filter(trackId -> trackId != null).toList()));
        if (trackIds.isEmpty()) {
            throw BizException.paramError("trackIds 不能为空");
        }
        return ApiResponse.ok(playlistService.addEntries(id, trackIds));
    }

    /** 删除条目（position 自动重排） */
    @DeleteMapping("/{id}/entries/{entryId}")
    public ApiResponse<Void> removeEntry(@PathVariable Long id, @PathVariable Long entryId) {
        playlistService.removeEntry(id, entryId);
        return ApiResponse.ok();
    }

    private Long currentOwnerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? null : authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> BizException.unauthorized("未认证"));
        return user.getId();
    }
}
