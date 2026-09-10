package com.bifrost.bootstrap;

import com.bifrost.core.book.sync.SyncAccountService;
import com.bifrost.core.security.SubsonicTokenUtil;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.entity.ReadingProgress;
import com.bifrost.domain.entity.SyncAccount;
import com.bifrost.domain.repo.BookRepository;
import com.bifrost.domain.repo.ReadingProgressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端 book-sync 契约测试（M3-sync T3.6 / T4.2）。
 *
 * <p>管理端走既有 {@code /api/**} 链（HTTP Basic），因此这里用 {@code admin:testpass}。</p>
 */
@SpringBootTest(properties = {
        "bifrost.db.path=target/test-data/book-sync-admin.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=testpass"
})
@AutoConfigureMockMvc
@DirtiesContext
class BookSyncAdminIntegrationTest {

    private static final String ADMIN_BASIC = "Basic YWRtaW46dGVzdHBhc3M=";

    @Autowired private MockMvc mockMvc;
    @Autowired private SyncAccountService syncAccountService;
    @Autowired private ReadingProgressRepository progressRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private com.bifrost.domain.repo.SyncDeviceRepository syncDeviceRepository;
    @Autowired private ObjectMapper objectMapper;

    private Long accountId;
    private String username;
    private String authKey;

    @BeforeEach
    void setUp() {
        progressRepository.deleteAll();
        bookRepository.deleteAll();
        syncDeviceRepository.deleteAll();
        SyncAccount account = syncAccountService.currentOrCreate();
        accountId = account.getId();
        username = account.getUsername();
        authKey = SubsonicTokenUtil.token(syncAccountService.revealPassword(account), "");
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", ADMIN_BASIC);
    }

    /** 用协议端点造一条进度（最接近真实来源）。 */
    private void pushProgress(String document, double percentage, String device, String deviceId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document", document);
        body.put("progress", percentage > 0.5 ? "120" : "12");
        body.put("percentage", percentage);
        body.put("device", device);
        body.put("device_id", deviceId);
        mockMvc.perform(put("/syncs/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-auth-user", username)
                        .header("x-auth-key", authKey)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private Book saveBook(String partialMd5, String title) {
        Book book = new Book();
        String path = "/library/synthetic/" + title.replace(' ', '-') + ".epub";
        book.setFilePath(path);
        book.setFileSize(10L);
        book.setFileLastModified(10L);
        book.setFingerprint(path + "|10|10");
        book.setTitle(title);
        book.setAuthors("Some Author");
        book.setLibraryRootId(999L);
        book.setIsAvailable(true);
        book.setPartialMd5(partialMd5);
        book.setCoverSource("EMBEDDED");
        return bookRepository.save(book);
    }

    // ---------- 同步账号 ----------

    @Test
    void accountExposesPlaintextPasswordAndServerUrl() throws Exception {
        mockMvc.perform(admin(get("/api/book-sync/account")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.password").isNotEmpty())      // R2a：明文回显
                .andExpect(jsonPath("$.data.serverUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.registrationEnabled").value(true))
                .andExpect(jsonPath("$.data.autoScanOnUnmatched").value(true));
    }

    @Test
    void accountUpdateRejectsAdminPassword() throws Exception {
        mockMvc.perform(admin(put("/api/book-sync/account"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"testpass\"}"))
                .andExpect(status().isBadRequest())                       // R2c
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("管理员口令")));
    }

    @Test
    void accountUpdateAndPasswordGeneration() throws Exception {
        mockMvc.perform(admin(put("/api/book-sync/account"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reader2\",\"password\":\"synctest\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("reader2"))
                .andExpect(jsonPath("$.data.password").value("synctest"));

        mockMvc.perform(admin(post("/api/book-sync/account/password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value("reader2"));

        // 生成的口令不是原来的
        assertNotEquals("synctest", syncAccountService.revealPassword(syncAccountService.currentOrCreate()));
    }

    // ---------- 进度列表 / 重置 / 概览 ----------

    @Test
    void progressListShowsMatchedBookAndResetRemovesIt() throws Exception {
        String document = "aaaabbbbccccddddaaaabbbbccccdddd";
        Book book = saveBook(document, "Matched Book");
        pushProgress(document, 0.42, "Kobo_nova", "dev-1");

        mockMvc.perform(admin(get("/api/book-sync/progress")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].bookId").value(book.getId()))
                .andExpect(jsonPath("$.data.items[0].bookTitle").value("Matched Book"))
                .andExpect(jsonPath("$.data.items[0].coverUrl").value("/opds/v1.2/catalog/" + book.getId() + "/cover"))
                .andExpect(jsonPath("$.data.items[0].percentage").value(0.42))
                .andExpect(jsonPath("$.data.items[0].device").value("Kobo_nova"))
                .andExpect(jsonPath("$.data.items[0].reportedAt").isNotEmpty());

        Long progressId = progressRepository
                .findBySyncAccountIdAndDocumentFingerprint(accountId, document).orElseThrow().getId();
        mockMvc.perform(admin(delete("/api/book-sync/progress/" + progressId)))
                .andExpect(status().isOk());
        mockMvc.perform(admin(get("/api/book-sync/progress")))
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(admin(delete("/api/book-sync/progress/" + progressId)))
                .andExpect(status().isNotFound())                        // 1001
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void statsCountMatchedOrphansAndDevices() throws Exception {
        String matched = "11112222333344441111222233334444";
        saveBook(matched, "Known Book");
        pushProgress(matched, 0.3, "Kobo_nova", "dev-1");
        pushProgress("99998888777766669999888877776666", 0.1, "Kindle", "dev-2");

        mockMvc.perform(admin(get("/api/book-sync/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressCount").value(2))
                .andExpect(jsonPath("$.data.matchedCount").value(1))
                .andExpect(jsonPath("$.data.orphanCount").value(1))
                .andExpect(jsonPath("$.data.deviceCount").value(2))
                .andExpect(jsonPath("$.data.lastReportedAt").isNotEmpty());
    }

    @Test
    void orphanFlowBindRematchIgnore() throws Exception {
        String unknown = "deadbeefdeadbeefdeadbeefdeadbee1";
        pushProgress(unknown, 0.05, "Kobo_nova", "dev-1");

        mockMvc.perform(admin(get("/api/book-sync/orphans")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].documentFingerprint").value(unknown))
                .andExpect(jsonPath("$.data.items[0].scanAttemptedAt").doesNotExist());  // 待结算

        Long orphanId = progressRepository
                .findBySyncAccountIdAndDocumentFingerprint(accountId, unknown).orElseThrow().getId();

        // 重新匹配：库里还没有对应书 → matched=false（正常结果，不是错误）
        mockMvc.perform(admin(post("/api/book-sync/orphans/" + orphanId + "/rematch")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(false));

        // 人工绑定
        Book book = saveBook("feedfacefeedfacefeedfacefeedfac1", "Bind Target");
        mockMvc.perform(admin(post("/api/book-sync/orphans/" + orphanId + "/bind"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookId").value(book.getId()))
                .andExpect(jsonPath("$.data.matchSource").value("MANUAL"));

        ReadingProgress bound = progressRepository.findById(orphanId).orElseThrow();
        assertEquals(book.getId(), bound.getBookId());
        assertNotNull(bound.getScanAttemptedAt(), "人工绑定后应视为已结算");
        assertTrue(progressRepository.findBySyncAccountIdAndBookIdIsNull(accountId, org.springframework.data.domain.Pageable.unpaged()).isEmpty());

        // 忽略另一条孤儿
        pushProgress("cafebabecafebabecafebabecafebabe", 0.2, "Kindle", "dev-2");
        Long ignoredId = progressRepository
                .findBySyncAccountIdAndDocumentFingerprint(accountId, "cafebabecafebabecafebabecafebabe")
                .orElseThrow().getId();
        mockMvc.perform(admin(post("/api/book-sync/orphans/" + ignoredId + "/ignore")))
                .andExpect(status().isOk());
        mockMvc.perform(admin(get("/api/book-sync/orphans")))
                .andExpect(jsonPath("$.data.total").value(0));             // 默认隐藏已忽略
        mockMvc.perform(admin(get("/api/book-sync/orphans").param("includeIgnored", "true")))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void rematchFindsBookAddedLater() throws Exception {
        String document = "abcabcabcabcabcabcabcabcabcabcab";
        pushProgress(document, 0.33, "Kobo_nova", "dev-1");
        Long id = progressRepository.findBySyncAccountIdAndDocumentFingerprint(accountId, document)
                .orElseThrow().getId();

        saveBook(document, "Later Book");   // "我后来把书放进库了"

        mockMvc.perform(admin(post("/api/book-sync/orphans/" + id + "/rematch")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.progress.bookTitle").value("Later Book"))
                .andExpect(jsonPath("$.data.progress.matchSource").value("AUTO"));
    }

    @Test
    void devicesAreListed() throws Exception {
        pushProgress("01230123012301230123012301230123", 0.5, "Kobo_nova", "dev-1");

        mockMvc.perform(admin(get("/api/book-sync/devices")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].deviceId").value("dev-1"))
                .andExpect(jsonPath("$.data.items[0].deviceName").value("Kobo_nova"))
                .andExpect(jsonPath("$.data.items[0].reportCount").value(1));
    }

    // ---------- 删书摘链 ----------

    @Test
    void deletingBookDetachesProgressAsSettledOrphan() throws Exception {
        String document = "55556666777788885555666677778888";
        Book book = saveBook(document, "To Delete");
        pushProgress(document, 0.6, "Kobo_nova", "dev-1");

        mockMvc.perform(admin(delete("/api/books/" + book.getId())))
                .andExpect(status().isOk());

        ReadingProgress orphan = progressRepository
                .findBySyncAccountIdAndDocumentFingerprint(accountId, document).orElseThrow();
        assertNull(orphan.getBookId(), "删书后进度保留为孤儿（R5-c）");
        assertNull(orphan.getMatchSource());
        assertNotNull(orphan.getScanAttemptedAt(), "且标记为已结算 → 不会被自动重绑");
    }

    @Test
    void bookDtoExposesDocumentFingerprint() throws Exception {
        Book book = saveBook("abcdefabcdefabcdefabcdefabcdef99", "Fingerprint Book");

        mockMvc.perform(admin(get("/api/books/" + book.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partialMd5").value("abcdefabcdefabcdefabcdefabcdef99"));
    }
}
