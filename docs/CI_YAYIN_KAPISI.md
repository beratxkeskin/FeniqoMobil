# CI ve Yayın Kapısı

Bu belge, `main` dalına girecek değişiklikler için GitHub Actions kalite kapısını ve GitHub
üzerinde yapılması gereken branch protection ayarını tanımlar.

## Otomatik kontroller

`.github/workflows/ci.yml`, `main` hedefli pull request'lerde, `main` push'larında ve manuel
çalıştırmada aşağıdaki zorunlu işleri başlatır:

| Check adı | Runner | Kapsam |
|---|---|---|
| `Source and secret safety` | Ubuntu | Temiz checkout, tracked yerel dosya/anahtar deposu ve bilinen credential kalıbı taraması |
| `Android tests, Room, lint and debug build` | Ubuntu | SharedLogic/SharedUI/Android testleri, Room migration testleri, release lint, debug APK ve build sonrası temiz çalışma ağacı |
| `Android API 35 device release gate` | Ubuntu + KVM | Gradle Managed Device üzerinde SQLCipher açılış/yeniden açılış, Room 1→22 zinciri, FileProvider/avatar izolasyonu, WorkManager benzersiz iş kalıcılığı, bildirim manifest/channel ve kritik Compose cihaz testleri |
| `Offline demo and Android release gate` | Ubuntu | Supabase ayarı olmadan demo APK, eksik production/sürüm yapılandırmasının fail-closed reddi ve sentetik, gizli olmayan değerlerle R8/resource-shrunk unsigned release AAB doğrulaması |
| `iOS common compile` | macOS | `sharedLogic` için iOS Simulator ARM64 ortak kod derlemesi |

CI hiçbir Supabase ortamına migration veya veri mutation uygulamaz. Test ve derleme sırasında
kullanılan publishable değerler açıkça sentetik CI placeholder'larıdır; gerçek production anahtarı
workflow'a veya repository'ye eklenmez. Üretilen unsigned AAB yayınlanmaz ve artifact olarak
saklanmaz. Harici GitHub action'ları hareketli tag yerine doğrulanmış tam commit SHA'larına
sabitlenmiştir; secret tarayıcısı yeni workflow'larda bu kuralın gevşetilmesini de reddeder.

Room schema/migration doğrulaması `:sharedLogic:testAndroidHostTest` içindeki
`MigrationTestHelper` testleriyle çalışır. Gradle sonrasında `git diff` ve `git status` denetimi,
üretilmiş schema veya kaynak değişikliğinin commit dışında bırakılmasını engeller. Temiz checkout
üzerindeki derleme de kodun untracked bir production kaynağına bağımlı olmasını görünür hata yapar.
Release job'undaki `assembleDemo` adımı hiçbir staging/production değişkeni tanımlamadan çalışır ve
çevrimdışı demo varyantının gerçek Supabase yapılandırmasına yeniden bağlanmasını engeller.
Release AAB adımı açık `FENIQO_VERSION_CODE`/`FENIQO_VERSION_NAME` değerleri olmadan fail-closed
durur; R8 mapping dosyasının oluşması küçültme kapısının gerçekten çalıştığını kanıtlar. CI paketi
imzasızdır ve yayın amacı taşımaz.

Android cihaz kapısı API 35 `aosp-atd` sistem görüntüsünü Gradle Managed Device olarak başlatır.
Manuel kabul amacı taşıyan `diagnostic` probe sınıfları CI listesine alınmaz; yalnız sentetik veriyle
deterministik çalışabilen test sınıfları açık bir allowlist üzerinden koşar. Bu kapı gerçek Android
framework/SQLite/Keystore/FileProvider/WorkManager davranışını denetler, ancak fiziksel cihazdaki
OEM davranışı, gerçek process-kill yeniden başlatması ve TalkBack ile insan kabulünün yerine geçmez.

## GitHub branch protection

Workflow repository'ye gönderilip en az bir kez çalıştıktan sonra GitHub'da:

1. `Settings` → `Rules` → `Rulesets` → `New branch ruleset` ekranını açın.
2. Hedef dal olarak `main` seçin.
3. Pull request zorunluluğunu etkinleştirin.
4. `Require status checks to pass` altında yukarıdaki beş check'i zorunlu seçin.
5. `Require branches to be up to date before merging` seçeneğini etkinleştirin.
6. Yönetici bypass'ını yalnız acil durum prosedürü varsa sınırlandırın.

Bu GitHub ayarı repository dosyasıyla zorla açılamaz; repository yöneticisi tarafından bir kez
uygulanmalıdır. Ruleset etkinleştirilene kadar workflow sonuç üretir fakat başarısız bir check'in
merge'i engellemesi GitHub tarafından zorunlu tutulmaz.

## Yerel eşdeğer doğrulama

Windows proje kökünde:

```powershell
.\scripts\ci\check-secret-leaks.ps1
.\gradlew.bat :sharedLogic:testAndroidHostTest :sharedUI:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:lintRelease :androidApp:assembleDebug
.\gradlew.bat :androidApp:releaseGateApi35DebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.feniqo.mobile.data.local.database.EncryptedDatabaseIntegrationTest,com.feniqo.mobile.data.local.database.RoomMigrationChainInstrumentedTest,com.feniqo.mobile.platform.AndroidPlatformReleaseGateTest,com.feniqo.mobile.presentation.transaction.CustomSplitBoundaryAndLayoutAndroidTest,com.feniqo.mobile.presentation.transaction.CustomSplitMembershipDepartureAndroidTest,com.feniqo.mobile.presentation.transaction.TransactionScreenVisualTest"
.\gradlew.bat :sharedLogic:compileKotlinIosSimulatorArm64
```

`lintRelease` ve release paketleme için [Supabase mobil build yapılandırması](SUPABASE_MOBIL_BUILD_CONFIG.md)
belgesindeki production ortam kapısı geçerlidir. Yerel doğrulama production'a bağlanma veya rollout
onayı anlamına gelmez.
