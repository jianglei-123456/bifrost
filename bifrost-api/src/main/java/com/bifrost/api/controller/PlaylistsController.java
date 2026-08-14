package com.bifrost.api.controller;

import com.bifrost.api.dto.PlaylistDetailView;
import com.bifrost.api.response.ApiResponse;
import com.bifrost.common.exception.BizException;
import com.bifrost.core.audio.service.PlaylistService;
import com.bifrost.domain.entity.Playlist;
import com.bifrost.domain.entity.PlaylistEntry;
import com.bifrost.domain.entity.User;
import com.bifrost.domain.repo.TrackRepository;
import com.bifrost.domain.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 歌单管理（《通用功能说明》§9.2：/api/playlists*）。
 */
@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistsController {

    private final PlaylistService playlistService;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;

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

    /** 追加曲目 {trackId}（position 自动追加） */
    @PostMapping("/{id}/entries")
    public ApiResponse<PlaylistEntry> addEntry(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long trackId = body.get("trackId");
        if (trackId == null) {
            throw BizException.paramError("trackId 不能为空");
        }
        return ApiResponse.ok(playlistService.addEntry(id, trackId));
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
