-- =====================================================================================
-- DDS Production Migration: In-App Notification System & Follow-up Reminders
-- Database: MySQL 8+ compatible
-- Timezone: Asia/Kolkata
-- Safe execution: Idempotent with IF NOT EXISTS
-- =====================================================================================

-- 1. Main In-App Notifications Table
CREATE TABLE IF NOT EXISTS `notifications` (
    `id` BINARY(16) NOT NULL,
    `recipient_id` BINARY(16) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `message` TEXT NOT NULL,
    `type` VARCHAR(50) NOT NULL,
    `priority` VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    `related_entity_type` VARCHAR(50) NULL,
    `related_entity_id` VARCHAR(64) NULL,
    `navigation_url` VARCHAR(500) NULL,
    `is_read` BOOLEAN NOT NULL DEFAULT FALSE,
    `read_at` DATETIME NULL,
    `expires_at` DATETIME NULL,
    `metadata` TEXT NULL,
    `idempotency_key` VARCHAR(150) NULL,
    `is_deleted` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_by` VARCHAR(255) NULL,
    `updated_by` VARCHAR(255) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_notif_recipient` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes on notifications for fast user inbox and unread counting
CREATE INDEX `idx_notif_recipient_read_created` ON `notifications` (`recipient_id`, `is_read`, `is_deleted`, `created_at` DESC);
CREATE INDEX `idx_notif_recipient_created` ON `notifications` (`recipient_id`, `created_at` DESC);
CREATE UNIQUE INDEX `idx_notif_idempotency` ON `notifications` (`idempotency_key`);


-- 2. Follow-Up Reminders Queue (Reliable Database-Backed Engine)
CREATE TABLE IF NOT EXISTS `follow_up_reminders` (
    `id` BINARY(16) NOT NULL,
    `follow_up_id` BINARY(16) NOT NULL,
    `lead_id` BINARY(16) NOT NULL,
    `recipient_user_id` BINARY(16) NOT NULL,
    `reminder_type` VARCHAR(20) NOT NULL,
    `scheduled_for` DATETIME NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `attempt_count` INT NOT NULL DEFAULT 0,
    `dispatched_at` DATETIME NULL,
    `claimed_at` DATETIME NULL,
    `claimed_by` VARCHAR(100) NULL,
    `error_message` TEXT NULL,
    `idempotency_key` VARCHAR(150) NOT NULL,
    `is_deleted` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_by` VARCHAR(255) NULL,
    `updated_by` VARCHAR(255) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_fur_follow_up` FOREIGN KEY (`follow_up_id`) REFERENCES `lead_follow_ups` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_fur_lead` FOREIGN KEY (`lead_id`) REFERENCES `leads` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_fur_recipient` FOREIGN KEY (`recipient_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for the reminder dispatch scheduler
CREATE INDEX `idx_fur_due_status` ON `follow_up_reminders` (`status`, `scheduled_for`);
CREATE INDEX `idx_fur_followup_status` ON `follow_up_reminders` (`follow_up_id`, `status`);
CREATE INDEX `idx_fur_recipient` ON `follow_up_reminders` (`recipient_user_id`);
CREATE UNIQUE INDEX `idx_fur_idempotency` ON `follow_up_reminders` (`idempotency_key`);


-- 3. User Notification Preferences Table
CREATE TABLE IF NOT EXISTS `user_notification_preferences` (
    `id` BINARY(16) NOT NULL,
    `user_id` BINARY(16) NOT NULL,
    `follow_up_reminders_enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `lead_allocation_enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `lead_reassignment_enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `task_assignment_enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `email_notifications_enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `browser_notifications_enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `is_deleted` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_by` VARCHAR(255) NULL,
    `updated_by` VARCHAR(255) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_unp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    UNIQUE KEY `uk_unp_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

