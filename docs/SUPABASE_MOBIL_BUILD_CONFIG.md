# Supabase Mobil Build Configuration

Supabase proje URL'si ve mobil istemcide kullanılabilen publishable/legacy anon key kaynak
koda yazılmaz. Android derlemesi değerleri aşağıdaki sırayla arar:

1. CI ve yerel ortam değişkenleri:
   - `FENIQO_SUPABASE_URL`
   - `FENIQO_SUPABASE_PUBLISHABLE_KEY`
2. Git tarafından izlenmeyen kök `local.properties`:
   - `feniqo.supabase.url`
   - `feniqo.supabase.publishableKey`

Mobil uygulama yalnız yukarıdaki iki kaynaktan beslenir; referans web `.env` fallback'i bulunmaz.

## Güvenlik sınırı

- Mobil istemcide yalnızca `sb_publishable_` ile başlayan publishable anahtarlar kabul edilir.
- `sb_secret_...` veya service-role anahtarları kesinlikle mobil yapılandırmaya eklenemez.
- Gradle derleme sürecinde bu değerleri loglamaz veya hata mesajlarında yazdırmaz.
- UI `SupabaseClient` kullanmaz; istemci repository/data-source katmanına Hilt ile verilir.
- iOS build configuration, iOS uygulama kabuğu aktif geliştirilmeye başlandığında
  `.xcconfig` ve `Info.plist` aktarımıyla aynı `SupabaseConnectionConfig` sözleşmesine bağlanacaktır.

## Android oturum saklama

- Supabase erişim ve yenileme tokenları `AndroidSupabaseSessionManager` üzerinden saklanır.
- Oturum JSON'u, dışarı aktarılamayan Android Keystore AES-256-GCM anahtarıyla şifrelenir.
- Diskte yalnızca IV ve şifreli veri bulunur; dosya `noBackupFilesDir` altında tutulur.
- Çıkışta şifreli oturum dosyası silinir. Keystore anahtarı token içermez.
- Supabase istemcisi güvenli depodan otomatik yükleme/kaydetme ve token yenilemeyi etkinleştirir.
- iOS için aynı `SessionManager` sözleşmesinin Keychain uyarlaması 10.4 kapsamında eklenecektir.
