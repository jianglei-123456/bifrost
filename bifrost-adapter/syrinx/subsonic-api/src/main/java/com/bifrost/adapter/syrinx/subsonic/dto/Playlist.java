package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 歌单详情（getPlaylist / createPlaylist：含条目）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Playlist {

    @JacksonXmlProperty(isAttribute = true)
    private String id;
    @JacksonXmlProperty(isAttribute = true)
    private String name;
    @JacksonXmlProperty(isAttribute = true)
    private String owner;
    @JacksonXmlProperty(isAttribute = true)
    private Boolean isPublic;
    @JacksonXmlProperty(isAttribute = true)
    private String created;
    @JacksonXmlProperty(isAttribute = true)
    private String changed;
    @JacksonXmlProperty(isAttribute = true)
    private Integer songCount;
    @JacksonXmlProperty(isAttribute = true)
    private Integer duration;
    @JacksonXmlProperty(isAttribute = true)
    private String comment;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "entry")
    private List<Child> entry;
}
