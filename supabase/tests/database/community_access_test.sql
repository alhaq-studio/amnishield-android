-- Al-Haq Initiative Community Access Program Database Tests
-- pgTAP Test Suite for PostgreSQL RPC & Table Constraints
BEGIN;
SELECT plan(8);

-- Test 1: Check table exists
SELECT has_table('community_access_requests', 'Table community_access_requests should exist');

-- Test 2: Check columns exist
SELECT columns_are(
    'community_access_requests',
    ARRAY['id', 'app_id', 'user_id', 'user_name', 'email', 'device_identifier', 'status', 'requested_at', 'temp_expires_at', 'verified_expires_at', 'license_key', 'is_flagged', 'flag_reason', 'created_at', 'updated_at'],
    'Table should have all expected schema columns'
);

-- Test 3: Check check_community_grant_eligibility function exists
SELECT has_function('check_community_grant_eligibility', ARRAY['text'], 'RPC function check_community_grant_eligibility(text) should exist');

-- Test 4: Check eligibility for fresh email returns true
SELECT is(
    (public.check_community_grant_eligibility('brandnew@alhaq.org') ->> 'eligible')::boolean,
    true,
    'Fresh email should be eligible for community access'
);

-- Test 5: Check empty or whitespace email returns false
SELECT is(
    (public.check_community_grant_eligibility('   ') ->> 'eligible')::boolean,
    false,
    'Empty or whitespace email should be rejected'
);

-- Test 6: Insert pending request and assert eligibility returns false
INSERT INTO public.community_access_requests (app_id, user_name, email, status)
VALUES ('test-app-01', 'Test User', 'pending@alhaq.org', 'pending_review');

SELECT is(
    (public.check_community_grant_eligibility('pending@alhaq.org') ->> 'eligible')::boolean,
    false,
    'Pending request email should not be eligible for new grant'
);

-- Test 7: Cancelled request should allow re-eligibility
INSERT INTO public.community_access_requests (app_id, user_name, email, status)
VALUES ('test-app-02', 'Cancelled User', 'cancelled@alhaq.org', 'cancelled');

SELECT is(
    (public.check_community_grant_eligibility('cancelled@alhaq.org') ->> 'eligible')::boolean,
    true,
    'Cancelled request email should be re-eligible for grant'
);

-- Test 8: Flagged email should return eligible: false
INSERT INTO public.community_access_requests (app_id, user_name, email, status, is_flagged)
VALUES ('test-app-03', 'Flagged User', 'flagged@alhaq.org', 'flagged_email', true);

SELECT is(
    (public.check_community_grant_eligibility('flagged@alhaq.org') ->> 'eligible')::boolean,
    false,
    'Flagged email should be blocked from eligibility'
);

SELECT * FROM finish();
ROLLBACK;
