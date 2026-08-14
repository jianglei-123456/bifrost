package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 正在播放（getNowPlaying）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NowPlaying {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "entry")
    private List<Entry> entry;

    /** 正在播放条目 = Child + 播放上下文 */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Entry extends Child {
        /** 用户（v1=admin） */
        @JacksonXmlProperty(isAttribute = true)
        private String username;
        /** 距播放开始的分钟数 */
        @JacksonXmlProperty(isAttribute = true)
        private Integer minutesAgo;
        /** 播放器标识（客户端 c 参数，Q20） */
        @JacksonXmlProperty(isAttribute = true)
        private String playerId;
    }
}
