package com.bifrost.domain.repo;

import com.bifrost.domain.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 书签仓储。
 */
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    /** 用户对某曲目的书签（唯一） */
    Optional<Bookmark> findByUserIdAndTrackId(Long userId, Long trackId);

    /** 用户全部书签（新→旧） */
    List<Bookmark> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /** 删除用户对某曲目的书签 */
    void deleteByUserIdAndTrackId(Long userId, Long trackId);

    /** 删除引用某曲目的书签（曲目被彻底移除时清理用） */
    void deleteByTrackId(Long trackId);
}
