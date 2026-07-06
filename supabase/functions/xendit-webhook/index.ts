import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "x-callback-token, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return jsonResponse({ message: "Method tidak didukung." }, 405);
  }

  const expectedCallbackToken = Deno.env.get("XENDIT_CALLBACK_TOKEN");
  if (!expectedCallbackToken) {
    return jsonResponse({ message: "Xendit callback token belum dikonfigurasi." }, 503);
  }

  const callbackToken = req.headers.get("x-callback-token") ?? "";
  if (!constantTimeEquals(callbackToken, expectedCallbackToken)) {
    return jsonResponse({ message: "Callback token tidak valid." }, 401);
  }

  const rawBody = await req.text();
  const payload = parseJsonObject(rawBody);
  const invoiceId = stringField(payload, "id");
  const externalId = stringField(payload, "external_id");
  const status = stringField(payload, "status");
  const currency = stringField(payload, "currency") || "IDR";
  const amount = numberField(payload, "amount");

  if (!invoiceId && !externalId) {
    return jsonResponse({ message: "Payload webhook tidak berisi invoice id." }, 400);
  }
  if (!status) {
    return jsonResponse({ message: "Payload webhook tidak berisi status." }, 400);
  }
  if (!Number.isFinite(amount) || amount <= 0) {
    return jsonResponse({ message: "Payload webhook tidak berisi amount valid." }, 400);
  }

  const paidAt = stringField(payload, "paid_at") || stringField(payload, "paid_at_iso");
  const expiredAt = stringField(payload, "expiry_date") || stringField(payload, "expired_at");

  const adminClient = createClient(requiredEnv("SUPABASE_URL"), secretKey(), {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
      detectSessionInUrl: false,
    },
  });

  const { data, error } = await adminClient.rpc("ticket_024_apply_xendit_webhook", {
    p_xendit_invoice_id: invoiceId || null,
    p_xendit_external_id: externalId || null,
    p_amount: amount,
    p_currency: currency,
    p_xendit_status: status,
    p_paid_at: paidAt || null,
    p_expired_at: expiredAt || null,
  });

  if (error) {
    console.error("xendit webhook rejected", {
      invoice_id: invoiceId,
      external_id: externalId,
      status,
      message: error.message,
    });
    return jsonResponse({ message: "Webhook tidak valid untuk order ini." }, 400);
  }

  return jsonResponse({ ok: true, result: data });
});

function secretKey(): string {
  const keys = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (keys) return JSON.parse(keys).default;
  return requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`${name} belum dikonfigurasi.`);
  return value;
}

function parseJsonObject(text: string): Record<string, unknown> {
  if (!text) return {};
  const parsed = JSON.parse(text);
  return typeof parsed === "object" && parsed !== null && !Array.isArray(parsed) ? parsed : {};
}

function stringField(row: Record<string, unknown>, field: string): string {
  const value = row[field];
  return typeof value === "string" ? value : "";
}

function numberField(row: Record<string, unknown>, field: string): number {
  const value = row[field];
  if (typeof value === "number") return value;
  if (typeof value === "string") return Number(value);
  return Number.NaN;
}

function constantTimeEquals(left: string, right: string): boolean {
  const encoder = new TextEncoder();
  const leftBytes = encoder.encode(left);
  const rightBytes = encoder.encode(right);
  const length = Math.max(leftBytes.length, rightBytes.length);
  let diff = leftBytes.length ^ rightBytes.length;
  for (let index = 0; index < length; index += 1) {
    diff |= (leftBytes[index] ?? 0) ^ (rightBytes[index] ?? 0);
  }
  return diff === 0;
}

function jsonResponse(body: unknown, status = 200): Response {
  return Response.json(body, {
    status,
    headers: corsHeaders,
  });
}
