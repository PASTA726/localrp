# Changelog

All notable LocalRP changes. The app targets Turkish-speaking users; entries are bilingual when a change matters for English readers.

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
