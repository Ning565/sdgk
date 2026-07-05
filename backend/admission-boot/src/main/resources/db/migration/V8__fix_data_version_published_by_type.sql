-- Align data_version.published_by with the Java entity type (Long).
UPDATE data_version
SET published_by = NULL
WHERE published_by IS NOT NULL
  AND published_by NOT REGEXP '^[0-9]+$';

ALTER TABLE data_version
    MODIFY published_by BIGINT DEFAULT NULL COMMENT '发布人ID';
