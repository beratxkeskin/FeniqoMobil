# Görev 7.3-D5 (Custom Split UI Entegrasyonu) — Kabul Raporu

**Tarih:** 2026-09-19
**Değerlendirme Statüsü:** Manuel Kabul Tamamlandı
**Hedef Cihazlar / Test Ortamları:**
- Pixel 8 AVD (Android 14, API 34)
- Samsung Galaxy A71 (SM-A715F, Android 13)
- İzole SQLite Room Memory Veritabanı & Instrumentation Pipeline (`connectedDebugAndroidTest`)

---

## 1. Yönetici Özeti

Görev 7.3-D5 kapsamında geliştirilen Custom Split UI entegrasyonu, mimari kurallar ve sözleşmeler doğrultusunda hem demo/emülatör kullanıcı etkileşimleri hem de izole Android enstrümante test fikstürleri üzerinden test edilmiştir.

Üretim repository güvenlik kontrollerine dokunulmadan, gerçek **Room Flow → ViewModel → Compose** reaktif hattında canlı üyelik değişimleri, sınır değerleri ve kullanıcı etkileşimleri doğrulanmıştır.

Tüm senaryoların test kanıtları, test çıktıları ve ekran görüntüleri aşağıda sunulmuştur.

---

## 2. Senaryo Sonuçları

### Senaryo 1: 100 TL Ortak Gideri 60/40 Dağıtma, Kaydetme ve Yeniden Açma
- **Durum:** GEÇTİ
- **Doğrulama:** 100 TL ortak gider formunda "Özel Tutar" modu seçilerek 60 TL ve 40 TL paylar girildi, "✓ Tam Eşleşti" rozeti görüldü, işlem kaydedildi ve işlem listesinden yeniden açıldığında 60/40 pay dağılımının Room SSOT üzerinden eksiksiz yüklendiği teyit edildi.
- **Kanıtlar:**
  - `scenario_1_saved_transaction.png`
  - `scenario_1_reopened_form.png`
  - `scenario_1_reopened_details.png`

---

### Senaryo 2: Yalnızca Açıklamayı Değiştirip Kaydetme (Payların Korunması)
- **Durum:** GEÇTİ
- **Doğrulama:** Yeniden açılan 60/40 ortak harcamanın yalnızca başlığı/açıklaması değiştirilerek kaydedildi. Kayıt sonrasında işlem detayları tekrar açıldığında özel payların (60/40) bozulmadan ve eşit dağıtıma dönüştürülmeden korunduğu doğrulandı.
- **Kanıtlar:**
  - `scenario_2_title_edited_saved.png`
  - `scenario_2_reopened_shares_preserved.png`

---

### Senaryo 3: Ayrıntılarda Payları Değiştirip İptal Etme (Ana Formun Korunması)
- **Durum:** GEÇTİ
- **Doğrulama:** Ayrıntılar akordeonunda paylar değiştirilip form uygulanmadan "Vazgeç" / Geri butonuna basıldığında, ana formdaki taslağın bozulmadığı ve önceki geçerli payların aynen korunduğu doğrulandı.
- **Kanıtlar:**
  - `scenario_3_details_cancelled_main_preserved.png`

---

### Senaryo 4: CUSTOM + Taksit Kombinasyonunda Kaydın Engellenmesi ve Düzeltme Aksiyonları
- **Durum:** GEÇTİ
- **Doğrulama:** Özel tutarlı bölüşüm seçiliyken taksit etkinleştirildiğinde submit engellendi ve `SPLIT_CUSTOM_NOT_SUPPORTED_WITH_INSTALLMENT` hatası gösterildi. Sunulan aksiyonlarla hem "Eşit Paylaşım"a geçerek hem de "Taksiti Kapat" seçeneğiyle paylar silinmeden formun düzeldiği ve kaydedilebildiği doğrulandı.
- **Kanıtlar:**
  - `scenario_4_custom_installment_error.png`
  - `scenario_4_equal_recovery.png`
  - `scenario_4_installment_disabled_recovery.png`

---

### Senaryo 5: Payer Değişiminde Eski Payer'ın Sıfır Payının Hata Göstermesi
- **Durum:** GEÇTİ
- **Doğrulama:** Payer 0 TL pay alabiliyorken, payer başka bir kullanıcı olarak değiştirildiğinde eski payer'ın 0 TL payı için anında `SPLIT_CUSTOM_NON_PAYER_ZERO_SHARE_NOT_ALLOWED` ("Yalnızca harcamayı ödeyen kişi 0 ₺ pay alabilir") doğrulama hatası verildi ve submit bloklandı.
- **Kanıtlar:**
  - `scenario_5_zero_share_error_on_payer_change.png`

---

### Senaryo 6: Yerel Test Fikstüründe Üyelik Ayrılması (İzole Room Flow → ViewModel → Compose)
- **Test Sınıfı:** `com.feniqo.mobile.presentation.transaction.CustomSplitMembershipDepartureAndroidTest`
- **Koşu Sonucu:** 2/2 Test Başarılı (Pixel 8 AVD, API 34 — 0 Hata, 0 Atlama)
- **Doğrulanan Akışlar:**
  1. **Katılımcı Ayrılması (`participantDeparture_preservesShares_removesBalancedBadge_andBlocksSubmit`):**
     - Form açıkken izole Room veritabanında katılımcı üye (`user-2`) soft-delete ile ayrıldı olarak işaretlendi.
     - Reaktif Flow zincirinde paylar (60 TL ve 40 TL) silinmeden korundu.
     - `✓ Tam Eşleşti` rozeti anında kayboldu.
     - Katılımcı yanında kırmızı `Ayrıldı` rozeti ve `"Pay sahibi çalışma alanında aktif bir üye olmalıdır."` uyarısı belirdi.
     - Dağıtım özetinde `"Aktif olmayan üye var"` ve `"Seçilen bazı katılımcılar çalışma alanında aktif üye değil."` mesajı çıktı.
     - `"Kalan tutarı ödeyene aktar"` butonu kilitlendi (`disabled`).
     - Form submit engellendi, Room'a hiçbir işlem yazılmadı ve `SPLIT_CUSTOM_MEMBER_NOT_ACTIVE` hatası verildi.
  2. **Ödeyen (Payer) Ayrılması (`payerDeparture_preservesShares_removesBalancedBadge_andBlocksSubmit`):**
     - İzole Room veritabanında ödeyen üye (`user-2`) soft-delete ile ayrıldı yapıldı.
     - Reaktif hatta paylar (60/40) korundu.
     - `✓ Tam Eşleşti` rozeti kayboldu.
     - Ödeyen üzerinde hem `Ödeyen (Zorunlu)` hem `Ayrıldı` rozetleri görüntülendi.
     - Dağıtım özetinde `"Aktif olmayan üye var"` ve `"Ödeyen ve bazı katılımcılar çalışma alanında aktif üye değil."` uyarısı belirdi.
     - Kalan aktarma butonu devre dışı bırakıldı ve submit engellendi (`splitError = SPLIT_CUSTOM_MEMBER_NOT_ACTIVE`).
- **Kanıtlar:**
  - `scenario_6_before_departure_balanced.png`
  - `scenario_6_participant_departed_evidence.png`
  - `scenario_6_payer_departed_evidence.png`

---

### Senaryo 7: Sınır Değerleri ve Düzen Erişilebilirliği (3 Ayrı Alt Sonuç)

#### 7.a. Tek Geçersiz Büyük Giriş
- **Durum:** GEÇTİ
- **Doğrulama:** Pay tutarına `999999999999999` girildiğinde `NumberFormatException` veya çökme yaşanmadan anında `Desteklenen para sınırı aşıldı` hatası gösterildi.
- **Kanıt:** `scenario_7_large_amount_overflow.png`

#### 7.b. İki Geçerli Payın Toplam Sınırını Aşması
- **Test Metodu:** `scenario7b_twoIndividuallyValidShares_exceedingCombinedLimit_showsWarningWithoutCrash` (`CustomSplitBoundaryAndLayoutAndroidTest`)
- **Durum:** GEÇTİ
- **Doğrulama:** İki üyeye ayrı ayrı geçerli `5.000.000.000.000` TL (5 trilyon TL; her biri `Money.MAX_AMOUNT_MINOR` sınırından küçük) girildi. İkisinin toplamı 10 trilyon TL olup güvenli kuruş sınırını aştığında:
  - Çökme yaşanmadı.
  - Özet alanında `Dağıtılan: Desteklenen para sınırı aşıldı` ve `Desteklenen para sınırı aşıldı (Fazla)` durum uyarıları gösterildi.
  - Tutar güvenli olmayan `Money` nesnesine dönüştürülmeden arayüz korundu.
- **Kanıt:** `scenario_7_two_shares_sum_overflow_instrumented.png`

#### 7.c. Uzun Üye Listesi + Açık Klavye Etkileşimi
- **Test Metodu:** `scenario7c_twelveMembersList_accessibleWithKeyboardAndActions` (`CustomSplitBoundaryAndLayoutAndroidTest`)
- **Durum:** GEÇTİ
- **Doğrulama:** 12 kişilik üye fikstürüyle form açıldı.
  - Liste kaydırılarak son (12.) üyenin pay alanına (`Kullanıcı user-12 pay tutarı`) erişildi ve odaklanıldı.
  - Sayısal klavye açıkken `WindowInsets.ime` padding'i sayesinde:
    - `"Kalan tutarı ödeyene aktar"` butonunun ve `"Vazgeç"` ikincil butonunun klavye altında kalmadan tam görünürlük ve erişilebilirlik sınırında olduğu,
    - `"Ayrıntıları uygula"` birincil butonunun ise tıklandığı ve formu başarıyla uyguladığı kanıtlandı.
- **Kanıt:** `scenario_7_twelve_members_keyboard_accessible_instrumented.png`

---

## 3. Test İcra Özeti

```powershell
# Senaryo 6
.\gradlew.bat :androidApp:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.feniqo.mobile.presentation.transaction.CustomSplitMembershipDepartureAndroidTest"
# Çıktı: BUILD SUCCESSFUL in 1m 14s (Pixel_8(AVD) - 2 passed, 0 failed)

# Senaryo 7.b & 7.c
.\gradlew.bat :androidApp:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.feniqo.mobile.presentation.transaction.CustomSplitBoundaryAndLayoutAndroidTest"
# Çıktı: BUILD SUCCESSFUL in 2m 10s (Pixel_8(AVD) & SM-A715F - 2 passed, 0 failed)
```

## 4. Sonuç ve Statü

Görev 7.3-D5 için tüm birim, mimari sözleşme, Room SSOT akışları, emülatör manuel doğrulamaları ve enstrümante Android Compose testleri başarıyla tamamlanmış, kanıt açıkları giderilmiş ve statü **Manuel Kabul Tamamlandı** olarak kapatılmıştır.
