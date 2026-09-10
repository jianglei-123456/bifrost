package com.bifrost.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 关闭 {@code bifrost.kosync.enabled} 后：端点必须<b>整个消失（404）</b>，而不是"还在但不鉴权"（M3-sync T2.2/T2.7）。
 *
 * <p>为什么值得一条专门的回归测试：当前项目只有 {@code /api/**}、{@code /rest/**}、{@code /opds/**}
 * 三条 SecurityFilterChain，<b>没被任何 securityMatcher 命中的前缀是完全不设防的</b>。
 * 如果哪天有人把控制器的开关与安全链的开关改成不同条件（或删掉其中一个），端点就会变成
 * "任何人可读写任意账号的阅读进度"——这条测试会让那次改动直接变红。</p>
 */
@SpringBootTest(properties = {
        "bifrost.db.path=target/test-data/kosync-disabled.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=testpass",
        "bifrost.kosync.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext
class KosyncDisabledIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void allProtocolEndpointsDisappear() throws Exception {
        mockMvc.perform(get("/users/auth")).andExpect(status().isNotFound());
        mockMvc.perform(get("/syncs/progress/0123456789abcdef0123456789abcdef"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/healthcheck")).andExpect(status().isNotFound());
        mockMvc.perform(put("/syncs/progress")).andExpect(status().isNotFound());
    }
}
