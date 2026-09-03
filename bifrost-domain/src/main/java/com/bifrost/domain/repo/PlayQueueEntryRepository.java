package com.bifrost.domain.repo;

import com.bifrost.domain.entity.PlayQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 播放队列条目仓储。
 */
public interface PlayQueueEntryRepository extends JpaRepository<PlayQueueEntry, Long> {

    /** 队列条目按提交顺序 */
    List<PlayQueueEntry> findByQueueIdOrderBySeqAsc(Long queueId);

    /** 删除整个队列的条目（全量重建用） */
    void deleteByQueueId(Long queueId);

    /** 删除引用某曲目的条目（曲目被彻底移除时清理用） */
    void deleteByTrackId(Long trackId);
}
