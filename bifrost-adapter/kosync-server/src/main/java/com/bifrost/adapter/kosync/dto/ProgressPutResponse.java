package com.bifrost.adapter.kosync.dto;

/**
 * {@code PUT /syncs/progress} 成功响应。
 *
 * <p>{@code timestamp} 必须是<b>秒级 epoch</b>：客户端用它跟本机翻页时间比新旧，毫秒值会被判成
 * "永远更新"从而导致设备每次拉取都往前跳（调研档 §3 硬约束 3）。</p>
 */
public record ProgressPutResponse(String document, long timestamp) {
}
