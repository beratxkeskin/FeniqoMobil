# Market Prices Edge Function

Bu Function yalnız oturum açmış mobil istemcilerin normalize piyasa fiyatı istemesi içindir.
Mobil uygulama Twelve Data veya Supabase service-role anahtarı taşımaz.

Gerekli Edge secret adları:

- `TWELVE_DATA_API_KEY`
- Supabase tarafından sağlanan `SUPABASE_URL`
- Supabase tarafından sağlanan `SUPABASE_ANON_KEY`
- Supabase tarafından sağlanan `SUPABASE_SERVICE_ROLE_KEY`

Secret değerleri repository'ye, `.env` dosyasına veya mobil build config'e eklenmez. Function
gateway JWT doğrulamasına ek olarak kullanıcı token'ını Auth `/user` endpoint'iyle doğrular ve
`claim_market_price_request` RPC'si üzerinden kullanıcı başına dakikada 10 istek sınırı uygular.
İstek başına en fazla 20 sembol kabul edilir. Sağlayıcı host'u ve `/quote` endpoint'i kaynak kodda
sabittir; istemciden URL alınmaz.

Yerel, sağlayıcıya çıkmayan sözleşme testi:

```powershell
node --test supabase/functions/market-prices/core.test.mjs
```

Gerçek Deno runtime ve sağlayıcı smoke testi ancak yerel Supabase ortamında test secret'ı ile veya
ayrı staging ortamında yapılır. Production deploy ayrıca açık onay gerektirir.
