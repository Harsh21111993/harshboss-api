-- ============================================================================
-- Harsh-Boss — link calendar events to source emails (V7)
-- When an email contains a meeting/interview invitation, the AI detects it and
-- auto-creates a calendar event linked back to the email via source_email_id.
-- The UI then shows a "View source email" link on the event.
-- ============================================================================

ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS source_email_id UUID REFERENCES emails(id) ON DELETE SET NULL;
ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS source_email_url TEXT;
CREATE INDEX IF NOT EXISTS idx_calendar_events_source_email ON calendar_events (source_email_id);
