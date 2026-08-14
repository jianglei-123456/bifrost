package com.bifrost.bootstrap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 测试用 Subsonic 最小端点（阶段 03 验证认证过滤器；阶段 05 由真实 syrinx 控制器取代）。
 */
@RestController
public class TestRestPingController {

    @GetMapping("/rest/ping.view")
    public Map<String, Object> ping() {
        return Map.of("subsonic-response", Map.of(
                "status", "ok",
                "version", "1.16.1",
                "type", "Bifrost",
                "openSubsonic", true));
    }

    @GetMapping("/rest/getOpenSubsonicExtensions.view")
    public Map<String, Object> extensions() {
        return Map.of("subsonic-response", Map.of(
                "status", "ok",
                "version", "1.16.1",
                "openSubsonicExtensions", List.of()));
    }
}
