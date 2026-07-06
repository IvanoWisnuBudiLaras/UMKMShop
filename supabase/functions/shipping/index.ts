type ShippingAction = "search_villages" | "estimate_shipping";

type ShippingRequest = {
  action?: ShippingAction;
  query?: string;
  originVillageCode?: string;
  destinationVillageCode?: string;
  weightGrams?: number;
};

type ApiVillage = Record<string, unknown>;
type ApiShippingOption = Record<string, unknown>;

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

  const payload = await readJson(req);
  if (!payload.action) {
    return jsonResponse({ message: "Action wajib diisi." }, 400);
  }

  try {
    if (payload.action === "search_villages") {
      return await searchVillages(payload.query ?? "");
    }
    if (payload.action === "estimate_shipping") {
      return await estimateShipping(payload);
    }
    return jsonResponse({ message: "Action tidak dikenal." }, 400);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Layanan ongkir belum tersedia.";
    return jsonResponse({ message }, 502);
  }
});

async function searchVillages(query: string): Promise<Response> {
  const cleanedQuery = query.trim();
  if (cleanedQuery.length < 3) {
    return jsonResponse({ message: "Ketik minimal 3 huruf kelurahan." }, 400);
  }

  const baseUrl = env("API_CO_ID_BASE_URL", "https://api.co.id/api").replace(/\/$/, "");
  const path = env("API_CO_ID_VILLAGE_PATH", "/regional/indonesia/villages");
  const url = new URL(`${baseUrl}${path}`);
  url.searchParams.set(env("API_CO_ID_VILLAGE_QUERY_PARAM", "search"), cleanedQuery);

  const body = await providerFetch(url.toString(), { method: "GET" });
  const rows = extractArray(body)
    .map(normalizeVillage)
    .filter((village) => village.villageCode && village.villageName)
    .slice(0, 10);

  return jsonResponse({ villages: rows });
}

async function estimateShipping(payload: ShippingRequest): Promise<Response> {
  const origin = payload.originVillageCode?.trim() ?? "";
  const destination = payload.destinationVillageCode?.trim() ?? "";
  const weight = payload.weightGrams ?? 0;

  if (!/^\d{10}$/.test(origin) || !/^\d{10}$/.test(destination)) {
    return jsonResponse({ message: "Alamat penjual dan pembeli harus punya kode kelurahan valid." }, 400);
  }
  if (!Number.isInteger(weight) || weight <= 0) {
    return jsonResponse({ message: "Berat barang harus lebih dari 0 gram." }, 400);
  }

  const baseUrl = env("API_CO_ID_BASE_URL", "https://api.co.id/api").replace(/\/$/, "");
  const path = env("API_CO_ID_SHIPPING_PATH", "/indonesia-expedition-cost");
  const body = await providerFetch(`${baseUrl}${path}`, {
    method: "POST",
    body: JSON.stringify({
      origin,
      destination,
      weight,
    }),
  });
  const options = extractArray(body)
    .map(normalizeShippingOption)
    .filter((option) => option.cost > 0)
    .sort((left, right) => left.cost - right.cost);

  const estimate = options[0];
  if (!estimate) {
    return jsonResponse({ message: "Provider tidak mengembalikan estimasi ongkir." }, 502);
  }

  return jsonResponse({ estimate });
}

async function providerFetch(url: string, init: RequestInit): Promise<unknown> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), Number(env("API_CO_ID_TIMEOUT_MS", "5000")));
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body) headers.set("Content-Type", "application/json");
  const key = requiredEnv("API_CO_ID_KEY");
  headers.set(env("API_CO_ID_KEY_HEADER", "Authorization"), `${env("API_CO_ID_KEY_PREFIX", "Bearer ")}${key}`);

  try {
    const response = await fetch(url, {
      ...init,
      headers,
      signal: controller.signal,
    });
    const text = await response.text();
    const parsed = text ? JSON.parse(text) : null;
    if (!response.ok) {
      throw new Error(providerMessage(parsed) ?? "Provider ongkir menolak request.");
    }
    return parsed;
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error("Layanan ongkir timeout. Isi ongkir manual untuk lanjut.");
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

function normalizeVillage(row: ApiVillage) {
  return {
    villageCode: stringValue(row, ["village_code", "villageCode", "code", "id"]),
    postalCode: stringValue(row, ["postal_code", "postalCode", "zip_code", "zipCode"]),
    villageName: stringValue(row, ["village_name", "villageName", "name", "subdistrict_name"]),
    districtName: stringValue(row, ["district_name", "districtName", "district"]),
    cityName: stringValue(row, ["city_name", "cityName", "city", "regency_name"]),
    provinceName: stringValue(row, ["province_name", "provinceName", "province"]),
  };
}

function normalizeShippingOption(row: ApiShippingOption) {
  return {
    serviceName: stringValue(row, ["service_name", "serviceName", "service", "courier", "name"]) || "Ongkir",
    cost: numberValue(row, ["cost", "price", "value", "shipping_cost", "shippingCost"]),
    etd: stringValue(row, ["etd", "duration", "estimated_delivery"]),
  };
}

function extractArray(payload: unknown): Record<string, unknown>[] {
  if (Array.isArray(payload)) return payload.filter(isRecord);
  if (!isRecord(payload)) return [];
  for (const key of ["data", "results", "items", "villages", "costs", "options"]) {
    const value = payload[key];
    if (Array.isArray(value)) return value.filter(isRecord);
    if (isRecord(value)) {
      const nested = extractArray(value);
      if (nested.length > 0) return nested;
    }
  }
  return [];
}

function stringValue(row: Record<string, unknown>, keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (typeof value === "string" && value.trim().length > 0) return value.trim();
    if (typeof value === "number") return String(value);
  }
  return "";
}

function numberValue(row: Record<string, unknown>, keys: string[]): number {
  for (const key of keys) {
    const value = row[key];
    if (typeof value === "number") return value;
    if (typeof value === "string") {
      const parsed = Number(value.replace(/[^\d.]/g, ""));
      if (Number.isFinite(parsed)) return parsed;
    }
  }
  return 0;
}

async function readJson(req: Request): Promise<ShippingRequest> {
  try {
    return await req.json();
  } catch {
    return {};
  }
}

function providerMessage(payload: unknown): string | null {
  if (!isRecord(payload)) return null;
  const message = payload.message ?? payload.error;
  return typeof message === "string" ? message : null;
}

function jsonResponse(body: unknown, status = 200): Response {
  return Response.json(body, {
    status,
    headers: corsHeaders,
  });
}

function env(name: string, fallback: string): string {
  return Deno.env.get(name) ?? fallback;
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`${name} belum dikonfigurasi.`);
  return value;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
