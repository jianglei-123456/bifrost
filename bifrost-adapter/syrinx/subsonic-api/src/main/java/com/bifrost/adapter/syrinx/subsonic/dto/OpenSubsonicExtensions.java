package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * OpenSubsonic 扩展通告（getOpenSubsonicExtensions，免认证）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenSubsonicExtensions {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "openSubsonicExtension")
    private List<Extension> openSubsonicExtension;

    /** 扩展项 */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Extension {
        @JacksonXmlProperty(isAttribute = true)
        private String name;
        @JacksonXmlProperty(isAttribute = true)
        private String versions;
    }
}
