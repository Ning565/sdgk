-- Store imported plan history and current-year rank estimates for DB-backed recommendations.
ALTER TABLE enrollment_plan
    ADD COLUMN last_year_min_rank INT DEFAULT NULL AFTER subject_rule_status,
    ADD COLUMN last_year_min_score DECIMAL(5,1) DEFAULT NULL AFTER last_year_min_rank,
    ADD COLUMN last_year_plan_count INT DEFAULT NULL AFTER last_year_min_score,
    ADD COLUMN two_year_min_rank INT DEFAULT NULL AFTER last_year_plan_count,
    ADD COLUMN three_year_min_rank INT DEFAULT NULL AFTER two_year_min_rank,
    ADD COLUMN predicted_rank INT DEFAULT NULL AFTER three_year_min_rank,
    ADD COLUMN rank_range_min INT DEFAULT NULL AFTER predicted_rank,
    ADD COLUMN rank_range_max INT DEFAULT NULL AFTER rank_range_min,
    ADD INDEX idx_predicted_rank (data_version_id, predicted_rank);
