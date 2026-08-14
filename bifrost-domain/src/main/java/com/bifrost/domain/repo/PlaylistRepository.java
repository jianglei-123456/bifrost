package com.bifrost.domain.repo;

import com.bifrost.domain.entity.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 歌单仓储。
 */
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    /** 按所有者查询 */
    List<Playlist> findByOwnerId(Long ownerId);

    /** 全部歌单按创建时间倒序 */
    List<Playlist> findAllByOrderByCreatedAtDesc();
}
