-- V6: 志愿表容量改为用户可配置，NULL 表示不限，默认 96
ALTER TABLE volunteer_form ADD COLUMN max_items INT NULL DEFAULT 96 COMMENT '用户设置的志愿表容量上限(NULL=不限)';
