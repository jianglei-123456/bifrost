package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 结构化歌词列表（getLyricsBySongId，OpenSubsonic songLyrics 扩展）。
 *
 * <p>结构见《Subsonic_API_参考》/OpenSubsonic：{@code <lyricsList><structuredLyrics
 * displayArtist displayTitle lang synced><line start>文本</line></structuredLyrics></lyricsList>}；
 * 未同步歌词的 line 不含 start 属性。</p>
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LyricsList {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "structuredLyrics")
    private List<StructuredLyrics> structuredLyrics;

    /** 一组结构化歌词 */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StructuredLyrics {
        /** 展示歌手（可空） */
        @JacksonXmlProperty(isAttribute = true)
        private String displayArtist;
        /** 展示歌名（可空） */
        @JacksonXmlProperty(isAttribute = true)
        private String displayTitle;
        /** 歌词语言（未知用 und） */
        @JacksonXmlProperty(isAttribute = true)
        private String lang = "und";
        /** 是否带时间同步 */
        @JacksonXmlProperty(isAttribute = true)
        private boolean synced;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "line")
        private List<Line> line;

        /** 歌词行：可选 start（毫秒，仅同步歌词）；文本节点 JSON 字段名 value */
        @Getter
        @Setter
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Line {
            /** 行起始时间（毫秒；仅同步歌词） */
            @JacksonXmlProperty(isAttribute = true)
            private Long start;
            /** 行文本（XML 文本节点；JSON 字段名 value） */
            @JacksonXmlText
            private String value;
        }
    }
}
