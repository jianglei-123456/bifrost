package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 歌单列表（getPlaylists）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Playlists {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "playlist")
    private List<PlaylistRef> playlist;

    /** 歌单引用 */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlaylistRef {
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
    }
}
