-- ====================================================================
-- AmniQuest Backend Schema Migration
-- Version: 1.0.0
-- Migration: 20261020000000_amniquest_backend_schema.sql
-- Platform: Supabase PostgreSQL + Row Level Security (RLS)
-- ====================================================================

-- 1. Extend public.profiles with AmniQuest Game & Launcher State
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS username TEXT UNIQUE,
    ADD COLUMN IF NOT EXISTS full_name TEXT,
    ADD COLUMN IF NOT EXISTS has_profile BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS xp INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS coins INT NOT NULL DEFAULT 90,
    ADD COLUMN IF NOT EXISTS level INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS inventory JSONB NOT NULL DEFAULT '{"STREAK_FREEZER": 2}'::jsonb,
    ADD COLUMN IF NOT EXISTS customization_info JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS achievements JSONB NOT NULL DEFAULT '["THE_EARLY_FEW"]'::jsonb,
    ADD COLUMN IF NOT EXISTS active_boosts JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS last_updated BIGINT NOT NULL DEFAULT (extract(epoch from now()) * 1000)::bigint,
    ADD COLUMN IF NOT EXISTS created_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS streak JSONB NOT NULL DEFAULT '{"currentStreak": 0, "longestStreak": 0, "lastCompletedDate": "0001-01-01", "streakFailureHistory": {}}'::jsonb,
    ADD COLUMN IF NOT EXISTS "blockedAndroidPackages" JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS "unlockedAndroidPackages" JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS fcm_tokens JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Ensure profiles select, insert, and update RLS policies exist for PostgREST upsert
DROP POLICY IF EXISTS "Users can view own profile" ON public.profiles;
CREATE POLICY "Users can view own profile" ON public.profiles
    FOR SELECT USING (auth.uid() = id);

DROP POLICY IF EXISTS "Users can insert own profile" ON public.profiles;
CREATE POLICY "Users can insert own profile" ON public.profiles
    FOR INSERT WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;
CREATE POLICY "Users can update own profile" ON public.profiles
    FOR UPDATE USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- Index for fast username availability lookups
CREATE INDEX IF NOT EXISTS idx_profiles_username ON public.profiles (lower(trim(username)));

-- 2. Create Quests Table (CommonQuestInfo)
CREATE TABLE IF NOT EXISTS public.quests (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    reward INT NOT NULL DEFAULT 5,
    integration_id TEXT NOT NULL DEFAULT 'DEEP_FOCUS',
    selected_days JSONB NOT NULL DEFAULT '[]'::jsonb,
    auto_destruct TEXT NOT NULL DEFAULT '9999-12-31',
    time_range JSONB NOT NULL DEFAULT '[0, 24]'::jsonb,
    created_on TEXT NOT NULL,
    last_completed_on TEXT NOT NULL DEFAULT '0001-01-01',
    instructions TEXT NOT NULL DEFAULT '',
    quest_json TEXT NOT NULL DEFAULT '',
    is_destroyed BOOLEAN NOT NULL DEFAULT FALSE,
    last_updated BIGINT NOT NULL DEFAULT (extract(epoch from now()) * 1000)::bigint,
    "isHardLock" BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Ensure columns exist if table was previously created
ALTER TABLE public.quests
    ADD COLUMN IF NOT EXISTS "isHardLock" BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_updated BIGINT NOT NULL DEFAULT (extract(epoch from now()) * 1000)::bigint,
    ADD COLUMN IF NOT EXISTS is_destroyed BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for per-user quest listing and syncing
CREATE INDEX IF NOT EXISTS idx_quests_user_id ON public.quests(user_id);
CREATE INDEX IF NOT EXISTS idx_quests_user_updated ON public.quests(user_id, last_updated DESC);

-- Enable RLS on quests
ALTER TABLE public.quests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can manage own quests" ON public.quests;
CREATE POLICY "Users can manage own quests" ON public.quests
    FOR ALL USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- 3. Create Quest Stats Table (StatsInfo)
CREATE TABLE IF NOT EXISTS public.quest_stats (
    id TEXT PRIMARY KEY,
    quest_id TEXT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for stats history queries
CREATE INDEX IF NOT EXISTS idx_quest_stats_user_date ON public.quest_stats(user_id, date DESC);

-- Enable RLS on quest_stats
ALTER TABLE public.quest_stats ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can manage own stats" ON public.quest_stats;
CREATE POLICY "Users can manage own stats" ON public.quest_stats
    FOR ALL USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- 4. Automatic Profile Provisioning Trigger
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    INSERT INTO public.profiles (id, email, display_name, full_name, username, created_at, updated_at)
    VALUES (
        NEW.id,
        COALESCE(NEW.email, ''),
        COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', ''),
        COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', ''),
        COALESCE(NEW.raw_user_meta_data->>'username', 'user_' || replace(NEW.id::text, '-', '')),
        NOW(),
        NOW()
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS tr_on_auth_user_created ON auth.users;
CREATE TRIGGER tr_on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_user();

-- 5. Storage Bucket Configuration for Avatars ("profile")
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'profile',
    'profile',
    TRUE,
    5242880, -- 5 MB
    ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO UPDATE SET
    public = TRUE,
    file_size_limit = 5242880,
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp'];

-- Storage RLS Policies
DROP POLICY IF EXISTS "Public can view profile avatars" ON storage.objects;
CREATE POLICY "Public can view profile avatars" ON storage.objects
    FOR SELECT USING (bucket_id = 'profile');

DROP POLICY IF EXISTS "Users can upload own avatar" ON storage.objects;
CREATE POLICY "Users can upload own avatar" ON storage.objects
    FOR INSERT WITH CHECK (
        bucket_id = 'profile' AND
        auth.uid()::text = (storage.foldername(name))[1]
    );

DROP POLICY IF EXISTS "Users can update own avatar" ON storage.objects;
CREATE POLICY "Users can update own avatar" ON storage.objects
    FOR UPDATE USING (
        bucket_id = 'profile' AND
        auth.uid()::text = (storage.foldername(name))[1]
    );

DROP POLICY IF EXISTS "Users can delete own avatar" ON storage.objects;
CREATE POLICY "Users can delete own avatar" ON storage.objects
    FOR DELETE USING (
        bucket_id = 'profile' AND
        auth.uid()::text = (storage.foldername(name))[1]
    );
