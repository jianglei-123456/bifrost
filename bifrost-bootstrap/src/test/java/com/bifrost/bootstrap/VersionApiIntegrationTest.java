package com.bifrost.bootstrap;

import com.bifrost.common.constant.BifrostVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/version}（1.0.0 起）：回答"这个部署跑的是哪个版本"。
 *
 * <p>与 Subsonic 信封的 {@code serverVersion} 同源（{@link BifrostVersion}）；
 * 走 /api/** 认证链，未认证 401 + code 1002。</p>
 */
@SpringBootTest(properties = {"bifrost.db.path=target/test-data/version-api.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=testpass"})
@AutoConfigureMockMvc
class VersionApiIntegrationTest {

    private static final String BASIC = "Basic " + Base64.getEncoder().encodeToString(
            "admin:testpass".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Test
    void versionRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/version"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void versionReturnsReleaseVersion() throws Exception {
        mockMvc.perform(get("/api/version").header("Authorization", BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.version").value(BifrostVersion.VERSION));
    }
}
