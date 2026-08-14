package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 协议错误（<error code message helpUrl/>）。
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Error {

    /** 错误码（0/10/20/30/40/41/42/43/44/50/60/70） */
    @JacksonXmlProperty(isAttribute = true)
    private int code;

    /** 错误信息 */
    @JacksonXmlProperty(isAttribute = true)
    private String message;

    /** 帮助链接（OpenSubsonic 扩展，可选） */
    @JacksonXmlProperty(isAttribute = true)
    private String helpUrl;
}
