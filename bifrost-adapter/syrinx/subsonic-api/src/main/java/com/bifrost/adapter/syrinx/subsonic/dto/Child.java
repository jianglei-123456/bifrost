package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 歌曲/目录条目（Child）：曲目、目录、歌单条目、nowPlaying 条目的基础结构。
 *
 * <p>字段全集见《Subsonic_API_参考》§4.0；可选属性未设置时省略。</p>
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Child {

    /** 实体 ID（tr-<id> / ar-<id> / al-<id> / 库根数字 ID） */
    @JacksonXmlProperty(isAttribute = true)
    private String id;

    /** 父级 ID（目录层级） */
    @JacksonXmlProperty(isAttribute = true)
    private String parent;

    /** 是否目录 */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean isDir;

    /** 标题（曲目/目录名） */
    @JacksonXmlProperty(isAttribute = true)
    private String title;

    @JacksonXmlProperty(isAttribute = true)
    private String album;

    @JacksonXmlProperty(isAttribute = true)
    private String artist;

    @JacksonXmlProperty(isAttribute = true)
    private Integer track;

    @JacksonXmlProperty(isAttribute = true)
    private Integer discNumber;

    @JacksonXmlProperty(isAttribute = true)
    private Integer year;

    @JacksonXmlProperty(isAttribute = true)
    private String genre;

    /** 封面 ID（统一 al-<id>，Q13） */
    @JacksonXmlProperty(isAttribute = true)
    private String coverArt;

    @JacksonXmlProperty(isAttribute = true)
    private Long size;

    @JacksonXmlProperty(isAttribute = true)
    private String contentType;

    @JacksonXmlProperty(isAttribute = true)
    private String suffix;

    @JacksonXmlProperty(isAttribute = true)
    private Integer duration;

    @JacksonXmlProperty(isAttribute = true)
    private Integer bitRate;

    /** 虚拟相对路径（模拟目录树） */
    @JacksonXmlProperty(isAttribute = true)
    private String path;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean isVideo;

    @JacksonXmlProperty(isAttribute = true)
    private Integer userRating;

    @JacksonXmlProperty(isAttribute = true)
    private Integer playCount;

    /** ISO-8601 创建时间 */
    @JacksonXmlProperty(isAttribute = true)
    private String created;

    /** ISO-8601 收藏时间（Q14） */
    @JacksonXmlProperty(isAttribute = true)
    private String starred;

    @JacksonXmlProperty(isAttribute = true)
    private String albumId;

    @JacksonXmlProperty(isAttribute = true)
    private String artistId;
}
