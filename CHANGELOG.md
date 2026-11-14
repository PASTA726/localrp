# Changelog

All notable LocalRP changes. The app targets Turkish-speaking users; entries are bilingual when a change matters for English readers.

## v0.30.00 — 2026-11-14 (Stabil · optimizasyon · rehber) — İLK STABİL YAYIN

Alfa döneminin kapanışı: sıcak yol optimizasyonu, AI rehberi ve stres testleriyle ilk **stabil** yayın.

- **Sıcak yol optimizasyonu (`ResponseCleanup`)**: akış sırasında **her parçada** çalışan temizleyici, her çağrıda konuşmacı etiketi regex'ini ve boş satır desenini yeniden derliyordu — artık konuşmacı başına derlenmiş regex önbelleği (sınırlı, 8 kayıt + boşaltma) ve tek örneklenmiş sabit desen kullanır. Uzun sohbetlerde parça işleme maliyeti düşer.
- **AI rehberi ("AI için daha kolay yer")**: "AI ayarları" sayfasının tepesine "AI'nın yanıtını ne şekillendirir?" haritası — karakter kartı, düşünme modu, sahne hafızası, hafızaya eklenen olaylar, dünya kitabı ve örnekleyicinin sırasıyla etkisi. Kullanıcı hangi düğmenin neyi değiştirdiğini tek bakışta görür.
- **Stres/stabilite testleri (3 yeni)**: 500 akış parçası (doğruluk + hız tavanı), 40 farklı konuşmacıyla önbellek boşaltma döngüsü (sonuçlar bozulmaz), tek yanıtta 200 think bloğu (kötü ihtimal: karesel substring endişesi — bütçede kalır).
- Toplam **216 test / 0 hata**. versionCode 63 / 0.30.00 — **stabil** etiketiyle yayınlanır (pre-release değil).

## v0.29.00-alpha1 — 2026-11-14 (Web araması · kullanıcı dokunuşuyla bağlam)

"Think yeteneği + web arama" hedefinin ikinci parçası: sohbet composer'ına 🌐 düğmesi — kullanıcı yazdığı sorgu DuckDuckGo lite üzerinden çekilir, sonuçlar seçilip **tek seferlik bağlam bloğu** olarak sıradaki yanıta verilir.

- **Kullanıcı dokunuşuyla çalışır, gizlilik**: uygulama kendi kendine hiçbir şey çekmez/göndermez; sorguda sohbet metni taşınmaz. Düğmeye basılmadıkça davranış eskisi gibidir.
- **Saf ayrıştırıcı (`WebSearch.java`)**: DDG lite HTML → başlık + özet + URL; en fazla 5 sonuç, özet 200 krk, bağlam bloğu 800 krk tavan; `uddg=` sarmalayıcı çözülür; `javascript:`/bozuk girdi atlanır. JVM'de test edilir (6 yeni test: ayrıştırma, 5 tavanı, çöp girdi, özet kırpma, blok bütçesi, URL çözümü).
- **Tek seferlik enjeksiyon**: sonuç (tek ya da tümü) `pendingWebSearch` haritasına girer; üretim yolu onu tükettikten sonra kaybolur — yönlendirme notuyla aynı disiplin. Yeni kullanıcı mesajı ya da notlu yeniden üretim bekleyen bloğu düşürür (bayat bağlam sızıntısı yok).
- **Yerel + sağlayıcı**: blok prompt'a "Web arama sonuçları (kullanıcı bu yanıt için çekti; doğal biçimde kullan, uydurma)" başlığıyla girer; sağlayıcı yolunda sistem prompt'una biner.
- **Ağ**: `HttpsURLConnection`, yalnızca https, 10 sn bağlantı/okuma zaman aşımı, LocalRP User-Agent; herhangi bir hata "Sonuç yok" bildirimiyle affedilir (asla çökmez).
- Toplam **213 test / 0 hata**. versionCode 62 / 0.29.00-alpha1.

## v0.28.00-alpha1 — 2026-11-13 (Think yeteneği · konu kayması düzeltmesi)

"AI çok hızlı konuyu kaybediyor" şikâyetine iki yönlü çözüm: **düşünme modu** (prompt düzeyi konu çapası) ve `think` etiket temizliği.

- **Düşünme modu (varsayılan açık)**: Model, görünür yanıtı yazmadan önce kısa bir sessiz akıl yürütme bloğu (`think`…`/think`) yazar — sahne nedir, son olaylar neydi, hangi konu sürüyor, karakterin bu turdaki amacı ne — ve yanıtını bu konuya sıkı şekilde bağlar. Blok hem taze hem devam turlarında prompt'un tepesine girer (konu kayması sohbet ortasında olur); yerel motor ve online sağlayıcı yollarının ikisinde de çalışır.
- **`think` etiket temizliği (`ResponseCleanup`)**: Kendi akıl yürütme bloğu üreten modellerin (Qwen3, R1 distill) çıktısındaki kapalı `think`…`/think` blokları görünür yanıttan her konumda silinir; **kapanmamış** etiket (akış ortasında kesilmiş düşünce) etiketten itibaren her şeyi gizler — akıl yürütme sızıntısı balonlara asla düşmez. Akış görüntüsü ve saklanan mesaj aynı temizliği kullanır.
- **Ayar arayüzü**: "AI ayarları" sayfasına "Düşünme modu" bölümü (aç/kapat; neden/açıklama metniyle). Genel pref (`think_mode`), sohbet başına geçersiz kılma sonraki partilere.
- Yeni testler (5): think bloğu temizliği (kapalı/çoklu/çok satırlı/kapalı-artık-yok), kapanmamış etiket davranışı (tamamı gizlenir, kısmi çift silinmez), etiket sonrası yankılanan konuşmacı etiketi, prompt'ta think bloğunun varlığı/yokluğu (iki dil × taze/devam).
- Toplam **207 test / 0 hata**. versionCode 61 / 0.28.00-alpha1.

## v0.27.00-alpha1 — 2026-11-13 (test derinleştirme · kenar durumları)

"Daha iyi testlerle dene" partisi: mevcut 192 testin karartmadığı kenar durumlarına odaklanıp 10 yeni test eklendi — testler **2 gerçek davranış kusuru** yakalayıp düzeltti.

- **SpeechText (TTS bölme) — 2 düzeltme**: (1) nokta/ünlem ünlem işaretinden sonra boşluk yoksa artık bölünmez — "3.14", "github.com/a" gibi terimler ortadan bölünmüyordu (boşluklu sınır kuralı); (2) boşluksuz taşan cümlede sabit kesim noktası bir emoji'yi (surrogate pair) ortadan bölebiliyordu — kesim artık çifti korur. Yeni testler: ondalık/URL bütünlüğü, emoji taşması (ayrık surrogate yok, içerik kaybı yok), boşluksuz taşma.
- **RoleplayPrompt — 1 düzeltme**: geçmiş karakter bütçesi tek bir satıra bile yetmediğinde "Yakın sahne geçmişi" başlığı boş olarak prompt'a girebiliyordu (token israfı + model kafa karışıklığı) — başlık artık yalnızca en az bir satır sığdığında girer. Yeni testler: depth 0 (sağlayıcı modu), küçük bütçe.
- **PersistenceBundle — unicode yuvarlama**: emoji, Türkçe, CJK, satır sonu, tırnak ve ters bölü karakterlerinin karakter/yedek alanlarının tamamında hayatta kaldığı doğrulandı (kendi bozulma taramalarımızın risk yüzeyi).
- **ChatMemory — 3 test**: Türkçe tokenizasyon + stopword filtresi, bütçe sınırı davranışı, bütçeyi aşan (yalnızca decode yoluyla girebilen) kaydın atlanıp küçük adayın yine de prompt'a girmesi.
- **OpenAiCompat — egzotik SSE yükleri**: boş/eksik choices, sayısal/boolean content, JSON null message.content, unicode escape'li content — hiçbiri çökmez, metin olarak güvenli işlenir.
- Toplam **202 test / 0 hata** (10 yeni). versionCode 60 / 0.27.00-alpha1.

## v0.26.00-alpha1 — 2026-11-13 (kullanım kolaylığı + ikon + tam kod tabanı bug avı)

- **Uygulama ikonu yenilendi**: palyaço konsepti korunarak küçük boyut okunabilirliği geçişi — daha kalın yüz detayları, %15 büyütülmüş trajedi maskesi, daha güçlü sahne ışığı. Yeni **monokrom katman** (themed icon): Android 13+ duvar kağıdına göre renklenen tema ikonu artık destekleniyor (minSdk 33).
- **Sohbet listesi canlı arama**: RP adına veya sohbetteki karakter adına göre anlık filtre (karakter adıyla da bulunur); eşleşme yoksa yönlendirici boş durum.
- **Sohbet kartı uzun basma hızlı menüsü**: Sohbeti aç / RP ayarları / Grubu sil — sohbeti açmadan silme/yeniden adlandırma keşfedilebilirliği. Silme akışı (duraklat + anlık görüntü + geri alınabilir sil) grup editörüyle paylaşılan tek metoda çıkarıldı (`deleteGroupWithUndo`).
- **Boş sohbet ipucu**: mesaj menüsü (kopyala/düzenle/hafızaya ekle/sesli oku) keşfedilemez bir uzun basma olduğundan boş sohbet durumunda adıyla belirtiliyor.
- **Codex tam kod tabanı bug avı** (v0.21–v0.25 sınıfları arası etkileşimler dahil): 1 yüksek bulgu → düzeltildi. **Yedek geri yükleme** bozuk yerel sohbet kaydını, kurtarılan/kısmi çözümle sessizce üzerine yazabiliyordu; artık bozuk kayıt yalnızca kesinlikle daha uzun gelen bir yedekle onarılır, aksi halde ham veri korunur ve bütünlük raporuna girer. Karar mantığı saf `PersistenceCodec.mergeStoredTranscript` olarak çıkarıldı ve testlendi.
- 1 yeni birim testi (`mergeStoredTranscript`: temiz/bozuk/kurtarılmış kayıt + gelen yedek kombinasyonları) → toplam 192.
- versionCode 59 / 0.26.00-alpha1.

## v0.25.00-alpha1 — 2026-11-13 (gelişmiş prompt kontrolü · sampler + karakter başına ayar)

P2'nin son kalemi. Not: harici llama.cpp motor kütüphanesi (`com.arm.aichat`) sampler API'si sunmuyor — bu ayarlar **sağlayıcı yolunda** geçerlidir; yerel motor mevcut davranışını korur. Instruct şablon değiştirme de motor desteği gerektirdiğinden sonraki partilere bırakıldı (mevcut prompt kaynakları: genel talimat özel metni + karakter başına talimatlar + sohbet profili şablonu zaten genişletilebilir özelleştirmeyi kapsıyor).

- **Örnekleyici (global)**: AI gelişmiş ayarlarında genel sıcaklık (0.0–2.0) ve top-p (0.0–1.0) — ayarlanmamışsa alan istek gövdesine hiç girmez, sağlayıcının kendi varsayılanı geçerli kalır.
- **Sohbet profili örnekleyicisi**: her sohbet için sıcaklık/top-p override'ı + "genel ayara döndür" + "tüm profili sıfırla" bu alanları da sıfırlar.
- **Karakter başına üretim ayarı**: karakter editöründe isteğe bağlı sıcaklık — o karakter konuşurken sohbet profilini/geneli geçer. `CharacterProfile`'a yeni alan (`samplerTemp`), yedek v2'ye opsiyonel alan olarak taşınır (eski yedekler uyumlu).
- **Çözümleme sırası** (saf, testli): karakter > sohbet profili > genel > (yok) sağlayıcı varsayılanı. Geçersiz/aralık dışı değerler sessizce yok sayılır.
- İstek gövdesi yalnızca ayarlı alanları taşır (`temperature`, `top_p`) — mevcut sağlayıcı davranışı değişmez.
- 4 yeni birim testi (`SamplerSettings` 3, `OpenAiCompat` sampler 1; ayrıca `PersistenceCodec` yuvarlama testi genişletildi) → toplam 191.
- versionCode 58 / 0.25.00-alpha1.

## v0.24.00-alpha1 — 2026-11-13 (görsel anlama · sağlayıcı yolu)

P2 önceliğinin ilk partisi: mesaja eklenen fotoğraf, vision destekli OpenAI-uyumlu modellere gerçek görsel olarak gider.

- **Sağlayıcıya görsel gönderimi**: son kullanıcı mesajına ekli fotoğraf, base64 JPEG veri URL'si (≤1024px, kalite 80 — istek gövdesini makul tutar) olarak son `user` turuna biner; istek gövdesi OpenAI vision biçimini kullanır (`content` dizisi: `text` + `image_url` parçaları). Metin turları değişmez — eski uyumluluk korunur. Geçmişteki eski görseller yer tutucu kalır (token ekonomisi).
- **Anahtar**: Sağlayıcı ayarlarında "Görselleri sağlayıcıya gönder" (varsayılan açık). Kapalıyken fotoğraflar eski `[görsel]` notu olarak gider, hiçbir resim verisi gönderilmez. Model vision desteklemiyorsa sağlayıcının hata mesajı açıkça görünür.
- **Gizlilik**: görseller yalnızca sağlayıcı zaten açıkken ve mesajla birlikte gönderilir; hiçbir resim otomatik dışarı çıkmaz. Yerel (llama.cpp) yolda davranış değişmez — yerel vision (mmproj'lı GGUF'lar) ileri bir parti.
- **Kodlanamayan görsel** (bozuk dosya/okunamayan yol): tur metin-only gider — üretim başarısızlığa düşmez.
- 3 yeni birim testi (`OpenAiCompat`: vision content parçaları, boş veri URL'si, trailing görsel yerleşimi) → toplam 187.
- versionCode 57 / 0.24.00-alpha1.

## v0.23.00-alpha1 — 2026-11-13 (gelişmiş hafıza)

P1 önceliği: hikâye ne kadar uzarsa uzasın model önemli olayları hatırlar. Hepsi çevrimdışı ve deterministik — model çağrısı gerektirmez.

- **Önemli olayları saklama**: depolanan mesaja uzun bas → "🧠 Hafızaya ekle". Sabitlenen olaylar sohbet başına saklanır (`memory_<chatId>`, lorebook kalıbıyla; en fazla 40 anı, anı başına 600 karakter, bozuk kayıtlara karşı bütünlük koruması).
- **İlgili anıların geri çağrılması**: her turda sabitlenen anılar son mesajlara karşı kelime-örtüşme puanıyla puanlanır; en ilgili 5 anı (700 karakter bütçesi) prompt'a "Hatırlanan önemli anlar" bloğu olarak girer. Örtüşme yoksa blok girmez — gürültü yaratmaz.
- **Otomatik özet (çıkarımsal)**: prompt penceresinden (geçmiş derinliği) taşan eski mesajlar, konuşmacı + ilk cümle biçiminde sıkıştırılır ve "Hikâyede şimdiye kadar olanlar" bloğu olarak taze kurulumlara girer. Sağlayıcı modunda geçmiş gerçek tur olarak gittiğinden recap kullanılmaz. Model-üretimli özet ileri bir geliştirme olarak değerlendirilecek.
- **Yönetim**: hızlı araç şeridinde "🧠 Hafıza" — anıları listeler, dokununca siler. Anı varken şerit her zaman görünür.
- Saf mantık `ChatMemory` (7 test) + `RoleplayPrompt` hafıza enjeksiyonu (1 test) → toplam 184.
- versionCode 56 / 0.23.00-alpha1.

## v0.22.00-alpha1 — 2026-11-12 (ses sistemi · ilk parti: TTS okuma + sesli giriş)

P1 önceliğinin ilk partisi: mesajları dinlemek ve mikrofonla yazmak. (Otomatik özet/anı hafızası sonraki partide.)

- **Sesli okuma (TTS)**: depolanan her mesajın uzun basma menüsünde "🔊 Sesli oku" — Android `TextToSpeech` motoru cihazın dilinde okur. Uzun mesajlar cümle sınırlarından kesilerek sıraya alınır (`SpeechText` saf sınıfı, 2800 karakter tavanı, JVM testli); okuma sırasında menüde "⏹ Okumayı durdur" çıkar. Motor lazy başlatılır, her `onDestroy`'de kapatılır (rotasyon dahil — ses pencereden uzun yaşamaz). Başarırsızlıkta bilgilendirici mesaj (motor yok / hazırlanıyor).
- **Sesli giriş (STT)**: composer'da 🎤 düğmesi — sistem `RecognizerIntent` diyaloğunu açar (RECORD_AUDIO izni gerekmez, tanıma Google tarafında çalışır); dönen metin açık composer'a eklenir. Cihazda tanıma yoksa bilgilendirilir.
- İsteğe bağlı sesli sohbetin temeli: oku + dikte birlikte kullanılabilir; tam çift yönlü sesli sohbet modu sonraki partilerde değerlendirilecek.
- 5 yeni birim testi (`SpeechText`) → toplam 176.
- versionCode 55 / 0.22.00-alpha1.

## v0.21.00-alpha1 — 2026-11-12 (Karakter Card V2: SillyTavern uyumluluğu)

P0 önceliği: SillyTavern ekosistemiyle karakter alışverişi.

- **PNG karakter kartı**: karakter görselinin içine V2 JSON `tEXt` chunk (anahtar `chara`, base64) olarak gömülür — SillyTavern'in kendi saklama formatı. Görsel yoksa adın ilk harfinden gradyan kart üretilir; JPEG avatar PNG'ye yeniden kodlanır. Dışa aktarma: karaktere uzun basma menüsünden "PNG kart olarak dışa aktar".
- **JSON karakter kartı**: `spec: "chara_card_v2"` + `data` sarmalayıcısı ile V2 JSON (`system_prompt` ↔ talimatlar, `mes_example` ↔ örnek diyaloglar, `personality` ↔ kişilik). Long-press menüsünden ayrı seçenek.
- **İçe aktarma**: Karakterler sayfasındaki "Kart içe aktar (PNG / JSON)" her iki formatı da kabul eder; PNG kendisi import edilen karakterin avatarına kopyalanır. V1 düz kartlar (data sarmalayıcısız) da okunur. `scenario` ve `first_mes` alanları LocalRP'de özel alan olmadığından açıklamaya paragraf olarak eklenir (atılırlar). İçe aktarılan karakter yeni kimlik alır — çakışma yok.
- **Saf mantık**: `CharacterCard` sınıfı (JSON eşleme + PNG chunk kodlayıcı/çözücü, java.util.zip.CRC32 ile RFC 1950 CRC), JVM testli; chunk sınırları ve PNG imza doğrulaması bozuk dosyalara karşı korur.
- Eski "JSON dışa aktar" (LocalRP PersistenceCodec formatı) değişmedi — yedek/taşıma yolu aynı kalır.
- 10 yeni birim testi (`CharacterCard`) → toplam 171.
- versionCode 54 / 0.21.00-alpha1.

## v0.20.01-alpha1 — 2026-11-12 (erişilebilirlik + tanılama)

İki dış AI incelemesinin (Codex + DeepSeek) ortak önceliği: dokunma alanları, çökme kaydı ve Ayarlar'da tanılama kartı.

- **48dp dokunma alanları**: sohbet konuşmacı seçici (40), devam düğmesi (44), fotoğraf/görsel düğmeleri (44), görsel görüntüleyici kapatma (44), Ayarlar satır düğmeleri (46), konuşma başlatıcı çipleri (40), composer fotoğraf çipi (38) ve v0.20.00'ın yeni çipleri/hızlı araçları/nav öğeleri artık en az 48dp — Android Views erişilebilirlik kılavuzunun önerisi.
- **TalkBack etiketleri**: eksik açıklamalar tamamlandı (composer'daki ekli fotoğrafın kaldırma ✕ düğmesi dahil); metinli kontroller zaten etiketli.
- **Çökme kaydı**: global uncaught exception yakalayıcı, son çökmenin türünü/mesajını/yığın tepesini (kırpılmış, toplam uzunluk sınırlı) SharedPreferences'a yazar. Zincirleme davranış korunur: önceki handler aynen çalışır, süreç ölümü etkilenmez; işlem başına bir kez kurulur.
- **Ayarlar'da Tanılama kartı**: uygulama sürümü, Android sürümü, cihaz modeli, boş depolama ve varsa son çökme bloğu; "Tanılama raporunu kopyala" panoya tam raporu verir (hiçbir şey otomatik gönderilmez — manuel kurulum yapan kullanıcının hata raporuna yapıştırması için), çökme kaydı varken "Son çökme kaydını temizle" çıkar.
- 6 yeni birim testi (`Diagnostics`: kayıt kırpma, null güvenliği, yığın tavanı, rapor biçimi) → toplam 161.
- versionCode 53 / 0.20.01-alpha1.

## v0.20.00-alpha1 — 2026-11-12 (yeni arayüz: FigCraft önerisi koda döküldü)

Onaylanan FigCraft UI önerisinin ilk dört parçası native olarak uygulandı.

- **Alt navigasyon yeniden stil**: her öğe artık glifi etiketin üstünde yığıyor (FigCraft mockup'ındaki gibi); seçili öğe hem glifte hem etikette vurgu rengi alıyor, diğerleri sönük kalıyor. `addNavItem`'in baştan beri kullanılmayan icon parametresi artık gerçekten kullanılıyor.
- **Ana sayfa karşılama**: varsayılan kullanıcı kişiliğinin adı varsa sayfa alt başlığı "Selam, {ad} — hikâyen seni bekliyor" oluyor; ad yoksa eski statik slogan korunuyor.
- **Ana sayfa arama hapı + filtre çipleri**: karakter bölümünde canlı arama ("Karakter ara…") ve kullanım çipleri (Tümü / Grup RP'de / Tek sohbette). Yazarken ve çip değişirken yalnızca grid host yeniden inşa ediliyor — sayfanın geri kalanı ve pencere dokunulmuyor (önceki flicker dersleri). Karar mantığı JVM testli yeni `HomeCards` sınıfında: sohbet sayısı, kullanım etiketi (grup/tek) ve sorgu+çip filtresi; `Locale.ROOT` küçültme artı Türkçe `İ` için U+0307 temizliği.
- **Kart alt bilgisi**: ana sayfa kartlarında karakterin sohbet kullanımını gösteren etiket şeridi (GRUP/TEK hapı + "N sohbet"); yalnızca sohbeti olan karakterlerde görünür. Karakterler sayfası aynı grid'i footersız kullanıyor.
- **Sohbet hızlı araç şeridi**: composer'ın üstünde tek dokunuşlu üç hap — `↻ Yeniden üret`, `✎ Yönlendir`, `↩ Geri getir` — v0.19.22 balon menüsündeki aksiyonları yeniden kullanıyor. Üretim/model yükleme/benchmark sırasında, model yanıtlayamıyorken ya da kuyruğun sonunda AI mesajı yokken gizlenir; geri getirme hapı yalnızca stash geri yüklenebilir durumdaysa görünür.
- 8 yeni birim testi (`HomeCards`) → toplam 155.
- versionCode 52 / 0.20.00-alpha1.

## v0.19.22-alpha1 — 2026-11-12 (daha iyi AI rahatlatma araçları)

Yeniden üretim akışına iki rahatlatma aracı: yönlendirme notu ve eski yanıtı geri getirme.

- **Yönlendirerek yeniden üret**: son AI mesajına uzun basınca yeni seçenek — tek seferlik bir yönlendirme notu ("daha kısa yaz", "aksiyona odaklan"…) yalnızca bu yanıtın prompt'una girer (`RoleplayPrompt`'a `steer` parametresi; yerel motor ve online sağlayıcı sistem promptu ikisi de destekler), kayda yazılmaz ve bir sonraki turda otomatik düşer. Not boş bırakılırsa sıradan yeniden üretim yapılır.
- **Eski yanıtı geri getir**: yeniden üretim artık silinen kuyruğu oturumluk bir stash'e alıyor; yeni yanıt beğenilmezse son AI mesajının menüsünden önceki hâli birebir geri getirilebilir (zaman damgaları ve görseller dahil). Yeni kullanıcı mesajı yazmak, sohbetin yukarısının değişmesi veya düzenlenmesi geri almayı güvenli biçimde devre dışı bırakır (`RegenUndo` saf karar sınıfı, JVM testli). Stash bellekte yaşar: uygulama kapanınca kaybolur — bu durumda kuyruğun görsel dosyaları diskte kalabilir.
- 8 yeni birim testi (steer prompt'ı TR/EN/boş; `RegenUndo` guard kuralları) → toplam 146.
- versionCode 51 / 0.19.22-alpha1.

## v0.19.21-alpha1 — 2026-11-12 (operasyon temizliği · 2)

Temizlik turunun ikinci aşaması: kullanılmayan importlar ve ölü sözlük girdileri.

- **Kullanılmayan importlar**: `MainActivity`'den `android.graphics.Color` (tüm renk erişimi `Design.*` üzerinden) ve `org.json.JSONArray` (başka dosyada kullanılıyor) kaldırıldı; diğer tüm kaynak dosyalar tarandı, temiz.
- **Ölü sözlük girdileri**: `EN` tablosundan 25 doğrulanmış ölü anahtar silindi (617+ → 723 girdi tablosunda). Doğrulama yöntemi: anahtarın birebir dize kaynağı tüm kaynak kodda (`L10n.java` hariç) hiçbir yerde geçmiyorsa ölüdür — bu kural dinamik sarmaları (`L10n.s(theme[1])`, `L10n.s(model.why)`) ve kaçış dizili anahtarları (`\n`, `\"`) korur. Silinenler yalnızca yenilemelerde bölünen birleşik cümleler ve eski etiketler ("Sıra: Otomatik", "Profili kaydet", eski yaş uyarısı metni…); canlı parçaları ayriyeten sözlükte.
- Ölü alan taraması: 111 alan denetlendi, hiçbiri ölü değil.
- versionCode 50 / 0.19.21-alpha1.

## v0.19.20-alpha1 — 2026-11-12 (operasyon temizliği)

Ölü kod çıkarma + bir GUI geçişinde sessizce erişilemez olmuş özelliğin geri bağlanması.

- **Uzun basış menüsü geri geldi**: karakter grid hücrelerinde uzun basış yeniden "Kopya oluştur / JSON dışa aktar" menüsünü açar — list→grid yenilemesinde bu etkileşim sessizce kaybolmuştu (grid hücresinde `OnLongClickListener` yoktu). Davranış: uzun basış = eylemler, dokunuş = editör (öncekiyle aynı).
- **Ölü kod**: hiç çağrılmayan `addCharacterStrip` + `addCharacterCard` (eski liste düzeni, grid yenilemesinde boşa düşmüştü) kaldırıldı; `PersistenceCodec.encodePersona` (legacy kayıt yazıcısı — uygulama artık yalnızca çözümlüyor) kaldırıldı, testi satır-içi legacy JSON ile `decodePersona`'yı doğrular hale geldi.
- **Depo temizliği**: 35 birleştirilmiş yerel dal silindi (yalnızca `main` kaldı); `.tmp` arşivindeki 40 eski APK kaldırıldı (~907 MB) — tüm APK'lar zaten GitHub Releases'ta SHA-256 doğrulamalı yayında, en güncel APK ve tüm sürüm notları/raporlar korundu.
- **Doküman**: `ARCHITECTURE.md`'deki bayat satır sayıları güncellendi.
- **Bilinçli tutulanlar**: dinamik sarmalanmış dize sözlük girdileri (tema adları `L10n.s(theme[1])`, katalog metinleri `L10n.s(model.why)` kullanım yerinde çevrildiği için "kullanılmayan" görünen girdiler gerçekte canlı); `docs/logo-source.png` (kullanıcının eski logo kaynak dosyası olabileceği için korundu).
- versionCode 49 / 0.19.20-alpha1.

## v0.19.19-alpha1 — 2026-11-11 (güvenlik güncellemesi)

Platform düzeyinde savunma derinliği: ağ politikası ve veri çıkarma kuralları artık açıkça shipped — davranışsal değişiklik yok, garanti sözleşmeye dönüştü.

- **Ağ politikası** (`res/xml/network_security_config.xml`): tüm trafik için `cleartextTrafficPermitted="false"`. Uygulama yalnızca https uçlarına konuşur (model kataloğu indirmeleri, OpenAI-uyumlu sağlayıcı); bu artık manifest seviyesinde dayatılmış, gelecekteki kod yolunun platform varsayılanına muhtaç olmadığı bir garanti.
- **Veri çıkarma kuralları** (`res/xml/data_extraction_rules.xml`): Android 12+ bulut yedekleme **ve** cihazdan-cihaza transfer için tüm depolama alanları (root/file/database/sharedpref/external) açıkça dışlandı. RP kayıtları, karakterler ve kişilikler yalnızca uygulama içi, kullanıcı başlattığı yedekle cihazdan çıkar. `allowBackup="false"` ile birlikte çift kilit.
- **Sağlayıcı endpoint politikası sağlamlaştırıldı** — `OpenAiCompat.allowsEndpoint` string-prefix kontrolünden `URL`-parse tabanlı şema kontrolüne geçti: büyük/küçük harf ("HTTPS://") bağlantının kendisiyle aynı şekilde değerlendirilir, bozuk/farklı şemalar (ftp/file/javascript/gopher) kapalı başarısız olur. `DownloadPolicy.isHttps` ile aynı muhakeme.
- **Regresyon koruması**: `SecurityPolicyTest` iki politika XML'ini doğrudan okur — cleartext izni veya çıkarma kuralı gevşetilirse derleme başarısız olur; `OpenAiCompatTest` şema politikasının uç durumlarını sabitler.
- Mevcut savunma katmanları (denetim sonucu değişiklik yok, hepsi yerinde): AndroidKeyStore AES/GCM anahtar saklama (düz metin diskte yok), https-only indirmeler + in-app SHA-256 doğrulama + Range-doğrulamalı devam, yedek ZIP sağlama özeti + ZIP-bombası limitleri + yol geçişi reddi, `allowBackup=false`, tek izin (INTERNET), log yazımı yok, tüm prefs `MODE_PRIVATE`.
- versionCode 48 / 0.19.19-alpha1.

## v0.19.18-alpha1 — 2026-11-11 (kişilikler yedeğe dahil)

v0.19.16'da bilinçli olarak dışarıda bırakılan sınırlar kapatıldı: **kullanıcı kişilikleri artık `.json` / `.zip` yedeğine giriyor**.

- **Yedek paketi** — `personas` dizisi (id, ad, açıklama; v2'de avatar `@avatars/…` işaretçisiyle), `defaultPersona` kimliği ve `chatPersonas` (sohbet başına seçim, yalnızca pakete giren sohbetler için) taşınır. Eski yedek dosyaları bu bölümler olmadan aynen okunur (ileriye/geriye uyumlu).
- **ZIP medyası** — persona profil fotoğrafları `media/avatars/` altında paketlenir, sağlama özetine girer; geri yüklemede baytlar yerel dizine yazılır, eksik bayt güvenle boşa düşer (mevcut medya kuralı).
- **Geri yükleme** — kişilikler id-bazlı birleştirilir (aynı id güncellenir, yenisi eklenir); içe aktarılan varsayılan yalnızca birleşme sonrası varsa uygulanır, aksi halde yerel varsayılan korunur; sohbet kişilik seçimi yalnızca geri yüklenen sohbete ve birleşme sonrası var olan kişiliğe uygulanır.
- **Varsayılan öz-iyileştirme** — `default_persona_id` silinmiş bir kişiliğe işaret ederse ilk okunuşta ilk kişiliğe düzeltilir (önceden yalnızca tekil silme akışında düzeltiliyordu; harici inceleme bulgusuydu).
- Seçmeli dışa aktarma: kişiliklerin tamamı gider (küçük ve varsayılan seçim anlamlı kalsın diye), sohbet kişilik seçimleri yalnızca seçilen sohbetler için filtrelenir.
- versionCode 47 / 0.19.18-alpha1.

## v0.19.17-alpha1 — 2026-11-11 (İngilizce mod kapsama düzeltmesi)

"English (recommended: better RP)" modunda hâlâ Türkçe görünen yerler kapatıldı; arayüz artık İngilizce modda uçtan uca İngilizce.

- **Kapsam taraması**: 742 benzersiz `L10n.s()` çağrı yeri dizesinin tamamı `EN` sözlüğüyle eşleştirildi; eksik ~100 girdi (kişilik sistemi, yedekleme, gizlilik, cihaz analizi, model ekranları, dünyaya giriş) çevrilerek eklendi. Sözlük artık 717+ girdi.
- **Yeni sarmalanan çağrı yerleri**: bölüm başlıkları ("Son sohbetler", "Ayarlar", "Model", "Sahne teması", "Motor testi", "Karakter zinciri"…), dinamik sayacın dil parçaları (" karakter", " mesaj", " token", " sohbet"), ayraçlar (" · ", " / ", "  ·  "), model bilgi etiketleri ("Mimari:", "Nicemleme:"), indirme yüzdesi parçası ve "sen"/"Sen" gösterim yedekleri (arama, avatar baş harfleri).
- **Bilinçli passthrough**: noktalama ayraçları, "en"/"tr" dil kodları, kullanıcı verisi etiketi `new Message("Sen", …)` (kalıcı veri; `L10n.speaker` zaten çevirir), İngilizce dalı ternary ile zaten çevrilmiş prompt dizeleri, hazır karakter metinleri ve tema veri tabloları (kullanım yerinde çevriliyor).
- versionCode 46 / 0.19.17-alpha1.

## v0.19.16-alpha1 — 2026-11-11 (çoklu kullanıcı kişilik sistemi)

Tekil "kullanıcı profili" artık **kişilik listesi**: birden fazla kimlik tanımla, sohbet başına seç.

- **Kişilik yöneticisi** — Karakterler ekranındaki kart artık kişilik listesini açar: her kart avatar + ad + açıklama; "＋ Yeni kişilik" ile ekleme, dokununca editör.
- **Editör** — ad, kendini tanıtma, profil fotoğrafı; "Varsayılan kişilik yap" düğmesi; silme (kullanan sohbet seçimleri otomatik temizlenir, avatar dosyası silinir).
- **Sohbet başına kişilik** — Üretim profili → "Bu sohbetin kişiliği": "Genel varsayılanı izle" veya listeden bir kişilik; seçim/geri döndürme yerel motor bağlamını sıfırlar (eski kimlik bağlamda kalıntı bırakmaz).
- **Varsayılan kişilik** — ilk tanımlanan otomatik varsayılan olur; değiştirilebilir; prompt çözümü: sohbet seçimi → genel varsayılan → yok (blok eklenmez).
- **Geçiş (migrasyon)** — eski tekil `user_persona` kaydı ilk açılışta `id="default"` kişiliğe taşınır; kullanıcı hiçbir şey yapmaz, profili kaybolmaz.
- Avatar taslağı artık kişilik sahibiyle eşleşir: başka bir kimlik için kalmış taslak sessizce uygulanmaz, silinir.
- Yedek kapsamı: kişilikler (önceki tekil profil gibi) `SharedPreferences`'ta yaşar; `.json/.zip` yedeğine dahil değildir (öncekiyle aynı kapsam; ayrı turda değerlendirilir).
- versionCode 45 / 0.19.16-alpha1.

## v0.19.15-alpha1 — 2026-11-11 (yeni başlatıcı ikonu: dram sahnesi)

- **Yeni ikon** (kullanıcı seçimi): karanlık sahnede spot ışığı altında palyaço, elinde gözyaşlı tragedya (üzgün) maskesi — dram tiyatrosu teması. Dört konsept (maskeler / kitap / d20 zar / dram sahnesi) arasından kullanıcı bu sahneyi seçti.
- Uygulama: adaptive icon foreground + background vektörleri (minSdk 33, eski PNG gerekmez); spot ışığı alfa degradesi aapt gradient ile, maske eğimi VectorDrawable `<group>` rotasyonuyla. 66dp güvenli alanına uyarlı; ışık huzmesi maske kenarında doğal kırpılır.
- versionCode 44 / 0.19.15-alpha1.

## v0.19.14-alpha1 — 2026-11-11 (aynı sayfa yenilemesi artık yerinde çiziliyor)

v0.19.13'te bilinçli olarak ertelenen kalem: sayfa içi yenilemeler (lore kaydı, ayar toggle'ları, model durumu) hâlâ tüm pencereyi `setContentView` ile yeniden kuruyordu — gezinme flaşı gidince kalan ikinci titreme kaynağı.

- **Yerinde sayfa yenilemesi**: aynı sayfa (başlık + alt başlık + sekme + geri durumu eşleşiyorsa) yeniden çizilirken artık yalnızca içerik listesi temizlenip yeniden dolduruluyor; başlık, alt bar ve pencere korunur. Lore kaydetme/silme, ayar toggle'ları ve profil düğmeleri artık flaş yapmaz.
- **Chat sayfaları her zaman tam kurulum** kalır (mesaj çizimi zaten yerinde); çapa geçersiz kılınır.
- **Tema ve dil değişimi tam kurulumu zorlar** (`resetPageAnchor`): başlık/alt bar eski paletle/eski dille kalmasın.
- Scroll konumu liste düzenlemesinde korunur (istenen davranış); içerik kısalırsa otomatik kelepçelenir.
- Harici inceleme (Codex CLI): "Kritik yok"; 4 bulgunun tamamı incelendi — undo bar kesinleşmesinin yerinde yenilemede korunması (bar 8 sn sayacıyla kendi kendine kesinleşir; gerçek gezinmede eskisi gibi kesinleşir), aynı adlı grup eşleşmesinin ulaşılamaz akış olması, scroll konumu ve alan bayatlaması sorunsuz — tamamı bilinçli kabul edildi.
- versionCode 43 / 0.19.14-alpha1.

## v0.19.13-alpha1 — 2026-11-11 (hata raporu taraması: titreme + çökme riskleri)

Kullanıcı hata raporu ("her alan/gezinme değişiminde flaş etkisi, ara sıra çökme") üzerine iki harici salt-okunur inceleme (Codex CLI + Antigravity CLI) yapıldı; bulgular bağımsız doğrulanıp girmeden düzeltildi.

- **Gezinme flaşı kaldırıldı**: her sayfa değişimindeki ~120 ms alpha fade çıkarıldı — yavaş telefonlarda her gezinme "flaş" gibi algılanıyordu.
- **Akış titremesi giderildi**: üretim sırasında her token'da balon tam yeniden çiziliyordu (metin taraması + span kurulumu + kaydırma, saniyede onlarca kez). Artık ~11 fps'e throttle'lı: ara güncellemeler tek toplu çizime coalesce edilir, bitişte eksiksiz son çizim korunur; kuyrukta kalıntı bırakmaz.
- **Geri yükleme çökme riski**: yedek geri yükleme iş parçacığı, kullanıcı uygulamayı kapatmışsa ölü activity üzerinde sayfa açıyordu → `uiAlive()` guard eklendi (veri zaten yazılmış; yalnızca ekran/Toast çökmesi önlenir).
- **Sağlayıcı sızıntısı**: activity kapanırken devam eden çevrimiçi turn'un SSE akışı artık `onDestroy`'da kesiliyor (`providerClient.stop()`).
- **Bozuk kayıt NPE koruması**: null konuşmacılı kayıtlar balon çizimini çökertemez.
- **Arama ANR/dengeleme**: sohbette arama artık tuş başına değil, 250 ms debounce ile tarama yapıyor; dialog kapanınca kuyruk temizleniyor.
- Bilinçli olarak bu turda değiştirilmeyenler: model durumu/lore sayfalarının yerinde güncellenmeye çevrilmesi (tam sayfa yeniden kurma davranışı korundu; ayrı bir turda değerlendirilir).
- versionCode 42 / 0.19.13-alpha1.

## v0.19.12-alpha1 — 2026-11-10 (DeepSeek madde 14 / B: sağlayıcı ayarları + üretim yönlendirmesi)

Faz 5'in ikinci alt turu: **çevrimiçi sağlayıcı artık Ayarlar'da yapılandırılıp, açık onayla yanıt üretebiliyor**. Çevrimdışı llama.cpp varsayılanı korundu: `provider_enabled` kapalıyken (varsayılan) hiçbir davranış değişmedi ve hiçbir veri gönderilmedi. Değişiklikler salt-okunur harici inceleme (Codex CLI, read-only sandbox) ile iki turda gözden geçirildi.

- **Ayarlar → Çevrimiçi sağlayıcı**: OpenAI-uyumlu **https** endpoint + model adı + geçmiş mesaj sayısı (2–20) kaydı; API anahtarı AndroidKeyStore'da AES/GCM ile kriptolu kaydet/sil; "Bağlantıyı test et" (birkaç tokenlık tek seferlik ping, açık kullanıcı eylemi). Sağlayıcı ancak yapılandırma eksiksizken etkinleştirilir.
- **Üretim yönlendirmesi**: sağlayıcı etkin ve eksiksizken `beginGeneration` yanıtı çevrimiçiye yönlendirir; sistem promptu history-free RoleplayPrompt derlemesinden (identity/theme/lore bloklarını korur, geçmiş başlığı basmaz), sohbet geçmişi gerçek user/assistant turnleri olarak taşınır (sondaki kullanıcı mesajı final user turn olur). Yerel motor yolu (bağlam bütçesi, draft, zincir) hiç dokunulmadı.
- **Ortak üretim kanalı**: eski büyük anonim dinleyici `GenerationPipe` iç sınıfına taşındı; iki motor da aynı balon/draft/kayıt/zincir/hata diyaloğu iş akışını kullanıyor. Sağlayıcı callback'leri main thread'e yönlendirilir; stop, çift-terminal guard'ı ve generation-sequence koruması var.
- **Durum görünürlüğü**: sağlayıcı etkinken sohbet başlığı "Online sağlayıcı · veri açık onayınla gider" der — yanıtların nereden geldiği ekranda görünür.
- **Bilinçli sınırlar**: yerel model yükleme/benchmark süreci küresel engel kalmaya devam eder (sağlayıcı açıkken de bekletir); etkinlik kapanırsa aktif tur, yerel motorla aynı şekilde kaydedilmeye çalışılır; geçmiş etiketi mesaj sayısını sayar (turn değil).
- versionCode 41 / 0.19.12-alpha1.

## v0.19.11-alpha1 — 2026-11-10 (DeepSeek madde 14 / A: çevrimiçi sağlayıcı temeli)

Faz 5'in ilk alt turu: **InferenceBackend soyutlaması** — çevrimdışı llama.cpp varsayılan kalır; çevrimiçi zemin bu turda üretime bağlanmaz (sıradaki tur: ayarlar arayüzü + üretim yönlendirmesi). Değişiklikler salt-okunur harici inceleme (Codex CLI, read-only sandbox) ile gözden geçirildi; iki tur yapıldı, tüm gerçek bulgular girmeden düzeltildi.

- **GenerationBackend arayüzü**: tüm yanıt üreticileri aynı sözleşmeye bağlanır (onToken/onComplete/onStopped/onError); çift motor (yerel GGUF / çevrimiçi OpenAI-uyumlu) tek akışta yönlendirilecek.
- **OpenAiCompat istemcisi**: OpenAI-uyumlu `chat/completions` akış (SSE) sınıfı — istek gövdesi, delta ayıklama (akış + non-stream çakışma yanıtı), `[DONE]`, akış-ortası hata parçaları, sağlayıcı hata gövdelerinin okunması ve `stop()` desteği. Saf parçalar (istek gövdesi, SSE ayriştirme, hata mesajı) birim testle düzenlenir.
- **Güvenlik dayatmaları**: endpoint **yalnızca https** kabul eder; API anahtarı düz bağlantıda sızamaz. Sunucu hatası metni kullanıcıya ham olarak aktarılmaz (URL sıntması engeli): hata yalnızca istisna sınıfıyla özetlenir.
- **ApiKeyStore**: API anahtarı asla düz metin diskte durmaz — AndroidKeyStore'da üretilen AES/GCM anahtarıyla şifreli tutulur (Base64 iv||şifreli). Keystore kaybolursa "yapılandırılmış" yanıltmaz: yapılandırma denetimi çözümlü anahtara bakar. Anahtar silme (erase) eylemi vardır.
- **Seçmeceler sisteme dokunmaz**: bu tur hiçbir sohbet/ayar akışını değiştirmez; çevrimdışı davranış birebir korunur.
- Bilinçli sınırlar: stop() bağlantı penceresi içinde (≤10 sn) kalırsa POST gövdesi gönderilebilir (anahtar, kullanıcının kendi yapılandırdığı noktaya gider); sunucu akışı kapatırsa [DONE] öncesi yanıt tam kabul edilir.
- versionCode 40 / 0.19.11-alpha1.

## v0.19.10-alpha1 — 2026-11-10 (DeepSeek madde 12: dünya kitabı / lorebook)

DeepSeek incelemesinin "lorebook" kalemi (madde 12) uygulandı. Değişiklikler salt-okunur harici inceleme (Codex CLI, read-only sandbox) ile gözden geçirildi; bulgular sürüm girmeden düzeltildi.

- **Dünya kitabı**: sohbet menüsünde yeni "Dünya kitabı" öğesi. Her sohbet kendi kitabını taşır: anahtar kelime(ler) (virgülle ayırın) + modelin bilmesi gereken olgu.
- **Anahtar-kelime tetikleme**: yanıt üretiminde son 3 mesajda bir anahtar geçerse o kayıt prompta "Dünya kitabı" bloğu olarak yazılır. Eşleşme büyük/küçük harf duyarsızdır; ı/İ/I/i yazım farkları her iki tarafta aynı forma katlanır.
- **Bütçe**: blok en fazla 900 karakter tutar; sığmayan kayıtlar sırayla kırpılır. Anahtar hiç geçmezse blok prompta hiç yazılmaz (bedava).
- **Yedek**: dünya kitabı kayıtları v1 ve v2 yedek paketlerine "lore" alanı olarak yazılır; seçmeli dışa aktarma yalnızca seçilen sohbetlerin kitaplarını taşır. Geri yüklemede boş olmayan içe aktarılan kitap, aynı sohbetin mevcut kitabını değiştirir.
- **Bilinçli tercihler**: bütçe sabittir (ayarlama kaldırıldı); restore asla mevcut kayıt silmez (sohbet geçmişindeki "yalnızca daha uzun" kuralıyla aynı felsefe); diyakritik farklı yazımlar nadir yanlış pozitif üretebilir — ancak bu yalnızca fazladan bilgi enjekte eder.
- versionCode 39 / 0.19.10-alpha1.

## v0.19.9-alpha1 — 2026-11-10 (DeepSeek madde 13: ilk kurulum akışı — dil → model → ilk karakter)

DeepSeek incelemesinin "onboarding" kalemi (madde 13) uygulandı. Değişiklikler salt-okunur harici inceleme (Codex CLI, read-only sandbox) ile gözden geçirildi; bulgular sürüm girmeden düzeltildi.

- **İlk kurulum akışı**: yeni kurulum (hiç verisi olmayan) ilk açılışta üç adımlı bir akışla karşılanır; verisi olan tüm kullanıcılar bu akışı asla görmez:
  1. **Dil**: Türkçe / English seçimi anında uygulanır; seçim, model prompt dilini de belirler.
  2. **Model**: cihazdaki kayıtlı model kopyalarına dokunarak yükleme (fizibilite etiketiyle), model kataloğunu açma ya da atlayıp sonradan Ayarlar'dan tamamlama. Yükleme tamamlanınca sayfa durumu otomatik güncellenir.
  3. **İlk karakter**: ad + tanıtım + kişilik formu veya hazır karakterlerden alanları dolduran tek dokunuş; "Oluştur ve başla" ile ana ekrana inilir.
- Akış tamamlandığında `onboarding_done` işaretlenir; adım adım geri gitme (‹) desteklenir.
- versionCode 38 / 0.19.9-alpha1.

## v0.19.8-alpha1 — 2026-11-10 (DeepSeek madde 11: model profilleri + fizibilite + açılışta otomatik yükleme)

DeepSeek incelemesinin "model profilleri" kalemi (madde 11) uygulandı. Değişiklikler salt-okunur harici inceleme (Codex CLI, read-only sandbox) ile gözden geçirildi; bulgular sürüm girmeden düzeltildi.

- **Üç durumlu fizibilite göstergesi**: model boyutuna ve cihaz RAM'ine göre "sığar / sınırda / zor" — katalog çipleri artık "rahat / sınırda / riskli" diye ayrışıyor (sınırda ≈ RAM'in %42'sine sığan dosya); "Model kopyalarını yönet" listesi de her dosyanın yanında aynı hükmi gösterir. RAM okunamadıysa hiçbir hüküm uydurulmaz.
- **Model profili**: Ayarlar → Model kopyalarını yönet → bir model seçince: boyut, tahmini bellek kullanımı (~1,3× dosya), RAM hükmü, yükleme eylemi ve büyük "açılışta otomatik yükle" anahtarı tek diyalogda.
- **Açılışta otomatik yükleme**: sabitlenen model ilk açılışta arka planda sessizce yüklenir (kısa bir Toast ile). Dosya silinmişse tercih kendi kendine kapanır; silme başarısızsa tercih korunur.
- versionCode 37 / 0.19.8-alpha1.

## v0.19.7-alpha1 — 2026-11-10 (DeepSeek madde 7: sohbet başına üretim profili)

DeepSeek incelemesinin "sohbet başına üretim profili" kalemi (madde 7) uygulandı. Değişiklikler salt-okunur harici inceleme (Codex CLI, read-only sandbox) ile gözden geçirildi; bulgular sürüm girmeden düzeltildi.

- **Üretim profili**: sohbet menüsünde yeni "Üretim profili" öğesi. Bu ekranda üç ayar sohbet özeline alınır; bir değer override olursa yalnız bu sohbette yazılır, diğer sohbetlerde genel ayar aynen sürer:
  - **Yanıt uzunluğu** (64–1024 token) — genel "max_output_tokens" yerine sohbet özeli yazılır.
  - **Geçmiş derinliği** (2–14 mesaj) — genel "history_depth" yerine sohbet özeli yazılır.
  - **Sahne teması** — genel "ai_theme" yerine sohbet özeli yazılır; genel "özel tema" tanımı da bu sohbet için seçilebilir.
- Her ayar için tek tek "genel ayara döndür" düğmeleri ve profili tümüyle sıfırlayan onaylı bir "Profili sıfırla" eylemi var. Tema değişikliği/geri dönüşü ve profil sıfırlama o sohbetin motor bağlamını temizler; bu değişiklikler o sohbette yanıt sürerken engellenir.
- Ayarlar'daki genel motor tercihlerine dokunulmaz; bellek modeli ve tema sistemi aynı kalır.
- versionCode 36 / 0.19.7-alpha1.

## v0.19.6-alpha1 — 2026-11-10 (DeepSeek madde 9b: seçmeli dışa aktarma + geri alınabilir silme)

DeepSeek incelemesinin "yedekleme" kaleminin ikinci bölümü (madde 9b) uygulandı; böylece madde 9 tamamen kapandı. Değişiklikler salt-okunur harici inceleme (Codex CLI, read-only sandbox) ile gözden geçirildi; bulgular sürüm girmeden düzeltildi.

- **Seçmeli dışa aktar**: Ayarlar → Veri yedeği → "Seçmeli dışa aktar" — karakter ve sohbet listesini işaretleyip JSON veya ZIP biçimi seçilir; yalnızca seçilenler yazılır. Seçilen sohbetlerin karakterleri tutarlılık için otomatik yazılır. Seçim, SAF dosya seçicisi aktiviteyi yeniden kurarsa bile korunur.
- **Geri alınabilir silme**:
  - **Karakter silme** artık 8 saniyelik "silindi — Geri al" çubuğu gösterir; Geri al'a dokunmak karakteri kaydettiği hâliyle geri getirir.
  - **Sohbet (grup) silme** aynı çubukla geri alınır: grup, geçmişi ve cihazdaki görsel dosyaları; görseller yalnızca süre dolunca/çubuk kapatılınca silinir, böylece geri alma gerçekten çalışır.
  - Süre dolunca veya başka bir sayfaya geçince silme kalıcılaşır; en fazla bir bekleyen geri alma olur.
- **Bilinen sınırlar**: geri alma penceresi kapanmadan uygulama tamamen kapanırsa silinmiş sohbetin görsel dosyaları diskte kalabilir (galeriden silinebilir); sohbet geçmişi temizleme düğmesi hâlâ geri alınamaz.
- versionCode 35 / 0.19.6-alpha1.

## v0.19.5-alpha1 — 2026-11-09 (DeepSeek madde 9: yedek v2 — medya dahil ZIP)

DeepSeek incelemesinin "yedekleme" kaleminin birinci bölümü (madde 9a) uygulandı. İkinci bölüm (seçmeli dışa aktarma + geri alınabilir silme) sonraki tura kaldı. Değişiklikler salt-okunur harici inceleme (Codex CLI, read-only sandbox) ile gözden geçirildi; bulgular sürüm girmeden düzeltildi.

- **Yedeği dışa aktar (medya dahil)**: Ayarlar → Veri yedeği → yeni düğme tek bir `.zip` yazar:
  - `bundle.json` — karakterler, sohbetler, geçmişler (yeni sürüm 2 biçimi; görsel/kapak yolları `@avatars/…` / `@images/…` işaretleriyle yazılır).
  - `checksum.txt` — iki SHA-256 sağlaması: JSON metni ve ZIP içindeki tüm görsel baytları (kapsama: dosya adı + baytlar).
  - `media/avatars/…`, `media/images/…` — yalnızca yedekte gerçekten referans edilen dosyalar; cihazda silinmişse atlanır.
- **İçe aktarma iki yol**: `PK` sihirli byte'ı ile ZIP tanınır; ZIP'te önce sağlamalar doğrulanır (uyuşmazlıkta yedek tümüyle reddedilir). ZIP kayması koruması: yalnızca `bundle.json`, `checksum.txt` ve `media/…/tek-dosya-adı` girdileri okunur; `..` ve ters eğik çizgi içeren adlar reddedilir; girdi başına 64 MB açılma sınırı vardır. Medya baytları yerel kopya eksikken açılır; aynı ad yerelde varsa paketlenen bayt üzerine yazar — paket kendi kendi yoluna ne yazdıysa o geri gelir.
- **Eski JSON yedekleri aynen çalışır**: sürüm 1 salt-JSON yedeği (mutlak yollar) hiçbir değişiklik olmadan okunur; başka cihazdan gelen yollar bugünkü gibi ayıklanır.
- versionCode 34 / 0.19.5-alpha1.

## v0.19.4-alpha1 — 2026-11-09 (DeepSeek madde 8: görsel kontrol paneli + galeri)

DeepSeek incelemesinin "görsel" kalemi (madde 8) uygulandı. Değişiklikler salt-okunur harici inceleme (Codex CLI) ile gözden geçirildi; bulgular sürüm girmeden düzeltildi.

- **Görsel üretim paneli**: ✨ diyaloğunda artık adım sayısı (10–50), CFG ölçeği (1.5–15) ve tohum alanları var; boş tohum rastgele demek. Son kullanılan değerler bir sonraki açılışta korunur. Girdiler motor sınırına kırpılır (NaN/sonsuz reddedilir).
- **Görsel galerisi**: Ayarlar → Görünüm → "Görsel galerisi" — üretimler ve sohbet fotoğrafları küçük önizlemelerle en yeniden listelenir. Karta dokunmak tam ekran zoomable görüntüleyiciyi açar (v0.19.3'ten).
- **Galeri eylemleri** (uzun basınca):
  - **Karakter avatarı yap** — görseli avatar alanına kopyalar, karakteri listeden seçtirir; klasör yoksa açılır, kayıt başarısızsa öksüz kopya silinir.
  - **Sohbete ekle** — görseli seçilen sohbete senin fotoğraf mesajın olarak yerleştirir.
  - **Sil** — onaylı silme; dosya bir sohbette veya karakter avatarında kullanılıyorsa silme kilitlenir ve ne yapması gerektiği söylenir.
- versionCode 33 / 0.19.4-alpha1.

## v0.19.3-alpha1 — 2026-11-09 (Big Pickle GUI incelemesi — 3. tur: görsel, ikon, önizleme)

Big Pickle raporunun 3. tur maddeleri uygulandı (9, 13, 15, 16, 18; madde 17 — 18+ kapısı — zaten 2. tur tint ile kapsandı). Ayrıca uygulama yeni bir vektör launcher ikonu aldı ve bu turun değişiklikleri salt-okunur harici inceleme (Codex CLI) ile iki kez birbiri ardından gözden geçirildi.

- **Görsel mesaj artık kimlikli**: görsel balonlarda metin balonlardaki gibi kalın vurgulu konuşmacı başlığı var (ad + saat); görsel balonlardaki çift saat kalktı, saat artık tek kaynak olarak başlıkta.
- **Tam ekran görüntüleyici**: görsel balona dokunmak artık tam ekran görüntüleyiciyi açar; kıs / aç (pinch) yakınlaştırma, sürükleyerek kaydırma, çift dokunmayla zoom, tek dokunma veya ✕ ile kapanır. Matrix matematiği sığdırma ölçeğine bağlandı ve pan sınırlı (görsel ekrandan çıkamaz). Yavaş kod çözmede dialog kapandıysa bitmap geri dönüştürülür (recycle).
- **Composer hiyerarşisi**: konuşmacı çipi görsel olarak sönüklaştı; 📷 ve ✨ ekleme düğmeleri sönük düz zemine çekildi; tek belirgin aksiyon artık 48dp dairesel gönder düğmesi. 📷/✨/gönder düğmelerine contentDescription eklendi.
- **Tema önizlemesi**: Görünüm ekranındaki her palet kartı artık canlı mini önizleme gösterir (hero şeridi + karakter balonu + kullanıcı balonu), aday paletin kendi token'larıyla boyanır — aktif temadan bağımsız doğru önizleme.
- **Katalog çipleri bilgi veriyor**: her model kartında yeni uygunluk çipi var: indirme boyutu + RAM'e göre "bu cihazda rahat" ya da "RAM'e göre riskli" etiketi; görsel model kartlarında da aynı çip.
- **Açılış flaşı + geçiş**: windowBackground artık kayıtlı palet ailesiyle eşleşir (koyu paletler için ayrı AppThemeDark twin'i); beyaz açılış karesi kalktı. Sayfa geçişlerine 120ms hafif fade eklendi.
- **Yeni launcher ikonu**: iki konuşma balonu + amber kıvılcım (RP + görsel üretim), indigo-mor gradyan zemin üzerinde; adaptive icon tamamen vektöre taşındı, minSdk 33 ile artık gereksiz 10 PNG dökümü kaldırıldı — APK ve kaynak ağacı hafifledi.
- versionCode 32 / 0.19.3-alpha1.

## v0.19.2-alpha1 — 2026-11-07 (Big Pickle GUI incelemesi — 2. tur: klavye, odak, diyaloglar)

Big Pickle raporunun 2. turundaki beş madde uygulandı (3, 4, 7, 11, 14). Tasarım dili aynen korunur.

- **Klavye inset'i Android 10+**: içerik padding'i yalnızca sistem çubuklarını değil, açılan klavyenin (IME) yüksekliğini de sayar; sohbet yazma çubuğu Android 10–15'te klavyenin üstünde kalır (önceden yalnızca Android 15+ için uygulanmıştı).
- **Yazma alanında odak ve klavye korunur**: mesaj gönderince composer yeniden kurulmaz; fotoğraf çipi yerinde kaldırılır, klavye/odak açık kalır ve yazım sürer. Çipteki ✕ da aynı davranır; rebuild yalnızca fotoğraf seçme yolunda bırakıldı.
- **Diyaloglar artık paletli**: onay, sohbet menüsü, konuşmacı seçici, mesaj eylemleri, sohbet araması, görsel üretim, üretim hatası ve 18+ kapısı diyaloğu temanın arka planı/renkleriyle boyanır (tintDialog); sistem lavantası karanlık paletlerde kalmaz.
- **İngilizce sızıntıları kapandı**: karakter adı, sohbet adı ve karakter seçimi hata balonları (setError) artık dil sözlüğünden geçer; pano etiketi de çevrilir.
- **Boş sohbet başlangıç çipleri**: boş bir RP sahnesinde üç hazır giriş çipi ("İlk sahneyi kur", "Kısa bir selam", "Arka planı sor") composer'ı doldurur; kullanıcı düzenleyip kendisi gönderir, uygulama asla kendi kendine göndermez.
- versionCode 31 / 0.19.2-alpha1.

## v0.19.1-alpha1 — 2026-11-07 (Big Pickle GUI incelemesi — 1. tur: navigasyon + okunabilirlik)

Big Pickle raporunun "1. tur" (gezinme doğruluğu + balon düzeni + erişilebilirlik hızlı kazançları) uygulandı. Tasarım dili aynen korunur.

- **Sohbetten dönüş artık geldiğin yere**: ana sayfadaki sohbet kartından açılan sohbet geri okuyla Ana sayfaya, Sohbetler listesinden açılan sohbet listeye döner (backAction origin takibi).
- **Bayat model geri eylem hedefi**: Ayarlar ekranındaki "Modeli yükle ve yönet" girişi artık açıkça Ayarlar'a bağlanır (eski bir sohbetin geri dönüş hedefi olarak kalması sorunu kapanır).
- **Balon gerçek boyutları**: ağırlık tabanlı genişlik fiilen çalışmıyordu (satırda tek ağırlıklı çocuk balondur) — artık WRAP_CONTENT + maxWidth (kullanıcı ~%62, karakter ~%74) ve kullanıcı balonları sağa yaslı. Görsel balonlarda aynı kural ImageView maxWidth'ına uygulanır.
- **Çift saat kalktı**: metin balonlarında saat yalnızca başlıkta; görsel balonlarda satır ucundaki saat tek kaynak kalır.
- **WCAG kontrast yükseltmeleri** (hesaplanmış): Okyanus vurgu 2.96→4.69 (#0B7F94), Orman 3.47→5.26 (#2E7A48), Lavanta vurgu 4.38→5.65 (#6B48E8), açık tema tehlike rengi 3.91→4.51 (#D93B41), Lavanta hero alt metni 2.44→4.10 (heroB #7159EC + metin #EFEAFA), Gün Batımı hero alt metni 2.41→3.76 (#FFF5E0). Renk tonları korunarak yalnızca doygunluk koyulaştırıldı.
- **Dokunma hedefleri + etiketler**: iki geri ok 40→48dp ve "Geri" contentDescription; sohbet ⋯ menüsü 48dp + "Sohbet menüsü" etiketi; "Daha eski mesajlar" düğmesi 48dp.
- **"Son sohbetler" gerçekten son**: ana sayfa ve Sohbetler listesi son mesaj saatine göre azalan sıralanır; ana sayfa listesi ilk 4 ile sınırlıdır.
- versionCode 30 / 0.19.1-alpha1.
- **"Son sohbetler" gerçekten son**: ana sayfa koşum sayfa çalıştırması son mesaj saatine göre azalan sıralı; ana sayfa listenin ateşli üst sınırı 4; CN Sohbetleri sayfa tamı sıra.
- versionCode 30 / 0.19.1-alpha1.

## v0.19.0-alpha1 — 2026-11-07 (Mesaj eylemleri + sohbet arama + markdown + karakter çoğaltma)

DeepSeek incelemesinin öncelik listesinden ilk grup uygulandı: RP kalitesini en hızlı artıran sohbet araçları.

- **Mesaj eylemleri**: kayıtlı mesajlara uzun basınca artık Düzenle, Sil ve Yeniden üret de var (kopyalama aynen duruyor). Düzenle, metni tek diyalogda değiştirir ve motor bağlamını tazeler; Sil mesajı ve görsel dosyasını kaldırır; Yeniden üret, son AI mesajını silip turu son kullanıcı mesajından yeniden başlatır (bağlam sıfırlanır, zincir sayacı temizlenir).
- **Sohbette arama**: menüdeki "Sohbette ara" tüm kaydı Türkçe-uyumlu katlama ile tarar; bulunan sonuçlara dokununca sohbet penceresi o mesaja atlar (pencere daha eski mesajları yükleyerek kayar). Akış halindeki taslaklar kopyala-only menüde kalır.
- **Markdown-lite**: `*italik*` ve `**kalın**` baloncuk metninde stil olarak uygulanur; eşleşmeyen yıldızlar metinde kalır; italik içinde kalın çalışır. Sahne hafızası ekranından kapatılabilir (markdown_lite).
- **Karakter kartı ekstraları**: kart uzun basınca "Kopya oluştur" ve "JSON dışa aktar" çıkar; dışa aktarım SAF ile tek karakterlik PersistenceCodec JSON belgesi yazar.
- versionCode 29 / 0.19.0-alpha1.

## v0.18.1-alpha1 — 2026-11-06 (Hata düzeltme paketi — görsel motor + sohbet)

Kapsamlı hata incelemesinin (3 kritik, 10 orta, 8 düşük bulgu) düzeltme paketi. Uygulamanın davranışı ve veri biçimi değişmedi; çökmeler, durum sızıntıları ve UI boşlukları kapatıldı.

- **Kritik**: ✨ görsel üretim kutusunun kilitlenmesi (sayfa bileşenine eklenen alan girdileri) düzeltildi; kutu artık ayrık `dialogField` bileşeniyle kurulur, boş fikirde açık kalır ve "İstenmeyenler" yazısı kaybolmaz.
- **Kritik**: görsel motoru geri aramaları ana iş parçacığına taşındı (`mainSafe`) — arka plan iş parçacığından doğrudan arayüz erişimi (beyaz ekran/donma riski) engellendi.
- **Kritik**: yükleme durumu bayrağı (`loadingFile`) her çıkışta temizlenir; boşa kilitlenen motor "iş sürüyor" durumuna asla takılmaz.
- **Orta**: parça (.part) tamamlanınca boyut kontrolü yetmez — SHA-256 doğrulaması zorunlu; foto ekli ama metin boş gönderim artık model istemez; 📷 çipi gönderimden sonra kaybolur ve yazıyla **hedef sohbette** bağlanır (`pendingComposerImageChatId`); görsel üretim sırasında Devam/Tekrar dene ve otomatik üretim kilitlenir; aktivite kapanınca görsel üretim + sayaç durur; yazı, çip kapatma/rebuild akarında taslak olarak korunur; sohbet temizleme ve grup silme mesaj görsellerini dosya olarak da siler; yedek geri yüklemede bu cihazda bulunmayan görsel/avatar yolları zarif biçimde boşaltılır (çapraz cihaz bozulması önlenir); profil/karakter avatarı Kaydet başarılana kadar silinmez (geri çekilme dosya kaybetmez); görsel motor Loaded iken `unload`, yükleme/üretim sürerken `deleteStoredModel` reddeder.
- **Düşük**: " · indiriliyor…" İngilizce çevirisi eklendi; konuşmacı seçimi görsel-only mesajları atlar; prompt geçmişi ve sohbet önizlemesi boş metinli görsel kayıtlarda "Karakter: " yerine "[görsel]" yer tutucu yazar; katalogda yüklü modelin "yükle" düğmesi pasifleşmez artık ve depolanan görsel modele "Sil" düğmesi eklendi; sohbet görselleri (yol+boyut) anahtarlı bitmap cache ile yeniden çizim başına tekrar çözülmez, sayfadan çıkışta geri dönüştürülür.
- versionCode 28 / 0.18.1-alpha1.

## v0.18.0-alpha1 — 2026-11-06 (YENİ — Görsel motor + sohbette görsel + 18+ görsel model bölümü)

- **Yerel görsel motoru**: uygulama paketi artık `stable-diffusion.cpp` tabanlı `libstable-diffusion.so` + `libsd-jni.so` JNI köprüsünü (arm64-v8a) taşır. İlerleme geri araması, iptal (`sd_cancel_generation`) ve PNG çıktısı JNI katmanında. Metin motorundan tamamen bağımsızdır; her şey cihaz içinde çalışır.
- **Sohbette görsel üretimi**: composer'daki ✨ düğmesi görsel üretim sayfası açar — fikir + "istenmeyenler" girişi, 512×512 tek kare, ilerleme canlı adım sayacı ("12/25") ile chatStatus'ta görünür; ➤ düğmesi üretimi iptal eder. Üretilen görsel anlatıcının/karakterin mesajı olarak kayıt ve sohbete görsel baloncuk düşer.
- **Kullanıcı fotoğraf gönderimi**: composer'daki 📷 düğmesi fotoğraf seçer; gönderilen mesaj görsel baloncuğu taşır. Dürüstlük notu: paketli metin motoru görselleri göremez — fotoğraf yalnızca sohbet kaydında saklanır, motor sahte "görme" yanıtı üretmez.
- **Model kataloğunda "Görsel" sekmesi** (sansürlü): SD-Turbo (4 adım, ~½ dk sınıfı) ve Dreamlike-Diffusion 1.0. İndirme akışı metin modellerle aynı: https-only, dosya adı sertleştirmesi (`.safetensors` kabulü eklendi), parça devamı (HTTP 206 doğrulamalı) ve **gerçek LFS SHA-256 doğrulaması**.
- **Sansürsüz görsel bölümü (18+)**: aynı dört eşikli kapı (kutucuk + yaş + birebir onay kelimesi + 10 sn geri sayım) **görsel modeller için de** zorunlu; MeinaMix V3.0 ve MeinaHentai v2 kartları bu bölümde. Dosyalar uygulamanın özel alanındaki `images/` alt klasörüne iner; hiçbir şey otomatik yüklenmez.
- **Doğrulanmış model özetleri** (2026-11-04, HF paths-info API): sd_turbo.safetensors 5.21 GB / `3f067a1b…36549`; dreamlike-diffusion-1.0.safetensors 2.13 GB / `c86a1a99…12983d`; meinamix_meinaV30BakedVAE.safetensors 3.85 GB / `2dc286ef…bd3f25`; meinahentai_v2.safetensors 2.13 GB / `e7640356…452e7b4`.
- **Kalıcılık**: `Message` sınıfına opsiyonel `image` alanı eklendi; `PersistenceCodec` depolama ve yedek blokları görsel yolunu (`"image"`) yazar/okur — geriye dönük uyumlu, eski yedekler sorunsuz açılır.
- Not: Görsel üretim telefon CPU'sunda yavaştır; kartlar gerçekçi süre yazar (SD1.5 tabanlılar dakikalar sınıfı). GPU/Vulkan henüz kullanılmaz.
- versionCode 27 / 0.18.0-alpha1.

## v0.17.0-alpha1 — 2026-11-04 (Minimal UI yenilemesi · NSFW kapısı sertleştirmesi)

- **Minimal UI yenilemesi (geniş)**: tasarım dili sadeleştirildi — kartlar çerçevesiz, yumuşak 22–24 yarıçaplı düz yüzeylere geçti; bölüm başlıkları küçük, harf aralıklı, soluk (sessiz hiyerarşi); alt gezinme metin-bazlı hap bar'a döndü (seçili sekme aksan füme vurgulu); ayar satırları dolgusuz buton yerine sakin liste satırı; bilgi kartlarındaki büyük harf rozet kalktı; hero, tile, karakter hücreleri ve sohbet composer'ı aynı yumuşak dile geçti; sayfa başlıkları daha ferah tipografiyle (26–22 sp) eşleşti; sohbette ayrı çizgi kaldırıldı.
- **Sansürsüz (18+) kapısı sertleştirdi**: artık dört eşik var: uyarı kutucuku, sayısal yaş girişi, ekranda görülen onay kelimesinin **birebir yazılması** ("onaylıyorum" / EN "I confirm") ve **10 saniyelik geri sayım** (Onayla düğmesi 10'dan geriye sayılarak, dolmadan etkinleşmez). Onaylamayan hallerde diyalog açık kalır ve dürüst geri bildirim gösterilir.
- **Onay artık kalıcı değil**: `uncensored_ack` pref'i kullanımdan kaldırıldı; açılma yalnızca mevcut uygulama oturumunda geçerli. Uygulama her kapanıp açıldığında kapı yeniden sorulur — bir kere geçmek yetmez.
- **Görsel modeller**: bu sürüme dahil edilmedi; sohbet içinde görüntü üretimi (yerel stable-diffusion hattı) ayrı bir planlama konusu olarak sonraki alfa sürümlerine bırakıldı.
- versionCode 26 / 0.17.0-alpha1.

## v0.16.0-alpha2 — 2026-11-04 (İngilizce kapsamı genişletildi — English mode arayüzü eksiksiz)

- **İngilizce kapsamı genişletildi**: sözlük ~245 anahtardan ~400 anahtara taşındı. English modunda artık tüm ana ekranlar İngilizceye dönüyor: karakter listesi ve düzenleyici, grup düzenleyici, sohbet listesi, sohbet üst başlığı, model yönetimi, katalog kartları ve hız testi, yedek/geri yükleme diyaloğu ve bildirimleri, konuşmacı seçici, 18+ kapısı, ana sayfa hero başlığı ("Hikâyen\nburada başlıyor." → "Your story starts here.") ve "KENDİ DÜNYANI KUR" köşe imlesi dâhil.
- **Çağrı yerleri sarmalandı**: label/button/field/confirm merkezî çevirmeye ek olarak doğrudan setText / Toast / AlertDialog / modelStatus setMessage noktaları L10n.s ile sarıldı; bileşik dizgeler parça anahtarlarla (" yüklendi.", " · boş: " vb.) çevrilir.
- **İndirme motoru durumları çevrildi**: ModelDownload.kt içindeki ilerleme/hata mesajları (dosya boyutu uyuşmazlığı, depolama yetersizliği, SHA-256 doğrulaması, https zorunluluğu, sunucu kodu) İngilizce sözlüğe eklendi.
- **Katalog "why" açıklamaları**: CPU / GPU / sansürsüz kartlarındaki model öneri metinleri İngilizceye çevrildi; dil değişince kartlar güncel metni gösterir.
- **Küçük düzeltme**: "Karakter yazıyor…" göstergesi artık ayrı "Bir karakter" anahtarı kullanır; alt gezinmedeki çoğul "Characters" etiketiyle çakışmaz.
- versionCode 25 / 0.16.0-alpha2.

## v0.16.0-alpha1 — 2026-11-04 (YENİ — tek Ayarlar ekranı · prompt dil seçenekleri · özel AI teması · sıkılaştırılmış 18+ kapısı)

Kullanıcı bildirimleri doğrultusunda büyük paket:

- **Tek Ayarlar ekranı**: alt gezinmede iki ayrı sekme (AI / Görünüm) yerine tek **"⚙ Ayarlar"** sekmesi. Ayarların beş bölümü altında toplanır: Dil / Language, Model (yükleme + katalog), Yanıt tarzı (prompt dilleri, AI teması, hafıza ve zincir), Görünüm (GUI teması) ve Veri yedeği. Her buton açık bir alt ekrana gider; aynı ayar artık birden fazla yerden girilmez.
- **Prompt dilleri ekranı (yeni)**: genel AI talimatı ve sistem promptu ayrı ayrı seçilebilir: **Varsayılan (Türkçe) / Varsayılan (English) / Motor varsayılanı (yalnızca sistem promptu) / Özel**. Özel seçtiğinde yazdığın metin kullanılır; eski kullanıcının kayıtlı talimatları otomatik "Özel" kaynağa taşınır. Her değişim model bağlamını sıfırlar.
- **AI temaları arttı** (+5 tema: Buhar punk, Sürreal rüya, Savaş ve entrika, Sıcak slice of life, Uzun yol macerası) ve **Özel tema**: "Özel tema (kendin yaz)" ile kendi tema adını ve prompt talimatını yazıp tüm RP sohbetlerinde kullan.
- **Sıkılaştırılmış 18+ kapısı**: diyalog artık iki zorunlu onay ister: kutucuk **"Uyarıyı okudum; 18 yaş üstü olduğumu onaylıyorum"** + yaş girişi. Kutucuk onaysız ya da yaş 18 altı olduğunda diyalog kapanmaz ve uyarı toast'ı gösterilir — uyarı atlama imkânı yok.
- **İngilizce kapsam genişlemesi**: Ayarlar ekranı, navigasyon, prompt-dilleri, tema seçici, model yönetimi sözlükte eklendi; katalog model açıklamaları ("why") Türkçe kalır (dokümante kısmi kapsam).
- versionCode 24 / 0.16.0-alpha1.

## v0.15.0-alpha1 — 2026-11-03 (YENİ — Sansürsüz model bölümü, 18+ yaş kapısı)

Kullanıcı isteği: **Model önerileri** ekranına üçüncü sekme **"Sansürsüz"** eklendi. Kilitli başlar; açmak için yaş girişi gerekir:

- **Yaş kapısı**: sekmeye ilk dokunuş ta bir diyalog açar — nümerik yaş girişi. **18 ve üstü onaylanır** ve seçim cihazda saklanır (prefs `uncensored_ack`; yalnızca yerel, hiçbir sunucuya gitmez). 18 altındaki girişler dürüstçe reddedilir; bölüm açılmaz.
- **Katalog (3 model, Qwen tabanlı abliterated GGUF)** — her kartta kırmızı **"SANSÜRSÜZ · 18+"** rozeti ve dürüst hız tahminleri:
  - Huihui-Qwen3.5-4B (2,7 GB) — denge önerisi
  - Qwen3-0.6B ablit. (0,4 GB) — en hızlı giriş
  - Huihui-Qwen3-8B v2 (5,0 GB) — en yüksek kalite, CPU'da yavaş
- **Doğrulama**: tüm URL'ler Hugging Face paths-info API'den LFS **SHA-256** ile teyitli (asla uydurulmaz); HTTP resume desteği HEAD ile kontrol edildi. G4 indirme doğrulaması (indirme sonrası digest kontrolü) bu liste için de geçerli.
- **Sorumluluk görünürlüğü**: sekme başlığında dürüst uyarı — abliterated = filtre katmanı çıkarılmış eğitim; uygulama hiçbir sohbet verisini genel ağa göndermez, çıktı işleme cihazda kalır, sorumluluk kullanıcıya aittir.
- 1 yeni katalog testi (abliterated listesi bütünlüğü); mevcut 6 test artık üç listeyi de kapsıyor.
- versionCode 23 / 0.15.0-alpha1.

## v0.14.0-alpha3 — 2026-11-03 (YENİ — İngilizce dil seçeneği)

Kullanıcı isteği: İngilizce modeller RP'de daha iyi. Tek anahtar iki şeye birden geçer:

- **Dil / Language bölümü** — AI ayarları ekranında **"Türkçe"** ve **"English"** düğmeleri. English seçilince uygulama arayüzü sözlük üzerinden İngilizce olur (başlıklar, gezinme, sohbet, düzenleyiciler, AI ayarları kartları) ve model promptu tamamen İngilizce yazılır: `General AI instruction:`, `Scene:`, `User identity:`, `Recent scene history:` + `User:` geçmiş etiketi, kural kuyruğu `Reply in English and stay within the scene.` AI tema adları ve talimatları da promptta İngilizce'dir. Değişim yapılırken model bağlamı sıfırlanır.
- **Kapsam notu (düürst alfa)**: sözlük tam kapsamlı değildir; çevrilmemiş/dinamik dizgeler Türkçe kalır. Karakter metinleri, hazır karakterler ve kullanıcının kendi talimat Promptları her zaman kullanıcının yazdığı dilde kalır.
- **Prompt biçimi korunur**: eski Türkçe prompt bayt bayt aynıdır — `RoleplayPrompt` yeni `english` bayrağı yalnızca ayrı bir aşırı yüklemede; altı kilitli biçim testi değişmedi, 2 yeni İngilizce mod testi eklendi.
- versionCode 22 / 0.14.0-alpha3.

## v0.14.0-alpha2 — 2026-11-03 (DÜZELTME — "AI profili eklenmiyor")

- **Fotoğraf seçimi artık düzenleyiciyi yeniden kurmuyor** (cihaz bildirimi):
  - Belirti: fotoğraf seçildikten sonra düzenleyici kapatılıp yeniden açılıyordu; yeni karakter akışında bu, yazılan tüm alanları ve seçilen fotoğrafı birlikte kaybediyordu — "profil eklenmiyor" bildiriminin kaynağı.
  - Düzeltme: karakter düzenleyicisinde seçim, "Fotoğraf: ✓ hazır" durum satırını yerinde günceller; persona düzenleyicide önizleme paneli yerinde değişir. Yazılan metinler korunur; **Kaydet** düğmesine basıldığında foto yolu karaktere / kullanıcı profiline kaydedilir.
- versionCode 21 / 0.14.0-alpha2.

## v0.14.0-alpha1 — 2026-11-03 (ALFA — kullanıcı personası, fotoğraflar, hazır karakterler)

Kullanıcı isteğiyle üç yeni özellik. Veri ve üretim davranışı korunur; alpha3'ün düzeltmeleri değişmez.

- **Kullanıcı personası**: Karakterler ekranının en üstündeki **"Sen (kullanıcı profili)"** kartından adını, kendini tanıtımını ve profil fotoğrafını kaydedebilirsin. Persona tanımlı değilken hiçbir şey değişmez; tanımlıysa model promptuna **"Kullanıcı kimliği: ad — tanıtım"** bloğu girer (taze sahnede tam blok, devam eden sahnede kompakt kimlik satırı). `RoleplayPrompt` yeni isteğe bağlı parametreyi kabul eder; eski imza kalır ve kilitli prompt biçimi testleri değişmedi.
- **Profil fotoğrafları (bot + kullanıcı)**: Karakter düzenleyicide "Profil fotoğrafı seç / kaldır"; persona düzenleyicide aynı yetenek. Fotoğraf 512 px'e küçültülmüş PNG kopya olarak gizli depolamada (`filesDir/avatars/`) yaşar; seçtiğin kaynak dosyaya dokunulmaz. Fotoğrafı olan karakterin avatarı şuralarda görünür: sohbet başlığı, mesaj balonları, grup kartındaki yüz, karakter ızgarası/kartları, kullanıcı profili kartı. Fotoğraf yoksa harf-çemberi avatar aynı şekilde kullanılır.
- **Hazır karakterler**: Karakterler ekranında **"Hazır karakterler"** bölümü — altı Türkçe RP karakteri (dedektif, tamirci, arşivci, denizci, çizgi roman sanatçısı, emekli aktris). Tek dokunuşla listene normal bir karakter olarak kopyalanır; kopya tümüyle düzenlenebilir. Katalog bütünlüğü testle kilitli (isim tekilliği, alan dolgunluğu, örnek diyaloğun konuşmacı etiketi).
- **Depolama**: karakter kaydında opsiyonel `avatar` alanı (`PersistenceCodec`); persona `user_persona` anahtarında JSON. Eski yedek ve kayıtlar uyumlu kalır (alan yoksa boş avatar).
- versionCode 20 / 0.14.0-alpha1.

## v0.13.0-alpha3 — 2026-11-03 (DÜZELTME — akış balonu hatası, cihaz bildirimi)

- **Kritik düzeltme — üretilen yanıt balon balon şişiyordu** (alpha2 kullanıcı bildirimi; teşekkürler):
  - Belirti: akış sırasında mevcut mesaj güncellenmiyor, her parçada altına **kilitli kopyaları** düşüyordu; yalnızca en alttaki tam metni tutuyordu.
  - Kök neden: balon, `addMessage` içinde avatar sırasına sarıldığı için balon TextView'ın doğrudan ebeveyni satırdır, `content` değil. Akışın "balon hâlâ ekranda mı" denetimleri (`getParent() == content`) bu yüzden hiçbir zaman eşleşemiyordu → her token'da yeni balon. Hata v0.8.0'ın avatar-sıra tasarımından itibaren yapılıydı; birim testler UI yolunu kapsamadığından yakalanmamıştı, telefon'da gerçek kullanımda bugün görüldü.
  - Düzeltme: `balloonInContent()` ballo *satırının* ekranda olup olmadığını doğrular; akış (onToken), sonlandırma (finish/kesme/bitiş) ve boş-balon temizliği aynı yardımcıya geçti — **tek balon tekrar yerinde büyür**.
- **İkinci düzeltme — "Daha eski mesajlar" düğmesi** : doğru ebeveyne (satır) taşıma; yanıltıcı balon penceresinde taşar ve "child already has a parent" çökmesi riski kapanır.
- versionCode 19 / 0.13.0-alpha3, user agent 0.13.0-alpha3.

## v0.13.0-alpha2 — 2026-10-30 (ALFA — görsel sistem parlatması)

> **Hâlen bir ALFA sürümüdür** (Pre-release): alpha1'in geri bildirimine göre görsel sistem yeniden gözden geçirildi. Uygulama görünmeye devam ediyor; veri ve üretim davranışı değişmedi.

- **Yüzen alt gezinme**: tam genişlik şerit yerine kenarlardan boşluklu, 28 dp köşeli yuvarlatılmış gezinme kartı (modern sohbet uygulaması deseni); seçili öğe artık kutu vurgusuyla değil yerinde küçük bir vurgu noktasıyla belirtilir.
- **Temiz başlık çubukları**: her sayfada tekrarlayan "LOCALRP · YEREL HİKÂYELER" ibaresi kaldırıldı; sayfa başlığı büyütüldü (26–27 dp), altında sessiz açıklama satırı. Ana sayfa markası hero kartında yaşamaya devam eder.
- **Sessiz geri düğmeleri**: iç sayfaların ve sohbetin geri düğmesi vurgu hapı yerine nötr kart renginde yuvarlak düğme oldu.
- **Düğme tipografisi**: birincil/ikincil düğme metinleri kalın punta + hafif harf aralığı; stadyum biçim ve 48 dp dokunma hedefi (alpha1'de verildi) korunur.
- **Sohbet girdi çubuğu**: yazma alanının oturduğu şerit karta değil sayfa zeminine alındı; hap girdi kutusu ve dairesel gönder düğmesi korunur.
- versionCode 18 / 0.13.0-alpha2, user agent 0.13.0-alpha2.

## v0.13.0-alpha1 — 2026-10-30 (ALFA — arayüz yeniden inşası)

> **Bu bir alfa sürümüdür** (GitHub'da Pre-release işaretli): arayüzün yeni tasarım dili ilk kez bir sürümde tam uygulanıyor ve cihaz üzerinde görsel doğrulama henüz yapılmadı. Görünümdeki hataları bekliyoruz; sorun bulursanız GitHub'da issue açın. Veri saklama, üretim akışı ve indirme doğrulama davranışı v0.12.0 temeline dayanır; değişen yalnızca görsel katmandır.

- **Arayüz yeniden inşası — Faz 2 (aynı dil, tüm kurulum ekranları)**:
  - Tüm düğmeler stadyum (hap) biçimine alındı: birincil doldurulmuş vurgu, ikincil konturlu vurgu, tehlike yumuşak kırmızı — 26 dp köşe, 48 dp dokunma hedefi (Karakterler, Grup düzenleyici, AI ayarları, Görünüm, Model kataloğu dahil).
  - Kurulum ekranlarının metin/çok satırlı alanları 18 dp köşeli yumuşak kutulara alındı; kartlar 20 dp köşeye yükseltildi.
  - Ekran içerikleri ve veri davranışı korundu; değişim yalnızca görsel katman.
- **Faz 1** (aynı faza dahil bir önceki iş grubu): altı tema paleti 0'dan, mesaj balonlarının C.AI-modern dile geçişi, sohbet girdisinin hap biçimi, Ana ekran ve RP Sohbetleri'nde yan yana başlangıç döşemeleri.
- **G4 — indirme SHA-256 doğrulaması** ve **G5 — GPU kartlarının dürüst uyarısı**: ayrıntıları aşağıdaki "Unreleased" bölümünde taşınan notlarda.

## Unreleased (yapılmakta)

- **G4 — indirilen model sayısal olarak doğrulanır**: katalogdaki her girdi, dosyanın Hugging Face LFS işaretçisinden **gerçekten çekilen** resmî SHA-256 özetini taşır (uydurulmadı; 2026-10-30 tarihli). İndirme bittiğinde `DownloadPolicy` dosyayı bu özete karşı doğrular; eğer eşleşmezse indirilen dosya silinir — bozuk ya da müdahale edilmiş bir indirme artık "boyutuna güvenilerek kurulu sayılamaz". `ModelCatalogTest` 64 haneli özet muhafızını kazandı (6 test).
- **G5 — GPU kartları dürüst davranır**: paketli kapalı motor yalnızca CPU'dur; GPU sekmesindeki kartlar artık "⚠ Bu motorda çalışmaz — paketli motor yalnızca CPU; Vulkan/MLC motoru gerekir." uyarısını gösterir, hız çipi "GPU  motor yolu yok" olur ve İndir/Modeli yükle düğmeleri pasifleştirilir — çalışmayacak bir modelin indirilmesi engellenir.
- **Arayüz yeniden inşası — Faz 1 (ekran dili: modern/minimal, Character.AI referanslı)**:
  - **Yeni renk sistemi**: altı tema (Lavanta, Okyanus, Orman, Gölge, Deniz Gecesi, Gün Batımı) token düzeyinde yeniden yazıldı — koyu temalar gerçek-siyah ana sayfa arka planına geçti, vurgu renkleri modernleştirildi (ör. Gölge #9B7BFF, Lavanta #7C5CFC); tema kimlikleri ve adları korunarak tüm ekranlar tek geçişte modernleşir.
  - **Mesaj balonları**: karakter mesajları 30 dp avatar + düz yumuşak kart (kontur yok, 20 dp köşe) + vurgu renkli konuşmacı adı; kullanıcı balonları sağa ağırlıklı, yumuşatılmış vurgu dolgusu — uzun basma (kopyala) ve akış/temizlik davranışı değişmedi.
  - **Sohbet alanı**: girdi alanı hap (stadyum) biçimine alındı (26 dp köşe), konuşmacı seçici "Devam" ve `⋯` düğmeleri pill diline geçti (30 dp köşe, 44 dp dokunma hedefi); dairesel 46 dp gönder/durdur düğmesi korundu.
  - **Ana ekran ve RP Sohbetleri**: üstte yığılı iki düğme yerine yan yana iki başlangıç döşemesi ("Tek karakterli RP" / "Grup RP", ikon + başlık + kısa açıklama); kalan sohbet/karakter kartları zaten C.AI-kart dilindeydi ve kalır.
  - Kapsam notu: bu faz Ana ekran, Sohbetler ve sohbet ekranını kapsar; karakter oluşturucu, Grup, Modeller, Katalog, Görünüm ve Gelişmiş AI ekranları sonraki fazlarda aynı dile geçirilir.

- **İnceleme raporu yanıtı (2026-10-30, "LocalRP Proje İnceleme Raporu" belgeleri)**:
  - **B1 — taslak yazımı kısıtlandı**: üretilen metin artık her token'da değil, en fazla **1,5 saniyede bir** kaydedilir (ilk token daima kayıt; durdurma/bitiş yolları anahtarı siler). Önceki halinde tam metin her token'da yeniden serileştirildiği için yanıt uzadıkça her yazım ağırlaşıyordu; bu ana iş parçacığı yükü kaldırıldı.
  - **B2 — dokümanlar güncellendi**: `ARCHITECTURE.md` artık tüm saf sınıfları listeler (Store, RoleplayPrompt, DownloadPolicy, RuntimePolicy, PersistenceCodec, ResponseCleanup, ChatWindow, Avatar); "her erişimde JSON yeniden ayrıştırıldı" ve "yedekleme sonraki adım" gibi v0.7.4/v0.11.0'da bayatlamış ifadeler gerçeği belirten düzeltmelerle değiştirildi. `README.md`'ye yedekleme, ölçeklenebilirlik, test/CI ve lisans satırları eklendi.
  - **B11 — `THIRD_PARTY_NOTICES.md` eklendi**: paketli motor (`com.arm:ai-chat`, Apache-2.0), coroutines, yalnızca-test bağımlılıkları ve katalog modellerinin lisans bildirimleri.
  - **B12 — CHANGELOG yazım düzeltmeleri**: "SAĞLANIR" → "gerçekleşir"; "biyolojik dosya sınırı" → boyut sınırı ve bellek tamponu notu; v0.8.0 girdisindeki anlamsız ifadeler düzeltildi.
- **B5'in "doğrulanmalı" sorusuna kod kanıtı**: yedek dosyası okuma sırasında belleğe **tamponlandı** (`MainActivity:1648`, 256 MB sanite üst sınırı); gerçek yedeklerin boyutunun çok altında kalmak zorunda olmadığından orta-vadede Room'a geçiş doğru karar olarak kalıyor.
- **G0 — Akan yanıt düzeltmeleri** (acil hata görevi, kod incelemesiyle):
  - Akış sırasındaki ara balon artık final mesajla **aynı temizlik fonksiyonunu** (`ResponseCleanup`) kullanır: modelin yansıttığı "Ad:" öneki, yıldızlı varyantlar (`*Ad*:`, `*Ad* metin`) ve motor özel token'ları (`<|im_end|>` vb.) hiçbir ara balonda görünmez. Konuşmacının bir başkasına hitabı (ör. "Ashe burada, iyi misin?") korunur.
  - Sohbetten çıkıp geri dönüldüğünde (re-entry) taslak balon `generationViews` haritasıyla yeniden bağlanır; ikinci bir balon oluşmaz, tek balon büyümeye devam eder.
  - **Çökme dayanıklılığı**: üretilen metin her token'da taslak olarak kaydedilir; süreç ölürse bir sonraki açılışta yarım yanıt **tek kez** normal mesaj olarak geri konur (mükerrere karşı denetimli; `finish`'te taslak silinir).
  - Not: tek-balon güncelleme deseni v0.7.0'dan beri yapıda; "her token yeni balon" davranışı mevcut kodda yeniden üretilemedi. Kabul kriterindeki iki gerçek boşluk (ara temizlik + re-entry/çöküş taslağı) bu sürümde kapatıldı.
- **6 yeni test** (`ResponseCleanupTest`): etiket temizliği (iki nokta, tire, tam genişlik iki nokta, yıldızlı biçimler), özel token temizliği, boş satır küçültme, boş girdi. Toplam **63 test / 0 hata**.

## v0.12.0 — 2026-10-28

- **Uzun sohbet çizim penceresi** (Görev 08 — performans denetimi sonrası tek hedefli değişiklik):
  - **Ölçüm dürüstlüğü**: telefon bu oturumda bağlı olmadığından cihaz ölçümleri (model yükleme süresi, ilk token gecikmesi, nokta RAM) **alınamadı** ve sayı uydurulmadı. Kod okumasıyla sıralanan en ağır UI hot spot: çok uzun bir RP geçmişinin tamamının tek seferde görünürlüğe alınması (O(n) view, ekran açılışında).
  - **Çözüm**: sohbet açılışı artık yalnızca en yeni 40 mesajı çizer; daha eskiler, sohbetin üstündeki **"Daha eski mesajlar (n)"** düğmesiyle 40'lık dilimler hâlinde kullanıcının isteğiyle yüklenir (sıra korunur, en üstte eskiler tükendiğinde düğme kaybolur). `ChatWindow` saf yardımcı sınıfı + 3 test pencere aritmetiğini kilitler.
  - Yan etki yok: mesaj akışı, taslaklar, otomatik üretim ve kalıcılık değişmedi; küçük sohbetler eskisi gibi tam çizilir.
  - **Uygulamada ölçüm yolu**: Modeller sekmesindeki **Motor testi (bench)** düğmesi model yükleme ve üretim hızını gerçek cihazında ölçer; telefon bağlandığında rakamlar bu yoldan alınacak ve v0.11.x notundaki donanım testi listesiyle birlikte raporlanacaktır.
- **Sonuç** (kod tarafı, ölçümsüz): 41+ mesajlı sohbetlerde ekran açılışı 40 mesaj çizer; 10.000 mesajlık soyut durumda bile açılış maliyeti sabittir, tarama maliyeti kullanıcının yöneldiği kadar.
- **3 yeni test** (`ChatWindowTest`: pencere sınırı, ilk gecikme mesajı, üst sınır olmaması). Toplam **57 test / 0 hata** (yerel çalıştırma; CI'da aynı set).
- versionCode 16 / 0.12.0, user agent 0.12.0.

## v0.11.0 — 2026-10-28

- **CI ve test tabanı** (Görev 06, APK'da görünmez değişiklik): GitHub Actions workflow'u (push/PR × unit testler + debug derleme, `contents: read` izni, APK yayını yok) ve katalog bütünlük testleri eklendi. Toplam test sayısı 43 → 48 / 0 hata.

- **Yedekleme ve geri yükleme**: 0.7.4'ten beri beklenen özellik (sözlü verilen taahhüt) tamamlandı:
  - **Görünüm → Veri yedeği** bölümünden, SAF ile seçtiğin herhangi bir klasöre `localrp-yedek-<tarih>.json` dışa aktarımı: tüm karakterler, sohbetler ve tamamı geçmişler tek dosyada. Dosya cihazdan çıkmaz; aktarım yalnızca senin onayınla gerçekleşir (uygulama hiçbir veriyi otomatik göndermez).
  - **Geri yükleme**: aynı kimlikli karakter/sohbetler güncellenir, yeni olanlar eklenir. Geçmişler yalnızca yedekteki kopya **kesinlikle daha uzun** (gerçek devamlar) olduğunda değiştirilir; kısa/eski yedekler hiçbir şeyi küçültmez, tekrarlı içe aktarım mükerrer çoğaltma yapmaz.
  - **Güvenlik**: mevcut kalıcılık verisi okunamıyorsa geri yükleme reddedilir (startup raporu onarım yolu korunur); bozuk tek kayıtlar (0.7.4 codec'inin aynen davranışıyla) atlanır; 256 MB boyut sınırı (yedek okuma tamponunun bellek sınırlaması; gerçek yedekler çok daha küçüktür).
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
  - **Sohbet ekranı**: başlıkta sohbet avatarı, karakter mesaj baloncuklarının solunda 32 dp avatar, konuşmacı adı ve saat balon başlığında; kullanıcı baloncukları avatarsız (aynı davranış).
  - **Mesaj baloncukları**: konuşmacı adı aksan rengiyle daha görünür; pad-konumlar cAI ritmine göre yeniden ayarlandı.
  - **Gönderici**: tam genişlik "Gönder" düğmesi yerine pill giriş alanı + 46 dp dairesel ➤ gönder düğmesi; üretim sırasında düğme "■" durdur biçimine dönüyor (aynı davranış, aynı kilit).
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
