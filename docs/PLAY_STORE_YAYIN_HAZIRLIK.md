# Play Store ve Hukuki Yayın Hazırlığı

Bu belge yayın öncesi tek kontrol listesidir. Teknik hazırlığın tamamlanması, hukuki onay veya
Google Play production yayını anlamına gelmez. Onay kanıtı bulunmayan bir satır kapatılamaz.

## 1. Mevcut yayın kararı

Durum: **YAYINA KAPALI**.

Bloklayan dış girdiler:

- veri sorumlusunun/yayıncı tüzel kişisinin onaylı unvanı, adresi ve iletişim kanalı;
- hukuk danışmanı veya proje sahibi tarafından onaylanmış Gizlilik Politikası ve Kullanım Koşulları;
- herkese açık HTTPS politika ve hesap silme URL'leri;
- Play Console Data Safety formunun gerçek production davranışıyla doğrulanması;
- Play App Signing kurulumu, güvenli upload key ve yetkili Play Console hesabı;
- internal ve closed test kanıtları ile son fiziksel cihaz kabul tutanağı.

Bu değerler bilinmeden uygulamada hukuki metin uydurulmaz, signing anahtarı repository'ye eklenmez
ve mağaza formu gönderilmiş gibi işaretlenmez.

## 2. Teknik veri envanteri

| Veri grubu | Amaç | Konum/aktarımı | Kullanıcı kontrolü | Data Safety taslak sınıfı |
|---|---|---|---|---|
| E-posta, kullanıcı kimliği ve oturum | Hesap ve kimlik doğrulama | Supabase Auth; oturum Android güvenli depolamada | Çıkış, parola/e-posta akışları | Kişisel bilgi / hesap yönetimi |
| Profil adı ve tercihler | Kişiselleştirme | Room ve senkronize profil | Uygulama içinden düzenleme | Kişisel bilgi / uygulama işlevi |
| İşlem, bütçe, hedef, borç, abonelik, varlık ve ortak alan kayıtları | Kişisel finans yönetimi | SQLCipher ile yerel Room; kullanıcı kapsamlı Supabase senkronizasyonu | CRUD, soft-delete, dışa aktarma | Finansal bilgi / uygulama işlevi |
| Makbuz görseli ve OCR sonucu | Kullanıcının seçtiği belgeyi işleme | Özel yerel dosya alanı ve private storage akışı | Bağ kaldırma/silme akışı; OCR yayın kapısı şu an kapalı | Fotoğraf/dosya ve finansal bilgi |
| Profil avatarı | Profil görseli | Yalnız uygulamanın dahili cihaz depolaması | Değiştirme ve kaldırma | Fotoğraf; cihazla sınırlı kullanım |
| Bildirim tercihi ve hatırlatıcı kayıtları | Yerel ödeme hatırlatması | DataStore/Room ve Android notification channel | Ayarlardan açma/kapama; Android runtime izni | Uygulama etkinliği/işlevsellik değerlendirmesi gerekli |
| Kamera erişimi | Avatar/makbuz yakalama | Yalnız kullanıcı aksiyonuyla runtime permission | İzin reddedilebilir | Fotoğraf/video erişimi beyanı doğrulanmalı |

Kod tabanında reklam SDK'sı, konum, rehber veya mikrofon izni bulunmuyor. Analytics/crash
raporlama sağlayıcısı eklenmeden önce bu envanter ve politika yeniden gözden geçirilmelidir.
Data Safety formuna aktarılmadan önce Supabase'in veri işleyen/servis sağlayıcı rolü, production
retention süreleri ve ülke/bölge aktarımı hukuk sahibi tarafından doğrulanmalıdır.

## 3. Hukuki metin girdi paketi

Nihai metin hazırlanırken aşağıdaki alanların tamamı doldurulmalıdır:

- yayıncı/veri sorumlusu tam unvanı, ülke, posta adresi ve iletişim e-postası;
- işlenen veri kategorileri, amaç, hukuki dayanak ve saklama süreleri;
- Supabase ve varsa diğer veri işleyenler, veri bölgeleri ve aktarım dayanağı;
- kullanıcı erişim, düzeltme, silme, taşıma ve itiraz kanalları;
- hesap silme talebinin uygulama içi ve web üzerinden tamamlanma süresi;
- çocuklara yönelik kullanım yaşı ve ebeveyn izni kararı;
- finansal bilginin bilgilendirme amaçlı olduğu ve yatırım/bankacılık hizmeti olmadığı kapsam;
- güvenlik olayı iletişimi, politika sürümü, yürürlük ve değişiklik bildirimi yöntemi;
- Kullanım Koşulları için kabul anı, yasak kullanım, sorumluluk sınırı, fesih ve uyuşmazlık hukuku.

Onaylı dosya teslim sözleşmesi:

| Belge | Beklenen URL | Sürüm kanıtı | Durum |
|---|---|---|---|
| Gizlilik Politikası | Proje sahibi tarafından verilecek kalıcı HTTPS URL | Onaylayan, tarih, sürüm/hash | Bekliyor |
| Kullanım Koşulları | Proje sahibi tarafından verilecek kalıcı HTTPS URL | Onaylayan, tarih, sürüm/hash | Bekliyor |
| Hesap ve veri silme sayfası | Giriş gerektirmeden erişilen HTTPS URL | Talep adımları ve SLA | Bekliyor |

## 4. Signing ve dağıtım sözleşmesi

- Play App Signing kullanılacak; app signing key Google Play tarafından korunacak.
- Ayrı upload key yayın yetkilisi tarafından çevrimdışı/güvenli ortamda üretilecek.
- `.jks`, `.keystore`, parola veya base64 anahtar repository'ye, dokümana ve test fixture'ına
  eklenmeyecek.
- CI signing bilgileri ancak onaylı secret store üzerinden ve yalnız korumalı yayın ortamında
  bağlanacak. Pull request workflow'u signing secret'ına erişmeyecek.
- Anahtar sahibi, yedek konumu, erişim kurtarma yöntemi ve rotation tarihi özel operasyon
  kaydında tutulacak; bu repository'de yalnız kanıt referansı bulunacak.

## 5. Yayın aşamaları ve kanıtlar

### Şimdi tamamlanan hazırlık

- [x] Host/unit, lint, build, secret ve iOS ortak derleme CI kapıları.
- [x] API 35 emülatörde otomatik Android cihaz test kapısı tanımı.
- [x] SQLCipher açılış/yanlış anahtar ve Room 1→22 migration cihaz testleri.
- [x] FileProvider/avatar hesap izolasyonu, WorkManager benzersiz iş ve notification channel cihaz testleri.
- [x] Teknik veri envanteri, hukuki girdi sözleşmesi ve signing yaklaşımı.

### Internal test öncesi zorunlu

- [ ] Onaylı politika/koşul/silme URL'lerini uygulama ve mağaza kaydına bağla.
- [ ] Production application ID, versionCode/versionName, ikon, feature graphic ve ekran görüntülerini onayla.
- [ ] Play App Signing'i kur ve güvenli upload key ile imzalı AAB üret.
- [ ] Data Safety, içerik derecelendirmesi, uygulama erişimi ve iletişim formlarını iki kişiyle doğrula.
- [ ] Fiziksel API 26 ve güncel Android cihazda SQLCipher upgrade, offline/sync ve dosya yaşam döngüsü smoke testi yap.

### Closed test öncesi zorunlu

- [ ] Internal test crash/ANR, izin reddi ve hesap silme geri bildirimlerini kapat.
- [ ] TalkBack, büyük yazı, küçük ekran, koyu tema ve Türkçe metin taşması insan kabulünü tamamla.
- [ ] Uygulama süreçten öldürüldükten ve cihaz yeniden başladıktan sonra WorkManager görevlerini doğrula.
- [ ] Bildirim izni kabul/ret/kalıcı ret ve ayarlardan geri dönüş akışlarını fiziksel cihazda doğrula.
- [ ] En az bir eski production-benzeri şifreli DB kopyasının v22 yükseltmesini doğrula; gerçek kullanıcı verisini artifact/log'a koyma.

### Production öncesi zorunlu

- [ ] Closed test geri bildirimlerini kapat ve son release commit SHA/AAB SHA-256 kaydını al.
- [ ] Beş GitHub check'ini `main` ruleset içinde zorunlu yap.
- [ ] Hukuki metin sürümü ile uygulamada/Play Console'da görünen URL'lerin aynı olduğunu doğrula.
- [ ] Staged rollout, rollback sahibi, destek kanalı ve izleme planını onayla.
- [ ] Production Supabase hedefi ve migration durumu ayrı production runbook onayıyla doğrulansın.

## 6. Fiziksel cihaz kabul kaydı

Her koşum için tarih, tester, cihaz/model, Android sürümü, uygulama versionCode, commit SHA,
senaryo sonucu ve kanıt yolu kaydedilir. Finansal payload, token, e-posta veya makbuz görüntüsü
ekran kaydı/log/artifact içine alınmaz. Başarısız senaryo kapatılmadan production aşamasına geçilmez.
