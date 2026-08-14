package com.bifrost.domain.repo;

import com.bifrost.domain.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 曲目仓储。
 */
public interface TrackRepository extends JpaRepository<Track, Long> {

    /** 按库根查询（扫描用） */
    List<Track> findByLibraryRootId(Long libraryRootId);

    /** 按文件绝对路径查询（唯一） */
    Optional<Track> findByFilePath(String filePath);

    /** 按专辑查询 */
    List<Track> findByAlbumId(Long albumId);

    /** 按艺术家查询 */
    List<Track> findByArtistId(Long artistId);

    /** 指定库根内可见（可用）曲目涉及的艺术家 ID（去重） */
    @Query("select distinct t.artistId from Track t where t.libraryRootId = :rootId and t.isAvailable = true")
    List<Long> findArtistIdsByLibraryRootId(@Param("rootId") Long rootId);

    /** 指定库根内可见曲目涉及的专辑 ID（去重） */
    @Query("select distinct t.albumId from Track t where t.libraryRootId = :rootId and t.isAvailable = true")
    List<Long> findAlbumIdsByLibraryRootId(@Param("rootId") Long rootId);

    /** 指定库根内"未知艺术家"（artistId 为空）曲目涉及的专辑 ID（去重） */
    @Query("select distinct t.albumId from Track t where t.libraryRootId = :rootId and t.isAvailable = true and t.artistId is null")
    List<Long> findUnknownArtistAlbumIdsByLibraryRootId(@Param("rootId") Long rootId);

    /** 至少有一条可见曲目的专辑 ID（去重） */
    @Query("select distinct t.albumId from Track t where t.isAvailable = true and t.albumId is not null")
    List<Long> findVisibleAlbumIds();

    /** 搜索：标题或曲目艺术家匹配关键字（仅可见；可选库根过滤） */
    @Query("select t from Track t where t.isAvailable = true "
            + "and (:rootId is null or t.libraryRootId = :rootId) "
            + "and (lower(t.title) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(t.artistName, '')) like lower(concat('%', :q, '%'))) "
            + "order by t.title asc")
    List<Track> searchByKeyword(@Param("q") String keyword, @Param("rootId") Long rootId);
}
