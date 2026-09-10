package com.bifrost.api.controller;

import com.bifrost.api.dto.booksync.SyncAccountView;
import com.bifrost.api.response.ApiResponse;
import com.bifrost.common.exception.BizException;
import com.bifrost.core.book.sync.SyncAccountService;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.domain.entity.SyncAccount;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 同步账号管理（M3-sync T3.2，Dashboard「阅读进度」页的"同步账号"卡片）。
 *
 * <p>R2a：口令<b>明文回显</b>（家庭自托管，用户要把它抄进阅读设备）；R2c：口令不得与管理员口令相同
 * （由 {@code SyncAccountService} 强制）。</p>
 */
@RestController
@RequestMapping("/api/book-sync/account")
@RequiredArgsConstructor
public class BookSyncAccountController {

    private final SyncAccountService syncAccountService;
    private final BifrostProperties properties;

    /** 读同步账号（含明文口令与可抄进设备的 {@code serverUrl}）。 */
    @GetMapping
    public ApiResponse<SyncAccountView> get(HttpServletRequest request) {
        return ApiResponse.ok(view(syncAccountService.currentOrCreate(), request));
    }

    /** 改用户名 / 改口令（都可选，只改传了的字段）。 */
    @PutMapping
    public ApiResponse<SyncAccountView> update(@RequestBody(required = false) JsonNode body,
                                               HttpServletRequest request) {
        String username = textOrNull(body == null ? null : body.get("username"));
        String password = textOrNull(body == null ? null : body.get("password"));
        if (username == null && password == null) {
            throw BizException.paramError("username / password 至少提供一个");
        }
        return ApiResponse.ok(view(syncAccountService.update(username, password), request));
    }

    /** 生成随机口令并落库（返回明文，供用户抄进设备）。 */
    @PostMapping("/password")
    public ApiResponse<SyncAccountView> generatePassword(HttpServletRequest request) {
        return ApiResponse.ok(view(syncAccountService.resetPassword(), request));
    }

    private SyncAccountView view(SyncAccount account, HttpServletRequest request) {
        String hint = properties.getKosync().getPublicBaseUrl();
        boolean hintConfigured = hint != null && !hint.isBlank();
        return new SyncAccountView(
                account.getUsername(),
                syncAccountService.revealPassword(account),
                properties.getKosync().isRegistrationEnabled(),
                properties.getKosync().isAutoScanOnUnmatched(),
                hintConfigured ? hint.trim() : fallbackServerUrl(request),
                hintConfigured);
    }

    /**
     * 兜底地址：当前请求的 scheme + host + port。
     *
     * <p>仅在未配置 {@code bifrost.kosync.publicBaseUrl} 时使用。它取的是"管理端访问后端用的地址"，
     * 在反代 / 端口映射场景下未必等于阅读设备可达的地址——所以配置项优先，页面上也应提示用户核对。</p>
     */
    private String fallbackServerUrl(HttpServletRequest request) {
        String scheme = request.getScheme() == null ? "http" : request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
