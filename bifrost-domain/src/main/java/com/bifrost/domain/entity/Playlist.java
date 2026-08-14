package com.bifrost.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 歌单（Playlist）。
 *
 * <p>v1 所有者恒为 admin、公开恒为 true；comment 为 Q18 增补字段（协议 updatePlaylist comment 参数）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "playlist")
public class Playlist extends BaseEntity {

    /** 歌单名称 */
    @Column(nullable = false, length = 255)
    private String name;

    /** 所有者（User 外键，v1=admin） */
    @Column(nullable = false)
    private Long ownerId;

    /** 是否公开（v1 恒 true；Java 关键字规避，列名 is_public） */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = true;

    /** 备注（Q18 增补；协议 updatePlaylist comment） */
    @Column(length = 1024)
    private String comment;
}
