# Changelog

All notable LocalRP changes. The app targets Turkish-speaking users; entries are bilingual when a change matters for English readers.

## v0.11.0 — 2026-10-28

- **Yedekleme ve geri yükleme**: 0.7.4'ten beri beklenen özellik (sözlü verilen taahhüt) tamamlandı:
  - **Görünüm → Veri yedeği** bölümünden, SAF ile seçtiğin herhangi bir klasöre `localrp-yedek-<tarih>.json` dışa aktarımı: tüm karakterler, sohbetler ve tamamı geçmişler tek dosyada. Dosya cihazdan çıkmaz; aktarım yalnızca senin onayınla SAĞLANIR (uygulama hiçbir veriyi otomatik göndermez).
  - **Geri yükleme**: aynı kimlikli karakter/sohbetler güncellenir, yeni olanlar eklenir. Geçmişler yalnızca yedekteki kopya **kesinlikle daha uzun** (gerçek devamlar) olduğunda değiştirilir; kısa/eski yedekler hiçbir şeyi küçültmez, tekrarlı içe aktarım mükerrer çoğaltma yapmaz.
  - **Güvenlik**: mevcut kalıcılık verisi okunamıyorsa geri yükleme reddedilir (startup raporu onarım yolu korunur); bozuk tek kayıtlar (0.7.4 codec'inin aynen davranışıyla) atlanır; 256 MB biyolojik dosya sınırı.
  - Version-1 bundle: `{ characters:[], groups:[], chats:{ "<groupId>":[messages] } }`.
- **5 yeni test** (`PersistenceBundleTest`): bundle round trip, geçersiz dosyalardan (array/version-2/bozuk) reddi, bozuk kayıt atlama, kimliklere göre birleştirme, "yalnızca daha uzun geçmiş değişir" kuralı. Toplam **43 test / 0 hata**.
- versionCode 15 / 0.11.0, user agent 0.11.0.

## v0.10.0 — 2026-10-28

- **Inference runtime güvenilirliği** (Görev 05): motor değiştirilmedi; karar mantığı saf `RuntimePolicy` katmanına taşındı ve kilit bir UI hatası kapatıldı:
  - **Sonsuz meşgul kilidi düzeltildi**: model yükleme/boşaltma sürerken tekrar denenen `unload`/`loadModel` sessizce reddediyordu ve "Model bellekten kaldırılıyor…" göstergesi sonsuza kadar dönüyordu — artık "Önceki model işlemi sürüyor; bekleyin." durumu geri bildirilir.
  - **Reload kararı test edilebilir**: `reloadNeeded()` — kullanıcı sıfırlaması, farklı sohbete geçiş ve (initialized/error/benchmarking) bozuk motor durumları nettir; ProcessingSystemPrompt için bekleyen çağrı reload sayılmaz.
  - **Context bütçesi**: GGUF eğitim uzunluğu (min 512) × 2.5 karakter/token, 8192 motor limitiyle sınırlı, 2048–12000 clamp — aynı sayılar artık 10 testle korundu.
  - **predictLength clamp** (32–2048) politika katmanında; üretime giren her istek sınırlanır.
  - Sohbet/grup RP davranışları (v0.7.3 paylaşılır context kuralı) aynen korunur; agresif bellek optimizasyonu yapılmadı.
- **11 yeni test** (`RuntimePolicyTest`); toplam **38 senaryo / 0 hata**.
- Donanıma bağlı davranışlar (native llama.cpp hata yolları, gerçek token gecikmeleri) JVM'de test edilemez — cihaz testi listesi aşağıdadır.
- versionCode 14 / 0.10.0, user agent 0.10.0.

## v0.9.0 — 2026-10-28

- **GGUF indirme güvenliği ve güvenilirliği** (Görev 04): yeni saf `DownloadPolicy` katmanı (12 JVM testi) ve `ModelDownload` entegrasyonu:
  - **Kör devam etme yasağı**: 206 yanıtı yalnızca Content-Range kelimesi kelimesine "bytes <elimizdekiOfset>-.../../" ile başlıyorsa eklenir; uyumsuz ofset, eksik/bozuk Content-Range veya Range'i yok sayan 200 yanıtı → .part sıfırdan temiz başlatılır (bütün dosya bozulması kapatıldı).
  - **https-only politika**: başlangıç URL'i https olmak zorunda; yönlendirme sonrası nihai URL tekrar denetlenir (0.7.2 davranışı korunup politikaya taşındı); `Accept-Encoding: identity` ile uzunluk belirsizliği azaltıldı.
  - **Dosya adı sertleştirme**: hedef ad yalnızca düz "*.gguf" olabilir — `/`, `\`, `..`, gizli nokta-dosya, kontrol karakterleri ve >96 ad reddedilir (path traversal kapatıldı).
  - **Katalog boyut doğrulaması**: indirme sırasında katalogun güvenilir bayt bilgisi (model.bytes) tamamlanan dosyayla karşılaştırılır; uyuşmazlıkta dosya silinir, kurulum olmaz.
  - **SHA-256 altyapısı**: `sha256Hex` + `shaMatches` hazır; katalog artık sağladığında (şu an hiç sağlamıyor — uydurma hash kabul edilemez) tamamlanan dosya doğrulanır, uyuşmazlıkta dosya silinir.
  - Depolama, iptal/iinterruption ve Content-Length eksikliği davranışları korunarak tek dosyada politika altına alındı.
- **12 yeni test** (`DownloadPolicyTest`): https politikası, dosya adı teması (traversal/dotfile/boş), `parseContentRange` biçim denetimi, 206/200/eksik başlık devam kararları, SHA-256 referans dize testi ("abc" → ba7816bf…), 64 hanelik olmayan beklenen hash reddi. Toplam **27 test / 0 hata**.
- versionCode 13 / 0.9.0, user agent 0.9.0.

## v0.8.0 — 2026-10-28

- **Character.AI / PolyBuzz tarzı arayüz yenilemesi** (Görev 09):
  - **Avatar sistemi**: karakterler ve sohbetler artık programlı avatar kullanıyor — ismin ilk harfi + isimden türetilen iki tonlu gradyan çemberi. Hiçbir görsel APK'ya gömülmedi, hiçbir şey indirilmiyor; açık/koyu tema paletine göre tonlanıyor.
  - **Ana ekran**: karakterler artık 2 sütunlu grid kartlarda ("Keşfet" hissi); son sohbetler kartları cAI tarzı avatar + kalın başlık + tek satır önizleme + zaman gösteriyor.
  - **Sohbet ekranı**: başlıkta sohbet avatarı, karakter mesaj baloncuklarının solunda 32 dp avatar ve aynadaki Sağda saat; kullanıcı baloncukları avatarsız (aynı davranış).
  - **Mesaj baloncukları**: konuşmacı adı aksan rengiyle daha görünür; pad-konumlar cAI ritmine göre yeniden ayarlandı.
  - **Gönderici**: tam genişlik "Gönder" düğmesi yerine pill giriş alanı + 46 dp dairesel ➤ gönder düğmesi; üretim sırasında düğme kemik "■" durdur varyantına dönüyor (aynı davranış, aynı kilit).
  - Tüm özellikler ve gezinme korunuyor; animasyon yok; zaten üretim akışını bozma.
- versionCode 12 / 0.8.0, user agent 0.8.0.

## v0.7.4 — 2026-10-28

- **Persistence reliability** (Görev 03): stored JSON is now decoded through a tested `PersistenceCodec`:
  - **Crash-safe-by-refusal**: if a stored value exists but cannot be parsed as JSON, all writers refuse to touch it (`overwriteAllowed`); the app never replaces unreadable data with an empty default. The startup screen reports the affected section and how to repair it.
  - **Rescue decode**: a broken element inside a stored array no longer makes the whole list unreadable — a token-level salvage pass recovers every still-readable record (storage stays write-locked until repaired).
  - **One bad record survives**: malformed single records are skipped without truncating the list (0.7.2 behavior, kept and now verified by tests).
  - **Write feedback**: saving a character or conversation when data is unreadable now tells the user "verisi okunamıyor" instead of silently doing nothing.
  - **Faster large chats**: conversation transcripts are cached in memory; `messages()` no longer re-parses the whole JSON array on every read.
- **First unit tests added**: `PersistenceCodecTest` (15 scenarios) — legacy round trips, empty data, malformed roots, missing optional fields, rescue behavior, user flags and timestamps. Requires `junit` + `org.json:json` test dependencies (org.json is not on the JVM test classpath otherwise).
- versionCode 11 / 0.7.4, user agent 0.7.4.

## v0.7.3 — 2026-10-28

- **Group roleplay identity fix** (user-reported: the AI sometimes lost track of which character it was playing):
  - On continuing turns, the prompt now re-injects this turn's speaker identity (name + one-line hint) and lists the other characters *by name* with an explicit "do not write as them" instruction. Previously the continuing branch only said "context continues" and the model stayed anchored to the previous speaker's persona in the shared engine context.
  - Chat history in the prompt now labels the user as `Kullanıcı:` instead of the display label `Sen:`, removing an ambiguity that read like a character named "Sen".
  - The global system prompt gained an explicit multi-character turn rule ("write only the requested character's reply").
- versionCode 10 / 0.7.3.

## v0.7.2 — 2026-10-28

- **Download integrity hardened** (Görev 02 implementation phase): the catalog only presents a model as "İndirildi" when the stored file size matches the verified catalog size; a hand-copied or truncated file now shows the "İndir" state instead of a ready model. Redirects to any non-https final URL are refused. The start/cancel race in `ModelDownload` is closed (`cancel()` can no longer miss the just-launched job; a download cancelled in the launch window aborts itself).
- **Corrupt-record isolation** in `Store`: a malformed JSON record inside `chat_<id>` / `characters` / `groups` is now skipped instead of truncating the whole list on next save.
- **Benchmark recovery**: a failed `bench()` no longer strands the engine — best-effort `cleanUp` on the `Benchmarking` state in both the benchmark error path and generation reload path (`needsReload` now includes `Benchmarking`).
- **Honest unload state**: the "model unloaded" bookkeeping no longer parses Turkish status text; it checks runtime state (`isModelReady()` / `loadedModelPath()`), and SAF-picked filenames are capped at 72 chars to avoid filesystem `NAME_MAX` failures.
- Catalog byte sizes corrected for `Gemma-2-2B Q4_K_M` (1 708 582 752) and `Qwen2.5-3B Q4_K_M` (2 104 932 768) against today's live `x-linked-size` values.
- Housekeeping: `styles.xml` static colors now mirror the default Lavanta palette (fixed cold-start first-frame mismatch), removed dead `Design.byIdOrNull`, `LocalRP_Agent_Task_Prompts/` gitignored, empty `dev/` source folder removed, versionCode 9 / 0.7.2.

## v0.7.1 — 2026-10-24

- Model download: `.part` files are kept and **resumed from where they left off** (HTTP Range/206); downloads refuse to run twice (single-flight gate with honest "try again in a second" feedback); the catalog card mounts its progress label and cancel button immediately and updates the percentage in place.
- Cancel semantics hardened: a cancelled download's slot is only released when its writer truly exits; connections are always `disconnect()`ed.
- Repository: Gradle 8.13 wrapper committed and verified (including a from-network build on a fresh cache); private working notes untracked; `ARCHITECTURE.md` added; README states Android 13+, ARM64 and the `com.arm:ai-chat:0.1.0` (Apache-2.0) dependency.
- Small cleanups: dead code removed, error dialog guarded, "İniniyor" → "İndiriliyor".

## v0.7.0 — 2026-10-24

- **Model recommendation center** split into CPU and GPU categories with verified Hugging Face URLs/sizes, per-device memory/cores card and in-app streaming download UI.
- **6 GUI themes** (Lavanta, Okyanus, Orman, Gölge, Deniz Gecesi, Gün Batımı) with instant application and a new "Görünüm" bottom-tab; message bubbles, cards, headers and navigation all follow the palette.
- **Advanced AI settings**: editable system prompt, history message/char budget sliders, auto vs manual context budget, and an auto **character chain** (0–3) with hard caps and per-chat counters.
- Model can now be loaded directly from the app-private storage (no copy step) via `loadStoredModel`.

## v0.6.0

- GGUF model info card (name/architecture/quantization/training context/size), engine benchmark (`bench`), response metrics (duration, tokens, tok/s, context fill).
- Context budget derived from the model's training context (2.048–12.000 chars, engine capped at 8.192 tokens).
- Eight AI scene themes (Serbest → Korku) that steer response atmosphere; theme switches rebuild the model context.

## v0.5.0

- Stable bottom chat composer, dynamic send/stop button, draft protection, message timestamps, long-press copy.
- Group speaker selection: mention-based (Turkish-aware), turn rotation, manual pin ("Sıradaki konuşsun"), auto-chain basics.
- Model copy management, loading progress, error recovery chat-side.
