-- ============================================================
-- V3: Volunteer Tables
-- Prediction results, volunteer forms, items, check runs,
-- check issues, export records
-- MySQL 8.4 | charset=utf8mb4 | engine=InnoDB
-- ============================================================

-- -----------------------------------------------------------
-- prediction_result — 预测结果(冲/稳/保标签)
-- -----------------------------------------------------------
CREATE TABLE prediction_result (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    profile_hash        CHAR(64)        NOT NULL                COMMENT '考生画像哈希(用于去重,避免重复预测)',
    plan_id             BIGINT          NOT NULL                COMMENT '招生计划ID(关联enrollment_plan)',
    plan_data_version   BIGINT          NOT NULL                COMMENT '计划所属数据版本',
    model_version       VARCHAR(32)     NOT NULL                COMMENT '模型版本号',
    probability         DECIMAL(8,6)    DEFAULT NULL            COMMENT '录取概率(0-1)',
    predicted_rank_min  INT             DEFAULT NULL            COMMENT '预测最低位次',
    predicted_rank_max  INT             DEFAULT NULL            COMMENT '预测最高位次',
    label               ENUM('冲','稳','保') NOT NULL           COMMENT '标签: 冲/稳/保',
    reason_text         VARCHAR(1024)   DEFAULT NULL            COMMENT '推荐理由(文本)',
    reason_code         VARCHAR(64)     DEFAULT NULL            COMMENT '理由编码',
    is_valid            TINYINT         NOT NULL DEFAULT 1      COMMENT '是否有效: 1=有效,0=已过期',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prediction (profile_hash, plan_id, plan_data_version, model_version),
    KEY idx_label (label),
    KEY idx_profile_hash (profile_hash),
    KEY idx_plan (plan_id),
    KEY idx_probability (profile_hash, probability DESC),
    KEY idx_is_valid (is_valid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预测结果表';

-- -----------------------------------------------------------
-- volunteer_form — 志愿表
-- -----------------------------------------------------------
CREATE TABLE volunteer_form (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    user_id         BIGINT          NOT NULL                COMMENT '用户ID',
    year            SMALLINT        NOT NULL                COMMENT '报考年份',
    name            VARCHAR(128)    NOT NULL                COMMENT '志愿表名称(如"第一志愿方案")',
    version         INT             NOT NULL DEFAULT 1      COMMENT '版本号(乐观锁/快照版本)',
    item_count      INT             NOT NULL DEFAULT 0      COMMENT '志愿项数量(冗余,方便展示)',
    status          TINYINT         NOT NULL DEFAULT 1      COMMENT '状态: 1=草稿,2=已完成,3=已提交',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_year (user_id, year),
    KEY idx_status (status),
    CONSTRAINT fk_form_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='志愿表';

-- -----------------------------------------------------------
-- volunteer_item — 志愿表项
-- -----------------------------------------------------------
CREATE TABLE volunteer_item (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    form_id         BIGINT          NOT NULL                COMMENT '志愿表ID',
    plan_id         BIGINT          NOT NULL                COMMENT '招生计划ID',
    sort_order      INT             NOT NULL                COMMENT '排序(志愿顺序,越小越优先)',
    note            VARCHAR(512)    DEFAULT NULL            COMMENT '备注',
    added_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '添加时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_form_plan (form_id, plan_id),
    UNIQUE KEY uk_form_order (form_id, sort_order),
    CONSTRAINT fk_item_form FOREIGN KEY (form_id) REFERENCES volunteer_form(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='志愿表项';

-- -----------------------------------------------------------
-- volunteer_check_run — 志愿检查运行记录
-- -----------------------------------------------------------
CREATE TABLE volunteer_check_run (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    form_id         BIGINT          NOT NULL                COMMENT '志愿表ID',
    form_version    INT             NOT NULL                COMMENT '被检查的志愿表版本号',
    check_status    TINYINT         NOT NULL DEFAULT 0      COMMENT '检查状态: 0=进行中,1=已完成,2=失败',
    issue_count     INT             NOT NULL DEFAULT 0      COMMENT '问题总数',
    error_count     INT             NOT NULL DEFAULT 0      COMMENT 'ERROR级别问题数',
    warning_count   INT             NOT NULL DEFAULT 0      COMMENT 'WARNING级别问题数',
    info_count      INT             NOT NULL DEFAULT 0      COMMENT 'INFO级别问题数',
    checked_at      DATETIME(3)     DEFAULT NULL            COMMENT '检查完成时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_form (form_id),
    CONSTRAINT fk_check_form FOREIGN KEY (form_id) REFERENCES volunteer_form(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='志愿检查运行记录';

-- -----------------------------------------------------------
-- volunteer_check_issue — 志愿检查问题明细
-- -----------------------------------------------------------
CREATE TABLE volunteer_check_issue (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    check_run_id    BIGINT          NOT NULL                COMMENT '检查运行ID',
    item_id         BIGINT          DEFAULT NULL            COMMENT '关联的志愿项ID',
    plan_id         BIGINT          DEFAULT NULL            COMMENT '关联的招生计划ID',
    rule_code       VARCHAR(64)     NOT NULL                COMMENT '规则编码(如 SUBJ_MISMATCH/ORDER_GAP)',
    level           ENUM('ERROR','WARNING','INFO') NOT NULL COMMENT '问题级别',
    message         VARCHAR(1024)   NOT NULL                COMMENT '问题描述',
    detail_json     JSON            DEFAULT NULL            COMMENT '问题详情(JSON)',
    PRIMARY KEY (id),
    KEY idx_check_run (check_run_id),
    KEY idx_level (level),
    CONSTRAINT fk_issue_check FOREIGN KEY (check_run_id) REFERENCES volunteer_check_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='志愿检查问题明细';

-- -----------------------------------------------------------
-- export_record — 导出记录
-- -----------------------------------------------------------
CREATE TABLE export_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    user_id         BIGINT          NOT NULL                COMMENT '用户ID',
    form_id         BIGINT          DEFAULT NULL            COMMENT '志愿表ID',
    file_name       VARCHAR(256)    NOT NULL                COMMENT '文件名',
    file_url        VARCHAR(1024)   DEFAULT NULL            COMMENT '文件下载地址(OSS URL)',
    status          TINYINT         NOT NULL DEFAULT 0      COMMENT '状态: 0=处理中,1=成功,2=失败',
    error_message   VARCHAR(1024)   DEFAULT NULL            COMMENT '错误信息',
    trace_id        VARCHAR(64)     DEFAULT NULL            COMMENT '追踪ID(关联日志系统)',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_form (form_id),
    KEY idx_status (status),
    CONSTRAINT fk_export_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导出记录表';
