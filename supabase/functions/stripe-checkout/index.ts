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

    let lineItem: any;

    if (priceId && priceId.startsWith("price_")) {
      lineItem = { price: priceId, quantity: 1 };
    } else {
      const isAnnual = plan === "annual" || priceId === "annual";
      lineItem = {
        price_data: {
          currency: "usd",
          product_data: {
            name: isAnnual ? "AmniShield Pro (Annual Pass)" : "AmniShield Pro (Monthly Subscription)",
            description: "Unified Cross-Platform Digital Protection Suite (Android, Windows PC, Web Extensions)"
          },
          unit_amount: isAnnual ? 3999 : 499,
          recurring: {
            interval: isAnnual ? "year" : "month"
          }
        },
        quantity: 1
      };
    }

    // Create Checkout Session with fallback handling
    let session;
    try {
      session = await stripe.checkout.sessions.create({
        payment_method_types: ["card"],
        line_items: [lineItem],
        mode: mode || "subscription",
        customer_email: customerEmail || undefined,
        success_url: successUrl || "https://app.amnishield.com/?checkout=success",
        cancel_url: cancelUrl || "https://amnishield.com/#pricing",
      });
    } catch (createErr: any) {
      if (createErr.message.includes("No such price")) {
        const isAnnual = plan === "annual" || priceId === "annual" || String(priceId).includes("annual");
        session = await stripe.checkout.sessions.create({
          payment_method_types: ["card"],
          line_items: [{
            price_data: {
              currency: "usd",
              product_data: {
                name: isAnnual ? "AmniShield Pro (Annual Pass)" : "AmniShield Pro (Monthly Subscription)",
                description: "Unified Cross-Platform Digital Protection Suite"
              },
              unit_amount: isAnnual ? 3999 : 499,
              recurring: { interval: isAnnual ? "year" : "month" }
            },
            quantity: 1
          }],
          mode: mode || "subscription",
          customer_email: customerEmail || undefined,
          success_url: successUrl || "https://app.amnishield.com/?checkout=success",
          cancel_url: cancelUrl || "https://amnishield.com/#pricing",
        });
      } else {
        throw createErr;
      }
    }

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
