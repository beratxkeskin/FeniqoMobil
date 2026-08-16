# FeniqoMobil Geliştirme Planı

> Standart araçlar için güncel çalışma giriş noktasıdır. Ayrıntılı alt görevler, checkbox'lar ve
> tarihsel ilerleme notlarında [FENIQO_MOBIL_YOL_HARITASI.md](FENIQO_MOBIL_YOL_HARITASI.md)
> tek yetkili kaynaktır; bu dosya aynı yüzlerce checkbox'ı kopyalamaz.

## Güncel durum

- Son tamamlanan ana adım: **5.4 — Realtime**
- Sıradaki ana adım: **6.1 — WorkManager**
- Güncel doğrulama: **73/73 ortak Android host testi**, Android debug APK ve iOS Simulator
  ARM64 ortak kod derlemesi başarılı.
- Production Supabase durumu: migration uygulanmadı.
- Staging: `FeniqoMobil-Staging`; V1 migration, RLS, RPC ve Realtime publication doğrulandı.

## Tamamlanan fazlar

| Faz | Durum | Çıktı |
|---|---|---|
| 1. Analiz ve kapsam | Tamamlandı | Web envanteri, V1 sınırı, güvenlik ve migration planı |
| 2. Proje omurgası | Tamamlandı | KMP modülleri, bağımlılıklar, tema ve UI kabuğu |
| 3. Domain | Tamamlandı | Temel/ikinci dalga modeller, repository sözleşmeleri ve use case'ler |
| 4. Yerel veri | Tamamlandı | Room v3, DAO/mapper, SQLCipher/Keystore ve outbox |
| 5. Uzak veri | Tamamlandı | Auth, DTO/remote, sync motoru, staging kabulü ve Realtime |

5.1'de Android için build configuration ve güvenli oturum saklama uygulanmıştır. iOS `.xcconfig`,
Keychain ve üretim güvenlik adaptörlerinin kalan kısmı Android-first kararı gereği 10.4'te tamamlanır.

## Aktif faz: 6.1 WorkManager

Amaç: uygulama kapalıyken veya arka plandayken bekleyen outbox kayıtlarının güvenli biçimde
senkronize edilmesini sağlamak.

Sıralı alt adımlar:

1. Hilt destekli bir `CoroutineWorker` oluştur.
2. Worker'ın yalnız `SyncRepository` kullanmasını sağla; DAO veya Supabase client enjekte etme.
3. Ağ bağlantısı constraint'i tanımla.
4. Uygulama başlangıcında benzersiz one-time ilk sync işi planla.
5. Outbox için benzersiz arka plan sync isteği oluştur; duplicate worker zincirini engelle.
6. Geçici ağ hatasında `Result.retry()` ve WorkManager exponential backoff kullan.
7. Auth yokluğu, kullanıcı conflict'i ve kalıcı veri hatasını retry fırtınasına dönüştürme.
8. Worker planlama ve sonuç eşleme testlerini ekle.
9. Android debug APK ve ortak testleri doğrula.
10. Ana adım tamamlanınca ayrıntılı yol haritasını ve bu dosyayı güncelle.

6.1 tamamlanma ölçütü: ağ kapalıyken oluşturulan kayıt uygulama kapalı olsa bile ağ geri
geldiğinde tekil bir worker zinciriyle gönderilir; aynı outbox operasyonu yinelenmez.

## Sonraki fazlar

### 6.2 Senkronizasyon gözlemi

- son başarılı senkronizasyon zamanı;
- bekleyen operasyon ve conflict sayısı;
- müdahaleci olmayan offline göstergesi;
- manuel senkronizasyon.

### 7. Presentation — V1 kullanıcı akışı

1. Type-safe navigasyon ve UI state sözleşmeleri.
2. Splash/oturum, giriş ve kayıt ekranları.
3. İşlem listesi, ekleme/düzenleme/silme ve filtreleme.
4. Kategori yönetimi.
5. Dashboard ve sync durumu.

### 8–9. Genişletmeler ve cihaz özellikleri

Bütçe, tekrarlayan işlemler, hedef/borç, workspace, varlık/rapor, biyometri, OCR ve veri
taşınabilirliği çekirdek V1 kabulünden sonra ele alınır.

### 10. Kalite ve yayın

- otomatik test kapsamının tamamlanması;
- statik analiz ve performans;
- Android release/internal testing;
- iOS Keychain, DB güvenliği ve SwiftUI ürün akışı.

## Her adım için çalışma döngüsü

1. İlgili sözleşme ve mevcut kod okunur.
2. Küçük, geri alınabilir uygulama dilimi seçilir.
3. Kod ve hedefli test birlikte yazılır.
4. Riskle orantılı geniş regresyon çalıştırılır.
5. Tamamlanma ölçütü karşılanırsa checkbox ve ilerleme notu güncellenir.
6. Commit/push yalnız proje sahibinin açık isteğiyle yapılır.

## Doğrulama komutları

Windows proje kökünde:

```powershell
.\gradlew.bat :sharedLogic:testAndroidHostTest
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :sharedLogic:compileKotlinIosSimulatorArm64
```

Migration için yalnız doğru hedef doğrulandıktan sonra `supabase` CLI kullanılır. Production
komutları ayrı açık onay olmadan çalıştırılmaz.

## Plan güncelleme kuralı

- Ayrıntılı görev durumu ve ilerleme notu `FENIQO_MOBIL_YOL_HARITASI.md` içinde güncellenir.
- Bu dosyada yalnız güncel faz, sıradaki faz ve yüksek seviyeli tamamlanma özeti tutulur.
- Ürün kapsamı değişirse `PRODUCT.md` ve `FEATURES.md` de güncellenir.
- Mimari veya veri sözleşmesi değişirse ilgili ana belge aynı değişiklik setinde güncellenir.
