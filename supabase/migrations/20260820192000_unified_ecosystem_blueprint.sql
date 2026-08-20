-- ==========================================================
-- AmniShield Ecosystem Architecture & Deployment Migration
-- Version: 2.0.0
-- Migration: 20260820192000_unified_ecosystem_blueprint.sql
-- ==========================================================

-- 1. Profiles: Add role and guardian master PIN support
ALTER TABLE IF EXISTS public.profiles 
    ADD COLUMN IF NOT EXISTS account_role TEXT CHECK (account_role IN ('personal', 'guardian', 'admin')) DEFAULT 'personal',
    ADD COLUMN IF NOT EXISTS guardian_master_pin_hash TEXT;

-- 2. Devices: Upgrade to Unified Policy Payload & Ephemeral Pairing
CREATE TABLE IF NOT EXISTS public.devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    device_name TEXT NOT NULL,
    platform TEXT CHECK (platform IN ('android', 'firefox', 'chromium', 'linux', 'windows', 'edge', 'chrome')) NOT NULL,
    pairing_token TEXT,
    pairing_token_expires_at TIMESTAMPTZ,
    is_managed BOOLEAN NOT NULL DEFAULT FALSE,
    guardian_pin_hash TEXT,
    policy_payload JSONB NOT NULL DEFAULT '{
        "strict_mode": false,
        "allow_unblur": true,
        "shield_theme": "emerald",
        "sensitivity": "medium",
        "blocked_categories": ["adult", "gambling"],
        "blocked_domains": [],
        "blocked_apps": []
    }'::jsonb,
    last_heartbeat TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Ensure columns exist if table was previously created
ALTER TABLE public.devices 
    ADD COLUMN IF NOT EXISTS pairing_token TEXT,
    ADD COLUMN IF NOT EXISTS pairing_token_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS is_managed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS guardian_pin_hash TEXT,
    ADD COLUMN IF NOT EXISTS policy_payload JSONB NOT NULL DEFAULT '{
        "strict_mode": false,
        "allow_unblur": true,
        "shield_theme": "emerald",
        "sensitivity": "medium",
        "blocked_categories": ["adult", "gambling"],
        "blocked_domains": [],
        "blocked_apps": []
    }'::jsonb,
    ADD COLUMN IF NOT EXISTS last_heartbeat TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 3. Licenses Table
CREATE TABLE IF NOT EXISTS public.licenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    license_key TEXT UNIQUE NOT NULL,
    tier TEXT CHECK (tier IN ('individual', 'family_multi', 'lifetime')) DEFAULT 'individual',
    max_devices INT NOT NULL DEFAULT 3,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Privacy-Safe Aggregate Protection Telemetry (No URL or personal data logging)
CREATE TABLE IF NOT EXISTS public.device_telemetry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    blocked_elements_count INT NOT NULL DEFAULT 0,
    active_focus_minutes INT NOT NULL DEFAULT 0,
    recorded_date DATE NOT NULL DEFAULT CURRENT_DATE,
    CONSTRAINT unique_device_date UNIQUE(device_id, recorded_date)
);

-- 5. Enable Row-Level Security on All Tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.licenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_telemetry ENABLE ROW LEVEL SECURITY;

-- 6. Drop existing duplicate policies if present to prevent conflict
DROP POLICY IF EXISTS "Users can manage own devices" ON public.devices;
DROP POLICY IF EXISTS "devices_select" ON public.devices;
DROP POLICY IF EXISTS "devices_insert" ON public.devices;
DROP POLICY IF EXISTS "devices_update" ON public.devices;
DROP POLICY IF EXISTS "devices_delete" ON public.devices;

DROP POLICY IF EXISTS "Users can view own licenses" ON public.licenses;
DROP POLICY IF EXISTS "Users can view own telemetry" ON public.device_telemetry;

-- 7. Define Hardened RLS Policies
CREATE POLICY "devices_owner_access" ON public.devices 
    FOR ALL USING (auth.uid() = owner_id);

CREATE POLICY "licenses_user_select" ON public.licenses 
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "telemetry_owner_access" ON public.device_telemetry 
    FOR ALL USING (auth.uid() = owner_id);

-- 8. Ephemeral Pairing Token Function
CREATE OR REPLACE FUNCTION public.generate_device_pairing_token(p_device_id UUID)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_token TEXT;
BEGIN
    -- Verify ownership
    IF NOT EXISTS (SELECT 1 FROM public.devices WHERE id = p_device_id AND owner_id = auth.uid()) THEN
        RAISE EXCEPTION 'Device not found or unauthorized';
    END IF;

    -- Generate a 6-digit random pairing token
    v_token := lpad((floor(random() * 900000 + 100000))::text, 6, '0');

    UPDATE public.devices
    SET pairing_token = v_token,
        pairing_token_expires_at = now() + interval '10 minutes'
    WHERE id = p_device_id AND owner_id = auth.uid();

    RETURN v_token;
END;
$$;

-- 9. Realtime Publication
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'devices'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.devices;
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'device_telemetry'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.device_telemetry;
    END IF;
END $$;
