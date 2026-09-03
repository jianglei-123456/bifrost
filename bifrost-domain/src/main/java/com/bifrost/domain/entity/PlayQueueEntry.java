package com.bifrost.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 播放队列条目（PlayQueueEntry）。
 *
 * <p>seq 记录客户端提交的队列顺序（唯一约束 queue_id+seq）；队列随 savePlayQueue 全量重建。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "play_queue_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_queue_seq", columnNames = {"queue_id", "seq"})
})
public class PlayQueueEntry extends BaseEntity {

    /** 所属播放队列（PlayQueue 外键） */
    @Column(name = "queue_id", nullable = false)
    private Long queueId;

    /** 曲目（Track 外键） */
    @Column(name = "track_id", nullable = false)
    private Long trackId;

    /** 序号（客户端队列顺序，从 1 起） */
    @Column(nullable = false)
    private Integer seq;
}
