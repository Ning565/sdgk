-- V1: All tables for admission platform MVP
-- Compatible with MySQL 8.4+ / 9.x

-- ============================================
-- Auth & User
-- ============================================
CREATE TABLE user_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mobile_ciphertext VARCHAR(500) NOT NULL COMMENT 'AES-GCM encrypted mobile',
    mobile_hash VARCHAR(64) NOT NULL COMMENT 'HMAC-SHA256 for lookup',
    mobile_masked VARCHAR(20) NOT NULL COMMENT '138****1234',
    nickname VARCHAR(50) DEFAULT NULL,
    avatar_url VARCHAR(500) DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    last_login_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_mobile_hash (mobile_hash),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Candidate
-- ============================================
CREATE TABLE candidate_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    year INT NOT NULL,
    score INT DEFAULT NULL,
    `rank` INT DEFAULT NULL,
    rank_source VARCHAR(20) DEFAULT 'AUTO' COMMENT 'AUTO/MANUAL',
    subject_combo_index INT DEFAULT NULL COMMENT '0-19 for 山东6选3 combo',
    subjects_json VARCHAR(500) DEFAULT NULL COMMENT 'JSON array of 3 subjects',
    education_level VARCHAR(20) DEFAULT 'UNDERGRADUATE' COMMENT 'UNDERGRADUATE/VOCATIONAL/UNLIMITED',
    preferred_regions_json TEXT DEFAULT NULL,
    preferred_majors_json TEXT DEFAULT NULL,
    excluded_majors_json TEXT DEFAULT NULL,
    tuition_max INT DEFAULT NULL,
    school_nature VARCHAR(20) DEFAULT 'UNLIMITED' COMMENT 'PUBLIC/PRIVATE/UNLIMITED',
    accept_joint_program TINYINT(1) DEFAULT 1,
    cooperation_types_json VARCHAR(500) DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_year (user_id, year),
    INDEX idx_year (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Score Rank Segment (一分一段表)
-- ============================================
CREATE TABLE score_rank_segment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data_version_id BIGINT NOT NULL,
    year INT NOT NULL,
    score INT NOT NULL,
    cumulative_count INT NOT NULL COMMENT '累计人数',
    segment_count INT NOT NULL DEFAULT 0 COMMENT '本段人数',
    UNIQUE KEY uk_version_year_score (data_version_id, year, score),
    INDEX idx_year_score (year, score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- School
-- ============================================
CREATE TABLE school (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL COMMENT '院校代号 e.g. A001',
    name VARCHAR(200) NOT NULL,
    short_name VARCHAR(100) DEFAULT NULL,
    province VARCHAR(50) DEFAULT NULL,
    city VARCHAR(50) DEFAULT NULL,
    school_type VARCHAR(50) DEFAULT NULL COMMENT '公办/民办/独立学院',
    school_tag VARCHAR(200) DEFAULT NULL COMMENT '双一流/985/211 etc.',
    website VARCHAR(500) DEFAULT NULL,
    logo_url VARCHAR(500) DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code),
    INDEX idx_province (province),
    INDEX idx_school_type (school_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Standard Major
-- ============================================
CREATE TABLE standard_major (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    category_code VARCHAR(10) DEFAULT NULL COMMENT '门类代码',
    category_name VARCHAR(50) DEFAULT NULL COMMENT '门类名称 e.g. 工学',
    subcategory_code VARCHAR(10) DEFAULT NULL COMMENT '专业类代码',
    subcategory_name VARCHAR(100) DEFAULT NULL COMMENT '专业类名称',
    education_level VARCHAR(20) DEFAULT 'UNDERGRADUATE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code),
    INDEX idx_category (category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Plan Series (跨年度稳定计划序列)
-- ============================================
CREATE TABLE plan_series (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT DEFAULT NULL,
    standard_major_code VARCHAR(20) DEFAULT NULL,
    major_name VARCHAR(200) DEFAULT NULL,
    enrollment_type_code VARCHAR(20) DEFAULT NULL,
    campus_code VARCHAR(20) DEFAULT NULL,
    education_level VARCHAR(20) DEFAULT NULL,
    match_confidence VARCHAR(20) DEFAULT 'AUTO' COMMENT 'AUTO/MANUAL/UNCONFIRMED',
    match_status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_school (school_id),
    INDEX idx_major_code (standard_major_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Enrollment Plan (招生计划)
-- ============================================
CREATE TABLE enrollment_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data_version_id BIGINT NOT NULL,
    year INT NOT NULL,
    plan_code VARCHAR(50) DEFAULT NULL,
    school_id BIGINT DEFAULT NULL,
    school_code VARCHAR(20) NOT NULL,
    standard_major_code VARCHAR(20) DEFAULT NULL,
    major_code VARCHAR(50) DEFAULT NULL,
    major_name VARCHAR(200) NOT NULL,
    enrollment_type_code VARCHAR(50) DEFAULT 'NORMAL' COMMENT 'NORMAL/SINO_FOREIGN/SCHOOL_ENTERPRISE',
    campus_code VARCHAR(50) DEFAULT NULL,
    campus_name VARCHAR(200) DEFAULT NULL,
    education_level VARCHAR(20) DEFAULT 'UNDERGRADUATE',
    plan_count INT NOT NULL DEFAULT 0,
    tuition DECIMAL(10,0) DEFAULT NULL,
    duration INT DEFAULT 4 COMMENT '学制(年)',
    subject_requirement_text VARCHAR(500) DEFAULT NULL,
    subject_rule_json TEXT DEFAULT NULL,
    eligible_subject_bitmap BIGINT DEFAULT NULL COMMENT '20位位图',
    subject_rule_status VARCHAR(30) DEFAULT 'PARSED' COMMENT 'PARSED/MANUAL_CONFIRMED/INVALID',
    plan_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/NEW/STOPPED/REVOKED',
    remark TEXT DEFAULT NULL,
    plan_series_id BIGINT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plan (data_version_id, year, school_code, major_code, enrollment_type_code, campus_code, education_level),
    INDEX idx_version_status (data_version_id, plan_status),
    INDEX idx_school (data_version_id, school_id),
    INDEX idx_major (data_version_id, standard_major_code),
    INDEX idx_bitmap (eligible_subject_bitmap),
    INDEX idx_plan_series (plan_series_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Admission History (历史录取)
-- ============================================
CREATE TABLE admission_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data_version_id BIGINT NOT NULL,
    plan_series_id BIGINT DEFAULT NULL,
    year INT NOT NULL,
    school_id BIGINT DEFAULT NULL,
    school_code VARCHAR(20) DEFAULT NULL,
    major_name VARCHAR(200) DEFAULT NULL,
    plan_count INT DEFAULT NULL,
    lowest_rank INT DEFAULT NULL,
    lowest_score DECIMAL(5,1) DEFAULT NULL,
    highest_rank INT DEFAULT NULL,
    highest_score DECIMAL(5,1) DEFAULT NULL,
    avg_rank INT DEFAULT NULL,
    avg_score DECIMAL(5,1) DEFAULT NULL,
    admission_batch INT DEFAULT 1 COMMENT '批次',
    education_level VARCHAR(20) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_version_series_year (data_version_id, plan_series_id, year),
    INDEX idx_series_year (plan_series_id, year),
    INDEX idx_school_year (school_code, year),
    INDEX idx_year_rank (year, lowest_rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Links
-- ============================================
CREATE TABLE school_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    link_type VARCHAR(50) NOT NULL COMMENT 'SUNSHINE/SCHOOL_OFFICIAL/SCHOOL_ADMISSION',
    title VARCHAR(200) DEFAULT NULL,
    url VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_school (school_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE major_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_series_id BIGINT DEFAULT NULL,
    link_type VARCHAR(50) NOT NULL COMMENT 'SUNSHINE_MAJOR/SCHOOL_MAJOR',
    title VARCHAR(200) DEFAULT NULL,
    url VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_series (plan_series_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Prediction Result
-- ============================================
CREATE TABLE prediction_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_hash VARCHAR(64) NOT NULL,
    plan_id BIGINT NOT NULL,
    plan_data_version BIGINT NOT NULL,
    model_version VARCHAR(30) NOT NULL,
    probability INT DEFAULT NULL COMMENT '0-100',
    predicted_rank_min INT DEFAULT NULL,
    predicted_rank_max INT DEFAULT NULL,
    label VARCHAR(10) DEFAULT NULL COMMENT '冲/稳/保',
    reason_text TEXT DEFAULT NULL,
    reason_code VARCHAR(50) DEFAULT NULL,
    is_valid TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_prediction (profile_hash, plan_id, plan_data_version, model_version),
    INDEX idx_profile_label (profile_hash, label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Volunteer Form
-- ============================================
CREATE TABLE volunteer_form (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    year INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    item_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_year (user_id, year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE volunteer_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    form_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    sort_order INT NOT NULL COMMENT '1-N连续序号',
    note VARCHAR(200) DEFAULT NULL,
    added_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_form_plan (form_id, plan_id),
    UNIQUE KEY uk_form_order (form_id, sort_order),
    INDEX idx_form (form_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Volunteer Check
-- ============================================
CREATE TABLE volunteer_check_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    form_id BIGINT NOT NULL,
    form_version INT NOT NULL,
    check_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    issue_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    warning_count INT NOT NULL DEFAULT 0,
    info_count INT NOT NULL DEFAULT 0,
    checked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_form (form_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE volunteer_check_issue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    check_run_id BIGINT NOT NULL,
    item_id BIGINT DEFAULT NULL,
    plan_id BIGINT DEFAULT NULL,
    rule_code VARCHAR(50) NOT NULL,
    level VARCHAR(10) NOT NULL COMMENT 'ERROR/WARNING/INFO',
    message VARCHAR(500) NOT NULL,
    detail_json TEXT DEFAULT NULL,
    INDEX idx_run (check_run_id),
    INDEX idx_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Export Record
-- ============================================
CREATE TABLE export_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    form_id BIGINT NOT NULL,
    file_name VARCHAR(300) DEFAULT NULL,
    file_path VARCHAR(1000) DEFAULT NULL,
    file_size BIGINT DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    confirmed_with_errors TINYINT(1) NOT NULL DEFAULT 0,
    data_version_id BIGINT DEFAULT NULL,
    trace_id VARCHAR(64) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_form (form_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Data Version (版本管理)
-- ============================================
CREATE TABLE data_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data_type VARCHAR(30) NOT NULL COMMENT 'SCORE_RANK/PLAN/HISTORY/LINK',
    year INT NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/VALIDATING/READY/PUBLISHED/ARCHIVED',
    source_batch_id BIGINT DEFAULT NULL,
    row_count INT DEFAULT 0,
    checksum VARCHAR(64) DEFAULT NULL,
    published_by VARCHAR(50) DEFAULT NULL,
    published_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type_year (data_type, year),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE active_data_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data_type VARCHAR(30) NOT NULL,
    year INT NOT NULL,
    data_version_id BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_type_year (data_type, year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Import Batch
-- ============================================
CREATE TABLE import_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data_type VARCHAR(30) NOT NULL,
    year INT NOT NULL,
    file_name VARCHAR(300) DEFAULT NULL,
    file_size BIGINT DEFAULT 0,
    file_url VARCHAR(1000) DEFAULT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UPLOADING' COMMENT 'UPLOADING/PARSING/VALIDATION_FAILED/READY/PUBLISHED/CANCELLED',
    total_rows INT DEFAULT 0,
    valid_rows INT DEFAULT 0,
    error_rows INT DEFAULT 0,
    created_by VARCHAR(50) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type_year (data_type, year),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE import_row_error (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    row_num INT DEFAULT NULL,
    field_name VARCHAR(100) DEFAULT NULL,
    original_value TEXT DEFAULT NULL,
    error_type VARCHAR(50) DEFAULT NULL,
    error_message VARCHAR(500) DEFAULT NULL,
    suggestion VARCHAR(500) DEFAULT NULL,
    INDEX idx_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE import_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    file_type VARCHAR(20) NOT NULL COMMENT 'ORIGINAL/ERROR_DETAIL',
    file_url VARCHAR(1000) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Year Config
-- ============================================
CREATE TABLE year_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    year INT NOT NULL UNIQUE,
    score_min INT NOT NULL DEFAULT 0,
    score_max INT NOT NULL DEFAULT 750,
    volunteer_limit INT NOT NULL DEFAULT 96,
    is_open TINYINT(1) NOT NULL DEFAULT 0,
    remark VARCHAR(500) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- Seed Data
-- ============================================
INSERT INTO year_config (year, score_min, score_max, volunteer_limit, is_open, remark) VALUES
(2023, 0, 750, 96, 1, '2023年度'),
(2024, 0, 750, 96, 1, '2024年度'),
(2025, 0, 750, 96, 1, '2025年度'),
(2026, 0, 750, 96, 1, '当前招生年度');

-- Init data versions for each year
INSERT INTO data_version (data_type, year, version_no, status, row_count) VALUES
('SCORE_RANK', 2025, 1, 'DRAFT', 0),
('PLAN', 2025, 1, 'DRAFT', 0),
('HISTORY', 2023, 1, 'DRAFT', 0),
('HISTORY', 2024, 1, 'DRAFT', 0),
('HISTORY', 2025, 1, 'DRAFT', 0),
('LINK', 2025, 1, 'DRAFT', 0);
