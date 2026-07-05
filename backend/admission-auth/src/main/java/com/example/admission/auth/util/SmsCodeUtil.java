package com.example.admission.auth.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码生成、存储和校验工具类，基于 Redis 实现。
 * <p>
 * Redis key 模式：
 * <ul>
 *   <li>{@code sms:code:{mobile}} — 当前验证码（带 TTL）</li>
 *   <li>{@code sms:retry:{mobile}} — 重发冷却标记</li>
 *   <li>{@code sms:daily:{mobile}:{date}} — 每日发送计数</li>
 *   <li>{@code sms:attempts:{mobile}} — 失败校验尝试计数</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsCodeUtil {

    private static final String CODE_PREFIX = "sms:code:";
    private static final String RETRY_PREFIX = "sms:retry:";
    private static final String DAILY_PREFIX = "sms:daily:";
    private static final String ATTEMPTS_PREFIX = "sms:attempts:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.sms.code-expiration-seconds:300}")
    private int codeExpireSeconds;

    @Value("${app.security.sms.resend-interval-seconds:60}")
    private int resendIntervalSeconds;

    @Value("${app.security.sms.daily-send-limit:10}")
    private int dailySendLimit;

    @Value("${app.security.sms.max-verify-attempts:3}")
    private int maxVerifyAttempts;

    // ---- Key 构造 ----

    private String codeKey(String mobile) {
        return CODE_PREFIX + mobile;
    }

    private String retryKey(String mobile) {
        return RETRY_PREFIX + mobile;
    }

    private String dailyKey(String mobile) {
        return DAILY_PREFIX + mobile + ":" + LocalDate.now();
    }

    private String attemptsKey(String mobile) {
        return ATTEMPTS_PREFIX + mobile;
    }

    // ---- 验证码生成 ----

    /**
     * 生成密码学安全的 6 位数字验证码。
     *
     * @return 6 位数字字符串（如 "003817"）
     */
    public String generateCode() {
        int code = RANDOM.nextInt(1_000_000); // 0 - 999999
        return String.format("%06d", code);
    }

    // ---- 验证码存储 ----

    /**
     * 将验证码存入 Redis，设置过期时间和重发冷却标记。
     *
     * @param mobile 手机号
     * @param code   6 位验证码
     */
    public void saveCode(String mobile, String code) {
        String key = codeKey(mobile);
        redisTemplate.opsForValue().set(key, code, Duration.ofSeconds(codeExpireSeconds));

        // 设置重发冷却
        String retryKey = retryKey(mobile);
        redisTemplate.opsForValue().set(retryKey, "1", Duration.ofSeconds(resendIntervalSeconds));

        // 递增每日计数（首次设置 24h TTL）
        String dailyKey = dailyKey(mobile);
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1) {
            redisTemplate.expire(dailyKey, 24, TimeUnit.HOURS);
        }

        // 新验证码下发后重置失败尝试计数
        redisTemplate.delete(attemptsKey(mobile));

        log.debug("Verification code saved for mobile: {}", mobile);
    }

    // ---- 限流检查 ----

    /**
     * 检查是否在重发冷却间隔内已向此手机号发送过验证码。
     *
     * @param mobile 手机号
     * @return 冷却期生效中（不可重发）返回 {@code true}
     */
    public boolean isResendBlocked(String mobile) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(retryKey(mobile)));
    }

    /**
     * 检查此手机号的每日发送上限是否已达。
     *
     * @param mobile 手机号
     * @return 已达每日上限返回 {@code true}
     */
    public boolean isDailyLimitReached(String mobile) {
        String count = redisTemplate.opsForValue().get(dailyKey(mobile));
        if (count == null) {
            return false;
        }
        try {
            return Integer.parseInt(count) >= dailySendLimit;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 返回今日剩余可发送条数。
     *
     * @param mobile 手机号
     * @return 剩余每日配额（达上限时为 0）
     */
    public int getRemainingDailyQuota(String mobile) {
        String count = redisTemplate.opsForValue().get(dailyKey(mobile));
        if (count == null) {
            return dailySendLimit;
        }
        try {
            int used = Integer.parseInt(count);
            return Math.max(0, dailySendLimit - used);
        } catch (NumberFormatException e) {
            return dailySendLimit;
        }
    }

    // ---- IP 限流 ----

    /**
     * 检查并递增按 IP 的短信发送速率计数器。
     *
     * @param ipAddress    客户端 IP 地址
     * @param maxPerMinute 每分钟最大发送数
     * @return 未触发限流返回 {@code true}，被阻止返回 {@code false}
     */
    public boolean checkIpRateLimit(String ipAddress, int maxPerMinute) {
        String ipKey = "sms:ip:" + ipAddress;
        Long count = redisTemplate.opsForValue().increment(ipKey);
        if (count != null && count == 1) {
            redisTemplate.expire(ipKey, 60, TimeUnit.SECONDS);
        }
        return count == null || count <= maxPerMinute;
    }

    // ---- 校验 ----

    /**
     * 校验用户输入的验证码是否与 Redis 中存储的一致。
     * <p>
     * 追踪失败尝试：连续失败达到 {@code maxVerifyAttempts} 次后，验证码作废并删除。
     * </p>
     *
     * @param mobile 手机号
     * @param code   待校验的验证码
     * @return 验证码匹配且有效返回 {@code true}
     */
    public boolean verifyCode(String mobile, String code) {
        String key = codeKey(mobile);
        String storedCode = redisTemplate.opsForValue().get(key);

        // 验证码不存在（已过期或从未发送）
        if (storedCode == null) {
            log.debug("Verification code not found for mobile: {}", mobile);
            return false;
        }

        // 验证码匹配
        if (storedCode.equals(code)) {
            // 校验成功后删除验证码（一次性使用）
            redisTemplate.delete(key);
            redisTemplate.delete(attemptsKey(mobile));
            log.debug("Verification code verified successfully for mobile: {}", mobile);
            return true;
        }

        // 验证码不匹配 — 记录失败尝试
        String attemptsKey = attemptsKey(mobile);
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null) {
            // 首次失败时设置与验证码相同的 TTL
            if (attempts == 1) {
                redisTemplate.expire(attemptsKey, Duration.ofSeconds(codeExpireSeconds));
            }
            // 失败次数过多后作废验证码
            if (attempts >= maxVerifyAttempts) {
                redisTemplate.delete(key);
                redisTemplate.delete(attemptsKey);
                log.debug("Verification code invalidated after {} failed attempts for mobile: {}",
                        attempts, mobile);
            }
        }
        return false;
    }

    /**
     * 删除指定手机号的已存储验证码。
     * 用于登出或清理场景。
     *
     * @param mobile 手机号
     */
    public void deleteCode(String mobile) {
        redisTemplate.delete(codeKey(mobile));
        redisTemplate.delete(attemptsKey(mobile));
    }
}
