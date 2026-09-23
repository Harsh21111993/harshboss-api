-- ============================================================================
-- Harsh-Boss — lightweight important emails (V8)
--
-- ARCHITECTURE CHANGE: We no longer store ALL emails in our DB. Instead:
--   1. Sync fetches emails from Gmail/Outlook into memory
--   2. AI triages each in memory (brief, label, importance, score)
--   3. ONLY important emails (HIGH/MEDIUM) + meeting invitations are persisted
--   4. For full content, the user clicks "View in Gmail" → opens the email
--      in their provider's web UI (we store the providerMessageId + URL)
--
-- This keeps our DB lightweight — we're an intelligence layer, not a mailbox clone.
-- ============================================================================

CREATE TABLE important_emails (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider              TEXT NOT NULL,                    -- 'MICROSOFT' | 'GOOGLE'
    provider_message_id   TEXT NOT NULL,                    -- the email's ID in Gmail/Graph
    provider_url          TEXT,                             -- deep link to view in Gmail/Outlook
    from_address          TEXT NOT NULL,
    from_name             TEXT NOT NULL,
    subject               TEXT NOT NULL,
    brief                 TEXT NOT NULL,                    -- AI-generated 1-2 sentence summary
    label                 TEXT NOT NULL,                    -- EmailLabel
    importance            TEXT NOT NULL,                    -- Importance
    score                 INT  NOT NULL,                    -- 0-100
    reason                TEXT NOT NULL,                    -- why it's important
    suggested_action      TEXT NOT NULL,
    action_items          TEXT NOT NULL DEFAULT '[]',       -- JSON array
    received_at           TIMESTAMPTZ NOT NULL,             -- from the provider
    detected_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(), -- when our AI flagged it
    is_meeting_invitation BOOLEAN NOT NULL DEFAULT FALSE,   -- did this email also create a calendar event?
    UNIQUE (user_id, provider, provider_message_id)         -- dedup: don't re-add the same email
);

CREATE INDEX idx_important_emails_user       ON important_emails (user_id, detected_at DESC);
CREATE INDEX idx_important_emails_importance ON important_emails (user_id, importance);
CREATE INDEX idx_important_emails_label      ON important_emails (user_id, label);
