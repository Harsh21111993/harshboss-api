-- ============================================================================
-- Harsh-Boss — Job search feature (V10)
-- Tables for: resumes, jobs (cached), job_applications (tracking)
-- ============================================================================

CREATE TABLE resumes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name       TEXT NOT NULL,
    full_name       TEXT NOT NULL,
    email           TEXT,
    phone           TEXT,
    current_title   TEXT,
    years_experience INT,
    skills          TEXT NOT NULL DEFAULT '[]',
    experience      TEXT NOT NULL DEFAULT '[]',
    education       TEXT NOT NULL DEFAULT '[]',
    location        TEXT,
    summary         TEXT,
    preferred_role  TEXT,
    preferred_location TEXT,
    salary_expectation TEXT,
    embedding_id    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_resumes_user ON resumes (user_id);

CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source          TEXT NOT NULL,
    source_id       TEXT NOT NULL,
    title           TEXT NOT NULL,
    company         TEXT,
    location        TEXT,
    description     TEXT NOT NULL,
    salary          TEXT,
    job_type        TEXT,
    remote          BOOLEAN NOT NULL DEFAULT FALSE,
    apply_url       TEXT NOT NULL,
    posted_at       TIMESTAMPTZ,
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source, source_id)
);
CREATE INDEX idx_jobs_source ON jobs (source);
CREATE INDEX idx_jobs_remote ON jobs (remote);

CREATE TABLE job_applications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id          UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    resume_id       UUID REFERENCES resumes(id) ON DELETE SET NULL,
    status          TEXT NOT NULL DEFAULT 'NOT_APPLIED',
    match_score     NUMERIC(5, 2),
    match_reason    TEXT,
    applied_at      TIMESTAMPTZ,
    interview_date  TIMESTAMPTZ,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, job_id)
);
CREATE INDEX idx_applications_user_status ON job_applications (user_id, status);
