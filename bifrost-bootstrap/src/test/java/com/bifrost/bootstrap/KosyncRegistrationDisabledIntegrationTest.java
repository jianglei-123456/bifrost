package com.bifrost.bootstrap;

import com.bifrost.core.book.sync.SyncAccountService;
import com.bifrost.core.security.SubsonicTokenUtil;
import com.bifrost.domain.entity.SyncAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 关闭自助注册后的行为（M3-sync T2.4，R2b 的开关）。
 *
 * <p>注册关闭不影响已配置账号的登录与上报——设备上改用 Login 即可。</p>
 */
@SpringBootTest(properties = {
        "bifrost.db.path=target/test-data/kosync-no-registration.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=testpass",
        "bifrost.kosync.registration-enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext
class KosyncRegistrationDisabledIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SyncAccountService syncAccountService;
    @Autowired private ObjectMapper objectMapper;

    private String username;
    private String authKey;

    @BeforeEach
    void setUp() {
        SyncAccount account = syncAccountService.currentOrCreate();
        username = account.getUsername();
        authKey = SubsonicTokenUtil.token(syncAccountService.revealPassword(account), "");
    }

    @Test
    void registrationIsRejectedWith402() throws Exception {
        mockMvc.perform(post("/users/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", authKey))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value(3004));
    }

    @Test
    void loginStillWorksWhenRegistrationIsDisabled() throws Exception {
        mockMvc.perform(get("/users/auth")
                        .header("x-auth-user", username)
                        .header("x-auth-key", authKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorized").value("OK"));
    }
}
