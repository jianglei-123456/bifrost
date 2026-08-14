package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 收藏（getStarred2：ID3 结构）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Starred2 {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "artist")
    private List<ArtistID3> artist;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "album")
    private List<AlbumID3> album;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "song")
    private List<Child> song;
}
