package com.bifrost.domain.repo;

import com.bifrost.domain.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 专辑仓储。
 *
 * <p>聚合键查找使用大小写不敏感匹配（规范化键 = trim + 小写折叠，Q10 find-or-create）。
 * {@link JpaSpecificationExecutor} 供列表类型查询（random/newest/starred/byYear/byGenre 等）。</p>
 */
public interface AlbumRepository extends JpaRepository<Album, Long>, JpaSpecificationExecutor<Album> {

    /** 按聚合键（专辑艺术家 + 标题，忽略大小写）查找 */
    Optional<Album> findByAlbumArtistNameIgnoreCaseAndTitleIgnoreCase(String albumArtistName, String title);

    /** 按聚合键查找（专辑艺术家为空，即"未知艺术家"专辑） */
    Optional<Album> findByAlbumArtistNameIsNullAndTitleIgnoreCase(String title);

    /** 艺术家专辑数（getArtists albumCount） */
    long countByArtistId(Long artistId);

    /** 无署名艺术家（"未知艺术家"）专辑数 */
    long countByArtistIdIsNull();

    /** 搜索：标题或专辑艺术家匹配关键字 */
    @Query("select a from Album a where lower(a.title) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(a.albumArtistName, '')) like lower(concat('%', :q, '%')) order by a.title asc")
    List<Album> searchByKeyword(@Param("q") String keyword);
}
