package com.bifrost.adapter.syrinx.subsonic.service;

import com.bifrost.adapter.syrinx.subsonic.SubsonicIds;
import com.bifrost.adapter.syrinx.subsonic.dto.AlbumID3;
import com.bifrost.adapter.syrinx.subsonic.dto.AlbumList;
import com.bifrost.adapter.syrinx.subsonic.dto.AlbumList2;
import com.bifrost.adapter.syrinx.subsonic.dto.ArtistID3;
import com.bifrost.adapter.syrinx.subsonic.dto.Artists;
import com.bifrost.adapter.syrinx.subsonic.dto.Bookmarks;
import com.bifrost.adapter.syrinx.subsonic.dto.Child;
import com.bifrost.adapter.syrinx.subsonic.dto.Directory;
import com.bifrost.adapter.syrinx.subsonic.dto.Indexes;
import com.bifrost.adapter.syrinx.subsonic.dto.Lyrics;
import com.bifrost.adapter.syrinx.subsonic.dto.LyricsList;
import com.bifrost.adapter.syrinx.subsonic.dto.MusicFolders;
import com.bifrost.adapter.syrinx.subsonic.dto.NowPlaying;
import com.bifrost.adapter.syrinx.subsonic.dto.OpenSubsonicExtensions;
import com.bifrost.adapter.syrinx.subsonic.dto.PlayQueue;
import com.bifrost.adapter.syrinx.subsonic.dto.Playlist;
import com.bifrost.adapter.syrinx.subsonic.dto.Playlists;
import com.bifrost.adapter.syrinx.subsonic.dto.RandomSongs;
import com.bifrost.adapter.syrinx.subsonic.dto.ScanStatus;
import com.bifrost.adapter.syrinx.subsonic.dto.SearchResult2;
import com.bifrost.adapter.syrinx.subsonic.dto.SearchResult3;
import com.bifrost.adapter.syrinx.subsonic.dto.Starred;
import com.bifrost.adapter.syrinx.subsonic.dto.Starred2;
import com.bifrost.adapter.syrinx.subsonic.dto.User;
import com.bifrost.adapter.syrinx.subsonic.dto.Users;
import com.bifrost.common.util.Dates;
import com.bifrost.common.util.Strings;
import com.bifrost.core.audio.UnknownAlbum;
import com.bifrost.core.audio.UnknownArtist;
import com.bifrost.core.audio.model.AlbumListType;
import com.bifrost.core.audio.service.LibraryQueryService;
import com.bifrost.core.audio.service.NowPlayingService;
import com.bifrost.core.audio.service.PlaylistService;
import com.bifrost.domain.entity.Album;
import com.bifrost.domain.entity.Artist;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.entity.PlaylistEntry;
import com.bifrost.domain.entity.Track;
import com.bifrost.domain.repo.AlbumRepository;
import com.bifrost.domain.repo.ArtistRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
import com.bifrost.domain.repo.PlaylistEntryRepository;
import com.bifrost.domain.repo.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Subsonic 载荷组装（DTO ← 核心服务；ID 体系 Q15、封面 Q13、收藏时间 Q14、未知艺术家 Q9/Q16）。
 */
@Service
@RequiredArgsConstructor
public class SubsonicAssembler {

    private final LibraryQueryService queryService;
    private final LibraryRootRepository libraryRootRepository;
    private final TrackRepository trackRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final PlaylistService playlistService;
    private final PlaylistEntryRepository playlistEntryRepository;
    private final NowPlayingService nowPlayingService;

    // ---------- 音乐文件夹 ----------

    public MusicFolders buildMusicFolders() {
        MusicFolders folders = new MusicFolders();
        List<MusicFolders.MusicFolder> items = libraryRootRepository.findAllByOrderByIdAsc().stream()
                .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                .map(r -> {
                    MusicFolders.MusicFolder f = new MusicFolders.MusicFolder();
                    f.setId(String.valueOf(r.getId()));
                    f.setName(r.getName());
                    return f;
                })
                .toList();
        folders.setMusicFolder(items);
        return folders;
    }

    // ---------- 索引（getIndexes / getArtists） ----------

    public Indexes buildIndexes(Long rootId) {
        Indexes indexes = new Indexes();
        indexes.setLastModified(lastModifiedMillis(rootId));
        indexes.setIndex(buildIndexGroups(rootId));
        return indexes;
    }

    /** 索引时间戳：最近一次扫描时间（稳定，供 ifModifiedSince 缓存，Q16）。 */
    private Long lastModifiedMillis(Long rootId) {
        List<LibraryRoot> roots = rootId == null
                ? libraryRootRepository.findAllByOrderByIdAsc()
                : libraryRootRepository.findById(rootId).map(List::of).orElse(List.of());
        return roots.stream()
                .map(LibraryRoot::getLastScanAt)
                .filter(java.util.Objects::nonNull)
                .map(Instant::toEpochMilli)
                .max(Long::compareTo)
                .orElse(System.currentTimeMillis());
    }

    public Artists buildArtists(Long rootId) {
        Artists artists = new Artists();
        artists.setIndex(buildIndexGroups(rootId));
        return artists;
    }

    private List<Indexes.Index> buildIndexGroups(Long rootId) {
        return queryService.artistIndex(rootId).stream()
                .map(group -> {
                    Indexes.Index index = new Indexes.Index();
                    index.setName(group.letter());
                    index.setArtist(group.artists().stream().map(item -> {
                        Indexes.ArtistRef ref = new Indexes.ArtistRef();
                        ref.setId(item.unknown() ? SubsonicIds.UNKNOWN_ARTIST_ID : SubsonicIds.artist(item.artistId()));
                        ref.setName(item.name());
                        return ref;
                    }).toList());
                    return index;
                })
                .toList();
    }

    // ---------- 模拟目录树（Q15） ----------

    public Directory buildRootDirectory(LibraryRoot root) {
        Directory directory = new Directory();
        directory.setId(String.valueOf(root.getId()));
        directory.setName(root.getName());
        List<Child> children = new ArrayList<>();
        for (LibraryQueryService.ArtistIndexGroup group : queryService.artistIndex(root.getId())) {
            for (LibraryQueryService.ArtistIndexItem item : group.artists()) {
                children.add(buildArtistDir(item.unknown() ? SubsonicIds.UNKNOWN_ARTIST_ID : SubsonicIds.artist(item.artistId()),
                        item.name(), String.valueOf(root.getId())));
            }
        }
        directory.setChild(children);
        return directory;
    }

    public Directory buildArtistDirectory(Long artistId) {
        var detail = queryService.artistDetail(artistId).orElse(null);
        if (detail == null) {
            return null;
        }
        Directory directory = new Directory();
        directory.setId(SubsonicIds.artist(artistId));
        directory.setName(detail.artist().getName());
        directory.setChild(detail.albums().stream()
                .map(a -> buildAlbumDir(a, detail.artist().getName(), SubsonicIds.artist(artistId)))
                .toList());
        return directory;
    }

    public Directory buildUnknownArtistDirectory() {
        Directory directory = new Directory();
        directory.setId(SubsonicIds.UNKNOWN_ARTIST_ID);
        directory.setName(UnknownArtist.NAME);
        List<Album> albums = albumRepository.findAll().stream()
                .filter(a -> a.getArtistId() == null)
                .sorted(Comparator.comparing(Album::getYear, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(a -> a.getTitle() == null ? "" : a.getTitle()))
                .toList();
        directory.setChild(albums.stream()
                .map(a -> buildAlbumDir(a, UnknownArtist.NAME, SubsonicIds.UNKNOWN_ARTIST_ID))
                .toList());
        return directory;
    }

    public Directory buildAlbumDirectory(Long albumId) {
        var detail = queryService.albumDetail(albumId).orElse(null);
        if (detail == null) {
            return null;
        }
        Directory directory = new Directory();
        directory.setId(SubsonicIds.album(albumId));
        directory.setName(detail.album().getTitle());
        Map<Long, Album> albums = albumRepository.findAll().stream()
                .collect(Collectors.toMap(Album::getId, a -> a));
        directory.setChild(detail.tracks().stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsAvailable()))
                .map(t -> buildSong(t, detail.album()))
                .toList());
        return directory;
    }

    private Child buildArtistDir(String id, String name, String parentId) {
        Child child = new Child();
        child.setId(id);
        child.setParent(parentId);
        child.setIsDir(true);
        child.setTitle(name);
        child.setPath(name);
        return child;
    }

    private Child buildAlbumDir(Album album, String artistName, String parentId) {
        Child child = new Child();
        child.setId(SubsonicIds.album(album.getId()));
        child.setParent(parentId);
        child.setIsDir(true);
        child.setTitle(album.getTitle());
        child.setArtist(artistName);
        child.setPath((artistName == null ? "" : artistName) + "/" + album.getTitle());
        return child;
    }

    // ---------- 曲目 / 专辑 / 艺术家 ----------

    public Child buildSong(Track t, Album album) {
        Child child = new Child();
        child.setId(SubsonicIds.track(t.getId()));
        child.setParent(SubsonicIds.album(t.getAlbumId()));
        child.setIsDir(false);
        child.setTitle(t.getTitle());
        child.setAlbum(album == null ? t.getAlbumArtistName() : album.getTitle());
        child.setArtist(t.getArtistName());
        child.setTrack(t.getTrackNo());
        child.setDiscNumber(t.getDiscNo());
        child.setYear(t.getYear());
        child.setGenre(t.getGenre());
        child.setCoverArt(SubsonicIds.album(t.getAlbumId()));
        child.setSize(t.getFileSize());
        child.setContentType(contentType(t.getFormat()));
        child.setSuffix(t.getFormat());
        child.setDuration(t.getDuration());
        child.setBitRate(t.getBitrate());
        child.setPath(virtualPath(t));
        child.setIsVideo(false);
        child.setUserRating(t.getRating() != null && t.getRating() > 0 ? t.getRating() : null);
        child.setPlayCount(t.getPlayCount());
        child.setCreated(Dates.formatIso(t.getCreatedAt()));
        child.setStarred(Dates.formatIso(t.getStarredAt()));
        child.setAlbumId(SubsonicIds.album(t.getAlbumId()));
        if (t.getArtistId() != null) {
            child.setArtistId(SubsonicIds.artist(t.getArtistId()));
        }
        return child;
    }

    public AlbumID3 buildAlbumID3(Album album, int songCount) {
        AlbumID3 dto = new AlbumID3();
        dto.setId(SubsonicIds.album(album.getId()));
        dto.setName(album.getTitle());
        dto.setArtist(album.getAlbumArtistName());
        if (album.getArtistId() != null) {
            dto.setArtistId(SubsonicIds.artist(album.getArtistId()));
        }
        dto.setCoverArt(SubsonicIds.album(album.getId()));
        dto.setSongCount(songCount);
        dto.setDuration(album.getDuration());
        dto.setPlayCount(album.getPlayCount());
        dto.setYear(album.getYear());
        dto.setGenre(album.getGenre());
        dto.setCreated(Dates.formatIso(album.getCreatedAt()));
        dto.setStarred(Dates.formatIso(album.getStarredAt()));
        return dto;
    }

    public ArtistID3 buildArtistID3(String id, String name, int albumCount) {
        ArtistID3 dto = new ArtistID3();
        dto.setId(id);
        dto.setName(name);
        dto.setAlbumCount(albumCount);
        return dto;
    }

    /** 专辑内曲目排序：discNo → trackNo → 文件名（可见曲目）。 */
    public List<Track> sortedAvailableTracks(Long albumId) {
        return queryService.albumTracksSorted(albumId).stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsAvailable()))
                .toList();
    }

    /** 艺术家详情（ID3 + 专辑列表，按年份缺失排后 → 标题）。 */
    public ArtistID3 buildArtistDetail(Long artistId) {
        var detail = queryService.artistDetail(artistId).orElse(null);
        if (detail == null) {
            return null;
        }
        ArtistID3 dto = buildArtistID3(SubsonicIds.artist(artistId), detail.artist().getName(),
                (int) albumRepository.countByArtistId(artistId));
        dto.setAlbum(detail.albums().stream()
                .map(a -> buildAlbumID3(a, trackRepository.findByAlbumId(a.getId()).size()))
                .toList());
        return dto;
    }

    /** 未知艺术家详情（Q9：虚拟分组，含其专辑）。 */
    public ArtistID3 buildUnknownArtist() {
        List<Album> albums = albumRepository.findAll().stream()
                .filter(a -> a.getArtistId() == null)
                .toList();
        ArtistID3 dto = buildArtistID3(SubsonicIds.UNKNOWN_ARTIST_ID, UnknownArtist.NAME, albums.size());
        dto.setAlbum(albums.stream()
                .sorted(Comparator.comparing(Album::getYear, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(a -> a.getTitle() == null ? "" : a.getTitle()))
                .map(a -> buildAlbumID3(a, trackRepository.findByAlbumId(a.getId()).size()))
                .toList());
        return dto;
    }

    /** 专辑详情（ID3 + 曲目列表）。 */
    public AlbumID3 buildAlbumDetail(Long albumId) {
        var detail = queryService.albumDetail(albumId).orElse(null);
        if (detail == null) {
            return null;
        }
        AlbumID3 dto = buildAlbumID3(detail.album(), sortedAvailableTracks(albumId).size());
        dto.setSong(sortedAvailableTracks(albumId).stream()
                .map(t -> buildSong(t, detail.album()))
                .toList());
        return dto;
    }

    // ---------- 专辑列表 ----------

    public AlbumList buildAlbumList(AlbumListType type, int size, int offset, String genre,
                                    Integer fromYear, Integer toYear, Long rootId) {
        var query = new LibraryQueryService.AlbumListQuery(type, size, offset, genre, fromYear, toYear, rootId, null);
        List<AlbumID3> items = queryService.albumList(query).stream()
                .map(a -> buildAlbumID3(a, trackRepository.findByAlbumId(a.getId()).size()))
                .toList();
        AlbumList list = new AlbumList();
        list.setAlbum(items);
        return list;
    }

    public AlbumList2 buildAlbumList2(AlbumListType type, int size, int offset, String genre,
                                      Integer fromYear, Integer toYear, Long rootId) {
        var query = new LibraryQueryService.AlbumListQuery(type, size, offset, genre, fromYear, toYear, rootId, null);
        List<AlbumID3> items = queryService.albumList(query).stream()
                .map(a -> buildAlbumID3(a, trackRepository.findByAlbumId(a.getId()).size()))
                .toList();
        AlbumList2 list = new AlbumList2();
        list.setAlbum(items);
        return list;
    }

    public RandomSongs buildRandomSongs(int size, String genre, Integer fromYear, Integer toYear, Long rootId) {
        List<Track> candidates = trackRepository.findAll().stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsAvailable()))
                .filter(t -> rootId == null || rootId.equals(t.getLibraryRootId()))
                .filter(t -> genre == null || genre.equalsIgnoreCase(t.getGenre()))
                .filter(t -> fromYear == null || (t.getYear() != null && t.getYear() >= fromYear))
                .filter(t -> toYear == null || (t.getYear() != null && t.getYear() <= toYear))
                .toList();
        List<Track> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new Random());
        Map<Long, Album> albums = albumRepository.findAll().stream()
                .collect(Collectors.toMap(Album::getId, a -> a));
        RandomSongs dto = new RandomSongs();
        dto.setSong(shuffled.stream().limit(Math.max(0, size)).map(t -> buildSong(t, albums.get(t.getAlbumId()))).toList());
        return dto;
    }

    // ---------- 正在播放 ----------

    public NowPlaying buildNowPlaying() {
        Map<Long, Album> albums = albumRepository.findAll().stream()
                .collect(Collectors.toMap(Album::getId, a -> a));
        NowPlaying dto = new NowPlaying();
        List<NowPlaying.Entry> entries = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (NowPlayingService.NowPlayingEntry playing : nowPlayingService.list()) {
            Track t = trackRepository.findById(playing.trackId()).orElse(null);
            if (t == null) {
                continue;
            }
            NowPlaying.Entry entry = new NowPlaying.Entry();
            copyChildFields(entry, buildSong(t, albums.get(t.getAlbumId())));
            entry.setUsername(playing.username());
            entry.setPlayerId(playing.playerId());
            entry.setMinutesAgo((int) Math.max(0, (now - playing.startTime().toEpochMilli()) / 60_000));
            entries.add(entry);
        }
        dto.setEntry(entries);
        return dto;
    }

    private static void copyChildFields(Child target, Child source) {
        target.setId(source.getId());
        target.setParent(source.getParent());
        target.setIsDir(source.getIsDir());
        target.setTitle(source.getTitle());
        target.setAlbum(source.getAlbum());
        target.setArtist(source.getArtist());
        target.setTrack(source.getTrack());
        target.setDiscNumber(source.getDiscNumber());
        target.setYear(source.getYear());
        target.setGenre(source.getGenre());
        target.setCoverArt(source.getCoverArt());
        target.setSize(source.getSize());
        target.setContentType(source.getContentType());
        target.setSuffix(source.getSuffix());
        target.setDuration(source.getDuration());
        target.setBitRate(source.getBitRate());
        target.setPath(source.getPath());
        target.setIsVideo(source.getIsVideo());
        target.setUserRating(source.getUserRating());
        target.setPlayCount(source.getPlayCount());
        target.setCreated(source.getCreated());
        target.setStarred(source.getStarred());
        target.setAlbumId(source.getAlbumId());
        target.setArtistId(source.getArtistId());
    }

    // ---------- 收藏 / 搜索 ----------

    public Starred buildStarred(Long rootId) {
        Starred dto = new Starred();
        fillStarred(dto, rootId, false);
        return dto;
    }

    public Starred2 buildStarred2(Long rootId) {
        Starred2 dto = new Starred2();
        fillStarred(dto, rootId, true);
        return dto;
    }

    private void fillStarred(Object target, Long rootId, boolean id3) {
        Set<Long> artistIds = rootId == null ? null
                : Set.copyOf(trackRepository.findArtistIdsByLibraryRootId(rootId));
        Set<Long> albumIds = rootId == null ? null
                : Set.copyOf(trackRepository.findAlbumIdsByLibraryRootId(rootId));

        List<AlbumID3> albums = albumRepository.findAll().stream()
                .filter(a -> a.getStarredAt() != null)
                .filter(a -> albumIds == null || albumIds.contains(a.getId()))
                .sorted(Comparator.comparing(Album::getStarredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(a -> buildAlbumID3(a, trackRepository.findByAlbumId(a.getId()).size()))
                .toList();
        List<Child> songs = trackRepository.findAll().stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsAvailable()))
                .filter(t -> t.getStarredAt() != null)
                .filter(t -> rootId == null || rootId.equals(t.getLibraryRootId()))
                .map(t -> buildSong(t, albumRepository.findById(t.getAlbumId()).orElse(null)))
                .toList();

        if (id3) {
            Starred2 s = (Starred2) target;
            s.setAlbum(albums);
            s.setSong(songs);
            s.setArtist(artistRepository.findAll().stream()
                    .filter(a -> a.getStarredAt() != null)
                    .filter(a -> artistIds == null || artistIds.contains(a.getId()))
                    .map(a -> buildArtistID3(SubsonicIds.artist(a.getId()), a.getName(),
                            (int) albumRepository.countByArtistId(a.getId())))
                    .toList());
        } else {
            Starred s = (Starred) target;
            s.setAlbum(albums);
            s.setSong(songs);
            s.setArtist(artistRepository.findAll().stream()
                    .filter(a -> a.getStarredAt() != null)
                    .filter(a -> artistIds == null || artistIds.contains(a.getId()))
                    .map(a -> {
                        Indexes.ArtistRef ref = new Indexes.ArtistRef();
                        ref.setId(SubsonicIds.artist(a.getId()));
                        ref.setName(a.getName());
                        return ref;
                    })
                    .toList());
        }
    }

    public SearchResult2 buildSearchResult2(String query, int artistCount, int albumCount, int songCount, Long rootId) {
        LibraryQueryService.SearchResult result = queryService.search(query, artistCount, albumCount, songCount, rootId);
        SearchResult2 dto = new SearchResult2();
        dto.setArtist(result.artists().stream().map(a -> {
            Indexes.ArtistRef ref = new Indexes.ArtistRef();
            ref.setId(SubsonicIds.artist(a.getId()));
            ref.setName(a.getName());
            return ref;
        }).toList());
        dto.setAlbum(result.albums().stream()
                .map(a -> buildAlbumID3(a, trackRepository.findByAlbumId(a.getId()).size()))
                .toList());
        dto.setSong(result.tracks().stream()
                .map(t -> buildSong(t, albumRepository.findById(t.getAlbumId()).orElse(null)))
                .toList());
        return dto;
    }

    public SearchResult3 buildSearchResult3(String query, int artistCount, int albumCount, int songCount, Long rootId) {
        LibraryQueryService.SearchResult result = queryService.search(query, artistCount, albumCount, songCount, rootId);
        SearchResult3 dto = new SearchResult3();
        dto.setArtist(result.artists().stream()
                .map(a -> buildArtistID3(SubsonicIds.artist(a.getId()), a.getName(),
                        (int) albumRepository.countByArtistId(a.getId())))
                .toList());
        dto.setAlbum(result.albums().stream()
                .map(a -> buildAlbumID3(a, trackRepository.findByAlbumId(a.getId()).size()))
                .toList());
        dto.setSong(result.tracks().stream()
                .map(t -> buildSong(t, albumRepository.findById(t.getAlbumId()).orElse(null)))
                .toList());
        return dto;
    }

    // ---------- 歌单 ----------

    public Playlists buildPlaylists() {
        Playlists dto = new Playlists();
        dto.setPlaylist(playlistService.list().stream().map(this::buildPlaylistRef).toList());
        return dto;
    }

    private Playlists.PlaylistRef buildPlaylistRef(com.bifrost.domain.entity.Playlist p) {
        Playlists.PlaylistRef ref = new Playlists.PlaylistRef();
        fillPlaylistCommon(ref, p);
        return ref;
    }

    public Playlist buildPlaylist(com.bifrost.domain.entity.Playlist p, boolean withEntries) {
        Playlist dto = new Playlist();
        Playlists.PlaylistRef ref = new Playlists.PlaylistRef();
        fillPlaylistCommon(ref, p);
        dto.setId(ref.getId());
        dto.setName(ref.getName());
        dto.setOwner(ref.getOwner());
        dto.setIsPublic(ref.getIsPublic());
        dto.setCreated(ref.getCreated());
        dto.setChanged(ref.getChanged());
        dto.setSongCount(ref.getSongCount());
        dto.setDuration(ref.getDuration());
        dto.setComment(ref.getComment());
        if (withEntries) {
            List<Child> entries = new ArrayList<>();
            for (PlaylistEntry e : playlistEntryRepository.findByPlaylistIdOrderByPositionAsc(p.getId())) {
                Track t = trackRepository.findById(e.getTrackId()).orElse(null);
                if (t != null && Boolean.TRUE.equals(t.getIsAvailable())) {
                    entries.add(buildSong(t, albumRepository.findById(t.getAlbumId()).orElse(null)));
                }
            }
            dto.setEntry(entries);
        }
        return dto;
    }

    private void fillPlaylistCommon(Playlists.PlaylistRef ref, com.bifrost.domain.entity.Playlist p) {
        ref.setId(SubsonicIds.playlist(p.getId()));
        ref.setName(p.getName());
        ref.setOwner("admin");
        ref.setIsPublic(p.getIsPublic());
        ref.setCreated(Dates.formatIso(p.getCreatedAt()));
        ref.setChanged(Dates.formatIso(p.getUpdatedAt()));
        ref.setComment(p.getComment());
        List<PlaylistEntry> entries = playlistEntryRepository.findByPlaylistId(p.getId());
        ref.setSongCount(entries.size());
        int duration = 0;
        for (PlaylistEntry e : entries) {
            Track t = trackRepository.findById(e.getTrackId()).orElse(null);
            if (t != null && t.getDuration() != null) {
                duration += t.getDuration();
            }
        }
        ref.setDuration(duration);
    }

    // ---------- 书签 / 播放队列 ----------

    /** 用户书签列表（getBookmarks）；曲目失效的书签不出现在响应。 */
    public Bookmarks buildBookmarks(List<com.bifrost.domain.entity.Bookmark> bookmarks, String username) {
        Bookmarks dto = new Bookmarks();
        List<Bookmarks.Bookmark> items = new ArrayList<>();
        for (var b : bookmarks) {
            Track t = trackRepository.findById(b.getTrackId()).orElse(null);
            if (t == null || !Boolean.TRUE.equals(t.getIsAvailable())) {
                continue; // 曲目缺失则书签隐藏（记录保留）
            }
            Bookmarks.Bookmark item = new Bookmarks.Bookmark();
            item.setPosition(b.getPosition());
            item.setUsername(username);
            item.setComment(b.getComment());
            item.setCreated(Dates.formatIso(b.getCreatedAt()));
            item.setChanged(Dates.formatIso(b.getUpdatedAt()));
            item.setEntry(buildSong(t, albumRepository.findById(t.getAlbumId()).orElse(null)));
            items.add(item);
        }
        dto.setBookmark(items);
        return dto;
    }

    /** 播放队列（getPlayQueue）；无队列时返回空 playQueue（username 外无可回显）。 */
    public PlayQueue buildPlayQueue(com.bifrost.domain.entity.PlayQueue queue,
                                    List<com.bifrost.domain.entity.PlayQueueEntry> entries,
                                    String username) {
        PlayQueue dto = new PlayQueue();
        dto.setUsername(username);
        if (queue != null) {
            dto.setPosition(queue.getPosition());
            dto.setChanged(Dates.formatIso(queue.getUpdatedAt()));
            if (queue.getCurrentTrackId() != null) {
                Track current = trackRepository.findById(queue.getCurrentTrackId()).orElse(null);
                if (current != null && Boolean.TRUE.equals(current.getIsAvailable())) {
                    dto.setCurrent(SubsonicIds.track(current.getId()));
                }
            }
        }
        List<Child> children = new ArrayList<>();
        for (var e : entries == null ? List.<com.bifrost.domain.entity.PlayQueueEntry>of() : entries) {
            Track t = trackRepository.findById(e.getTrackId()).orElse(null);
            if (t != null && Boolean.TRUE.equals(t.getIsAvailable())) {
                children.add(buildSong(t, albumRepository.findById(t.getAlbumId()).orElse(null)));
            }
        }
        dto.setEntry(children);
        return dto;
    }

    // ---------- 歌词（getLyrics / getLyricsBySongId） ----------

    /**
     * 老协议 getLyrics：按 歌手/歌名 大小写不敏感匹配有歌词的曲目。
     * 未命中返回空歌词（status=ok，协议允许），命中返回曲目歌手/歌名与歌词原文。
     */
    public Lyrics buildLyrics(String artist, String title) {
        Lyrics dto = new Lyrics();
        dto.setArtist(artist);
        dto.setTitle(title);
        for (Track t : trackRepository.findAll()) {
            if (!Boolean.TRUE.equals(t.getIsAvailable())
                    || t.getLyrics() == null || t.getLyrics().isBlank()) {
                continue;
            }
            if (lyricsMatch(t, artist, title)) {
                dto.setArtist(t.getArtistName() != null ? t.getArtistName() : artist);
                dto.setTitle(t.getTitle());
                // 原样返回内嵌歌词：含 LRC 时间戳的行保留标签（Musly 等客户端靠解析 [mm:ss] 驱动滚动）
                dto.setValue(t.getLyrics().trim());
                return dto;
            }
        }
        return dto;
    }

    /** getLyricsBySongId（OS songLyrics）：曲目歌词 → structuredLyrics（能解析出时间戳则同步）。 */
    public LyricsList buildLyricsList(Track t) {
        LyricsList dto = new LyricsList();
        List<LyricsList.StructuredLyrics> items = new ArrayList<>();
        String lyrics = t.getLyrics();
        if (lyrics != null && !lyrics.isBlank()) {
            LyricsList.StructuredLyrics s = new LyricsList.StructuredLyrics();
            s.setDisplayArtist(t.getArtistName());
            s.setDisplayTitle(t.getTitle());
            s.setLang("und");
            List<LyricsList.StructuredLyrics.Line> lines = new ArrayList<>();
            List<SyncedLine> synced = parseSyncedLyrics(lyrics);
            if (synced != null) {
                s.setSynced(true);
                for (SyncedLine sl : synced) {
                    LyricsList.StructuredLyrics.Line line = new LyricsList.StructuredLyrics.Line();
                    line.setStart(sl.start());
                    line.setValue(sl.text());
                    lines.add(line);
                }
            } else {
                s.setSynced(false);
                for (String raw : lyrics.split("\\r?\\n")) {
                    String text = raw.trim();
                    if (!text.isEmpty() && !isLrcMetaLine(text)) {
                        LyricsList.StructuredLyrics.Line line = new LyricsList.StructuredLyrics.Line();
                        line.setValue(text);
                        lines.add(line);
                    }
                }
                if (lines.isEmpty()) { // 兜底：整段作为一行
                    LyricsList.StructuredLyrics.Line line = new LyricsList.StructuredLyrics.Line();
                    line.setValue(lyrics.trim());
                    lines.add(line);
                }
            }
            s.setLine(lines);
            items.add(s);
        }
        dto.setStructuredLyrics(items);
        return dto;
    }

    private static boolean lyricsMatch(Track t, String artist, String title) {
        boolean titleOk = title == null || title.isBlank()
                || (t.getTitle() != null && t.getTitle().equalsIgnoreCase(title.trim()));
        boolean artistOk = artist == null || artist.isBlank()
                || (t.getArtistName() != null && t.getArtistName().equalsIgnoreCase(artist.trim()));
        return titleOk && artistOk;
    }

    /** 是否 LRC 元信息行（如 [ar:…] [ti:…] [offset:…]），非同步展示时剔除。 */
    private static boolean isLrcMetaLine(String text) {
        return text.startsWith("[") && text.endsWith("]") && text.indexOf(']') > 1;
    }

    /** LRC 时间标签：{@code [mm:ss[.xx]]} 或 {@code [mm:ss.xxx]}，可一行多标签。 */
    private static final java.util.regex.Pattern LRC_TAG =
            java.util.regex.Pattern.compile("^\\s*\\[([0-9]{1,3}):([0-9]{1,2})(?:[.:]([0-9]{1,3}))?\\]");

    /**
     * 解析同步歌词；无任何时间戳返回 null（调用方按非同步处理）。
     *
     * @return 时间升序的行（start 毫秒）
     */
    private static List<SyncedLine> parseSyncedLyrics(String text) {
        List<SyncedLine> out = new ArrayList<>();
        boolean any = false;
        for (String raw : text.split("\\r?\\n")) {
            String rest = raw;
            List<Long> starts = new ArrayList<>();
            java.util.regex.Matcher m;
            while ((m = LRC_TAG.matcher(rest)).lookingAt()) {
                starts.add(toMillis(m.group(1), m.group(2), m.group(3)));
                rest = rest.substring(m.end());
            }
            if (!starts.isEmpty()) {
                any = true;
            }
            String lyric = rest.trim();
            if (lyric.isEmpty() || starts.isEmpty()) {
                continue;
            }
            for (Long start : starts) {
                out.add(new SyncedLine(start, lyric));
            }
        }
        return any ? out : null;
    }

    /** mm / ss / 分数段 → 毫秒（1 位=百毫秒、2 位=厘秒[×10]、3 位=毫秒）。 */
    private static long toMillis(String mm, String ss, String frac) {
        long ms = Long.parseLong(mm) * 60_000L + Long.parseLong(ss) * 1000L;
        if (frac != null && !frac.isEmpty()) {
            long f = Long.parseLong(frac);
            ms += frac.length() == 1 ? f * 100L : frac.length() == 2 ? f * 10L : f;
        }
        return ms;
    }

    /** 同步歌词行 */
    private record SyncedLine(long start, String text) {
    }

    // ---------- 扫描 / 用户 / 扩展 ----------

    public ScanStatus buildScanStatus() {
        ScanStatus dto = new ScanStatus();
        dto.setScanning(libraryRootRepository.findAll().stream()
                .anyMatch(r -> r.getScanStatus() == com.bifrost.domain.enums.ScanStatus.SCANNING));
        dto.setCount(trackRepository.count());
        return dto;
    }

    public User buildUser(String username) {
        User dto = new User();
        dto.setUsername(username);
        dto.setFolder(libraryRootRepository.findAllByOrderByIdAsc().stream()
                .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                .map(r -> String.valueOf(r.getId()))
                .toList());
        return dto;
    }

    public Users buildUsers(String username) {
        Users dto = new Users();
        dto.setUser(List.of(buildUser(username)));
        return dto;
    }

    public OpenSubsonicExtensions buildOpenSubsonicExtensions() {
        OpenSubsonicExtensions dto = new OpenSubsonicExtensions();
        // songLyrics 扩展版本号为整数 1/2（见 OS 扩展文档）：仅实现 Version 1（line 级同步/多语言），
        // 未实现 Version 2（enhanced/karaoke）故只通告 "1"
        dto.setOpenSubsonicExtension(List.of(ext("songLyrics", "1")));
        return dto;
    }

    private static OpenSubsonicExtensions.Extension ext(String name, String versions) {
        OpenSubsonicExtensions.Extension e = new OpenSubsonicExtensions.Extension();
        e.setName(name);
        e.setVersions(versions);
        return e;
    }

    // ---------- 工具 ----------

    private String virtualPath(Track t) {
        String artist = t.getArtistName() == null ? UnknownArtist.NAME : t.getArtistName();
        String albumTitle = t.getAlbumArtistName() == null ? UnknownAlbum.NAME : Strings.trimToNull(albumTitleOf(t));
        String fileName = String.format("%02d - %s.%s",
                t.getTrackNo() == null ? 1 : t.getTrackNo(),
                t.getTitle() == null ? "" : t.getTitle(),
                t.getFormat() == null ? "mp3" : t.getFormat());
        return artist + "/" + albumTitle + "/" + fileName;
    }

    private String albumTitleOf(Track t) {
        if (t.getAlbumId() == null) {
            return UnknownAlbum.NAME;
        }
        return albumRepository.findById(t.getAlbumId()).map(Album::getTitle).orElse(UnknownAlbum.NAME);
    }

    private static String contentType(String format) {
        if (format == null) {
            return "application/octet-stream";
        }
        return switch (format.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "flac" -> "audio/flac";
            case "m4a" -> "audio/mp4";
            case "wav" -> "audio/wav";
            default -> "application/octet-stream";
        };
    }
}
