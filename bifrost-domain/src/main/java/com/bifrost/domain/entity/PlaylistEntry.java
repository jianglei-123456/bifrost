package com.bifrost.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 歌单条目（PlaylistEntry）。
 *
 * <p>position 唯一于歌单内（唯一约束 playlistId+position）；曲目缺失时条目保留（歌单引用不漂移）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "playlist_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_playlist_position", columnNames = {"playlist_id", "position"})
})
public class PlaylistEntry extends BaseEntity {

    /** 所属歌单（外键） */
    @Column(name = "playlist_id", nullable = false)
    private Long playlistId;

    /** 曲目（Track 外键） */
    @Column(name = "track_id", nullable = false)
    private Long trackId;

    /** 序号（唯一于歌单内） */
    @Column(nullable = false)
    private Integer position;
}
