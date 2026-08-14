package com.bifrost.core.audio;

import com.bifrost.common.util.Strings;
import com.bifrost.core.audio.model.TagResult;
import com.bifrost.domain.entity.Album;
import com.bifrost.domain.entity.Artist;
import com.bifrost.domain.entity.Track;
import com.bifrost.domain.repo.AlbumRepository;
import com.bifrost.domain.repo.ArtistRepository;
import com.bifrost.domain.repo.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 聚合服务：艺术家/专辑 find-or-create 与专辑聚合字段刷新（Q10）。
 *
 * <p>同名专辑跨库根合并为同一实体；Album 无 DB 唯一约束，由本服务保证不重复。
 * 无署名曲目不创建 Artist（Q9，归"未知艺术家"虚拟分组）。</p>
 */
@Service
@RequiredArgsConstructor
public class AggregationService {

    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final TrackRepository trackRepository;

    /**
     * 按名称查找或创建艺术家（全局唯一，indexLetter 随聚合计算入库）。
     *
     * @return 艺术家实体；名称为空返回 null（"未知艺术家"场景，Q9）
     */
    public Artist findOrCreateArtist(String name) {
        String trimmed = Strings.trimToNull(name);
        if (trimmed == null) {
            return null;
        }
        return artistRepository.findByName(trimmed).orElseGet(() -> {
            Artist artist = new Artist();
            artist.setName(trimmed);
            artist.setIndexLetter(PinyinIndex.indexLetter(trimmed));
            return artistRepository.save(artist);
        });
    }

    /**
     * 按聚合键查找或创建专辑（Q10）。
     *
     * @param albumArtistName 专辑艺术家名称（已由解析层回退为 track artist，可空）
     * @param title           专辑标题（可空；空值兜底为"未知专辑"，与"未知艺术家"对称）
     * @param trackArtistId   曲目艺术家 ID（专辑 artistId 兜底来源）
     * @return 专辑实体（复用或新建）
     */
    public Album findOrCreateAlbum(String albumArtistName, String title, Long trackArtistId) {
        String artist = Strings.trimToNull(albumArtistName);
        String albumTitle = Strings.trimToNull(title);
        if (albumTitle == null) {
            albumTitle = UnknownAlbum.NAME; // 无专辑标签 → "未知专辑"（稳定聚合键）
        }
        Optional<Album> existing = artist == null
                ? albumRepository.findByAlbumArtistNameIsNullAndTitleIgnoreCase(albumTitle)
                : albumRepository.findByAlbumArtistNameIgnoreCaseAndTitleIgnoreCase(artist, albumTitle);
        if (existing.isPresent()) {
            return existing.get();
        }
        Album album = new Album();
        album.setTitle(albumTitle);
        album.setAlbumArtistName(artist);
        // 专辑艺术家：albumArtist 对应 Artist；缺失时以首个曲目艺术家聚合（可空）
        album.setArtistId(artist != null ? findOrCreateArtist(artist).getId() : trackArtistId);
        return albumRepository.save(album);
    }

    /** 专辑内曲目排序：discNo → trackNo → 文件名。 */
    public List<Track> albumTracksSorted(Long albumId) {
        return trackRepository.findByAlbumId(albumId).stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(nvl(a.getDiscNo()), nvl(b.getDiscNo()));
                    if (c != 0) return c;
                    c = Integer.compare(nvl(a.getTrackNo()), nvl(b.getTrackNo()));
                    if (c != 0) return c;
                    return String.valueOf(a.getTitle()).compareTo(String.valueOf(b.getTitle()));
                })
                .toList();
    }

    /**
     * 增量刷新专辑聚合字段（仅本次扫描涉及的专辑）：总时长、播放次数、最后播放、年份、流派。
     */
    public void refreshAlbumAggregates(Collection<Long> albumIds) {
        if (albumIds == null || albumIds.isEmpty()) {
            return;
        }
        for (Long albumId : albumIds) {
            albumRepository.findById(albumId).ifPresent(this::refreshAlbum);
        }
    }

    private void refreshAlbum(Album album) {
        List<Track> tracks = trackRepository.findByAlbumId(album.getId());
        int duration = 0;
        int playCount = 0;
        Integer year = null;
        String genre = null;
        java.time.Instant lastPlayed = null;
        for (Track t : tracks) {
            duration += nvl(t.getDuration());
            playCount += nvl(t.getPlayCount());
            if (year == null) year = t.getYear();
            if (genre == null) genre = t.getGenre();
            if (t.getLastPlayed() != null && (lastPlayed == null || t.getLastPlayed().isAfter(lastPlayed))) {
                lastPlayed = t.getLastPlayed();
            }
        }
        album.setDuration(duration);
        album.setPlayCount(playCount);
        album.setYear(year);
        album.setGenre(genre);
        album.setLastPlayed(lastPlayed);
        albumRepository.save(album);
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
