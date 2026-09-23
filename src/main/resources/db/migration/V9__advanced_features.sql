-- ============================================================================
-- Harsh-Boss — 10 advanced features (V9)
-- Tables for: notifications, follow-up reminders, deadlines, tasks,
-- contacts, slack integration, meeting prep briefs, thread summaries.
-- ============================================================================

-- 1. Notifications (smart push notifications for HIGH-importance events)
CREATE TABLE notifications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type          TEXT NOT NULL,                    -- 'HIGH_EMAIL' | 'MEETING_CHANGE' | 'DEADLINE' | 'FOLLOW_UP' | 'MEETING_PREP'
    title         TEXT NOT NULL,
    body          TEXT NOT NULL,
    link_url      TEXT,                             -- deep link to the relevant email/event
    is_read       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at       TIMESTAMPTZ
);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id, is_read, created_at DESC);

-- 2. Follow-up reminders (unanswered HIGH-importance emails > 24h)
CREATE TABLE follow_up_reminders (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    important_email_id UUID REFERENCES important_emails(id) ON DELETE CASCADE,
    subject           TEXT NOT NULL,
    from_address      TEXT NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL,
    hours_overdue     INT NOT NULL,
    dismissed         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_followups_user_active ON follow_up_reminders (user_id, dismissed, created_at DESC);

-- 3. Deadlines (extracted from emails — "sign-off by Friday")
CREATE TABLE deadlines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    important_email_id UUID REFERENCES important_emails(id) ON DELETE SET NULL,
    title           TEXT NOT NULL,                  -- e.g. "Budget approval sign-off"
    due_at          TIMESTAMPTZ NOT NULL,
    source_subject  TEXT,                            -- the email subject it came from
    source_url      TEXT,                            -- link to the email in Gmail
    status          TEXT NOT NULL DEFAULT 'PENDING', -- 'PENDING' | 'DONE' | 'OVERDUE'
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_deadlines_user_due ON deadlines (user_id, due_at, status);

-- 4. Tasks (email action items converted to tasks)
CREATE TABLE tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    important_email_id UUID REFERENCES important_emails(id) ON DELETE SET NULL,
    title           TEXT NOT NULL,
    description     TEXT,
    source_url      TEXT,                            -- link to the email in Gmail
    external_id     TEXT,                             -- ID in Jira/Asana/Todoist if synced
    external_system TEXT,                             -- 'JIRA' | 'ASANA' | 'TODOIST' | 'LOCAL'
    status          TEXT NOT NULL DEFAULT 'TODO',    -- 'TODO' | 'IN_PROGRESS' | 'DONE'
    priority        TEXT NOT NULL DEFAULT 'MEDIUM',  -- 'HIGH' | 'MEDIUM' | 'LOW'
    due_at          TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);
CREATE INDEX idx_tasks_user_status ON tasks (user_id, status, created_at DESC);

-- 5. Contacts (relationship health tracking)
CREATE TABLE contacts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email_address       TEXT NOT NULL,
    name                TEXT NOT NULL,
    last_contacted_at   TIMESTAMPTZ,
    last_replied_at     TIMESTAMPTZ,
    total_emails        INT NOT NULL DEFAULT 0,
    total_replies       INT NOT NULL DEFAULT 0,
    avg_response_hours  NUMERIC(10, 2),
    relationship_health TEXT NOT NULL DEFAULT 'GOOD', -- 'GOOD' | 'STALE' | 'AT_RISK' | 'COLD'
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, email_address)
);
CREATE INDEX idx_contacts_user_health ON contacts (user_id, relationship_health);

-- 6. Slack integration config
CREATE TABLE slack_config (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    webhook_url     TEXT NOT NULL,
    channel         TEXT,                             -- e.g. '#important-emails'
    notify_high_only BOOLEAN NOT NULL DEFAULT TRUE,
    auto_forward    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. Thread summaries (cached summaries of email threads)
CREATE TABLE thread_summaries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    thread_subject  TEXT NOT NULL,
    decided         TEXT NOT NULL,                    -- what was decided
    pending         TEXT NOT NULL,                    -- what's pending
    action_needed   TEXT NOT NULL,                    -- what you need to do
    email_count     INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_threads_user ON thread_summaries (user_id, created_at DESC);

-- 8. Meeting prep briefs (generated 15 min before meetings)
CREATE TABLE meeting_prep_briefs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    calendar_event_id   UUID REFERENCES calendar_events(id) ON DELETE CASCADE,
    meeting_title       TEXT NOT NULL,
    meeting_start       TIMESTAMPTZ NOT NULL,
    attendees           TEXT NOT NULL,                -- JSON array
    relevant_emails     TEXT NOT NULL,                -- JSON array of email summaries
    last_meeting_notes  TEXT,
    agenda              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_meeting_prep_user ON meeting_prep_briefs (user_id, meeting_start DESC);
