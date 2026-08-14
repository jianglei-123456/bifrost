package com.bifrost.core.audio.service;

import com.bifrost.common.exception.BizException;
import com.bifrost.domain.entity.Playlist;
import com.bifrost.domain.entity.PlaylistEntry;
import com.bifrost.domain.repo.PlaylistEntryRepository;
import com.bifrost.domain.repo.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 歌单服务：CRUD、条目增删（position 唯一于歌单内维护）。
 *
 * <p>v1 owner=admin、public=true；comment 字段（Q18）。
 * 曲目缺失（isAvailable=false）时歌单条目保留，展示层隐藏（歌单引用不漂移）。</p>
 */
@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistEntryRepository entryRepository;

    /** 歌单及条目视图 */
    public record PlaylistWithEntries(Playlist playlist, List<PlaylistEntry> entries) {
    }

    @Transactional
    public Playlist create(String name, String comment, Long ownerId) {
        if (name == null || name.isBlank()) {
            throw BizException.paramError("歌单名称不能为空");
        }
        Playlist playlist = new Playlist();
        playlist.setName(name.trim());
        playlist.setComment(comment);
        playlist.setOwnerId(ownerId);
        playlist.setIsPublic(true);
        return playlistRepository.save(playlist);
    }

    public List<Playlist> list() {
        return playlistRepository.findAllByOrderByCreatedAtDesc();
    }

    public PlaylistWithEntries get(Long id) {
        Playlist playlist = require(id);
        return new PlaylistWithEntries(playlist, entryRepository.findByPlaylistIdOrderByPositionAsc(id));
    }

    @Transactional
    public Playlist update(Long id, String name, String comment) {
        Playlist playlist = require(id);
        if (name != null && !name.isBlank()) {
            playlist.setName(name.trim());
        }
        playlist.setComment(comment);
        return playlistRepository.save(playlist);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        entryRepository.deleteByPlaylistId(id);
        playlistRepository.deleteById(id);
    }

    /** 追加曲目（position = 当前最大 + 1）。 */
    @Transactional
    public PlaylistEntry addEntry(Long playlistId, Long trackId) {
        require(playlistId);
        int next = entryRepository.findTopByPlaylistIdOrderByPositionDesc(playlistId)
                .map(e -> e.getPosition() + 1).orElse(1);
        PlaylistEntry entry = new PlaylistEntry();
        entry.setPlaylistId(playlistId);
        entry.setTrackId(trackId);
        entry.setPosition(next);
        return entryRepository.save(entry);
    }

    /** 删除条目并重排剩余 position（保持 1..n 连续）。 */
    @Transactional
    public void removeEntry(Long playlistId, Long entryId) {
        require(playlistId);
        PlaylistEntry target = entryRepository.findById(entryId)
                .orElseThrow(() -> BizException.notFound("歌单条目不存在: " + entryId));
        if (!target.getPlaylistId().equals(playlistId)) {
            throw BizException.paramError("条目不属于该歌单");
        }
        entryRepository.delete(target);
        List<PlaylistEntry> rest = entryRepository.findByPlaylistIdOrderByPositionAsc(playlistId);
        int position = 1;
        for (PlaylistEntry e : rest) {
            e.setPosition(position++);
            entryRepository.save(e);
        }
    }

    private Playlist require(Long id) {
        return playlistRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("歌单不存在: " + id));
    }
}
