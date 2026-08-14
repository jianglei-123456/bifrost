package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 用户（getUser / getUsers；Q18 省略 email 属性）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User {

    @JacksonXmlProperty(isAttribute = true)
    private String username;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean adminRole = true;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean settingsRole = true;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean downloadRole = true;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean uploadRole = false;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean playlistRole = true;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean coverArtRole = true;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean commentRole = true;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean podcastRole = false;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean streamRole = true;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean jukeboxRole = false;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean shareRole = false;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean scrobblingEnabled = true;

    /** 可访问音乐文件夹 ID（启用库根） */
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "folder")
    private List<String> folder;
}
