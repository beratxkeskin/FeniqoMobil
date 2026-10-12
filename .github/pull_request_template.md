## Değişiklik özeti

- Ne değişti:
- Kullanıcı/ürün etkisi:

## Doğrulama

- [ ] Değişen iş kuralı için hedefli test eklendi veya güncellendi.
- [ ] Yalnız riskle orantılı test ve kalite kontrolleri çalıştırıldı.
- [ ] `git diff --check` temiz.
- [ ] Secret, yerel yapılandırma ve build çıktısı eklenmedi.

## Localization kontrolü

- [ ] Yeni kullanıcı metinleri aynı değişiklikte TR ve EN kaynaklarına eklendi.
- [ ] Kotlin/Swift koduna hardcoded kullanıcı metni eklenmedi.
- [ ] ViewModel/domain hazır kullanıcı cümlesi veya önceden biçimlendirilmiş değer üretmiyor.
- [ ] Kullanıcı metni navigasyon kimliği, karşılaştırma değeri veya iş kuralı olarak kullanılmıyor.
- [ ] `contentDescription`, snackbar, toast, dialog, bildirim ve boş/hata durumları kapsandı.
- [ ] Tarih, sayı, para, yüzde ve çoğullar locale-aware biçimlendiriliyor.
- [ ] Değişen kullanıcı yüzeyi hedefli TR→EN testiyle doğrulandı veya neden gerekmediği açıklandı.
- [ ] `scripts/ci/check-localization-policy.ps1` başarıyla geçti.

## Etki sınırı

- Room/migration etkisi:
- Supabase/staging/production etkisi:
- Manuel kalan işlem:
