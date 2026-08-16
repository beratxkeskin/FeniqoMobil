# FeniqoMobil AI ve Geliştirici Çalışma Sözleşmesi

Bu dosya repository kökünden aşağıdaki tüm dosyalar için geçerlidir. Codex, Claude, Gemini,
Copilot ve diğer kodlama araçları bu kuralları aynı proje sözleşmesi olarak kullanmalıdır.

## 1. Zorunlu okuma sırası

Bir değişikliğe başlamadan önce en az şu dosyaları oku:

1. `AGENTS.md`
2. `PRODUCT.md`
3. `ARCHITECTURE.md`
4. `DATABASE.md` — veri veya senkronizasyonla ilgiliyse zorunlu
5. `FEATURES.md`
6. `DEVELOPMENT_PLAN.md`
7. `FENIQO_MOBIL_YOL_HARITASI.md` — güncel checkbox ve ayrıntılı adım için

Supabase işi için ayrıca:

- `docs/SUPABASE_V1_GUVENLIK_VE_MIGRATION_PLANI.md`
- `supabase/README.md`
- uygulanacak tüm önceki sıralı migration dosyaları

## 2. Kaynakların önceliği

Çelişki halinde şu sırayı kullan:

1. Proje sahibinin güncel ve açık talimatı.
2. Çalışan kod, testler ve uygulanmış migration gerçeği.
3. `AGENTS.md` güvenlik ve çalışma kuralları.
4. Konuya özel ana belge: `PRODUCT`, `ARCHITECTURE`, `DATABASE`, `FEATURES`.
5. Ayrıntılı yol haritası ve uzman `docs/` belgeleri.

Çelişkiyi sessizce yorumlama; etkiliyse proje sahibine bildir ve ilgili belgeleri birlikte düzelt.

## 3. Değişmez mimari kurallar

- Android-first Kotlin Multiplatform yaklaşımını koru.
- Android UI Jetpack Compose, ilk iOS UI SwiftUI'dır.
- Clean Architecture + MVVM kullan.
- Room uygulamanın Single Source of Truth'üdür.
- UI doğrudan Supabase, DAO, DTO veya Realtime payload kullanamaz.
- Repository yerel veritabanı ile uzak kaynak arasındaki tek koordinasyon sınırıdır.
- Entity, DTO ve domain modelini birbirine dönüştürmeden doğrudan taşıma.
- Para `Double` değil en küçük para biriminde `Long` tutulur.
- Silme soft-delete/tombstone; sync optimistic `version/baseVersion` kullanır.
- Yerel mutation entity + outbox olarak aynı Room transaction'ında yazılır.
- Conflict sessizce overwrite edilmez; iki kopya kullanıcı kararına kadar korunur.
- Hilt yalnız Android'de kalır.
- WorkManager, Keystore, BiometricPrompt, CameraX ve ML Kit Android'e özeldir.
- Android bağımlılığını `commonMain` içine ekleme.

## 4. Güvenlik kuralları

- Secret/service-role key'i hiçbir mobil kaynak, test fixture, doküman veya Git commitine ekleme.
- Yalnız publishable/legacy anon key build configuration üzerinden kullanılabilir.
- Token, parola, finansal payload, makbuz veya kişisel veri loglama.
- RLS'yi geliştirme kolaylığı için kapatma veya service-role ile istemci davranışını taklit etme.
- Production Supabase'e migration, SQL, policy, RPC, storage veya veri mutation uygulama.
  Bunun için hedef proje, dosya kapsamı ve risk açıklandıktan sonra ayrıca açık onay gerekir.
- Staging işlemi öncesinde bağlı proje kimliğini doğrula; production ile aynı olduğunu varsayma.
- Gizli yerel dosyaları (`local.properties`, `.env`, Supabase `.temp`) commit etme.

## 5. Dosya ve migration disiplini

- Mevcut kullanıcı değişikliklerini koru; ilgisiz dosyaları düzeltme veya yeniden biçimlendirme.
- Uygulanmış migration dosyasını değiştirme; yeni timestamp'li ileri yönlü migration ekle.
- Room şema sürümü değişirse migration, export edilen schema JSON ve migration testi birlikte eklenir.
- Büyük Gradle değişikliklerini gerekçesiz yapma; dependency'yi doğru source set'e ekle.
- Build çıktısı, IDE metadata'sı, anahtar veya geçici dosya ekleme.
- Var olan isimlendirme ve paket düzenini izle; aynı sorumluluk için paralel yeni abstraction üretme.
- Kod yorumlarını yalnız neden/iş kuralı açıklıyorsa ekle; gerektiğinde temiz Türkçe kullan.

## 6. Çalışma biçimi

1. Önce mevcut durumu ve ilgili testleri incele.
2. Kullanıcı yalnız inceleme/teşhis istediğinde dosya değiştirme.
3. Değişiklik istendiğinde küçük ve tutarlı bir dilim uygula.
4. Varsayım yapman gerekiyorsa bunu görünür kıl; mimariyi değiştiren varsayım için onay al.
5. Kullanıcı öğretici ilerleme istediğinde neyi ve neden yaptığını Türkçe açıkla.
6. Kullanıcı özerk uygulama izni verdiyse ilişkili küçük adımları tamamla, ancak kapsamı genişletme.
7. Manuel işlem gerçekten gerekiyorsa en sonda tam yolu, ekranı ve beklenen sonucu yaz.

## 7. Test ve tamamlanma

Riskle orantılı olarak aşağıdakileri çalıştır:

```powershell
.\gradlew.bat :sharedLogic:testAndroidHostTest
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :sharedLogic:compileKotlinIosSimulatorArm64
```

Ek kurallar:

- Değişen iş kuralı için hedefli test olmadan ana adımı tamamlandı sayma.
- Room değişikliğinde DAO/migration testini çalıştır.
- DI veya Android platform değişikliğinde debug APK derlemesini çalıştır.
- `commonMain` değişikliğinde mümkünse Android testi ve iOS compile doğrula.
- Test başarısızlığını gizleme, testi silme veya doğrulamayı atlayıp checkbox işaretleme.
- Mevcut warning'leri yeni error gibi raporlama; yeni warning ürettiysen belirt.

## 8. Belge güncelleme

Her ana adım tamamlandığında:

- `FENIQO_MOBIL_YOL_HARITASI.md` checkbox ve ilerleme notunu güncelle;
- `DEVELOPMENT_PLAN.md` güncel/sonraki faz bilgisini güncelle;
- kullanıcı özelliği durumu değiştiyse `FEATURES.md` güncelle;
- ürün, mimari veya veri kararı değiştiyse ilgili ana belgeyi aynı değişiklikte güncelle.

Aynı bilgiyi farklı belgelerde ayrıntılı biçimde kopyalama. Ayrıntı ana kaynakta tutulur, diğer
belgeler bağlantı verir.

## 9. Git kuralları

- Varsayılan dal `main` olsa da dal ve remote'u komutla doğrula.
- Commit veya push yalnız açık kullanıcı isteğiyle yapılır.
- Commit öncesi `git diff --check`, durum, staged kapsam ve secret taraması yap.
- Kullanıcı değişikliklerini `reset --hard`, checkout veya benzeri yıkıcı komutlarla silme.
- Push öncesi uzak dalın ileride olup olmadığını doğrula.
- Test sonucu, production/staging etkisi ve manuel kalan işleri teslim mesajında açıkça belirt.

## 10. Yasak kısa yollar

- UI'dan hızlıca Supabase çağırmak.
- Ağ cevabını Room'a yazmadan ekranda göstermek.
- Para için `Double` kullanmak.
- Hard-delete ile sync problemini çözmek.
- Conflict durumunda son yazan kazanır davranışını izinsiz eklemek.
- Service-role key ile mobil istemciyi çalıştırmak.
- Production üzerinde deneme migration'ı yapmak.
- Android platform API'sini `commonMain` içinde `expect/actual` ihtiyacını değerlendirmeden kullanmak.
- Bir özelliği yalnız model/DTO bulunduğu için tamamlandı işaretlemek.

