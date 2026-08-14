package com.bifrost.adapter.syrinx.subsonic;

/**
 * Subsonic 认证过滤器写入的请求属性名（由 bootstrap 的过滤器与 syrinx 控制器共享）。
 */
public final class SubsonicAuthAttributes {

    /** 认证用户名 */
    public static final String USERNAME = "bifrost.subsonic.username";

    /** 客户端名（c 参数，Q20 playerId） */
    public static final String CLIENT = "bifrost.subsonic.client";

    private SubsonicAuthAttributes() {
    }
}
