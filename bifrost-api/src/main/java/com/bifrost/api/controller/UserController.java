package com.bifrost.api.controller;

import com.bifrost.api.response.ApiResponse;
import com.bifrost.core.security.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 账号 API（《通用功能说明》§9.2：GET /api/user、PUT /api/user/password）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final AuthenticationService authenticationService;

    /** 查看当前账号（单用户语义，v1=admin）。 */
    @GetMapping("/user")
    public ApiResponse<Map<String, Object>> currentUser() {
        return ApiResponse.ok(Map.of(
                "username", currentUsername(),
                "role", "ADMIN"));
    }

    /** 修改密码（校验旧密码，AES-GCM 重加密存储）。 */
    @PutMapping("/user/password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body) {
        authenticationService.changePassword(currentUsername(),
                body.get("oldPassword"), body.get("newPassword"));
        return ApiResponse.ok();
    }

    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }
}
