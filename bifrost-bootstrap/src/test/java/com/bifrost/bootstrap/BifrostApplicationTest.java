package com.bifrost.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 01 验收测试：上下文可启动（含 SQLite 建库）且 /api/ping 可用。
 */
@SpringBootTest(properties = {"bifrost.db.path=target/test-data/test.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=testpass"})
@AutoConfigureMockMvc
class BifrostApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        // 仅验证上下文加载成功
    }

    @Test
    void pingReturnsPong() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }
}
