package com.bifrost.domain.repo;

import com.bifrost.domain.entity.PlayQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 播放队列仓储。
 */
public interface PlayQueueRepository extends JpaRepository<PlayQueue, Long> {

    /** 用户播放队列（每用户至多一行） */
    Optional<PlayQueue> findByUserId(Long userId);
}
