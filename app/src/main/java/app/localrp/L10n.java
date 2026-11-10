package app.localrp;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight in-app translator. The app's UI strings are literal Turkish at call
 * sites; to switch the visible language we look those exact literals up in this
 * dictionary and substitute English. Unmapped strings (content typed by users, preset
 * texts, dynamic parts) pass through untouched, so partial coverage is safe by design.
 * Also flags the AI/RP language: when English is on, RoleplayPrompt writes the full
 * engine prompt in English and tells the model to reply in English — English-tuned
 * models are markedly better at RP.
 */
public final class L10n {
    private L10n() {}

    public static boolean english = false;

    public static void refresh(String prefLanguage) {
        english = "en".equals(prefLanguage);
    }

    /** Translates a known Turkish literal; unknown strings pass through. */
    public static String s(String tr) {
        if (!english || tr == null) return tr;
        String en = EN.get(tr);
        return en != null ? en : tr;
    }

    /** Chat display label for the user's own messages ("Sen" → "You" in English mode). */
    public static String speaker(String speakerLabel) {
        return english && "Sen".equals(speakerLabel) ? "You" : speakerLabel;
    }

    static final Map<String, String> EN = new HashMap<>();

    static {
        // ---- section headings & actions
        EN.put("Son sohbetler", "Recent chats");
        EN.put("Tümünü gör", "See all");
        EN.put("Karakterlerin", "Your characters");
        EN.put("Yönet", "Manage");
        EN.put("Karakterler", "Characters");
        EN.put("Bir veya daha fazla", "One or more");
        EN.put("Çalışma durumu", "Status");
        EN.put("Model dosyası", "Model file");
        EN.put("Katalog", "Catalog");
        EN.put("Gelişmiş AI", "Advanced AI");
        EN.put("AI teması", "AI theme");
        EN.put("Yanıt ayarları", "Reply settings");
        EN.put("Motor testi", "Engine benchmark");
        EN.put("Dil / Language", "Language / Dil");
        EN.put("Veri yedeği", "Data backup");
        EN.put("Profil fotoğrafı", "Profile photo");
        EN.put("Hazır karakterler", "Ready-made characters");
        EN.put("Tek dokunuşla ekle", "One-tap add");
        EN.put("Sohbet", "Chat");

        // ---- buttons
        EN.put("＋  Tek karakterli RP", "＋  Solo RP");
        EN.put("＋  Grup RP", "＋  Group RP");
        EN.put("＋  Yeni karakter", "＋  New character");
        EN.put("Kaydet", "Save");
        EN.put("Değişiklikleri kaydet", "Save changes");
        EN.put("Sohbeti başlat", "Start chat");
        EN.put("Karakter oluştur", "Create character");
        EN.put("Karakteri sil", "Delete character");
        EN.put("Profil fotoğrafı seç", "Pick profile photo");
        EN.put("Fotoğraf seç", "Choose photo");
        EN.put("Fotoğrafı kaldır", "Remove photo");
        EN.put("Profili kaydet", "Save profile");
        EN.put("＋ Ekle", "＋ Add");
        EN.put("Telefondan GGUF seç", "Pick a GGUF from the phone");
        EN.put("Model önerilerini gör ve indir", "View and download suggestions");
        EN.put("Gelişmiş AI ayarları", "Advanced AI settings");
        EN.put("GUI temasını değiştir", "Change UI theme");
        EN.put("AI temasını değiştir", "Change AI theme");
        EN.put("Talimatı kaydet", "Save instruction");
        EN.put("Hız testini başlat", "Start speed test");
        EN.put("Model kopyalarını yönet", "Manage model copies");
        EN.put("Yedeği dışa aktar", "Export backup");
        EN.put("Yedeği geri yükle", "Restore backup");
        EN.put("Sohbet geçmişini temizle", "Clear chat history");
        EN.put("Grubu sil", "Delete group");
        EN.put("Sil", "Delete");
        EN.put("Vazgeç", "Cancel");
        EN.put("Temizle", "Clear");
        EN.put("Tamam", "OK");

        // ---- empty states & empty hints
        EN.put("Henüz bir RP yok", "No story yet");
        EN.put("Bir karakter seçerek bire bir hikâye başlat veya grup kur.", "Start a solo story with a character or set up a group.");
        EN.put("Hikâyene karakter ekle", "Add characters to your story");
        EN.put("Kişiliğini ve konuşma tarzını birlikte tanımla.", "Define its personality and voice together.");
        EN.put("İlk karakterini oluştur", "Create your first character");
        EN.put("Adını, kişiliğini ve hikâyedeki yerini belirle.", "Set its name, personality and place in the story.");
        EN.put("Burada henüz RP yok", "No story here yet");
        EN.put("Bire bir hikâye başlat veya grup kur.", "Start a solo story or set up a group.");
        EN.put("RP sahnesi burada başlıyor", "The scene starts here");
        EN.put("Mesajını yaz; seçili karakter otomatik yanıtlasın.", "Write a message; your selected character replies automatically.");
        EN.put("Önce karakter oluştur", "Create a character first");
        EN.put("RP için en az bir karakter gerekli.", "At least one character is required for RP.");

        // ---- editors
        EN.put("Karakter adı", "Character name");
        EN.put("Kısa tanım", "Short description");
        EN.put("Kişilik ve konuşma tarzı", "Personality and speech style");
        EN.put("Karakter talimatları", "Character instructions");
        EN.put("Örnek diyalog", "Example dialogue");
        EN.put("Sohbet adı", "Conversation name");
        EN.put("Adın (boş bırakılırsa \"Sen\" sayılır)", "Your name (empty means \"You\")");
        EN.put("Kendini tanıt (davranışını ve sesini etkiler)", "Introduce yourself (shapes your behavior and voice)");
        EN.put("Bir ad gerekli", "A name is required");
        EN.put("Sohbet adı gerekli", "A conversation name is required");
        EN.put("En az bir karakter seç", "Pick at least one character");
        EN.put("Fotoğraf: seçilmedi", "Photo: none");
        EN.put("Fotoğraf: ✓ hazır", "Photo: ✓ ready");
        EN.put("Fotoğraf: ✓ hazır (Kaydet ile saklanır)", "Photo: ✓ ready (tap Save to store)");

        // ---- chat composer & status
        EN.put("Hikâyeye bir şey yaz…", "Write to the story…");
        EN.put("Sıra: ", "Next: ");
        EN.put("Sıra: Otomatik", "Next: Auto");
        EN.put("  ▾", "  ▾");
        EN.put("Devam", "Continue");
        EN.put("Yanıt üretilemedi", "Reply failed");
        EN.put("Yanıt yarıda kesildi", "Reply was cut off");
        EN.put("Model boş yanıt döndürdü", "Model returned an empty reply");
        EN.put("Yanıt durduruldu.", "Reply stopped.");
        EN.put("yanıt yazıyor…", "is typing…");
        EN.put("Sahneyi başlatmak için dokun", "Tap to start the scene");
        EN.put("RP ayarları", "RP settings");
        EN.put("Sohbet listesine dön", "Back to chat list");
        EN.put("Yeniden dene", "Retry");
        EN.put("Kapat", "Close");
        EN.put("Metni kopyala", "Copy text");
        EN.put("Mesaj kopyalandı.", "Message copied.");
        EN.put("Sen", "You");

        // ---- status text (dynamic tails get composed strings; key parts are static)
        EN.put("Model yükleniyor…", "Model is loading…");
        EN.put("Motor testi sürüyor…", "Engine benchmark in progress…");
        EN.put("Son yanıt: ", "Last reply: ");
        EN.put(" · bağlam %", " · context %");
        EN.put("Sahne hazır · bağlam %", "Scene ready · context %");
        EN.put(" · çevrimdışı", " · offline");
        EN.put("Model yeniden hazırlanacak · yanıt biraz sürebilir", "Model will reload · first reply may take a moment");
        EN.put("Yanıtlar için bir GGUF modeli seç", "Pick a GGUF model to get replies");
        EN.put("Model seçildi · yüklemek için AI ayarları", "Model picked · load it in AI settings");
        EN.put("Model yeniden hazırlanacak · çevrimdışı", "Model will reload · offline");
        EN.put("Çevrimdışı yanıtlar için önce AI ayarlarından model yükle", "Load a model in AI settings for offline replies");
        EN.put("Bire bir karakter RP · cihaz içinde", "Solo character RP · on-device");
        EN.put("Grup RP · cihaz içinde", "Group RP · on-device");
        EN.put("Başka bir sohbette AI yanıt üretiyor…", "The AI is replying in another chat…");

        // ---- AI settings info cards
        EN.put("Cihaz içi AI", "On-device AI");
        EN.put("GGUF modeli llama.cpp ile bu telefonda çalışır. Modeli yükledikten sonra sohbet internet olmadan da sürer.",
                "A GGUF model runs on this phone via llama.cpp. Once loaded, chats continue without internet.");
        EN.put("Model dosyası uygulamanın özel alanına kopyalanır; seçtiğin kaynak dosya silinmez.",
                "The model file is copied into the app's private storage; your source file is kept.");
        EN.put("Cihazdaki model kopyaları: ", "Local model copies: ");
        EN.put(" dosya · ", " files · ");
        EN.put("Düşük değer daha kısa yanıt verir. Bu motor sürümü temperature/top-p ayarlarını dışa açmıyor; yanıt uzunluğu ve sahne temasıyla yönlendirme yapılabilir.",
                "Lower values give shorter replies. This engine does not expose temperature/top-p; shape tone via reply length and scene theme.");
        EN.put("Sohbet değiştirildiğinde model bağlamı sıfırlanır; o sohbetteki ilk yanıt biraz daha uzun sürebilir.",
                "Switching chats resets the model context; the first reply in a chat may take longer.");
        EN.put("Cihaz hızı ölçülüyor. Test bitince sonuç burada gösterilecek.",
                "Measuring your device. Results appear here when done.");
        EN.put("Küçük bir prompt işleme ve 16 token üretme testi çalıştırır; cihazın yaklaşık hızını gösterir.",
                "Runs a small prompt process and 16-token generation; shows the approximate speed of your device.");
        EN.put("Hız testi için önce modeli yükle.", "Load a model to run the speed test.");

        // ---- appearance & backup
        EN.put("Tema sistemi", "Theme system");
        EN.put("Her tema kartları, sohbet balonlarını, gezinmeyi ve sistem çubuklarını tek seferde boyar.",
                "Each theme paints cards, chat bubbles, navigation and system bars in one stroke.");
        EN.put("Seçim anında uygulanır; model bağlamı etkilenmez.", "Applied instantly; the model context is not affected.");
        EN.put("GÜNCEL", "CURRENT");
        EN.put("Açık palet", "Light palette");
        EN.put("Koyu palet", "Dark palette");
        EN.put(" teması uygulandı.", " theme applied.");
        EN.put("Yedekleme", "Backup");
        EN.put("Karakterler, sohbetler ve geçmişler tek bir .json dosyasına yazılır. Dosya tamamen senin kontrolündedir: uygulama hiçbir veriyi otomatik göndermez.",
                "Characters, chats and histories are written to a single .json file. You control it fully: the app never sends data automatically.");

        // ---- persona
        EN.put("  (kullanıcı)", "  (user)");
        EN.put("Kullanıcı personası tanımlı değil — dokunup kendini tanıt", "No user persona yet — tap to introduce yourself");
        EN.put("RP sohbetlerinde mesajların \"Sen\" etiketiyle kalır; model sana \"Kullanıcı kimliği\" bloğunda burada yazdığın bilgilerle davranır.",
                "In RP chats your messages keep the \"You\" label; the AI treats you through the \"User identity\" block from this screen.");
        EN.put("Kullanıcı profili kaydedildi.", "User profile saved.");
        EN.put("Profil kayda geçemedi.", "Could not save the profile.");

        // ---- presets
        EN.put("Beğendiğin hazır karakter kopyalanıp tamamen düzenlenebilir bir karakter olarak listene geçer.",
                "A ready-made character is copied into your list as a fully editable character.");
        EN.put(" karakter listene kopyalansın mı? Kopyalandıktan sonra tamamen düzenlenebilir.",
                " — add this ready-made character to your list? After copying it is fully editable.");
        EN.put(" eklendi.", " added.");
        EN.put("Karakter kaydedilemedi: verisi okunamıyor.", "Could not save the character: data unreadable.");
        EN.put("Değişiklik kaydedilemedi: karakter verisi okunamıyor.", "Could not save changes: character data unreadable.");
        EN.put("Değişiklik kaydedilemedi: sohbet verisi okunamıyor.", "Could not save changes: chat data unreadable.");
        EN.put(" silinsin mi?", " delete?");
        EN.put(" ve sohbeti silinsin mi?", " and its chat delete?");

        // ---- language row itself
        EN.put("Türkçe", "Türkçe");
        EN.put("English (önerilir: RP için daha iyi)", "English (recommended: better RP)");
        EN.put("Yanıt dili İngilizce olarak ayarlandı — model promptları artık İngilizce.",
                "English enabled — prompts are now in English.");

        // ---- AI theme names & instructions (English mode)
        EN.put("Serbest", "Freeform");
        EN.put("Karanlık fantezi", "Dark fantasy");
        EN.put("Gotik atmosfer, tehlikeli büyü ve ahlaki belirsizlik kullan; umut ile karanlık arasında denge kur.",
                "Use a gothic mood, dangerous magic and moral ambiguity; balance hope against darkness.");
        EN.put("Bilim kurgu", "Science fiction");
        EN.put("Teknoloji, uzay, yapay zekâ ve etik gerilim üzerinden somut, tutarlı bir gelecek kur.",
                "Build a concrete, consistent future through technology, space, AI and ethical tension.");
        EN.put("Romantik drama", "Romantic drama");
        EN.put("Duygusal yakınlık, kırılganlık ve gerilimi öne çıkar; duyguları abartmadan göster.",
                "Bring out emotional closeness, vulnerability and tension; show feelings without excess.");
        EN.put("Gizem ve dedektiflik", "Mystery and detective");
        EN.put("İpuçları, şüpheler ve yavaş açığa çıkan sırlar kullan; her yanıt yeni bir iz bıraksın.",
                "Use clues, suspicions and slowly revealing secrets; let every reply leave a new trace.");
        EN.put("Tarihî dönem", "Historical period");
        EN.put("Dönemin dili, kıyafetleri ve toplumsal kurallarına sadık kal; modern ifadelerden kaçın.",
                "Stay faithful to the period's speech, clothing and social rules; avoid modern phrasing.");
        EN.put("Anime / hafif roman", "Anime / light novel");
        EN.put("Canlı duygular, iç monolog ve komik zamanlama kullan; sahneyi abartısız sürdür.",
                "Use vivid emotions, inner monologue and comic timing; keep the scene grounded.");
        EN.put("Korku", "Horror");
        EN.put("Gerilim, belirsizlik ve görünmeyen tehdit yarat; kan ve şiddeti ölçülü tut.",
                "Create tension, uncertainty and unseen threats; keep gore and violence restrained.");
        EN.put("Sahne atmosferi", "Scene tone");
        EN.put("Tema, tüm RP sohbetlerinde sahnenin atmosferini belirler. Değiştirince model bağlamı sıfırlanır.",
                "The theme sets the mood of all RP chats. Changing it resets the model context.");

        // ---- uncensored catalog (18+, age-gated)
        EN.put("Sansürsüz", "Uncensored");
        EN.put("Sansürsüz modeller abliterated (filtre katmanı çıkarılmış) eğitimlidir; 18 yaş üstü içindir.",
                "Uncensored models are abliterated (filter layer removed); 18+ only.");
        EN.put("Uygulama hiçbir sohbet verisini genel ağa göndermez; çıktı işleme tamamen bu cihazda çalışır. Sorumluluk kullanıcıya aittir.",
                "The app never sends chat data online; processing happens entirely on this device. Responsibility lies with the user.");
        EN.put("SANSÜRSÜZ  ·  18+", "UNCENSORED  ·  18+");
        EN.put("Sansürsüz modeller (18+)", "Uncensored models (18+)");
        EN.put("Sansürsüz (abliterated) modeller 18 yaş altına uygun değildir. Devam etmek için yaşını gir.",
                "Uncensored (abliterated) models are not suitable for minors. Enter your age to continue.");
        EN.put("Yaşın", "Your age");
        EN.put("Onayla", "Confirm");
        EN.put("Sansürsüz bölümü bu oturum için açıldı.", "Uncensored section unlocked for this session.");
        EN.put("Onaylamak için kelimeyi yaz: ", "Type the word to confirm: ");
        EN.put("onaylıyorum", "I confirm");
        EN.put("Onay kelimesini tam yazmalısın.", "You must type the word exactly.");
        EN.put("Açılma bu oturum için geçerlidir; uygulama kapandığında yeniden sorulur.",
                "The unlock lasts for this session only; you will be asked again when the app restarts.");
        EN.put("Bu bölüm yalnızca 18 yaş üstüne açık.", "This section is limited to ages 18 and up.");

        // ---- v0.16: single Settings hub + prompt sources + custom theme
        EN.put("Ana sayfa", "Home");
        EN.put("Karakter", "Characters");
        EN.put("Bir karakter", "A character");
        EN.put("Sohbetler", "Chats");
        EN.put("Ayarlar", "Settings");
        EN.put("Dil · model · yanıt tarzı · görünüm · veri", "Language · model · reply style · look · data");
        EN.put("Model", "Model");
        EN.put("Modeli yükle ve yönet", "Load and manage model");
        EN.put("Yanıt tarzı", "Reply style");
        EN.put("Prompt dilleri ve talimatlar", "Prompt languages and instructions");
        EN.put("Sahne hafızası ve zincir", "Scene memory and chaining");
        EN.put("Model yönetimi", "Model management");
        EN.put("GGUF seç, yükle ve belleği izle", "Pick a GGUF, load it, watch memory");
        EN.put("Yanıt biçimi", "Reply shape");
        EN.put("Modeli bellekten kaldır", "Unload model from memory");
        EN.put("Seçili modeli yükle", "Load selected model");
        EN.put("Henüz model seçilmedi", "No model picked yet");
        EN.put("Model seçilmedi", "No model picked");
        EN.put("Hazır · bu işlem için internet gerekmez", "Ready · no internet needed");
        EN.put("Seçildi · yüklemeye hazır", "Picked · ready to load");
        EN.put("Seçildi · yükleme başlatılıyor…", "Picked · loading…");
        EN.put("Model önerileri", "Model suggestions");
        EN.put("Öneri", "Suggestion");
        EN.put("CPU önerileri", "CPU picks");
        EN.put("GPU önerileri", "GPU picks");

        // prompt sources
        EN.put("Prompt dilleri", "Prompt languages");
        EN.put("Genel talimat ve sistem promptu", "General instruction and system prompt");
        EN.put("Genel AI talimatı", "General AI instruction");
        EN.put("Her promptun \"Genel AI talimatı\" satırını seçer: varsayılanlar dile göre belirlenir; özel seçtiğinde kendi metnin kullanılır.",
                "Chooses the \"General AI instruction:\" line of every prompt: defaults follow the language, or use your own text.");
        EN.put("Varsayılan (Türkçe)", "Default (Turkish)");
        EN.put("Varsayılan (English)", "Default (English)");
        EN.put("Özel", "Custom");
        EN.put("AI'nin genel yanıt yönergesi", "The AI's general reply instruction");
        EN.put("Özel talimatı kaydet", "Save custom instruction");
        EN.put("Özel talimat kaydedildi.", "Custom instruction saved.");
        EN.put("Genel talimat tercihleri güncellendi.", "General instruction preference updated.");
        EN.put("Motorun dünya görüşünü belirleyen çekirdek prompt; karakter tanımından önce gelir.",
                "The core prompt shaping the engine's worldview; comes before the character definition.");
        EN.put("Sistem promptu", "System prompt");
        EN.put("Motor varsayılanı", "Engine default");
        EN.put("Özel sistem promptunu kaydet", "Save custom system prompt");
        EN.put("Sistem promptu kaydedildi; model bağlamı yeniden kurulacak.", "System prompt saved; the model context will be rebuilt.");
        EN.put("Sistem promptu tercihi güncellendi.", "System prompt preference updated.");
        EN.put("Motor kavrayışının çekirdeği", "The core of what the engine understands");
        EN.put("Değişiklikler model bağlamını sıfırlar; ilk yanıt biraz daha uzun sürebilir.", "Changes reset the model context; the first reply may take longer.");

        // advanced page
        EN.put("Sahne hafızası", "Scene memory");
        EN.put("Model bağlamının derinliği", "Depth of the model context");
        EN.put("Bağlam davranışı", "Context behavior");
        EN.put("Karakter zinciri", "Character chaining");
        EN.put("Kendiliğinden devam sayısı", "Auto-continue count");
        EN.put("Kapalı", "Off");
        EN.put(" zincir", " chain");
        EN.put(" mesaj", " messages");
        EN.put(" metin", " of text");

        // theme picker + custom theme
        EN.put("AI sahne teması", "AI scene tone");
        EN.put("Özel tema (kendin yaz)", "Custom tone (write your own)");
        EN.put("Özel tema", "Custom tone");
        EN.put("Tema adı", "Theme name");
        EN.put("Sahne talimatı (prompta girer)", "Scene instruction (goes into the prompt)");
        EN.put("Özel temayı kaydet ve uygula", "Save and apply custom tone");
        EN.put("Bir tema adı gerekli.", "A theme name is required.");
        EN.put(" teması seçildi.", " tone selected.");
        EN.put("Yanıt üretilirken tema değiştirilemez.", "Cannot change the tone while a reply is streaming.");
        EN.put("Model yüklenirken tema değiştirilemez.", "Cannot change the tone while the model is loading.");
        EN.put("Motor testi sürerken tema değiştirilemez.", "Cannot change the tone during the engine benchmark.");
        EN.put("Buhar punk", "Steam punk");
        EN.put("Pirinç, buhar ve mekanik ayrıntılar; endüstriyel devrim atmosferini abartmadan koru.",
                "Brass, steam and mechanical detail; keep the industrial-revolution mood without excess.");
        EN.put("Sürreal rüya", "Surreal dream");
        EN.put("Mantık yerine çağrışım kullan: kayan nesneler, değişen mekânlar, rüya mantığı.",
                "Use association over logic: floating objects, shifting places, dream logic.");
        EN.put("Savaş ve entrika", "War and intrigue");
        EN.put("Siyasal baskı, askerî zorunluluk ve güven bunalımı; kimseye sığınak sağlama.",
                "Political pressure, military necessity and broken trust; give no one shelter.");
        EN.put("Sıcak slice of life", "Warm slice of life");
        EN.put("Gündelik küçük anlar, sıcak davranışlar; dram yerine samimiyet taşı.",
                "Everyday small moments, warm behavior; carry intimacy over drama.");
        EN.put("Uzun yol macerası", "Long-road adventure");
        EN.put("Bir hedefe giden gezi sahnesi: yol arkadaşları, bulmacalar ve zorlayıcı seçimler.",
                "A journey toward a goal: fellow travelers, puzzles and hard choices.");

        // stricter 18+ gate
        EN.put("UYARI: Bu modellerde güvenlik filtresi yoktur; 18 yaş altına uygun içerik koruması içermez.",
                "WARNING: these models have no safety filter and no content protection for minors.");
        EN.put("İşleme tamamen bu cihazda, çevrimdışı çalışır; sorumluluk sana aittir. Devam için yaşını gir ve uyarıyı onayla.",
                "Processing runs fully on this device, offline; you take responsibility. Enter your age and acknowledge the warning.");
        EN.put("İşleme tamamen bu cihazda, çevrimdışı çalışır; sorumluluk sana aittir.",
                "Processing runs fully on this device, offline; you take responsibility.");
        EN.put("Uyarıyı okudum; 18 yaş üstü olduğumu onaylıyorum.", "I have read the warning and confirm I am 18 or older.");
        EN.put("Önce uyarıyı onaylaman gerekli.", "You must acknowledge the warning first.");

        // ---- v0.16.0-alpha2: full English coverage sweep
        EN.put("Açıklama yok", "No description");
        EN.put("AI ayarları", "AI settings");
        EN.put("Anlaşıldı", "Understood");
        EN.put("  ·  bağlam ", "  ·  context ");
        EN.put("Bağlam bütçesi", "Context budget");
        EN.put("Bağlam bütçesi modele göre hesaplanacak.", "The context budget is calculated from the model.");
        EN.put("bazı kayıtlar", "some records");
        EN.put("Bilgiler bu telefonda saklanır", "Data is stored on this phone");
        EN.put("bir sohbetin geçmişi", "a chat's history");
        EN.put(" bloğunda burada yazdığın bilgilerle davranır.", " block behaves on the information you write here.");
        EN.put(" · boş: ", " · free: ");
        EN.put("Boyut sınıfı: ", "Size class: ");
        EN.put("BU CİHAZ İÇİN ÖNERİ", "RECOMMENDED FOR THIS DEVICE");
        EN.put("Bu dosya geçerli bir LocalRP yedeği değil.", "This file is not a valid LocalRP backup.");
        EN.put("Bu model şu anda yüklü.", "This model is currently loaded.");
        EN.put("⚠ Bu motorda çalışmaz — paketli motor yalnızca CPU; Vulkan/MLC motoru gerekir.",
                "⚠ Will not run on this engine — the bundled engine is CPU-only; a Vulkan/MLC engine is required.");
        EN.put("Bu sohbetin tüm mesajları silinsin mi?", "Delete all messages in this chat?");
        EN.put("Cihazda kayıtlı model kopyası yok.", "No model copy stored on this device.");
        EN.put("CPU çekirdeği: ", "CPU cores: ");
        EN.put("CPU ve GPU dengelerine göre GGUF seç ve indir", "Pick and download GGUFs balanced for CPU and GPU");
        EN.put("Çevrimdışı", "Offline");
        EN.put("Değişiklikler bir sonraki yanıtta uygulanır.", "Changes apply from the next reply on.");
        EN.put(" · devam edince indirilmiş sayılır", " · counts as downloaded once resumed");
        EN.put(" · indiriliyor…", " · downloading…");
        EN.put("[görsel]", "[image]");

        // ---- message actions / search / markdown (v0.19.0)
        EN.put(" · mesaj", " · message");
        EN.put("Düzenle", "Edit");
        EN.put("Yeniden üret", "Regenerate");
        EN.put("Bu mesajın metni yok; görsel kayıtta saklanır.",
                "This message has no text; the image is kept in the transcript.");
        EN.put("Yanıt üretilirken mesaj değiştirilemez. Önce yanıtı durdur.",
                "Messages cannot change while a reply is generating. Stop it first.");
        EN.put("Mesajı düzenle", "Edit message");
        EN.put("Düzenle: ", "Edit: ");
        EN.put("Metin boş kalamaz; silmek için Sil'i kullan.",
                "Text cannot be empty; use Delete to remove the message.");
        EN.put("Mesaj güncellendi.", "Message updated.");
        EN.put("Bu mesaj ve görseli silinsin mi?", "Delete this message and its image?");
        EN.put("Bu mesaj silinsin mi?", "Delete this message?");
        EN.put("Mesaj silindi.", "Message deleted.");
        EN.put("Yanıt üretilirken mesaj silinemez. Önce yanıtı durdur.",
                "Messages cannot be deleted while a reply is generating. Stop it first.");
        EN.put("Silinemedi: sohbet verisi okunamıyor.", "Delete failed: the chat data is unreadable.");
        EN.put("Yeniden üretilemedi: sohbet verisi okunamıyor.",
                "Regeneration failed: the chat data is unreadable.");
        EN.put("Sohbette ara", "Search in chat");
        EN.put("Sohbette ara…", "Search in chat…");
        EN.put("En az iki harf yaz; tüm sohbet geçmişinde aranır.",
                "Type at least two letters; the whole chat history is searched.");
        EN.put("Eşleşme yok.", "No matches.");
        EN.put("Açık", "On");
        EN.put("Kapalı", "Off");
        EN.put("Markdown biçimlendirme", "Markdown formatting");
        EN.put("*italik* ve **kalın** baloncuklarda uygulanır",
                "*italic* and **bold** are applied inside balloons");

        // ---- character card extras
        EN.put("Kopya oluştur", "Duplicate");
        EN.put("JSON dışa aktar", "Export JSON");
        EN.put("Geri", "Back");
        EN.put("Sohbet menüsü", "Chat menu");
        EN.put(" (kopya)", " (copy)");
        EN.put("Kopya oluşturuldu.", "Copy created.");
        EN.put("Kopya oluşturulamadı: karakter verisi okunamıyor.",
                "Could not create the copy: the character data is unreadable.");
        EN.put("Karakter dışa aktarıldı.", "Character exported.");
        EN.put("Dışa aktaramadı: karakter bulunamadı.", "Export failed: character not found.");
        EN.put("Dışa aktarılamadı: dosyaya yazılamadı.", "Export failed: could not write the file.");

        // ---- chat starter chips (empty chat)
        EN.put("İlk sahneyi kur", "Start the scene");
        EN.put("Kısa bir selam", "Simple greeting");
        EN.put("Arka planı sor", "Ask about the backstory");
        EN.put("*Kapıda durur, yorgun bir gülümseme.* Selam… buraya tam zamanında geldin.",
                "*Stands at the door, a tired smile.* Hi… you're right on time.");
        EN.put("Selam! Burada seni tekrar görmek güzel.", "Hey! Good to see you here again.");
        EN.put("Bu sahneyi nasıl kurdun? Baştan anlat.", "How did you set this scene up? Tell me from the start.");

        // ---- GUI round 3
        EN.put("İndirme ", "Download ");
        EN.put("  ·  bu cihazda rahat", "  ·  fits this device comfortably");
        EN.put("  ·  RAM'e göre riskli", "  ·  risky for this RAM");
        EN.put("Fotoğraf ekle", "Attach photo");
        EN.put("Görsel üret", "Generate an image");
        EN.put("Gönder (veya üretimi durdur)", "Send (or stop generating)");
        EN.put("Görsel açılamadı.", "The image could not be opened.");
        EN.put("Konuşma balonları · sahneler", "Conversation balloons · scenes");
        EN.put("Hikâyen burada başlıyor.", "Your story starts here.");

        // ---- image control panel + gallery (DeepSeek item 8)
        EN.put("Adım sayısı (10–50)", "Sampling steps (10–50)");
        EN.put("CFG ölçeği (1.5–15)", "CFG scale (1.5–15)");
        EN.put("Tohum (boş = rastgele)", "Seed (empty = random)");
        EN.put("Görsel galerisi", "Image gallery");
        EN.put("Üretilen ve sohbete eklenen görseller", "Generated and chat-attached images");
        EN.put("Burada henüz görsel yok", "No images here yet");
        EN.put("Sohbette ✨ ile üret ya da 📷 ile bir fotoğraf ekle.", "Generate with ✨ in a chat, or attach with 📷.");
        EN.put("Karakter avatarı yap", "Set as character avatar");
        EN.put("Sohbete ekle", "Add to a chat");
        EN.put("Bu görsel silinsin mi?", "Delete this image?");
        EN.put("Görsel silindi.", "Image deleted.");
        EN.put("Silinemedi: dosya yazılabilir değil.", "Delete failed: the file is not writable.");
        EN.put("Hangi karakter?", "Which character?");
        EN.put("Hangi sohbet?", "Which conversation?");
        EN.put(" için avatar güncellendi.", "'s avatar was updated.");
        EN.put(" sohbetine eklendi.", " was added to the conversation.");
        EN.put("Avatar kopyası yazılamadı.", "Could not write the avatar copy.");
        EN.put("Önce bir karakter oluştur.", "Create a character first.");
        EN.put("Bu görsel bir sohbette veya karakter avatarında kullanılıyor; önce o kaydı sil.",
                "This image is used in a chat or as a character avatar; delete that record first.");
        EN.put("Avatar kaydedilemedi: karakter verisi okunamıyor.",
                "Could not save the avatar: the character data is unreadable.");
        EN.put("Önce bir sohbet oluştur.", "Create a conversation first.");

        // ---- backup v2: ZIP bundle with media (DeepSeek item 9)
        EN.put("Yedeği dışa aktar (medya dahil)", "Export backup (media included)");
        EN.put("Yedek doğrulamadı: sağlama farkı.",
                "Backup failed verification: checksum mismatch.");
        EN.put(" medya dosyası", " media file(s)");

        // ---- selective export + undoable delete (DeepSeek item 9b)
        EN.put("Yedek biçimi", "Backup format");
        EN.put("JSON (küçük, görsellersiz)", "JSON (small, without images)");
        EN.put("ZIP (görsellerle)", "ZIP (with images)");
        EN.put("En az bir karakter veya sohbet seç.", "Select at least one character or conversation.");
        EN.put("Yazılacak bir şey yok", "Nothing to export");
        EN.put("Önce karakter ya da sohbet oluştur.", "Create a character or conversation first.");
        EN.put(" silindi.", " was deleted.");
        EN.put(" geri getirildi.", " was restored.");
        EN.put("Geri al", "Undo");
        EN.put("Geri alınamadı: karakter verisi okunamıyor.",
                "Could not undo: the character data is unreadable.");
        EN.put("Geri alınamadı: sohbet verisi okunamıyor.",
                "Could not undo: the conversation data is unreadable.");

        // ---- per-chat generation profile (DeepSeek item 7)
        EN.put("Üretim profili", "Generation profile");
        EN.put("Bu sohbet: ", "This conversation: ");
        EN.put("Genel ayarı izler: ", "Follows the global setting: ");
        EN.put("Genel ayarı izler", "Follows the global setting");
        EN.put(" token", " tokens");
        EN.put("Uzunluğu genel ayara döndür", "Return length to the global setting");
        EN.put("Derinliği genel ayara döndür", "Return depth to the global setting");
        EN.put("Temayı genel ayara döndür", "Return theme to the global setting");
        EN.put("Bu sohbet için tema seç", "Pick a theme for this conversation");
        EN.put("Tüm profili genel ayara döndür", "Reset the whole profile to the global setting");
        EN.put("Sıfırla", "Reset");
        EN.put("Profil genel ayara döndü.", "Profile returned to the global setting.");
        EN.put("Bu sohbetin üretim profili sıfırlansın mı?", "Reset this conversation's generation profile?");
        EN.put("Yanıt sürerken profil değiştirilemez; önce durdur.",
                "The profile cannot change while a reply is running; stop it first.");
        EN.put("Özel temayı Ayarlar → AI teması → Özel tema ile yaz.",
                "Write the custom theme under Settings → AI theme → Custom theme.");

        // ---- model profiles + feasibility + auto-load (DeepSeek item 11)
        EN.put("  ·  sığar", "  ·  fits");
        EN.put("  ·  sınırda", "  ·  borderline");
        EN.put("  ·  zor", "  ·  heavy");
        EN.put("Bu cihazda rahat sığar.", "Fits this device comfortably.");
        EN.put("Sınırda: çalışır ama bellek dar.", "Borderline: it runs, but memory will be tight.");
        EN.put("Bu cihazda zor; büyük ihtimalle açılmaz.", "Hard on this device; it will most likely not load.");
        EN.put("RAM bilgisi okunamadı; gösterge bilinmiyor.", "RAM could not be read; the verdict is unknown.");
        EN.put("Boyut: ", "Size: ");
        EN.put("Tahmini bellek kullanımı ~1,3× dosya.\n",
                "Estimated memory use is ~1.3× the file.\n");
        EN.put("Açılışta otomatik yükle", "Load automatically at launch");
        EN.put("Açılışta otomatik yükleme: AÇIK (kapat)", "Auto-load at launch: ON (tap to turn off)");
        EN.put("Açılışta otomatik yükleme kapatıldı.", "Auto-load at launch turned off.");
        EN.put(" açılışta otomatik yüklenecek.", " will load automatically at launch.");
        EN.put("Açılışta otomatik yükleniyor…", "Auto-loading at launch…");
        EN.put(" cihazdan silinsin mi? Tekrar kullanmak için yeniden indirilir.",
                " delete from the device? Re-download to use it again.");
        EN.put("Dil Türkçe olarak ayarlandı.", "Language set to Turkish.");
        EN.put("Dosya açılamadı.", "Could not open the file.");
        EN.put("Dosya beklenenden çok büyük.", "The file is larger than expected.");
        EN.put("Dosya tamamen senin kontrolündedir: uygulama hiçbir veriyi otomatik göndermez.",
                "The file is fully under your control: the app never sends any data automatically.");
        EN.put("Eğitim bağlamı: ", "Training context: ");
        EN.put("Fotoğraf açılamadı; başka bir dosya deneyin.", "Could not open the photo; try another file.");
        EN.put("Fotoğraf gizli depoya küçük kopya olarak yazılır; seçtiğin kaynak dosya silinmez. Sohbetlerde karakterin avatarı bu foto olur ve Kaydet ile saklanır.",
                "The photo is written to private storage as a small copy; your original file is kept. In chats it becomes the character's avatar and is saved when you press Save.");
        EN.put("Geçmiş derinliği, bağlam ve kendiliğinden devam", "History depth, context and auto-continue");
        EN.put("Geçmiş karakter bütçesi", "History character budget");
        EN.put("Geçmişler yalnızca yedekteki kopya daha uzunsa değiştirilir. Devam edilsin mi?",
                "Histories are replaced only if the backup copy is longer. Continue?");
        EN.put("Geçmiş mesaj sayısı", "History message count");
        EN.put("Geri yükleme yapılamadı: mevcut veriler okunamıyor.", "Restore failed: the current data cannot be read.");
        EN.put("Geri yüklendi · ", "Restored · ");
        EN.put(" karakter · ", " characters · ");
        EN.put(" sohbet", " chats");
        EN.put("GGUF başlığı okundu; ayrıntılı alan bulunamadı.", "GGUF header read; no detailed fields found.");
        EN.put("GGUF  ·  Cihaz içi yapay zekâ", "GGUF  ·  On-device AI");
        EN.put("Görünüm", "Appearance");
        EN.put("GPU hızları * Vulkan/MLC tabanlı motorlar içindir; bu sürümün motoru llama.cpp CPU ile çalışır.",
                "GPU speeds are for Vulkan/MLC-based engines; this build's engine runs llama.cpp on CPU.");
        EN.put("Grubu düzenle", "Edit group");
        EN.put("Hazır · ", "Ready · ");
        EN.put(" hazır", " ready");
        EN.put("Hikâyendeki kişileri yönet", "Manage the people of your story");
        EN.put("Hikâyene katılacak karakterleri seç", "Pick the characters joining your story");
        EN.put("Hikâyen\nburada başlıyor.", "Your story\nstarts here.");
        EN.put("Sahne", "Scene");
        EN.put("İlgili sohbeti temizleyerek o bölümü onarabilirsin.", "You can repair that section by clearing the related chat.");
        EN.put("İndir", "Download");
        EN.put("İndir  ·  ", "Download  ·  ");
        EN.put("İndirildi", "Downloaded");
        EN.put("İndirildi  ·  ", "Downloaded  ·  ");
        EN.put("İndiriliyor: ", "Downloading: ");
        EN.put("İndirme hatası: ", "Download error: ");
        EN.put("). İndirme kaldığı yerden devam etsin mi?", "). Resume the download where it left off?");
        EN.put("İndirme kapalı  ·  ", "Download off  ·  ");
        EN.put("İndirmeler Hugging Face'den doğrudan olur ve uygulamanın özel alanına kaydedilir. Parola veya hesap gerekmez.",
                "Downloads come straight from Hugging Face and are saved into the app's private storage. No password or account needed.");
        EN.put("İndirmeyi durdur", "Stop download");
        EN.put("Kaldığı yerden devam", "Resume where it left off");
        EN.put("Karakteri düzenle", "Edit character");
        EN.put("Karakterler, sohbetler ve geçmişler tek bir .json dosyasına yazılır. ",
                "Characters, chats and histories are written into one .json file. ");
        EN.put("＋  Karakter oluştur", "＋  Create character");
        EN.put("Kaydedilen parça: ", "Saved partial file: ");
        EN.put("KENDİ DÜNYANI KUR", "BUILD YOUR OWN WORLD");
        EN.put(" konuşsun", " speaks next");
        EN.put("Kopyayı sil", "Delete the copy");
        EN.put("Koruma gereği bu bölümler okunana kadar yazmaya kapatıldı; hiçbir veri üzerine yazılmadı. ",
                "As a safeguard, writing is blocked until those sections can be read; no data was overwritten. ");
        EN.put("LocalRP mesajı", "LocalRP message");
        EN.put("Mobil veri uyarısı", "Mobile data warning");
        EN.put(") mobil veriyle indirilecek. Wi-Fi önerilir.", ") will download over mobile data. Wi-Fi is recommended.");
        EN.put("Model bellekten kaldırılıyor…", "Unloading the model from memory…");
        EN.put("Model dosyası silinemedi.", "Could not delete the model file.");
        EN.put("modele göre otomatik", "automatic, based on the model");
        EN.put("Model hazır.", "Model ready.");
        EN.put("Model hazır · çevrimdışı", "Model ready · offline");
        EN.put("Modelin eğitim bağlamı düşük; uzun sohbetlerde bağlam otomatik sıfırlanabilir.",
                "The model's training context is small; the context may reset automatically in long chats.");
        EN.put("Modeli yükle", "Load model");
        EN.put("Modeli yükle  ›", "Load a model  ›");
        EN.put("Model klasörü açılamadı.", "Could not open the model folder.");
        EN.put("Model kopyaları", "Model copies");
        EN.put("Model kopyası silindi.", "Model copy deleted.");
        EN.put("Model yüklenemedi", "Model failed to load");
        EN.put("Model yükleniyor; bitmesini bekle.", "A model is loading; wait for it to finish.");
        EN.put("Motor bağlamı en fazla 8192 token; uygulama bütçesi ",
                "The engine context is at most 8192 tokens; the app budget is ");
        EN.put("Motor sonuç döndürmedi.", "The engine returned no result.");
        EN.put("Motor testi çalışıyor…", "Benchmark running…");
        EN.put("Motor testi hatası: ", "Benchmark error: ");
        EN.put("Motor testi sürüyor; bitmesini bekle.", "A benchmark is running; wait for it to finish.");
        EN.put("Motor testi tamamlanamadı", "Benchmark could not finish");
        EN.put("Motor testi tamamlandı.", "Benchmark finished.");
        EN.put("Otomatik (sırayla)", "Automatic (in turn)");
        EN.put("Önceki indirme hâlâ sonlanıyor; bir saniye sonra tekrar dene.",
                "A previous download is still finishing; try again in a second.");
        EN.put("Önce model gerekli", "A model is needed first");
        EN.put("RP karakterini seç", "Pick the RP character");
        EN.put("Saklı verilerde bozuk bölümler bulundu (", "Corrupted sections found in stored data (");
        EN.put("Seçilen model dosyası", "Selected model file");
        EN.put("Sen (kullanıcı profili)", "You (user persona)");
        EN.put("▶  Sıradaki: ", "▶  Next: ");
        EN.put("Sıradaki konuşmacı", "Next speaker");
        EN.put("Silinemedi: karakter verisi okunamıyor.", "Delete failed: the character data cannot be read.");
        EN.put("Tema kısıtlaması yok; karakterin doğal dünyası ve sahnenin tonu korunur.",
                "No theme constraint; the character's natural world and the scene's own tone are preserved.");
        EN.put("Uygulama arayüzü ile AI rol yapma dilini birlikte değiştirir. English mode: better RP with English-tuned models.",
                "Switches the app UI and the AI roleplay language together.");
        EN.put("Uygulamanın temasını seç", "Pick the app theme");
        EN.put("Veri okunamadı", "Data could not be read");
        EN.put("Yanıtlar için GGUF modeli seç  ›", "Pick a GGUF model for replies  ›");
        EN.put(" yanıtlasın", " replies");
        EN.put("Yanıt uzunluğu", "Reply length");
        EN.put("Yanıt üretilirken motor testi başlatılamaz.", "Cannot start the benchmark while a reply is streaming.");
        EN.put("Yanıt üretilirken sohbet temizlenemez. Önce yanıtı durdur.",
                "Cannot clear the chat while a reply is streaming. Stop the reply first.");
        EN.put("Yanıt üretmek için AI ayarlarından bir GGUF modeli seçip yükle. Mesajın kaydedildi; model hazır olunca yanıtlanacak.",
                "Pick and load a GGUF model from AI settings to generate replies. Your message is saved; it will be answered once the model is ready.");
        EN.put("Yapay zekâ sana bu kimlikle davranır", "The AI treats you with this persona");
        EN.put("Yarı kalan bir indirme var (", "A partial download exists (");
        EN.put("Yedek okunamadı: ", "Backup could not be read: ");
        EN.put("Yedek oluşturulamadı (veri okunamıyor).", "Backup failed (the data cannot be read).");
        EN.put("Yedek yazılamadı: ", "Backup could not be written: ");
        EN.put("Yedek yazıldı · ", "Backup written · ");
        EN.put("Yerel hikâyelerin, senin kontrolünde", "Your local stories, under your control");
        EN.put("Yükleme başlatılıyor…", "Starting to load…");
        EN.put(" yüklendi.", " loaded.");
        EN.put(" indirildi.", " downloaded.");
        EN.put("Yüklenemedi: ", "Failed to load: ");
        EN.put("  ·  yüklü", "  ·  loaded");
        EN.put("Zincir açıkken karakter, kendi yanıtından sonra sıradaki karakteri otomatik çağırır. Sahne palazlanır ama token tüketimi artar.",
                "With chaining on, a character automatically calls the next one after its reply. The scene grows richer but token use rises.");

        EN.put("); indirilmedi.", "); the download was not saved.");
        EN.put("Dosya boyutu katalog bekleneniyle uyuşmuyor (", "File size does not match the catalog expectation (");
        EN.put("Dosya tam inmedi (", "The file did not complete (");
        EN.put("); tekrar deneyebilirsin.", "); you can try again.");
        EN.put("Depolama yetersiz. Gerekli: ", "Not enough storage. Needed: ");
        EN.put(" GB, boş: ", " GB, available: ");
        EN.put("Sunucu ", "The server returned ");
        EN.put(" döndürdü; indirme başlatılamadı.", "; the download could not start.");
        EN.put("İndirme reddedildi: hedef dosya adı veya adres geçersiz.",
                "Download refused: the target file name or URL is invalid.");
        EN.put("Model klasörü yok.", "The model folder is missing.");
        EN.put("Model klasörü oluşturulamadı.", "The model folder could not be created.");
        EN.put("İndirme güvenli olmayan (https olmayan) bir adrese yönlendirildi; iptal edildi.",
                "The download was redirected to an insecure (non-https) URL; cancelled.");
        EN.put("SHA-256 doğrulaması başarısız; dosya silindi, indirme tekrar denenebilir.",
                "SHA-256 verification failed; the file was deleted and the download can be retried.");
        EN.put("İndirme tamamlanamadı.", "The download could not finish.");

        // ---- model catalog "why" texts (CPU / GPU / uncensored cards)
        EN.put("En hızlı acemi model. Uzun sahnelerde hız gerçekten hissedilir, Türkçe düzeyi orta.",
                "The fastest starter model. Speed is genuinely felt in long scenes; Turkish is mid-level.");
        EN.put("Denge önerisi: kalite/hız oranı metin RP için en iyi. Türkçesi kibar, konuşma düzeni başarılı.",
                "Balanced pick: the best quality/speed ratio for text RP. Polite Turkish and solid conversational flow.");
        EN.put("Çok dilli, talimata sadık ve tutarlı; hikâyede bireysel karakter sesleri idare eder.",
                "Multilingual, instruction-faithful and consistent; distinct character voices are passable in stories.");
        EN.put("Bu boyutta narratif kalitedeki en iyi seçim; atmosfer ve Türkçe doğallık yüksek, hız biraz daha düşük.",
                "The best narrative quality at this size; strong atmosphere and natural Turkish, a bit slower.");
        EN.put("Sağlam telefonlar için güçlü seçenek; uzun sahnelerde soğunun çöken bir model değil.",
                "A strong option for capable phones; it does not turn sluggish and collapse in long scenes.");
        EN.put("Yaşlı ve tek metin odaklı; etiketli konuşmada çok iyi ama bağlam penceresi 4K, çoğu sahne kısa kalır.",
                "Small and text-focused; very good at labeled dialogue, but with a 4K context most scenes stay short.");
        EN.put("Küçük model sayılır: GPU'da gerçek zamanlı akış en rahat hissedilen boyut.",
                "Effectively tiny: on GPU, real-time streaming is most comfortable at this size.");
        EN.put("Klasik küçük model; GPU senaryoları için çok hafif, uzun sahne bağlamı zayıf.",
                "The classic small model; very light for GPU scenarios, weak at long-scene context.");
        EN.put("GPU senaryosunda da dengeli; kalite düşmeden hız artırmak isteyenler için.",
                "Balanced on GPU too, for those who want more speed without losing quality.");
        EN.put("GPU'da Q5/Q6 quant kalitesini seçebilirsin; metin kalitesi en yüksek boyut.",
                "On GPU you can pick Q5/Q6 quant quality; the best text quality at this tier.");
        EN.put("Denge önerisi: RP'de daha zengin sahne ve daha güçlü karakter sesi; abliterated sürüm geliştirici filtre katmanını çıkarır.",
                "Balanced pick: richer scenes and a stronger character voice in RP; the abliterated build removes the developer filter layer.");
        EN.put("En hızlı giriş: bu listedeki en hızlı modeldir; küçük boyut nedeniyle uzun sahnelerde derinlik sınırlıdır.",
                "Fastest entry: the quickest model on this list; limited depth in long scenes due to its small size.");
        EN.put("En yüksek sahne kalitesi; ağır maliyeti vardır: 8B model CPU'da yavaştır, güçlü cihaz + sabır gerektirir.",
                "The highest scene quality; it comes at a cost: an 8B model is slow on CPU and needs a capable device plus patience.");

        // ---- image engine (Görsel tab + chat image generation)
        EN.put("Görsel", "Images");
        EN.put("Görsel modeli yükleniyor…", "Loading image model…");
        EN.put("Görsel motoruyla bir işlem sürüyor; bitmesini bekle.", "The image engine is busy; wait for it to finish.");
        EN.put("Görsel motoru bu cihazda bulunamadı.", "The image engine is missing on this device.");
        EN.put("Görsel modeli yüklenemedi: ", "Could not load image model: ");
        EN.put("Görsel üretmeden önce bir görsel modeli yükle.", "Load an image model before generating.");
        EN.put("Görsel üretiliyor…", "Generating image…");
        EN.put("Görsel üretilemedi.", "Image generation failed.");
        EN.put("Görsel modeller bu cihazda çalışır: model yüklendikten sonra sohbette ✨ düğmesiyle resim üretebilirsin. Her şey çevrimdışı işler.",
                "Image models run on this device: once a model is loaded, press ✨ in a chat to paint. Everything works offline.");
        EN.put("Görsel modeller (18+)", "Image models (18+)");
        EN.put("Görsel modelini yükle", "Load image model");
        EN.put("Yüklü  ·  sohbette ✨ ile üret", "Loaded  ·  press ✨ in a chat to paint");
        EN.put("Yükleniyor… ilk üretimden önce birkaç dakika sürebilir.", "Loading… it may take a few minutes before the first generation.");
        EN.put(" yüklendi; sohbette ✨ düğmesiyle görsel üretebilirsin.", " loaded; press ✨ in a chat to paint images.");
        EN.put("Görsel modeli yüklü değil", "No image model loaded");
        EN.put("Önce Ayarlar → Model önerileri → Görsel bölümünden bir görsel modeli indir ve yükle.",
                "First download and load an image model under Settings → Model picks → Images.");
        EN.put("Model önerilerini aç", "Open model picks");
        EN.put("Görsel üret (", "Generate image (");
        EN.put("Üret", "Generate");
        EN.put("Görsel fikri", "Image idea");
        EN.put("Örn: fırtınada duran deniz feneri", "e.g. a lighthouse standing in a storm");
        EN.put("İstenmeyenler (boş kalabilir)", "Avoid (may stay empty)");
        EN.put("Örn: bulanık, düşük kalite", "e.g. blurry, low quality");
        EN.put("Bir fikir yaz.", "Write an idea first.");
        EN.put("Bu cihazda tek bir 512×512 görsel üretilir; CPU ile 1–3 dakika sürebilir. İptal etmek için duraklat düğmesine bas.",
                "One 512×512 image is produced on this device; CPU may take 1–3 minutes. Use the stop button to cancel.");
        EN.put(" · görsel üretiliyor…", " · generating image…");
        EN.put("Önce yürüyen yanıtı bekleyin.", "Wait for the running response to finish first.");
        EN.put("Görsel üretiliyor · model bu cihazda çalışıyor", "Generating image · the model runs on this device");
        EN.put("Fotoğraf eklendi", "Photo attached");
        EN.put("Görsel üretildi", "Image generated");
        EN.put("Motor fotoğrafları göremez; görsel yalnızca sohbet kaydında saklanır.",
                "The engine cannot see photos; images are only kept in the chat transcript.");
        EN.put("📷 Fotoğraf eklendi · gönder ile birlikte gider", "📷 Photo attached · goes with your next send");
        EN.put("Bu cihazda fotoğraf seçici yok.", "No photo picker on this device.");
        EN.put("Anlatıcı", "Narrator");
        EN.put("Yüklendi", "Loaded");
    }
}
