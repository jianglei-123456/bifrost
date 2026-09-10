package com.bifrost.bootstrap;

import com.bifrost.core.book.sync.SyncAccountService;
import com.bifrost.core.book.sync.UnmatchedProgressScanTrigger;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KOSync 协议端到端集成测试（M3-sync T2.7 / T4.2）。
 *
 * <p>逐条钉死客户端的硬约束（{@code doc/m3-sync/调研/01-KOSync协议实证.md} §3），并且<b>全程带真实
 * 客户端的 Accept 头</b>（{@code application/vnd.koreader.v1+json}）——这条曾经抓到一个致命问题：
 * 用 {@code produces=application/json} 会被 Spring 内容协商判成 406，真实设备上表现为"注册与同步全部失败"。</p>
 */
@SpringBootTest(properties = {
        "bifrost.db.path=target/test-data/kosync-protocol.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=testpass"
})
@AutoConfigureMockMvc
@DirtiesContext
class KosyncProtocolIntegrationTest {

    /** 真实 KOReader 每个请求都会带的 Accept 头 */
    private static final String VENDOR_ACCEPT = "application/vnd.koreader.v1+json";

    @Autowired private MockMvc mockMvc;
    @Autowired private SyncAccountService syncAccountService;
    @Autowired private ReadingProgressRepository progressRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private ObjectMapper objectMapper;

    /** 用 spy 断言"同一指纹只触发一次扫描"（C1），并阻断真实扫描的副作用 */
    @MockitoSpyBean private UnmatchedProgressScanTrigger scanTrigger;

    private Long accountId;
    private String username;
    private String authKey;

    @BeforeEach
    void setUp() {
        // 测试库跨次运行会残留数据（同 JVM 内多个用例也共享）→ 清干净，保证可重复执行
        progressRepository.deleteAll();
        bookRepository.deleteAll();

        SyncAccount account = syncAccountService.currentOrCreate();
        accountId = account.getId();
        username = account.getUsername();
        authKey = SubsonicTokenUtil.token(syncAccountService.revealPassword(account), "");
    }

    // ---------- 请求构造（全部模拟真实客户端） ----------

    private String body(Map<String, Object> fields) throws Exception {
        return objectMapper.writeValueAsString(fields);
    }

    private static Map<String, Object> putBody(String document, String progress, double percentage,
                                              String device, String deviceId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("document", document);
        map.put("progress", progress);
        map.put("percentage", percentage);
        map.put("device", device);
        map.put("device_id", deviceId);
        return map;
    }

    private MockHttpServletRequestBuilder clientPut(String document, String progress, double percentage,
                                                   String device, String deviceId) throws Exception {
        return put("/syncs/progress")
                .header("accept", VENDOR_ACCEPT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-auth-user", username)
                .header("x-auth-key", authKey)
                .content(body(putBody(document, progress, percentage, device, deviceId)));
    }

    private MockHttpServletRequestBuilder clientGet(String path) {
        return get(path)
                .header("accept", VENDOR_ACCEPT)
                .header("x-auth-user", username)
                .header("x-auth-key", authKey);
    }

    private MockHttpServletRequestBuilder clientPost(String path, String json) {
        return post(path)
                .header("accept", VENDOR_ACCEPT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private MockHttpServletRequestBuilder clientPutRaw(String json) {
        return put("/syncs/progress")
                .header("accept", VENDOR_ACCEPT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-auth-user", username)
                .header("x-auth-key", authKey)
                .content(json);
    }

    // ---------- 注册 / 登录 ----------

    @Test
    void registerIsIdempotentAndMustReturn201() throws Exception {
        MvcResult result = mockMvc.perform(clientPost("/users/create",
                        body(Map.of("username", username, "password", authKey))))
                .andExpect(status().isCreated())          // 客户端注册成功只认 201
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();

        assertTrue(result.getResponse().getContentType().startsWith("application/json"),
                "客户端 JSON 中间件只认 application/*json；回 vendor +json 会导致它完全不解析响应体");
    }

    @Test
    void registerWithForeignUsernameIsRejectedWithReadableMessage() throws Exception {
        mockMvc.perform(clientPost("/users/create",
                        body(Map.of("username", "someone-else", "password", authKey))))
                .andExpect(status().isPaymentRequired())   // 402 在客户端 expected_status 里 → message 会展示给用户
                .andExpect(jsonPath("$.code").value(3002))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void registerWithWrongPasswordIsRejectedAsConflict() throws Exception {
        mockMvc.perform(clientPost("/users/create",
                        body(Map.of("username", username,
                                "password", SubsonicTokenUtil.token("definitely-wrong", "")))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void registerWithoutCredentialsIsInvalidRequest() throws Exception {
        mockMvc.perform(clientPost("/users/create", "{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(3003));
    }

    @Test
    void authorizeReturnsOkAndUnauthorizedCarriesMessage() throws Exception {
        mockMvc.perform(clientGet("/users/auth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorized").value("OK"));

        mockMvc.perform(get("/users/auth").header("accept", VENDOR_ACCEPT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(3001))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(result -> assertNull(result.getResponse().getHeader("WWW-Authenticate"),
                        "不该发 WWW-Authenticate：那是浏览器 Basic 语义，会让部分客户端误触发认证流程"));

        mockMvc.perform(get("/users/auth")
                        .header("accept", VENDOR_ACCEPT)
                        .header("x-auth-user", username)
                        .header("x-auth-key", SubsonicTokenUtil.token("nope", "")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 上报 / 读取 ----------

    @Test
    void putThenGetEchoesFieldsByteExact() throws Exception {
        String document = "aaaaaaaabbbbbbbbccccccccdddddddd";
        String progress = "/body/DocFragment[20]/body/p[22]/img.0";
        String device = "Kobo_nova（客厅 📚）";
        String deviceId = "0f2c4d6e-1111-2222-3333-444455556666";

        MvcResult put = mockMvc.perform(clientPut(document, progress, 0.4231, device, deviceId))
                .andExpect(status().isOk())                     // 只有 200 算成功；202 会被客户端当失败重投
                .andExpect(jsonPath("$.document").value(document))
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andReturn();

        assertTrue(put.getResponse().getContentType().startsWith("application/json"));
        assertNull(put.getResponse().getHeader("Content-Encoding"), "客户端不发 Accept-Encoding，响应不得压缩");
        long timestamp = objectMapper.readTree(put.getResponse().getContentAsString()).get("timestamp").asLong();
        assertTrue(Math.abs(Instant.now().getEpochSecond() - timestamp) < 5,
                "timestamp 必须是秒级 epoch：毫秒值会被客户端判成'永远更新'，导致每次拉取都往前跳");

        mockMvc.perform(clientGet("/syncs/progress/" + document))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value(document))
                .andExpect(jsonPath("$.percentage").value(0.4231))
                .andExpect(jsonPath("$.progress").value(progress))
                .andExpect(jsonPath("$.device").value(device))
                .andExpect(jsonPath("$.device_id").value(deviceId))
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    void unknownDocumentReturnsEmptyObjectWith200Not404() throws Exception {
        mockMvc.perform(clientGet("/syncs/progress/ffffffffffffffffffffffffffffffff"))
                .andExpect(status().isOk())                 // 404 会让客户端抛 "404 not expected" → 笼统报错弹窗
                .andExpect(content().json("{}", true));     // 严格模式：必须是空对象（无 percentage → 客户端友好提示）
    }

    @Test
    void olderPushStillOverwritesNewerOne() throws Exception {
        String document = "11111111222222223333333344444444";

        mockMvc.perform(clientPut(document, "56", 0.56, "Kobo", "dev-a"))
                .andExpect(status().isOk());
        // 更旧的推送随后到达：服务端不做新旧比较（协议语义），照样覆盖
        mockMvc.perform(clientPut(document, "36", 0.22, "Kindle", "dev-b"))
                .andExpect(status().isOk());

        mockMvc.perform(clientGet("/syncs/progress/" + document))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentage").value(0.22))
                .andExpect(jsonPath("$.progress").value("36"))
                .andExpect(jsonPath("$.device").value("Kindle"));
    }

    @Test
    void putRejectsMissingFieldsWith403() throws Exception {
        Map<String, Object> missingDocument = putBody("d".repeat(32), "1", 0.1, "Kobo", "dev");
        missingDocument.remove("document");
        mockMvc.perform(clientPutRaw(body(missingDocument)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(3003));

        Map<String, Object> missingPercentage = putBody("d".repeat(32), "1", 0.1, "Kobo", "dev");
        missingPercentage.remove("percentage");
        mockMvc.perform(clientPutRaw(body(missingPercentage)))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthcheckIsAnonymous() throws Exception {
        mockMvc.perform(get("/healthcheck").header("accept", VENDOR_ACCEPT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OK"));
    }

    // ---------- 指纹 → 图书：即时匹配 / 自动扫描触发 ----------

    @Test
    void firstPushBindsImmediatelyWhenBookAlreadyInLibrary() throws Exception {
        String document = "0123456789abcdef0123456789abcdef";
        Book book = bookRepository.save(libraryBook(document, "Immediate Match"));
        doNothing().when(scanTrigger).triggerAsync();

        mockMvc.perform(clientPut(document, "/body/x", 0.5, "Kobo", "dev-1"))
                .andExpect(status().isOk());

        ReadingProgress row = progressRepository
                .findBySyncAccountIdAndDocumentFingerprint(accountId, document).orElseThrow();
        assertEquals(book.getId(), row.getBookId(), "库里有这本书时就该即时绑定，不必等扫描");
        verify(scanTrigger, times(0)).triggerAsync();
    }

    @Test
    void unmatchedFingerprintTriggersScanOnlyOnce() throws Exception {
        doNothing().when(scanTrigger).triggerAsync();
        String first = "deadbeefdeadbeefdeadbeefdeadbeef";
        String second = "feedfacefeedfacefeedfacefeedface";

        // 同一指纹推两次（翻页防抖、挂起、关书都会推）→ 只应触发一次扫描
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(clientPut(first, String.valueOf(i + 1), 0.1 * (i + 1), "Kobo", "dev-1"))
                    .andExpect(status().isOk());
        }
        // 另一个新指纹 → 再触发一次
        mockMvc.perform(clientPut(second, "1", 0.1, "Kobo", "dev-1"))
                .andExpect(status().isOk());

        verify(scanTrigger, times(2)).triggerAsync();

        ReadingProgress pending = progressRepository
                .findBySyncAccountIdAndDocumentFingerprint(accountId, first).orElseThrow();
        assertNull(pending.getBookId());
        assertNull(pending.getScanAttemptedAt(), "结算发生在扫描完成事件里，此刻还不该被标记");
    }

    @Test
    void storedTimestampIsSecondPrecision() throws Exception {
        String document = "abcdefabcdefabcdefabcdefabcdefab";
        mockMvc.perform(clientPut(document, "7", 0.07, "Kobo", "dev-1"))
                .andExpect(status().isOk());

        ReadingProgress row = progressRepository
                .findBySyncAccountIdAndDocumentFingerprint(accountId, document).orElseThrow();
        assertNotNull(row.getReportedAt());
        assertEquals(0, row.getReportedAt().getNano(), "存储的时间戳必须是秒级");
        assertEquals(accountId, row.getSyncAccountId());
    }

    private static Book libraryBook(String partialMd5, String title) {
        Book book = new Book();
        String path = "/library/synthetic/" + title.replace(' ', '-') + ".epub";
        book.setFilePath(path);
        book.setFileSize(1L);
        book.setFileLastModified(1L);
        book.setFingerprint(path + "|1|1");
        book.setTitle(title);
        book.setLibraryRootId(999L);
        book.setIsAvailable(true);
        book.setPartialMd5(partialMd5);
        return book;
    }
}
