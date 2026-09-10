package com.bifrost.adapter.kosync;

/**
 * 请求属性名（过滤器写入、控制器读取），与 {@code SubsonicAuthAttributes} 同款。
 */
public final class KosyncAuthAttributes {

    /** 已认证的同步账号 id（{@link Long}） */
    public static final String ATTR_SYNC_ACCOUNT_ID = "bifrost.kosync.accountId";

    /** 已认证的同步用户名（{@link String}） */
    public static final String ATTR_SYNC_USERNAME = "bifrost.kosync.username";

    private KosyncAuthAttributes() {
    }
}
