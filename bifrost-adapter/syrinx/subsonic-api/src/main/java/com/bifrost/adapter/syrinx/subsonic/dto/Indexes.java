package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 字母索引分组（getIndexes / getArtists 共用）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Indexes {

    /** 最近修改时间（毫秒；扫描时间） */
    @JacksonXmlProperty(isAttribute = true)
    private Long lastModified;

    /** 忽略冠词列表（v1 置空，Q16） */
    @JacksonXmlProperty(isAttribute = true)
    private String ignoredArticles = "";

    /** 快捷方式（v1 无播客，省略） */
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "shortcut")
    private List<Shortcut> shortcut;

    /** 字母分组 */
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "index")
    private List<Index> index;

    /** 根级条目（v1 不输出，Q16） */
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "child")
    private List<Child> child;

    /** 快捷方式 */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Shortcut {
        @JacksonXmlProperty(isAttribute = true)
        private String id;
        @JacksonXmlProperty(isAttribute = true)
        private String name;
    }

    /** 字母分组 */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Index {
        /** 分组字母（A–Z / #） */
        @JacksonXmlProperty(isAttribute = true)
        private String name;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "artist")
        private List<ArtistRef> artist;
    }

    /** 文件结构艺术家引用 */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ArtistRef {
        /** ar- 前缀 ID（"未知艺术家"为 ar-unknown） */
        @JacksonXmlProperty(isAttribute = true)
        private String id;
        @JacksonXmlProperty(isAttribute = true)
        private String name;
    }
}
