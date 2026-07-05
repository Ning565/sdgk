-- V4: Add missing columns that entities expect but tables lack
ALTER TABLE school_link ADD COLUMN sort_order INT DEFAULT 0 AFTER url;
ALTER TABLE major_link ADD COLUMN sort_order INT DEFAULT 0 AFTER url;
