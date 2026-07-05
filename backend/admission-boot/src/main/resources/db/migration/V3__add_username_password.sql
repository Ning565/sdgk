-- V2: Add username and password fields to user_account for password-based auth
ALTER TABLE user_account
    ADD COLUMN username VARCHAR(50) DEFAULT NULL COMMENT '用户名' AFTER id,
    ADD COLUMN password_hash VARCHAR(200) DEFAULT NULL COMMENT 'BCrypt密码哈希' AFTER username,
    ADD UNIQUE KEY uk_username (username),
    MODIFY COLUMN mobile_ciphertext VARCHAR(500) DEFAULT NULL,
    MODIFY COLUMN mobile_hash VARCHAR(64) DEFAULT NULL,
    MODIFY COLUMN mobile_masked VARCHAR(20) DEFAULT NULL;
