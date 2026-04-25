-- ============================================================
-- ATS Module — Database Migration
-- Run against your MySQL database once.
-- Compatible with the existing UniLearn schema.
-- ============================================================

-- ── 1. Add pipeline_stage column to job_application ─────────────────────────
ALTER TABLE job_application
  ADD COLUMN IF NOT EXISTS pipeline_stage VARCHAR(50) NOT NULL DEFAULT 'SUBMITTED'
  COMMENT 'ATS pipeline stage: SUBMITTED|SCREENING|SHORTLISTED|INTERVIEW|OFFER_SENT|HIRED|REJECTED|WITHDRAWN';

-- Backfill: map existing status values to pipeline_stage where possible
UPDATE job_application SET pipeline_stage = status WHERE pipeline_stage = 'SUBMITTED';

-- Add index for fast stage-based filtering
CREATE INDEX IF NOT EXISTS idx_app_pipeline_stage ON job_application (pipeline_stage);

-- ── 2. Add notes column to job_application ───────────────────────────────────
ALTER TABLE job_application
  ADD COLUMN IF NOT EXISTS notes TEXT DEFAULT NULL
  COMMENT 'Free-text review notes added by partner/admin';

-- ── 3. Create ats_audit_log table ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ats_audit_log (
  id          INT           NOT NULL AUTO_INCREMENT,
  entity_type VARCHAR(50)   NOT NULL COMMENT 'JOB_APPLICATION | JOB_OFFER',
  entity_id   INT           NOT NULL,
  action      VARCHAR(100)  NOT NULL COMMENT 'STAGE_CHANGED | SCORE_CALCULATED | NOTE_ADDED | CV_EXTRACTED | ...',
  actor_id    INT           DEFAULT NULL COMMENT 'User who performed the action',
  old_value   TEXT          DEFAULT NULL,
  new_value   TEXT          DEFAULT NULL,
  details_json TEXT         DEFAULT NULL COMMENT 'Optional JSON with extra context',
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  INDEX idx_audit_entity  (entity_type, entity_id),
  INDEX idx_audit_actor   (actor_id),
  INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='ATS audit trail for all stage changes, scoring events and notes';

-- ── 4. Register AtsAuditLog with Hibernate ───────────────────────────────────
-- No SQL needed — the @Entity annotation on AtsAuditLog.java handles this.
-- Just make sure the table above is created before starting the app.

-- ── Verification queries ─────────────────────────────────────────────────────
-- SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
--   WHERE TABLE_NAME = 'job_application' AND TABLE_SCHEMA = DATABASE()
--   ORDER BY ORDINAL_POSITION;

-- SELECT COUNT(*) FROM ats_audit_log;
