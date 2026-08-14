package com.bifrost.common.constant;

/**
 * 业务错误码（管理 REST 统一信封 {@code code} 字段，0 = 成功）。
 *
 * <p>约定见《通用功能说明》§4.1：1000 参数错误、1001 资源不存在、1002 未认证、
 * 1003 无权限、1004 冲突/状态非法、1100 扫描进行中、1200 内部错误；
 * 2000+ 为音乐领域预留、3000+ 为视频/图书领域预留。</p>
 */
public final class ErrorCodes {

    /** 成功 */
    public static final int OK = 0;

    /** 参数错误 */
    public static final int PARAM_ERROR = 1000;

    /** 资源不存在 */
    public static final int NOT_FOUND = 1001;

    /** 未认证 */
    public static final int UNAUTHORIZED = 1002;

    /** 无权限 */
    public static final int FORBIDDEN = 1003;

    /** 冲突 / 状态非法 */
    public static final int CONFLICT = 1004;

    /** 扫描进行中 */
    public static final int SCAN_IN_PROGRESS = 1100;

    /** 内部错误 */
    public static final int INTERNAL_ERROR = 1200;

    private ErrorCodes() {
    }
}
