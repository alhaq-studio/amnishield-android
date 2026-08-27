-- AmniShield Web Admin Console Database Schema
-- Migration: create_admin_console_schema

-- Profiles table (linked to Supabase Auth users)
CREATE TABLE IF NOT EXISTS profiles (
  id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email TEXT NOT NULL,
  display_name TEXT,
  role TEXT NOT NULL DEFAULT 'parent',
  is_premium BOOLEAN NOT NULL DEFAULT FALSE,
  license_key TEXT,
  stripe_customer_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Devices table (registered devices per user/family)
CREATE TABLE IF NOT EXISTS devices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  device_name TEXT NOT NULL,
  device_type TEXT NOT NULL,
  device_identifier TEXT,
  last_seen_at TIMESTAMPTZ,
  is_online BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Policies table (remote parental/admin rules)
CREATE TABLE IF NOT EXISTS policies (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  device_id UUID REFERENCES devices(id) ON DELETE CASCADE,
  focus_mode_active BOOLEAN NOT NULL DEFAULT TRUE,
  web_filter_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  app_blocker_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  adult_pack_active BOOLEAN NOT NULL DEFAULT FALSE,
  social_pack_active BOOLEAN NOT NULL DEFAULT FALSE,
  blocked_domains TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  blocked_apps TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  schedule_start TIME,
  schedule_end TIME,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- License activations log
CREATE TABLE IF NOT EXISTS license_activations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  license_key TEXT NOT NULL,
  activated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ,
  source TEXT
);

-- Enable Row-Level Security
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE license_activations ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY profiles_select ON profiles FOR SELECT USING (auth.uid() = id);
CREATE POLICY profiles_update ON profiles FOR UPDATE USING (auth.uid() = id);
CREATE POLICY profiles_insert ON profiles FOR INSERT WITH CHECK (auth.uid() = id);

CREATE POLICY devices_select ON devices FOR SELECT USING (auth.uid() = owner_id);
CREATE POLICY devices_insert ON devices FOR INSERT WITH CHECK (auth.uid() = owner_id);
CREATE POLICY devices_update ON devices FOR UPDATE USING (auth.uid() = owner_id);
CREATE POLICY devices_delete ON devices FOR DELETE USING (auth.uid() = owner_id);

CREATE POLICY policies_select ON policies FOR SELECT USING (auth.uid() = owner_id);
CREATE POLICY policies_insert ON policies FOR INSERT WITH CHECK (auth.uid() = owner_id);
CREATE POLICY policies_update ON policies FOR UPDATE USING (auth.uid() = owner_id);
CREATE POLICY policies_delete ON policies FOR DELETE USING (auth.uid() = owner_id);

CREATE POLICY activations_select ON license_activations FOR SELECT USING (auth.uid() = profile_id);
CREATE POLICY activations_insert ON license_activations FOR INSERT WITH CHECK (auth.uid() = profile_id);

-- Enable Realtime on policies and devices for live sync
ALTER PUBLICATION supabase_realtime ADD TABLE policies;
ALTER PUBLICATION supabase_realtime ADD TABLE devices;
