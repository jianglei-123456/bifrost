package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 音乐文件夹列表（getMusicFolders）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MusicFolders {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "musicFolder")
    private List<MusicFolder> musicFolder;

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MusicFolder {
        /** 库根 ID（纯数字） */
        @JacksonXmlProperty(isAttribute = true)
        private String id;
        /** 库根名称 */
        @JacksonXmlProperty(isAttribute = true)
        private String name;
    }
}
