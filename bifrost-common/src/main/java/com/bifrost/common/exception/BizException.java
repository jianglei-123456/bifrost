package com.bifrost.common.exception;

import com.bifrost.common.constant.ErrorCodes;
import lombok.Getter;

/**
 * 业务异常：携带业务错误码，由全局异常处理器转换为统一响应信封。
 *
 * <p>未知异常不抛本类型，由 {@code @RestControllerAdvice} 兜底转为 500/1200。</p>
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务错误码（见 {@link ErrorCodes}） */
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** 参数错误 */
    public static BizException paramError(String message) {
        return new BizException(ErrorCodes.PARAM_ERROR, message);
    }

    /** 资源不存在 */
    public static BizException notFound(String message) {
        return new BizException(ErrorCodes.NOT_FOUND, message);
    }

    /** 未认证 */
    public static BizException unauthorized(String message) {
        return new BizException(ErrorCodes.UNAUTHORIZED, message);
    }

    /** 无权限 */
    public static BizException forbidden(String message) {
        return new BizException(ErrorCodes.FORBIDDEN, message);
    }

    /** 冲突 / 状态非法 */
    public static BizException conflict(String message) {
        return new BizException(ErrorCodes.CONFLICT, message);
    }

    /** 扫描进行中 */
    public static BizException scanInProgress(String message) {
        return new BizException(ErrorCodes.SCAN_IN_PROGRESS, message);
    }

    /** 内部错误 */
    public static BizException internalError(String message) {
        return new BizException(ErrorCodes.INTERNAL_ERROR, message);
    }
}
