package com.bifrost.domain.enums;

/**
 * 用户角色。
 *
 * <p>v1 仅 ADMIN（单管理员）；USER 预留（多用户体系为可增强）。</p>
 */
public enum UserRole {
    /** 管理员（v1 唯一角色） */
    ADMIN,
    /** 普通用户（预留） */
    USER
}
