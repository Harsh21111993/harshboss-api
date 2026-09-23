-- ============================================================================
-- Harsh-Boss — users table (V3)
-- Stores the workspace owner (the "logged-in" user). In production this is
-- replaced by OAuth2/JWT auth; for now we seed one default user and expose
-- GET /api/users/me to return it.
-- ============================================================================

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name     TEXT        NOT NULL,
    email         TEXT        NOT NULL UNIQUE,
    title         TEXT,                          -- e.g. "Senior Engineering Manager"
    avatar_color  TEXT        NOT NULL DEFAULT 'emerald',
    timezone      TEXT        NOT NULL DEFAULT 'Asia/Calcutta',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed the default workspace user. Change these values to your own name/email.
-- IMPORTANT: the id is pinned so the V3 seed can reference it if needed later.
INSERT INTO users (id, full_name, email, title, avatar_color, timezone)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Aarav Patel',
    'aarav.patel@atlas.ai',
    'Senior Engineering Manager',
    'emerald',
    'Asia/Calcutta'
);
