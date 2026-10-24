# LocalRP — Yerel AI Roleplay (Android)

LocalRP; GGUF modellerle **tamamen çevrimdışı** çalışan, Türkçe arayüzlü bir Android roleplay (RP) uygulamasıdır. Karakterlerini oluşturur, tek veya çok karakterli grup RP'leri kurarsın; model dosyaları uygulamanın özel alanında kalır, hikâyelerin telefonda saklanır.

> LocalRP is an offline-first, Turkish-language Android roleplay app running GGUF models locally (llama.cpp). Build characters, start 1-on-1 or group RP, keep everything on-device.

## Öne çıkanlar / Highlights

- **Yerel AI / Local AI**: llama.cpp tabanlı motor (`com.arm:ai-chat`), ARM64 GGUF desteği. Çıkarım tamamen cihazda; sohbet metni hiçbir sunucuya gitmez. Inference is fully on-device; chat text never leaves the phone.
- **Model öneri merkezine** CPU ve GPU kategorileri: her kartta tahmini hız aralığı, bağlam penceresi, dil düzeyi ve "neden önerilir" notu. CPU önerileri llama.cpp CPU ölçütlerine göre, GPU önerileri `*` işaretli olup Vulkan/MLC tabanlı motorlar içindir.
- **Uygulama içi indirme**: Hugging Face'ten doğrudan, iptal edilebilir, ilerleme göstergeli indirme; `.part` dosyası silinmez ve **kaldığı yerden devam eder** (HTTP Range); depolama kontrolü ve mobil veri için onay.
- **Karakter ve grup RP**: kişiye özel tanım/kişilik/örnek diyalog, çok karakterli sohbetler, ad geçince o karakterin konuşması, sıra döngüsü ve elle sabitlenen sıradaki konuşmacı.
- **Detaylı AI ayarları**: sistem promptu, geçmiş mesaj sayısı ve karakter bütçesi, otomatik/elle bağlam bütçesi, karakter zinciri (otomatik devam) sayısı.
- **6 GUI teması**: Lavanta, Okyanus, Orman (açık) · Gölge, Deniz Gecesi, Gün Batımı (koyu) — kartlar, balonlar ve sistem çubukları tek dokunuşla boyanır.
- **RP kontrolleri**: Her an durdurma, yeniden dene, uzun basınca kopyala, sohbet geçmişi temizleme, üretim ölçümleri (süre/token/tok-s/bağlam %), motor testi (`bench`).
- **Gizlilik / Privacy**: İnternet izni yalnızca model indirmede kullanılır; anahtar gömülmez; veri `SharedPreferences` + uygulama içi dosyada kalır.

## Kurulum / Install

**Ön koşullar / Prerequisites**: JDK 17, Android SDK (Platform 36, Build-Tools 36.0.0, platform-tools). Repo, Gradle **8.13** sürümüne sabitlenmiş bir **wrapper** ile gelir; ek Gradle kurulumu gerekmez (ilk `./gradlew` çağrısı dağıtımı bir kez indirir).

> The repo ships with a Gradle wrapper pinned to 8.13; you only need JDK 17 and an Android SDK (Platform 36, Build-Tools 36.0.0).

1. **APK'yi derle / Build the APK**:
   ```bash
   ./gradlew :app:assembleDebug
   # Çevrimdışı derleme (önceden doldurulmuş bir önbellek varsa):
   GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew --no-daemon --offline :app:assembleDebug
   ```
   Çıktı: `app/build/outputs/apk/debug/app-debug.apk`
2. Telefona kur / Install on phone:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Uygulamada **AI sekmesi → Model önerileri**nden bir model indir (öneri: `Llama-3.2-1B Q4_K_M`, ~0,8 GB; daha hızlı seçenek: `Qwen2.5-0.5B`). Sonra aynı ekrandan **Modeli yükle**. İndirme yarıda kesilirse "İndir" düğmesi kaldığı yerden devam eder.
4. Alternatif: kendi `.gguf` dosyanı telefon depolama yöneticinden paylaşarak LocalRP'ye kopyala ("GGUF model dosyası seç") veya `Android/data/app.localrp/files/models/` altına koy.

> Model dosyaları APK'da değildir; özel depolamada saklanır. İlk indirme için bir kez internet gerekir; sonrasında uygulama tamamen çevrimdışı çalışır.

## Kullanım / Usage

- **Ana sayfa**: yeni tek/çok karakterli RP başlat, son sohbetlere dön.
- **Karakter**: oluştur, düzenle, sil; tanım + kişilik + özel talimat + örnek diyalog.
- **Sohbetler**: tüm RP geçmişi; mesaja uzun basınca kopyala.
- **AI**: model seçimi/yükleme, GGUF bilgi kartı, motor testi, model önerileri (CPU/GPU) ve **Gelişmiş AI** (sistem promptu, geçmiş derinliği, bağlam bütçesi, karakter zinciri).
- **Görünüm**: 6 tema arasından anında seçim.
- Sohbette mesaj gönderince karakter otomatik yanıt verir; gruplarda konuşmacı adı geçtiğinde o karakter konuşur veya "Sıradaki konuşsun" ile elle seçilir.

## Mimari / Architecture

| Dosya | Rol |
| --- | --- |
| `MainActivity.java` | Tek activity; tüm ekranlar, RP akışı, konuşmacı seçimi, veri deposu |
| `LocalAiRuntime.kt` | llama.cpp motoru, model yükleme/çıkarma, GGUF metadata, ölçümler |
| `AppTheme.java` / `Design.java` | 6 GUI teması + palet tokenları + sistem çubuğu boyama |
| `ModelCatalog.java` | Doğrulanmış CPU/GPU GGUF katalogu (URL, boyut, öneri rozetleri) |
| `ModelDownload.kt` | Akış indirme: ilerleme, iptal, `.part` temizliği, depolama kontrolü |

Veri modeli `SharedPreferences` içinde JSON; modeller `Android/data/app.localrp/files/models/` altında.

## Sınırlar / Limitations

- Paketli motor temperature/top-p/top-k ayarını dışa açmıyor; yanıt yönlendirmesi prompt ve uzunluk kaynaklı.
- Motor bağlamı en fazla 8.192 token; uygulama Türkçe için ~2,5 karakter/token hesabıyla bütçe koyar.
- GPU hız aralıkları `*` işaretlidir: Vulkan/MLC motorları içindir; paketli motor CPU'dur.
- **Android 15+**: Android 15'te zorunlu hale gelen edge-to-edge modunda sistem çubuklarının renklendirilmesi göz ardı edilir (çizim Android'e ait); arabirim, inset yastıklaması sayesinde çubukların altına girmeden düzgün çizilir. Çubuk boyama davranışının tam çalıştığı sürüm Android 14'tür.
- Veriler (`SharedPreferences` + uygulama dosyaları) cihazda **şifrelenmemiş** olarak saklanır; cihaz kilidi kullanıcıya aittir.

## Klasörler / Folder map

```
app/src/main/java/app/localrp/   Uygulama kodu (tek activity + motor/sağlayıcı sınıflar)
app/src/main/res/                Launcher ikonları ve tema XML'leri
docs/logo-source.png             Launcher ikonunun kaynak görseli
gradle/wrapper/                  Gradle 8.13 wrapper jar/properties (gradlew ile birlikte)
```

## Lisans / License

MIT — bkz. `LICENSE`. GGUF modeller kendi lisanslarına tabidir (Llama, Gemma, Qwen vb. model lisanslarına bak).
MIT licensed; model weights follow their own licenses (Llama, Gemma, Qwen, etc.).
