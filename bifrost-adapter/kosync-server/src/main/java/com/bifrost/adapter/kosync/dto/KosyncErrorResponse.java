package com.bifrost.adapter.kosync.dto;

/**
 * KOSync 错误响应体：{@code {"code": 3001, "message": "..."}}。
 *
 * <p>注册/登录失败时客户端会把 {@code message} <b>原样显示给用户</b>，所以文案必须是人话；
 * 其它失败只按 HTTP 状态码判定。</p>
 */
public record KosyncErrorResponse(int code, String message) {
}
