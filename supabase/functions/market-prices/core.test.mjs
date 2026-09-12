import test from "node:test";
import assert from "node:assert/strict";
import { createHandler, normalizeItem, parseScaledPrice, PROVIDER_URL } from "./core.mjs";

const NOW = new Date("2026-09-09T12:00:00.000Z");

function dependencies(overrides = {}) {
  return {
    now: () => NOW,
    ttlMs: () => 300_000,
    verifyJwt: async () => true,
    consumeQuota: async () => true,
    readCache: async () => null,
    readStaleCache: async () => null,
    fetchProvider: async () => ({ price: "2865.43210", currency: "TRY", observed_at: NOW.toISOString() }),
    writeCache: async () => {},
    ...overrides,
  };
}

function request(body, token = "valid-token") {
  return new Request("http://local/market-prices", {
    method: "POST",
    headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
    body: JSON.stringify(body),
  });
}

test("provider endpoint is compile-time fixed and HTTPS", () => {
  assert.equal(PROVIDER_URL, "https://api.twelvedata.com/quote");
});

test("symbol and decimal parsing are strict and floating-point free", () => {
  assert.equal(normalizeItem({ asset_type: "CRYPTO", symbol: " btc ", quote_currency: "TRY" }).item.symbol, "BTC");
  assert.equal(normalizeItem({ asset_type: "STOCKS", symbol: "https://evil", quote_currency: "USD" }).error, "INVALID_SYMBOL");
  assert.deepEqual(parseScaledPrice("0.00001234"), { price_unscaled: "1234", price_scale: 8 });
  assert.equal(parseScaledPrice("1e6"), null);
});

test("missing or rejected JWT fails closed before provider access", async () => {
  let providerCalls = 0;
  const handle = createHandler(dependencies({ verifyJwt: async () => false, fetchProvider: async () => { providerCalls++; } }));
  const response = await handle(request({ items: [{ asset_type: "CRYPTO", symbol: "BTC", quote_currency: "TRY" }] }));
  assert.equal(response.status, 401);
  assert.equal(providerCalls, 0);
});

test("batch limit and unsafe symbols are rejected", async () => {
  const handle = createHandler(dependencies());
  assert.equal((await handle(request({ items: [] }))).status, 400);
  assert.equal((await handle(request({ items: [{ asset_type: "STOCKS", symbol: "../AAPL", quote_currency: "USD" }] }))).status, 400);
});

test("exhausted authenticated quota returns 429 before provider access", async () => {
  let providerCalls = 0;
  const handle = createHandler(dependencies({ consumeQuota: async () => false, fetchProvider: async () => { providerCalls++; } }));
  const response = await handle(request({ items: [{ asset_type: "CRYPTO", symbol: "BTC", quote_currency: "TRY" }] }));
  assert.equal(response.status, 429);
  assert.equal(providerCalls, 0);
});

test("fresh cache avoids provider and returns normalized result", async () => {
  let providerCalls = 0;
  const cached = { asset_type: "CRYPTO", symbol: "BTC", quote_currency: "TRY", price_unscaled: "10", price_scale: 2 };
  const handle = createHandler(dependencies({ readCache: async () => cached, fetchProvider: async () => { providerCalls++; } }));
  const response = await handle(request({ items: [{ asset_type: "CRYPTO", symbol: "btc", quote_currency: "TRY" }] }));
  assert.equal(response.status, 200);
  assert.equal(providerCalls, 0);
  assert.equal((await response.json()).prices[0].availability, "FRESH");
});

test("provider result is validated, cached and returned", async () => {
  let written;
  const handle = createHandler(dependencies({ writeCache: async (record) => { written = record; } }));
  const response = await handle(request({ items: [{ asset_type: "CRYPTO", symbol: "BTC", quote_currency: "TRY" }] }));
  const body = await response.json();
  assert.equal(body.prices[0].price_unscaled, "286543210");
  assert.equal(body.prices[0].price_scale, 5);
  assert.equal(body.prices[0].source, "twelve-data");
  assert.equal(written.symbol, "BTC");
});

test("provider failure returns stale cache or unavailable per item", async () => {
  const stale = { asset_type: "STOCKS", symbol: "AAPL", quote_currency: "USD", price_unscaled: "20000", price_scale: 2 };
  const handle = createHandler(dependencies({ fetchProvider: async () => { throw new Error("offline"); }, readStaleCache: async () => stale }));
  const response = await handle(request({ items: [{ asset_type: "STOCKS", symbol: "AAPL", quote_currency: "USD" }] }));
  assert.equal((await response.json()).prices[0].availability, "STALE");
});
