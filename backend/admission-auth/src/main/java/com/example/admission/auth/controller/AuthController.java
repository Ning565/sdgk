package com.example.admission.auth.controller;

import com.example.admission.auth.dto.AuthResponse;
import com.example.admission.auth.dto.LoginRequest;
import com.example.admission.auth.dto.RegisterRequest;
import com.example.admission.auth.entity.UserAccount;
import com.example.admission.auth.service.AuthService;
import com.example.admission.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 认证 REST 控制器。基础路径：{@code /api/v1/auth}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 注册新用户。成功后自动登录。
     */
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        log.info("Register attempt: username={}", req.getUsername());
        AuthResponse resp = authService.register(req, httpRequest, httpResponse);
        return ApiResponse.success(resp);
    }

    /**
     * 用户名+密码登录。
     */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                            HttpServletRequest httpRequest,
                                            HttpServletResponse httpResponse) {
        log.info("Login attempt: username={}", req.getUsername());
        AuthResponse resp = authService.login(req, httpRequest, httpResponse);
        return ApiResponse.success(resp);
    }

    /**
     * 登出。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        authService.logout(session);
        return ApiResponse.success();
    }

    /**
     * 当前用户信息。
     */
    @GetMapping("/me")
    public ApiResponse<UserInfo> getCurrentUser() {
        UserAccount user = authService.checkLogin();
        return ApiResponse.success(UserInfo.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .build());
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class UserInfo {
        private Long userId;
        private String username;
        private String nickname;
        private String status;
        private LocalDateTime lastLoginAt;
    }
}
