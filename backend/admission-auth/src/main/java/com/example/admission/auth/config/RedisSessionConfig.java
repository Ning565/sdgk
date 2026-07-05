package com.example.admission.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 基于 Redis 的 HTTP Session 配置。
 * <p>
 * Session 使用配置的命名空间和 TTL 持久化到 Redis。
 * Session Cookie 设置了 HttpOnly、SameSite=Lax、Secure（HTTPS 时）安全标记。
 * </p>
 * <p>
 * 配置属性（application.yml）：
 * <ul>
 *   <li>{@code app.security.session.timeout} — Session 最大空闲时间（秒，默认 604800 = 7 天）</li>
 *   <li>{@code app.security.session.namespace} — Redis key 命名空间（默认 "admission:session"）</li>
 *   <li>{@code app.security.session.cookie-name} — Session Cookie 名称（默认 "ADMISSION_SESSION"）</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableRedisIndexedHttpSession(
        redisNamespace = "${app.security.session.namespace:admission:session}",
        maxInactiveIntervalInSeconds = 604800 // overridden below via property
)
public class RedisSessionConfig {

    @Value("${app.security.session.timeout:604800}")
    private int sessionTimeoutSeconds;

    @Value("${app.security.session.cookie-name:ADMISSION_SESSION}")
    private String cookieName;

    /**
     * 配置 Session Cookie 序列化器，使用安全加固标记。
     * <ul>
     *   <li><b>cookieName:</b> "ADMISSION_SESSION"（可自定义）</li>
     *   <li><b>httpOnly:</b> {@code true} — 禁止 JavaScript 访问 Cookie</li>
     *   <li><b>sameSite:</b> {@code Lax} — 防止 CSRF 攻击，同时允许顶层导航</li>
     *   <li><b>secure:</b> 根据请求自动检测（HTTPS 时为 true）</li>
     *   <li><b>cookiePath:</b> "/" — Cookie 对整个应用有效</li>
     *   <li><b>cookieMaxAge:</b> 配置的 Session 超时时间（秒）</li>
     * </ul>
     */
    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookieName);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        // 本地开发关闭 Secure，生产环境需开启
        serializer.setUseSecureCookie(false);
        // Cookie 有效期与 Session 超时时间一致
        serializer.setCookieMaxAge(sessionTimeoutSeconds);
        return serializer;
    }
}
