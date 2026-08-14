package com.bifrost.domain.entity;

import com.bifrost.domain.enums.ScanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 库根（LibraryRoot）。
 *
 * <p>一个挂载进媒体库的顶层目录，对应 Subsonic 的 musicFolder；
 * 禁用（enabled=false）不参与扫描，其曲目对客户端隐藏。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "library_root")
public class LibraryRoot extends BaseEntity {

    /** 库根名称（Subsonic musicFolder 名） */
    @Column(nullable = false, length = 255)
    private String name;

    /** 库根目录绝对路径（唯一） */
    @Column(nullable = false, unique = true, length = 1024)
    private String path;

    /** 是否启用（禁用不扫描、曲目隐藏） */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 上次扫描完成时间 */
    private Instant lastScanAt;

    /** 扫描状态：IDLE / SCANNING */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScanStatus scanStatus = ScanStatus.IDLE;

    /** 上次扫描统计（新增/更新/缺失/错误，JSON） */
    @Column(length = 1024)
    private String lastScanStats;
}
