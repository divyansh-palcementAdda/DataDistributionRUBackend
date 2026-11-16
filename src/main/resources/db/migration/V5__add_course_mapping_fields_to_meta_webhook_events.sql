-- ============================================================================
-- Migration: V5__add_course_mapping_fields_to_meta_webhook_events.sql
-- Description: Add audit and traceability fields for Meta Ads course mapping
-- ============================================================================

ALTER TABLE meta_webhook_events
    ADD COLUMN IF NOT EXISTS course_question VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS course_answer VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS resolved_course_id BINARY(16) NULL,
    ADD COLUMN IF NOT EXISTS mapping_method VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS mapping_reason VARCHAR(255) NULL;
