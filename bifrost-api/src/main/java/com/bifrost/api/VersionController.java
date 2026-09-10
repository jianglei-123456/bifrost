package com.bifrost.api;

import com.bifrost.api.response.ApiResponse;
import com.bifrost.common.constant.BifrostVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 版本查询接口（1.0.0 起提供）：回答"这个部署到底跑的是哪个版本"。
 *
 * <p>与 Subsonic 响应信封的 {@code serverVersion} 同源（{@link BifrostVersion}）。
 * 走 {@code /api/**} 的认证链（Basic / 令牌），未认证返回 401 + code 1002
 * ——它不像 {@code /api/ping} 那样免认证。</p>
 */
@RestController
public class VersionController {

    /** 版本信息（{@code data.version}）。 */
    public record VersionView(String version) {
    }

    @GetMapping("/api/version")
    public ApiResponse<VersionView> version() {
        return ApiResponse.ok(new VersionView(BifrostVersion.VERSION));
    }
}
