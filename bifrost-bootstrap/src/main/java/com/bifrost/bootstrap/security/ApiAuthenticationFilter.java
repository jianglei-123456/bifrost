package com.bifrost.bootstrap.security;

import com.bifrost.api.response.ApiResponse;
import com.bifrost.common.constant.ErrorCodes;
import com.bifrost.core.security.AuthenticationService;
import com.bifrost.domain.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 管理 REST 认证过滤器（/api/**）。
 *
 * <p>认证方式：① HTTP Basic（用户名:密码）；② 与 Subsonic 同款 t/s 令牌（同一账号，Q21）。
 * 失败：HTTP 401 + 信封 {@code {code:1002}}；{@code /api/ping} 免认证。</p>
 */
public class ApiAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiAuthenticationFilter(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isExempt(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        User user = authenticate(request);
        if (user == null) {
            writeUnauthorized(response);
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private User authenticate(HttpServletRequest request) {
        // ① t/s 令牌（优先）
        String u = request.getParameter("u");
        String t = request.getParameter("t");
        String s = request.getParameter("s");
        if (u != null && t != null && s != null) {
            return authenticationService.authenticateByToken(u, t, s).orElse(null);
        }
        // ② HTTP Basic
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Basic ")) {
            try {
                String decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                if (colon > 0) {
                    return authenticationService.authenticateByPassword(decoded.substring(0, colon),
                            decoded.substring(colon + 1)).orElse(null);
                }
            } catch (IllegalArgumentException ignored) {
                // 非法 Base64 → 未认证
            }
        }
        return null;
    }

    private boolean isExempt(String uri) {
        return "/api/ping".equals(uri);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未认证")));
    }
}
