# Kod Kalitesi ve Artifact Politikası

## Statik kalite kapısı

Kotlin kaynakları her pull requestte `ktlintCheck` ile denetlenir. Mevcut biçim ihlalleri modül
baseline dosyalarında dondurulur; yeni veya değiştirilen kod yeni ihlal ekleyemez. Baseline yalnız
ayrı bir temizlik değişikliğinde, ihlal sayısı azaltılarak güncellenebilir.

Dört yoğun üretim dosyası `scripts/ci/check-source-size-ratchet.ps1` ile mevcut satır sayısında
dondurulmuştur. Bu dosyalar büyütülmez; davranış koruyan, hedefli test içeren küçük dilimlerle
ayrıştırılır. Yeni üretim Kotlin dosyaları 2.200 satırı aşamaz. Bu sınır iyi tasarım hedefi değil,
kontrolsüz büyümeyi durduran üst güvenlik ağıdır.

## Test ve inceleme artifactleri

Git'e yalnız deterministik testlerden üretilen, sentetik veya açıkça maskelenmiş kanıtlar eklenebilir.
Gerçek kullanıcı e-postası, token, parola, finansal payload, makbuz veya kişisel ekran görüntüsü
artifact olarak saklanamaz. Yerel çalışma çıktıları `artifacts/local/` altında tutulur ve commit edilmez.

Takip edilen artifactler yalnız `.png`, `.jpg`, `.jpeg`, `.json`, `.md`, `.txt` veya `.xml` olabilir.
Görseller en fazla 2 MiB, metin tabanlı dosyalar en fazla 256 KiB olabilir. CI, takip edilen dosyaları
hassas veri kalıpları ve boyut sınırları için `scripts/ci/check-artifact-policy.ps1` ile denetler.

Bir artifact güncellendiğinde inceleyen kişi verinin sentetik/maskeli olduğunu ve eski kanıtın hâlâ
gerekli olup olmadığını kontrol eder. Geçici cihaz çıktıları ve manuel tanı dosyaları sürüm geçmişine
alınmaz.
