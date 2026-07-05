-- ============================================================
-- V2: Catalog Tables
-- Schools, majors, enrollment plans, admission history,
-- plan series, links, score-rank segments
-- MySQL 8.4 | charset=utf8mb4 | engine=InnoDB
-- ============================================================

-- -----------------------------------------------------------
-- school — 院校信息
-- -----------------------------------------------------------
CREATE TABLE school (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    code            VARCHAR(32)     NOT NULL                COMMENT '院校代码(教育部统一编码)',
    name            VARCHAR(256)    NOT NULL                COMMENT '院校全称',
    short_name      VARCHAR(64)     DEFAULT NULL            COMMENT '院校简称',
    province        VARCHAR(32)     NOT NULL                COMMENT '所在省份',
    city            VARCHAR(64)     DEFAULT NULL            COMMENT '所在城市',
    school_type     VARCHAR(32)     DEFAULT NULL            COMMENT '院校类型: 985/211/双一流/普通本科/高职/独立学院',
    school_tag      VARCHAR(128)    DEFAULT NULL            COMMENT '院校标签(C9/华东五校/国防七子等)',
    website         VARCHAR(256)    DEFAULT NULL            COMMENT '官网地址',
    logo_url        VARCHAR(512)    DEFAULT NULL            COMMENT 'Logo URL',
    status          TINYINT         NOT NULL DEFAULT 1      COMMENT '状态: 1=启用,0=禁用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_province (province),
    KEY idx_school_type (school_type),
    KEY idx_name (name),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='院校信息表';

-- -----------------------------------------------------------
-- standard_major — 标准专业目录
-- -----------------------------------------------------------
CREATE TABLE standard_major (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    code                VARCHAR(32)     NOT NULL                COMMENT '专业代码(教育部标准编码)',
    name                VARCHAR(256)    NOT NULL                COMMENT '专业名称',
    category_code       VARCHAR(16)     NOT NULL                COMMENT '专业大类代码',
    category_name       VARCHAR(128)    NOT NULL                COMMENT '专业大类名称',
    subcategory_code    VARCHAR(16)     DEFAULT NULL            COMMENT '专业子类代码',
    subcategory_name    VARCHAR(128)    DEFAULT NULL            COMMENT '专业子类名称',
    education_level     VARCHAR(16)     NOT NULL DEFAULT '本科' COMMENT '学历层次: 本科/专科',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_category (category_code),
    KEY idx_subcategory (subcategory_code),
    KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标准专业目录表';

-- -----------------------------------------------------------
-- enrollment_plan — 招生计划 (versioned)
-- -----------------------------------------------------------
CREATE TABLE enrollment_plan (
    id                      BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    data_version_id         BIGINT          NOT NULL                COMMENT '数据版本ID(关联data_version)',
    year                    SMALLINT        NOT NULL                COMMENT '招生年份',
    plan_code               VARCHAR(64)     NOT NULL                COMMENT '计划编码',
    school_id               BIGINT          NOT NULL                COMMENT '院校ID(关联school)',
    school_code             VARCHAR(32)     NOT NULL                COMMENT '院校代码(冗余,方便查询)',
    standard_major_code     VARCHAR(32)     NOT NULL                COMMENT '标准专业代码(关联standard_major)',
    major_name              VARCHAR(256)    NOT NULL                COMMENT '专业名称(原始)',
    enrollment_type_code    VARCHAR(32)     NOT NULL                COMMENT '招生类型: 普通类/艺术类/体育类/专项计划',
    campus_code             VARCHAR(32)     NOT NULL DEFAULT '000'  COMMENT '校区代码',
    campus_name             VARCHAR(128)    DEFAULT NULL            COMMENT '校区名称',
    education_level         VARCHAR(16)     NOT NULL DEFAULT '本科' COMMENT '学历层次',
    plan_count              INT             NOT NULL DEFAULT 0      COMMENT '计划人数',
    tuition                 INT             DEFAULT NULL            COMMENT '学费(元/年)',
    subject_requirement_text VARCHAR(512)   DEFAULT NULL            COMMENT '选科要求(文本)',
    subject_rule_json       JSON            DEFAULT NULL            COMMENT '选科规则(JSON,用于引擎匹配)',
    eligible_subject_bitmap BIGINT          DEFAULT NULL            COMMENT '适用选科位图(加速匹配)',
    subject_rule_status     TINYINT         NOT NULL DEFAULT 1      COMMENT '选科规则状态: 1=已解析,0=待解析',
    plan_status             TINYINT         NOT NULL DEFAULT 1      COMMENT '计划状态: 1=有效,0=无效',
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_version (data_version_id, year, school_code, standard_major_code, enrollment_type_code, campus_code, education_level),
    KEY idx_year (year),
    KEY idx_school (school_id),
    KEY idx_major (standard_major_code),
    KEY idx_enrollment_type (enrollment_type_code),
    KEY idx_plan_status (plan_status),
    KEY idx_plan_code (plan_code),
    CONSTRAINT fk_plan_school FOREIGN KEY (school_id) REFERENCES school(id) ON DELETE RESTRICT,
    CONSTRAINT fk_plan_major FOREIGN KEY (standard_major_code) REFERENCES standard_major(code) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='招生计划表';

-- -----------------------------------------------------------
-- admission_history — 录取历史
-- -----------------------------------------------------------
CREATE TABLE admission_history (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    data_version_id BIGINT          NOT NULL                COMMENT '数据版本ID',
    plan_series_id  BIGINT          NOT NULL                COMMENT '计划系列ID(关联plan_series)',
    year            SMALLINT        NOT NULL                COMMENT '录取年份',
    school_id       BIGINT          NOT NULL                COMMENT '院校ID',
    major_name      VARCHAR(256)    NOT NULL                COMMENT '专业名称',
    plan_count      INT             DEFAULT NULL            COMMENT '录取人数',
    lowest_rank     INT             DEFAULT NULL            COMMENT '最低位次',
    lowest_score    DECIMAL(6,2)    DEFAULT NULL            COMMENT '最低分',
    highest_rank    INT             DEFAULT NULL            COMMENT '最高位次',
    highest_score   DECIMAL(6,2)    DEFAULT NULL            COMMENT '最高分',
    avg_rank        DECIMAL(10,2)   DEFAULT NULL            COMMENT '平均位次',
    avg_score       DECIMAL(6,2)    DEFAULT NULL            COMMENT '平均分',
    admission_batch VARCHAR(32)     DEFAULT NULL            COMMENT '录取批次: 提前批/本科批/专科批',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_history (data_version_id, plan_series_id, year),
    KEY idx_year (year),
    KEY idx_school (school_id),
    KEY idx_lowest_rank (year, lowest_rank),
    KEY idx_admission_batch (admission_batch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='录取历史表';

-- -----------------------------------------------------------
-- plan_series — 计划系列(跨年关联相同招生单元)
-- -----------------------------------------------------------
CREATE TABLE plan_series (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    school_id           BIGINT          NOT NULL                COMMENT '院校ID',
    standard_major_code VARCHAR(32)     NOT NULL                COMMENT '标准专业代码',
    major_name          VARCHAR(256)    NOT NULL                COMMENT '专业名称',
    enrollment_type_code VARCHAR(32)    NOT NULL                COMMENT '招生类型',
    campus_code         VARCHAR(32)     NOT NULL DEFAULT '000'  COMMENT '校区代码',
    education_level     VARCHAR(16)     NOT NULL DEFAULT '本科' COMMENT '学历层次',
    match_confidence    DECIMAL(4,3)    DEFAULT 1.000          COMMENT '匹配置信度(0-1)',
    match_status        TINYINT         NOT NULL DEFAULT 1      COMMENT '匹配状态: 1=已确认,0=待审核',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_series (school_id, standard_major_code, enrollment_type_code, campus_code, education_level),
    KEY idx_school (school_id),
    KEY idx_major (standard_major_code),
    CONSTRAINT fk_series_school FOREIGN KEY (school_id) REFERENCES school(id) ON DELETE RESTRICT,
    CONSTRAINT fk_series_major FOREIGN KEY (standard_major_code) REFERENCES standard_major(code) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计划系列表(跨年关联)';

-- -----------------------------------------------------------
-- school_link — 院校外部链接
-- -----------------------------------------------------------
CREATE TABLE school_link (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    school_id       BIGINT          NOT NULL                COMMENT '院校ID',
    link_type       VARCHAR(32)     NOT NULL                COMMENT '链接类型: OFFICIAL/ADMISSION/BAIKE/WIKI',
    title           VARCHAR(256)    NOT NULL                COMMENT '链接标题',
    url             VARCHAR(1024)   NOT NULL                COMMENT '链接URL',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_school_type (school_id, link_type),
    CONSTRAINT fk_school_link FOREIGN KEY (school_id) REFERENCES school(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='院校外部链接表';

-- -----------------------------------------------------------
-- major_link — 专业外部链接
-- -----------------------------------------------------------
CREATE TABLE major_link (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    plan_series_id  BIGINT          NOT NULL                COMMENT '计划系列ID',
    link_type       VARCHAR(32)     NOT NULL                COMMENT '链接类型: INTRO/CURRICULUM/EMPLOYMENT',
    title           VARCHAR(256)    NOT NULL                COMMENT '链接标题',
    url             VARCHAR(1024)   NOT NULL                COMMENT '链接URL',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_series_type (plan_series_id, link_type),
    CONSTRAINT fk_major_link FOREIGN KEY (plan_series_id) REFERENCES plan_series(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专业外部链接表';

-- -----------------------------------------------------------
-- score_rank_segment — 一分一段表
-- -----------------------------------------------------------
CREATE TABLE score_rank_segment (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    data_version_id     BIGINT          NOT NULL                COMMENT '数据版本ID',
    year                SMALLINT        NOT NULL                COMMENT '年份',
    score               DECIMAL(6,2)    NOT NULL                COMMENT '分数',
    cumulative_count    INT             NOT NULL                COMMENT '累计人数',
    segment_count       INT             NOT NULL                COMMENT '本段人数',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_version_year_score (data_version_id, year, score),
    KEY idx_year_score (year, score),
    KEY idx_year_cumulative (year, cumulative_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='一分一段表';

-- Add foreign keys referencing data_version (created in V4) after V4 runs.
-- Using ALTER TABLE in V4 to add these constraints.
-- For now, note that enrollment_plan.data_version_id, admission_history.data_version_id,
-- and score_rank_segment.data_version_id will reference data_version(id).
