package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * ID3 艺术家（getArtist：含专辑列表）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArtistID3 {

    @JacksonXmlProperty(isAttribute = true)
    private String id;

    @JacksonXmlProperty(isAttribute = true)
    private String name;

    /** v1 艺术家无封面源，省略 coverArt（Q13） */

    @JacksonXmlProperty(isAttribute = true)
    private Integer albumCount;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "album")
    private List<AlbumID3> album;
}
