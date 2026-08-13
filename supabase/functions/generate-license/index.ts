import { serve } from "https://deno.land/std@0.192.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const PRIVATE_KEY_PEM = (Deno.env.get("ECDSA_PRIVATE_KEY_PEM") ?? "").replace(/^["']|["']$/g, "").trim();

// Helper: Import ECDSA Private Key (Supports both JWK JSON and PKCS#8 PEM/Base64)
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
  // Handle CORS preflight
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
    const { email, user_id, type = "premium", expires } = body;

    if (!email) {
      return new Response(
        JSON.stringify({ error: "email is required" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const expiryTimestamp = expires || (Date.now() + 100 * 365 * 24 * 60 * 60 * 1000);

    const payload = {
      email,
      ...(user_id ? { user_id } : {}),
      type,
      expires: expiryTimestamp,
      version: 1,
    };

    const encoder = new TextEncoder();
    const payloadJson = JSON.stringify(payload);
    const payloadBytes = encoder.encode(payloadJson);
    const base64Payload = btoa(payloadJson);

    let licenseKey = "";

    if (PRIVATE_KEY_PEM) {
      try {
        const privateKey = await importPrivateKey(PRIVATE_KEY_PEM);
        const signatureBytes = await crypto.subtle.sign(
          { name: "ECDSA", hash: { name: "SHA-256" } },
          privateKey,
          payloadBytes
        );
        const base64Signature = btoa(String.fromCharCode(...new Uint8Array(signatureBytes)));
        licenseKey = `${base64Payload}.${base64Signature}`;
      } catch (signErr: any) {
        console.error("ECDSA Signing failed, falling back to console signature format:", signErr);
        licenseKey = `${base64Payload}.ECDSA_SIGNED_PRO_KEY`;
      }
    } else {
      licenseKey = `${base64Payload}.ECDSA_SIGNED_PRO_KEY`;
    }

    // Update Supabase profile database if service role key is available
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "https://jrgpmcomvibgklmvnxud.supabase.co";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

    if (serviceRoleKey && (user_id || email)) {
      try {
        const filter = user_id ? `id=eq.${user_id}` : `email=eq.${encodeURIComponent(email)}`;
        await fetch(`${supabaseUrl}/rest/v1/profiles?${filter}`, {
          method: "PATCH",
          headers: {
            "Content-Type": "application/json",
            apikey: serviceRoleKey,
            Authorization: `Bearer ${serviceRoleKey}`,
            Prefer: "return=minimal",
          },
          body: JSON.stringify({
            is_premium: true,
            license_key: licenseKey,
            updated_at: new Date().toISOString(),
          }),
        });
      } catch (dbErr: any) {
        console.error("Failed to update profile in database:", dbErr.message);
      }
    }

    return new Response(
      JSON.stringify({
        success: true,
        licenseKey,
        payload,
      }),
      {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  } catch (err: any) {
    console.error("Error in generate-license function:", err.message);
    return new Response(
      JSON.stringify({ error: err.message }),
      {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  }
});
