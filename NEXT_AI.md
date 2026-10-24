# LocalRP — sonraki AI için devir notu

## Proje konumu ve dosyaların yeri

Proje kökü: `/home/teto/Projects/LocalRP` (`~/Projects/LocalRP`). Android modülü `app/` altındadır. Mevcut kaynak ağacı:

```text
LocalRP/
├── AGENTS.md                         # Proje sahibi talimatları; işe başlamadan oku
├── NEXT_AI.md                        # Bu devir notu
├── README.md                         # Genel bakış (TR/EN), derleme ve kullanım
├── LICENSE                           # MIT
├── .gitignore                        # build/, .gradle-home/, .tmp/, local.properties vb.
├── Neon Anime Sohbet Logosu.png       # Kaynak launcher görseli
├── build.gradle                      # Root plugin sürümleri (AGP, Kotlin)
├── settings.gradle                   # Google/Maven Central depoları ve :app modülü
├── gradle.properties                 # Gradle/AndroidX ayarları
├── local.properties                  # Makineye özel Android SDK yolu; değiştirme
└── app/
    ├── build.gradle                  # Android app, SDK, ABI, bağımlılıklar, sürüm
    └── src/main/
        ├── AndroidManifest.xml       # Launcher activity, ikon, INTERNET izni
        ├── java/app/localrp/
        │   ├── MainActivity.java     # Ekranlar, RP akışı, JSON/SharedPreferences deposu
        │   ├── AppTheme.java         # 6 GUI teması (açık/koyu) + palet tokenları
        │   ├── Design.java           # Aktif tema erişicisi + sistem çubuğu boyama
        │   ├── ModelCatalog.java     # Doğrulanmış CPU/GPU GGUF model katalogu
        │   ├── ModelDownload.kt      # Akış indirme: ilerleme, iptal, .part temizliği
        │   └── LocalAiRuntime.kt     # Arm AI Chat/llama.cpp GGUF motoru ve üretim
        └── res/
            ├── mipmap-*/             # Launcher ikonları, density boyutları
            ├── mipmap-anydpi-v26/    # Adaptive launcher icon tanımı
            └── values/               # Tema ve ikon arka plan rengi
```

Karakterler, RP sohbet listesi, mesajlar ve AI tercihleri `MainActivity.java` içindeki `Store`/ayar koduyla `SharedPreferences` (`localrp`) içinde saklanır. GGUF modeli uygulamanın özel dosya alanındaki `models/` klasörüne kopyalanır veya katalogdan doğrudan oraya indirilir. `app/build/` Gradle'ın ürettiği çıktılar içindir; güncel APK `app/build/outputs/apk/debug/app-debug.apk` yolundadır (v0.7.0, 14 MB, SHA-256 `d77b51f9c98684d1871ea050303ddf3e40a8158c7f157072b14aa83b5be40acc`).

## Ürün kararı

Kullanıcıyla Türkçe konuş. Uygulama yalnızca roleplay içindir. Tek insan ve tek botla yapılan bire bir sohbet de RP sayılır; grup RP'si bir veya daha fazla karakter içerebilir. Genel amaçlı serbest AI sohbet modu ekleme. Kullanıcı her mesajdan sonra ayrıca "AI yanıtla" veya karakter seç düğmesine basmamalı; mesaj gönderilince bir RP karakteri otomatik yanıt vermeli. Grup konuşmacı seçimi `chooseNextSpeaker()` içinde: kullanıcının mesajında adı geçen karakter öne alınır (Türkçe karakterlere duyarlı kelime-sınırı araması), aksi hâlde son konuşandan sonraki karakter sırayla seçilir, ilk karakter yedek. `manualSpeaker` tercihi sıradaki konuşmayı elle sabitlemek için "Sıradaki konuşsun" akışından gelir.

## Bu turdaki değişiklikler (v0.7.0)

### Model öneri ve indirme merkezi (CPU/GPU)

- `ModelCatalog.java` eklendi: doğrudan indirilebilir GGUF listesi, CPU ve GPU dengelerine ayrılmış. Tüm Hugging Face URL'leri ve boyutları `Range: bytes=0-0` isteğiyle HTTP 206 olarak doğrulandı (Qwen2.5-0.5B/1.5B/3B, Llama-3.2-1B, Gemma-2-2B Q4/Q5, Phi-3.5-mini, TinyLlama-1.1B).
- Her kart CPU ve GPU hız aralığı rozetleri, bağlam penceresi, dil düzeyi ve niçin önerildiği metnini içerir; her CPU/GPU sekmesinde bir "öneri" kartı vurgulanır.
- `addDeviceCard()` cihaz belleği/boş RAM/çekirdek sayısını gösterir; öneri bunun ışığında işaretlenir.
- `ModelDownload.kt` eklendi: `HttpURLConnection` akış indirme; `.part` dosyasına yazar, fd.sync + rename, iptal desteği, yüzdelik ilerleme, `StatFs` ile depolama kontrolü (128 MB tampon), HTTP kod kontrolü, mobil veri tespiti (`isActiveNetworkMetered`) ve onay diyaloğu.
- Katalog ekranında durum başına davranış: indiriliyor → ilerleme + "İndirmeyi durdur"; indirildi → "Modeli yükle"; tamamlanmamış `.part` kaldıysa yeniden başlatma onayı.
- İndirilen model `LocalAiRuntime.INSTANCE.modelsDirectory()` içinde katalog `fileHint` adlarıyla saklanır; `loadStoredModel(context, file, listener)` dosya kopyalama beklemeden doğrudan özel alandan yükler.
- `attachModel()` ortak yükleme akışı çıkarıldı: önceki modelin bellekten boşaltılması, GGUF metadata okuma, `setSystemPrompt(currentSystemPrompt())` ve onLoaded bildirimi tek yerde; `loadSelectedModel` ve `loadStoredModel` bunu kullanır.
- Manifest'e `INTERNET` izni eklendi. Uygulama indirme dışında hiçbir veri göndermez; çıkarım tamamen çevrimdışıdır.

### GUI tema sistemi ve modern çerçeve

- `AppTheme.java` içinde 6 GUI teması: **Lavanta** (açık), **Okyanus**, **Orman**, **Gölge** (koyu), **Deniz Gecesi**, **Gün Batımı** (koyu). Her tema metin, kart, çizgi, vurgu, tehlike, sohbet balonları ve hero paleti tokenlarını içerir.
- `Design.java` statik erişici (`Design.text()`, `Design.accent()` vb.) ve `applyBars()` (durum/navigasyon çubuğu boyama, koyu ikonlar) sunar; MainActivity onCreate'de `Design.refresh(this)` ile tercih (`ui_theme`) yüklenir.
- MainActivity'deki ~120 sabit renk kullanımı tokenlara taşındı; dosyada `Color.rgb(` artık yok. Sohbet balonları kullanıcı için `bubbleUser/bubbleUserStroke`, karakter için `cardBg/cardStroke` ayırımıyla boyanır.
- Alt gezinme 5 sekme oldu: Ana sayfa, Karakter, Sohbetler, AI, **Görünüm** (◐). Seçili öğe accentSoft yuvarlak rozetle işaretlenir.
- Başlık alanı modernize edildi: sayfa ekranlarında brand satırı + büyük başlık + alt açıklama hizası düzeltildi; geri düğmesi dairesel accentSoft rozet ("‹") oldu; sohbet başlığı tek satırda kısaltılır.
- `showAppearance()` eklendi: tema kartları, palet swatch'ları, "Sahne" mini önizleme ve anında uygulama (Design.refresh + applyBars + ekran yenileme). `ui_theme` SharedPreference'da saklanır, yeniden açılışta korunur.
- `addMessage()`/`renderMessage()` güncellendi: kullanıcı mesajları accent çizgili bubbleUser balonuna, karakter mesajları cardBg kartına boyanır; konuşmacı adı bold-accent, saat satırı korunur.

### Gelişmiş AI ayarları

- `showAiAdvanced()` eklendi ve AI sekmesinden girilir:
  - **Sistem promptu**: motorun global RP rolünü değiştiren, güvenli boş değer → varsayılana dönüş. Kaydet `custom_sys_prompt` tercihine yazılır ve `setCustomSystemPrompt` ile motora taşınır; model reload bağlamı yeniden kurar.
  - **Geçmiş mesaj sayısı** slider (2–14) ve **geçmiş karakter bütçesi** slider (1.200–3.600); `history_depth`/`history_chars` tercihleri okunur; sabit `MAX_HISTORY_MESSAGES`/`MAX_HISTORY_CHARS` yerine `historyDepth()`/`historyCharBudget()` fonksiyonları `appendHistory()`'de kullanılır.
  - **Bağlam davranışı** satırı: Otomatik (modele göre `contextCharBudget()`) ↔ Elle (3.072–12.000 karakter slider) geçiş; `context_budget_chars` ve `setManualContextBudget(-1|chars)`.
  - **Karakter zinciri** slider (0–3): karakter kendi yanıtından sonra sıradaki karakteri otomatik çağırır.
- `maybeBeginChain(group)` eklendi: yalnızca görünür sohbette, durdurulmamış/hatalı olmayan üretimden sonra ve model hazırken `content.postDelayed(700 ms)` ile bir sonraki konuşmacı otomatik çağrılır; `chainCounts` sohbet başına derinlik sayar, `forgetAutoAttempts(chatId)` zincir sayacını da sıfırlar (sendMessage/clearChat).
- API değişiklikleri: `LocalAiRuntime.currentSystemPrompt()`, `setCustomSystemPrompt`, `manualContextBudget`/`setManualContextBudget`, `modelsDirectory(context)`, `loadStoredModel`, `attachModel`; MainActivity `maxHistoryMessages/maxHistoryChars` sabitleri `historyDepth()/historyCharBudget()` oldu.
- Uygulama sürümü **0.7.0 / versionCode 7**.

### v0.6.0 tabanı

- GGUF model bilgi kartı (ad/mimari/quant/bağlam/boyut sınıfı), motor testi `bench(pp=64, tg=16, pl=1, nr=1)`, sohbet üretim ölçümleri (süre/token/tok-s/bağlam %), GGUF eğitim bağlamına göre 2.048–12.000 karakter bağlam bütçesi, 8 AI sahne teması (Serbest…Korku) ve tema değişince bağlam sıfırlama, CancellationException yeniden fırlatma.
- v0.5.0 tabanı: sabit alt giriş alanı, kompakt sohbet başlığı, alt gezinmenin sohbette gizlenmesi, tek dinamik Gönder/Durdur düğmesi, taslak koruma, mesaj saatleri, uzun basınca kopyalama; manuel sonraki konuşmacı, Türkçe duyarlı mention algılama, sıra tabanlı otomatik konuşmacı, sohbet menüsü, geçmiş temizleme, model kopyası yönetimi; model yüklü değilken gönderilen mesajın kaybolmaması.

## Önemli dosyalar

- `MainActivity.java` (~2.300 satır): RP ekranları, katalog/görünüm/gelişmiş AI ekranları, konuşmacı seçimi, zincir, taslaklar, üretim ölçümleri, SharedPreferences verisi ve eski kayıtların göçü. Renklerde Design tokenları kullanılır.
- `AppTheme.java` / `Design.java`: GUI teması veri + global erişim; `applyBars` sistem çubuklarını boyar.
- `ModelCatalog.java`: doğrulanmış model listesi, `cpuRecommendation()` (Llama-3.2-1B Q4_K_M) ve `gpuRecommendation()` (Qwen2.5-0.5B Q4_K_M). CPU tok/s aralıkları llama.cpp kıyılama mantığıyla tahmini; GPU aralıkları `*` işaretli, Vulkan/MLC motorları içindir — bu sürümün motoru llama.cpp CPU'dur.
- `ModelDownload.kt`: ilerleme/iptal/.part temizliği olan akış indirme; `CoroutineScope(SupervisorJob() + Dispatchers.IO)` içinde çalışır, LocalAiRuntime işlerini etkilemez.
- `LocalAiRuntime.kt`: Arm AI Chat/llama.cpp GGUF motoru, model kopyalama/doğrudan yükleme, GGUF metadata okuma, motor testi, üretim ölçümleri, iptal, hata toparlama ve sistem promptu/bütçe ayarları.
- `AndroidManifest.xml`: ikon kaynakları, klavye yeniden boyutlandırma, `INTERNET` izni ve uygulama manifesti.
- `build.gradle`: AGP 8.10.1, Kotlin Android 2.2.0; `app/build.gradle` minSdk 33, targetSdk 36, ARM64, uygulama v0.7.0.

## AI motoru sınırları

- Bağımlılık `com.arm:ai-chat:0.1.0` olarak sabit. Maven Central'da daha yeni bir sürüm bulunamadı; yerel SDK'da NDK olmadığı için yerel kütüphaneler yeniden derlenemiyor.
- `InferenceEngine` yalnızca `loadModel`, `setSystemPrompt`, `sendUserPrompt`, `bench`, `cleanUp`, `destroy` ve `state` sunuyor. **temperature / top-p / top-k dışa açık değil.** Yanıt yönlendirmesi yanıt uzunluğu, karakter promptu, sahne teması ve sistem promptuyla yapılıyor.
- Yerel motor bağlamı 8.192 token. Uygulama karakter bütçesi modelin GGUF eğitim bağlamına göre 2.048–12.000 karakter arasında hesaplanır; elle bütçe bu aralığı 1.024–12.000'e kullanır.
- `bench()` başarısız olursa motor `Benchmarking` durumunda kalabilir; `cleanUp` bu durumda çalışmadığı için tek çözüm uygulamayı yeniden başlatmaktır. Arayüz bu durumda kullanıcıyı bilgilendirir.
- Katalogdaki GPU hızları `*` işaretlidir: bu tahminler Vulkan/MLC tabanlı motorlar içindir ve bundled llama.cpp CPU motoruyla ölçülmez.

## Derleme ve doğrulama durumu

24 Ekim 2026'da v0.7.0 tam derlemesi çevrimdışı önbellekle doğrulandı:

```bash
GRADLE_USER_HOME="$PWD/.gradle-home" TMPDIR="$PWD/.tmp" ~/Android/tools/gradle-8.13/bin/gradle --no-daemon --offline :app:assembleDebug
```

Sonuç: **BUILD SUCCESSFUL**. APK `app/build/outputs/apk/debug/app-debug.apk`: 14.338.287 bayt (~14 MB), debug imzalı, `versionCode 7`, `versionName 0.7.0`, SHA-256 `d77b51f9c98684d1871ea050303ddf3e40a8158c7f157072b14aa83b5be40acc` (aapt ile doğrulandı). Katalog URL'lerinin resumable olduğu HTTP 206 ile app dışında doğrulandı. `:app:lintDebug` çevrimdışı önbellekte `lint-gradle:31.10.1` olmadığı için çalıştırılamıyor.

Gerçek telefonda kurulup açılması, GGUF katalog indirmesi, doğrudan yükleme, motor testi, GUI temalarının taşınabilir cihazda görünümü ve inference performansı henüz doğrulanmadı; son kontrolde `adb devices` boştu. Bu makinede ayrıca test için hazır bir `.gguf` dosyası bulunmuyor. Hedef cihaz Reeder S71, Android 14, ARM64, 12 GB RAM; bu cihaz için CPU önerisi Llama-3.2-1B Q4_K_M, GPU önerisi Qwen2.5-0.5B Q4_K_M.

## Sonraki işler

1. Telefonu USB ile bağlayıp `adb install -r app/build/outputs/apk/debug/app-debug.apk` ile kurmak; açılış ve alt 5 sekme gezinmesini doğrulamak.
2. AI sekmesinden katalog akışını test et: cihaz kartı, CPU/GPU filtreleri, indirme ilerlemesi/iptali, mobil veri onayı, "İndirildi" rozeti ve doğrudan "Modeli yükle" akışı.
3. Görünüm sekmesinden 6 GUI temasını; tema değişiminin kartlarına/balonlara/sistem çubuklarına anında yansıdığını ve seçimin yeniden açılışta korunduğunu doğrula.
4. Gelişmiş AI'dan sistem promptu, geçmiş sliderları, bağlam bütçesi geçişi ve karakter zincirini (0–3) gerçek modelle deney; zincir sırasında durdurma ve hata davranışını kontrol et.
5. Gerçek GGUF modeliyle ilk token süresini, saniyedeki token hızını ve bellek kullanımını ölçmek; katalogdaki tok/s aralıklarını gerçek ölçümlerle güncelle.
6. Yedekleme/geri yükleme ve karakter içe/dışa aktarma eklemek; grup RP için bağlam yönetimini ve konuşmacı modlarını olgunlaştırmak; otomatik konuşmacı seçiminde hafif bir model destekli seçim (mevcut motor üzerinden) düşünülebilir.

Kullanıcının istemediği online sağlayıcıları veya RP dışı sohbet modunu varsayılan olarak ekleme.