package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 书签列表（getBookmarks）。
 *
 * <p>结构与《Subsonic_API_参考》§4.14：{@code <bookmarks><bookmark position username comment
 * created changed><entry/></bookmark></bookmarks>}。</p>
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Bookmarks {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "bookmark")
    private List<Bookmark> bookmark;

    /** 书签 */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Bookmark {
        /** 书签位置（原样回显客户端数值） */
        @JacksonXmlProperty(isAttribute = true)
        private Long position;
        /** 书签所属用户 */
        @JacksonXmlProperty(isAttribute = true)
        private String username;
        @JacksonXmlProperty(isAttribute = true)
        private String comment;
        /** ISO-8601 创建时间 */
        @JacksonXmlProperty(isAttribute = true)
        private String created;
        /** ISO-8601 最近修改时间 */
        @JacksonXmlProperty(isAttribute = true)
        private String changed;
        /** 书签曲目（Child） */
        @JacksonXmlProperty(localName = "entry")
        private Child entry;
    }
}
