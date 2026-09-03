package com.bifrost.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 播放队列（PlayQueue，Subsonic getPlayQueue/savePlayQueue）。
 *
 * <p>每用户至多一行（user_id 唯一）；savePlayQueue 全量重建（换新行 + 重建条目），
 * 因此 updatedAt 即"changed"且保证随每次保存刷新。currentTrackId/position 可空。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "play_queue")
public class PlayQueue extends BaseEntity {

    /** 所属用户（User 外键，v1=admin；每用户至多一行） */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** 当前曲目（Track 外键，可空） */
    @Column(name = "current_track_id")
    private Long currentTrackId;

    /** 当前曲目内位置（原样回显，客户端单位自洽；可空） */
    private Long position;
}
