package com.bifrost.adapter.syrinx.subsonic;

import com.bifrost.adapter.syrinx.subsonic.dto.AlbumID3;
import com.bifrost.adapter.syrinx.subsonic.dto.ArtistID3;
import com.bifrost.adapter.syrinx.subsonic.dto.Child;
import com.bifrost.adapter.syrinx.subsonic.dto.Directory;
import com.bifrost.adapter.syrinx.subsonic.dto.Indexes;
import com.bifrost.adapter.syrinx.subsonic.dto.SubsonicResponse;
import com.bifrost.adapter.syrinx.subsonic.service.SubsonicAssembler;
import com.bifrost.common.exception.BizException;
import com.bifrost.core.audio.ScanService;
import com.bifrost.core.audio.model.AlbumListType;
import com.bifrost.core.audio.service.AnnotationService;
import com.bifrost.core.audio.service.BookmarkService;
import com.bifrost.core.audio.service.NowPlayingService;
import com.bifrost.core.audio.service.PlayQueueService;
import com.bifrost.core.audio.service.PlaylistService;
import com.bifrost.core.audio.service.ScrobbleService;
import com.bifrost.core.audio.CoverService;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.adapter.syrinx.subsonic.dto.Playlist;
import com.bifrost.domain.entity.Album;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.entity.PlaylistEntry;
import com.bifrost.domain.entity.Track;
import com.bifrost.domain.entity.User;
import com.bifrost.domain.repo.AlbumRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
import com.bifrost.domain.repo.PlaylistEntryRepository;
import com.bifrost.domain.repo.TrackRepository;
import com.bifrost.domain.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Subsonic 协议端点（/rest/**，Q3-A 口径：仅能力矩阵 ✅ 端点；⏳/❌/未知端点 → error 0）。
 *
 * <p>双格式由 {@link SubsonicRenderer} 按 f 参数渲染；认证由 SubsonicAuthenticationFilter 完成
 * （阶段 03）；ID 体系见 {@link SubsonicIds}（Q15）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
public class SubsonicController {

    private final SubsonicAssembler assembler;
    private final SubsonicRenderer renderer;
    private final BifrostProperties properties;
    private final ScanService scanService;
    private final PlaylistService playlistService;
    private final PlaylistEntryRepository playlistEntryRepository;
    private final AnnotationService annotationService;
    private final BookmarkService bookmarkService;
    private final PlayQueueService playQueueService;
    private final ScrobbleService scrobbleService;
    private final NowPlayingService nowPlayingService;
    private final CoverService coverService;
    private final LibraryRootRepository libraryRootRepository;
    private final TrackRepository trackRepository;
    private final AlbumRepository albumRepository;
    private final UserRepository userRepository;

    // ================= System =================

    @GetMapping({"/ping.view", "/ping"})
    public ResponseEntity<String> ping(HttpServletRequest request) {
        return renderer.render(request, ok(request));
    }

    @GetMapping({"/getLicense.view", "/getLicense"})
    public ResponseEntity<String> getLicense(HttpServletRequest request) {
        SubsonicResponse response = ok(request);
        response.setLicense(new com.bifrost.adapter.syrinx.subsonic.dto.License());
        return renderer.render(request, response);
    }

    @GetMapping({"/getOpenSubsonicExtensions.view", "/getOpenSubsonicExtensions"})
    public ResponseEntity<String> getOpenSubsonicExtensions(HttpServletRequest request) {
        SubsonicResponse response = ok(request);
        response.setOpenSubsonicExtensions(assembler.buildOpenSubsonicExtensions());
        return renderer.render(request, response);
    }

    // ================= Browsing =================

    @GetMapping({"/getMusicFolders.view", "/getMusicFolders"})
    public ResponseEntity<String> getMusicFolders(HttpServletRequest request) {
        SubsonicResponse response = ok(request);
        response.setMusicFolders(assembler.buildMusicFolders());
        return renderer.render(request, response);
    }

    @GetMapping({"/getIndexes.view", "/getIndexes"})
    public ResponseEntity<String> getIndexes(HttpServletRequest request,
                                             @RequestParam(required = false) String musicFolderId,
                                             @RequestParam(required = false) Long ifModifiedSince) {
        Long rootId = parseOptionalRoot(musicFolderId);
        Indexes indexes = assembler.buildIndexes(rootId);
        if (ifModifiedSince != null && indexes.getLastModified() != null && ifModifiedSince >= indexes.getLastModified()) {
            Indexes empty = new Indexes();
            empty.setLastModified(indexes.getLastModified());
            indexes = empty; // 未变化 → 空 indexes（Q16）
        }
        SubsonicResponse response = ok(request);
        response.setIndexes(indexes);
        return renderer.render(request, response);
    }

    @GetMapping({"/getMusicDirectory.view", "/getMusicDirectory"})
    public ResponseEntity<String> getMusicDirectory(HttpServletRequest request, @RequestParam String id) {
        Directory directory = resolveDirectory(id);
        if (directory == null) {
            return error(request, 70, "Directory not found");
        }
        SubsonicResponse response = ok(request);
        response.setDirectory(directory);
        return renderer.render(request, response);
    }

    @GetMapping({"/getArtists.view", "/getArtists"})
    public ResponseEntity<String> getArtists(HttpServletRequest request,
                                             @RequestParam(required = false) String musicFolderId) {
        SubsonicResponse response = ok(request);
        response.setArtists(assembler.buildArtists(parseOptionalRoot(musicFolderId)));
        return renderer.render(request, response);
    }

    @GetMapping({"/getArtist.view", "/getArtist"})
    public ResponseEntity<String> getArtist(HttpServletRequest request, @RequestParam String id) {
        ArtistID3 artist;
        if (SubsonicIds.isUnknownArtist(id)) {
            artist = assembler.buildUnknownArtist();
        } else {
            Long artistId = SubsonicIds.parseArtist(id);
            artist = artistId == null ? null : assembler.buildArtistDetail(artistId);
        }
        if (artist == null) {
            return error(request, 70, "Artist not found");
        }
        SubsonicResponse response = ok(request);
        response.setArtist(artist);
        return renderer.render(request, response);
    }

    @GetMapping({"/getAlbum.view", "/getAlbum"})
    public ResponseEntity<String> getAlbum(HttpServletRequest request, @RequestParam String id) {
        Long albumId = SubsonicIds.parseAlbum(id);
        AlbumID3 album = albumId == null ? null : assembler.buildAlbumDetail(albumId);
        if (album == null) {
            return error(request, 70, "Album not found");
        }
        SubsonicResponse response = ok(request);
        response.setAlbum(album);
        return renderer.render(request, response);
    }

    @GetMapping({"/getSong.view", "/getSong"})
    public ResponseEntity<String> getSong(HttpServletRequest request, @RequestParam String id) {
        Long trackId = SubsonicIds.parseTrack(id);
        Track track = trackId == null ? null : trackRepository.findById(trackId).orElse(null);
        if (track == null || !Boolean.TRUE.equals(track.getIsAvailable())) {
            return error(request, 70, "Song not found");
        }
        SubsonicResponse response = ok(request);
        response.setSong(assembler.buildSong(track, albumRepository.findById(track.getAlbumId()).orElse(null)));
        return renderer.render(request, response);
    }

    // ================= Lists =================

    @GetMapping({"/getAlbumList.view", "/getAlbumList"})
    public ResponseEntity<String> getAlbumList(HttpServletRequest request,
                                               @RequestParam String type,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(defaultValue = "0") int offset,
                                               @RequestParam(required = false) Integer fromYear,
                                               @RequestParam(required = false) Integer toYear,
                                               @RequestParam(required = false) String genre,
                                               @RequestParam(required = false) String musicFolderId) {
        AlbumListType listType = parseListType(request, type);
        if (listType == null) {
            return error(request, 10, "Invalid type");
        }
        if (listType == AlbumListType.BY_YEAR && (fromYear == null || toYear == null)) {
            return error(request, 10, "fromYear and toYear are required for type=byYear");
        }
        if (listType == AlbumListType.BY_GENRE && genre == null) {
            return error(request, 10, "genre is required for type=byGenre");
        }
        SubsonicResponse response = ok(request);
        response.setAlbumList(assembler.buildAlbumList(listType, Math.min(Math.max(1, size), 500),
                Math.max(0, offset), genre, fromYear, toYear, parseOptionalRoot(musicFolderId)));
        return renderer.render(request, response);
    }

    @GetMapping({"/getAlbumList2.view", "/getAlbumList2"})
    public ResponseEntity<String> getAlbumList2(HttpServletRequest request,
                                                @RequestParam String type,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(defaultValue = "0") int offset,
                                                @RequestParam(required = false) Integer fromYear,
                                                @RequestParam(required = false) Integer toYear,
                                                @RequestParam(required = false) String genre,
                                                @RequestParam(required = false) String musicFolderId) {
        AlbumListType listType = parseListType(request, type);
        if (listType == null) {
            return error(request, 10, "Invalid type");
        }
        if (listType == AlbumListType.BY_YEAR && (fromYear == null || toYear == null)) {
            return error(request, 10, "fromYear and toYear are required for type=byYear");
        }
        if (listType == AlbumListType.BY_GENRE && genre == null) {
            return error(request, 10, "genre is required for type=byGenre");
        }
        SubsonicResponse response = ok(request);
        response.setAlbumList2(assembler.buildAlbumList2(listType, Math.min(Math.max(1, size), 500),
                Math.max(0, offset), genre, fromYear, toYear, parseOptionalRoot(musicFolderId)));
        return renderer.render(request, response);
    }

    @GetMapping({"/getRandomSongs.view", "/getRandomSongs"})
    public ResponseEntity<String> getRandomSongs(HttpServletRequest request,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(required = false) String genre,
                                                 @RequestParam(required = false) Integer fromYear,
                                                 @RequestParam(required = false) Integer toYear,
                                                 @RequestParam(required = false) String musicFolderId) {
        SubsonicResponse response = ok(request);
        response.setRandomSongs(assembler.buildRandomSongs(Math.min(Math.max(1, size), 500), genre,
                fromYear, toYear, parseOptionalRoot(musicFolderId)));
        return renderer.render(request, response);
    }

    @GetMapping({"/getNowPlaying.view", "/getNowPlaying"})
    public ResponseEntity<String> getNowPlaying(HttpServletRequest request) {
        SubsonicResponse response = ok(request);
        response.setNowPlaying(assembler.buildNowPlaying());
        return renderer.render(request, response);
    }

    @GetMapping({"/getStarred.view", "/getStarred"})
    public ResponseEntity<String> getStarred(HttpServletRequest request,
                                             @RequestParam(required = false) String musicFolderId) {
        SubsonicResponse response = ok(request);
        response.setStarred(assembler.buildStarred(parseOptionalRoot(musicFolderId)));
        return renderer.render(request, response);
    }

    @GetMapping({"/getStarred2.view", "/getStarred2"})
    public ResponseEntity<String> getStarred2(HttpServletRequest request,
                                              @RequestParam(required = false) String musicFolderId) {
        SubsonicResponse response = ok(request);
        response.setStarred2(assembler.buildStarred2(parseOptionalRoot(musicFolderId)));
        return renderer.render(request, response);
    }

    // ================= Searching =================

    @GetMapping({"/search2.view", "/search2"})
    public ResponseEntity<String> search2(HttpServletRequest request,
                                          @RequestParam(required = false) String query,
                                          @RequestParam(defaultValue = "20") int artistCount,
                                          @RequestParam(defaultValue = "0") int artistOffset,
                                          @RequestParam(defaultValue = "20") int albumCount,
                                          @RequestParam(defaultValue = "0") int albumOffset,
                                          @RequestParam(defaultValue = "20") int songCount,
                                          @RequestParam(defaultValue = "0") int songOffset,
                                          @RequestParam(required = false) String musicFolderId) {
        SubsonicResponse response = ok(request);
        response.setSearchResult2(assembler.buildSearchResult2(query, artistCount + artistOffset,
                albumCount + albumOffset, songCount + songOffset, parseOptionalRoot(musicFolderId)));
        return renderer.render(request, response);
    }

    @GetMapping({"/search3.view", "/search3"})
    public ResponseEntity<String> search3(HttpServletRequest request,
                                          @RequestParam(required = false) String query,
                                          @RequestParam(defaultValue = "20") int artistCount,
                                          @RequestParam(defaultValue = "0") int artistOffset,
                                          @RequestParam(defaultValue = "20") int albumCount,
                                          @RequestParam(defaultValue = "0") int albumOffset,
                                          @RequestParam(defaultValue = "20") int songCount,
                                          @RequestParam(defaultValue = "0") int songOffset,
                                          @RequestParam(required = false) String musicFolderId) {
        // OS 澄清：空查询返回全部（离线同步）
        SubsonicResponse response = ok(request);
        response.setSearchResult3(assembler.buildSearchResult3(query, artistCount + artistOffset,
                albumCount + albumOffset, songCount + songOffset, parseOptionalRoot(musicFolderId)));
        return renderer.render(request, response);
    }

    // ================= Playlists =================

    @GetMapping({"/getPlaylists.view", "/getPlaylists"})
    public ResponseEntity<String> getPlaylists(HttpServletRequest request) {
        SubsonicResponse response = ok(request);
        response.setPlaylists(assembler.buildPlaylists());
        return renderer.render(request, response);
    }

    @GetMapping({"/getPlaylist.view", "/getPlaylist"})
    public ResponseEntity<String> getPlaylist(HttpServletRequest request, @RequestParam String id) {
        Long playlistId = SubsonicIds.parsePlaylist(id);
        Playlist playlist = playlistId == null ? null : buildPlaylist(playlistId, true);
        if (playlist == null) {
            return error(request, 70, "Playlist not found");
        }
        SubsonicResponse response = ok(request);
        response.setPlaylist(playlist);
        return renderer.render(request, response);
    }

    @RequestMapping(value = {"/createPlaylist.view", "/createPlaylist"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> createPlaylist(HttpServletRequest request,
                                                 @RequestParam(required = false) String playlistId,
                                                 @RequestParam(required = false) String name,
                                                 @RequestParam(required = false) List<String> songId) {
        List<String> songs = songId == null ? List.of() : songId;
        Long ownerId = currentUser().getId();
        com.bifrost.domain.entity.Playlist playlist;
        if (playlistId != null) {
            Long pid = SubsonicIds.parsePlaylist(playlistId);
            if (pid == null) {
                return error(request, 70, "Playlist not found");
            }
            playlist = playlistService.get(pid).playlist();
            for (String song : songs) {
                addEntryIfValid(pid, song);
            }
        } else {
            if (name == null || name.isBlank()) {
                return error(request, 10, "name is required");
            }
            playlist = playlistService.create(name.trim(), null, ownerId);
            for (String song : songs) {
                addEntryIfValid(playlist.getId(), song);
            }
        }
        SubsonicResponse response = ok(request);
        response.setPlaylist(buildPlaylist(playlist.getId(), true)); // Q20：1.14.0+ 返回 playlist
        return renderer.render(request, response);
    }

    @RequestMapping(value = {"/updatePlaylist.view", "/updatePlaylist"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> updatePlaylist(HttpServletRequest request,
                                                 @RequestParam String playlistId,
                                                 @RequestParam(required = false) String name,
                                                 @RequestParam(required = false) String comment,
                                                 @RequestParam(required = false) Boolean isPublic,
                                                 @RequestParam(required = false) List<String> songIdToAdd,
                                                 @RequestParam(required = false) List<Integer> songIndexToRemove) {
        Long pid = SubsonicIds.parsePlaylist(playlistId);
        if (pid == null) {
            return error(request, 70, "Playlist not found");
        }
        try {
            playlistService.get(pid);
        } catch (BizException e) {
            return error(request, 70, "Playlist not found");
        }
        if (name != null || comment != null) {
            playlistService.update(pid, name, comment);
        }
        if (songIdToAdd != null) {
            for (String song : songIdToAdd) {
                addEntryIfValid(pid, song);
            }
        }
        if (songIndexToRemove != null) {
            List<PlaylistEntry> entries = playlistEntryRepository.findByPlaylistIdOrderByPositionAsc(pid);
            List<Long> toRemove = new ArrayList<>();
            for (Integer index : songIndexToRemove) {
                if (index != null && index >= 0 && index < entries.size()) {
                    toRemove.add(entries.get(index).getId());
                }
            }
            for (Long entryId : toRemove) {
                playlistService.removeEntry(pid, entryId);
            }
        }
        return renderer.render(request, ok(request));
    }

    @RequestMapping(value = {"/deletePlaylist.view", "/deletePlaylist"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> deletePlaylist(HttpServletRequest request, @RequestParam String id) {
        Long pid = SubsonicIds.parsePlaylist(id);
        if (pid == null) {
            return error(request, 70, "Playlist not found");
        }
        try {
            playlistService.delete(pid);
        } catch (BizException e) {
            return error(request, 70, "Playlist not found");
        }
        return renderer.render(request, ok(request));
    }

    // ================= Media =================

    @GetMapping({"/stream.view", "/stream"})
    public ResponseEntity<?> stream(HttpServletRequest request, @RequestParam String id) throws IOException {
        Track track = resolveAvailableTrack(id);
        if (track == null) {
            return renderer.renderBinaryError(SubsonicResponse.failed(apiVersion(), 70, "Track not found"));
        }
        Path file = Path.of(track.getFilePath());
        if (!Files.isRegularFile(file)) {
            return renderer.renderBinaryError(SubsonicResponse.failed(apiVersion(), 70, "Track not found"));
        }
        // nowPlaying 记录（Q20）：stream 开始；不增加 playCount（OS 澄清）
        nowPlayingService.record(track.getId(), track.getTitle(), currentUsername(request),
                currentClient(request), Instant.now());

        long fileSize;
        InputStream in;
        try {
            fileSize = Files.size(file);
            in = Files.newInputStream(file); // 提前打开：IO 错误在响应头提交前暴露，可干净回 XML 70
        } catch (IOException e) {
            log.warn("stream 打开文件失败: {}", file);
            return renderer.renderBinaryError(SubsonicResponse.failed(apiVersion(), 70, "Track not found"));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentType(MediaType.parseMediaType(contentType(track.getFormat())));
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("X-Content-Duration", String.valueOf(track.getDuration() == null ? 0 : track.getDuration()));

        String range = request.getHeader("Range");
        if (range != null && range.startsWith("bytes=")) {
            long[] r = parseRange(range, fileSize);
            long start = r[0];
            long length = r[1] - r[0] + 1;
            headers.add(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + r[1] + "/" + fileSize);
            headers.setContentLength(length);
            in.skipNBytes(start);
            return ResponseEntity.status(206).headers(headers)
                    .body(new InputStreamResource(new LimitedInputStream(in, length)));
        }
        headers.setContentLength(fileSize);
        return ResponseEntity.ok().headers(headers)
                .body(new InputStreamResource(in));
    }

    @GetMapping({"/download.view", "/download"})
    public ResponseEntity<?> download(@RequestParam String id) {
        Track track = resolveAvailableTrack(id);
        if (track == null) {
            return renderer.renderBinaryError(SubsonicResponse.failed(apiVersion(), 70, "Track not found"));
        }
        Path file = Path.of(track.getFilePath());
        if (!Files.isRegularFile(file)) {
            return renderer.renderBinaryError(SubsonicResponse.failed(apiVersion(), 70, "Track not found"));
        }
        InputStream in;
        long fileSize;
        try {
            fileSize = Files.size(file);
            in = Files.newInputStream(file); // 提前打开：IO 错误在响应头提交前暴露
        } catch (IOException e) {
            log.warn("download 打开文件失败: {}", file);
            return renderer.renderBinaryError(SubsonicResponse.failed(apiVersion(), 70, "Track not found"));
        }
        String filename = sanitizeFilename(track.getTitle()) + "." + track.getFormat();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType(track.getFormat())));
        // filename 用 ASCII 兜底，中文经 RFC 5987 filename* 传输（避免 Tomcat 丢弃非 ASCII 头值）
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"track." + track.getFormat() + "\"; filename*=UTF-8''"
                        + urlEncode(filename));
        headers.setContentLength(fileSize);
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(in));
    }

    @GetMapping({"/getCoverArt.view", "/getCoverArt"})
    public ResponseEntity<?> getCoverArt(@RequestParam String id,
                                         @RequestParam(required = false) Integer size) {
        Long albumId = SubsonicIds.parseAlbum(id); // Q13：仅 al- 体系
        Optional<byte[]> data = albumId == null ? Optional.empty() : coverService.coverData(albumId, size);
        if (data.isEmpty()) {
            return renderer.renderBinaryError(SubsonicResponse.failed(apiVersion(), 70, "Cover art not found"));
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(data.get());
    }

    // ================= Annotation =================

    @RequestMapping(value = {"/star.view", "/star"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> star(HttpServletRequest request,
                                       @RequestParam(required = false) List<String> id,
                                       @RequestParam(required = false) List<String> albumId,
                                       @RequestParam(required = false) List<String> artistId) {
        applyStar(request, id, albumId, artistId, true);
        return renderer.render(request, ok(request));
    }

    @RequestMapping(value = {"/unstar.view", "/unstar"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> unstar(HttpServletRequest request,
                                         @RequestParam(required = false) List<String> id,
                                         @RequestParam(required = false) List<String> albumId,
                                         @RequestParam(required = false) List<String> artistId) {
        applyStar(request, id, albumId, artistId, false);
        return renderer.render(request, ok(request));
    }

    private void applyStar(HttpServletRequest request, List<String> ids, List<String> albumIds,
                           List<String> artistIds, boolean starred) {
        for (String value : ids == null ? List.<String>of() : ids) {
            Long trackId = SubsonicIds.parseTrack(value);
            if (trackId != null) {
                if (starred) {
                    annotationService.star(AnnotationService.TYPE_TRACK, trackId);
                } else {
                    annotationService.unstar(AnnotationService.TYPE_TRACK, trackId);
                }
            }
        }
        for (String value : albumIds == null ? List.<String>of() : albumIds) {
            Long albumId = SubsonicIds.parseAlbum(value);
            if (albumId != null) {
                if (starred) {
                    annotationService.star(AnnotationService.TYPE_ALBUM, albumId);
                } else {
                    annotationService.unstar(AnnotationService.TYPE_ALBUM, albumId);
                }
            }
        }
        for (String value : artistIds == null ? List.<String>of() : artistIds) {
            Long artistId = SubsonicIds.parseArtist(value);
            if (artistId != null) {
                if (starred) {
                    annotationService.star(AnnotationService.TYPE_ARTIST, artistId);
                } else {
                    annotationService.unstar(AnnotationService.TYPE_ARTIST, artistId);
                }
            }
        }
    }

    @RequestMapping(value = {"/setRating.view", "/setRating"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> setRating(HttpServletRequest request,
                                            @RequestParam String id,
                                            @RequestParam int rating) {
        Long trackId = SubsonicIds.parseTrack(id);
        if (trackId == null) {
            return error(request, 70, "Song not found");
        }
        annotationService.rate(AnnotationService.TYPE_TRACK, trackId, rating);
        return renderer.render(request, ok(request));
    }

    @RequestMapping(value = {"/scrobble.view", "/scrobble"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> scrobble(HttpServletRequest request,
                                           @RequestParam String id,
                                           @RequestParam(required = false) Long time,
                                           @RequestParam(defaultValue = "true") boolean submission) {
        Long trackId = SubsonicIds.parseTrack(id);
        if (trackId == null) {
            return error(request, 70, "Song not found");
        }
        scrobbleService.scrobble(trackId, time, submission, currentUsername(request), currentClient(request));
        return renderer.render(request, ok(request));
    }

    // ================= Scanning =================

    @GetMapping({"/getScanStatus.view", "/getScanStatus"})
    public ResponseEntity<String> getScanStatus(HttpServletRequest request) {
        SubsonicResponse response = ok(request);
        response.setScanStatus(assembler.buildScanStatus());
        return renderer.render(request, response);
    }

    @RequestMapping(value = {"/startScan.view", "/startScan"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> startScan(HttpServletRequest request,
                                            @RequestParam(defaultValue = "false") boolean fullScan) {
        // 后台触发，立即返回状态（Q 决策：扫描后台执行）；fullScan=true 强制全量重解析（回填歌词等）
        final boolean force = fullScan;
        CompletableFuture.runAsync(() -> {
            try {
                scanService.scanAll(force);
            } catch (Exception e) {
                log.warn("startScan 后台扫描失败", e);
            }
        });
        SubsonicResponse response = ok(request);
        response.setScanStatus(assembler.buildScanStatus());
        return renderer.render(request, response);
    }

    // ================= User =================

    @GetMapping({"/getUser.view", "/getUser"})
    public ResponseEntity<String> getUser(HttpServletRequest request) {
        SubsonicResponse response = ok(request);
        response.setUser(assembler.buildUser(currentUsername(request)));
        return renderer.render(request, response);
    }

    @GetMapping({"/getUsers.view", "/getUsers"})
    public ResponseEntity<String> getUsers(HttpServletRequest request) {
        SubsonicResponse response = ok(request);
        response.setUsers(assembler.buildUsers(currentUsername(request))); // Q19：单用户返回当前用户
        return renderer.render(request, response);
    }

    // ================= Bookmarks / Play Queue（§4.14，Q 增补） =================

    @GetMapping({"/getBookmarks.view", "/getBookmarks"})
    public ResponseEntity<String> getBookmarks(HttpServletRequest request) {
        SubsonicResponse response = ok(request);
        response.setBookmarks(assembler.buildBookmarks(
                bookmarkService.listByUser(currentUser().getId()), currentUsername(request)));
        return renderer.render(request, response);
    }

    @RequestMapping(value = {"/createBookmark.view", "/createBookmark"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> createBookmark(HttpServletRequest request,
                                                 @RequestParam(required = false) String id,
                                                 @RequestParam(required = false) Long position,
                                                 @RequestParam(required = false) String comment) {
        if (id == null) {
            return error(request, 10, "id is required");
        }
        Long trackId = SubsonicIds.parseTrack(id);
        if (!isAvailableTrack(trackId)) {
            return error(request, 70, "Song not found");
        }
        if (position == null) {
            return error(request, 10, "position is required");
        }
        bookmarkService.save(currentUser().getId(), trackId, position, comment);
        return renderer.render(request, ok(request));
    }

    @RequestMapping(value = {"/deleteBookmark.view", "/deleteBookmark"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> deleteBookmark(HttpServletRequest request,
                                                 @RequestParam(required = false) String id) {
        if (id == null) {
            return error(request, 10, "id is required");
        }
        Long trackId = SubsonicIds.parseTrack(id);
        if (trackId == null) {
            return error(request, 70, "Song not found");
        }
        bookmarkService.delete(currentUser().getId(), trackId); // 不存在幂等 ok
        return renderer.render(request, ok(request));
    }

    @GetMapping({"/getPlayQueue.view", "/getPlayQueue"})
    public ResponseEntity<String> getPlayQueue(HttpServletRequest request) {
        SubsonicResponse response = ok(request);
        var view = playQueueService.get(currentUser().getId()).orElse(null);
        response.setPlayQueue(assembler.buildPlayQueue(
                view == null ? null : view.queue(),
                view == null ? List.of() : view.entries(),
                currentUsername(request)));
        return renderer.render(request, response);
    }

    @RequestMapping(value = {"/savePlayQueue.view", "/savePlayQueue"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> savePlayQueue(HttpServletRequest request,
                                                @RequestParam(required = false) List<String> id,
                                                @RequestParam(required = false) String current,
                                                @RequestParam(required = false) Long position) {
        List<Long> trackIds = new ArrayList<>();
        if (id != null) {
            for (String song : id) {
                Long trackId = SubsonicIds.parseTrack(song);
                if (isAvailableTrack(trackId)) {
                    trackIds.add(trackId);
                }
            }
        }
        Long currentTrackId = null;
        if (current != null && !current.isBlank()) {
            Long parsed = SubsonicIds.parseTrack(current);
            currentTrackId = isAvailableTrack(parsed) ? parsed : null; // 当前曲目失效则忽略
        }
        playQueueService.save(currentUser().getId(), trackIds, currentTrackId, position);
        return renderer.render(request, ok(request));
    }

    // ================= Lyrics（歌词：getLyrics / getLyricsBySongId） =================

    @GetMapping({"/getLyrics.view", "/getLyrics"})
    public ResponseEntity<String> getLyrics(HttpServletRequest request,
                                            @RequestParam(required = false) String artist,
                                            @RequestParam(required = false) String title) {
        if ((artist == null || artist.isBlank()) && (title == null || title.isBlank())) {
            return error(request, 10, "artist or title is required");
        }
        SubsonicResponse response = ok(request);
        response.setLyrics(assembler.buildLyrics(artist, title)); // 未命中返回空歌词（ok）
        return renderer.render(request, response);
    }

    @GetMapping({"/getLyricsBySongId.view", "/getLyricsBySongId"})
    public ResponseEntity<String> getLyricsBySongId(HttpServletRequest request,
                                                    @RequestParam(required = false) String id) {
        Track track = id == null ? null : resolveAvailableTrack(id);
        if (track == null) {
            return error(request, 70, "Song not found");
        }
        SubsonicResponse response = ok(request);
        response.setLyricsList(assembler.buildLyricsList(track)); // 无歌词 → 空 lyricsList
        return renderer.render(request, response);
    }

    // ================= 未实现端点兜底（Q3-A） =================

    @GetMapping({"/{method}.view", "/{method}"})
    public ResponseEntity<String> notImplementedGet(HttpServletRequest request) {
        return error(request, 0, "Not implemented");
    }

    @PostMapping({"/{method}.view", "/{method}"})
    public ResponseEntity<String> notImplementedPost(HttpServletRequest request) {
        return error(request, 0, "Not implemented");
    }

    // ================= 工具 =================

    private SubsonicResponse ok(HttpServletRequest request) {
        return SubsonicResponse.ok(apiVersion());
    }

    private ResponseEntity<String> error(HttpServletRequest request, int code, String message) {
        return renderer.render(request, SubsonicResponse.failed(apiVersion(), code, message));
    }

    private String apiVersion() {
        return properties.getSubsonic().getApiVersion();
    }

    private Directory resolveDirectory(String id) {
        Long rootId = SubsonicIds.parseRoot(id);
        if (rootId != null) {
            return libraryRootRepository.findById(rootId).map(assembler::buildRootDirectory).orElse(null);
        }
        if (SubsonicIds.isUnknownArtist(id)) {
            return assembler.buildUnknownArtistDirectory();
        }
        Long artistId = SubsonicIds.parseArtist(id);
        if (artistId != null) {
            return assembler.buildArtistDirectory(artistId);
        }
        Long albumId = SubsonicIds.parseAlbum(id);
        if (albumId != null) {
            return assembler.buildAlbumDirectory(albumId);
        }
        return null;
    }

    private Track resolveAvailableTrack(String id) {
        Long trackId = SubsonicIds.parseTrack(id);
        if (trackId == null) {
            return null;
        }
        Track track = trackRepository.findById(trackId).orElse(null);
        return track != null && Boolean.TRUE.equals(track.getIsAvailable()) ? track : null;
    }

    /** 曲目是否存在且可用（null 安全）。 */
    private boolean isAvailableTrack(Long trackId) {
        return trackId != null && trackRepository.findById(trackId)
                .map(t -> Boolean.TRUE.equals(t.getIsAvailable())).orElse(false);
    }

    private AlbumListType parseListType(HttpServletRequest request, String type) {
        return AlbumListType.fromProtocol(type);
    }

    private Long parseOptionalRoot(String musicFolderId) {
        return musicFolderId == null ? null : SubsonicIds.parseRoot(musicFolderId);
    }

    private Playlist buildPlaylist(Long playlistId, boolean withEntries) {
        try {
            com.bifrost.domain.entity.Playlist playlist = playlistService.get(playlistId).playlist();
            return assembler.buildPlaylist(playlist, withEntries);
        } catch (BizException e) {
            return null;
        }
    }

    private void addEntryIfValid(Long playlistId, String songId) {
        Long trackId = SubsonicIds.parseTrack(songId);
        if (trackId != null && trackRepository.existsById(trackId)) {
            playlistService.addEntry(playlistId, trackId);
        }
    }

    private User currentUser() {
        return userRepository.findByUsername(currentUsername(null)).orElseThrow();
    }

    private String currentUsername(HttpServletRequest request) {
        if (request != null && request.getAttribute(SubsonicAuthAttributes.USERNAME) != null) {
            return (String) request.getAttribute(SubsonicAuthAttributes.USERNAME);
        }
        return "admin";
    }

    private String currentClient(HttpServletRequest request) {
        Object client = request.getAttribute(SubsonicAuthAttributes.CLIENT);
        return client == null ? "unknown" : client.toString();
    }

    private static long[] parseRange(String range, long fileSize) {
        String spec = range.substring("bytes=".length()).trim();
        String[] parts = spec.split("-", 2);
        long start;
        long end;
        try {
            start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0].trim());
            end = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1].trim()) : fileSize - 1;
        } catch (NumberFormatException e) {
            start = 0;
            end = fileSize - 1;
        }
        if (start < 0) {
            start = 0;
        }
        if (end >= fileSize) {
            end = fileSize - 1;
        }
        if (end < start) {
            end = start;
        }
        return new long[]{start, end};
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

    private static String sanitizeFilename(String name) {
        return name == null ? "track" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }

    /** 限制读取长度的输入流（Range 响应用）。 */
    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream in, long remaining) {
            super(in);
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = super.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int n = super.read(b, off, toRead);
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }
    }
}
