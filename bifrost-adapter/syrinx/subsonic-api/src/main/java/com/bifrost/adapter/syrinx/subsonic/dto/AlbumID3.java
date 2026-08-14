package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * ID3 专辑（getAlbum：含曲目列表；getAlbumList/getAlbumList2 的元素）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlbumID3 {

    /** al- 前缀 ID */
    @JacksonXmlProperty(isAttribute = true)
    private String id;

    @JacksonXmlProperty(isAttribute = true)
    private String name;

    @JacksonXmlProperty(isAttribute = true)
    private String artist;

    @JacksonXmlProperty(isAttribute = true)
    private String artistId;

    /** 封面 ID（统一 al-<id>，Q13） */
    @JacksonXmlProperty(isAttribute = true)
    private String coverArt;

    @JacksonXmlProperty(isAttribute = true)
    private Integer songCount;

    @JacksonXmlProperty(isAttribute = true)
    private Integer duration;

    @JacksonXmlProperty(isAttribute = true)
    private Integer playCount;

    @JacksonXmlProperty(isAttribute = true)
    private Integer year;

    @JacksonXmlProperty(isAttribute = true)
    private String genre;

    @JacksonXmlProperty(isAttribute = true)
    private String created;

    @JacksonXmlProperty(isAttribute = true)
    private String starred;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "song")
    private List<Child> song;
}
