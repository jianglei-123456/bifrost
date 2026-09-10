package com.bifrost.adapter.kosync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code POST /users/create} 请求体。
 *
 * <p>{@code password} <b>已经是口令的 md5 小写 hex</b>（客户端在设备上算好，`main.lua:568`）。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateUserRequest(String username, String password) {
}
