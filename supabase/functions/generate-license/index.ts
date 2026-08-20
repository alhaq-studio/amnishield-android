import { serve } from "https://deno.land/std@0.192.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const PRIVATE_KEY_PEM = (Deno.env.get("ECDSA_PRIVATE_KEY_PEM") ?? "").replace(/^["']|["']$/g, "").trim();
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "https://jrgpmcomvibgklmvnxud.supabase.co";
const SUPABASE_SERVICE_ROLE_KEY = (Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "").replace(/^["']|["']$/g, "").trim();

// Helper: Import ECDSA Private Key
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
    const authHeader = req.headers.get("Authorization") || "";
    const token = authHeader.replace(/^Bearer\s+/i, "").trim();

    if (!token) {
      return new Response(
        JSON.stringify({ error: "Unauthorized: Missing Authorization header" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 1. Authenticate caller: Verify token against Supabase Auth or Service Role
    let isServiceRole = (token === SUPABASE_SERVICE_ROLE_KEY && SUPABASE_SERVICE_ROLE_KEY.length > 0);
    let callerUser: any = null;

    if (!isServiceRole) {
      const userResp = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
        headers: {
          "apikey": SUPABASE_SERVICE_ROLE_KEY || token,
          "Authorization": `Bearer ${token}`
        }
      });
      if (!userResp.ok) {
        return new Response(
          JSON.stringify({ error: "Unauthorized: Invalid authentication token" }),
          { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }
      callerUser = await userResp.json();
    }

    const body = await req.json();
    const { email, user_id, type = "premium", expires } = body;

    const targetEmail = (email || callerUser?.email || "").toLowerCase().trim();
    const targetUserId = user_id || callerUser?.id;

    if (!targetEmail) {
      return new Response(
        JSON.stringify({ error: "email is required" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // If not service role, verify the user is requesting a license for themselves or is admin
    if (!isServiceRole && callerUser) {
      if (callerUser.email?.toLowerCase() !== targetEmail && callerUser.id !== targetUserId) {
        return new Response(
          JSON.stringify({ error: "Forbidden: Cannot generate license for another user account" }),
          { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }
    }

    if (!PRIVATE_KEY_PEM) {
      return new Response(
        JSON.stringify({ error: "Server Configuration Error: ECDSA_PRIVATE_KEY_PEM not set" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const expiryTimestamp = expires || (Date.now() + 100 * 365 * 24 * 60 * 60 * 1000);

    const payload = {
      email: targetEmail,
      ...(targetUserId ? { user_id: targetUserId } : {}),
      type,
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
    const licenseKey = `${base64Payload}.${base64Signature}`;

    // Update Supabase profile database
    if (SUPABASE_SERVICE_ROLE_KEY && (targetUserId || targetEmail)) {
      try {
        const filter = targetUserId ? `id=eq.${targetUserId}` : `email=eq.${encodeURIComponent(targetEmail)}`;
        await fetch(`${SUPABASE_URL}/rest/v1/profiles?${filter}`, {
          method: "PATCH",
          headers: {
            "Content-Type": "application/json",
            "apikey": SUPABASE_SERVICE_ROLE_KEY,
            "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
            "Prefer": "return=minimal"
          },
          body: JSON.stringify({
            is_premium: true,
            license_key: licenseKey,
            updated_at: new Date().toISOString()
          })
        });
      } catch (dbErr: any) {
        console.error("Database profile update error:", dbErr.message);
      }
    }

    return new Response(
      JSON.stringify({
        success: true,
        license_key: licenseKey,
        email: targetEmail,
        expires: expiryTimestamp,
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: any) {
    console.error("generate-license error:", err.message);
    return new Response(
      JSON.stringify({ error: err.message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
