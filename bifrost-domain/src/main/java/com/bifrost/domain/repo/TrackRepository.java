package com.bifrost.domain.repo;

import com.bifrost.domain.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
