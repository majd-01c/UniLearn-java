-- ============================================================================
-- Job Offer Module - Database Migration SQL (PROMPT 04)
-- ============================================================================
-- This migration script sets up database constraints and indexes for the Job
-- Offer module. Run this AFTER Hibernate has generated the tables.
-- 
-- Warning: These are DDL operations. Back up database before running.
-- ============================================================================

-- ─────────────────────────────────────────────────────────────────────────
-- 1. Verify Table Existence (job_offer)
-- ─────────────────────────────────────────────────────────────────────────

-- If job_offer table doesn't exist, Hibernate will create it from entity.
-- This script assumes it exists. If missing, run Hibernate first.

-- Verify structure:
-- SHOW CREATE TABLE job_offer;
-- SHOW CREATE TABLE job_application;
-- SHOW CREATE TABLE custom_skill;

-- ─────────────────────────────────────────────────────────────────────────
-- 2. Add Indexes for Query Performance
-- ─────────────────────────────────────────────────────────────────────────

-- *** job_offer table ***
-- Index for filtering offers by status (PENDING/ACTIVE/CLOSED/REJECTED)
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_offer' AND index_name = 'idx_status'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_status ON job_offer(status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index for filtering offers by type (INTERNSHIP/APPRENTICESHIP/JOB)
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_offer' AND index_name = 'idx_type'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_type ON job_offer(type)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index for location search/filtering
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_offer' AND index_name = 'idx_location'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_location ON job_offer(location)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index for sorting by creation date
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_offer' AND index_name = 'idx_created_at'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_created_at ON job_offer(created_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index for finding partner's offers (user_id in job_offer refers to partner)
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_offer' AND index_name = 'idx_partner_id'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_partner_id ON job_offer(partner_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index for published offers and expiry tracking
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_offer' AND index_name = 'idx_published_at'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_published_at ON job_offer(published_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_offer' AND index_name = 'idx_expires_at'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_expires_at ON job_offer(expires_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- *** job_application table ***
-- Index for filtering applications by status (SUBMITTED/REVIEWED/ACCEPTED/REJECTED)
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_application' AND index_name = 'idx_app_status'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_app_status ON job_application(status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index for sorting by creation date
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_application' AND index_name = 'idx_app_created_at'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_app_created_at ON job_application(created_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index for finding student's applications (user_id in job_application refers to student)
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_application' AND index_name = 'idx_student_id'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_student_id ON job_application(student_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index for finding all applicants for an offer
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_application' AND index_name = 'idx_offer_id'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_offer_id ON job_application(offer_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index for sorting/filtering by score (used in review/ranking)
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_application' AND index_name = 'idx_score'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_score ON job_application(score)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index for notification tracking
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'job_application' AND index_name = 'idx_status_notified'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_status_notified ON job_application(status_notified)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- *** custom_skill table ***
-- Index for finding partner's custom skills
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'custom_skill' AND index_name = 'idx_skill_partner_id'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_skill_partner_id ON custom_skill(partner_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index for searching skills by name
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'custom_skill' AND index_name = 'idx_skill_name'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_skill_name ON custom_skill(name)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────────────────
-- 3. Add/Verify Unique Constraints
-- ─────────────────────────────────────────────────────────────────────────

-- *** CRITICAL: Prevent duplicate applications ***
-- Each student can only apply once per offer
-- This constraint should already exist from Hibernate entity mapping:
-- @Table(uniqueConstraints = @UniqueConstraint(columnNames={"offer_id", "student_id"}))
--
-- To verify it exists:
-- SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
-- WHERE TABLE_NAME = 'job_application' AND CONSTRAINT_TYPE = 'UNIQUE';
--
-- If it doesn't exist, add it manually:
SET @uq_exists := (
    SELECT COUNT(1)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'job_application'
      AND constraint_type = 'UNIQUE'
      AND constraint_name = 'unique_student_offer'
);
SET @sql := IF(@uq_exists = 0,
    'ALTER TABLE job_application ADD CONSTRAINT unique_student_offer UNIQUE KEY (offer_id, student_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Prevent duplicate custom skill names per partner
SET @uq_exists := (
    SELECT COUNT(1)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'custom_skill'
      AND constraint_type = 'UNIQUE'
      AND constraint_name = 'unique_skill_per_partner'
);
SET @sql := IF(@uq_exists = 0,
    'ALTER TABLE custom_skill ADD CONSTRAINT unique_skill_per_partner UNIQUE KEY (partner_id, name)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────────────────
-- 4. Foreign Key Verification
-- ─────────────────────────────────────────────────────────────────────────

-- Hibernate automatically creates foreign keys from @ManyToOne mappings.
-- These should already exist:

-- job_offer.partner_id → user.id
-- job_application.student_id → user.id
-- job_application.offer_id → job_offer.id
-- custom_skill.partner_id → user.id

-- Verify they exist:
-- SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS 
-- WHERE TABLE_NAME = 'job_offer';

-- ─────────────────────────────────────────────────────────────────────────
-- 5. Column Definitions (for reference)
-- ─────────────────────────────────────────────────────────────────────────

-- *** job_offer table columns ***
-- id INT AUTO_INCREMENT PRIMARY KEY
-- partner_id INT NOT NULL → user(id)
-- title VARCHAR(255) NOT NULL
-- type VARCHAR(50) - INTERNSHIP, APPRENTICESHIP, JOB
-- status VARCHAR(50) - PENDING, ACTIVE, CLOSED, REJECTED
-- location VARCHAR(255)
-- description TEXT
-- requirements TEXT
-- required_skills TEXT (JSON or comma-separated)
-- preferred_skills TEXT (JSON or comma-separated)
-- min_experience_years INT DEFAULT 0
-- min_education VARCHAR(100)
-- required_languages TEXT
-- created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
-- updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
-- published_at TIMESTAMP NULL
-- expires_at TIMESTAMP NULL

-- *** job_application table columns ***
-- id INT AUTO_INCREMENT PRIMARY KEY
-- offer_id INT NOT NULL → job_offer(id) [UNIQUE with student_id]
-- student_id INT NOT NULL → user(id) [UNIQUE with offer_id]
-- message TEXT
-- cv_file_name VARCHAR(255)
-- status VARCHAR(50) - SUBMITTED, REVIEWED, ACCEPTED, REJECTED
-- score INT NULL (Phase 2: AI scoring)
-- score_breakdown TEXT (Phase 2: JSON breakdown)
-- created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
-- updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
-- scored_at TIMESTAMP NULL (Phase 2: When AI scored)
-- extracted_data TEXT (Phase 2: Parsed CV data)
-- status_notified TINYINT DEFAULT 0 (Phase 2: Notification tracking)
-- status_notified_at TIMESTAMP NULL (Phase 2: When notified)
-- status_message VARCHAR(255)

-- *** custom_skill table columns ***
-- id INT AUTO_INCREMENT PRIMARY KEY
-- partner_id INT NOT NULL → user(id)
-- name VARCHAR(100) NOT NULL
-- description TEXT
-- category VARCHAR(100)
-- created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
-- updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP

-- ─────────────────────────────────────────────────────────────────────────
-- 6. Phase 1 Completion Checklist
-- ─────────────────────────────────────────────────────────────────────────

/*
After running this migration, verify:

✓ Indexes created for performance:
  - job_offer: status, type, location, created_at, partner_id, published_at
  - job_application: status, created_at, student_id, offer_id, score
  - custom_skill: partner_id, name

✓ Unique constraints in place:
  - job_application(offer_id, student_id) - Prevents duplicate applications
  - custom_skill(partner_id, name) - Prevents duplicate skill names per partner

✓ Foreign keys intact:
  - job_offer.partner_id → user.id
  - job_application.student_id/offer_id → user.id / job_offer.id
  - custom_skill.partner_id → user.id

✓ No ATS or AI columns added in Phase 1
  - Score, scoreBreakdown, extractedData, statusNotified reserved for Phase 2
  - These exist but are NULL/unused

Verification queries:
SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_NAME = 'job_offer' OR TABLE_NAME = 'job_application' 
OR TABLE_NAME = 'custom_skill';

SELECT CONSTRAINT_NAME, TABLE_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
WHERE TABLE_SCHEMA = 'UniLearn' AND TABLE_NAME LIKE 'job_%' 
AND CONSTRAINT_TYPE = 'UNIQUE';
*/

-- ═══════════════════════════════════════════════════════════════════════
-- END Migration Script
-- ═══════════════════════════════════════════════════════════════════════
