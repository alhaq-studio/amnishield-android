-- Database Security Hardening Tests
-- pgTAP Test Suite for Function Security & Privileges
BEGIN;
SELECT plan(4);

-- Test 1: Function get_device_policy exists
SELECT has_function('get_device_policy', ARRAY['uuid'], 'Function get_device_policy(uuid) should exist');

-- Test 2: Function is SECURITY DEFINER
SELECT is_definer('get_device_policy', ARRAY['uuid'], 'Function get_device_policy should be SECURITY DEFINER');

-- Test 3: Unauthenticated caller cannot execute or view device policies
SELECT throws_ok(
    $$ SELECT public.get_device_policy('00000000-0000-0000-0000-000000000000'::uuid) $$,
    'Unauthorized: Authentication required.',
    'Function get_device_policy should block unauthenticated access'
);

-- Test 4: Verify check_community_grant_eligibility exists
SELECT has_function('check_community_grant_eligibility', ARRAY['text'], 'Function check_community_grant_eligibility(text) should exist');

SELECT * FROM finish();
ROLLBACK;
