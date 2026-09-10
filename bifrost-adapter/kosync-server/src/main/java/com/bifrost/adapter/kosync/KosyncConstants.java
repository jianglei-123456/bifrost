package com.bifrost.adapter.kosync;

/**
 * KOSync 协议常量（M3-sync T2.1）。
 *
 * <p>端点<b>挂根路径</b>（R1 决策）：与官方一致，设备上只需填 {@code http://<host>:18080}。</p>
 */
public final class KosyncConstants {

    private KosyncConstants() {
    }

    // ---- 路径 ----

    /** 注册 */
    public static final String PATH_USERS_CREATE = "/users/create";
    /** 校验账号 */
    public static final String PATH_USERS_AUTH = "/users/auth";
    /** 上报进度 */
    public static final String PATH_PROGRESS = "/syncs/progress";
    /** 读取进度 */
    public static final String PATH_PROGRESS_DOCUMENT = "/syncs/progress/{document}";
    /** 探活 */
    public static final String PATH_HEALTHCHECK = "/healthcheck";

    /**
     * 安全链匹配模式（T2.2）。
     *
     * <p>⚠️ 必须与控制器同开关：当前项目只有 {@code /api/**}、{@code /rest/**}、{@code /opds/**} 三条
     * {@code SecurityFilterChain}，Spring Boot 的默认链因已存在 SecurityFilterChain Bean 而被抑制——
     * <b>未被任何 securityMatcher 命中的前缀是完全不设防的</b>。</p>
     */
    public static final String[] SECURITY_PATTERNS = {"/users/**", "/syncs/**", "/healthcheck"};

    // ---- 协议错误码（Bifrost 图书保留段 3000+；客户端只按 HTTP 状态码判定，message 仅展示） ----

    /** 服务端故障 → 502 */
    public static final int CODE_INTERNAL = 3000;
    /** 未授权 → 401（唯一不会被客户端重投队列的失败码） */
    public static final int CODE_UNAUTHORIZED = 3001;
    /** 账号/注册冲突 → 402（在客户端 expected_status 里，message 会原样显示） */
    public static final int CODE_CONFLICT = 3002;
    /** 请求字段非法 → 403 */
    public static final int CODE_INVALID_FIELD = 3003;
    /** 注册已关闭 → 402 */
    public static final int CODE_REGISTRATION_DISABLED = 3004;

    // ---- 其它 ----

    /** 文档指纹最大长度（宽进：协议恒为 32 位 hex，服务端不解释其格式） */
    public static final int MAX_DOCUMENT_LENGTH = 64;
    /** 用户名最大长度 */
    public static final int MAX_USERNAME_LENGTH = 64;
    /** 设备名最大长度（仅声明；SQLite 不强制，且不做截断——硬约束 11） */
    public static final int MAX_DEVICE_LENGTH = 255;
    /** 位置串最大长度 */
    public static final int MAX_PROGRESS_LENGTH = 4096;

    /** {@code GET /users/auth} 成功响应体 */
    public static final String AUTHORIZED_OK = "OK";
    /** {@code GET /healthcheck} 成功响应体 */
    public static final String HEALTH_OK = "OK";

    /** 认证请求头 */
    public static final String HEADER_USER = "x-auth-user";
    /** 认证请求头（口令的 md5 小写 hex） */
    public static final String HEADER_KEY = "x-auth-key";
}
