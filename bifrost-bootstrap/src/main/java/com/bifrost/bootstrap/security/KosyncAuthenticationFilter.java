package com.bifrost.bootstrap.security;

import com.bifrost.adapter.kosync.KosyncAuthAttributes;
import com.bifrost.adapter.kosync.KosyncConstants;
import com.bifrost.adapter.kosync.dto.KosyncErrorResponse;
import com.bifrost.core.book.sync.SyncAccountService;
import com.bifrost.domain.entity.SyncAccount;
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
import java.util.List;

/**
 * KOSync 认证过滤器（{@code /users/**}、{@code /syncs/**}；{@code /healthcheck} 匿名）。
 *
 * <p>凭据 = {@code x-auth-user} + {@code x-auth-key}（口令的 md5 小写 hex），与管理员账号体系
 * <b>完全独立</b>（Q3-B/Q6-A）。</p>
 *
 * <p>失败一律 <b>401</b>：这是客户端唯一不会丢进离线重试队列的失败码（重试也没用——凭据错了），
 * 且响应体带 {@code message}（客户端会原样展示）。<b>不设</b> {@code WWW-Authenticate}：那是浏览器
 * Basic 语义，会让某些客户端误触发认证流程。</p>
 */
public class KosyncAuthenticationFilter extends OncePerRequestFilter {

    private final SyncAccountService syncAccountService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KosyncAuthenticationFilter(SyncAccountService syncAccountService) {
        this.syncAccountService = syncAccountService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isExempt(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        SyncAccount account = syncAccountService.authenticate(
                        request.getHeader(KosyncConstants.HEADER_USER),
                        request.getHeader(KosyncConstants.HEADER_KEY))
                .orElse(null);
        if (account == null) {
            writeUnauthorized(response);
            return;
        }
        request.setAttribute(KosyncAuthAttributes.ATTR_SYNC_ACCOUNT_ID, account.getId());
        request.setAttribute(KosyncAuthAttributes.ATTR_SYNC_USERNAME, account.getUsername());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                account.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_SYNC"))));
        chain.doFilter(request, response);
    }

    /**
     * 免认证路径。
     *
     * <p>{@code /healthcheck}：探活，官方亦匿名。</p>
     *
     * <p>{@code /users/create}：<b>客户端注册时根本不发认证头</b>（{@code KOSyncClient.lua:66-68} 只挂了
     * JSON 与 GinClient 中间件，没有挂 KOSyncAuth），所以注册端点必须匿名——它的"鉴权"是
     * "用户名 + 口令必须与已配置的同步账号一致"（M3-sync T2.4 ①）。</p>
     */
    private boolean isExempt(String uri) {
        return KosyncConstants.PATH_HEALTHCHECK.equals(uri)
                || KosyncConstants.PATH_USERS_CREATE.equals(uri);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                new KosyncErrorResponse(KosyncConstants.CODE_UNAUTHORIZED, "未授权：请检查同步账号与口令")));
    }
}
