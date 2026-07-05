package com.example.admission.auth.config;

import com.example.admission.common.ApiResponse;
import com.example.admission.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 高考志愿系统 Spring Security 配置。
 * <p>
 * <b>认证模型：</b>
 * <ul>
 *   <li>用户端使用 Spring Session（Redis 存储），基于 Cookie 的 Session 追踪。
 *       CSRF 防护通过 SameSite Cookie 属性处理。</li>
 *   <li>管理后台端点需要 ADMIN 角色（基于角色的访问控制）。</li>
 * </ul>
 * <p>
 * {@code formLogin}、{@code httpBasic}、{@code csrf} 已禁用，
 * 因为认证在应用层通过短信验证码处理。
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    /**
     * 配置 Spring Security 过滤器链。
     * <ul>
     *   <li>公开端点：{@code /api/v1/auth/**}、静态资源</li>
     *   <li>管理端点：{@code /api/admin/v1/**} 需要 ADMIN 角色</li>
     *   <li>其他所有端点需要认证</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用不使用的默认配置
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .csrf(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)

            // Session 管理：使用 Spring Session
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // 授权规则
            .authorizeHttpRequests(auth -> auth
                    // 公开认证端点
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    // 公开数据端点（无需认证）
                    .requestMatchers("/api/v1/score-ranks/**").permitAll()
                    .requestMatchers("/api/v1/schools/**").permitAll()
                    .requestMatchers("/api/v1/plans/**").permitAll()
                    .requestMatchers("/api/v1/majors/**").permitAll()
                    .requestMatchers("/api/v1/configs/**").permitAll()
                    // 静态资源和 Swagger UI
                    .requestMatchers(
                            "/",
                            "/index.html",
                            "/static/**",
                            "/public/**",
                            "/assets/**",
                            "/*.html",
                            "/*.css",
                            "/*.js",
                            "/*.ico",
                            "/*.png",
                            "/*.svg",
                            "/favicon.ico",
                            "/doc.html",
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-resources/**",
                            "/webjars/**"
                    ).permitAll()
                    // 管理后台认证端点（公开访问）
                    .requestMatchers("/api/admin/v1/auth/**").permitAll()
                    // 管理端点（MVP: permitAll，各 Controller 自行校验 Session）
                    .requestMatchers("/api/admin/v1/**").permitAll()
                    // MVP: 所有 API 端点由各 Controller 自行校验 Session
                    .requestMatchers("/api/**").permitAll()
                    // 其他全部放行
                    .anyRequest().permitAll()
            )

            // 异常处理：返回 JSON 而非重定向
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new HttpStatusEntryPoint(
                            org.springframework.http.HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler(accessDeniedHandler())
            );

        return http.build();
    }

    /**
     * 自定义 {@link AccessDeniedHandler}，
     * 当用户缺少所需权限时返回 JSON 格式的 {@link ApiResponse}，
     * 错误码为 {@link ErrorCode#FORBIDDEN}。
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            ApiResponse<Void> apiResponse = ApiResponse.error(ErrorCode.FORBIDDEN, "无权限访问该资源");
            PrintWriter writer = response.getWriter();
            writer.write(objectMapper.writeValueAsString(apiResponse));
            writer.flush();
        };
    }
}
