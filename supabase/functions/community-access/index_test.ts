// Deno Unit Tests for Al-Haq Initiative Community Access Edge Function
// Run with: deno test --allow-net --allow-env supabase/functions/community-access/index_test.ts

import { assertEquals, assertNotEquals } from "https://deno.land/std@0.192.0/testing/asserts.ts";
import {
  FALLBACK_DISPOSABLE_DOMAINS,
  getDisposableDomains,
  isDisposableEmail,
  signCommunityLicense,
  handleRequest
} from "./index.ts";

const TEST_PRIVATE_KEY_PEM = `-----BEGIN PRIVATE KEY-----
MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCADPX9tuEQ27m1lup+n
j/tar6XV7sqbp3IHCWE/7Yh1qQ==
-----END PRIVATE KEY-----`;

Deno.test("Disposable Domains: Fallback list contains known disposable domains", () => {
  assertEquals(FALLBACK_DISPOSABLE_DOMAINS.has("mailinator.com"), true);
  assertEquals(FALLBACK_DISPOSABLE_DOMAINS.has("tempmail.com"), true);
  assertEquals(FALLBACK_DISPOSABLE_DOMAINS.has("trashmail.com"), true);
  assertEquals(FALLBACK_DISPOSABLE_DOMAINS.has("alhaq.org"), false);
  assertEquals(FALLBACK_DISPOSABLE_DOMAINS.has("gmail.com"), false);
});

Deno.test("Disposable Domains: isDisposableEmail correctly detects domains", async () => {
  const isDisposable1 = await isDisposableEmail("user@mailinator.com");
  const isDisposable2 = await isDisposableEmail("fake@tempmail.com");
  const isDisposable3 = await isDisposableEmail("legit@alhaq.org");
  const isDisposable4 = await isDisposableEmail("student@university.edu");
  const isDisposableMalformed = await isDisposableEmail("invalid-email");

  assertEquals(isDisposable1, true);
  assertEquals(isDisposable2, true);
  assertEquals(isDisposable3, false);
  assertEquals(isDisposable4, false);
  assertEquals(isDisposableMalformed, false);
});

Deno.test("Cryptographic Signing: signCommunityLicense produces valid ECDSA token structure", async () => {
  const email = "community-tester@alhaq.org";
  const appId = "CAP-TEST-99999";
  const expires = Date.now() + 365 * 24 * 60 * 60 * 1000;

  const token = await signCommunityLicense(email, appId, expires, TEST_PRIVATE_KEY_PEM);
  assertNotEquals(token, "");

  const parts = token.split(".");
  assertEquals(parts.length, 2);

  const payloadJson = atob(parts[0]);
  const payload = JSON.parse(payloadJson);

  assertEquals(payload.email, email);
  assertEquals(payload.app_id, appId);
  assertEquals(payload.type, "community_pass");
  assertEquals(payload.version, 1);
  assertEquals(payload.expires, expires);
  assertNotEquals(parts[1], "");
});

Deno.test("HTTP Routing: OPTIONS preflight returns CORS status 200", async () => {
  const req = new Request("http://localhost/community-access", {
    method: "OPTIONS"
  });
  const res = await handleRequest(req);
  assertEquals(res.status, 200);
});

Deno.test("HTTP Routing: GET method is rejected with 405 Method Not Allowed", async () => {
  const req = new Request("http://localhost/community-access", {
    method: "GET"
  });
  const res = await handleRequest(req);
  assertEquals(res.status, 405);
  const data = await res.json();
  assertEquals(data.error, "Method Not Allowed");
});

Deno.test("HTTP Validation: Missing or invalid email returns 400 Bad Request", async () => {
  const req = new Request("http://localhost/community-access", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action: "request", email: "invalid-email" })
  });
  const res = await handleRequest(req);
  assertEquals(res.status, 400);
  const data = await res.json();
  assertEquals(data.error, "A valid email address is required.");
});

Deno.test("HTTP Validation: Disposable email returns 400 Bad Request with explanation", async () => {
  const req = new Request("http://localhost/community-access", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action: "request", email: "throwaway@mailinator.com" })
  });
  const res = await handleRequest(req);
  assertEquals(res.status, 400);
  const data = await res.json();
  assertEquals(
    data.error,
    "Disposable or temporary email addresses are not permitted. Please apply with your permanent email address."
  );
});

Deno.test("HTTP Actions: Cancel action succeeds with 200 OK", async () => {
  const req = new Request("http://localhost/community-access", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action: "cancel", email: "user@alhaq.org" })
  });
  const res = await handleRequest(req);
  assertEquals(res.status, 200);
  const data = await res.json();
  assertEquals(data.success, true);
  assertEquals(data.status, "cancelled");
});
