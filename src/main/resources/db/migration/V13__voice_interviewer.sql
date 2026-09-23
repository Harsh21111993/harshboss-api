-- ============================================================================
-- Harsh-Boss — AI Voice Interviewer (V13)
-- Tables for: interview_sessions, interview_turns (with pgvector embeddings)
-- ============================================================================

CREATE TABLE interview_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    resume_id       UUID REFERENCES resumes(id) ON DELETE SET NULL,
    tech_stack      TEXT NOT NULL,                    -- JSON: {language, frameworks, databases, years}
    status          TEXT NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS | COMPLETED | ABANDONED
    overall_score   NUMERIC(5, 2),                    -- average of all turn scores
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    summary         TEXT,                              -- AI-generated session summary
    weakness_tags   TEXT NOT NULL DEFAULT '[]',       -- JSON array of weakness topics
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_interview_sessions_user ON interview_sessions (user_id, started_at DESC);

CREATE TABLE interview_turns (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    question_number     INT NOT NULL,
    question_text       TEXT NOT NULL,
    question_embedding  vector(768),                     -- Gemini text-embedding-004 (768 dims)
    answer_transcript   TEXT NOT NULL,
    knowledge_score     INT NOT NULL DEFAULT 0,           -- 1-5 rubric
    communication_score INT NOT NULL DEFAULT 0,           -- 1-5 rubric
    problem_solving_score INT NOT NULL DEFAULT 0,         -- 1-5 rubric
    jev_pass_fail       TEXT,                             -- PASS | PARTIAL | FAIL (from Jev)
    jev_confidence      NUMERIC(5, 4),                    -- 0.0-1.0
    weakness_topic      TEXT,                             -- e.g., "Spring Boot: @Transactional Isolation"
    strengths           TEXT,                             -- what the candidate did well
    weaknesses          TEXT,                             -- what needs improvement
    feedback            TEXT,                             -- constructive feedback for the user
    next_question       TEXT,                             -- the next question generated
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_interview_turns_session ON interview_turns (session_id, question_number);
CREATE INDEX idx_interview_turns_weakness ON interview_turns (weakness_topic);
CREATE INDEX idx_interview_turns_embedding ON interview_turns USING hnsw (question_embedding vector_cosine_ops);
