package com.example.admission.auth.service;

import com.example.admission.auth.dto.AuthResponse;
import com.example.admission.auth.dto.LoginRequest;
import com.example.admission.auth.dto.RegisterRequest;
import com.example.admission.auth.entity.UserAccount;
import com.example.admission.auth.mapper.UserAccountMapper;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 核心认证服务：账号密码登录、注册、Session 管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    static final String SESSION_USER_ID = "userId";

    @Value("${app.security.session.timeout:604800}")
    private int sessionTimeoutSeconds;

    /**
     * 注册新用户.
     */
    @Transactional
    public AuthResponse register(RegisterRequest req, HttpServletRequest request, HttpServletResponse response) {
        // 检查用户名是否已存在
        UserAccount existing = userAccountMapper.selectByUsername(req.getUsername());
        if (existing != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        UserAccount user = new UserAccount();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getUsername());
        user.setMobileMasked("");
        user.setStatus(UserAccount.STATUS_ACTIVE);
        user.setLastLoginAt(LocalDateTime.now());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userAccountMapper.insert(user);

        log.info("New user registered: userId={}, username={}", user.getId(), req.getUsername());

        // 注册后自动登录
        createSession(request, user);
        return buildResponse(user);
    }

    /**
     * 用户名+密码登录.
     */
    @Transactional
    public AuthResponse login(LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        UserAccount user = userAccountMapper.selectByUsername(req.getUsername());
        if (user == null) {
            log.warn("Login failed: username not found: {}", req.getUsername());
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: wrong password for username: {}", req.getUsername());
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        if (UserAccount.STATUS_DISABLED.equals(user.getStatus())) {
            log.warn("Login attempt on disabled account: userId={}", user.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userAccountMapper.updateById(user);

        createSession(request, user);
        log.info("User logged in: userId={}, username={}", user.getId(), req.getUsername());
        return buildResponse(user);
    }

    /**
     * 登出.
     */
    public void logout(HttpSession session) {
        if (session != null) {
            Long userId = (Long) session.getAttribute(SESSION_USER_ID);
            log.info("User logged out: userId={}", userId);
            session.invalidate();
        }
    }

    /**
     * 获取当前用户（未登录返回 null）.
     */
    public UserAccount getCurrentUser() {
        Long userId = getCurrentUserId();
        if (userId == null) return null;
        return userAccountMapper.selectById(userId);
    }

    /**
     * 获取当前用户（未登录抛异常）.
     */
    public UserAccount checkLogin() {
        UserAccount user = getCurrentUser();
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (UserAccount.STATUS_DISABLED.equals(user.getStatus())) throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        return user;
    }

    public Long getCurrentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            if (auth.getPrincipal() instanceof UserAccount u) return u.getId();
        }
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sa) {
            HttpSession session = sa.getRequest().getSession(false);
            if (session != null) {
                Object uid = session.getAttribute(SESSION_USER_ID);
                if (uid instanceof Long) return (Long) uid;
            }
        }
        return null;
    }

    private void createSession(HttpServletRequest request, UserAccount user) {
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_USER_ID, user.getId());
        session.setMaxInactiveInterval(sessionTimeoutSeconds);
    }

    private AuthResponse buildResponse(UserAccount user) {
        return AuthResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname() != null ? user.getNickname() : user.getUsername())
                .needProfile(true)
                .build();
    }
}
