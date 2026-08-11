# Android Yerel Veritabanı Güvenlik ve Kurtarma Planı

## Amaç ve kapsam

FeniqoMobil'in Android Room veritabanı SQLCipher ile tamamen şifrelenir. SQLCipher'ın
32 baytlık rastgele parolası kaynak kodda, Gradle yapılandırmasında veya düz metin
depolamada tutulmaz. Parola, Android Keystore içindeki dışarı aktarılamayan 256 bit
AES anahtarıyla `AES/GCM/NoPadding` kullanılarak korunur.

Keystore ve SQLCipher yalnızca Android katmanındadır. Ortak KMP domain modelleri,
DAO'lar ve repository sözleşmeleri platform güvenlik API'lerini bilmez.

## Saklanan parçalar

| Parça | Konum | Yedeklenir mi? |
|---|---|---|
| SQLCipher veritabanı | Uygulamanın private database dizini | Hayır; Android backup kapatıldı |
| Keystore AES anahtarı | `AndroidKeyStore` | Hayır; anahtar malzemesi dışarı aktarılamaz |
| Şifreli SQLCipher parolası ve IV | `noBackupFilesDir` | Hayır |

## Yaşam döngüsü senaryoları

### İlk kurulum

1. Kriptografik olarak rastgele 32 bayt SQLCipher parolası üretilir.
2. Android Keystore'da uygulamaya özel AES-GCM anahtarı oluşturulur.
3. SQLCipher parolası bu anahtarla şifrelenip atomik olarak `noBackupFilesDir` altına yazılır.
4. Room, `SupportOpenHelperFactory` aracılığıyla şifreli dosyayı açar.

### Uygulamanın kaldırılması

Android uygulama private dosyalarını ve uygulamaya ait Keystore girdisini siler.
Yeniden kurulum yeni bir yerel veritabanı ve yeni anahtar oluşturur. Kullanıcı giriş
yaptıktan sonra sunucuyla senkronize edilmiş veriler yeniden indirilebilir. Sunucuya henüz
gönderilmemiş outbox kayıtları uygulama kaldırıldığında kurtarılamaz.

### Cihaz değişimi veya sistem yedeğinden geri yükleme

Keystore anahtarı cihazlar arasında taşınamaz. Kullanılamayan şifreli DB'nin yeni
cihaza kopyalanmasını önlemek için `android:allowBackup="false"` kullanılır. Yeni cihaz,
başarılı oturum açma sonrasında Supabase'i kaynak alarak yerel cache'i yeniden kurar.

### Anahtar veya anahtar zarfı kaybı/bozulması

Var olan DB dosyası varken Keystore girdisi ya da anahtar zarfı bulunamazsa uygulama
sessizce yeni parola üretmez ve DB'yi silmez. `DatabaseKeyUnavailableException` ile
güvenli biçimde durur. AES-GCM etiketi, zarfın değiştirilmesini veya yanlış anahtarı
tespit eder.

Kurtarma ekranı ileride kullanıcıya şu iki bilgiyi açıkça göstermelidir:

- Sunucuya ulaşmamış yerel değişiklikler kaybolabilir.
- Yerel veritabanı sıfırlanırsa oturum açıldıktan sonra sunucu verileri yeniden indirilir.

Silme ve yeniden kurma ancak kullanıcının açık onayıyla yapılır.

### Biyometrik kilit

Keystore sarmalama anahtarı kullanıcı kimlik doğrulamasına bağlı değildir. Bunun
nedeni WorkManager tabanlı arka plan senkronizasyonunun ekran kapalıyken de DB'ye
erişebilmesidir. BiometricPrompt uygulama arayüzüne erişimi kilitleyen ayrı bir katmandır.

## Doğrulama ölçütleri

Android cihaz testi aşağıdakileri birlikte doğrular:

1. Oluşan DB dosyası düz SQLite `SQLite format 3` başlığını taşımaz.
2. Keystore tarafından korunan aynı parola ile DB yeniden açılabilir.
3. Yanlış parola ile DB açılamaz.

Test kendine özel DB adı ve Keystore alias'ı kullanır; üretim verisine dokunmaz.
