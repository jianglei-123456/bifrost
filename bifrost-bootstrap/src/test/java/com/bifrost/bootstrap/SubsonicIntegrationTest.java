package com.bifrost.bootstrap;

import com.bifrost.core.audio.ScanService;
import com.bifrost.core.security.PasswordCipher;
import com.bifrost.core.security.SubsonicTokenUtil;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.repo.AlbumRepository;
import com.bifrost.domain.repo.ArtistRepository;
import com.bifrost.domain.repo.BookmarkRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
import com.bifrost.domain.repo.PlayQueueEntryRepository;
import com.bifrost.domain.repo.PlayQueueRepository;
import com.bifrost.domain.repo.PlaylistEntryRepository;
import com.bifrost.domain.repo.PlaylistRepository;
import com.bifrost.domain.repo.TrackRepository;
import com.bifrost.domain.repo.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 05 Subsonic 服务（Syrinx）集成测试：浏览/目录树/列表/搜索/歌单/标注/流媒体/封面/扫描/用户/书签/播放队列。
 */
@SpringBootTest(properties = {
        "bifrost.db.path=target/test-data/subsonic-test.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=testpass"})
@AutoConfigureMockMvc
class SubsonicIntegrationTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "testpass";
    private static final String SALT = "abcdef";
    private static final String TOKEN = SubsonicTokenUtil.token(PASSWORD, SALT);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ScanService scanService;
    @Autowired
    private LibraryRootRepository libraryRootRepository;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private AlbumRepository albumRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private PlaylistEntryRepository playlistEntryRepository;
    @Autowired
    private BookmarkRepository bookmarkRepository;
    @Autowired
    private PlayQueueRepository playQueueRepository;
    @Autowired
    private PlayQueueEntryRepository playQueueEntryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordCipher passwordCipher;
    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    Path musicDir;

    private Long rootId;

    @BeforeEach
    void setUp() throws Exception {
        bookmarkRepository.deleteAll();
        playQueueEntryRepository.deleteAll();
        playQueueRepository.deleteAll();
        playlistEntryRepository.deleteAll();
        playlistRepository.deleteAll();
        trackRepository.deleteAll();
        albumRepository.deleteAll();
        artistRepository.deleteAll();
        libraryRootRepository.deleteAll();
        userRepository.deleteAll();
        var admin = new com.bifrost.domain.entity.User();
        admin.setUsername(USERNAME);
        admin.setEncryptedPassword(passwordCipher.encrypt(PASSWORD));
        userRepository.save(admin);

        createSampleLibrary();
        LibraryRoot root = new LibraryRoot();
        root.setName("测试库");
        root.setPath(musicDir.toAbsolutePath().normalize().toString());
        root.setEnabled(true);
        rootId = libraryRootRepository.save(root).getId();
        scanService.scanRoot(rootId);
    }

    @Test
    void pingLicenseAndExtensions() throws Exception {
        mockMvc.perform(base("/rest/ping.view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/getLicense.view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.license.valid").value(true));
        mockMvc.perform(base("/rest/getOpenSubsonicExtensions.view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    void endpointsAcceptPathWithoutViewSuffix() throws Exception {
        // 协议端点带/不带 .view 后缀均可用（Navidrome/gonic 同款兼容）
        mockMvc.perform(base("/rest/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/getMusicFolders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.musicFolders.musicFolder[0].name").value("测试库"));
        mockMvc.perform(base("/rest/getOpenSubsonicExtensions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        // 未实现端点无后缀同样返回 error 0（而非 404）
        mockMvc.perform(base("/rest/getGenres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(0));
    }

    @Test
    void musicFoldersAndIndexes() throws Exception {
        mockMvc.perform(base("/rest/getMusicFolders.view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.musicFolders.musicFolder[0].name").value("测试库"));
        mockMvc.perform(base("/rest/getIndexes.view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.indexes.index[?(@.name=='Z')].artist[0].name").value("周杰伦"))
                .andExpect(jsonPath("$.subsonic-response.indexes.index[?(@.name=='#')].artist[0].name")
                        .value("未知艺术家"));
    }

    @Test
    void directoryTreeNavigation() throws Exception {
        // 根 → 艺术家目录
        JsonNode root = json(call("/rest/getMusicDirectory.view", "id", String.valueOf(rootId)));
        String jayArtistId = findChildId(root, "周杰伦");
        assertTrue(jayArtistId.startsWith("ar-"));
        // 艺术家目录 → 专辑目录
        JsonNode artistDir = json(call("/rest/getMusicDirectory.view", "id", jayArtistId));
        String albumId = findChildId(artistDir, "叶惠美");
        assertTrue(albumId.startsWith("al-"));
        // 专辑目录 → 曲目
        JsonNode albumDir = json(call("/rest/getMusicDirectory.view", "id", albumId));
        String trackId = findChildId(albumDir, "以父之名");
        assertTrue(trackId.startsWith("tr-"));
        // 未知艺术家目录可导航
        JsonNode unknown = json(call("/rest/getMusicDirectory.view", "id", "ar-unknown"));
        assertTrue(findFirstChildId(unknown).startsWith("al-"));
    }

    @Test
    void artistsAlbumsSongs() throws Exception {
        JsonNode artists = json(call("/rest/getArtists.view", "musicFolderId", String.valueOf(rootId)));
        String artistId = artists.at("/subsonic-response/artists/index/0/artist/0/id").asText();
        JsonNode artist = json(call("/rest/getArtist.view", "id", artistId));
        assertTrue(artist.at("/subsonic-response/artist/album").size() >= 1);

        JsonNode albums = json(call("/rest/getAlbumList2.view", "type", "newest"));
        assertTrue(albums.at("/subsonic-response/albumList2/album").size() >= 1);
        String albumId = albums.at("/subsonic-response/albumList2/album/0/id").asText();

        JsonNode album = json(call("/rest/getAlbum.view", "id", albumId));
        assertTrue(album.at("/subsonic-response/album/song").size() >= 1);
        String songId = album.at("/subsonic-response/album/song/0/id").asText();

        JsonNode song = json(call("/rest/getSong.view", "id", songId));
        assertTrue(song.at("/subsonic-response/song/id").asText().equals(songId));
    }

    @Test
    void albumListByGenreRequiresParam() throws Exception {
        mockMvc.perform(base("/rest/getAlbumList.view").param("type", "byGenre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(10));
    }

    @Test
    void search3EmptyQueryReturnsAll() throws Exception {
        mockMvc.perform(base("/rest/search3.view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.searchResult3.artist", hasSize(2)));
    }

    @Test
    void playlistLifecycle() throws Exception {
        String trackId = firstTrackId();
        // createPlaylist 返回 playlist（Q20）
        MvcResult created = mockMvc.perform(base("/rest/createPlaylist.view")
                        .param("name", "最爱").param("songId", trackId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.playlist.name").value("最爱"))
                .andReturn();
        String playlistId = objectMapper.readTree(created.getResponse().getContentAsString())
                .at("/subsonic-response/playlist/id").asText();
        assertTrue(playlistId.startsWith("pl-"));

        mockMvc.perform(base("/rest/getPlaylists.view"))
                .andExpect(jsonPath("$.subsonic-response.playlists.playlist[0].id").value(playlistId));
        mockMvc.perform(base("/rest/getPlaylist.view").param("id", playlistId))
                .andExpect(jsonPath("$.subsonic-response.playlist.entry", hasSize(1)));

        String track2 = secondTrackId();
        mockMvc.perform(base("/rest/updatePlaylist.view")
                        .param("playlistId", playlistId).param("songIdToAdd", track2)
                        .param("songIndexToRemove", "0"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/deletePlaylist.view").param("id", playlistId))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/getPlaylists.view"))
                .andExpect(jsonPath("$.subsonic-response.playlists.playlist", hasSize(0)));
    }

    /** 写端点接受 POST 表单（OpenSubsonic formPost / DSub 系客户端行为）。 */
    @Test
    void writeEndpointsAcceptPostForm() throws Exception {
        String trackId = firstTrackId();
        String auth = "u=" + USERNAME + "&t=" + TOKEN + "&s=" + SALT + "&v=1.16.1&c=test&f=json";
        // POST createPlaylist（此前只有 GET，POST 会误落入兜底 error 0 → 表现为"未实现"）
        MvcResult created = mockMvc.perform(post("/rest/createPlaylist.view")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(auth + "&name=POSTList&songId=" + trackId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.playlist.name").value("POSTList"))
                .andReturn();
        String playlistId = objectMapper.readTree(created.getResponse().getContentAsString())
                .at("/subsonic-response/playlist/id").asText();
        // POST deletePlaylist
        mockMvc.perform(post("/rest/deletePlaylist.view")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(auth + "&id=" + playlistId))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        // POST createBookmark / deleteBookmark
        mockMvc.perform(post("/rest/createBookmark.view")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(auth + "&id=" + trackId + "&position=77"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(post("/rest/deleteBookmark.view")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(auth + "&id=" + trackId))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        // 未实现端点 POST 仍由兜底返回 error 0（不回退成 404）
        mockMvc.perform(post("/rest/getGenres.view")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(auth))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(0));
    }

    @Test
    void annotationAndScrobble() throws Exception {
        String trackId = firstTrackId();
        String albumId = json(call("/rest/getAlbumList2.view", "type", "newest"))
                .at("/subsonic-response/albumList2/album/0/id").asText();
        String artistId = json(call("/rest/getArtists.view", "musicFolderId", String.valueOf(rootId)))
                .at("/subsonic-response/artists/index/0/artist/0/id").asText();

        mockMvc.perform(base("/rest/star.view")
                        .param("albumId", albumId).param("artistId", artistId).param("id", trackId))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/getStarred2.view"))
                .andExpect(jsonPath("$.subsonic-response.starred2.album", hasSize(1)))
                .andExpect(jsonPath("$.subsonic-response.starred2.artist", hasSize(1)));

        mockMvc.perform(base("/rest/setRating.view").param("id", trackId).param("rating", "5"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/scrobble.view").param("id", trackId).param("submission", "true"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/getSong.view").param("id", trackId))
                .andExpect(jsonPath("$.subsonic-response.song.playCount").value(1));
    }

    @Test
    void streamSupportsRangeAndDoesNotCount() throws Exception {
        String trackId = songIdInAlbum(albumIdByName("叶惠美"), 0); // mp3 曲目
        mockMvc.perform(base("/rest/stream.view").param("id", trackId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("audio/mpeg")))
                .andExpect(header().string("Accept-Ranges", "bytes"));
        mockMvc.perform(base("/rest/stream.view").param("id", trackId)
                        .header("Range", "bytes=0-99"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", containsString("bytes 0-99/")))
                .andExpect(header().string("Content-Length", "100"));
        mockMvc.perform(base("/rest/getSong.view").param("id", trackId))
                .andExpect(jsonPath("$.subsonic-response.song.playCount").value(0)); // stream 不计数
        mockMvc.perform(base("/rest/stream.view").param("id", "tr-999999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/xml")))
                .andExpect(content().string(containsString("code=\"70\"")));
    }

    @Test
    void downloadAndCoverArt() throws Exception {
        String trackId = firstTrackId();
        mockMvc.perform(base("/rest/download.view").param("id", trackId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
        // 目录封面（cover.jpg）→ getCoverArt 200
        String albumId = albumIdByName("叶惠美");
        mockMvc.perform(base("/rest/getCoverArt.view").param("id", albumId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("image/jpeg")));
        mockMvc.perform(base("/rest/getCoverArt.view").param("id", "al-999999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("code=\"70\"")));
    }

    @Test
    void scanStatusAndUser() throws Exception {
        mockMvc.perform(base("/rest/getScanStatus.view"))
                .andExpect(jsonPath("$.subsonic-response.scanStatus.count").isNumber());
        mockMvc.perform(base("/rest/startScan.view"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        awaitScanIdle(); // 等待后台扫描结束，避免跨测试 SQLITE_BUSY
        mockMvc.perform(base("/rest/getUser.view"))
                .andExpect(jsonPath("$.subsonic-response.user.username").value(USERNAME))
                .andExpect(jsonPath("$.subsonic-response.user.folder[0]").value(String.valueOf(rootId)));
        mockMvc.perform(base("/rest/getUsers.view"))
                .andExpect(jsonPath("$.subsonic-response.users.user[0].username").value(USERNAME));
    }

    @Test
    void unimplementedEndpointReturnsError0() throws Exception {
        mockMvc.perform(base("/rest/getGenres.view"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(0));
        mockMvc.perform(base("/rest/createUser.view"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(0));
    }

    @Test
    void xmlIsDefaultFormat() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/rest/ping.view")
                        .param("u", USERNAME).param("t", TOKEN).param("s", SALT)
                        .param("v", "1.16.1").param("c", "test"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<subsonic-response")));
    }

    @Test
    void nowPlayingTracksStream() throws Exception {
        String trackId = firstTrackId();
        mockMvc.perform(base("/rest/stream.view").param("id", trackId))
                .andExpect(status().isOk());
        mockMvc.perform(base("/rest/getNowPlaying.view"))
                .andExpect(jsonPath("$.subsonic-response.nowPlaying.entry[0].username").value(USERNAME))
                .andExpect(jsonPath("$.subsonic-response.nowPlaying.entry[0].playerId").value("test"));
    }

    @Test
    void bookmarkLifecycle() throws Exception {
        String track1 = songIdInAlbum(albumIdByName("叶惠美"), 0);
        String track2 = songIdInAlbum(albumIdByName("叶惠美"), 1);
        // 初始为空
        mockMvc.perform(base("/rest/getBookmarks.view"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.bookmarks.bookmark", hasSize(0)));
        // 创建（缺 position → error 10；缺 id → error 10）
        mockMvc.perform(base("/rest/createBookmark.view").param("id", track1))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(10));
        mockMvc.perform(base("/rest/createBookmark.view").param("position", "1"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(10));
        // 非法曲目 → error 70
        mockMvc.perform(base("/rest/createBookmark.view").param("id", "tr-999999").param("position", "1"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(70));
        mockMvc.perform(base("/rest/createBookmark.view")
                        .param("id", track1).param("position", "123").param("comment", "开场"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        // upsert：同曲目重复提交 → 更新（仍是 1 条）
        mockMvc.perform(base("/rest/createBookmark.view")
                        .param("id", track1).param("position", "456").param("comment", "副歌"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/getBookmarks.view"))
                .andExpect(jsonPath("$.subsonic-response.bookmarks.bookmark", hasSize(1)))
                .andExpect(jsonPath("$.subsonic-response.bookmarks.bookmark[0].position").value(456))
                .andExpect(jsonPath("$.subsonic-response.bookmarks.bookmark[0].username").value(USERNAME))
                .andExpect(jsonPath("$.subsonic-response.bookmarks.bookmark[0].comment").value("副歌"))
                .andExpect(jsonPath("$.subsonic-response.bookmarks.bookmark[0].entry.id").value(track1))
                .andExpect(jsonPath("$.subsonic-response.bookmarks.bookmark[0].created").isNotEmpty())
                .andExpect(jsonPath("$.subsonic-response.bookmarks.bookmark[0].changed").isNotEmpty());
        // 第二曲目书签并存
        mockMvc.perform(base("/rest/createBookmark.view").param("id", track2).param("position", "9"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/getBookmarks.view"))
                .andExpect(jsonPath("$.subsonic-response.bookmarks.bookmark", hasSize(2)));
        // 删除一个 → 剩一个；再删不存在幂等 ok
        mockMvc.perform(base("/rest/deleteBookmark.view").param("id", track1))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/deleteBookmark.view").param("id", track1))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/getBookmarks.view"))
                .andExpect(jsonPath("$.subsonic-response.bookmarks.bookmark", hasSize(1)))
                .andExpect(jsonPath("$.subsonic-response.bookmarks.bookmark[0].entry.id").value(track2));
    }

    @Test
    void playQueueLifecycle() throws Exception {
        String track1 = songIdInAlbum(albumIdByName("叶惠美"), 0);
        String track2 = songIdInAlbum(albumIdByName("叶惠美"), 1);
        // 初始空队列：ok + 空 entry
        mockMvc.perform(base("/rest/getPlayQueue.view"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.playQueue.entry", hasSize(0)));
        // 保存（id 顺序即队列顺序）
        mockMvc.perform(base("/rest/savePlayQueue.view")
                        .param("id", track1).param("id", track2)
                        .param("current", track2).param("position", "42"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/getPlayQueue.view"))
                .andExpect(jsonPath("$.subsonic-response.playQueue.entry", hasSize(2)))
                .andExpect(jsonPath("$.subsonic-response.playQueue.entry[0].id").value(track1))
                .andExpect(jsonPath("$.subsonic-response.playQueue.entry[1].id").value(track2))
                .andExpect(jsonPath("$.subsonic-response.playQueue.current").value(track2))
                .andExpect(jsonPath("$.subsonic-response.playQueue.position").value(42))
                .andExpect(jsonPath("$.subsonic-response.playQueue.username").value(USERNAME))
                .andExpect(jsonPath("$.subsonic-response.playQueue.changed").isNotEmpty());
        // 全量覆盖：只保留 track2
        mockMvc.perform(base("/rest/savePlayQueue.view").param("id", track2))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/getPlayQueue.view"))
                .andExpect(jsonPath("$.subsonic-response.playQueue.entry", hasSize(1)))
                .andExpect(jsonPath("$.subsonic-response.playQueue.entry[0].id").value(track2));
        // 空 id = 清空队列
        mockMvc.perform(base("/rest/savePlayQueue.view"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(base("/rest/getPlayQueue.view"))
                .andExpect(jsonPath("$.subsonic-response.playQueue.entry", hasSize(0)));
    }

    @Test
    void bookmarksXmlFormat() throws Exception {
        String trackId = songIdInAlbum(albumIdByName("叶惠美"), 0);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/rest/createBookmark.view")
                        .param("u", USERNAME).param("t", TOKEN).param("s", SALT)
                        .param("v", "1.16.1").param("c", "test")
                        .param("id", trackId).param("position", "321").param("comment", "highlight"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<subsonic-response")));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/rest/getBookmarks.view")
                        .param("u", USERNAME).param("t", TOKEN).param("s", SALT)
                        .param("v", "1.16.1").param("c", "test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<bookmarks")))
                .andExpect(content().string(containsString("position=\"321\"")))
                .andExpect(content().string(containsString("comment=\"highlight\"")))
                .andExpect(content().string(containsString("<entry id=\"tr-")))
                .andExpect(content().string(containsString("username=\"" + USERNAME + "\"")));
    }

    /** stream 返回真实音频字节（不只响应头）：修复"无法播放/只写响应头"类回归。 */
    @Test
    void streamReturnsAudioBytes() throws Exception {
        String mp3Id = songIdInAlbum(albumIdByName("叶惠美"), 0);
        mockMvc.perform(base("/rest/stream.view").param("id", mp3Id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("audio/mpeg")))
                .andExpect(result -> {
                    byte[] body = result.getResponse().getContentAsByteArray();
                    assertTrue(body.length > 0, "mp3 stream 未返回任何字节");
                    String len = result.getResponse().getHeader("Content-Length");
                    if (len != null) {
                        assertEquals(Long.parseLong(len), body.length);
                    }
                });
        String flacId = songIdInAlbum(albumIdByName("Let It Be"), 0);
        mockMvc.perform(base("/rest/stream.view").param("id", flacId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("audio/flac")))
                .andExpect(result -> assertTrue(
                        result.getResponse().getContentAsByteArray().length > 0, "flac stream 未返回任何字节"));
        // Range 206 精确返回请求区间字节
        mockMvc.perform(base("/rest/stream.view").param("id", mp3Id)
                        .header("Range", "bytes=100-199"))
                .andExpect(status().isPartialContent())
                .andExpect(result -> {
                    assertEquals(100, result.getResponse().getContentAsByteArray().length);
                    assertEquals("100", result.getResponse().getHeader("Content-Length"));
                });
    }

    /** 参数缺失等端点异常回 Subsonic 协议错误（10），而非管理端 ApiResponse/HTML 500。 */
    @Test
    void endpointExceptionReturnsProtocolError() throws Exception {
        mockMvc.perform(base("/rest/getSong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("failed"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(10));
        mockMvc.perform(base("/rest/getSong").param("id", "not-a-track"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(70));
    }

    /** 歌词：内嵌歌词 → getLyrics(getLyricsBySongId)，同步/非同步/缺失三态。 */
    @Test
    void lyricsEndpoints() throws Exception {
        // 注入：一首纯文本（非同步），一首 LRC（同步），一首无歌词（Let It Be）
        String plain = songIdInAlbum(albumIdByName("叶惠美"), 0); // 以父之名
        String syncedId = songIdInAlbum(albumIdByName("叶惠美"), 1); // 东风破
        for (com.bifrost.domain.entity.Track t : trackRepository.findAll()) {
            if ("以父之名".equals(t.getTitle())) {
                t.setLyrics("第一句\n第二句\n第三句");
            } else if ("东风破".equals(t.getTitle())) {
                t.setLyrics("[00:01.00]谁在用琵琶弹奏 一曲东风破\n[00:08.50]岁月在墙上剥落 看见小时候");
            }
            trackRepository.save(t);
        }
        // 非同步
        mockMvc.perform(base("/rest/getLyricsBySongId.view").param("id", plain))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.lyricsList.structuredLyrics", hasSize(1)))
                .andExpect(jsonPath("$.subsonic-response.lyricsList.structuredLyrics[0].synced").value(false))
                .andExpect(jsonPath("$.subsonic-response.lyricsList.structuredLyrics[0].line", hasSize(3)))
                .andExpect(jsonPath("$.subsonic-response.lyricsList.structuredLyrics[0].line[0].value").value("第一句"))
                .andExpect(jsonPath("$.subsonic-response.lyricsList.structuredLyrics[0].displayTitle").value("以父之名"));
        // 同步（LRC 时间戳 → ms）
        mockMvc.perform(base("/rest/getLyricsBySongId.view").param("id", syncedId))
                .andExpect(jsonPath("$.subsonic-response.lyricsList.structuredLyrics[0].synced").value(true))
                .andExpect(jsonPath("$.subsonic-response.lyricsList.structuredLyrics[0].line[0].start").value(1000))
                .andExpect(jsonPath("$.subsonic-response.lyricsList.structuredLyrics[0].line[0].value")
                        .value("谁在用琵琶弹奏 一曲东风破"))
                .andExpect(jsonPath("$.subsonic-response.lyricsList.structuredLyrics[0].line[1].start").value(8500));
        // 无歌词 → 空列表（ok）
        String noLyricsId = songIdInAlbum(albumIdByName("Let It Be"), 0);
        mockMvc.perform(base("/rest/getLyricsBySongId.view").param("id", noLyricsId))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.lyricsList.structuredLyrics", hasSize(0)));
        // 老协议 getLyrics：按 歌手+歌名 匹配
        mockMvc.perform(base("/rest/getLyrics.view").param("artist", "周杰伦").param("title", "东风破"))
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.lyrics.artist").value("周杰伦"))
                .andExpect(jsonPath("$.subsonic-response.lyrics.title").value("东风破"))
                .andExpect(jsonPath("$.subsonic-response.lyrics.value", containsString("一曲东风破")));
        // 参数/曲目错误
        mockMvc.perform(base("/rest/getLyrics.view"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(10));
        mockMvc.perform(base("/rest/getLyricsBySongId.view").param("id", "tr-999999"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(70));
        // 扩展通告：声明 songLyrics（OS 版本号为整数 1/2；仅实现 Version 1）
        mockMvc.perform(base("/rest/getOpenSubsonicExtensions.view"))
                .andExpect(jsonPath("$.subsonic-response.openSubsonicExtensions.openSubsonicExtension"
                        + "[?(@.name=='songLyrics')].versions").value("1"));
    }

    @Test
    void lyricsXmlFormat() throws Exception {
        for (com.bifrost.domain.entity.Track t : trackRepository.findAll()) {
            if ("东风破".equals(t.getTitle())) {
                t.setLyrics("[00:01.00]谁在用琵琶弹奏 一曲东风破\n[00:08.50]岁月在墙上剥落 看见小时候");
                trackRepository.save(t);
            }
        }
        String id = songIdInAlbum(albumIdByName("叶惠美"), 1);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/rest/getLyricsBySongId.view")
                        .param("u", USERNAME).param("t", TOKEN).param("s", SALT)
                        .param("v", "1.16.1").param("c", "test").param("id", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<lyricsList")))
                .andExpect(content().string(containsString("<structuredLyrics ")))
                .andExpect(content().string(containsString("synced=\"true\"")))
                .andExpect(content().string(containsString("<line start=\"1000\">")));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/rest/getLyrics.view")
                        .param("u", USERNAME).param("t", TOKEN).param("s", SALT)
                        .param("v", "1.16.1").param("c", "test")
                        .param("artist", "周杰伦").param("title", "东风破"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<lyrics ")))
                .andExpect(content().string(containsString("<lyric>")));
    }

    // ---------- 工具 ----------

    /** 带认证与 f=json 的基础请求。 */
    private MockHttpServletRequestBuilder base(String path) {
        return get(path)
                .param("u", USERNAME).param("t", TOKEN).param("s", SALT)
                .param("v", "1.16.1").param("c", "test").param("f", "json");
    }

    /** GET + 认证 + 附加参数（键值对）。 */
    private MvcResult call(String path, String... kv) throws Exception {
        MockHttpServletRequestBuilder request = base(path);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            request.param(kv[i], kv[i + 1]);
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String firstTrackId() throws Exception {
        String albumId = json(call("/rest/getAlbumList2.view", "type", "alphabeticalByName"))
                .at("/subsonic-response/albumList2/album/0/id").asText();
        return json(call("/rest/getAlbum.view", "id", albumId))
                .at("/subsonic-response/album/song/0/id").asText();
    }

    private String secondTrackId() throws Exception {
        String albumId = json(call("/rest/getAlbumList2.view", "type", "alphabeticalByName"))
                .at("/subsonic-response/albumList2/album/0/id").asText();
        return json(call("/rest/getAlbum.view", "id", albumId))
                .at("/subsonic-response/album/song/1/id").asText();
    }

    private static String findChildId(JsonNode directory, String title) {
        for (JsonNode child : directory.at("/subsonic-response/directory/child")) {
            if (title.equals(child.get("title").asText())) {
                return child.get("id").asText();
            }
        }
        throw new AssertionError("child not found: " + title);
    }

    private static String findFirstChildId(JsonNode directory) {
        JsonNode first = directory.at("/subsonic-response/directory/child/0");
        assertTrue(first.isObject(), "directory has no children");
        return first.get("id").asText();
    }

    /** 等待后台扫描结束（startScan 为异步触发；覆盖"太快未观察到 SCANNING"与"已开始"两种情形）。 */
    private void awaitScanIdle() throws Exception {
        Instant before = libraryRootRepository.findById(rootId).orElseThrow().getLastScanAt();
        long deadline = System.currentTimeMillis() + 10_000;
        boolean sawScanning = false;
        while (System.currentTimeMillis() < deadline) {
            LibraryRoot root = libraryRootRepository.findById(rootId).orElseThrow();
            if (root.getScanStatus() == com.bifrost.domain.enums.ScanStatus.SCANNING) {
                sawScanning = true;
            }
            if (sawScanning && root.getScanStatus() == com.bifrost.domain.enums.ScanStatus.IDLE) {
                return;
            }
            if (!sawScanning && root.getLastScanAt() != null && !root.getLastScanAt().equals(before)) {
                return; // 扫描太快已完成（未观察到 SCANNING，但 lastScanAt 已推进）
            }
            Thread.sleep(50);
        }
        throw new AssertionError("后台扫描未在 10s 内完成");
    }

    private String albumIdByName(String name) throws Exception {
        JsonNode albums = json(call("/rest/getAlbumList2.view", "type", "alphabeticalByName"));
        for (JsonNode album : albums.at("/subsonic-response/albumList2/album")) {
            if (name.equals(album.get("name").asText())) {
                return album.get("id").asText();
            }
        }
        throw new AssertionError("专辑不存在: " + name);
    }

    private String songIdInAlbum(String albumId, int index) throws Exception {
        return json(call("/rest/getAlbum.view", "id", albumId))
                .at("/subsonic-response/album/song/" + index + "/id").asText();
    }

    private void createSampleLibrary() throws Exception {
        MiniMediaFactory.writeMp3(musicDir.resolve("周杰伦/叶惠美"), "01 - 以父之名.mp3",
                "以父之名", "周杰伦", "周杰伦", "叶惠美", 1, 2003);
        MiniMediaFactory.writeMp3(musicDir.resolve("周杰伦/叶惠美"), "02 - 东风破.mp3",
                "东风破", "周杰伦", "周杰伦", "叶惠美", 2, 2003);
        MiniMediaFactory.writeFlac(musicDir.resolve("Beatles/Let It Be"), "01 - Let It Be.flac",
                "Let It Be", "The Beatles", "The Beatles", "Let It Be", 1, 1970);
        MiniMediaFactory.writeMp3(musicDir.resolve("solo"), "01 - 无题.mp3",
                "无题", null, null, null, 1, 2000);
        // 目录封面（周杰伦/叶惠美 目录下 cover.jpg）
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        Files.write(musicDir.resolve("周杰伦/叶惠美/cover.jpg"), out.toByteArray());
    }
}

