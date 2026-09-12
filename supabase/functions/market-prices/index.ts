import { createHandler, PROVIDER_URL, providerSymbol } from "./core.mjs";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const providerKey = Deno.env.get("TWELVE_DATA_API_KEY") ?? "";

function requiredSecretsPresent(): boolean {
  return Boolean(supabaseUrl && anonKey && serviceKey && providerKey);
}

const restHeaders = {
  apikey: serviceKey,
  authorization: `Bearer ${serviceKey}`,
  "content-type": "application/json",
};

function cacheQuery(item: Record<string, string>, freshOnly: boolean): string {
  const query = new URLSearchParams({
    select: "asset_type,symbol,quote_currency,price_unscaled,price_scale,observed_at,fetched_at,expires_at,source",
    asset_type: `eq.${item.asset_type}`,
    symbol: `eq.${item.symbol}`,
    quote_currency: `eq.${item.quote_currency}`,
    limit: "1",
  });
  if (freshOnly) query.set("expires_at", `gt.${new Date().toISOString()}`);
  return `${supabaseUrl}/rest/v1/market_prices?${query}`;
}

const handler = createHandler({
  now: () => new Date(),
  ttlMs: (type: string) => type === "CRYPTO" ? 5 * 60_000 : 15 * 60_000,
  verifyJwt: async (authorization: string) => {
    if (!requiredSecretsPresent()) return false;
    const response = await fetch(`${supabaseUrl}/auth/v1/user`, {
      headers: { apikey: anonKey, authorization },
      signal: AbortSignal.timeout(5_000),
    });
    return response.ok;
  },
  consumeQuota: async (authorization: string) => {
    const response = await fetch(`${supabaseUrl}/rest/v1/rpc/claim_market_price_request`, {
      method: "POST",
      headers: { apikey: anonKey, authorization, "content-type": "application/json" },
      body: "{}",
      signal: AbortSignal.timeout(5_000),
    });
    return response.ok && (await response.json()) === true;
  },
  readCache: async (item: Record<string, string>) => {
    const response = await fetch(cacheQuery(item, true), { headers: restHeaders, signal: AbortSignal.timeout(5_000) });
    if (!response.ok) return null;
    return (await response.json())[0] ?? null;
  },
  readStaleCache: async (item: Record<string, string>) => {
    const response = await fetch(cacheQuery(item, false), { headers: restHeaders, signal: AbortSignal.timeout(5_000) });
    if (!response.ok) return null;
    return (await response.json())[0] ?? null;
  },
  fetchProvider: async (item: Record<string, string>) => {
    const url = new URL(PROVIDER_URL);
    url.searchParams.set("symbol", providerSymbol(item));
    url.searchParams.set("apikey", providerKey);
    const response = await fetch(url, { signal: AbortSignal.timeout(5_000) });
    if (!response.ok) throw new Error("provider_unavailable");
    const quote = await response.json();
    if (quote.status === "error") throw new Error("provider_rejected");
    return {
      price: String(quote.close ?? ""),
      currency: String(quote.currency ?? ""),
      observed_at: new Date(Number(quote.timestamp) * 1000).toISOString(),
    };
  },
  writeCache: async (record: Record<string, unknown>) => {
    const response = await fetch(`${supabaseUrl}/rest/v1/market_prices?on_conflict=asset_type,symbol,quote_currency`, {
      method: "POST",
      headers: { ...restHeaders, prefer: "resolution=merge-duplicates,return=minimal" },
      body: JSON.stringify(record),
      signal: AbortSignal.timeout(5_000),
    });
    if (!response.ok) throw new Error("cache_write_failed");
  },
});

Deno.serve(handler);
