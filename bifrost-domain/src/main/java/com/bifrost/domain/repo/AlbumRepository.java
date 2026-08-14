package com.bifrost.domain.repo;

import com.bifrost.domain.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 专辑仓储。
 *
 * <p>聚合键查找使用大小写不敏感匹配（规范化键 = trim + 小写折叠，Q10 find-or-create）。</p>
 */
public interface AlbumRepository extends JpaRepository<Album, Long> {

    /** 按聚合键（专辑艺术家 + 标题，忽略大小写）查找 */
    Optional<Album> findByAlbumArtistNameIgnoreCaseAndTitleIgnoreCase(String albumArtistName, String title);
}
