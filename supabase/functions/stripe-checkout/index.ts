import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import Stripe from "npm:stripe@^14";

const STRIPE_SECRET_KEY = (Deno.env.get("STRIPE_SECRET_KEY") ?? "").replace(/^["']|["']$/g, "").trim();

const stripe = new Stripe(STRIPE_SECRET_KEY, {
  apiVersion: "2023-10-16",
});

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const { priceId, plan, customerEmail, successUrl, cancelUrl, mode } = await req.json();

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
    console.error(`Stripe Checkout session creation error: ${err.message}`);
    return new Response(
      JSON.stringify({ error: err.message }),
      {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  }
});
