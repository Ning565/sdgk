package com.example.admission.auth.service;

import com.example.admission.auth.util.SmsCodeUtil;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 短信验证码发送与校验服务。
 * <p>
 * 本地开发模式下，验证码打印到应用日志，不通过短信服务商发送。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final SmsCodeUtil smsCodeUtil;

    @Value("${app.security.sms.ip-rate-limit-per-minute:10}")
    private int ipRateLimitPerMinute;

    /**
     * 向指定手机号发送短信验证码。
     * <p>
     * 限流检查：
     * <ol>
     *   <li>重发冷却（60 秒）— 防止快速重复发送</li>
     *   <li>每日发送上限（每手机号每天 10 条）</li>
     *   <li>IP 级别限流（每 IP 每分钟 10 条）</li>
     * </ol>
     * 开发模式下验证码打印到日志，不通过短信服务商发送。
     * </p>
     *
     * @param mobile            手机号
     * @param ipAddress         客户端 IP 地址
     * @param deviceFingerprint 客户端设备指纹（预留，用于后续风控）
     * @throws BusinessException 触发限流或发送失败时抛出
     */
    public void sendCode(String mobile, String ipAddress, String deviceFingerprint) {
        // 1. 校验手机号格式
        if (mobile == null || !mobile.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
        }

        // 2. 检查重发冷却（60 秒）
        if (smsCodeUtil.isResendBlocked(mobile)) {
            log.debug("SMS resend blocked (cooldown active) for mobile: {}", mobile.substring(0, 3) + "****");
            throw new BusinessException(ErrorCode.SMS_SEND_TOO_FREQUENT, "验证码发送过于频繁，请60秒后再试");
        }

        // 3. 检查每日上限
        if (smsCodeUtil.isDailyLimitReached(mobile)) {
            log.debug("SMS daily limit reached for mobile: {}", mobile.substring(0, 3) + "****");
            throw new BusinessException(ErrorCode.SMS_SEND_TOO_FREQUENT, "今日验证码发送次数已达上限");
        }

        // 4. 检查 IP 限流
        if (ipAddress != null && !ipAddress.isBlank()) {
            if (!smsCodeUtil.checkIpRateLimit(ipAddress, ipRateLimitPerMinute)) {
                log.debug("SMS IP rate limit reached for IP: {}", ipAddress);
                throw new BusinessException(ErrorCode.SMS_SEND_TOO_FREQUENT, "请求过于频繁，请稍后再试");
            }
        }

        // 5. 生成 6 位验证码
        String code = smsCodeUtil.generateCode();

        // 6. 存入 Redis
        smsCodeUtil.saveCode(mobile, code);

        // 7. 发送短信（开发环境：打印到日志）
        log.info("===========================================");
        log.info("[DEV] SMS verification code for {}: {}", mobile.substring(0, 3) + "****" + mobile.substring(7), code);
        log.info("[DEV] Remaining daily quota: {}", smsCodeUtil.getRemainingDailyQuota(mobile));
        log.info("===========================================");

        // 生产环境在此对接阿里云短信 / 腾讯云短信：
        // smsProvider.send(mobile, code);
    }

    /**
     * 校验指定手机号的短信验证码。
     * <p>
     * 验证码一次性使用：校验成功后从 Redis 删除。
     * 连续 3 次失败后验证码作废。
     * </p>
     *
     * @param mobile 手机号
     * @param code   要校验的 6 位验证码
     * @throws BusinessException 验证码无效或已过期时抛出 {@link ErrorCode#SMS_CODE_ERROR}
     */
    public void verifyCode(String mobile, String code) {
        if (mobile == null || code == null) {
            throw new BusinessException(ErrorCode.SMS_CODE_ERROR, "验证码错误");
        }

        boolean valid = smsCodeUtil.verifyCode(mobile, code);
        if (!valid) {
            throw new BusinessException(ErrorCode.SMS_CODE_ERROR, "验证码错误或已过期");
        }
    }
}
