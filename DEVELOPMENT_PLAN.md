# FeniqoMobil Geliştirme Planı

> 2026-09-15 — İşlemler 01–18 tasarım uyarlamasının mevcut mimarinin desteklediği kapsamı tamamlandı ve doğrulandı: [İşlemler kabul kaydı](docs/ISLEMLER_TASARIM_KABUL.md). Kalıcı makbuz dosya yaşam döngüsü, tutarla ortak dağıtım sözleşmesi ve oturumlu cihaz kabulü sonraki altyapı dilimidir.

> Standart araçlar için güncel çalışma giriş noktasıdır. Ayrıntılı alt görevler, checkbox'lar ve
> tarihsel ilerleme notlarında [FENIQO_MOBIL_YOL_HARITASI.md](FENIQO_MOBIL_YOL_HARITASI.md)
> tek yetkili kaynaktır; bu dosya aynı yüzlerce checkbox'ı kopyalamaz.

## Güncel durum

- **V1 Çekirdek Kabul Doğrulaması, Hata Sınıflandırmalı Toparlanma ve Staging Durumu (2026-09-21):** Room v20 (`sync_operations.error_classification`), `DEFINITIVE_REJECTION` (kesin validation/yetki reddi) ve `AMBIGUOUS_RESULT` (SerializationException, genel IllegalArgumentException, ağ timeout/stale in-flight/ACK kaybı) ayrımı yapıldı; kesin ret durumunda temiz `CREATE` veya `HARD_DELETE`, belirsiz sonuç durumunda ise `operation_id` ve payload korunarak ardıl mutasyonların `predecessor_operation_id` ve `is_blocked = true` ile bağlanması uygulandı. Pre-flight kontroller `DefinitiveOutboxFailureException` ile fail-closed tipli hata olarak bağlandı (writer çağrısı 0 kaldı ve DEFINITIVE_REJECTION oldu; writer sonrası dönerken response decode hatası AMBIGUOUS_RESULT kaldı). NULL error_classification hiçbir zaman kesin ret sayılmaz; doğrudan sınıflandırma testleri eklendi. `RoomDaoTest.kt` içinde FakeRemoteTransactionRpc gerçek DTO sözleşmesi ve payload decode ile Flow A (ACK kaybı ve replay) ve Flow B (ACK kaybı sonrası delete ve zombi kalmaması) gerçek uçtan uca bileşen testi olarak kanıtlandı. 1499 testin tamamı (`:sharedLogic:testAndroidHostTest`), 686 test (`:androidApp:testDebugUnitTest`), 313 test (`:sharedUI:testAndroidHostTest`), `:androidApp:assembleDebug` ve `:sharedLogic:compileKotlinIosSimulatorArm64` başarıyla geçti. Kategori seed migration'ı fail-closed hale getirildi (12 yeni kayıt eklenir, 2 legacy kayıt [1120, 1121] tombstone yapılır, net aktif +10 olur; 27 aktif kanonik kategori post-check ile tam alan doğrulanır). İzole yerel PostgreSQL template veritabanlarında doğrudan migration dosyasının tam metnini çalıştıran 16 gerçek SQL kabul testi %100 geçti. Proje sahibi açık onayıyla `20260921000100_seed_extended_canonical_categories.sql` yalnız `rxfaiynkhaxrksosxvxp` staging projesine uygulandı; M1–M4 (Sözleşme, Yerel 16 Senaryo, Staging Uygulaması ve Staging Sonrası Doğrulama) GEÇTİ. 27 aktif kanonik kategori (9 gelir + 18 gider), 1120/1121 tombstone version=2 ve 0 katalog dışı fazlalık staging üzerinde kanıtlandı; kullanıcı kategorileri korundu. Production'a kesinlikle dokunulmadı. Git commit/push yapılmadı. Cihaz A (SM-A715F) ve Cihaz B (sdk_gphone16k_x86_64) ADB üzerinde şu an bağlı olmadığından özgün S1–S8 senaryoları kural gereği dürüstçe AÇIK bırakıldı (sahte başarı üretilmedi). Ayrıntılı durum tablosu için [yol haritasına](FENIQO_MOBIL_YOL_HARITASI.md) bakınız.

- **Kişisel Bilgiler görünen ad kaydı (2026-09-20):** Eksik yerel profil için oturum sahibinin uzak profili Room'a alınır; ad değişikliği Room ve V2 outbox'a atomik kaydedilir. Repository/Room/ViewModel testleri ile Android ve iOS derlemeleri geçti. Oturumlu cihaz ve ikinci cihaz eşitleme kabulü açık; ayrıntı [yol haritasında](FENIQO_MOBIL_YOL_HARITASI.md).

- **Profil Merkezi Rota ve Durum Entegrasyonu (2026-09-20):** Profil Merkezi (`ProfileScreen`) üzerindeki eski "Yakında" etiketleri kaldırıldı; Profil Özet kartı dahil tüm satırlar ilgili type-safe Compose rotalarına (`AccountRoute`, `PersonalInfoRoute`, `WorkspacePickerRoute`, `AppearanceRoute`, `NotificationsSettingsRoute`, `LanguageRegionRoute`, `SecurityPrivacyRoute`, `DataManagementRoute`, `LegalInfoRoute`, `HelpAboutRoute`) bağlandı. `UserSettingsRepository` üzerinden reaktif dil/bölge tercihi (`AppLanguage · Currency`) bağlandı; `SyncDisplaySeverity`'nin tüm 7 durumu (`UP_TO_DATE`, `PENDING`, `OFFLINE`, `CHECKING`, `SYNCING`, `ERROR`, `CONFLICT`) semantik yüzey/ikonlarla eşlendi; null profil durumu gerçek yükleme, boş durum ve oturum e-postası fallback'i ile ayrıştırılarak sonsuz yükleme engellendi; yardım makalesi ve lisansların dürüst "Hazırlanıyor" durumu korundu. Hedefli Robolectric Compose testleri, ViewModel testleri, `:sharedLogic:testAndroidHostTest`, `:sharedLogic:compileKotlinIosSimulatorArm64` ve `:androidApp:assembleDebug` ile doğrulandı.

- **İşlem sonrası navigasyon düzeltmesi (2026-09-19):** Başarı ekranının sekme geçmişine sızması ve taksit grubu kimliğiyle işlem açılması düzeltildi; hedefli Android testleri, ortak katman testleri, debug APK ve iOS derlemesi geçti. Ayrıntı ve cihaz kabulü [yol haritasında](FENIQO_MOBIL_YOL_HARITASI.md). Sonraki kontrol: tek/taksitli kayıt sonrası kapatma, Ana Sayfa ve sekmeler arası geçişin cihaz kabulü.

- **Rapor kategori adları düzeltildi:** kategori dağılımı satırları sabit “Kategori” metni yerine Room'daki güncel/tarihsel kategori adlarını gösterir; referansı artık bulunmayan kayıtlar açıkça “Silinmiş kategori” olarak işaretlenir. Hedefli ViewModel testi ve Android debug derlemesi başarılıdır.
- **Geliştirici demo hesabı** tamamlandı: ayrı `com.feniqo.mobile.demo` paketi normal uygulamayla yan yana kurulabilir; kurgusal Deniz Demo oturumu ve tarih duyarlı altı aylık finans senaryosu Room/repository üzerinden hazırlanır. Demo INTERNET izni taşımaz, uzak Auth/Sync çağrıları yerel adaptörlerle kapatılır, veriler düzenlenebilir ve üst çubuktan yalnız demo sandbox'ı sıfırlanabilir. Kullanım ve sınırlar [Demo kullanım rehberinde](docs/DEMO_KULLANIM.md) belgelenmiştir.
- **Raporlar ve Analizler Modülü (01–26 Tüm Onaylı Tasarım Ekranları)** tamamlandı: 6 onaylı tasarım panosundaki tüm 26 ekran (01–04 Raporlar Ana ve Dönem Özeti, 05–08 Kategori Dağılımı ve Detayı, 09–12 Nakit Akışı ve Dönem Karşılaştırması, 13–16 Harcama Takvimi, Bütçe, Abonelik ve Borç/Alacak Özetleri, 17–20 Gelecek Dönem Tahmini ve Finansal İçgörüler, 21–26 Filtreler, Özel Tarih Aralığı, Çoklu Para Birimi ve Sistem Durumları) Room SSOT, Clean Architecture, Long kuruş para modeli ve KMP saf domain use-case'leri (`DetailedReportUseCases`) ile eksiksiz kodlandı. Compose Canvas özel grafikleri (TrendDualLineChart, WeeklyDualBarChart, ReportDonutChart, BidirectionalCashFlowChart) eklendi; MoreHub üzerindeki pasif "Yakında" rozeti kaldırılarak aktif `ReportsRoute` akışına bağlandı; `maskAmounts` tutar gizleme TalkBack ve semantics seviyesinde uygulandı. Tüm modül birim testleri (`:androidApp:testDebugUnitTest`, `:sharedLogic:testAndroidHostTest`, `:sharedUI:testAndroidHostTest`), iOS Simulator Arm64 derlemesi (`:sharedLogic:compileKotlinIosSimulatorArm64`) ve Android debug APK derlemesi (`:androidApp:assembleDebug`) başarıyla doğrulandı.
- **Kimlik Doğrulama ve Parola Kurtarma Onaylı Tasarım Uyarlaması** tamamlandı: Onaylı tasarım panolarına (Pano A-01..04, Pano B-05..08, Pano C-09..14) birebir sadık kalınarak `WelcomeScreen`, `LoginScreen`, `RegisterScreen`, `AuthEmailVerificationScreen`, `ForgotPasswordScreen`, `PasswordResetSentScreen`, `ResetPasswordScreen` ve `PasswordResetSuccessScreen` ekranları kodlandı. Güvenli deep link (`feniqo://auth/callback`) yalnızca Supabase Auth SDK (`importAuthToken` / `exchangeCodeForSession`) üzerinden doğrulanarak `AuthRecoveryState.Verified` oturumuna dönüştürülür. Parola kurtarma oturumu `AppAuthState.PasswordRecovery` ile izole edildi, başarılı parola sıfırlamada veya terk edildiğinde oturum/state tamamen sıfırlanır. Tipli hata ayrımı ve hesap varlığını açığa çıkarmayan bilgilendirme korundu. Tüm birim testleri ve Android debug APK derlemesi başarıyla doğrulandı.
- **Ortak Alanlar (Workspaces) Modülü UI Yenilemesi** tamamlandı: Onaylı tasarım görsellerine (Görsel 01, 02 ve 03) sadık kalınarak `WorkspacePickerScreen`, `WorkspaceCreateScreen`, `WorkspaceJoinScreen`, `WorkspaceDetailsScreen` ve `WorkspaceSettlementScreen` ekranları modern Material 3 Feniqo tasarım diliyle yenilendi. Görsel 03'teki tüm diyalog ve sheet'ler (07 Davet kodu oluşturma/kopyalama, 08 Rol değişikliği, 09 Üye çıkarma, 10 Sahiplik devri, 11 Alandan ayrılma, 12 Para birimi ve alan türü seçicileri, 13 Boş durum ve dengeli ödeşme kartı, 14 Hata ve izin banner'ları, 15 Dışlanan gider uyarısı) uygulandı. Clean Architecture, Room SSOT, Long tutar kuralı ve domain yetki sınırları korundu; tüm hedefli birim testleri (`:androidApp:testDebugUnitTest`, `:sharedLogic:testAndroidHostTest`, `:sharedUI:allTests`), iOS Simulator ARM64 derlemesi ve Android debug APK derlemesi (`:androidApp:assembleDebug`) başarıyla doğrulandı.
- **Profil ve Ayarlar Modülü ve Alt Akışları (A1–A4 & B1–B4)** tamamlandı: Onaylı tasarım panolarına (A1–A4 & B1–B4) sadık kalınarak A1 Hesabım (diskten güvenli avatar okuma, dairesel maske, kamera rozeti, Pano A1 bottom sheet, yerel saklama güvencesi), A2 Fotoğraf Önizleme (1:1 dairesel maske, 3x3 kılavuz, zoom/pan, atomik tmp dosya değişimi, safeMove yedekleme garantisi ve path traversal korumalı `UserAvatarManager`, dar kapsamlı FileProvider, izole ContentResolver önbelleği), A3 E-posta Yönetimi (3 durumlu `EmailVerificationStatus`, durum yenileme, doğrulama e-postası tekrar gönderme, session email fallback'li `displayEmail`), A4 Hesap Silme Hazırlığı (gerçek yedekleme kapsamı açıklamalı veri dışa aktarma, ortak alan kontrolü ve senkronizasyon inceleme); B1 Yardım Merkezi (arama filtreleme, kategori kartları, popüler makaleler), B2 Makale Durumu (hazırlanıyor rozeti ve geri bildirim yönlendirmesi), B3 Yasal Bilgiler (genişletilebilir Gizlilik/Kullanım/KVKK metinleri, dürüst hazırlanıyor lisans rozeti), B4 Geri Bildirim (sahte toast kaldırıldı, taslak korumalı, ekran görüntüsü ekleme ve `FLAG_GRANT_READ_URI_PERMISSION` paylaşım intent'i; "Hata bildir" girişi doğrudan seçili ve kırmızı vurgulu [#D32F2F] buton). 25/25 birim testi, KMP host testleri, iOS simülatör derlemesi ve Android fiziksel cihaz (`SM-A715F - 13`) manuel kabul adımları eksiksiz tamamlandı. Görünüm, biyometri/cihaz kilidi, otomatik kilit, CSV dışa aktarma, JSON içe aktarma ve güvenli çıkış mevcut akışlara bağlıdır.
- Ana Sayfa (Dashboard) onaylı tasarım uyarlaması tamamlandı: onaylı konsept panellerine uygun olarak modern sans-serif "feniqo" logosu, kullanıcı avatarı ("A"), "Merhaba, $userName" ve "Ayına bir bakış" hiyerarşisi; koyu grafit "Dönem neti" kartı (tabular net tutar, döviz kodu, bu ayın işlem özeti, eşit genişlikte Gelir ve Gider kutucukları); ince yeşil tasarruf oranı göstergesi; kategori ikonlu bütçe özeti (aşım ve yaklaşma uyarıları); son işlemlerde onaylı renk kuralı (giderler eksi işaretli okunaklı kırmızı, gelirler yeşil, açıklamalar normal); Feniqo İçgörü kartı; yaklaşan ödemeler (gün/kısa ay rozetli); birikim hedefi; MoneyScore dairesel arc gauge ve ön değerlendirme kartı; farklı para birimi kapsam uyarısı; gerçek rotalara (Bütçeler, Abonelikler, Hedefler, İşlemler, Profil) bağlantılar uygulandı. Tüm SharedUI host testleri, AndroidApp unit testleri, SharedLogic host testleri, Android debug APK derlemesi ve iOS Simulator ARM64 ortak kod derlemesi başarıyla doğrulandı.
- Aktif çalışma: **Abonelikler (Gerçek Domain/Veri Altyapısı, Ekle/Düzenle Formu, Detay Ekranı ve Ödeme Kalıcılığı)** uygulandı, manuel emülatör kabulü bekliyor: Yaşam döngüsü durumları (`ACTIVE`, `PAUSED`, `CANCELLED`, `TRIAL`, `EXPIRED`), atomik fiyat geçmişi kaydı ve artış rozetleri, atomik gerçekleşen ödeme olayları ve takvim dönemi harcama/trend hesaplaması, taşma korumalı saf Kotlin `SubscriptionAnalyticsCalculator`; Room v15→v16→v17 ileri migration'ları (`16.json`, `17.json`, `website_url` ve `notes` sütunları); forward-only Supabase migration'ları (`20260913000100` ve `20260913000200`); `LocalMutationDao` Room `@Upsert` metot gövdesi hatasının düzeltilmesiyle ödemelerin SQLite'a kalıcı yazılması ve grafiğin reaktif güncellenmesi; ekran insets (üst/alt beyaz boşluk) optimizasyonu; detay kartı niteliklerine semantik renkli ikonlar eklenmesi; 4'lü aksiyon satırının detay kartının altına taşınması; kompakt dikey yerleşim ile kaydırmasız tek ekranda görünürlük sağlanması; **Görsel 1: Abonelik Ekle/Düzenle Formu** (`SubscriptionFormScreen.kt`); **Görsel 2: Abonelik Detay Ekranı** (`SubscriptionDetailScreen.kt`); tüm hedefli birim/host testleri, iOS simülatör derlemesi ve Android debug APK derlemesi başarıyla doğrulandı; staging/production Supabase'e dokunulmadı.
- Aktif çalışma: **Hedef Detay Ekranı** ilk güvenli dilimi uygulandı, manuel emülatör kabulü bekliyor. Hedef kartı type-safe detay rotasına gider; Room SSOT hedef/katkı gözlemi, ekle/çıkar/düzenle/soft-delete eylemleri, Long/basis-point ilerleme, deterministik içgörü, yaklaşık aylık gereksinim, güven koşullu katkı-hızı tahmini, gerçek katkı grafiği ve son hareketler eklendi. İsteğe bağlı açıklama ve yönetilen yerel/uzak kapak medyası bu dilime dahil edilmedi; Room v15 korunmuştur ve Supabase ortamlarına dokunulmamıştır.
- Aktif çalışma: **İşlem Formu (Gelir/Gider Ekle ve Düzenle) Yeniden Tasarımı ve Dirty Tracking** uygulandı, manuel kabul bekliyor: Feniqo sıcak-lüks tasarım dili (Scaffold, Serif başlık, 56dp alt bar birincil buton, doğal yazımlı 34sp tabular tutar alanı, tüm 5 ödeme yöntemi çipi ve semantik ikonları, takvim ikonlu tarih seçici, yalnız detay hatalarında otomatik açılan ve 'Dolu' rozeti gösteren 'Daha Fazla Ayrıntı' akordeonu); tam kapsamlı `FormSnapshot` dirty tracking (tür, tutar, para birimi, başlık, kategori, ödeme yöntemi, tarih, not, taksit, makbuz ve sıra bağımsız katılımcı kümesi); yalnız başarılı kayıt sonrasında sıfırlanan `hasUnsavedChanges`; güvenli çıkış onay diyaloğu ('Değişikliklerden Vazgeç' / 'Düzenlemeye Devam Et'); hedefli ViewModel testleri, sharedLogic host testleri, debug APK ve iOS simulator derlemeleri başarıyla doğrulandı.
- Önceki çalışma: **Faz 9.2 — Makbuz OCR** temel akışı tamamlandı fakat gerçek makbuzlarda fiyat/toplam algılama doğruluğu yetersiz bulundu; ürün kabulü ve fixture tabanlı doğruluk iyileştirmesi ileri bir dilime ertelendi.
- Ek güvenli domain dilimi: merchant/marka tanımanın platformdan bağımsız sözleşmesi, Türkçe
  normalleştiricisi, alias türü/kapsamı, kişisel→workspace→banka doğrulama önceliği, onaylı
  0–100 güven matrisi, 10 puanlık çakışma eşiği ve temsilî başlangıç kataloğu tamamlandı.
  Room/Supabase/UI/logo sağlayıcısı entegrasyonu yapılmadı.
- Kategori analitiği ve yönetim ekranı: sıcak-lüks görsel hiyerarşi, taşma korumalı tamsayı basis-point motoru (`CategoryAnalyticsCalculator`), fail-closed para birimi ve kategori türü tutarlılığı, filtre kapsam uyumu, kategori ve dönem filtreli İşlemler navigasyonu ve özel kategori yönetimi tamamlandı; tüm birim/host testleri ve APK derlemesi doğrulandı; emülatör görsel ve akış kabulü aşamasında.
- Mobil bilgi mimarisi: alt navigasyon Ana Sayfa / İşlemler / + / Bütçe / Daha Fazla olarak düzenlendi; Daha Fazla yalnız finansal modülleri gruplayan Financial Hub, avatar ise Profil/Hesap girişi olarak ayrıştırıldı.
- Tamamlanan fazlar:
  - Faz 8.5 E15-A — Kişisel Asset offline-first CRUD tamamlandı.
  - Faz 8.5 E15-B — Para birimi bazlı offline-first net değer özeti tamamlandı.
  - Faz 8.5 E15-C — Güvenli piyasa fiyat sözleşmesi, Room cache ve mobil fresh/stale/manual fallback tamamlandı; gerçek Edge runtime/provider smoke kabulü dış ortam bulunana kadar açık.
  - Faz 9.1 — Biyometrik uygulama kilidi, cihaz PIN/desen/parola geri dönüşü ve otomatik kilit süreleri tamamlandı.
  - Faz 8.4 E14-D — Workspace Ödeşme Ekranı ve Transfer Önerileri (MVI UI, `ObserveWorkspaceSettlementUseCase`, net bakiye ve transfer önerileri) tamamlandı.
  - Faz 8.4 E14-C — Ortak Gider Split Formu UI Entegrasyonu tamamlandı.
  - Faz 8.4 E14-B2 — Ortak Gider Split Senkronizasyonu ve Room v12 Altyapısı tamamlandı.
  - Faz 8.4 E12-F — OWNER Üye Çıkarma Akışı (`WORKSPACE_MEMBER DELETE` V2 outbox + UI entegrasyonu) tamamlandı.
  - Faz 8.4 E12-E — Workspace Sahiplik Devri UI Entegrasyonu tamamlandı.
  - Faz 8.4 E12-D — Workspace Sahiplik Devri Atomik ve Fail-Closed Altyapısı tamamlandı.
  - Faz 8.4 E12-C — OWNER Workspace Davet Oluşturma ve EDITOR/VIEWER Rol Yönetimi UI Entegrasyonu tamamlandı.
  - Faz 8.4 E12-B — Workspace Üye Rol Değişimi (`UPDATE`) ve Alandan Ayrılma (`DELETE`) V2 Outbox Altyapısı tamamlandı.
  - Faz 8.4 E12-A2 — Davet Kodu ile Workspace'e Güvenli Katılma tamamlandı.
  - Faz 8.3 — Hedefler ve Borçlar (Goals & Debts) başarıyla tamamlandı.
  - Mobil Navigasyon Bilgi Mimarisi (Ana Sayfa, İşlemler, + hızlı eylem, Bütçe, Financial Hub) type-safe route'lar ve pasif Yakında kayıtlarıyla tamamlandı.
  - Faz 8.2 — Tekrarlayan İşlemler ve Abonelikler (Recurring Transactions & Subscriptions) başarıyla tamamlandı.
  - Faz 8.1 — Bütçeler (Budgets) başarıyla tamamlandı.
- Sıradaki öncelik: **Faz 7.3 İşlem ve kategori akışlarının doğrulanması ve açıklarının kapatılması**: Görev 7.3-A tamamlandı (`TransactionSuccessViewModel` geçersiz kimlik koruması, Bütçe detayından ay/tarih aralıklı `TransactionsRoute` navigasyonu, `showConflict` sızıntı koruması ve tüm işlem/taksit/offline akışlarının test kanıtları). Görev 7.3-C1 (Kalıcı makbuz yaşam döngüsü ve saf karar kuralları) tamamlandı. Görev 7.3-D2 (Custom Split Saf Domain, Doğrulama ve Settlement) tamamlandı. Görev 7.3-D3 (Custom Split Room V19 Kalıcılığı ve Migration) tamamlandı. Görev 7.3-D4 (Custom Split DTO, Outbox Sözleşmesi ve RPC) yerel geliştirme kabulü verildi; test koşucusundaki sabit parola fallback'i kaldırılarak libpq/PGPASSWORD standardına geçirildi. Görev 7.3-D5 (Custom Split UI Entegrasyonu) manuel kabul tamamlandı (`docs/MANUEL_KABUL_7_3_D5.md`): CUSTOM + taksit kombinasyonunda sessiz eşit kayıt engellendi, UI ve submit katmanında açıklayıcı Türkçe hatayla (`SPLIT_CUSTOM_NOT_SUPPORTED_WITH_INSTALLMENT`) use case yazma çağrısı üretmeden durduruldu, taksiti kapatma veya eşit dağıtımı seçme imkânı verildi ve paylar otomatik silinmedi; dağıtılan/kalan/fazla tutar gösterimleri `Long` taşması ile `Money.MAX_AMOUNT_MINOR` sınırına karşı güvenli yapıldı, sınır dışı tutarlar `Money` constructor'ına verilmeden açıklayıcı durum metinleriyle sunuldu; "Tam Eşleşti" yalnız tüm seçili paylar eksiksiz ve geçerliyken (boş/geçersiz, non-payer 0 pay ve geçersiz payer durumları hariç) gösterilecek şekilde sıkılaştırıldı, taslak içindeki kişi bazlı hatalar güncel girdilerden anlık hesaplandı (`validateDraftParticipantShares`) ve `customShareErrors` oluştuğunda ayrıntılar akordeonunun otomatik açılması sağlandı; CUSTOM formunda üyelik Flow'unun payer/katılımcıları sessizce değiştirmesi engellendi, yeni form varsayılanı ile kullanıcının veya Room'un dağılımı ayrıştırıldı, üye ayrıldığında seçim ve tutar korunup inaktif (`(Ayrıldı)`) işaretlenerek düzeltme yapılmadan submit edilmesi engellendi. Hedefli birim ve host testleri (`:sharedUI:testAndroidHostTest`, `:androidApp:testDebugUnitTest`, `:sharedLogic:testAndroidHostTest`), iOS simülatör derlemesi (`:sharedLogic:compileKotlinIosSimulatorArm64`), Android debug APK derlemesi (`:androidApp:assembleDebug`), emülatör manuel akışları (Senaryo 1-5), izole enstrümante üyelik ayrılması testi (`CustomSplitMembershipDepartureAndroidTest` - Senaryo 6) ve sınır/düzen erişilebilirlik testleri (`CustomSplitBoundaryAndLayoutAndroidTest` - Senaryo 7) başarıyla tamamlandı. Faz 10 (otomatik test envanteri) ise V1 modül akışları tamamlandıktan sonraki genel kabul fazı olup mevcut aktif görev değildir. Navigation takip listesi: Reports, Kişisel Bilgiler, Hesap, Bildirimler ve Tercihler (Görünüm, Dil ve Bölge, Uygulama Kilidi ve Gizlilik, Veri Yönetimi) tamamlanıp ilgili type-safe rotalara bağlanmıştır; gerçek domain/veri/UI akışı henüz tamamlanmamış olan Transfer hızlı eylemi pasif `Yakında` durumunda tutulmaktadır.
- Production Supabase durumu: migration uygulanmadı.
- Staging: `FeniqoMobil-Staging` (ref: `rxfaiynkhaxrksosxvxp`); `20260921000100_seed_extended_canonical_categories.sql` dâhil tüm migration zinciri başarıyla uygulandı; M1–M4 doğrulaması GEÇTİ (27 aktif sistem kategorisi [9 gelir + 18 gider], 1120/1121 tombstone version=2, 0 katalog dışı fazlalık, kullanıcı kategorileri korundu).

## Tamamlanan fazlar

| Faz | Durum | Çıktı |
|---|---|---|
| 1. Analiz ve kapsam | Tamamlandı | Web envanteri, V1 sınırı, güvenlik ve migration planı |
| 2. Proje omurgası | Tamamlandı | KMP modülleri, bağımlılıklar, tema ve UI kabuğu |
| 3. Domain | Tamamlandı | Temel/ikinci dalga modeller, repository sözleşmeleri ve use case'ler |
| 4. Yerel veri | Tamamlandı | Room v3/v4, DAO/mapper, SQLCipher/Keystore ve outbox |
| 5. Uzak veri | Tamamlandı | Auth, DTO/remote, sync motoru, staging kabulü ve Realtime |
| 6.1 Arka plan sync | Tamamlandı | Hilt CoroutineWorker, BackgroundSyncScheduler, exponential backoff, KEEP / APPEND_OR_REPLACE |
| 6.2 Senkronizasyon gözlemi | Tamamlandı | Room v4 SyncOverview Flow, NetworkConnectivityObserver, ViewModel, SyncStatusIndicator Compose bileşeni |
| 7.1 Navigasyon ve UI durumları | Tamamlandı | Type-safe Navigation Compose rotaları, bağımsız Auth/Main NavHost, RootNavViewModel, stateless FeniqoAppShell |
| 7.2 Giriş, kayıt ve parola kurtarma ekranları | Tamamlandı | Pano A/B/C onaylı tasarımları, LoginScreen, RegisterScreen, WelcomeScreen, AuthEmailVerificationScreen, ForgotPasswordScreen, PasswordResetSentScreen, ResetPasswordScreen, PasswordResetSuccessScreen, doğrulanmış recovery deep link (feniqo://auth/callback), SDK session dönüşümü |
| 7.4 Dashboard | Tamamlandı | Stateless DashboardScreen, DashboardViewModel, Hilt modülleri, dinamik ay Room Flow SSOT, son işlemler, işlem/düzenleme navigasyonları, geçici MoneyScore kartı ve ön değerlendirme |
| 8.1 Bütçeler | Tamamlandı | Bütçe listesi, dinamik ay gezinimi, %80 uyarı ve %100 aşım, harcama kategorisiyle bütçe ekleme, ID tabanlı Room SSOT form düzenlemesi, onaylı silme ve kopyalama akışları, Room V2 outbox/ACK, Staging V2 SQL migration, sözleşme testi ve Android emülatör manuel smoke kabulü |
| 8.2 Tekrarlayan işlemler ve abonelikler | Tamamlandı | Tekrar vade hesaplayıcı, occurrence idempotency, kural/abonelik CRUD komutları, Room v6/v7/v8, V2 outbox/ACK/pull/conflict, Staging 13/13 migration, 38 senaryolu SQL sözleşme testi, MVI Compose liste ve form ekranları, hatırlatıcı planlayıcı, Room receipt claim, Android bildirim Worker'ı, 24h periyodik scheduler, Android 13+ izin CTA'sı ve Android emülatör manuel smoke kabulü |
| Mobil Navigasyon Bilgi Mimarisi | Tamamlandı | 5'li kalıcı alt navigasyon (Ana Sayfa, İşlemler, + hızlı eylem, Bütçe, Daha Fazla), semantic Financial Hub, avatar tabanlı Profil/Hesap ayrımı, type-safe route'lar ve geliştirilmemiş modüller için pasif Yakında durumu |
| 8.3 Hedefler ve borçlar | Tamamlandı | Goals (birikim CRUD, katkı ekleme/çıkarma, ilerleme/tahmini süre), Debts & Receivables (borç/alacak CRUD, ödeme/tahsilat geçmişi, fail-closed reaktif bakiye hesabı), Borç snowball planlayıcısı ve ekranı, Room v9/v10/v11 tabloları, V2 outbox/ACK/pull/conflict sync, Staging 15/15 migration, SQL sözleşme testi (0 kalıntı) ve Android emülatör manuel smoke kabulü |

5.1'de Android için build configuration ve güvenli oturum saklama uygulanmıştır. iOS `.xcconfig`,
Keychain ve üretim güvenlik adaptörlerinin kalan kısmı Android-first kararı gereği 10.4'te tamamlanır.

## Tamamlanan faz: Faz 9.3 — İçe/dışa aktarma ve gizlilik

İlk dilim (2026-09-10): Aktif Room çalışma alanındaki işlemler için güvenli CSV dışa aktarma
tamamlandı. Android sistem dosya seçicisi kullanılır; küçük para birimi korunur, private makbuz
yolu/owner kimliği dışlanır ve elektronik tablo formül enjeksiyonu etkisizleştirilir. Kişisel
kategori/işlem verisi için fail-closed doğrulanan JSON yedek v1 sözleşmesi ve tek Room
transaction'ında atomik, yeni kimlikli içe aktarma tamamlandı. Android dosya seçici 10 MiB sınırı,
kayıt sayısı önizlemesi ve açık onay uygular. Üretim kaynakları ile Edge Function kodunda hassas
log/ağ gövdesi taraması tamamlandı; doğrudan logger bulunmadı, ham exception mesajlarının
repository ve outbox kalıcı hata alanlarına taşınması güvenli kararlı kodlarla kapatıldı. Yirmi
hedefli test ve Android debug APK derlemesi geçti. Faz 9.3 tamamlandı; sıradaki faz 10.1'dir.

## Ertelenen kabul: Faz 9.2 — Makbuz OCR

Amaç: Makbuz görüntüsünü kamera veya galeriden alıp cihaz içinde metne dönüştüren, bulunan tutar/tarih/işyeri adaylarını kullanıcı onayı olmadan finans kayıtlarına yazmayan güvenli OCR akışı oluşturmak.

İlk dilim (2026-09-09): Ham metni saklamayan geçici aday modeli ve `Double` kullanmayan
deterministik parser tamamlandı. Etiketli toplamlar, geçerli tarihler ve düşük güvenli işyeri adayı
ayrıştırılır; para birimi form bağlamından gelir ve tüm adaylar kullanıcı onayına tabidir.

Android akışı (2026-09-09): CameraX arka kamera ve sistem galeri seçimi, bundled ML Kit tanıma,
izin ret/ayarlar geri dönüşü ve ayrı aday onay diyaloğu işlem formuna bağlandı. Geçici kamera
dosyası OCR sonrasında silinir; “Forma Aktar” seçilmeden hiçbir form alanı veya finans kaydı değişmez.

Faz 9.1 kapanış notu (2026-09-09): Ortak `SecuritySettings`, fail-closed kilit politikası, Android Preferences DataStore/Hilt repository, AndroidX `BiometricPrompt`, biyometri + cihaz PIN/desen/parola geri dönüşü ve navigation'dan önce çalışan lifecycle kilit kapısı tamamlandı. Uygulama kilidi SQLCipher anahtarından ayrı kaldığı için arka plan senkronizasyonu korunur. Otomatik testler, debug APK ve kullanıcı Android manuel kabulü geçti.

## Sonraki fazlar

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
