-- ============================================================================
-- V4: Add MISSED_COMPLETED to Follow-Up Status Enum
-- A previously missed follow-up that has subsequently been addressed by the user
-- taking a qualifying action (scheduling a new follow-up or updating lead status).
-- ============================================================================

ALTER TABLE lead_follow_ups MODIFY COLUMN status ENUM('CANCELLED','COMPLETED','MISSED','MISSED_COMPLETED','NOT_CONNECTED','PENDING','UPCOMING') NOT NULL;
