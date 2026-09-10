package com.bifrost.adapter.kosync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code PUT /syncs/progress} 请求体。
 *
 * <p>{@code progress} 是<b>字符串</b>（PDF=页码、EPUB=CRE XPointer），服务端原样保存；
 * {@code metadata} 等未知字段被忽略——客户端默认根本不发 metadata（`send_metadata=false`），
 * 即便发了也不作为匹配依据。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProgressPutRequest(
        String document,
        String progress,
        Double percentage,
        String device,
        @JsonProperty("device_id") String deviceId) {
}
