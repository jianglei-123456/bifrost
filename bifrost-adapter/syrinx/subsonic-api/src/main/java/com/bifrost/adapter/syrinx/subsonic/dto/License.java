package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 许可证（getLicense）：第三方服务器恒返回 valid=true。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class License {

    @JacksonXmlProperty(isAttribute = true)
    private boolean valid = true;

    @JacksonXmlProperty(isAttribute = true)
    private String email;

    @JacksonXmlProperty(isAttribute = true)
    private String licenseExpires;

    @JacksonXmlProperty(isAttribute = true)
    private String trialExpires;
}
