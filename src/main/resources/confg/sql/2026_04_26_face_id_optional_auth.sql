-- ============================================================
-- UniLearn Desktop - Optional Face ID Migration
-- Date: 2026-04-26
-- Target DB: MySQL 8+
-- ============================================================

-- 1) User-level Face ID fields (idempotent)
ALTER TABLE `user`
    ADD COLUMN IF NOT EXISTS `face_enabled` TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `face_descriptors` LONGTEXT NULL,
    ADD COLUMN IF NOT EXISTS `face_enrolled_at` DATETIME NULL;

-- 2) Verification audit table (idempotent)
CREATE TABLE IF NOT EXISTS `face_verification_log` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `action` VARCHAR(20) NOT NULL,
    `distance` DOUBLE NULL,
    `ip_address` VARCHAR(45) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_face_verification_log_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) Helpful index (only create if missing)
SET @face_log_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'face_verification_log'
      AND index_name = 'idx_face_verification_user_created'
);

SET @face_log_index_sql := IF(
    @face_log_index_exists = 0,
    'CREATE INDEX idx_face_verification_user_created ON face_verification_log (user_id, created_at)',
    'SELECT "idx_face_verification_user_created already exists"'
);

PREPARE stmt_face_log_index FROM @face_log_index_sql;
EXECUTE stmt_face_log_index;
DEALLOCATE PREPARE stmt_face_log_index;

-- 4) Optional check queries
-- SHOW COLUMNS FROM `user` LIKE 'face_%';
-- SHOW CREATE TABLE `face_verification_log`;
