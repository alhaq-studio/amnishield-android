-- Migration: Harden Billing, RLS, Indexes, and Schema Unification
-- Project: AmnShield (alhaq-initiative / jrgpmcomvibgklmvnxud)

-- 1. Create missing indexes on foreign keys for high query performance
CREATE INDEX IF NOT EXISTS idx_devices_owner_id ON public.devices(owner_id);
CREATE INDEX IF NOT EXISTS idx_policies_owner_id ON public.policies(owner_id);
CREATE INDEX IF NOT EXISTS idx_policies_device_id ON public.policies(device_id);
CREATE INDEX IF NOT EXISTS idx_license_activations_profile_id ON public.license_activations(profile_id);

-- 2. Webhook Event Idempotency Ledger (Prevents duplicate Stripe event processing)
CREATE TABLE IF NOT EXISTS public.stripe_webhook_events (
    id TEXT PRIMARY KEY,
    event_type TEXT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.stripe_webhook_events ENABLE ROW LEVEL SECURITY;

-- 3. Unified View for Android / Client Sync Compatibility (Security Invoker RLS Enforced)
CREATE OR REPLACE VIEW public.sync_policies 
WITH (security_invoker = true) AS
SELECT 
    p.id,
    p.owner_id AS user_id,
    p.device_id,
    p.focus_mode_active,
    p.web_filter_enabled,
    p.app_blocker_enabled,
    p.adult_pack_active,
    p.social_pack_active,
    p.blocked_domains,
    p.blocked_apps,
    p.schedule_start,
    p.schedule_end,
    p.updated_at
FROM public.policies p;

GRANT SELECT ON public.sync_policies TO authenticated, anon;
