-- Migration: Prevent unauthorized client modifications to premium & role columns in public.profiles
-- Author: Amn Ecosystem Master Agent
-- Date: 2026-08-18

CREATE OR REPLACE FUNCTION public.protect_profile_privileged_fields()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    -- Only service_role can modify is_premium, role, or license_key
    IF auth.role() IS DISTINCT FROM 'service_role' THEN
        -- If an authenticated user or anon attempts to change privileged fields, reject or preserve original values
        IF (NEW.is_premium IS DISTINCT FROM OLD.is_premium) OR
           (NEW.role IS DISTINCT FROM OLD.role) OR
           (NEW.license_key IS DISTINCT FROM OLD.license_key) THEN
            RAISE EXCEPTION 'Unauthorized: Only service-role backend can modify is_premium, role, or license_key.';
        END IF;
    END IF;

    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- Drop trigger if already exists and recreate
DROP TRIGGER IF EXISTS tr_protect_profile_privileged_fields ON public.profiles;

CREATE TRIGGER tr_protect_profile_privileged_fields
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.protect_profile_privileged_fields();
