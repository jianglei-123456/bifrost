package com.bifrost.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 专辑（Album）。
 *
 * <p>全局聚合实体（同名跨库根合并）；聚合键 = normalize(albumArtistName) + "|" + normalize(title)，
 * 无唯一约束（多碟/重名场景允许近似），由扫描逻辑 find-or-create 保证不重复（Q10）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "album")
public class Album extends BaseEntity {

    /** 专辑标题（规范化前的原值） */
    @Column(nullable = false, length = 512)
    private String title;

    /** 专辑艺术家（Artist 外键，可空：无 albumArtist 时以首个曲目艺术家聚合） */
    private Long artistId;

    /** 专辑艺术家名称（聚合键组成之一） */
    @Column(length = 255)
    private String albumArtistName;

    /** 发行年份（可空） */
    private Integer year;

    /** 流派（取首个） */
    @Column(length = 128)
    private String genre;

    /** 封面来源描述（EMBEDDED / 封面文件路径 / 空） */
    @Column(length = 512)
    private String coverSource;

    /** 总时长（秒，聚合计算） */
    @Column(nullable = false)
    private Integer duration = 0;

    /** 播放次数 */
    @Column(nullable = false)
    private Integer playCount = 0;

    /** 最后播放时间 */
    private Instant lastPlayed;

    /** 收藏时间（null=未收藏；Q14） */
    private Instant starredAt;

    /** 评分 1–5（0 无） */
    @Column(nullable = false)
    private Integer rating = 0;
}
