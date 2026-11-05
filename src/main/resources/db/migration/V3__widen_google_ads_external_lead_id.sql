-- ============================================================================
-- V3: Widen Google Ads Integration Event Columns
-- Google Ads Lead Form webhook delivers SHA-256 / hex lead_id hashes up to 224+ characters.
-- This migration widens external_lead_id and related tracking attributes to prevent truncation.
-- ============================================================================

ALTER TABLE google_ads_lead_events MODIFY COLUMN external_lead_id VARCHAR(500) NOT NULL;
ALTER TABLE google_ads_lead_events MODIFY COLUMN gcl_id VARCHAR(500) NULL;
ALTER TABLE google_ads_lead_events MODIFY COLUMN form_id VARCHAR(255) NULL;
ALTER TABLE google_ads_lead_events MODIFY COLUMN campaign_id VARCHAR(255) NULL;
ALTER TABLE google_ads_lead_events MODIFY COLUMN adgroup_id VARCHAR(255) NULL;
ALTER TABLE google_ads_lead_events MODIFY COLUMN creative_id VARCHAR(255) NULL;
ALTER TABLE google_ads_lead_events MODIFY COLUMN lead_source VARCHAR(255) NULL;
ALTER TABLE google_ads_lead_events MODIFY COLUMN submission_time VARCHAR(255) NULL;
ALTER TABLE google_ads_lead_events MODIFY COLUMN email VARCHAR(150) NULL;
ALTER TABLE google_ads_lead_events MODIFY COLUMN course_interested VARCHAR(255) NULL;

-- Also widen website lead submissions external_lead_id in case tracking IDs are passed
ALTER TABLE website_lead_submissions MODIFY COLUMN external_lead_id VARCHAR(500) NULL;

