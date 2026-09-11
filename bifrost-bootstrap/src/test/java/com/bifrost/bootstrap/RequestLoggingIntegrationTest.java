package com.bifrost.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 请求日志在**整条过滤链最前端**装配（{@code RequestLoggingFilter}）——锁住两件事：
 * ① Bean 真的被扫到（不是"类写了但没注册"）；② 位置在 Spring Security 链之前。
 *
 * <p>断言用的正是"客户端某页面没数据"的排查姿势：认证被拒（error 40）、端点未实现
 * （error 0）的请求也必须留下原样 URL——否则最需要看的那条恰恰看不到。</p>
 */
@SpringBootTest(properties = {"bifrost.db.path=target/test-data/request-log.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=testpass"})
@AutoConfigureMockMvc
class RequestLoggingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /** 认证会失败的请求（口令不对）同样留痕，且口令不进日志。 */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void requestIsLoggedBeforeAuthenticationRejectsIt(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/rest/getAlbumList2.view?u=admin&p=wrong-password&v=1.16.1&c=DSub"
                + "&f=json&type=random&size=10"));

        String log = output.getOut();
        assertTrue(log.contains("/rest/getAlbumList2.view?u=admin&p=***&v=1.16.1&c=DSub"
                + "&f=json&type=random&size=10"), log);
        assertFalse(log.contains("wrong-password"), log);
    }

    /** 未实现端点（走 {@code /{method}} 兜底返回 error 0）同样留痕。 */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void unimplementedEndpointIsLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/rest/getArtistInfo2.view?u=admin&p=testpass&v=1.16.1&c=DSub&id=ar-1"));

        String log = output.getOut();
        assertTrue(log.contains("/rest/getArtistInfo2.view?u=admin&p=***"), log);
        assertTrue(log.contains("<-- 200 GET /rest/getArtistInfo2.view"), log);
    }
}
