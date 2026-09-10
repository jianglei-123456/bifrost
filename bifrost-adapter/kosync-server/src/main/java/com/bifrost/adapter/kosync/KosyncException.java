package com.bifrost.adapter.kosync;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * KOSync 协议异常：HTTP 状态码 + 协议错误码 + 展示给用户的 message（T2.5）。
 *
 * <p>状态码的选择不是随意的——它决定客户端的行为：<b>401 = 不重试</b>（凭据错了，重试无意义）、
 * <b>402 = 可展示的失败</b>（在 {@code expected_status} 里，不会抛异常，message 原样给用户看）、
 * 其它（403/404/409/500…）都会被判为"临时错误"并丢进离线重试队列。</p>
 */
@Getter
public class KosyncException extends RuntimeException {

    /** 协议错误码（见 {@link KosyncConstants}） */
    private final int code;

    /** HTTP 状态码 */
    private final HttpStatus status;

    public KosyncException(int code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /** 3001 / 401：认证失败。 */
    public static KosyncException unauthorized(String message) {
        return new KosyncException(KosyncConstants.CODE_UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
    }

    /** 3002 / 402：账号或注册冲突。 */
    public static KosyncException conflict(String message) {
        return new KosyncException(KosyncConstants.CODE_CONFLICT, HttpStatus.PAYMENT_REQUIRED, message);
    }

    /** 3003 / 403：请求字段非法。 */
    public static KosyncException invalidField(String message) {
        return new KosyncException(KosyncConstants.CODE_INVALID_FIELD, HttpStatus.FORBIDDEN, message);
    }

    /** 3004 / 402：注册已关闭（R2b 的开关）。 */
    public static KosyncException registrationDisabled() {
        return new KosyncException(KosyncConstants.CODE_REGISTRATION_DISABLED,
                HttpStatus.PAYMENT_REQUIRED, "已关闭注册");
    }

    /** 3000 / 502：服务端故障（与官方可观测状态一致）。 */
    public static KosyncException internal(String message) {
        return new KosyncException(KosyncConstants.CODE_INTERNAL, HttpStatus.BAD_GATEWAY, message);
    }
}
