-- Preserve all three historical minimum ranks for file-backed recommendation plans.
ALTER TABLE volunteer_item
    ADD COLUMN two_year_min_rank INT DEFAULT NULL AFTER last_year_min_rank,
    ADD COLUMN three_year_min_rank INT DEFAULT NULL AFTER two_year_min_rank;

-- Backfill database-backed plans. File-backed specialized plans are completed
-- dynamically from the model artifact during export.
UPDATE volunteer_item vi
JOIN enrollment_plan ep ON ep.id = vi.plan_id
SET vi.two_year_min_rank = ep.two_year_min_rank,
    vi.three_year_min_rank = ep.three_year_min_rank
WHERE vi.two_year_min_rank IS NULL
   OR vi.three_year_min_rank IS NULL;
