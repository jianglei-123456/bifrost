package com.bifrost.bootstrap.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 管理端 SPA（Vue Dashboard）静态托管：镜像内前后端同源部署的落点（ADR-0007）。
 *
 * <p>产物在 jar 的 {@code /static/admin/}（构建期由 dashboard 的
 * {@code vite build --base=/admin/} 填入，见操作手册 05）。挂载在 <b>/admin/ 子路径</b>，
 * 而不是根路径——根命名空间完整留给协议端点（{@code /rest/**}、{@code /opds/**}、
 * {@code /users}、{@code /syncs}、{@code /healthcheck}），因此协议客户端拼错的路径
 * 不会被 SPA 兜底"吃掉"成 200 + HTML。</p>
 *
 * <p>兜底规则：{@code /admin/**} 下找不到资源时，只有"末段不含 {@code .} 的路径"
 * 才回 {@code index.html}（SPA history 路由刷新），带扩展名的资源缺失仍 404
 * ——否则一个拼错的 {@code /admin/assets/x.js} 会返回 HTML，浏览器报 MIME 错而不是 404。</p>
 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    /** jar 内管理端产物位置（与 docker/Dockerfile 的 COPY 目标一致） */
    static final String ADMIN_LOCATION = "classpath:/static/admin/";

    private static final String ADMIN_PATTERN = "/admin/**";
    private static final String INDEX_HTML = "index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ADMIN_PATTERN)
                .addResourceLocations(ADMIN_LOCATION)
                .resourceChain(false)
                .addResolver(new SpaFallbackResolver());
    }

    /**
     * {@code /admin/}（带尾斜杠）显式转发到 {@code index.html}。
     *
     * <p>必须在这里做，不能只靠资源解析器：{@code ResourceHttpRequestHandler} 对"空资源路径"
     * <b>直接返回 404、根本不调用解析器链</b>（{@code /admin/} 正好会被规范化成空路径，
     * 实测解析器一次都没被调用）。所以管理端首页这个请求由这一段显式认领。</p>
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }

    /**
     * SPA 兜底：未命中的无扩展名路径 → index.html。
     *
     * <p>带扩展名的资源缺失时返回 null（让它 404），否则一个拼错的 {@code /admin/assets/x.js}
     * 会返回 HTML，浏览器报 MIME 错而不是 404。</p>
     */
    static class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            String path = resourcePath == null ? "" : resourcePath;
            if (!isIndexRequest(path)) {
                Resource requested = location.createRelative(path);
                if (requested.exists() && requested.isReadable()) {
                    return requested;
                }
                if (hasExtension(path)) {
                    return null;
                }
            }
            return indexIfPresent(location);
        }

        /** 空路径 / 尾斜杠 / {@code "."} 都视为"要 index.html"（后两者是 Spring 规范化后的形态）。 */
        private static boolean isIndexRequest(String path) {
            return path.isEmpty() || path.endsWith("/") || ".".equals(path);
        }

        private static Resource indexIfPresent(Resource location) throws IOException {
            Resource index = location.createRelative(INDEX_HTML);
            return index.exists() && index.isReadable() ? index : null;
        }

        private static boolean hasExtension(String resourcePath) {
            int slash = resourcePath.lastIndexOf('/');
            String last = slash < 0 ? resourcePath : resourcePath.substring(slash + 1);
            return last.indexOf('.') >= 0;
        }
    }
}
