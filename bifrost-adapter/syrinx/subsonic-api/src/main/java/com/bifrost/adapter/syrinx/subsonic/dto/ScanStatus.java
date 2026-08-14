package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 扫描状态（getScanStatus / startScan）。
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScanStatus {

    /** 是否扫描中 */
    @JacksonXmlProperty(isAttribute = true)
    private boolean scanning;

    /** 已扫描曲目数 */
    @JacksonXmlProperty(isAttribute = true)
    private Long count;
}
