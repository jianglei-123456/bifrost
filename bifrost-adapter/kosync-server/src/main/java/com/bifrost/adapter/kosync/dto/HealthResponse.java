package com.bifrost.adapter.kosync.dto;

import com.bifrost.adapter.kosync.KosyncConstants;

/**
 * {@code GET /healthcheck} 响应（匿名；官方 Docker healthcheck 也打这个端点）。
 */
public record HealthResponse(String state) {

    public static HealthResponse ok() {
        return new HealthResponse(KosyncConstants.HEALTH_OK);
    }
}
