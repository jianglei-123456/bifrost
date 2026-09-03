package com.bifrost.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 曲目（Track）。
 *
 * <p>一条曲目 = 一个媒体文件及其解析出的元数据；归属一个库根与至多一个专辑。
 * filePath 唯一；fingerprint 用于增量扫描变更检测。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "track")
public class Track extends BaseEntity {

    /** 曲目标题（标签缺失时用文件名兜底） */
    @Column(nullable = false, length = 512)
    private String title;

    /** 曲目号（默认 1） */
    @Column(nullable = false)
    private Integer trackNo = 1;

    /** 碟号（默认 1） */
    @Column(nullable = false)
    private Integer discNo = 1;

    /** 曲目艺术家（Artist 外键，可空） */
    private Long artistId;

    /** 曲目艺术家名称（冗余，展示用） */
    @Column(length = 255)
    private String artistName;

    /** 专辑艺术家名称（冗余，聚合键组成之一） */
    @Column(length = 255)
    private String albumArtistName;

    /** 所属专辑（Album 外键） */
    private Long albumId;

    /** 流派（取首个） */
    @Column(length = 128)
    private String genre;

    /** 年份（可空） */
    private Integer year;

    /** 时长（秒） */
    @Column(nullable = false)
    private Integer duration = 0;

    /** 码率 kbps */
    private Integer bitrate;

    /** 采样率 Hz */
    private Integer sampleRate;

    /** 容器格式（mp3/flac/m4a/wav） */
    @Column(length = 16)
    private String format;

    /** 文件绝对路径（唯一） */
    @Column(nullable = false, unique = true, length = 1024)
    private String filePath;

    /** 文件大小（字节） */
    @Column(nullable = false)
    private Long fileSize = 0L;

    /** 文件最后修改时间（毫秒） */
    @Column(nullable = false)
    private Long fileLastModified = 0L;

    /** 变更检测指纹 = 路径+大小+mtime */
    @Column(nullable = false, length = 2048)
    private String fingerprint;

    /** 播放次数 */
    @Column(nullable = false)
    private Integer playCount = 0;

    /** 内嵌歌词（USLT/©lyr/Vorbis LYRICS 原文，含换行；null=无） */
    @Column(length = 65535)
    private String lyrics;

    /** 最后播放时间 */
    private Instant lastPlayed;

    /** 收藏时间（null=未收藏；Q14） */
    private Instant starredAt;

    /** 评分 1–5（0 无） */
    @Column(nullable = false)
    private Integer rating = 0;

    /** 文件是否仍存在（缺失=隐藏） */
    @Column(nullable = false)
    private Boolean isAvailable = true;

    /** 归属库根（LibraryRoot 外键） */
    @Column(nullable = false)
    private Long libraryRootId;
}
