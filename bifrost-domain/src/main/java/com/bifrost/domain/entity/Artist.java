package com.bifrost.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 艺术家（Artist）。
 *
 * <p>全局唯一（同名合并）；indexLetter 为索引分组字母（拼音首字母 / A–Z / #）。
 * starred/rating/playCount/lastPlayed 为 ADR-0001 增补字段（三态收藏与艺术家级播放统计）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "artist")
public class Artist extends BaseEntity {

    /** 艺术家名称（唯一） */
    @Column(nullable = false, unique = true, length = 255)
    private String name;

    /** 索引分组字母（拼音首字母 / A–Z / #） */
    @Column(length = 1)
    private String indexLetter;

    /** MusicBrainz ID（预留，刮削用） */
    @Column(length = 64)
    private String musicBrainzId;

    /** 收藏时间（null=未收藏；ADR-0001/Q14：starred = starredAt != null） */
    private Instant starredAt;

    /** 评分 1–5（0 无） */
    @Column(nullable = false)
    private Integer rating = 0;

    /** 播放次数 */
    @Column(nullable = false)
    private Integer playCount = 0;

    /** 最后播放时间 */
    private Instant lastPlayed;
}
