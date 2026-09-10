package com.bifrost.domain.entity;

import com.bifrost.domain.enums.ProgressMatchSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 阅读进度（KOSync 协议的 {@code document} 维度，M3-sync T1.1）。
 *
 * <p>一个同步账号对一份文档指纹只有一行（唯一约束），写入即<b>无条件后写覆盖</b>——
 * 协议语义如此（官方 2016 年已删除 202"拒绝旧进度"分支），新旧判断在客户端用它拿到的
 * {@code timestamp} 自行完成。</p>
 *
 * <p>孤儿生命周期（R5/C1）：{@code bookId == null && scanAttemptedAt == null} = 待结算
 * （自动扫描已触发）；{@code bookId == null && scanAttemptedAt != null} = <b>固定孤儿</b>，
 * 不再自动匹配，只能人工绑定。</p>
 *
 * <p>⚠️ 唯一约束必须写<b>物理列名</b>：既有 {@code book} 表的 {@code uk_book_filePath} 写了 Java
 * 属性名，导致索引从未真正创建（见 {@code doc/m3-sync/task/05-延后项.md} §5.11）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "reading_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_progress_account_document",
                columnNames = {"sync_account_id", "document_fingerprint"}),
        indexes = @Index(name = "idx_progress_book", columnList = "book_id"))
public class ReadingProgress extends BaseEntity {

    /** 归属同步账号 */
    @Column(name = "sync_account_id", nullable = false)
    private Long syncAccountId;

    /** 文档指纹（客户端算出的 32 位 hex；宽进 64 字符，服务端不解释其格式） */
    @Column(name = "document_fingerprint", nullable = false, length = 64)
    private String documentFingerprint;

    /** 百分比 0–1（协议字段，原样存储，不做修正） */
    @Column(nullable = false)
    private Double percentage;

    /** 位置串（PDF=页码字符串 / EPUB=CRE XPointer）；<b>字节级原样存储与回显</b> */
    @Column(nullable = false, length = 4096)
    private String progress;

    /** 设备名（客户端自由文本，原样存储与回显） */
    @Column(nullable = false, length = 255)
    private String device;

    /** 设备 ID（客户端生成的 UUID，原样存储与回显） */
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    /** 服务端记录时间（协议 {@code timestamp} 的载体，回显时取秒级 epoch） */
    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    /** 关联图书（null = 未匹配，即可能为孤儿） */
    @Column(name = "book_id")
    private Long bookId;

    /** 孤儿结算时间（非 null = 已尝试过匹配，不再自动重试） */
    @Column(name = "scan_attempted_at")
    private Instant scanAttemptedAt;

    /** 匹配来源：AUTO（自动/重新匹配命中）或 MANUAL（人工绑定） */
    @Enumerated(EnumType.STRING)
    @Column(name = "match_source", length = 16)
    private ProgressMatchSource matchSource;

    /** 人工忽略（孤儿页签默认隐藏） */
    @Column(nullable = false)
    private Boolean ignored = false;
}
