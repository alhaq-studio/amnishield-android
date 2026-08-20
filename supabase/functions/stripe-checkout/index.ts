import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import Stripe from "npm:stripe@^14";

const STRIPE_SECRET_KEY = (Deno.env.get("STRIPE_SECRET_KEY") ?? "").replace(/^["']|["']$/g, "").trim();
const PRIVATE_KEY_PEM = (Deno.env.get("ECDSA_PRIVATE_KEY_PEM") ?? "").replace(/^["']|["']$/g, "").trim();
const RESEND_API_KEY = (Deno.env.get("RESEND_API_KEY") ?? "").replace(/^["']|["']$/g, "").trim();
const SUPABASE_URL = (Deno.env.get("SUPABASE_URL") ?? "https://jrgpmcomvibgklmvnxud.supabase.co").trim();
const SUPABASE_SERVICE_ROLE_KEY = (Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "").replace(/^["']|["']$/g, "").trim();

const stripe = new Stripe(STRIPE_SECRET_KEY, {
  apiVersion: "2023-10-16",
});

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

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

// Generate ECDSA License Key
async function generateEcdsaLicenseKey(email: string, planMeta: string, mode: string): Promise<{ licenseKey: string; expiryTimestamp: number; planTitle: string }> {
  const isLifetime = mode === "payment" || planMeta === "lifetime";
  const isAnnual = planMeta === "annual" || (!isLifetime && mode === "subscription");

  let expiryTimestamp: number;
  let planTitle: string;
  if (isLifetime) {
    expiryTimestamp = Date.now() + 100 * 365 * 24 * 60 * 60 * 1000;
    planTitle = "Lifetime Pass (Never Expires)";
  } else if (planMeta === "monthly") {
    expiryTimestamp = Date.now() + 35 * 24 * 60 * 60 * 1000;
    planTitle = "Monthly Subscription";
  } else {
    expiryTimestamp = Date.now() + 366 * 24 * 60 * 60 * 1000;
    planTitle = "Annual Pass";
  }

  const payload = {
    email: email.toLowerCase().trim(),
    type: isLifetime ? "lifetime" : "premium",
    expires: expiryTimestamp,
    version: 1,
  };

  const encoder = new TextEncoder();
  const payloadJson = JSON.stringify(payload);
  const payloadBytes = encoder.encode(payloadJson);

  const privateKey = await importPrivateKey(PRIVATE_KEY_PEM);
  const signatureBytes = await crypto.subtle.sign(
    { name: "ECDSA", hash: { name: "SHA-256" } },
    privateKey,
    payloadBytes
  );

  const base64Payload = btoa(payloadJson);
  const base64Signature = btoa(String.fromCharCode(...new Uint8Array(signatureBytes)));
  const licenseKey = `${base64Payload}.${base64Signature}`;

  return { licenseKey, expiryTimestamp, planTitle };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const body = await req.json();

    // 1. Session Verification Action (Upon user redirect back from checkout)
    if (body.action === "verify_session" || (body.sessionId && !body.priceId && !body.plan)) {
      const sessionId = body.sessionId || body.session_id;
      if (!sessionId) {
        return new Response(JSON.stringify({ error: "sessionId is required" }), {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      console.log(`Verifying Stripe checkout session: ${sessionId}`);
      const session = await stripe.checkout.sessions.retrieve(sessionId);

      if (session.payment_status !== "paid" && session.status !== "complete") {
        return new Response(
          JSON.stringify({ success: false, message: `Session status is ${session.payment_status}` }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const email = session.customer_details?.email || session.customer_email || "";
      if (!email) {
        return new Response(
          JSON.stringify({ success: false, message: "No email associated with session" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const planMeta = session.metadata?.plan || (session.mode === "payment" ? "lifetime" : "annual");
      const { licenseKey, expiryTimestamp, planTitle } = await generateEcdsaLicenseKey(email, planMeta, session.mode || "subscription");

      // Update Database
      if (SUPABASE_SERVICE_ROLE_KEY) {
        try {
          const profileRes = await fetch(`${SUPABASE_URL}/rest/v1/profiles?email=eq.${encodeURIComponent(email.toLowerCase().trim())}&select=id`, {
            headers: {
              "apikey": SUPABASE_SERVICE_ROLE_KEY,
              "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
            },
          });

          if (profileRes.ok) {
            const profiles = await profileRes.json();
            if (profiles && profiles.length > 0) {
              await fetch(`${SUPABASE_URL}/rest/v1/profiles?id=eq.${profiles[0].id}`, {
                method: "PATCH",
                headers: {
                  "Content-Type": "application/json",
                  "apikey": SUPABASE_SERVICE_ROLE_KEY,
                  "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
                  "Prefer": "return=minimal",
                },
                body: JSON.stringify({
                  is_premium: true,
                  license_key: licenseKey,
                  updated_at: new Date().toISOString(),
                }),
              });
            }
          }
        } catch (dbErr: any) {
          console.error("Database update error:", dbErr.message);
        }
      }

      return new Response(
        JSON.stringify({
          success: true,
          isPremium: true,
          licenseKey,
          email,
          planTitle,
          expiryTimestamp,
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 2. Checkout Session Creation Action
    const { priceId, plan, customerEmail, successUrl, cancelUrl, mode } = body;

    const isLifetime = plan === "lifetime" || priceId === "lifetime" || mode === "payment";
    const isAnnual = plan === "annual" || priceId === "annual" || String(priceId).includes("annual");

    let lineItem: any;

    if (priceId && priceId.startsWith("price_")) {
      lineItem = { price: priceId, quantity: 1 };
    } else if (isLifetime) {
      lineItem = {
        price_data: {
          currency: "usd",
          product_data: {
            name: "AmniShield Pro (Lifetime Pass)",
            description: "Unified Cross-Platform Digital Protection Suite — One-Time Purchase, Lifetime ECDSA Key",
          },
          unit_amount: 8999, // $89.99 one-time
        },
        quantity: 1,
      };
    } else {
      lineItem = {
        price_data: {
          currency: "usd",
          product_data: {
            name: isAnnual ? "AmniShield Pro (Annual Pass)" : "AmniShield Pro (Monthly Subscription)",
            description: "Unified Cross-Platform Digital Protection Suite (Android, Windows PC, Web Extensions)",
          },
          unit_amount: isAnnual ? 3999 : 499,
          recurring: {
            interval: isAnnual ? "year" : "month",
          },
        },
        quantity: 1,
      };
    }

    const sessionMode = isLifetime ? "payment" : (mode || "subscription");

    const session = await stripe.checkout.sessions.create({
      payment_method_types: ["card"],
      line_items: [lineItem],
      mode: sessionMode,
      customer_email: customerEmail || undefined,
      metadata: {
        plan: isLifetime ? "lifetime" : (isAnnual ? "annual" : "monthly"),
      },
      success_url: successUrl || "https://app.amnishield.com/?checkout=success&session_id={CHECKOUT_SESSION_ID}",
      cancel_url: cancelUrl || "https://amnishield.com/#pricing",
    });

    return new Response(
      JSON.stringify({
        sessionId: session.id,
        url: session.url,
      }),
      {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  } catch (err: any) {
    console.error(`Stripe Checkout error: ${err.message}`);
    return new Response(
      JSON.stringify({ error: err.message }),
      {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  }
});
