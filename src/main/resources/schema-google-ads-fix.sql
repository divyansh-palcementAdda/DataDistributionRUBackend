-- ============================================================================
-- SQL Fix for Google Ads Lead Truncation Error (SQL Error: 1406, SQLState: 22001)
-- Run this directly in MySQL on the production / staging database (DDS_PROJECT_DB):
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

ALTER TABLE website_lead_submissions MODIFY COLUMN external_lead_id VARCHAR(500) NULL;

