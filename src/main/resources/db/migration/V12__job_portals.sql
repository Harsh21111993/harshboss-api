-- ============================================================================
-- Harsh-Boss — personalized job portals hub (V12)
-- Tracks job applications across 7 portals: Naukri, LinkedIn, Wellfound,
-- Instahyre, Otta, Turing, Cutshort.
-- ============================================================================

CREATE TABLE job_portal_applications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    resume_id       UUID REFERENCES resumes(id) ON DELETE SET NULL,
    portal          TEXT NOT NULL,                    -- 'NAUKRI' | 'LINKEDIN' | 'WELLFOUND' | 'INSTAHYRE' | 'OTTA' | 'TURING' | 'CUTSHORT'
    job_title       TEXT NOT NULL,
    company         TEXT,
    job_url         TEXT,                             -- the URL on the portal
    location        TEXT,
    salary          TEXT,
    work_mode       TEXT,                             -- 'REMOTE' | 'HYBRID' | 'ONSITE'
    status          TEXT NOT NULL DEFAULT 'NOT_APPLIED', -- 'NOT_APPLIED' | 'APPLIED' | 'INTERVIEW' | 'OFFER' | 'REJECTED'
    applied_at      TIMESTAMPTZ,
    interview_date  TIMESTAMPTZ,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_portal_apps_user ON job_portal_applications (user_id, updated_at DESC);
CREATE INDEX idx_portal_apps_portal ON job_portal_applications (user_id, portal);
CREATE INDEX idx_portal_apps_status ON job_portal_applications (user_id, status);
