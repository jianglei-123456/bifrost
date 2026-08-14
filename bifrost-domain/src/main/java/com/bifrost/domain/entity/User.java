package com.bifrost.domain.entity;

import com.bifrost.domain.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户（User）。
 *
 * <p>v1 单管理员（username 默认 admin）；表结构预留多用户。
 * 口令以 AES-GCM 可逆加密存储（因 Subsonic 令牌验证须还原明文）。
 * 按 Q18 不增加 email 字段（协议响应省略 email 属性）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "user")
public class User extends BaseEntity {

    /** 用户名（唯一；v1 单管理员 admin） */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** 口令密文（AES-GCM，见《音乐管理技术设计》§9） */
    @Column(nullable = false, length = 512)
    private String encryptedPassword;

    /** 角色（v1: ADMIN；预留 USER） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role = UserRole.ADMIN;
}
