package com.bifrost.core.audio.service;

import com.bifrost.common.util.Strings;
import com.bifrost.core.audio.PinyinIndex;
import com.bifrost.core.audio.UnknownArtist;
import com.bifrost.core.audio.model.AlbumListType;
import com.bifrost.domain.entity.Album;
import com.bifrost.domain.entity.Artist;
import com.bifrost.domain.entity.Track;
import com.bifrost.domain.repo.AlbumRepository;
import com.bifrost.domain.repo.ArtistRepository;
import com.bifrost.domain.repo.TrackRepository;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 媒体库查询服务（管理 REST 与 Subsonic 共用）。
 *
 * <p>负责：艺术家索引分组（含"未知艺术家"虚拟分组，Q9/Q16）、专辑列表类型查询（Q17）、
 * 详情、搜索。缺失文件（isAvailable=false）对浏览/搜索隐藏。</p>
 */
@Service
@RequiredArgsConstructor
public class LibraryQueryService {

    /** 艺术家索引项（unknown=true 表示"未知艺术家"虚拟分组项） */
    public record ArtistIndexItem(Long artistId, String name, long albumCount, Instant starredAt, boolean unknown) {
    }

    /** 索引分组 */
    public record ArtistIndexGroup(String letter, List<ArtistIndexItem> artists) {
    }

    /** 艺术家详情（含专辑，按年份缺失排后 → 标题） */
    public record ArtistDetail(Artist artist, List<Album> albums) {
    }

    /** 专辑详情（含曲目，discNo → trackNo → 文件名） */
    public record AlbumDetail(Album album, List<Track> tracks) {
    }

    /** 搜索结果（艺术家/专辑/曲目） */
    public record SearchResult(List<Artist> artists, List<Album> albums, List<Track> tracks) {
    }

    /** 专辑列表查询参数 */
    public record AlbumListQuery(AlbumListType type, int size, int offset, String genre,
                                 Integer fromYear, Integer toYear, Long libraryRootId, Long artistId) {
    }

    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final TrackRepository trackRepository;

    /**
     * 艺术家索引（字母分组，'#' 最后）；可选按库根过滤。
     */
    public List<ArtistIndexGroup> artistIndex(Long libraryRootId) {
        List<Artist> artists = artistRepository.findAllByOrderByNameAsc();
        Set<Long> allowedIds = libraryRootId == null
                ? null
                : new HashSet<>(trackRepository.findArtistIdsByLibraryRootId(libraryRootId));
        Map<Long, Long> albumCounts = albumRepository.findAll().stream()
                .filter(a -> a.getArtistId() != null)
                .collect(Collectors.groupingBy(Album::getArtistId, Collectors.counting()));

        Map<String, List<ArtistIndexItem>> groups = new LinkedHashMap<>();
        for (Artist a : artists) {
            if (allowedIds != null && !allowedIds.contains(a.getId())) {
                continue;
            }
            String letter = a.getIndexLetter() != null ? a.getIndexLetter() : PinyinIndex.FALLBACK_LETTER;
            groups.computeIfAbsent(letter, k -> new ArrayList<>())
                    .add(new ArtistIndexItem(a.getId(), a.getName(),
                            albumCounts.getOrDefault(a.getId(), 0L), a.getStarredAt(), false));
        }

        // "未知艺术家"虚拟分组（Q9）：无署名曲目的专辑
        List<Long> unknownAlbumIds = libraryRootId == null
                ? albumRepository.findAll().stream().filter(a -> a.getArtistId() == null).map(Album::getId).toList()
                : trackRepository.findUnknownArtistAlbumIdsByLibraryRootId(libraryRootId);
        if (!unknownAlbumIds.isEmpty()) {
            groups.computeIfAbsent(UnknownArtist.LETTER, k -> new ArrayList<>())
                    .add(new ArtistIndexItem(null, UnknownArtist.NAME, unknownAlbumIds.size(), null, true));
        }

        return groups.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, List<ArtistIndexItem>> e) ->
                                e.getKey().equals(PinyinIndex.FALLBACK_LETTER) ? 1 : 0)
                        .thenComparing(Map.Entry::getKey))
                .map(e -> new ArtistIndexGroup(e.getKey(), e.getValue()))
                .toList();
    }

    public Optional<ArtistDetail> artistDetail(Long id) {
        return artistRepository.findById(id).map(artist -> {
            List<Album> albums = albumRepository.findAll().stream()
                    .filter(a -> artist.getId().equals(a.getArtistId()))
                    .sorted(Comparator.comparing(Album::getYear, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Album::getTitle, Comparator.nullsLast(String::compareTo)))
                    .toList();
            return new ArtistDetail(artist, albums);
        });
    }

    public Optional<AlbumDetail> albumDetail(Long id) {
        return albumRepository.findById(id).map(album -> new AlbumDetail(album, albumTracksSorted(id)));
    }

    /** 专辑内曲目排序：discNo → trackNo → 文件名。 */
    public List<Track> albumTracksSorted(Long albumId) {
        return trackRepository.findByAlbumId(albumId).stream()
                .sorted(Comparator.comparing(Track::getDiscNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Track::getTrackNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(t -> t.getTitle() == null ? "" : t.getTitle()))
                .toList();
    }

    /**
     * 专辑列表（按 type 排序/过滤，Q17）。
     */
    public List<Album> albumList(AlbumListQuery q) {
        Specification<Album> spec = albumSpec(q);
        List<Album> all = albumRepository.findAll(spec);
        if (q.type() == AlbumListType.RANDOM) {
            Collections.shuffle(all, new Random());
        } else {
            all.sort(albumComparator(q.type()));
        }
        return all.stream().skip(Math.max(0, q.offset())).limit(Math.max(1, q.size())).toList();
    }

    /** 专辑列表总数（分页用）。 */
    public long countAlbums(AlbumListQuery q) {
        return albumRepository.count(albumSpec(q));
    }

    /** 曲目列表（专辑/艺术家/关键字过滤，可分页）。 */
    public List<Track> trackList(Long albumId, Long artistId, String keyword, int size, int offset) {
        List<Track> tracks;
        if (albumId != null) {
            tracks = albumTracksSorted(albumId);
        } else if (artistId != null) {
            tracks = trackRepository.findByArtistId(artistId);
        } else if (keyword != null) {
            tracks = trackRepository.searchByKeyword(keyword, null);
        } else {
            tracks = trackRepository.findAll();
        }
        tracks = tracks.stream().filter(t -> Boolean.TRUE.equals(t.getIsAvailable())).toList();
        return tracks.stream().skip(Math.max(0, offset)).limit(Math.max(1, size)).toList();
    }

    public Optional<Track> trackDetail(Long id) {
        return trackRepository.findById(id).filter(t -> Boolean.TRUE.equals(t.getIsAvailable()));
    }

    /**
     * 全局搜索（艺术家/专辑/曲目；空查询返回全部，对齐 search3 OS 澄清）。
     */
    public SearchResult search(String query, int artistCount, int albumCount, int songCount, Long libraryRootId) {
        String q = Strings.trimToNull(query);
        List<Artist> artists = q == null
                ? artistRepository.findAllByOrderByNameAsc()
                : artistRepository.findByNameContainingIgnoreCaseOrderByNameAsc(q);
        List<Album> albums = q == null
                ? albumRepository.findAll(Sort.by(Sort.Direction.ASC, "title"))
                : albumRepository.searchByKeyword(q);
        List<Track> tracks = trackRepository.searchByKeyword(q == null ? "" : q, libraryRootId);

        Set<Long> allowedArtistIds = libraryRootId == null
                ? null : new HashSet<>(trackRepository.findArtistIdsByLibraryRootId(libraryRootId));
        Set<Long> allowedAlbumIds = libraryRootId == null
                ? null : new HashSet<>(trackRepository.findAlbumIdsByLibraryRootId(libraryRootId));

        List<Artist> filteredArtists = allowedArtistIds == null ? artists
                : artists.stream().filter(a -> allowedArtistIds.contains(a.getId())).toList();
        List<Album> filteredAlbums = allowedAlbumIds == null ? albums
                : albums.stream().filter(a -> allowedAlbumIds.contains(a.getId())).toList();

        return new SearchResult(
                limit(filteredArtists, artistCount),
                limit(filteredAlbums, albumCount),
                limit(tracks, songCount));
    }

    private static <T> List<T> limit(Collection<T> items, int count) {
        List<T> list = new ArrayList<>(items);
        return list.subList(0, Math.min(Math.max(0, count), list.size()));
    }

    /** 专辑列表过滤条件（可见性 + 库根 + 流派/年份）。 */
    private Specification<Album> albumSpec(AlbumListQuery q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 至少一条可见曲目
            Subquery<Long> visible = query.subquery(Long.class);
            Root<Track> vt = visible.from(Track.class);
            visible.select(vt.get("albumId")).where(cb.equal(vt.get("isAvailable"), true));
            predicates.add(root.get("id").in(visible));
            // 库根过滤
            if (q.libraryRootId() != null) {
                Subquery<Long> inRoot = query.subquery(Long.class);
                Root<Track> rt = inRoot.from(Track.class);
                inRoot.select(rt.get("albumId")).where(cb.and(
                        cb.equal(rt.get("libraryRootId"), q.libraryRootId()),
                        cb.equal(rt.get("isAvailable"), true)));
                predicates.add(root.get("id").in(inRoot));
            }
            if (q.genre() != null) {
                predicates.add(cb.equal(root.get("genre"), q.genre()));
            }
            if (q.artistId() != null) {
                predicates.add(cb.equal(root.get("artistId"), q.artistId()));
            }
            if (q.fromYear() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("year").as(Integer.class), q.fromYear()));
            }
            if (q.toYear() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("year").as(Integer.class), q.toYear()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 专辑列表排序（null 安全，year 缺失排后）。 */
    private Comparator<Album> albumComparator(AlbumListType type) {
        Comparator<Album> yearDesc = Comparator.comparing(Album::getYear,
                Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<Album> titleAsc = Comparator.comparing(a -> a.getTitle() == null ? "" : a.getTitle());
        Comparator<Album> artistAsc = Comparator.comparing(a -> a.getAlbumArtistName() == null ? "" : a.getAlbumArtistName());
        return switch (type) {
            case NEWEST, BY_YEAR -> yearDesc.thenComparing(titleAsc);
            case HIGHEST -> Comparator.comparing(Album::getRating, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(titleAsc);
            case FREQUENT -> Comparator.comparing(Album::getPlayCount, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(titleAsc);
            case RECENT -> Comparator.comparing(Album::getLastPlayed, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(titleAsc);
            case ALPHABETICAL_BY_NAME -> titleAsc;
            case ALPHABETICAL_BY_ARTIST -> artistAsc.thenComparing(titleAsc);
            case STARRED -> Comparator.comparing(Album::getStarredAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case BY_GENRE -> yearDesc.thenComparing(titleAsc);
            case RANDOM -> titleAsc; // 随机在调用方 shuffle，此处占位
        };
    }
}
