package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 播放队列（getPlayQueue）。
 *
 * <p>结构与《Subsonic_API_参考》§4.14：{@code <playQueue current position username changed>
 * <entry/></playQueue>}。</p>
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlayQueue {

    /** 当前曲目 ID（tr- 前缀；可空） */
    @JacksonXmlProperty(isAttribute = true)
    private String current;

    /** 当前曲目内位置（原样回显；可空） */
    @JacksonXmlProperty(isAttribute = true)
    private Long position;

    /** 队列所属用户 */
    @JacksonXmlProperty(isAttribute = true)
    private String username;

    /** ISO-8601 最近保存时间 */
    @JacksonXmlProperty(isAttribute = true)
    private String changed;

    /** 队列曲目（按提交顺序） */
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "entry")
    private List<Child> entry;
}
