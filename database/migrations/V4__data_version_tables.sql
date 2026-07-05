-- ============================================================
-- V4: Data Version Tables
-- Data versioning, import batch management, year config
-- MySQL 8.4 | charset=utf8mb4 | engine=InnoDB
-- ============================================================

-- -----------------------------------------------------------
-- data_version — 数据版本管理(支持多版本/多年度数据共存)
-- -----------------------------------------------------------
CREATE TABLE data_version (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    data_type       ENUM('SCORE_RANK','PLAN','HISTORY','LINK') NOT NULL COMMENT '数据类型',
    year            SMALLINT        NOT NULL                COMMENT '数据年份',
    version_no      INT             NOT NULL                COMMENT '版本号(同类型同年递增)',
    status          ENUM('DRAFT','VALIDATING','READY','PUBLISHED','ARCHIVED') NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态',
    source_batch_id BIGINT          DEFAULT NULL            COMMENT '来源导入批次ID',
    row_count       INT             DEFAULT NULL            COMMENT '数据行数',
    checksum        CHAR(64)        DEFAULT NULL            COMMENT '数据校验和(SHA-256)',
    published_by    BIGINT          DEFAULT NULL            COMMENT '发布人(admin_user.id)',
    published_at    DATETIME(3)     DEFAULT NULL            COMMENT '发布时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_type_year_version (data_type, year, version_no),
    KEY idx_status (status),
    KEY idx_year_type (year, data_type),
    CONSTRAINT fk_version_publisher FOREIGN KEY (published_by) REFERENCES admin_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据版本管理表';

-- -----------------------------------------------------------
-- active_data_version — 当前生效的数据版本(业务侧只读此表)
-- -----------------------------------------------------------
CREATE TABLE active_data_version (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    data_type           ENUM('SCORE_RANK','PLAN','HISTORY','LINK') NOT NULL COMMENT '数据类型',
    year                SMALLINT        NOT NULL                COMMENT '数据年份',
    data_version_id     BIGINT          NOT NULL                COMMENT '生效的数据版本ID',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_type_year (data_type, year),
    CONSTRAINT fk_active_version FOREIGN KEY (data_version_id) REFERENCES data_version(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='当前生效数据版本表';

-- -----------------------------------------------------------
-- import_batch — 导入批次
-- -----------------------------------------------------------
CREATE TABLE import_batch (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    data_type       ENUM('SCORE_RANK','PLAN','HISTORY','LINK') NOT NULL COMMENT '数据类型',
    year            SMALLINT        NOT NULL                COMMENT '数据年份',
    file_name       VARCHAR(256)    NOT NULL                COMMENT '原始文件名',
    file_size       BIGINT          DEFAULT NULL            COMMENT '文件大小(字节)',
    file_url        VARCHAR(1024)   DEFAULT NULL            COMMENT '文件OSS地址',
    status          TINYINT         NOT NULL DEFAULT 0      COMMENT '导入状态: 0=待处理,1=处理中,2=已完成,3=失败',
    total_rows      INT             NOT NULL DEFAULT 0      COMMENT '总行数',
    valid_rows      INT             NOT NULL DEFAULT 0      COMMENT '有效行数',
    error_rows      INT             NOT NULL DEFAULT 0      COMMENT '错误行数',
    created_by      BIGINT          DEFAULT NULL            COMMENT '导入人(admin_user.id)',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_type_year (data_type, year),
    KEY idx_status (status),
    CONSTRAINT fk_batch_creator FOREIGN KEY (created_by) REFERENCES admin_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入批次表';

-- -----------------------------------------------------------
-- import_row_error — 导入行级错误
-- -----------------------------------------------------------
CREATE TABLE import_row_error (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    batch_id        BIGINT          NOT NULL                COMMENT '导入批次ID',
    row_number      INT             NOT NULL                COMMENT '错误行号',
    field_name      VARCHAR(128)    DEFAULT NULL            COMMENT '错误字段名',
    original_value  VARCHAR(1024)   DEFAULT NULL            COMMENT '原始值',
    error_type      VARCHAR(64)     NOT NULL                COMMENT '错误类型: FORMAT/REQUIRED/DUP/REFERENCE',
    error_message   VARCHAR(1024)   NOT NULL                COMMENT '错误描述',
    suggestion      VARCHAR(1024)   DEFAULT NULL            COMMENT '修改建议',
    PRIMARY KEY (id),
    KEY idx_batch (batch_id),
    KEY idx_row (batch_id, row_number),
    CONSTRAINT fk_error_batch FOREIGN KEY (batch_id) REFERENCES import_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入行级错误表';

-- -----------------------------------------------------------
-- import_file — 导入文件(原始文件及错误明细文件)
-- -----------------------------------------------------------
CREATE TABLE import_file (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    batch_id        BIGINT          NOT NULL                COMMENT '导入批次ID',
    file_type       ENUM('ORIGINAL','ERROR_DETAIL') NOT NULL COMMENT '文件类型: ORIGINAL=原始文件,ERROR_DETAIL=错误明细',
    file_url        VARCHAR(1024)   NOT NULL                COMMENT '文件OSS地址',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_batch (batch_id),
    CONSTRAINT fk_import_file_batch FOREIGN KEY (batch_id) REFERENCES import_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入文件表';

-- -----------------------------------------------------------
-- year_config — 年度配置(每年度独立配置)
-- -----------------------------------------------------------
CREATE TABLE year_config (
    id                      BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    year                    SMALLINT        NOT NULL                COMMENT '高考年份',
    score_min               DECIMAL(6,2)    NOT NULL DEFAULT 0      COMMENT '最低分数',
    score_max               DECIMAL(6,2)    NOT NULL DEFAULT 750    COMMENT '最高分数',
    subject_options_json    JSON            DEFAULT NULL            COMMENT '可选科目配置(JSON,含科目列表及组合规则)',
    volunteer_limit         INT             NOT NULL DEFAULT 96     COMMENT '志愿填报数量上限',
    is_open                 TINYINT         NOT NULL DEFAULT 0      COMMENT '志愿填报是否开放: 1=开放,0=关闭',
    remark                  VARCHAR(512)    DEFAULT NULL            COMMENT '备注',
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_year (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='年度配置表';

-- ============================================================
-- Add foreign keys from V2 tables to data_version
-- (data_version was not available when V2 ran)
-- ============================================================
ALTER TABLE enrollment_plan
    ADD CONSTRAINT fk_plan_data_version FOREIGN KEY (data_version_id) REFERENCES data_version(id) ON DELETE RESTRICT;

ALTER TABLE admission_history
    ADD CONSTRAINT fk_history_data_version FOREIGN KEY (data_version_id) REFERENCES data_version(id) ON DELETE RESTRICT;

ALTER TABLE score_rank_segment
    ADD CONSTRAINT fk_segment_data_version FOREIGN KEY (data_version_id) REFERENCES data_version(id) ON DELETE RESTRICT;

-- Add FK for admission_history -> plan_series (plan_series from V2)
ALTER TABLE admission_history
    ADD CONSTRAINT fk_history_plan_series FOREIGN KEY (plan_series_id) REFERENCES plan_series(id) ON DELETE RESTRICT;

-- Add FK for import_batch -> data_version
ALTER TABLE data_version
    ADD CONSTRAINT fk_version_batch FOREIGN KEY (source_batch_id) REFERENCES import_batch(id) ON DELETE SET NULL;
