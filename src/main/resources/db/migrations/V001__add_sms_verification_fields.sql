-- Database Migration: Add SMS verification fields to user table
-- Version: 001
-- Description: Adds SMS OTP verification fields for first login SMS verification feature

ALTER TABLE `user` ADD COLUMN `sms_phone_number` VARCHAR(20) NULL UNIQUE AFTER `phone`;

ALTER TABLE `user` ADD COLUMN `sms_verified` TINYINT(1) NOT NULL DEFAULT 0 AFTER `sms_phone_number`;

ALTER TABLE `user` ADD COLUMN `sms_verified_at` TIMESTAMP NULL AFTER `sms_verified`;

ALTER TABLE `user` ADD COLUMN `sms_otp_hash` VARCHAR(255) NULL AFTER `sms_verified_at`;

ALTER TABLE `user` ADD COLUMN `sms_otp_expires_at` TIMESTAMP NULL AFTER `sms_otp_hash`;

ALTER TABLE `user` ADD COLUMN `sms_otp_attempts` INT DEFAULT 0 AFTER `sms_otp_expires_at`;

ALTER TABLE `user` ADD COLUMN `sms_otp_last_sent_at` TIMESTAMP NULL AFTER `sms_otp_attempts`;

ALTER TABLE `user` ADD COLUMN `sms_otp_locked_until` TIMESTAMP NULL AFTER `sms_otp_last_sent_at`;

ALTER TABLE `user` ADD COLUMN `first_login_completed` TINYINT(1) NOT NULL DEFAULT 0 AFTER `sms_otp_locked_until`;

-- Create index for SMS phone number lookups
CREATE INDEX `idx_sms_phone_number` ON `user` (`sms_phone_number`);

-- Create index for SMS verification queries
CREATE INDEX `idx_sms_verified` ON `user` (`sms_verified`);

-- Create index for OTP expiry checks
CREATE INDEX `idx_sms_otp_expires_at` ON `user` (`sms_otp_expires_at`);

-- Add audit log table for SMS operations (optional but recommended)
CREATE TABLE IF NOT EXISTS `sms_audit_log` (
  `id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `operation` VARCHAR(50) NOT NULL,
  `status` VARCHAR(50) NOT NULL,
  `error_message` TEXT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_operation` (`operation`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
