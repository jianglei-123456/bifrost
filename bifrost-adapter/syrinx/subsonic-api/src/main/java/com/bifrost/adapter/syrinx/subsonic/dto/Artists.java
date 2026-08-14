package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * ID3 艺术家索引（getArtists）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Artists {

    @JacksonXmlProperty(isAttribute = true)
    private String ignoredArticles = "";

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "index")
    private List<Indexes.Index> index;
}
