-- Align volunteer check tables with the current entity model while keeping
-- the original V1 columns for backward compatibility.

ALTER TABLE volunteer_check_run
    MODIFY COLUMN form_version INT NOT NULL DEFAULT 0,
    ADD COLUMN user_id BIGINT DEFAULT NULL AFTER form_id,
    ADD COLUMN check_time DATETIME DEFAULT CURRENT_TIMESTAMP AFTER user_id,
    ADD COLUMN total_issues INT NOT NULL DEFAULT 0 AFTER check_time,
    ADD COLUMN profile_snapshot_at DATETIME DEFAULT NULL AFTER info_count,
    ADD COLUMN data_version_id BIGINT DEFAULT NULL AFTER profile_snapshot_at,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER data_version_id,
    ADD INDEX idx_form_status (form_id, status);

UPDATE volunteer_check_run
SET check_time = COALESCE(check_time, checked_at),
    total_issues = COALESCE(total_issues, issue_count),
    status = CASE
        WHEN check_status = 'EXPIRED' THEN 'EXPIRED'
        ELSE COALESCE(status, 'ACTIVE')
    END;

ALTER TABLE volunteer_check_issue
    ADD COLUMN form_id BIGINT DEFAULT NULL AFTER check_run_id,
    ADD COLUMN sort_order INT DEFAULT NULL AFTER plan_id,
    ADD COLUMN suggestion VARCHAR(500) DEFAULT NULL AFTER message,
    ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER suggestion,
    ADD INDEX idx_form (form_id);

UPDATE volunteer_check_issue i
JOIN volunteer_check_run r ON r.id = i.check_run_id
SET i.form_id = COALESCE(i.form_id, r.form_id);
