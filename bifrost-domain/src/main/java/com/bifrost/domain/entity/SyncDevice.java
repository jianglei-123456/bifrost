package com.bifrost.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 同步设备（R6：独立设备表）。
 *
 * <p>协议层没有"设备注册"，只有随每次上报捎带的 {@code device} / {@code device_id}；
 * 这张表就是"见到过并记录下来的设备"。<b>不做"踢设备"</b>——协议没有会话，封锁单设备无法生效
 * （Q5 决策），要踢只能改口令。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "sync_device",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_account_device",
                columnNames = {"sync_account_id", "device_id"}))
public class SyncDevice extends BaseEntity {

    /** 归属同步账号 */
    @Column(name = "sync_account_id", nullable = false)
    private Long syncAccountId;

    /** 客户端生成的设备 ID（UUID） */
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    /** 设备名（最近一次上报的值；客户端可改成自由文本） */
    @Column(name = "device_name", nullable = false, length = 255)
    private String deviceName;

    /** 首次见到 */
    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    /** 最近见到 */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /** 累计上报次数 */
    @Column(name = "report_count", nullable = false)
    private Long reportCount = 0L;
}
