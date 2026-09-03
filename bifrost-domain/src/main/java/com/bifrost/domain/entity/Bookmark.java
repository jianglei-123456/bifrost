package com.bifrost.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 书签（Bookmark，Subsonic bookmark 系列）。
 *
 * <p>每用户每曲目至多一个书签（唯一约束 user_id+track_id）；createBookmark 重复提交即更新
 * position/comment。position 为客户端上报的原始数值（协议参数单位约定不一：秒/毫秒，
 * 服务端按原样回显不解释，Q 约定见《Subsonic_API_参考》§4.14 扩展）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "bookmark", uniqueConstraints = {
        @UniqueConstraint(name = "uk_bookmark_user_track", columnNames = {"user_id", "track_id"})
})
public class Bookmark extends BaseEntity {

    /** 所属用户（User 外键，v1=admin） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 书签曲目（Track 外键） */
    @Column(name = "track_id", nullable = false)
    private Long trackId;

    /** 书签位置（原样回显，客户端单位自洽） */
    @Column(nullable = false)
    private Long position;

    /** 备注（可选） */
    @Column(length = 1024)
    private String comment;
}
