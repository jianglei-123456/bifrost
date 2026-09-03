package com.bifrost.domain.repo;

import com.bifrost.domain.entity.PlaylistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 歌单条目仓储。
 */
public interface PlaylistEntryRepository extends JpaRepository<PlaylistEntry, Long> {

    /** 歌单内条目按 position 排序 */
    List<PlaylistEntry> findByPlaylistIdOrderByPositionAsc(Long playlistId);

    /** 歌单内条目（无排序） */
    List<PlaylistEntry> findByPlaylistId(Long playlistId);

    /** 歌单内最大 position（追加用） */
    Optional<PlaylistEntry> findTopByPlaylistIdOrderByPositionDesc(Long playlistId);

    /** 删除整个歌单的条目（级联删除用） */
    void deleteByPlaylistId(Long playlistId);

    /** 删除引用某曲目的条目（曲目被彻底移除时清理用） */
    void deleteByTrackId(Long trackId);
}
