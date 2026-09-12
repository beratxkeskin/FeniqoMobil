export const MAX_ITEMS = 20;
export const MAX_BODY_BYTES = 16 * 1024;
export const PROVIDER_URL = "https://api.twelvedata.com/quote";
const TYPES = new Set(["CRYPTO", "STOCKS", "PRECIOUS_METALS"]);
const CURRENCIES = new Set(["TRY", "USD", "EUR"]);
const SYMBOL = /^[A-Z0-9][A-Z0-9._-]{0,31}$/;

export function normalizeItem(value) {
  if (!value || typeof value !== "object") return { error: "INVALID_REQUEST" };
  const assetType = String(value.asset_type ?? "");
  const symbol = String(value.symbol ?? "").trim().toUpperCase();
  const quoteCurrency = String(value.quote_currency ?? "");
  if (!TYPES.has(assetType)) return { error: "UNSUPPORTED_ASSET_TYPE" };
  if (!SYMBOL.test(symbol)) return { error: "INVALID_SYMBOL" };
  if (!CURRENCIES.has(quoteCurrency)) return { error: "UNSUPPORTED_CURRENCY" };
  return { item: { asset_type: assetType, symbol, quote_currency: quoteCurrency } };
}

export function parseScaledPrice(raw) {
  if (typeof raw !== "string" || !/^\d+(?:\.\d{1,12})?$/.test(raw)) return null;
  const [whole, fraction = ""] = raw.split(".");
  const digits = `${whole}${fraction}`.replace(/^0+(?=\d)/, "");
  try {
    const unscaled = BigInt(digits);
    if (unscaled <= 0n || unscaled > 9223372036854775807n) return null;
    return { price_unscaled: unscaled.toString(), price_scale: fraction.length };
  } catch {
    return null;
  }
}

export function providerSymbol(item) {
  return item.asset_type === "STOCKS" ? item.symbol : `${item.symbol}/${item.quote_currency}`;
}

export function createHandler(deps) {
  return async function handle(request) {
    if (request.method !== "POST") return json(405, { error: "METHOD_NOT_ALLOWED" });
    const contentLength = Number(request.headers.get("content-length") ?? "0");
    if (contentLength > MAX_BODY_BYTES) return json(413, { error: "REQUEST_TOO_LARGE" });
    const authorization = request.headers.get("authorization") ?? "";
    if (!/^Bearer\s+\S+$/.test(authorization) || !(await deps.verifyJwt(authorization))) {
      return json(401, { error: "AUTH_REQUIRED" });
    }
    if (!(await deps.consumeQuota(authorization))) return json(429, { error: "RATE_LIMITED" });

    let body;
    try { body = await request.json(); } catch { return json(400, { error: "INVALID_REQUEST" }); }
    if (!Array.isArray(body?.items) || body.items.length < 1 || body.items.length > MAX_ITEMS) {
      return json(400, { error: "INVALID_REQUEST" });
    }
    const normalized = body.items.map(normalizeItem);
    if (normalized.some((it) => it.error)) {
      return json(400, { error: normalized.find((it) => it.error).error });
    }

    const now = deps.now();
    const results = [];
    for (const { item } of normalized) {
      const cached = await deps.readCache(item, now);
      if (cached) {
        results.push({ ...cached, availability: "FRESH" });
        continue;
      }
      try {
        const quote = await deps.fetchProvider(item);
        const parsed = parseScaledPrice(quote.price);
        if (!parsed || quote.currency !== item.quote_currency) {
          results.push({ ...item, availability: "UNSUPPORTED" });
          continue;
        }
        const record = {
          ...item,
          ...parsed,
          observed_at: quote.observed_at,
          fetched_at: now.toISOString(),
          expires_at: new Date(now.getTime() + deps.ttlMs(item.asset_type)).toISOString(),
          source: "twelve-data",
        };
        await deps.writeCache(record);
        results.push({ ...record, availability: "FRESH" });
      } catch {
        const stale = await deps.readStaleCache(item);
        results.push(stale ? { ...stale, availability: "STALE" } : { ...item, availability: "UNAVAILABLE" });
      }
    }
    return json(200, { prices: results, generated_at: now.toISOString() });
  };
}

function json(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}
