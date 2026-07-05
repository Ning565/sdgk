package com.example.admission.auth.controller;

import com.example.admission.common.ApiResponse;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员认证 REST 控制器。基础路径：{@code /api/admin/v1/auth}
 *
 * <p>MVP 实现：使用硬编码的管理员账号（username="admin", password="admin123"），
 * Session 属性名为 {@code adminUserId}，与用户端的 {@code userId} 隔离。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/v1/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private static final String SESSION_ADMIN_USER_ID = "adminUserId";

    private static final String ADMIN_USERNAME = "admin";

    /** BCrypt hash of "admin123" */
    private static final String ADMIN_PASSWORD_HASH =
            "$2a$10$ecLv6f5fwQXeAwjqv3An.uYjUcbm/JuHDR82jMljoMb/PxNxvPMFi";

    private static final long ADMIN_ID = 1L;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 管理员用户名+密码登录。
     */
    @PostMapping("/login")
    public ApiResponse<AdminInfo> login(@Valid @RequestBody AdminLoginRequest req,
                                        HttpServletRequest request) {
        log.info("Admin login attempt: username={}", req.getUsername());

        if (!ADMIN_USERNAME.equals(req.getUsername())) {
            log.warn("Admin login failed: bad username={}", req.getUsername());
            throw new BusinessException(ErrorCode.ADMIN_NOT_EXIST);
        }

        if (!passwordEncoder.matches(req.getPassword(), ADMIN_PASSWORD_HASH)) {
            log.warn("Admin login failed: wrong password for username={}", req.getUsername());
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // 创建管理员 Session
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_ADMIN_USER_ID, ADMIN_ID);
        session.setMaxInactiveInterval(86400); // 24 小时

        log.info("Admin logged in successfully");

        AdminInfo info = AdminInfo.builder()
                .adminUserId(ADMIN_ID)
                .username(ADMIN_USERNAME)
                .build();
        return ApiResponse.success(info);
    }

    /**
     * 管理员登出。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        if (session != null) {
            Object adminId = session.getAttribute(SESSION_ADMIN_USER_ID);
            log.info("Admin logged out: adminUserId={}", adminId);
            session.invalidate();
        }
        return ApiResponse.success();
    }

    /**
     * 获取当前管理员信息。
     */
    @GetMapping("/me")
    public ApiResponse<AdminInfo> getCurrentAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_ADMIN_USER_ID) == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        AdminInfo info = AdminInfo.builder()
                .adminUserId(ADMIN_ID)
                .username(ADMIN_USERNAME)
                .build();
        return ApiResponse.success(info);
    }

    // --- DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminInfo {
        private Long adminUserId;
        private String username;
    }
}
