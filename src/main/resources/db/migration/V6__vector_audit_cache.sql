-- ============================================================================
-- Harsh-Boss — advanced AI layer (V6)
--   1. pgvector extension for email embeddings (semantic search)
--   2. audit_log table (LLMOps: every AI interaction persisted)
--   3. semantic_cache table (cache LLM responses by embedding similarity)
--   4. chat_memory tables (Spring AI JDBC ChatMemoryRepository)
-- ============================================================================

-- 1. pgvector — enables vector columns + cosine similarity search
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------
-- 2. audit_log — every AI call (user message, AI response, tokens, cost, latency)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    interaction_id  UUID NOT NULL,             -- groups request + response
    direction       TEXT NOT NULL,             -- 'REQUEST' | 'RESPONSE'
    content         TEXT NOT NULL,             -- the prompt or the response
    prompt_hash     TEXT,                      -- SHA-256 of the prompt (dedup)
    model           TEXT,
    prompt_tokens   INT,
    completion_tokens INT,
    total_tokens    INT,
    estimated_cost_usd NUMERIC(10, 6),
    latency_ms      BIGINT,
    flagged         BOOLEAN NOT NULL DEFAULT FALSE,  -- injection / blocked
    flag_reason     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_user        ON audit_log (user_id, created_at DESC);
CREATE INDEX idx_audit_log_interaction ON audit_log (interaction_id);
CREATE INDEX idx_audit_log_flagged     ON audit_log (flagged) WHERE flagged = TRUE;

-- ---------------------------------------------------------------------------
-- 3. semantic_cache — cache LLM responses by embedding similarity
--    When a new question comes in, embed it, search this table for
--    cosine distance < 0.08 (similarity > 0.92). If found, return the cached
--    answer instead of calling the LLM. Saves cost + latency.
-- ---------------------------------------------------------------------------
CREATE TABLE semantic_cache (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
    question        TEXT NOT NULL,
    question_embedding vector(768),           -- Gemini text-embedding-004 = 768 dims
    answer          TEXT NOT NULL,
    model           TEXT,
    tokens_used    INT,
    hit_count       INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ               -- null = no expiry
);

CREATE INDEX idx_semantic_cache_user   ON semantic_cache (user_id);
CREATE INDEX idx_semantic_cache_embedding ON semantic_cache
    USING ivfflat (question_embedding vector_cosine_ops) WITH (lists = 100);

-- ---------------------------------------------------------------------------
-- 4. chat_memory — Spring AI JDBC ChatMemoryRepository
--    Persists conversation memory so the agent remembers context across
--    restarts. Spring AI auto-creates this if spring-ai-model-jdbc is on the
--    classpath, but we define it explicitly for Flyway-managed schema.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id TEXT NOT NULL,
    content         TEXT NOT NULL,
    type            TEXT NOT NULL,            -- USER / ASSISTANT / SYSTEM
    "timestamp"     TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_memory_conv ON SPRING_AI_CHAT_MEMORY (conversation_id);
