ALTER TABLE volunteer_item
    ADD COLUMN subject_requirement_text VARCHAR(500) DEFAULT NULL AFTER tuition,
    ADD COLUMN plan_status VARCHAR(30) DEFAULT NULL AFTER subject_requirement_text;
