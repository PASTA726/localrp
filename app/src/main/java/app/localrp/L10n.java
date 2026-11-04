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
        EN.put("Model önerileri", "Model suggestions");
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
        EN.put("Karakter", "Character");
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
        EN.put("Sansürsüz bölümü açıldı.", "Uncensored section unlocked.");
        EN.put("Bu bölüm yalnızca 18 yaş üstüne açık.", "This section is limited to ages 18 and up.");

        // ---- v0.16: single Settings hub + prompt sources + custom theme
        EN.put("Ana sayfa", "Home");
        EN.put("Karakter", "Characters");
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
        EN.put("Katalog", "Catalog");
        EN.put("Sahne atmosferi", "Scene tone");

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
        EN.put(" teması uygulandı.", " tone applied.");
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
        EN.put("Uyarıyı okudum; 18 yaş üstü olduğumu onaylıyorum.", "I have read the warning and confirm I am 18 or older.");
        EN.put("Önce uyarıyı onaylaman gerekli.", "You must acknowledge the warning first.");
    }
}
