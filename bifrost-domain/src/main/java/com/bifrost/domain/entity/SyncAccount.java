package com.bifrost.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 同步账号（阅读进度同步的凭据主体，M3-sync T1.1）。
 *
 * <p>与管理员账号（{@link User}）<b>相互独立</b>：管理员口令不交给阅读设备（Q3-B/Q6-A）。
 * 口令以 AES-GCM 可逆存储——协议凭据是"口令的 md5"，服务端必须能还原明文再算 md5；
 * 管理端也要回显明文（R2a）。</p>
 *
 * <p>Day-one 只有一行；阅读进度按 {@link ReadingProgress#getSyncAccountId()} 隔离，
 * 未来加多账号不改协议层。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "sync_account",
        uniqueConstraints = @UniqueConstraint(name = "uk_sync_account_username", columnNames = "username"))
public class SyncAccount extends BaseEntity {

    /** 同步用户名（非空、唯一、不含 ':'） */
    @Column(nullable = false, length = 64)
    private String username;

    /** 口令（AES-GCM 可逆加密，hex(iv + ciphertext)） */
    @Column(nullable = false, length = 512)
    private String encryptedPassword;
}
