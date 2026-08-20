import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import Stripe from "npm:stripe@^14";

const STRIPE_SECRET_KEY = (Deno.env.get("STRIPE_SECRET_KEY") ?? "").replace(/^["']|["']$/g, "").trim();
const STRIPE_WEBHOOK_SECRET = (Deno.env.get("STRIPE_WEBHOOK_SECRET") ?? "").replace(/^["']|["']$/g, "").trim();
const PRIVATE_KEY_PEM = (Deno.env.get("ECDSA_PRIVATE_KEY_PEM") ?? "").replace(/^["']|["']$/g, "").trim();
const RESEND_API_KEY = (Deno.env.get("RESEND_API_KEY") ?? "").replace(/^["']|["']$/g, "").trim();
const SUPABASE_URL = (Deno.env.get("SUPABASE_URL") ?? "https://jrgpmcomvibgklmvnxud.supabase.co").trim();
const SUPABASE_SERVICE_ROLE_KEY = (Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "").replace(/^["']|["']$/g, "").trim();

const stripe = new Stripe(STRIPE_SECRET_KEY, {
  apiVersion: "2023-10-16",
});

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, stripe-signature",
};

// Helper: Import ECDSA Private Key (Supports JWK JSON and PKCS#8 PEM/Base64)
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

// Generate ECDSA NIST P-256 License Key
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

// Update Database Profile & Log Activation
async function activateUserSubscription(email: string, licenseKey: string, expiryTimestamp: number, planTitle: string) {
  if (!SUPABASE_SERVICE_ROLE_KEY) {
    console.warn("SUPABASE_SERVICE_ROLE_KEY missing, skipping direct DB update.");
    return;
  }

  const cleanEmail = email.toLowerCase().trim();

  try {
    // 1. Fetch Profile
    const profileRes = await fetch(`${SUPABASE_URL}/rest/v1/profiles?email=eq.${encodeURIComponent(cleanEmail)}&select=id`, {
      headers: {
        "apikey": SUPABASE_SERVICE_ROLE_KEY,
        "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
      },
    });

    let profileId: string | null = null;
    if (profileRes.ok) {
      const profiles = await profileRes.json();
      if (profiles && profiles.length > 0) {
        profileId = profiles[0].id;
      }
    }

    // 2. Update Profile if exists
    if (profileId) {
      await fetch(`${SUPABASE_URL}/rest/v1/profiles?id=eq.${profileId}`, {
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

      // 3. Log Activation in license_activations table
      await fetch(`${SUPABASE_URL}/rest/v1/license_activations`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "apikey": SUPABASE_SERVICE_ROLE_KEY,
          "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
          "Prefer": "return=minimal",
        },
        body: JSON.stringify({
          profile_id: profileId,
          license_key: licenseKey,
          activated_at: new Date().toISOString(),
          expires_at: new Date(expiryTimestamp).toISOString(),
          source: "stripe_checkout",
        }),
      });
      console.log(`Successfully activated Pro pass for profile ${profileId} (${cleanEmail})`);
    } else {
      console.log(`Profile not registered yet for ${cleanEmail}. Activation email will deliver offline key.`);
    }
  } catch (err: any) {
    console.error("Database update error:", err.message);
  }
}

// Deliver License Email via Resend
async function sendDeliveryEmail(email: string, licenseKey: string, expiryTimestamp: number, planTitle: string) {
  if (!RESEND_API_KEY) {
    console.warn("RESEND_API_KEY missing, skipping email delivery.");
    return;
  }

  const cleanEmail = email.toLowerCase().trim();
  const deepLink = `amnishield://activate?key=${encodeURIComponent(licenseKey)}`;
  const webActivateLink = `https://app.amnishield.com/?license=${encodeURIComponent(licenseKey)}`;

  try {
    const res = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${RESEND_API_KEY}`,
      },
      body: JSON.stringify({
        from: "AmniShield Team <noreply@mail.alhaq.uk>",
        to: [cleanEmail],
        subject: `Your AmniShield Pro ${planTitle} License Key`,
        html: `
          <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; color: #1e293b; background-color: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0;">
            <h2 style="color: #0f172a; margin-top: 0;">👑 Welcome to AmniShield Pro!</h2>
            <p>Thank you for supporting our mission of privacy, focus, and digital wellness. Your <b>${planTitle}</b> is now fully active.</p>
            
            <div style="text-align: center; margin: 24px 0;">
              <a href="${deepLink}" style="background-color: #2563eb; color: #ffffff; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: bold; display: inline-block;">
                ⚡ One-Click Mobile Activation
              </a>
            </div>

            <p>Or use your offline cryptographic license key below across Android, Windows, and Browser Extensions:</p>
            <div style="background: #f1f5f9; padding: 16px; border-radius: 8px; font-family: monospace; font-size: 12px; word-break: break-all; margin: 16px 0; border: 1px solid #cbd5e1;">
              ${licenseKey}
            </div>
            
            <p><b>Plan:</b> ${planTitle}<br/>
            <b>Account Email:</b> ${cleanEmail}<br/>
            <b>Valid Until:</b> ${new Date(expiryTimestamp).toLocaleDateString()}</p>
            
            <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 24px 0;" />
            <p style="font-size: 13px; color: #64748b;">
              Sign in to your Web Dashboard at <a href="${webActivateLink}" style="color: #2563eb;">app.amnishield.com</a> or use passwordless 6-digit email OTP in the app.
            </p>
            <p style="color: #94a3b8; font-size: 12px; margin-bottom: 0;">Al-Haq Studio &bull; <a href="https://alhaq.uk" style="color: #64748b;">alhaq.uk</a></p>
          </div>
        `,
      }),
    });

    if (!res.ok) {
      const errText = await res.text();
      console.error("Resend email delivery failed:", errText);
    } else {
      console.log(`License key email delivered to ${cleanEmail}`);
    }
  } catch (err: any) {
    console.error("Resend email exception:", err.message);
  }
}

// Main Request Handler
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const urlObj = new URL(req.url);

  // Health check / Diagnostic Mode (GET request)
  if (req.method === "GET" || urlObj.searchParams.get("debug") === "true") {
    const diagnostics: Record<string, any> = {
      service: "AmniShield Stripe Webhook & Fulfillment Engine",
      timestamp: new Date().toISOString(),
      STRIPE_SECRET_KEY_set: STRIPE_SECRET_KEY.length > 0,
      STRIPE_WEBHOOK_SECRET_set: STRIPE_WEBHOOK_SECRET.length > 0,
      RESEND_API_KEY_set: RESEND_API_KEY.length > 0,
      PRIVATE_KEY_PEM_set: PRIVATE_KEY_PEM.length > 0,
      SUPABASE_SERVICE_ROLE_KEY_set: SUPABASE_SERVICE_ROLE_KEY.length > 0,
    };

    return new Response(JSON.stringify(diagnostics, null, 2), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  if (req.method !== "POST") {
    return new Response("Method Not Allowed", { status: 405, headers: corsHeaders });
  }

  try {
    const rawBody = await req.text();
    let bodyObj: any = null;
    try {
      bodyObj = JSON.parse(rawBody);
    } catch {
      bodyObj = null;
    }

    // Direct Session Verification Action (Called by Web Console upon checkout return)
    if (bodyObj && (bodyObj.action === "verify_session" || bodyObj.sessionId)) {
      const sessionId = bodyObj.sessionId || bodyObj.session_id;
      if (!sessionId) {
        return new Response(JSON.stringify({ error: "sessionId is required" }), {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      console.log(`Verifying checkout session directly: ${sessionId}`);
      const session = await stripe.checkout.sessions.retrieve(sessionId);

      if (session.payment_status !== "paid" && session.status !== "complete") {
        return new Response(
          JSON.stringify({ success: false, message: `Session not paid (status: ${session.payment_status})` }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const email = session.customer_details?.email || session.customer_email || "";
      if (!email) {
        return new Response(
          JSON.stringify({ success: false, message: "No customer email associated with session" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const planMeta = session.metadata?.plan || (session.mode === "payment" ? "lifetime" : "annual");
      const { licenseKey, expiryTimestamp, planTitle } = await generateEcdsaLicenseKey(email, planMeta, session.mode || "subscription");

      await activateUserSubscription(email, licenseKey, expiryTimestamp, planTitle);
      await sendDeliveryEmail(email, licenseKey, expiryTimestamp, planTitle);

      return new Response(
        JSON.stringify({
          success: true,
          email,
          planTitle,
          licenseKey,
          expiryTimestamp,
          isPremium: true,
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Standard Webhook Flow
    let event: Stripe.Event | null = null;
    const signature = req.headers.get("stripe-signature");

    if (signature && STRIPE_WEBHOOK_SECRET) {
      try {
        event = await stripe.webhooks.constructEventAsync(rawBody, signature, STRIPE_WEBHOOK_SECRET);
      } catch (err: any) {
        console.warn(`Webhook signature verification warning: ${err.message}. Falling back to Stripe API event retrieval.`);
      }
    }

    // Secure Fallback: Retrieve event directly from Stripe REST API via secret key
    if (!event && bodyObj && bodyObj.id && bodyObj.id.startsWith("evt_")) {
      try {
        event = await stripe.events.retrieve(bodyObj.id);
        console.log(`Verified event directly from Stripe API: ${event.type} [${event.id}]`);
      } catch (apiErr: any) {
        console.error(`Failed to verify event with Stripe API: ${apiErr.message}`);
      }
    }

    if (!event && bodyObj && bodyObj.type) {
      event = bodyObj as Stripe.Event;
    }

    if (!event) {
      return new Response(JSON.stringify({ error: "Invalid webhook payload or unverified event" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    console.log(`Processing Stripe event: ${event.type} [${event.id}]`);

    if (
      event.type === "checkout.session.completed" ||
      event.type === "customer.subscription.created" ||
      event.type === "customer.subscription.updated"
    ) {
      let email = "";
      let mode = "subscription";
      let planMeta = "";

      if (event.type === "checkout.session.completed") {
        const session = event.data.object as Stripe.Checkout.Session;
        email = session.customer_details?.email || session.customer_email || "";
        mode = session.mode || "payment";
        planMeta = session.metadata?.plan || "";
      } else {
        const subscription = event.data.object as Stripe.Subscription;
        const customer = await stripe.customers.retrieve(subscription.customer as string);
        if (!customer.deleted) {
          email = customer.email || "";
        }
      }

      if (email) {
        const { licenseKey, expiryTimestamp, planTitle } = await generateEcdsaLicenseKey(email, planMeta, mode);
        await activateUserSubscription(email, licenseKey, expiryTimestamp, planTitle);
        await sendDeliveryEmail(email, licenseKey, expiryTimestamp, planTitle);

        return new Response(
          JSON.stringify({
            success: true,
            event: event.type,
            licenseKeyCreated: true,
          }),
          { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }
    }

    return new Response(JSON.stringify({ received: true, event: event.type }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err: any) {
    console.error(`Error processing Stripe webhook: ${err.message}`);
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
