-- ============================================================================
-- Security Hardening: Function Search Path & Execution Privileges
-- Migration: 20260906120000_harden_function_security.sql
-- Resolves Supabase Security Advisor Lints:
--   - 0011_function_search_path_mutable (sync_devices_columns, get_device_policy)
--   - 0028_anon_security_definer_function_executable (get_device_policy)
--   - 0029_authenticated_security_definer_function_executable (get_device_policy)
-- ============================================================================

-- 1. Pin search_path on sync_devices_columns to prevent search_path hijacking
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_proc p 
        JOIN pg_namespace n ON p.pronamespace = n.oid 
        WHERE n.nspname = 'public' AND p.proname = 'sync_devices_columns'
    ) THEN
        EXECUTE 'ALTER FUNCTION public.sync_devices_columns() SET search_path = public';
        EXECUTE 'REVOKE EXECUTE ON FUNCTION public.sync_devices_columns() FROM PUBLIC, anon';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.sync_devices_columns() TO service_role';
    END IF;
END $$;

-- 2. Define/Harden get_device_policy with static search_path and caller authorization
CREATE OR REPLACE FUNCTION public.get_device_policy(p_device_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_policy RECORD;
BEGIN
    -- Authorization Check:
    -- Bypassed for service_role; enforced for authenticated users.
    IF auth.role() IS DISTINCT FROM 'service_role' THEN
        IF auth.uid() IS NULL THEN
            RAISE EXCEPTION 'Unauthorized: Authentication required.';
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM public.devices 
            WHERE id = p_device_id 
              AND (owner_id = auth.uid() OR user_id = auth.uid())
        ) THEN
            RAISE EXCEPTION 'Access denied: Device not found or unauthorized.';
        END IF;
    END IF;

    -- Query specific policies record if defined
    SELECT * INTO v_policy
    FROM public.policies
    WHERE device_id = p_device_id
    ORDER BY updated_at DESC
    LIMIT 1;

    IF FOUND THEN
        RETURN to_jsonb(v_policy);
    END IF;

    -- Fallback to devices.policy_payload if separate policy is not found
    RETURN COALESCE(
        (SELECT policy_payload FROM public.devices WHERE id = p_device_id),
        '{}'::jsonb
    );
END;
$$;

-- 3. Revoke public/anonymous execution on get_device_policy
REVOKE EXECUTE ON FUNCTION public.get_device_policy(UUID) FROM PUBLIC, anon;

-- 4. Restrict execution to authenticated callers and service_role
GRANT EXECUTE ON FUNCTION public.get_device_policy(UUID) TO authenticated, service_role;

-- 5. Additional System Security Hardening: Pin search_path on all existing functions
DO $$
BEGIN
    -- Pin search_path on generate_device_pairing_token
    IF EXISTS (
        SELECT 1 FROM pg_proc p 
        JOIN pg_namespace n ON p.pronamespace = n.oid 
        WHERE n.nspname = 'public' AND p.proname = 'generate_device_pairing_token'
    ) THEN
        EXECUTE 'ALTER FUNCTION public.generate_device_pairing_token(UUID) SET search_path = public';
    END IF;

    -- Pin search_path on protect_profile_privileged_fields
    IF EXISTS (
        SELECT 1 FROM pg_proc p 
        JOIN pg_namespace n ON p.pronamespace = n.oid 
        WHERE n.nspname = 'public' AND p.proname = 'protect_profile_privileged_fields'
    ) THEN
        EXECUTE 'ALTER FUNCTION public.protect_profile_privileged_fields() SET search_path = public';
    END IF;
END $$;
