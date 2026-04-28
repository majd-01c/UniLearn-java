-- ══════════════════════════════════════════════════════════════════════
-- Migration: Add per-offer ATS score configuration
-- Run once on your database.
-- ══════════════════════════════════════════════════════════════════════

ALTER TABLE job_offer
    ADD COLUMN IF NOT EXISTS score_config VARCHAR(500) DEFAULT NULL
    COMMENT 'JSON: per-offer ATS scoring weights { expW, eduW, skillsW, langW }';
