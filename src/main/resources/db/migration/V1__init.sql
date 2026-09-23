-- ============================================================================
-- Harsh-Boss — initial schema (V1)
-- All tables use UUID primary keys (PostgreSQL gen_random_uuid()).
-- ============================================================================

-- Enable pgcrypto for gen_random_uuid() (Postgres 13+ has it built-in, but be safe)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- emails
-- ---------------------------------------------------------------------------
CREATE TABLE emails (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_address  TEXT        NOT NULL,
    from_name     TEXT        NOT NULL,
    subject       TEXT        NOT NULL,
    body          TEXT        NOT NULL,
    folder        TEXT        NOT NULL,        -- EmailFolder enum (INBOX / SPAM)
    is_read       BOOLEAN     NOT NULL DEFAULT FALSE,
    received_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_emails_folder_read ON emails (folder, is_read);
CREATE INDEX idx_emails_received_at ON emails (received_at DESC);

-- ---------------------------------------------------------------------------
-- email_analyses (1:1 with emails)
-- ---------------------------------------------------------------------------
CREATE TABLE email_analyses (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id         UUID NOT NULL UNIQUE REFERENCES emails(id) ON DELETE CASCADE,
    brief            TEXT NOT NULL,
    label            TEXT NOT NULL,           -- EmailLabel
    importance       TEXT NOT NULL,           -- Importance
    score            INT  NOT NULL,
    reason           TEXT NOT NULL,
    suggested_action TEXT NOT NULL,
    action_items     TEXT NOT NULL,           -- JSON array string
    key_dates        TEXT NOT NULL,           -- JSON array string
    analyzed_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_email_analyses_email ON email_analyses (email_id);
CREATE INDEX idx_email_analyses_importance ON email_analyses (importance);

-- ---------------------------------------------------------------------------
-- calendar_events
-- ---------------------------------------------------------------------------
CREATE TABLE calendar_events (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title              TEXT        NOT NULL,
    platform           TEXT        NOT NULL,  -- Platform
    start_time         TIMESTAMPTZ NOT NULL,
    end_time           TIMESTAMPTZ NOT NULL,
    organizer          TEXT        NOT NULL,
    attendees          TEXT        NOT NULL,  -- JSON array string
    join_url           TEXT,
    location           TEXT,
    is_hidden_by_others BOOLEAN     NOT NULL DEFAULT FALSE,
    status             TEXT        NOT NULL   -- EventStatus (TENTATIVE / CONFIRMED)
);

CREATE INDEX idx_calendar_events_start_time ON calendar_events (start_time);
CREATE INDEX idx_calendar_events_platform   ON calendar_events (platform);

-- ---------------------------------------------------------------------------
-- approvals
-- ---------------------------------------------------------------------------
CREATE TABLE approvals (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type                   TEXT        NOT NULL,           -- "MEETING_PROPOSAL"
    requester_name         TEXT        NOT NULL,
    requester_email        TEXT        NOT NULL,
    requested_time         TIMESTAMPTZ NOT NULL,
    status                 TEXT        NOT NULL,            -- ApprovalStatus
    created_at             TIMESTAMPTZ NOT NULL,
    message                TEXT,
    proposal_title         TEXT        NOT NULL,
    proposed_start         TIMESTAMPTZ NOT NULL,
    duration_minutes       INT         NOT NULL,
    platform               TEXT        NOT NULL,            -- Platform
    conflict_event_title   TEXT,
    conflict_event_start   TIMESTAMPTZ,
    conflict_event_end     TIMESTAMPTZ,
    alternatives           TEXT,                             -- JSON array string of ISO timestamps
    auto_reply_sent        TEXT,
    decision_email_sent    TEXT
);

CREATE INDEX idx_approvals_status      ON approvals (status);
CREATE INDEX idx_approvals_created_at  ON approvals (created_at DESC);

-- ---------------------------------------------------------------------------
-- sent_emails
-- ---------------------------------------------------------------------------
CREATE TABLE sent_emails (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    to_address           TEXT        NOT NULL,
    to_name              TEXT,
    subject              TEXT        NOT NULL,
    body                 TEXT        NOT NULL,
    sent_at              TIMESTAMPTZ NOT NULL,
    reason               TEXT        NOT NULL,
    related_approval_id  UUID REFERENCES approvals(id) ON DELETE SET NULL
);

CREATE INDEX idx_sent_emails_related_approval ON sent_emails (related_approval_id);
CREATE INDEX idx_sent_emails_sent_at          ON sent_emails (sent_at DESC);
