package com.bifrost.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端 SPA 托管与根命名空间边界（ADR-0007，1.0.0 起前后端同镜像）。
 *
 * <p>夹具 {@code src/test/resources/static/admin/index.html} 代替真实前端产物
 * （测试 classpath 优先于 {@code src/main/resources}）。</p>
 *
 * <p>这组断言锁住的关键取舍：<b>SPA 只占 /admin/**</b>——协议端点的根命名空间
 * 不被兜底吃掉，拼错的路径必须还是 404，而不是 200 + 一页 HTML。</p>
 */
@SpringBootTest(properties = {"bifrost.db.path=target/test-data/admin-spa.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=testpass"})
@AutoConfigureMockMvc
class AdminSpaIntegrationTest {

    private static final String FIXTURE_MARKER = "bifrost-admin-fixture";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootRedirectsToAdmin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin/"));
    }

    @Test
    void adminWithoutTrailingSlashRedirects() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin/"));
    }

    /**
     * {@code /admin/}：Spring 的 ResourceHttpRequestHandler 对空资源路径直接 404
     * （不调用解析器），所以由 {@code AdminWebConfig} 显式转发到 index.html。
     * MockMvc 不执行内部转发（只记录 forwardedUrl），因此这里断言状态 + 转发目标；
     * "转发目标真的能出内容"由 {@link #spaDeepLinkFallsBackToIndex()} 覆盖。
     */
    @Test
    void adminIndexIsServed() throws Exception {
        mockMvc.perform(get("/admin/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/index.html"));
    }

    @Test
    void adminIndexHtmlIsServed() throws Exception {
        mockMvc.perform(get("/admin/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(FIXTURE_MARKER)));
    }

    @Test
    void spaDeepLinkFallsBackToIndex() throws Exception {
        mockMvc.perform(get("/admin/books/12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(FIXTURE_MARKER)));
    }

    @Test
    void missingAssetIsNotFound() throws Exception {
        mockMvc.perform(get("/admin/assets/nope.js"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownRootPathIsNotFound() throws Exception {
        mockMvc.perform(get("/nosuch/path"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownOpdsPathIsNotFound() throws Exception {
        mockMvc.perform(get("/opds/v1.2/nosuch"))
                .andExpect(status().isNotFound());
    }
}
