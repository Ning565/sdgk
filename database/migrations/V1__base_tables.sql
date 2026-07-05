-- ============================================================
-- V1: Base Tables
-- Core user, candidate, admin, audit tables
-- MySQL 8.4 | charset=utf8mb4 | engine=InnoDB
-- ============================================================

-- -----------------------------------------------------------
-- user_account — front-end user (student/parent)
-- -----------------------------------------------------------
CREATE TABLE user_account (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    mobile_ciphertext VARCHAR(512)  NOT NULL                COMMENT '手机号密文(AES-256-GCM加密)',
    mobile_hash     CHAR(64)        NOT NULL                COMMENT '手机号SHA-256哈希,用于唯一性约束',
    mobile_masked   VARCHAR(16)     NOT NULL                COMMENT '手机号脱敏展示(如138****1234)',
    nickname        VARCHAR(64)     DEFAULT NULL            COMMENT '昵称',
    avatar_url      VARCHAR(512)    DEFAULT NULL            COMMENT '头像URL',
    status          TINYINT         NOT NULL DEFAULT 1      COMMENT '状态: 1=正常,0=禁用',
    last_login_at   DATETIME(3)     DEFAULT NULL            COMMENT '最后登录时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mobile_hash (mobile_hash),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账户表';

-- -----------------------------------------------------------
-- candidate_profile — 考生画像 (one per user per year)
-- -----------------------------------------------------------
CREATE TABLE candidate_profile (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    user_id             BIGINT          NOT NULL                COMMENT '关联用户ID',
    year                SMALLINT        NOT NULL                COMMENT '高考年份',
    score               DECIMAL(6,2)    DEFAULT NULL            COMMENT '高考分数',
    rank                INT             DEFAULT NULL            COMMENT '位次',
    rank_source         VARCHAR(32)     DEFAULT NULL            COMMENT '位次来源: REAL/MANUAL/ESTIMATED',
    subject_combo_index INT             DEFAULT NULL            COMMENT '选科组合编码(位图)',
    subjects_json       JSON            DEFAULT NULL            COMMENT '选考科目明细(JSON)',
    intentions_json     JSON            DEFAULT NULL            COMMENT '志愿意向(专业/城市)(JSON)',
    exclusions_json     JSON            DEFAULT NULL            COMMENT '排除项(院校/专业)(JSON)',
    tuition_max         INT             DEFAULT NULL            COMMENT '学费上限(元)',
    school_types_json   JSON            DEFAULT NULL            COMMENT '院校类型偏好(JSON)',
    cooperation_types_json JSON         DEFAULT NULL            COMMENT '合作办学类型偏好(JSON)',
    remark              VARCHAR(1024)   DEFAULT NULL            COMMENT '备注',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_year (user_id, year),
    KEY idx_year (year),
    KEY idx_score_rank (year, score, rank),
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考生画像表';

-- -----------------------------------------------------------
-- admin_user — 后台管理用户
-- -----------------------------------------------------------
CREATE TABLE admin_user (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    username        VARCHAR(64)     NOT NULL                COMMENT '用户名(登录用)',
    password_hash   VARCHAR(256)    NOT NULL                COMMENT '密码哈希(BCrypt)',
    real_name       VARCHAR(64)     DEFAULT NULL            COMMENT '真实姓名',
    status          TINYINT         NOT NULL DEFAULT 1      COMMENT '状态: 1=正常,0=禁用',
    last_login_at   DATETIME(3)     DEFAULT NULL            COMMENT '最后登录时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理用户表';

-- -----------------------------------------------------------
-- admin_role — 角色
-- -----------------------------------------------------------
CREATE TABLE admin_role (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    code            VARCHAR(64)     NOT NULL                COMMENT '角色编码(SUPER_ADMIN/OPERATOR/READONLY)',
    name            VARCHAR(64)     NOT NULL                COMMENT '角色名称',
    description     VARCHAR(256)    DEFAULT NULL            COMMENT '角色描述',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理角色表';

-- -----------------------------------------------------------
-- admin_permission — 权限
-- -----------------------------------------------------------
CREATE TABLE admin_permission (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    code            VARCHAR(128)    NOT NULL                COMMENT '权限编码(如 school:create)',
    name            VARCHAR(128)    NOT NULL                COMMENT '权限名称',
    resource_type   VARCHAR(64)     NOT NULL                COMMENT '资源类型(如 SCHOOL/PLAN/USER)',
    resource_path   VARCHAR(256)    DEFAULT NULL            COMMENT '资源路径模式(支持通配)',
    action          VARCHAR(32)     NOT NULL                COMMENT '操作: CREATE/READ/UPDATE/DELETE/EXPORT/MANAGE',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_resource (resource_type, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理权限表';

-- -----------------------------------------------------------
-- admin_user_role — 用户-角色关联
-- -----------------------------------------------------------
CREATE TABLE admin_user_role (
    user_id         BIGINT          NOT NULL                COMMENT '管理用户ID',
    role_id         BIGINT          NOT NULL                COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_aur_user FOREIGN KEY (user_id) REFERENCES admin_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_aur_role FOREIGN KEY (role_id) REFERENCES admin_role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- -----------------------------------------------------------
-- admin_role_permission — 角色-权限关联
-- -----------------------------------------------------------
CREATE TABLE admin_role_permission (
    role_id         BIGINT          NOT NULL                COMMENT '角色ID',
    permission_id   BIGINT          NOT NULL                COMMENT '权限ID',
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_arp_role FOREIGN KEY (role_id) REFERENCES admin_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_arp_perm FOREIGN KEY (permission_id) REFERENCES admin_permission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- -----------------------------------------------------------
-- audit_log — 审计日志
-- -----------------------------------------------------------
CREATE TABLE audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    admin_user_id   BIGINT          DEFAULT NULL            COMMENT '操作管理员ID(NULL表示系统操作)',
    action          VARCHAR(64)     NOT NULL                COMMENT '操作动作(LOGIN/EXPORT/DELETE/UPDATE)',
    target_type     VARCHAR(64)     NOT NULL                COMMENT '目标资源类型',
    target_id       VARCHAR(128)    DEFAULT NULL            COMMENT '目标资源ID',
    detail_json     JSON            DEFAULT NULL            COMMENT '操作详情(JSON)',
    ip_address      VARCHAR(64)     DEFAULT NULL            COMMENT '操作IP地址',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_admin_user (admin_user_id),
    KEY idx_target (target_type, target_id),
    KEY idx_created_at (created_at),
    KEY idx_action (action, created_at),
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_user_id) REFERENCES admin_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';
