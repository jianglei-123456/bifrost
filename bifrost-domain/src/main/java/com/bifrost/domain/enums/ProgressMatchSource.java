package com.bifrost.domain.enums;

/**
 * 阅读进度与图书的匹配来源（M3-sync T1.1）。
 */
public enum ProgressMatchSource {

    /** 自动匹配（首次上报命中，或自动扫描结算命中，或人工"重新匹配"命中） */
    AUTO,

    /** 人工绑定（孤儿页手工指定图书） */
    MANUAL
}
