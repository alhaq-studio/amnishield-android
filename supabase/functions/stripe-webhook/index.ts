import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import Stripe from "npm:stripe@^14";

const STRIPE_SECRET_KEY = (Deno.env.get("STRIPE_SECRET_KEY") ?? "").replace(/^["']|["']$/g, "").trim();
const STRIPE_WEBHOOK_SECRET = (Deno.env.get("STRIPE_WEBHOOK_SECRET") ?? "").replace(/^["']|["']$/g, "").trim();
const PRIVATE_KEY_PEM = (Deno.env.get("ECDSA_PRIVATE_KEY_PEM") ?? "").replace(/^["']|["']$/g, "").trim();
const RESEND_API_KEY = (Deno.env.get("RESEND_API_KEY") ?? "").replace(/^["']|["']$/g, "").trim();

const stripe = new Stripe(STRIPE_SECRET_KEY, {
  apiVersion: "2023-10-16",
});

// Helper: Convert Hex String to Uint8Array
function hexToBytes(hex: string): Uint8Array {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(hex.substring(i * 2, i * 2 + 2), 16);
  }
  return bytes;
}

// Helper: Import ECDSA Private Key (Supports both JWK JSON and PKCS#8 PEM/Base64)
async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const cleanPem = pem.replace(/^["']|["']$/g, "").trim();

  // If it's a JWK JSON string
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

  // Fallback to PKCS#8 PEM
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

// Main Webhook Handler for Stripe Events
Deno.serve(async (req) => {
  const urlObj = new URL(req.url);

  // Health check / Diagnostic Mode (GET request)
  if (req.method === "GET" || urlObj.searchParams.get("debug") === "true") {
    const diagnostics: Record<string, any> = {
      service: "AmnShield Stripe Webhook",
      timestamp: new Date().toISOString(),
      STRIPE_SECRET_KEY_set: STRIPE_SECRET_KEY.length > 0,
      STRIPE_WEBHOOK_SECRET_set: STRIPE_WEBHOOK_SECRET.length > 0,
      RESEND_API_KEY_set: RESEND_API_KEY.length > 0,
      PRIVATE_KEY_PEM_set: PRIVATE_KEY_PEM.length > 0,
    };

    return new Response(JSON.stringify(diagnostics, null, 2), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }

  if (req.method !== "POST") {
    return new Response("Method Not Allowed", { status: 405 });
  }

  try {
    const signature = req.headers.get("stripe-signature");
    if (!signature) {
      return new Response("Missing stripe-signature header", { status: 401 });
    }

    const rawBody = await req.text();
    let event: Stripe.Event;

    try {
      event = await stripe.webhooks.constructEventAsync(
        rawBody,
        signature,
        STRIPE_WEBHOOK_SECRET
      );
    } catch (err: any) {
      console.error(`Webhook signature verification failed: ${err.message}`);
      return new Response(`Webhook Error: ${err.message}`, { status: 400 });
    }

    console.log(`Received Stripe event: ${event.type} [${event.id}]`);

    // Handle completed checkout sessions & subscription updates
    if (
      event.type === "checkout.session.completed" ||
      event.type === "customer.subscription.created" ||
      event.type === "customer.subscription.updated"
    ) {
      let email = "";
      let mode = "subscription";

      if (event.type === "checkout.session.completed") {
        const session = event.data.object as Stripe.Checkout.Session;
        email = session.customer_details?.email || session.customer_email || "";
        mode = session.mode || "payment";
      } else {
        const subscription = event.data.object as Stripe.Subscription;
        const customer = await stripe.customers.retrieve(subscription.customer as string);
        if (!customer.deleted) {
          email = customer.email || "";
        }
      }

      if (email) {
        // Expiry calculation: 100 years for lifetime, 1 year for annual/subscription
        const isLifetime = mode === "payment";
        const expiryTimestamp = isLifetime
          ? Date.now() + 100 * 365 * 24 * 60 * 60 * 1000
          : Date.now() + 365 * 24 * 60 * 60 * 1000;

        // Construct signed JWT ECDSA payload
        const payload = {
          email: email,
          type: "premium",
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

        // 1. Update or upsert user profile in Supabase Database
        const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "https://jrgpmcomvibgklmvnxud.supabase.co";
        const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
        
        if (serviceRoleKey) {
          try {
            await fetch(`${supabaseUrl}/rest/v1/profiles?email=eq.${encodeURIComponent(email)}`, {
              method: "PATCH",
              headers: {
                "Content-Type": "application/json",
                "apikey": serviceRoleKey,
                "Authorization": `Bearer ${serviceRoleKey}`,
                "Prefer": "return=minimal"
              },
              body: JSON.stringify({
                is_premium: true,
                license_key: licenseKey,
                updated_at: new Date().toISOString()
              })
            });
          } catch (dbErr: any) {
            console.error(`Failed to update Supabase profile for ${email}:`, dbErr.message);
          }
        }

        // 2. Deliver license key via Resend Email API
        if (RESEND_API_KEY) {
          await fetch("https://api.resend.com/emails", {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${RESEND_API_KEY}`,
            },
            body: JSON.stringify({
              from: "AmnShield Team <noreply@mail.alhaq.uk>",
              to: [email],
              subject: "Your AmnShield Premium License Key",
              html: `
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                  <h2 style="color: #0f172a;">Thank you for purchasing AmnShield Premium!</h2>
                  <p>Your subscription is now active! Here is your offline license key. Copy and paste this directly into the app's Profile settings or log into your account:</p>
                  <div style="background: #f1f5f9; padding: 15px; border-radius: 8px; font-family: monospace; word-break: break-all; margin: 20px 0;">
                    ${licenseKey}
                  </div>
                  <p>Valid until: <b>${new Date(expiryTimestamp).toLocaleDateString()}</b></p>
                  <p>Log in to your account at <a href="https://app.amnshield.com" style="color: #3b82f6;">app.amnshield.com</a> to manage your protected devices.</p>
                  <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                  <p style="color: #64748b; font-size: 12px;">Al-Haq Studio &bull; <a href="https://alhaq.uk" style="color: #3b82f6;">alhaq.uk</a></p>
                </div>
              `,
            }),
          });
        }

        return new Response(
          JSON.stringify({
            success: true,
            event: event.type,
            licenseKeyCreated: true,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      }
    }

    return new Response(JSON.stringify({ received: true, event: event.type }), { status: 200 });
  } catch (err: any) {
    console.error(`Error processing Stripe webhook: ${err.message}`);
    return new Response(JSON.stringify({ error: err.message }), { status: 500 });
  }
});
