package com.bifrost.bootstrap.security;

import com.bifrost.adapter.syrinx.subsonic.SubsonicAuthAttributes;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.core.security.AuthenticationService;
import com.bifrost.core.security.SubsonicTokenUtil;
import com.bifrost.domain.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Subsonic 认证过滤器（/rest/**）。
 *
 * <p>解析 u/t/s/p/v/c/f（URL 编码参数）：令牌认证 t=md5(password+salt) 恒定时间比较；
 * 密码认证 p（明文或 enc: hex 解码）。失败响应按协议：<b>HTTP 200</b> + status=failed +
 * 错误码（40 认证失败 / 10 缺参 / 42 apiKey 不支持 / 43 参数冲突 / 20 版本过低 / 30 版本过高）。
 * getOpenSubsonicExtensions 免认证；容忍 DSub 附带的 Authorization 头（忽略）。
 * 认证成功将用户名/客户端名（c）写入请求属性供 Controller 使用。</p>
 */
public class SubsonicAuthenticationFilter extends OncePerRequestFilter {

    /** 请求属性：认证用户名 */
    public static final String ATTR_USERNAME = SubsonicAuthAttributes.USERNAME;
    /** 请求属性：客户端名（c 参数，Q20 playerId） */
    public static final String ATTR_CLIENT = SubsonicAuthAttributes.CLIENT;
    /** 免认证端点（容忍带/不带 .view 后缀，Q21） */
    private static final String OPEN_SUBSONIC_EXTENSIONS = "getOpenSubsonicExtensions";

    private static final String MIN_SUPPORTED_VERSION = "1.0.0";

    private final AuthenticationService authenticationService;
    private final BifrostProperties properties;

    public SubsonicAuthenticationFilter(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        this.properties = null;
    }

    public SubsonicAuthenticationFilter(AuthenticationService authenticationService, BifrostProperties properties) {
        this.authenticationService = authenticationService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && isOpenSubsonicExtensionsPath(uri)) {
            chain.doFilter(request, response); // 免认证（Q21）
            return;
        }
        String u = request.getParameter("u");
        String v = request.getParameter("v");
        String c = request.getParameter("c");
        String t = request.getParameter("t");
        String s = request.getParameter("s");
        String p = request.getParameter("p");

        if (u == null || v == null || c == null) {
            writeError(request, response, 10, "Required parameter is missing");
            return;
        }
        String apiVersion = properties != null && properties.getSubsonic() != null
                ? properties.getSubsonic().getApiVersion() : "1.16.1";
        if (compareVersions(v, apiVersion) > 0) {
            writeError(request, response, 30, "Incompatible Subsonic REST protocol version. Server must upgrade.");
            return;
        }
        if (compareVersions(v, MIN_SUPPORTED_VERSION) < 0) {
            writeError(request, response, 20, "Incompatible Subsonic REST protocol version. Client must upgrade.");
            return;
        }
        if (request.getParameter("apiKey") != null) {
            writeError(request, response, 42, "Provided authentication mechanism is not supported");
            return;
        }
        boolean hasToken = t != null || s != null;
        if (hasToken && p != null) {
            writeError(request, response, 43, "Conflicting authentication parameters");
            return;
        }
        if (hasToken && (t == null || s == null)) {
            writeError(request, response, 10, "Required parameter is missing");
            return;
        }

        User user;
        if (hasToken) {
            user = authenticationService.authenticateByToken(u, t, s).orElse(null);
        } else if (p != null) {
            user = authenticationService.authenticateByPassword(u, SubsonicTokenUtil.decodePasswordParam(p)).orElse(null);
        } else {
            writeError(request, response, 10, "Required parameter is missing");
            return;
        }
        if (user == null) {
            writeError(request, response, 40, "Wrong username or password");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(ATTR_USERNAME, user.getUsername());
        request.setAttribute(ATTR_CLIENT, c);
        chain.doFilter(request, response);
    }

    /** 免认证端点路径判断：最后一段为 getOpenSubsonicExtensions（.view 后缀可选）。 */
    private static boolean isOpenSubsonicExtensionsPath(String uri) {
        String last = uri.substring(uri.lastIndexOf('/') + 1);
        if (last.endsWith(".view")) {
            last = last.substring(0, last.length() - ".view".length());
        }
        return OPEN_SUBSONIC_EXTENSIONS.equals(last);
    }

    /** 协议错误响应：HTTP 200 + status=failed（XML 默认，f=json 切 JSON）。 */
    private void writeError(HttpServletRequest request, HttpServletResponse response, int code, String message)
            throws IOException {
        String apiVersion = properties != null && properties.getSubsonic() != null
                ? properties.getSubsonic().getApiVersion() : "1.16.1";
        response.setStatus(HttpServletResponse.SC_OK);
        String f = request.getParameter("f");
        if ("json".equalsIgnoreCase(f)) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"subsonic-response\":{\"status\":\"failed\",\"version\":\""
                    + apiVersion + "\",\"error\":{\"code\":" + code + ",\"message\":\""
                    + escapeJson(message) + "\"}}}");
        } else {
            response.setContentType("text/xml;charset=UTF-8");
            response.getWriter().write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<subsonic-response xmlns=\"http://subsonic.org/restapi\" status=\"failed\" version=\""
                    + apiVersion + "\"><error code=\"" + code + "\" message=\""
                    + escapeXml(message) + "\"/></subsonic-response>");
        }
    }

    /** 版本比较：a > b → 正数；a < b → 负数；相等 → 0（按 主.次.修订 数值比较）。 */
    static int compareVersions(String a, String b) {
        int[] av = parseVersion(a);
        int[] bv = parseVersion(b);
        for (int i = 0; i < 3; i++) {
            if (av[i] != bv[i]) {
                return Integer.compare(av[i], bv[i]);
            }
        }
        return 0;
    }

    private static int[] parseVersion(String v) {
        int[] parts = new int[3];
        String[] split = (v == null ? "" : v).split("\\.");
        for (int i = 0; i < 3 && i < split.length; i++) {
            try {
                parts[i] = Integer.parseInt(split[i].trim());
            } catch (NumberFormatException e) {
                parts[i] = 0;
            }
        }
        return parts;
    }

    private static String escapeXml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
