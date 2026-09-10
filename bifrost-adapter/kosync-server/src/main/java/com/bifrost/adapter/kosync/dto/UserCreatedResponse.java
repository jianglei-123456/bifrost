package com.bifrost.adapter.kosync.dto;

/**
 * {@code POST /users/create} 成功响应（**必须 201**：客户端注册成功只认 201）。
 */
public record UserCreatedResponse(String username) {
}
