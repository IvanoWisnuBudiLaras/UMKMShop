import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

type OrderRow = {
  id: string;
  chat_room_id: string;
  buyer_id: string;
  seller_id: string;
  product_id: string;
  item_note: string | null;
  subtotal: number | string;
  shipping_cost: number | string;
  total_amount: number | string;
  status: string;
  xendit_invoice_id: string | null;
  xendit_invoice_url: string | null;
};

type CreateInvoiceRequest = {
  order_id?: string;
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return jsonResponse({ message: "Method tidak didukung." }, 405);
  }

  const authorization = req.headers.get("authorization") ?? "";
  if (!authorization.toLowerCase().startsWith("bearer ")) {
    return jsonResponse({ message: "Session login wajib tersedia." }, 401);
  }

  const payload = await readJson(req);
  const orderId = payload.order_id?.trim();
  if (!orderId) {
    return jsonResponse({ message: "order_id wajib diisi." }, 400);
  }

  try {
    const userClient = createClient(
      requiredEnv("SUPABASE_URL"),
      publishableKey(),
      {
        global: {
          headers: { Authorization: authorization },
        },
        auth: {
          persistSession: false,
          autoRefreshToken: false,
          detectSessionInUrl: false,
        },
      },
    );

    const { data: userData, error: userError } = await userClient.auth.getUser();
    if (userError || !userData.user) {
      return jsonResponse({ message: "Session login tidak valid." }, 401);
    }

    const { data: order, error: orderError } = await userClient
      .from("orders")
      .select(
        [
          "id",
          "chat_room_id",
          "buyer_id",
          "seller_id",
          "product_id",
          "item_note",
          "subtotal",
          "shipping_cost",
          "total_amount",
          "status",
          "xendit_invoice_id",
          "xendit_invoice_url",
        ].join(","),
      )
      .eq("id", orderId)
      .single<OrderRow>();

    if (orderError || !order) {
      return jsonResponse({ message: "Order tidak ditemukan." }, 404);
    }
    if (order.seller_id !== userData.user.id) {
      return jsonResponse({ message: "Hanya seller order ini yang bisa membuat invoice." }, 403);
    }
    if (order.status !== "pending") {
      return jsonResponse({ message: "Order tidak lagi pending." }, 409);
    }
    if (order.xendit_invoice_id && order.xendit_invoice_url) {
      return jsonResponse({
        invoice_url: order.xendit_invoice_url,
        order,
      });
    }

    const externalId = `umkmshop-order-${order.id}`;
    const invoice = await createXenditInvoice({
      externalId,
      amount: toNumber(order.total_amount),
      description: order.item_note?.trim() || `Invoice UMKMShop ${order.id}`,
    });

    const adminClient = createClient(requiredEnv("SUPABASE_URL"), secretKey(), {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
        detectSessionInUrl: false,
      },
    });

    const { data: updatedOrder, error: rpcError } = await adminClient.rpc(
      "ticket_024_store_xendit_invoice",
      {
        p_order_id: order.id,
        p_seller_id: userData.user.id,
        p_xendit_invoice_id: stringField(invoice, "id"),
        p_xendit_invoice_url: stringField(invoice, "invoice_url"),
        p_xendit_external_id: externalId,
        p_xendit_status: stringField(invoice, "status"),
      },
    );

    if (rpcError) {
      console.error("store invoice failed", { order_id: order.id, message: rpcError.message });
      return jsonResponse({ message: "Gagal menyimpan invoice Xendit." }, 502);
    }

    return jsonResponse({
      invoice_url: stringField(invoice, "invoice_url"),
      order: updatedOrder,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Gagal membuat invoice Xendit.";
    console.error("create invoice failed", { order_id: orderId, message });
    return jsonResponse({ message }, 502);
  }
});

async function createXenditInvoice(input: {
  externalId: string;
  amount: number;
  description: string;
}): Promise<Record<string, unknown>> {
  if (!Number.isFinite(input.amount) || input.amount <= 0) {
    throw new Error("Total invoice harus lebih dari 0.");
  }

  const response = await fetch("https://api.xendit.co/v2/invoices", {
    method: "POST",
    headers: {
      "Authorization": `Basic ${btoa(`${requiredEnv("XENDIT_SECRET_KEY")}:`)}`,
      "Content-Type": "application/json",
      "Accept": "application/json",
    },
    body: JSON.stringify({
      external_id: input.externalId,
      amount: input.amount,
      currency: "IDR",
      description: input.description.slice(0, 255),
    }),
  });

  const text = await response.text();
  const body = parseJsonObject(text);
  if (!response.ok) {
    throw new Error(providerMessage(body) ?? "Xendit menolak pembuatan invoice.");
  }

  const invoiceId = stringField(body, "id");
  const invoiceUrl = stringField(body, "invoice_url");
  if (!invoiceId || !invoiceUrl) {
    throw new Error("Response Xendit tidak berisi invoice lengkap.");
  }

  return body;
}

async function readJson(req: Request): Promise<CreateInvoiceRequest> {
  try {
    return await req.json();
  } catch {
    return {};
  }
}

function publishableKey(): string {
  const keys = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS");
  if (keys) return JSON.parse(keys).default;
  return requiredEnv("SUPABASE_ANON_KEY");
}

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

function toNumber(value: number | string): number {
  return typeof value === "number" ? value : Number(value);
}

function stringField(row: Record<string, unknown>, field: string): string {
  const value = row[field];
  return typeof value === "string" ? value : "";
}

function parseJsonObject(text: string): Record<string, unknown> {
  if (!text) return {};
  const parsed = JSON.parse(text);
  return typeof parsed === "object" && parsed !== null && !Array.isArray(parsed) ? parsed : {};
}

function providerMessage(payload: Record<string, unknown>): string | null {
  for (const key of ["message", "error", "error_code"]) {
    const value = payload[key];
    if (typeof value === "string" && value.trim()) return value;
  }
  return null;
}

function jsonResponse(body: unknown, status = 200): Response {
  return Response.json(body, {
    status,
    headers: corsHeaders,
  });
}
