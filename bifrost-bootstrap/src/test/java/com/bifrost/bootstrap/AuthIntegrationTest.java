package com.bifrost.bootstrap;

import com.bifrost.core.security.AuthenticationService;
import com.bifrost.core.security.SubsonicTokenUtil;
import com.bifrost.domain.entity.User;
import com.bifrost.domain.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 03 认证集成测试（Q5-B/Q21）：初始账号、AES-GCM 口令、双入口认证与协议错误码。
 */
@SpringBootTest(properties = {
        "bifrost.db.path=target/test-data/auth-test.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=secret123"})
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthenticationService authenticationService;

    @Test
    void initialAdminCreatedWithEncryptedPassword() {
        User admin = userRepository.findByUsername(USERNAME).orElseThrow();
        assertNotEquals(PASSWORD, admin.getEncryptedPassword()); // 永不明文落库
        assertTrue(authenticationService.authenticateByPassword(USERNAME, PASSWORD).isPresent());
    }

    @Test
    void tokenAuthenticationWorks() {
        String salt = "testsalt";
        String token = SubsonicTokenUtil.token(PASSWORD, salt);
        assertTrue(authenticationService.authenticateByToken(USERNAME, token, salt).isPresent());
        assertTrue(authenticationService.authenticateByToken(USERNAME, "wrongtoken", salt).isEmpty());
        assertTrue(authenticationService.authenticateByToken(USERNAME, token, "wrongsalt").isEmpty());
    }

    @Test
    void changePasswordRequiresOldPassword() {
        authenticationService.changePassword(USERNAME, PASSWORD, "newpass456");
        assertTrue(authenticationService.authenticateByPassword(USERNAME, "newpass456").isPresent());
        assertFalse(authenticationService.authenticateByPassword(USERNAME, PASSWORD).isPresent());
        authenticationService.changePassword(USERNAME, "newpass456", PASSWORD); // 还原
    }

    @Test
    void apiUserWithBasicAuth() throws Exception {
        mockMvc.perform(get("/api/user")
                        .header("Authorization", basic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(USERNAME));
    }

    @Test
    void apiUserWithoutAuthRejected() throws Exception {
        mockMvc.perform(get("/api/user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void apiPingExempt() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void apiChangePassword() throws Exception {
        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", basic(USERNAME, PASSWORD))
                        .contentType("application/json")
                        .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"changed789\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertTrue(authenticationService.authenticateByPassword(USERNAME, "changed789").isPresent());
        authenticationService.changePassword(USERNAME, "changed789", PASSWORD); // 还原
    }

    @Test
    void subsonicTokenAuthOk() throws Exception {
        String salt = "abcdef";
        String token = SubsonicTokenUtil.token(PASSWORD, salt);
        mockMvc.perform(get("/rest/ping.view")
                        .param("u", USERNAME).param("t", token).param("s", salt)
                        .param("v", "1.16.1").param("c", "test").param("f", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    void subsonicTokenAuthOkWithoutViewSuffix() throws Exception {
        // 客户端可省略 .view 后缀（如 /rest/ping）
        String salt = "abcdef";
        String token = SubsonicTokenUtil.token(PASSWORD, salt);
        mockMvc.perform(get("/rest/ping")
                        .param("u", USERNAME).param("t", token).param("s", salt)
                        .param("v", "1.16.1").param("c", "test").param("f", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    void subsonicWrongPasswordReturns40() throws Exception {
        mockMvc.perform(get("/rest/ping.view")
                        .param("u", USERNAME).param("t", "badbadbadbadbadbadbadbadbadbadbad")
                        .param("s", "abcdef").param("v", "1.16.1").param("c", "test").param("f", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("failed"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(40));
    }

    @Test
    void subsonicMissingParamsReturns10() throws Exception {
        mockMvc.perform(get("/rest/ping.view")
                        .param("u", USERNAME).param("t", "x").param("s", "y").param("c", "test").param("f", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(10));
    }

    @Test
    void subsonicApiKeyReturns42() throws Exception {
        mockMvc.perform(get("/rest/ping.view")
                        .param("u", USERNAME).param("p", PASSWORD).param("apiKey", "xyz")
                        .param("v", "1.16.1").param("c", "test").param("f", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(42));
    }

    @Test
    void subsonicConflictingParamsReturns43() throws Exception {
        mockMvc.perform(get("/rest/ping.view")
                        .param("u", USERNAME).param("t", "x").param("s", "y").param("p", PASSWORD)
                        .param("v", "1.16.1").param("c", "test").param("f", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(43));
    }

    @Test
    void subsonicVersionTooHighReturns30() throws Exception {
        mockMvc.perform(get("/rest/ping.view")
                        .param("u", USERNAME).param("p", PASSWORD)
                        .param("v", "1.17.0").param("c", "test").param("f", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(30));
    }

    @Test
    void subsonicPasswordAuthXmlFormat() throws Exception {
        // 默认 XML 格式的错误响应
        mockMvc.perform(get("/rest/ping.view")
                        .param("u", USERNAME).param("p", "wrong")
                        .param("v", "1.16.1").param("c", "test"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/xml"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=\"40\"")));
    }

    @Test
    void subsonicOpenSubsonicExtensionsExempt() throws Exception {
        mockMvc.perform(get("/rest/getOpenSubsonicExtensions.view").param("f", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
        mockMvc.perform(get("/rest/getOpenSubsonicExtensions").param("f", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    void corsHeadersPresentOnSubsonicAndApi() throws Exception {
        mockMvc.perform(get("/rest/ping.view")
                        .header("Origin", "http://localhost:5173")
                        .param("u", USERNAME).param("p", PASSWORD)
                        .param("v", "1.16.1").param("c", "test").param("f", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mockMvc.perform(get("/api/ping")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void corsPreflightAllowed() throws Exception {
        mockMvc.perform(options("/rest/ping.view")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")));
        mockMvc.perform(options("/api/scan")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}

