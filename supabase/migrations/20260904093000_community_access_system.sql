-- Al-Haq Initiative Community Access Program Schema
-- Migration: 20260904093000_community_access_system.sql

CREATE TABLE IF NOT EXISTS public.community_access_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_id TEXT UNIQUE NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    user_name TEXT NOT NULL,
    email TEXT NOT NULL,
    device_identifier TEXT,
    status TEXT NOT NULL DEFAULT 'pending_review' 
        CHECK (status IN ('pending_review', 'verified_active', 'flagged_email', 'cancelled')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    temp_expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '7 days'),
    verified_expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '372 days'),
    license_key TEXT,
    is_flagged BOOLEAN NOT NULL DEFAULT FALSE,
    flag_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for fast query lookup & rate limiting
CREATE INDEX IF NOT EXISTS idx_comm_access_email ON public.community_access_requests(email);
CREATE INDEX IF NOT EXISTS idx_comm_access_app_id ON public.community_access_requests(app_id);
CREATE INDEX IF NOT EXISTS idx_comm_access_status ON public.community_access_requests(status);

-- Enable Row Level Security
ALTER TABLE public.community_access_requests ENABLE ROW LEVEL SECURITY;

-- Security Definer RPC: Check Grant Eligibility without exposing table data to clients
CREATE OR REPLACE FUNCTION public.check_community_grant_eligibility(target_email TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    clean_email TEXT;
    existing_record RECORD;
BEGIN
    clean_email := lower(trim(target_email));
    
    IF clean_email IS NULL OR clean_email = '' THEN
        RETURN jsonb_build_object(
            'eligible', false,
            'reason', 'Email address is required.'
        );
    END IF;

    -- Look for any non-cancelled grant requested in the last 365 days
    SELECT id, app_id, status, temp_expires_at, verified_expires_at, is_flagged, requested_at
    INTO existing_record
    FROM public.community_access_requests
    WHERE email = clean_email
      AND status != 'cancelled'
      AND requested_at > (now() - INTERVAL '365 days')
    ORDER BY requested_at DESC
    LIMIT 1;

    IF FOUND THEN
        IF existing_record.is_flagged THEN
            RETURN jsonb_build_object(
                'eligible', false,
                'status', 'flagged',
                'reason', 'The email associated with this grant was flagged as invalid or unreachable. Please re-apply with a permanent email.'
            );
        ELSE
            RETURN jsonb_build_object(
                'eligible', false,
                'status', existing_record.status,
                'app_id', existing_record.app_id,
                'reason', 'An active or pending community grant already exists for this email.'
            );
        END IF;
    END IF;

    RETURN jsonb_build_object(
        'eligible', true,
        'reason', 'Eligible for Al-Haq Community Access Program.'
    );
END;
$$;

-- Allow authenticated and anon roles to execute the eligibility check function
GRANT EXECUTE ON FUNCTION public.check_community_grant_eligibility(TEXT) TO authenticated, anon;

-- RLS Policies: Service role has full access; Authenticated users can view only their own records
CREATE POLICY "Service role full access on community requests"
    ON public.community_access_requests
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

CREATE POLICY "Users can view own community requests"
    ON public.community_access_requests
    FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id OR email = lower(auth.jwt()->>'email'));
