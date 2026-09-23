-- ============================================================================
-- Harsh-Boss — associate all data with the workspace user (V4)
-- Adds user_id to emails, calendar_events, approvals and backfills existing
-- rows with the default seeded user so nothing is "orphaned".
-- ============================================================================

-- ---------------------------------------------------------------------------
-- emails.user_id
-- ---------------------------------------------------------------------------
ALTER TABLE emails ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_emails_user_id ON emails (user_id);

-- Backfill: associate all pre-existing emails with the default user.
UPDATE emails SET user_id = (SELECT id FROM users LIMIT 1) WHERE user_id IS NULL;

-- ---------------------------------------------------------------------------
-- calendar_events.user_id
-- ---------------------------------------------------------------------------
ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_calendar_events_user_id ON calendar_events (user_id);

UPDATE calendar_events SET user_id = (SELECT id FROM users LIMIT 1) WHERE user_id IS NULL;

-- ---------------------------------------------------------------------------
-- approvals.user_id
-- ---------------------------------------------------------------------------
ALTER TABLE approvals ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_approvals_user_id ON approvals (user_id);

UPDATE approvals SET user_id = (SELECT id FROM users LIMIT 1) WHERE user_id IS NULL;

-- ---------------------------------------------------------------------------
-- sent_emails.user_id
-- ---------------------------------------------------------------------------
ALTER TABLE sent_emails ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_sent_emails_user_id ON sent_emails (user_id);

UPDATE sent_emails SET user_id = (SELECT id FROM users LIMIT 1) WHERE user_id IS NULL;
