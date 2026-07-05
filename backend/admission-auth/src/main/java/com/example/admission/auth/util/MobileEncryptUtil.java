package com.example.admission.auth.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 手机号加密、哈希和脱敏工具类。
 * <p>
 * 使用 AES-256-GCM 进行加密（带完整性校验的认证加密），
 * 使用 HMAC-SHA256 进行单向哈希，用于数据库查找。
 * </p>
 * <p>
 * 首次启动时如果未配置加密密钥，会自动生成随机 256 位密钥并打印到日志。
 * 生产环境必须通过 {@code app.security.sms.encryption-key} 配置密钥。
 * </p>
 */
@Slf4j
@Component
public class MobileEncryptUtil {

    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;  // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final SecretKey aesKey;
    private final SecretKey hmacKey;
    private final SecureRandom secureRandom;

    /**
     * 构造工具类，加载或生成 AES 和 HMAC 密钥。
     *
     * @param configuredKey 配置的 Base64 编码 256 位 AES 密钥（可为空）
     */
    public MobileEncryptUtil(@Value("${app.security.sms.encryption-key:}") String configuredKey) {
        this.secureRandom = new SecureRandom();

        if (configuredKey != null && !configuredKey.isBlank()) {
            byte[] keyBytes = Base64.getDecoder().decode(configuredKey);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("Encryption key must be 256 bits (32 bytes) when Base64-decoded");
            }
            this.aesKey = new SecretKeySpec(keyBytes, "AES");
            log.info("Mobile encryption key loaded from configuration");
        } else {
            // 开发环境自动生成密钥；打印日志以便生产配置
            byte[] generatedKey = new byte[32];
            secureRandom.nextBytes(generatedKey);
            this.aesKey = new SecretKeySpec(generatedKey, "AES");
            String base64Key = Base64.getEncoder().encodeToString(generatedKey);
            log.warn("============================================================");
            log.warn("No encryption key configured. Auto-generated key (DEVELOPMENT ONLY):");
            log.warn("app.security.sms.encryption-key: {}", base64Key);
            log.warn("Copy this key to application.yml for production use.");
            log.warn("============================================================");
        }

        // 从 AES 密钥派生 HMAC 密钥（HKDF 方式：对常量做 HMAC-SHA256）
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(this.aesKey);
            byte[] hmacBytes = mac.doFinal("admission-hmac-derivation".getBytes(StandardCharsets.UTF_8));
            this.hmacKey = new SecretKeySpec(hmacBytes, HMAC_SHA256);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive HMAC key", e);
        }
    }

    /**
     * 使用 AES-256-GCM 加密手机号。
     * <p>
     * 输出格式：{@code Base64(IV || ciphertext)}。
     * 12 字节 IV 前置，16 字节 GCM auth tag 由 cipher 自动追加。
     * </p>
     *
     * @param mobile 明文手机号
     * @return Base64 编码的密文（IV + 加密数据 + auth tag）
     */
    public String encrypt(String mobile) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec);
            byte[] ciphertext = cipher.doFinal(mobile.getBytes(StandardCharsets.UTF_8));

            // 将 IV 前置到密文：IV(12) || encrypted_data || auth_tag(16)
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt mobile number", e);
        }
    }

    /**
     * 解密 AES-256-GCM 加密的手机号。
     *
     * @param ciphertext Base64 编码的密文（IV + 加密数据 + auth tag）
     * @return 原始明文手机号
     */
    public String decrypt(String ciphertext) {
        try {
            byte[] data = Base64.getDecoder().decode(ciphertext);
            ByteBuffer byteBuffer = ByteBuffer.wrap(data);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] encrypted = new byte[byteBuffer.remaining()];
            byteBuffer.get(encrypted);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);
            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt mobile number", e);
        }
    }

    /**
     * 计算手机号的 HMAC-SHA256 哈希，用于数据库查找。
     * 单向确定性哈希，可在不暴露原始手机号的情况下做唯一索引。
     *
     * @param mobile 明文手机号
     * @return Base64 编码的 HMAC-SHA256 哈希
     */
    public String hash(String mobile) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(hmacKey);
            byte[] hash = mac.doFinal(mobile.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash mobile number", e);
        }
    }

    /**
     * 对手机号进行脱敏处理，用于展示。
     * <p>
     * 格式：{@code 138****1234}（前 3 位 + "****" + 后 4 位）。
     * </p>
     *
     * @param mobile 明文手机号
     * @return 脱敏后的手机号，不足 7 位时返回原值
     */
    public String mask(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
