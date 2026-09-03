package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 歌词（getLyrics，Subsonic 1.2.0 老协议）。
 *
 * <p>XML：{@code <lyrics artist title><lyric>…</lyric></lyrics>}；JSON 中歌词文本字段为
 * {@code value}（OpenSubsonic 命名），与 XML 的 {@code <lyric>} 同名异构。</p>
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Lyrics {

    /** 歌手名（回显/匹配结果） */
    @JacksonXmlProperty(isAttribute = true)
    private String artist;

    /** 歌名（回显/匹配结果） */
    @JacksonXmlProperty(isAttribute = true)
    private String title;

    /** 歌词文本（可含换行；XML 元素名 lyric，JSON 字段名 value） */
    @JacksonXmlProperty(localName = "lyric")
    private String value;
}
