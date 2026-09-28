-- Migration: 20260929120000_founding_supporters_registry.sql
-- Description: Founding Supporters Public Registry with Row Level Security (RLS) and immutability guarantees.

CREATE TABLE IF NOT EXISTS public.founding_supporters (
    id SERIAL PRIMARY KEY,
    handle TEXT NOT NULL,
    joined_date DATE NOT NULL DEFAULT CURRENT_DATE,
    tier TEXT NOT NULL DEFAULT 'Supporter',
    is_verified BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for ascending order display on Wall
CREATE INDEX IF NOT EXISTS idx_founding_supporters_id ON public.founding_supporters(id ASC);

-- Enable Row Level Security
ALTER TABLE public.founding_supporters ENABLE ROW LEVEL SECURITY;

-- Policy 1: Anyone (authenticated, anon, public) can read the Founding Supporters Wall
CREATE POLICY "Public read-only access to founding supporters wall"
    ON public.founding_supporters
    FOR SELECT
    TO public, anon, authenticated
    USING (is_verified = TRUE);

-- Policy 2: Service role full administrative access
CREATE POLICY "Service role full access on founding supporters"
    ON public.founding_supporters
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

-- Policy 3: Allow anonymous opt-in inserts from authenticated mobile app client
CREATE POLICY "Allow anonymous client opt-in insert"
    ON public.founding_supporters
    FOR INSERT
    TO anon, authenticated
    WITH CHECK (
        length(trim(handle)) > 0 AND
        length(handle) <= 32 AND
        tier IN ('Founder', 'Core Architect', 'Supporter', 'Pioneer')
    );

-- Seed Initial Founding Pioneers
INSERT INTO public.founding_supporters (id, handle, joined_date, tier, is_verified)
VALUES
    (1, 'Al-Haq Team', '2026-08-01', 'Founder', TRUE),
    (2, 'Habibur Rahman', '2026-08-10', 'Core Architect', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Reset sequence to continue from max id
SELECT setval('founding_supporters_id_seq', (SELECT COALESCE(MAX(id), 1) FROM public.founding_supporters));
