package com.bifrost.adapter.kosync.dto;

import com.bifrost.domain.entity.ReadingProgress;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code GET /syncs/progress/:document} 响应。
 *
 * <p>未知 document 返回<b>空对象 {@code {}}（HTTP 200）而不是 404</b>：客户端拿到没有
 * {@code percentage} 的对象会友好提示"没找到进度"，404 会变成笼统的报错弹窗（硬约束 7）。
 * 字段名与值都必须原样：{@code progress} 是引擎直接消费的位置串，{@code device}/{@code device_id}
 * 用于客户端判断"这是我自己"。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgressGetResponse(
        String document,
        Double percentage,
        String progress,
        String device,
        @JsonProperty("device_id") String deviceId,
        Long timestamp) {

    /** 未命中：全空 → 序列化为 {@code {}}。 */
    public static ProgressGetResponse notFound() {
        return new ProgressGetResponse(null, null, null, null, null, null);
    }

    public static ProgressGetResponse of(ReadingProgress progress) {
        return new ProgressGetResponse(
                progress.getDocumentFingerprint(),
                progress.getPercentage(),
                progress.getProgress(),
                progress.getDevice(),
                progress.getDeviceId(),
                progress.getReportedAt() == null ? null : progress.getReportedAt().getEpochSecond());
    }
}
