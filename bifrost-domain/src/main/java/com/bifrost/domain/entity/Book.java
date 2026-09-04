package com.bifrost.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 图书（Book）。
 *
 * <p>一条 Book = 一个电子文件及其解析出的元数据（M2-book，ADR-0004）。
 * 物理隔开 music 侧 Track/Album/Artist（不复用、不聚合）；
 * 字段集见 {@code doc/m2-book/task/01-图书核心.md} T1.1 与 CONTEXT.md。
 * 一书一文件（Q12 决策）；series/author 字段化、不归一（Q3 B）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "book", uniqueConstraints = @UniqueConstraint(name = "uk_book_filePath", columnNames = "filePath"))
public class Book extends BaseEntity {

    // ---- 文件（每书一文件，unique） ----

    /** 文件绝对路径（唯一，非空） */
    @Column(nullable = false, length = 1024)
    private String filePath;

    /** 文件大小（字节，非空） */
    @Column(nullable = false)
    private Long fileSize = 0L;

    /** 文件最后修改时间（毫秒 epoch，非空） */
    @Column(nullable = false)
    private Long fileLastModified = 0L;

    /** 变更检测指纹 = path|size|mtime（非空） */
    @Column(nullable = false, length = 2048)
    private String fingerprint;

    // ---- 元数据 ----

    /** 标题（解析失败时回退 fileName 去扩展名；非空） */
    @Column(nullable = false, length = 1024)
    private String title;

    /** 多作者以 " & " 拼接（Q18 决策，Calibre 同款） */
    @Column(length = 2048)
    private String authors;

    /** 语言（dc:language；BCP-47，如 "zh-CN" / "en"） */
    @Column(length = 32)
    private String language;

    /** 出版社 */
    @Column(length = 512)
    private String publisher;

    /** 出版年份（仅取 dc:date 前 4 位，1–9999） */
    private Integer pubDate;

    /** 简介（dc:description） */
    @Column(length = 65535)
    private String description;

    /** 多主题以 "; " 拼接 */
    @Column(length = 2048)
    private String subject;

    /** 标识符（dc:identifier；通常 ISBN/UUID/URL） */
    @Column(length = 512)
    private String identifier;

    /** 系列名（calibre:series 或 EPUB 3 belongs-to-collection） */
    @Column(length = 512)
    private String series;

    /** 系列索引（calibre:series_index / group-position） */
    private Double seriesIndex;

    /** 版权声明（dc:rights） */
    @Column(length = 1024)
    private String rights;

    /** 格式大类（EPUB / PDF） */
    @Column(length = 16)
    private String format;

    /** 扩展名（小写；epub / pdf / kepub.epub） */
    @Column(length = 32)
    private String extension;

    // ---- 封面 ----

    /** 封面来源标记：EMBEDDED / UPLOADED / null（无封面） */
    @Column(length = 16)
    private String coverSource;

    // ---- 扫描状态 ----

    /** 文件是否仍存在（缺失=隐藏，保留记录供元数据查询） */
    @Column(nullable = false)
    private Boolean isAvailable = true;

    /** 归属库根（LibraryRoot 外键） */
    @Column(nullable = false)
    private Long libraryRootId;

    // ---- 收藏 / 评分（Day-one 不暴露 REST 写入，预留字段） ----

    /** 收藏时间（null=未收藏） */
    private Instant starredAt;

    /** 评分 0–5（0=无） */
    @Column(nullable = false)
    private Integer rating = 0;
}