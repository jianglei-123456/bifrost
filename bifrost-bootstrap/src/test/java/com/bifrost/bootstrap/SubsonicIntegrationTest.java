package com.bifrost.bootstrap;

import com.bifrost.core.audio.ScanService;
import com.bifrost.core.security.PasswordCipher;
import com.bifrost.core.security.SubsonicTokenUtil;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.repo.AlbumRepository;
import com.bifrost.domain.repo.ArtistRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 05 Subsonic 服务（Syrinx）集成测试：浏览/目录树/列表/搜索/歌单/标注/流媒体/封面/扫描/用户。
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

