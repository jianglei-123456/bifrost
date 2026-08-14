package com.bifrost.api.response;

/**
 * 管理 REST 统一响应信封 {@code {code, message, data}}。
 *
 * <p>code=0 成功；非 0 为业务错误码（见 {@code ErrorCodes}）。《通用功能说明》§4.1。</p>
 *
 * @param code    业务错误码（0=成功）
 * @param message 人类可读信息
 * @param data    业务数据（成功时）
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
