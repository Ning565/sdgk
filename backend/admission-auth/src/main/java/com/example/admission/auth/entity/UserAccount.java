package com.example.admission.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账号实体，映射到 {@code user_account} 表。
 * <p>
 * 手机号以 AES-256-GCM 加密存储在 {@code mobile_ciphertext}，
 * 以 HMAC-SHA256 哈希存储在 {@code mobile_hash} 用于唯一性查找，
 * 以脱敏形式（如 "138****1234"）存储在 {@code mobile_masked} 用于展示。
 * </p>
 */
@Data
@TableName("user_account")
public class UserAccount {

    /**
     * 主键，自增。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名，用于登录。
     */
    private String username;

    /**
     * BCrypt 密码哈希。
     */
    private String passwordHash;

    /**
     * AES-256-GCM 加密后的手机号（Base64 编码）。可选字段。
     */
    private String mobileCiphertext;

    /**
     * 手机号 HMAC-SHA256 哈希，用于唯一索引和查找。
     */
    private String mobileHash;

    /**
     * 脱敏手机号，用于展示（如 "138****1234"）。
     */
    private String mobileMasked;

    /**
     * 用户展示昵称。
     */
    private String nickname;

    /**
     * 头像图片 URL。
     */
    private String avatarUrl;

    /**
     * 账号状态：{@code ACTIVE}（正常）或 {@code DISABLED}（禁用）。
     */
    private String status;

    /**
     * 最后一次成功登录的时间戳。
     */
    private LocalDateTime lastLoginAt;

    /**
     * 记录创建时间，插入时自动填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间，插入和更新时自动填充。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // --- 状态常量 ---

    /** 账号正常，可登录。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 账号已禁用，不可登录。 */
    public static final String STATUS_DISABLED = "DISABLED";
}
