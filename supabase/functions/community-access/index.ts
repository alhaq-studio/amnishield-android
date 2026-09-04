import { serve } from "https://deno.land/std@0.192.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const PRIVATE_KEY_PEM = (Deno.env.get("ECDSA_PRIVATE_KEY_PEM") ?? "").replace(/^["']|["']$/g, "").trim();
const SUPABASE_URL = (Deno.env.get("SUPABASE_URL") ?? "https://jrgpmcomvibgklmvnxud.supabase.co").trim();
const SUPABASE_SERVICE_ROLE_KEY = (Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "").replace(/^["']|["']$/g, "").trim();

// Built-in list of disposable/temporary email domains
const FALLBACK_DISPOSABLE_DOMAINS = new Set([
  "tempmail.com", "10minutemail.com", "mailinator.com", "guerrillamail.com",
  "trashmail.com", "yopmail.com", "sharklasers.com", "dispostable.com",
  "getairmail.com", "mohmal.com", "inboxkitten.com", "fakemailgenerator.com",
  "crazymailing.com", "burnermail.io", "temp-mail.org", "throwawaymail.com",
  "tempail.com", "emailondeck.com", "generator.email", "nada.ltd"
]);

// In-memory cache for dynamic disposable domains
let cachedDisposableDomains: Set<string> = new Set(FALLBACK_DISPOSABLE_DOMAINS);
let lastCacheUpdate = 0;
const CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours

async function getDisposableDomains(): Promise<Set<string>> {
  const now = Date.now();
  if (now - lastCacheUpdate < CACHE_TTL_MS && cachedDisposableDomains.size > FALLBACK_DISPOSABLE_DOMAINS.size) {
    return cachedDisposableDomains;
  }

  try {
    const res = await fetch("https://raw.githubusercontent.com/disposable-email-domains/disposable-email-domains/master/disposable_email_blocklist.conf");
    if (res.ok) {
      const text = await res.text();
      const domains = text.split("\n").map(d => d.trim().toLowerCase()).filter(d => d.length > 0);
      cachedDisposableDomains = new Set([...FALLBACK_DISPOSABLE_DOMAINS, ...domains]);
      lastCacheUpdate = now;
    }
  } catch (err) {
    console.warn("Could not refresh dynamic disposable domain blocklist, using fallback:", err);
  }

  return cachedDisposableDomains;
}

// Helper: Import ECDSA Private Key for NIST P-256 signing
async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const cleanPem = pem.replace(/^["']|["']$/g, "").trim();

  if (cleanPem.startsWith("{")) {
    const jwk = JSON.parse(cleanPem);
    return await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "ECDSA", namedCurve: "P-256" },
      false,
      ["sign"]
    );
  }

  const pemHeader = "-----BEGIN PRIVATE KEY-----";
  const pemFooter = "-----END PRIVATE KEY-----";
  const pemContents = cleanPem
    .replace(pemHeader, "")
    .replace(pemFooter, "")
    .replace(/\s+/g, "");

  const binaryDer = Uint8Array.from(atob(pemContents), (c) => c.charCodeAt(0));

  return await crypto.subtle.importKey(
    "pkcs8",
    binaryDer,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"]
  );
}

// Sign a 1-year community license key
async function signCommunityLicense(email: string, appId: string, expiryTimestamp: number): Promise<string> {
  const payload = {
    email: email.toLowerCase().trim(),
    app_id: appId,
    type: "community_pass",
    expires: expiryTimestamp,
    version: 1,
  };

  const encoder = new TextEncoder();
  const payloadJson = JSON.stringify(payload);
  const payloadBytes = encoder.encode(payloadJson);
  const base64Payload = btoa(payloadJson);

  const privateKey = await importPrivateKey(PRIVATE_KEY_PEM);
  const signatureBytes = await crypto.subtle.sign(
    { name: "ECDSA", hash: { name: "SHA-256" } },
    privateKey,
    payloadBytes
  );
  const base64Signature = btoa(String.fromCharCode(...new Uint8Array(signatureBytes)));
  return `${base64Payload}.${base64Signature}`;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method Not Allowed" }), {
      status: 405,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    const body = await req.json();
    const { action = "request", name, email, app_id, device_identifier } = body;

    const cleanEmail = (email || "").toLowerCase().trim();
    const cleanName = (name || "").trim();
    const cleanAppId = (app_id || `CAP-${Date.now()}-${Math.floor(10000 + Math.random() * 90000)}`).trim();

    if (!cleanEmail || !cleanEmail.includes("@")) {
      return new Response(
        JSON.stringify({ error: "A valid email address is required." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 1. Check for disposable email domain
    const emailParts = cleanEmail.split("@");
    const domain = emailParts[emailParts.length - 1].toLowerCase();
    const blocklist = await getDisposableDomains();

    if (blocklist.has(domain)) {
      console.warn(`[COMMUNITY_ACCESS] REJECTED_DISPOSABLE email_domain=${domain}`);
      return new Response(
        JSON.stringify({
          error: "Disposable or temporary email addresses are not permitted. Please apply with your permanent email address."
        }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ACTION: CANCEL COMMUNITY ACCESS
    if (action === "cancel") {
      console.log(`[COMMUNITY_ACCESS] CANCELLED email_domain=${domain}`);
      if (SUPABASE_SERVICE_ROLE_KEY) {
        // Update community request status to cancelled
        await fetch(`${SUPABASE_URL}/rest/v1/community_access_requests?email=eq.${encodeURIComponent(cleanEmail)}`, {
          method: "PATCH",
          headers: {
            "Content-Type": "application/json",
            "apikey": SUPABASE_SERVICE_ROLE_KEY,
            "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
            "Prefer": "return=minimal"
          },
          body: JSON.stringify({
            status: "cancelled",
            updated_at: new Date().toISOString()
          })
        });

        // Clear is_premium in profile
        await fetch(`${SUPABASE_URL}/rest/v1/profiles?email=eq.${encodeURIComponent(cleanEmail)}`, {
          method: "PATCH",
          headers: {
            "Content-Type": "application/json",
            "apikey": SUPABASE_SERVICE_ROLE_KEY,
            "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
            "Prefer": "return=minimal"
          },
          body: JSON.stringify({
            is_premium: false,
            license_key: null,
            updated_at: new Date().toISOString()
          })
        });
      }

      return new Response(
        JSON.stringify({
          success: true,
          status: "cancelled",
          message: "Your Community Access grant has been cancelled. Thank you for your honesty and Amanah."
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ACTION: REQUEST COMMUNITY ACCESS
    if (action === "request") {
      // 2. Query Eligibility RPC via Service Role
      if (SUPABASE_SERVICE_ROLE_KEY) {
        try {
          const rpcRes = await fetch(`${SUPABASE_URL}/rest/v1/rpc/check_community_grant_eligibility`, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              "apikey": SUPABASE_SERVICE_ROLE_KEY,
              "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
            },
            body: JSON.stringify({ target_email: cleanEmail })
          });
          if (rpcRes.ok) {
            const eligibility = await rpcRes.json();
            if (eligibility && eligibility.eligible === false) {
              console.log(`[COMMUNITY_ACCESS] INELIGIBLE email_domain=${domain} status=${eligibility.status}`);
              return new Response(
                JSON.stringify({
                  error: eligibility.reason || "This email is not currently eligible for the Community Access Program.",
                  status: eligibility.status || "ineligible"
                }),
                { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
              );
            }
          }
        } catch (rpcErr) {
          console.warn("Eligibility RPC check warning:", rpcErr);
        }
      }

      const now = Date.now();
      const tempExpiresAt = now + (7 * 24 * 60 * 60 * 1000); // 7 days grace
      const fullExpiresAt = now + (372 * 24 * 60 * 60 * 1000); // 7 days grace + 365 days

      let licenseKey = "";
      let signDurationMs = 0;
      if (PRIVATE_KEY_PEM) {
        const signStart = performance.now();
        licenseKey = await signCommunityLicense(cleanEmail, cleanAppId, fullExpiresAt);
        signDurationMs = Math.round(performance.now() - signStart);
      }

      console.log(`[COMMUNITY_ACCESS] GRANTED action=request email_domain=${domain} app_id=${cleanAppId} sign_latency_ms=${signDurationMs}`);

      // Persist in Supabase Database via Service Role
      if (SUPABASE_SERVICE_ROLE_KEY) {
        // 1. Insert/Upsert into community_access_requests
        await fetch(`${SUPABASE_URL}/rest/v1/community_access_requests`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "apikey": SUPABASE_SERVICE_ROLE_KEY,
            "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
            "Prefer": "resolution=merge-duplicates"
          },
          body: JSON.stringify({
            app_id: cleanAppId,
            user_name: cleanName || "Community Member",
            email: cleanEmail,
            device_identifier: device_identifier || null,
            status: "pending_review",
            requested_at: new Date(now).toISOString(),
            temp_expires_at: new Date(tempExpiresAt).toISOString(),
            verified_expires_at: new Date(fullExpiresAt).toISOString(),
            license_key: licenseKey || null,
            is_flagged: false
          })
        });

        // 2. Ensure profile exists and has active status
        await fetch(`${SUPABASE_URL}/rest/v1/profiles?email=eq.${encodeURIComponent(cleanEmail)}`, {
          method: "PATCH",
          headers: {
            "Content-Type": "application/json",
            "apikey": SUPABASE_SERVICE_ROLE_KEY,
            "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
            "Prefer": "return=minimal"
          },
          body: JSON.stringify({
            is_premium: true,
            role: "community_grant",
            license_key: licenseKey || null,
            updated_at: new Date(now).toISOString()
          })
        });
      }

      return new Response(
        JSON.stringify({
          success: true,
          status: "pending_review",
          app_id: cleanAppId,
          email: cleanEmail,
          temp_expires_at: tempExpiresAt,
          verified_expires_at: fullExpiresAt,
          license_key: licenseKey,
          message: "Immediate 7-day temporary access granted. Al-Haq Community Pass will automatically graduate upon verification."
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    return new Response(
      JSON.stringify({ error: `Unknown action: ${action}` }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: any) {
    console.error("community-access function error:", err.message);
    return new Response(
      JSON.stringify({ error: err.message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
