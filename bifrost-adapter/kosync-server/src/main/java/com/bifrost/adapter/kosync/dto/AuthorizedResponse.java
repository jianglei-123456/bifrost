package com.bifrost.adapter.kosync.dto;

import com.bifrost.adapter.kosync.KosyncConstants;

/**
 * {@code GET /users/auth} 成功响应。
 *
 * <p>客户端<b>不读响应体</b>，只看状态码 200；字段存在只为与官方响应形状一致。</p>
 */
public record AuthorizedResponse(String authorized) {

    public static AuthorizedResponse ok() {
        return new AuthorizedResponse(KosyncConstants.AUTHORIZED_OK);
    }
}
