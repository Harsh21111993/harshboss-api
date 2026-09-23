-- ============================================================================
-- Harsh-Boss — OAuth2 token storage (V5)
-- Stores the access/refresh tokens granted by Microsoft (Graph) or Google
-- (Gmail + Calendar) so the app can fetch REAL emails from the user's mailbox.
-- ============================================================================

CREATE TABLE user_oauth_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider        TEXT NOT NULL,                    -- 'MICROSOFT' | 'GOOGLE'
    access_token    TEXT NOT NULL,
    refresh_token   TEXT,                             -- may be null if provider didn't return one
    token_type      TEXT NOT NULL DEFAULT 'Bearer',
    expires_at      TIMESTAMPTZ NOT NULL,
    scope           TEXT,                             -- space-separated scopes granted
    email_address   TEXT,                             -- the mailbox address (from /me or userinfo)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, provider)
);

CREATE INDEX idx_oauth_tokens_user    ON user_oauth_tokens (user_id);
CREATE INDEX idx_oauth_tokens_refresh ON user_oauth_tokens (refresh_token);
