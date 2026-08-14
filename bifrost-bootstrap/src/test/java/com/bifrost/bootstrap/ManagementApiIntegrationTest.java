package com.bifrost.bootstrap;

import com.bifrost.core.security.PasswordCipher;
import com.bifrost.domain.entity.User;
import com.bifrost.domain.enums.UserRole;
import com.bifrost.domain.repo.AlbumRepository;
import com.bifrost.domain.repo.ArtistRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
import com.bifrost.domain.repo.PlaylistEntryRepository;
import com.bifrost.domain.repo.PlaylistRepository;
import com.bifrost.domain.repo.TrackRepository;
import com.bifrost.domain.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 04 管理 REST 集成测试（含真实扫描建库与浏览链路）。
 */
@SpringBootTest(properties = {
        "bifrost.db.path=target/test-data/api-test.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=testpass"})
@AutoConfigureMockMvc
class ManagementApiIntegrationTest {

    private static final String BASIC = "Basic " + Base64.getEncoder().encodeToString(
            "admin:testpass".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private PlaylistEntryRepository playlistEntryRepository;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private AlbumRepository albumRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private LibraryRootRepository libraryRootRepository;
    @Autowired
    private PasswordCipher passwordCipher;

    @TempDir
    Path musicDir;

    @BeforeEach
    void resetDatabase() throws Exception {
        playlistEntryRepository.deleteAll();
        playlistRepository.deleteAll();
        trackRepository.deleteAll();
        albumRepository.deleteAll();
        artistRepository.deleteAll();
        libraryRootRepository.deleteAll();
        userRepository.deleteAll();
        User admin = new User();
        admin.setUsername("admin");
        admin.setEncryptedPassword(passwordCipher.encrypt("testpass"));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        createSampleLibrary();
    }

    @Test
    void libraryRootCrudScanAndBrowse() throws Exception {
        String rootPath = jsonEscape(musicDir.toAbsolutePath().normalize().toString());
        // 新增
        mockMvc.perform(post("/api/library-roots")
                        .header("Authorization", BASIC).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试库\",\"path\":\"" + rootPath + "\",\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber());
        Long rootId = rootIdFromList();
        // 触发扫描
        mockMvc.perform(post("/api/library-roots/" + rootId + "/scan")
                        .header("Authorization", BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.added").value(4));
        // 浏览（/api/artists 为真实实体平面列表；"未知艺术家"为虚拟分组，仅出现于索引分组）
        mockMvc.perform(get("/api/artists").header("Authorization", BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2)); // 周杰伦 + The Beatles
        mockMvc.perform(get("/api/albums").header("Authorization", BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3)); // 叶惠美/Let It Be/未知专辑
        mockMvc.perform(get("/api/tracks").header("Authorization", BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4));
        // 搜索
        mockMvc.perform(get("/api/search").param("q", "周杰伦").header("Authorization", BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.artists[0].name").value("周杰伦"));
        // 编辑 + 删除（删除后曲目隐藏）
        mockMvc.perform(put("/api/library-roots/" + rootId)
                        .header("Authorization", BASIC).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名库\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名库"));
        mockMvc.perform(delete("/api/library-roots/" + rootId).header("Authorization", BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/tracks").header("Authorization", BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0)); // 已隐藏
    }

    @Test
    void playlistsAndAnnotation() throws Exception {
        String rootPath = jsonEscape(musicDir.toAbsolutePath().normalize().toString());
        mockMvc.perform(post("/api/library-roots").header("Authorization", BASIC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试库\",\"path\":\"" + rootPath + "\"}"))
                .andExpect(status().isOk());
        Long rootId = rootIdFromList();
        mockMvc.perform(post("/api/library-roots/" + rootId + "/scan").header("Authorization", BASIC))
                .andExpect(status().isOk());

        String tracks = mockMvc.perform(get("/api/tracks").header("Authorization", BASIC))
                .andReturn().getResponse().getContentAsString();
        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        long trackId = om.readTree(tracks).get("data").get("items").get(0).get("id").asLong();
        long albumId = om.readTree(tracks).get("data").get("items").get(0).get("albumId").asLong();

        // 歌单
        String playlistJson = mockMvc.perform(post("/api/playlists")
                        .header("Authorization", BASIC).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"最爱\",\"comment\":\"备注\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("最爱"))
                .andReturn().getResponse().getContentAsString();
        long playlistId = om.readTree(playlistJson).get("data").get("id").asLong();
        mockMvc.perform(post("/api/playlists/" + playlistId + "/entries")
                        .header("Authorization", BASIC).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackId\":" + trackId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").value(1));
        mockMvc.perform(get("/api/playlists/" + playlistId).header("Authorization", BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries", hasSize(1)));

        // 标注（三态收藏 + 评分）
        mockMvc.perform(post("/api/starred").header("Authorization", BASIC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"album\",\"id\":" + albumId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/rating").header("Authorization", BASIC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"track\",\"id\":" + trackId + ",\"rating\":5}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/albums/" + albumId).header("Authorization", BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.album.starredAt").exists());
    }

    @Test
    void errorCases() throws Exception {
        // 未认证 → 401 + 1002
        mockMvc.perform(get("/api/artists")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
        // 资源不存在 → 404 + 1001
        mockMvc.perform(get("/api/albums/999999").header("Authorization", BASIC))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1001));
        // 重复库根路径 → 冲突
        String rootPath = jsonEscape(musicDir.toAbsolutePath().normalize().toString());
        mockMvc.perform(post("/api/library-roots").header("Authorization", BASIC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"path\":\"" + rootPath + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/library-roots").header("Authorization", BASIC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"B\",\"path\":\"" + rootPath + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1004));
        // 扫描不存在库根 → 404
        mockMvc.perform(post("/api/library-roots/999999/scan").header("Authorization", BASIC))
                .andExpect(status().isNotFound());
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Long rootIdFromList() throws Exception {
        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        String list = mockMvc.perform(get("/api/library-roots").header("Authorization", BASIC))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(list).get("data").get(0).get("id").asLong();
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
    }
}

