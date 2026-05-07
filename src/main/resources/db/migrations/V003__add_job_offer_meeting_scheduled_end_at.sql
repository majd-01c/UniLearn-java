SET @scheduled_end_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'job_offer_meeting'
      AND column_name = 'scheduled_end_at'
);

SET @add_scheduled_end_column_sql := IF(
    @scheduled_end_column_exists = 0,
    'ALTER TABLE job_offer_meeting ADD COLUMN scheduled_end_at DATETIME NULL AFTER scheduled_at',
    'SELECT 1'
);

PREPARE add_scheduled_end_column_stmt FROM @add_scheduled_end_column_sql;
EXECUTE add_scheduled_end_column_stmt;
DEALLOCATE PREPARE add_scheduled_end_column_stmt;

UPDATE job_offer_meeting
SET scheduled_end_at = DATE_ADD(scheduled_at, INTERVAL 30 MINUTE)
WHERE scheduled_end_at IS NULL
  AND scheduled_at IS NOT NULL;

ALTER TABLE job_offer_meeting
    MODIFY scheduled_end_at DATETIME NOT NULL;

SET @meeting_window_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'job_offer_meeting'
      AND index_name = 'idx_job_offer_meeting_window'
);

SET @add_meeting_window_index_sql := IF(
    @meeting_window_index_exists = 0,
    'CREATE INDEX idx_job_offer_meeting_window ON job_offer_meeting (scheduled_at, scheduled_end_at)',
    'SELECT 1'
);

PREPARE add_meeting_window_index_stmt FROM @add_meeting_window_index_sql;
EXECUTE add_meeting_window_index_stmt;
DEALLOCATE PREPARE add_meeting_window_index_stmt;
