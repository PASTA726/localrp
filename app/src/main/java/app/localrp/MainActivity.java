package app.localrp;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Native LocalRP screens, local storage, and the on-device GGUF chat flow. */
public class MainActivity extends Activity {
    private static final int MODEL_PICKER = 74;
    private static final int BACKUP_EXPORT = 75;
    private static final int BACKUP_IMPORT = 76;
    private static final int AVATAR_PICK = 77;
    private static final int CHAT_PHOTO_PICK = 78;
    private static final int CHAR_EXPORT = 79;
    private static final int BACKUP_EXPORT_MEDIA = 80;
    private static final String PREFS = "localrp";
    private static final String DEFAULT_AI_INSTRUCTIONS = "Her zaman sahnenin içinde kal. Seçili karakter olarak konuş ve kullanıcının rolünü üstlenme.";
    private static final String DEFAULT_AI_INSTRUCTIONS_EN = "Stay inside the scene at all times. Speak as the selected character and never take over the user's role.";
    private static final String SYS_PROMPT_TR = "Sen bir rol yapma asistanısın. Daima sahnenin içinde kal; seçili karakter olarak konuş; kullanıcı ve diğer karakterler adına yazma; anlatı, aksiyon ve diyalogu tutarlı sürdür.";
    private static final String SYS_PROMPT_EN = "You are a roleplay assistant. Stay inside the scene at all times; speak as the selected character; never write for the user or other characters; keep narration, action and dialogue consistent.";
    private static final String BUILTIN_ROLEPLAY_ID = "localrp_builtin_roleplay_character";
    private static final String DEFAULT_AI_THEME = "serbest";
    private static final String[][] AI_THEMES = {
            {"serbest", "Serbest", ""},
            {"karanlik-fantezi", L10n.s("Karanlık fantezi"),
                    L10n.s("Gotik atmosfer, tehlikeli büyü ve ahlaki belirsizlik kullan; umut ile karanlık arasında denge kur.")},
            {"bilim-kurgu", "Bilim kurgu",
                    L10n.s("Teknoloji, uzay, yapay zekâ ve etik gerilim üzerinden somut, tutarlı bir gelecek kur.")},
            {"romantik", "Romantik drama",
                    L10n.s("Duygusal yakınlık, kırılganlık ve gerilimi öne çıkar; duyguları abartmadan göster.")},
            {"gizem", "Gizem ve dedektiflik",
                    L10n.s("İpuçları, şüpheler ve yavaş açığa çıkan sırlar kullan; her yanıt yeni bir iz bıraksın.")},
            {"tarihi", L10n.s("Tarihî dönem"),
                    L10n.s("Dönemin dili, kıyafetleri ve toplumsal kurallarına sadık kal; modern ifadelerden kaçın.")},
            {"anime", "Anime / hafif roman",
                    L10n.s("Canlı duygular, iç monolog ve komik zamanlama kullan; sahneyi abartısız sürdür.")},
            {"korku", "Korku",
                    L10n.s("Gerilim, belirsizlik ve görünmeyen tehdit yarat; kan ve şiddeti ölçülü tut.")},
            {"buhar", "Buhar punk",
                    L10n.s("Pirinç, buhar ve mekanik ayrıntılar; endüstriyel devrim atmosferini abartmadan koru.")},
            {"sur-real", L10n.s("Sürreal rüya"),
                    L10n.s("Mantık yerine çağrışım kullan: kayan nesneler, değişen mekânlar, rüya mantığı.")},
            {"savas", L10n.s("Savaş ve entrika"),
                    L10n.s("Siyasal baskı, askerî zorunluluk ve güven bunalımı; kimseye sığınak sağlama.")},
            {"razo", L10n.s("Sıcak slice of life"),
                    L10n.s("Gündelik küçük anlar, sıcak davranışlar; dram yerine samimiyet taşı.")},
            {"macera", L10n.s("Uzun yol macerası"),
                    L10n.s("Bir hedefe giden gezi sahnesi: yol arkadaşları, bulmacalar ve zorlayıcı seçimler.")}
    };
    private static final int MAX_HISTORY_MESSAGES = 8;
    private static final int MAX_HISTORY_CHARS = 2600;

    private int historyDepth() { return prefs().getInt("history_depth", MAX_HISTORY_MESSAGES); }
    private int historyCharBudget() { return prefs().getInt("history_chars", MAX_HISTORY_CHARS); }

    // ---- per-chat generation profiles (DeepSeek item 7): -1/null keys follow the global setting ----

    /** Per-chat token override key; -1 falls back to the global "max_output_tokens" (always bounded). */
    private int chatTokenBudget(String groupId) {
        SharedPreferences prefs = prefs();
        int override = prefs.getInt("profile_tokens_" + groupId, -1);
        int value = override == -1 ? prefs.getInt("max_output_tokens", 320) : override;
        return Math.max(64, Math.min(1024, value));
    }

    private int chatHistoryDepth(String groupId) {
        SharedPreferences prefs = prefs();
        int override = prefs.getInt("profile_depth_" + groupId, -1);
        int value = override == -1 ? prefs.getInt("history_depth", MAX_HISTORY_MESSAGES) : override;
        return Math.max(2, Math.min(14, value));
    }

    /** Profile edits must not yank the engine context from under a live generation. */
    private boolean chatProfileBusy(Group group) {
        if (group.id.equals(generatingChatId)) {
            Toast.makeText(this, L10n.s(L10n.s("Yanıt sürerken profil değiştirilemez; önce durdur.")), Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private String chatThemeId(String groupId) {
        SharedPreferences prefs = prefs();
        return prefs.getString("profile_theme_" + groupId, prefs.getString("ai_theme", DEFAULT_AI_THEME));
    }
    private Store store;
    private LinearLayout content;
    private LinearLayout pageRoot;
    private TextView pageSubtitleView;
    private String currentTab = "home";
    /** Same-page anchor for in-place refresh: rebuilt page headers/nav only on a real page change. */
    private String pageTitle;
    private String pageSubtitle;
    private TextView pageBackButton;
    private boolean pageHadBack;
    private Uri selectedModel;
    private String selectedModelName = L10n.s("Henüz model seçilmedi");
    private String modelStatus = L10n.s("Model seçilmedi");
    private String visibleChatId;
    private String generatingChatId;
    /** Active online provider client for the current turn (stop path); null offline. */
    private OpenAiCompat providerClient;
    private String generatingSpeaker;
    private long generationSequence;
    private boolean modelLoading;
    private TextView modelStatusView;
    private ProgressBar modelProgress;
    private TextView chatStatusView;
    private Button chatActionButton;
    private Button chatContinueButton;
    private TextView chatSpeakerView;
    private TextView chatModelHint;
    private EditText chatInput;
    private String chatInputChatId;
    private final java.util.Map<String, String> chatDrafts = new java.util.HashMap<>();
    // Live link from a chat id to its in-flight draft balloon (task G0): re-entering a
    // chat while its response streams must re-link the new view, not spawn a second one.
    private final java.util.Map<String, TextView> generationViews = new java.util.HashMap<>();
    // Render window for long conversations (Task 08): the view shows at most the
    // newest messages; older ones load in chunks only when the user asks.
    private static final int MAX_INITIAL_RENDER = 40;
    private static final int RENDER_CHUNK = 40;
    private List<Message> chatHistory = new ArrayList<>();
    private int chatRenderStart;
    private View chatOlderButton;
    private ScrollView chatScroll;
    /** Where a freshly opened chat should return to (one-shot; null = chat list). */
    private Runnable chatBackOrigin;
    private LinearLayout chatComposer;
    private View chatEmptyState;
    private boolean autoScroll = true;
    private Runnable backAction;
    private Runnable modelsBackAction;
    private final Set<String> autoAttempted = new HashSet<>();
    private final java.util.Map<String, Long> contextChars = new java.util.HashMap<>();
    private final java.util.Map<String, String> chatMetrics = new java.util.HashMap<>();
    // Image engine UI state: the "Görsel" catalog tab shows image models, the composer
    // can attach a photo or start an on-device generation.
    private int pendingCatalogFilter = -1;
    private String imageLoadStatus;
    private ViewGroup pendingComposerImageChip;
    private File pendingComposerImage;
    private String pendingComposerImageChatId;
    private Group imageAttachGroup;
    /** Character id chosen for a pending single-character JSON export. */
    private String charExportTarget;
    /** Chat image balloons decode once per (path,size) and re-scan-free on re-render. */
    private final java.util.concurrent.ConcurrentHashMap<String, Bitmap> imageDecodeCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private Runnable imageGeneratingTick;
    private android.os.Handler imageGeneratingTickTickHandler;

    // v0.14.0 feature set: user persona + profile photos + preset characters.
    private boolean avatarPickPersona;          // target of AVATAR_PICK: true = user persona, false = character
    private String personaAvatarDraft;          // freshly picked persona photo, applied on Kaydet
    /** Which persona identity the draft belongs to; a stale draft is discarded, never applied silently. */
    private String personaDraftOwnerId;
    private CharacterProfile editorCharacter;   // character being edited (null = creating)
    private Runnable editorAfterSave, editorOnBack;
    private String pickedAvatarPath = "";       // freshly picked character photo, applied on Kaydet
    private String chatHeaderAvatarPath = "";   // photo for the chat header avatar
    private TextView avatarStatusView;          // in-place status line of the character editor photo section
    private LinearLayout personaPreviewPane;    // persona editor preview container (swapped in place on pick)
    private EditText personaNameField;          // persona editor name field (preview label uses live text)
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private int catalogFilter;
    /** Per-session only uncensored unlock (v0.17): cleared on every process start. */
    private boolean uncensoredSessionUnlock;
    private TextView downloadProgressView;
    private long downloadBytes, downloadTotal = -1L;
    private final java.util.Map<String, Integer> chainCounts = new java.util.HashMap<>();

    // Comfort tools (v0.19.22): one-shot steering note + regenerate undo stash.
    /** Chat-scoped one-shot steering note ("Yönlendirerek yeniden üret"); consumed
     *  by the next beginGeneration in that chat, never persisted. */
    private final java.util.Map<String, String> pendingSteer = new java.util.HashMap<>();
    /** Pre-regenerate tail kept in memory so "Eski yanıtı geri getir" can undo a
     *  regenerate this session; image files stay on disk until the stash is discarded. */
    private final java.util.Map<String, RegenStash> regenStashes = new java.util.HashMap<>();

    /** In-memory snapshot of the transcript tail a regenerate removed. */
    private static final class RegenStash {
        final int index;
        final String anchorText;
        final long anchorAt;
        final java.util.List<Message> tail;
        RegenStash(int index, String anchorText, long anchorAt, java.util.List<Message> tail) {
            this.index = index;
            this.anchorText = anchorText;
            this.anchorAt = anchorAt;
            this.tail = tail;
        }
    }

    /** Captures the transcript tail at {@code index} for a later restore; replaces
     *  (and frees) any previous stash for the chat. */
    private void stashForRegenerate(String chatId, int index, java.util.List<Message> stored) {
        discardRegenStash(chatId);
        if (index < 0 || index >= stored.size()) return;
        java.util.List<Message> tail = new ArrayList<>();
        for (int i = index; i < stored.size(); i++) tail.add(stored.get(i));
        String anchorText = index == 0 ? null : stored.get(index - 1).text;
        long anchorAt = index == 0 ? 0L : stored.get(index - 1).at;
        regenStashes.put(chatId, new RegenStash(index, anchorText, anchorAt, tail));
    }

    /** Drops a chat's stash; stashed image files leave the disk with it. */
    private void discardRegenStash(String chatId) {
        RegenStash stash = regenStashes.remove(chatId);
        if (stash == null) return;
        for (Message m : stash.tail) {
            if (m.hasImage()) deleteMessageImageFile(m.image);
        }
    }


    @Override public void onCreate(Bundle state) {
        // Window background (GUI review item 18): the cold-start window uses the saved
        // palette family, so dark themes no longer draw a white square before the
        // first content frame.
        SharedPreferences startPrefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (isDarkThemeId(startPrefs.getString("ui_theme", "lavanta"))) {
            setTheme(R.style.AppThemeDark);
        } else {
            setTheme(R.style.AppTheme);
        }
        super.onCreate(state);
        store = new Store(this, BUILTIN_ROLEPLAY_ID);
        store.migrateToRoleplay();
        if (!store.integrityIssues().isEmpty()) reportIntegrityIssues();
        // Leftover generation drafts (crash mid-stream) become normal messages once, up front.
        restoreInterruptedDrafts();
        L10n.refresh(prefs().getString("app_language", "tr"));
        Design.refresh(this);
        modelsBackAction = this::showHome;
        SharedPreferences prefs = prefs();
        // v0.16 migration: users who saved a custom system prompt before prompt sources
        // existed keep their text by mapping it to the "custom" source explicitly.
        if (prefs.getString("custom_sys_prompt", null) != null
                && !prefs.getString("custom_sys_prompt", "").isEmpty()
                && prefs.getString("sys_prompt_source", null) == null) {
            prefs.edit().putString("sys_prompt_source", "custom").apply();
        }
        // Same for a previously saved general instruction: it was by definition custom.
        if (!prefs.getString("ai_instructions", "").isEmpty()
                && prefs.getString("general_prompt_source", null) == null) {
            prefs.edit().putString("general_prompt_source", "custom").apply();
        }
        applySystemPrompt();
        LocalAiRuntime.INSTANCE.setManualContextBudget(prefs.getInt("context_budget_chars", -1));
        // A selective-export selection must survive the activity recreation that SAF
        // storage pickers may trigger; returning without it would dump a full backup.
        if (state != null && state.getStringArrayList("pendingExportChars") != null
                && state.getStringArrayList("pendingExportGroups") != null) {
            pendingExportCharacters = new java.util.HashSet<>(state.getStringArrayList("pendingExportChars"));
            pendingExportGroups = new java.util.HashSet<>(state.getStringArrayList("pendingExportGroups"));
        }
        String modelUri = prefs.getString("selected_model_uri", null);
        if (modelUri != null) selectedModel = Uri.parse(modelUri);
        selectedModelName = prefs.getString("selected_model_name", selectedModelName);
        if (LocalAiRuntime.INSTANCE.isModelReady()) modelStatus = L10n.s("Hazır · bu işlem için internet gerekmez");
        else if (LocalAiRuntime.INSTANCE.isModelLoading()) modelStatus = L10n.s("Model yükleniyor…");
        else if (selectedModel != null) modelStatus = L10n.s("Seçildi · yüklemeye hazır");
        if (shouldShowOnboarding()) showOnboarding(); else showHome();
        // Auto-load the pinned model a tick after the first frame so the UI is alive
        // before any long JNI work starts (DeepSeek item 11).
        new android.os.Handler(android.os.Looper.getMainLooper()).post(this::maybeAutoLoadModelAtStartup);
    }

    private void reportIntegrityIssues() {
        java.util.List<String> parts = new ArrayList<>();
        for (String key : store.integrityIssues()) {
            if ("characters".equals(key)) parts.add(L10n.s("karakter listesi"));
            else if ("groups".equals(key)) parts.add(L10n.s("sohbet listesi"));
            else if (key.startsWith("chat_")) parts.add(L10n.s(L10n.s("bir sohbetin geçmişi")));
        }
        if (parts.isEmpty()) parts.add(L10n.s(L10n.s("bazı kayıtlar")));
        String detail = String.join(", ", parts);
        new AlertDialog.Builder(this)
                .setTitle(L10n.s(L10n.s("Veri okunamadı")))
                .setCancelable(false)
                .setMessage(L10n.s(L10n.s("Saklı verilerde bozuk bölümler bulundu (")) + detail + L10n.s("). ")
                        + L10n.s(L10n.s("Koruma gereği bu bölümler okunana kadar yazmaya kapatıldı; hiçbir veri üzerine yazılmadı. "))
                        + L10n.s(L10n.s("İlgili sohbeti temizleyerek o bölümü onarabilirsin.")))
                .setPositiveButton(L10n.s(L10n.s("Anlaşıldı")), null)
                .show();
    }

    private void showHome() {
        visibleChatId = null;
        backAction = null;
        setPage("LocalRP", L10n.s("Yerel hikâyelerin, senin kontrolünde"), "home");
        addHero();
        addHomeActions();
        List<Group> groups = store.groups();
        if (groups.isEmpty()) addEmpty(L10n.s("Henüz bir RP yok"), L10n.s("Bir karakter seçerek bire bir hikâye başlat veya grup kur."), null, null);
        else {
            addSection(L10n.s("Son sohbetler"), L10n.s("Tümünü gör"), this::showChats);
            for (Group group : recentChats(groups, 4)) addGroupCard(group, this::showHome);
        }
        addSection("Karakterlerin", L10n.s("Yönet"), this::showCharacters);
        List<CharacterProfile> characters = store.characters();
        if (characters.isEmpty()) addEmpty("Hikâyene karakter ekle", L10n.s("Kişiliğini ve konuşma tarzını birlikte tanımla."), L10n.s("＋  Karakter oluştur"), v -> showCharacterEditor(null));
        else addCharacterGrid(characters);
    }

    // ---- first-launch onboarding (DeepSeek item 13): dil → model → ilk karakter ----

    /** True only while the three-step guided setup is on screen. */
    private boolean onboardingActive;

    /**
     * Which step is currently rendered ("intro"/"model"/"character"). Long model loads
     * finish asynchronously and may land after the user has moved on — the callbacks
     * must only refresh the model step, never yank the user backwards.
     */
    private String onboardingStep = "intro";

    /** A brand-new install (no data and no completion marker) enters the guided setup;
     * existing users with any saved data are never interrupted by onboarding. */
    private boolean shouldShowOnboarding() {
        SharedPreferences prefs = prefs();
        return !prefs.getBoolean("onboarding_done", false)
                && store.characters().isEmpty() && store.groups().isEmpty();
    }

    /** Step 1 — welcome + app/UI/prompt language. */
    private void showOnboarding() {
        onboardingActive = true;
        visibleChatId = null;
        backAction = null;
        onboardingStep = "intro";
        SharedPreferences prefs = prefs();
        setPage(L10n.s("LocalRP'ye hoş geldin"), L10n.s("Yerel, çevrimdışı yapay zekâ ile rol yapma"), "home");
        addInfoCard(L10n.s("Tamamen senin cihazında"),
                L10n.s(L10n.s("Karakterler, sohbetler ve model dosyaları yalnızca bu telefonda kalır; "))
                        + L10n.s(L10n.s("hiçbir sohbet verisi otomatik olarak gönderilmez.")), L10n.s("Gizlilik"));
        addSection("Dil", L10n.s("Uygulama ve yanıt dili"), null);
        addSecondary(L10n.s(L10n.s("Türkçe")) + (prefs.getString("app_language", "tr").equals("tr") ? "  ✓" : ""),
                v -> pickOnboardingLanguage("tr"));
        addSecondary("English (recommended: better RP)"
                + (prefs.getString("app_language", "tr").equals("en") ? "  ✓" : ""),
                v -> pickOnboardingLanguage("en"));
        addPrimary(L10n.s(L10n.s("İleri: model seçimi")), v -> showOnboardingModel());
    }

    /** Language switch inside onboarding: must re-render the step, not the settings page. */
    private void pickOnboardingLanguage(String language) {
        prefs().edit().putString("app_language", language).apply();
        L10n.refresh(language);
        contextChars.clear();
        showOnboarding();
    }

    /** Step 2 — model: load a stored copy, browse the catalog, or skip for now. */
    private void showOnboardingModel() {
        visibleChatId = null;
        backAction = this::showOnboarding;
        onboardingStep = "model";
        setPage(L10n.s("Model seçimi"), L10n.s("Yanıtlar, telefonda çalışan GGUF modeliyle üretilir"), "home");
        if (modelLoading) {
            addInfoCard(L10n.s("Model yükleniyor…"), modelStatus, "GGUF");
            addBody(L10n.s(L10n.s("Yükleme bitince bu sayfa güncellenir; beklemek zorunda değilsin.")));
        } else if (LocalAiRuntime.INSTANCE.isModelReady()) {
            addInfoCard(L10n.s("Model hazır"), modelStatus, "GGUF");
            addBody(L10n.s(L10n.s("Model yüklü; sohbetler internet olmadan da sürer.")));
        } else {
            addInfoCard(L10n.s("Model yok (henüz)"), modelStatus, "GGUF");
            addBody(L10n.s(L10n.s("Model seçmezsen karakterler ve sohbetler yine kaydedilir; "))
                    + L10n.s(L10n.s("ancak yanıt üretimi için bir model yüklemelisin.")));
        }
        List<File> storedModels = LocalAiRuntime.INSTANCE.storedModels(this);
        if (!storedModels.isEmpty()) {
            addSection(L10n.s("Cihazdaki model kopyaları"), L10n.s("Dokun: yükle"), null);
            for (File file : storedModels) {
                int verdict = fitVerdict(file.length());
                String verdictTag = verdict < 0 ? ""
                        : verdict == 0 ? L10n.s(L10n.s("  ·  sığar"))
                        : verdict == 1 ? L10n.s(L10n.s("  ·  sınırda"))
                        : L10n.s("  ·  zor");
                addSecondary(file.getName() + L10n.s("  ·  ") + humanSize(file.length()) + verdictTag,
                        v -> loadStoredModelFile(file));
            }
        }
        addPrimary(L10n.s(L10n.s("Model önerilerini gör ve indir")), v -> showModelCatalog());
        addSecondary(L10n.s(L10n.s("İleri: ilk karakter")), v -> showOnboardingCharacter());
        addBody(L10n.s(L10n.s("Bu adımı istediğin zaman Ayarlar → Model yönetimi'nden tamamlayabilirsin.")));
    }

    /** Step 3 — first character: a short form or a one-tap preset fill. */
    private void showOnboardingCharacter() {
        visibleChatId = null;
        backAction = this::showOnboardingModel;
        onboardingStep = "character";
        setPage(L10n.s("İlk karakter"), L10n.s("Kimliği adı kadar kişiliği çizer"), "home");
        addBody(L10n.s(L10n.s("Karakterler, sohbetleri yönlendiren profillerdir; her şeyi sonra düzenleyebilirsin.")));
        EditText nameField = field(L10n.s("Adı"), "", 1);
        nameField.setSingleLine(true);
        EditText descriptionField = field(L10n.s("Kısa tanıtım (hikâyedeki yeri, dünyası)"), "", 3);
        EditText personalityField = field(L10n.s("Kişilik ve konuşma tarzı (isteğe bağlı)"), "", 2);
        addSection(L10n.s("Hazır karakterler"), L10n.s("Dokun: alanları doldurur"), null);
        for (PresetCharacters.Preset preset : PresetCharacters.PRESETS) {
            addSecondary(preset.name, v -> {
                nameField.setText(preset.name);
                descriptionField.setText(preset.description);
                personalityField.setText(preset.personality);
            });
        }
        addPrimary(L10n.s(L10n.s("Karakteri oluştur ve başla")), v -> {
            String name = nameField.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, L10n.s(L10n.s("En az bir isim yaz veya hazır bir karakter seç.")), Toast.LENGTH_SHORT).show();
                return;
            }
            CharacterProfile created = new CharacterProfile();
            created.name = name;
            created.description = descriptionField.getText().toString().trim();
            created.personality = personalityField.getText().toString().trim();
            if (!store.saveCharacter(created)) {
                Toast.makeText(this, L10n.s(L10n.s("Karakter kaydedilemedi: verisi okunamıyor.")), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, created.name + L10n.s(" eklendi."), Toast.LENGTH_SHORT).show();
            finishOnboarding();
        });
        addSecondary(L10n.s("Atla (karakterleri sonra eklerim)"), v -> finishOnboarding());
    }

    /** Marks the guided setup complete and lands on the home screen. */
    private void finishOnboarding() {
        prefs().edit().putBoolean("onboarding_done", true).apply();
        onboardingActive = false;
        showHome();
    }

    private void showCharacters() {
        visibleChatId = null;
        backAction = this::showHome;
        setPage(L10n.s("Karakterler"), L10n.s("Hikâyendeki kişileri yönet"), "characters");
        addUserProfileCard();
        addPrimary("＋  Yeni karakter", v -> showCharacterEditor(null));
        List<CharacterProfile> characters = store.characters();
        if (characters.isEmpty()) addEmpty(L10n.s("İlk karakterini oluştur"), L10n.s("Adını, kişiliğini ve hikâyedeki yerini belirle."), L10n.s("Karakter oluştur"), v -> showCharacterEditor(null));
        else addCharacterGrid(characters);
        addSection(L10n.s("Hazır karakterler"), L10n.s("Tek dokunuşla ekle"), null);
        addBody(L10n.s("Beğendiğin hazır karakter kopyalanıp tamamen düzenlenebilir bir karakter olarak listene geçer."));
        addPresetGrid();
    }

    /** v0.14.0 "Sen (kullanıcı profili)" card; v0.19.16: shows the default persona and
     *  opens the multi-persona manager. */
    private void addUserProfileCard() {
        Persona persona = store.defaultPersona();
        String name = persona != null ? persona.name : "";
        String description = persona != null ? persona.description : "";
        String avatar = personaAvatarDraft != null ? personaAvatarDraft
                : (persona != null ? persona.avatarPath : "");
        int count = store.personas().size();
        LinearLayout card = makeCard();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View face = Avatar.forIdentity(this, name.isEmpty() ? L10n.s("Sen") : name, avatar, 44);
        row.addView(face, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView title = label(name.isEmpty()
                ? L10n.s(L10n.s("Kullanıcı kişilikleri"))
                : name + L10n.s(L10n.s("  (varsayılan kişilik)")), 16, Design.text());
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        texts.addView(title);
        TextView detail = label(count == 0
                ? L10n.s("Henüz kişilik yok — dokunup kendini tanıt")
                : (description.isEmpty()
                        ? count + L10n.s(L10n.s(" kişilik · dokun: yönet"))
                        : count + L10n.s(L10n.s(" kişilik · ")) + brief(description)), 12, Design.muted());
        detail.setPadding(0, dp(3), 0, 0);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(detail);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);
        card.setOnClickListener(v -> showPersonas());
        addCardToPage(card);
    }

    /** v0.19.16: persona manager — the list of user identities; tap one to edit. */
    private void showPersonas() {
        visibleChatId = null;
        backAction = this::showCharacters;
        setPage(L10n.s("Kullanıcı kişilikleri"), L10n.s("Sohbetlerde seni bu kimlikler taşır"), "characters");
        java.util.List<Persona> personas = store.personas();
        if (personas.isEmpty()) {
            addEmpty(L10n.s("Henüz kişilik yok"),
                    L10n.s("Farklı roller için farklı kimlikler: gerçek adın, takma adın, karakterlerin karşıtı bir oyuncu…"),
                    L10n.s("İlk kişiliği ekle"), v -> showPersonaEditor(null));
        } else {
            addInfoCard(L10n.s("Nasıl çalışır?"),
                    L10n.s("Sohbetler varsayılan olarak \"varsayılan\" etiketli kişiliği kullanır; ")
                            + L10n.s("her sohbet kendi kişiliğini seçebilir (Sohbetler → sohbet → Üretim profili)."), L10n.s("Kişilik"));
            for (Persona persona : personas) {
                LinearLayout card = makeCard();
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                View face = Avatar.forIdentity(this,
                        persona.name.isEmpty() ? L10n.s("Sen") : persona.name, persona.avatarPath, 44);
                row.addView(face, new LinearLayout.LayoutParams(dp(44), dp(44)));
                LinearLayout texts = new LinearLayout(this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(dp(12), 0, 0, 0);
                TextView title = label(persona.name.isEmpty()
                        ? L10n.s(L10n.s("Adsız kişilik")) : persona.name, 16, Design.text());
                title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
                texts.addView(title);
                String tag = store.isDefaultPersona(persona.id) ? L10n.s(L10n.s("  ·  varsayılan")) : "";
                TextView detail = label(brief(persona.description) + tag, 12, Design.muted());
                detail.setPadding(0, dp(3), 0, 0);
                detail.setSingleLine(true);
                detail.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(detail);
                row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                card.addView(row);
                card.setOnClickListener(v -> showPersonaEditor(persona));
                addCardToPage(card);
            }
            addPrimary(L10n.s("＋  Yeni kişilik"), v -> showPersonaEditor(null));
        }
    }

    /**
     * Persona editor; {@code editing == null} creates a new identity. Reuses the
     * single-persona avatar draft fields, but only for the persona being edited —
     * a stale draft for another identity is discarded here, never applied silently.
     */
    private void showPersonaEditor(Persona editing) {
        visibleChatId = null;
        backAction = this::showPersonas;
        setPage(editing == null ? L10n.s("Yeni kişilik") : L10n.s("Kişiliği düzenle"),
                L10n.s("Yapay zekâ sana bu kimlikle davranır"), "characters");
        List<Persona> personas = store.personas();
        // A draft belonging to ANY other identity (including one from a cancelled
        // "new persona" session) must not preview or persist into this editor.
        boolean draftForeign = personaAvatarDraft != null && !personaAvatarDraft.isEmpty()
                && (editing == null || personaDraftOwnerId == null || !editing.id.equals(personaDraftOwnerId));
        if (draftForeign) {
            deleteAvatarFile(personaAvatarDraft);
            personaAvatarDraft = null;
        }
        personaDraftOwnerId = editing != null ? editing.id : null;

        String name = editing != null ? editing.name : "";
        String description = editing != null ? editing.description : "";
        final String storedAvatar = editing != null ? editing.avatarPath : "";

        LinearLayout preview = makeCard();
        preview.setGravity(Gravity.CENTER);
        personaPreviewPane = preview;
        fillPersonaPreview(preview, name,
                personaAvatarDraft != null && !personaAvatarDraft.isEmpty() ? personaAvatarDraft : storedAvatar);
        addCardToPage(preview);

        EditText nameField = field(L10n.s(L10n.s("Adın (boş bırakılırsa \"Sen\" sayılır)")), name, 1);
        nameField.setSingleLine(true);
        personaNameField = nameField;
        EditText descriptionField = field(L10n.s(L10n.s("Kendini tanıt (davranışını ve sesini etkiler)")), description, 3);
        addBody(L10n.s(L10n.s("RP sohbetlerinde mesajların \"Sen\" etiketiyle kalır; model sana \"Kullanıcı kimliği\" bloğunda burada yazdığın bilgilerle davranır.")));
        addPrimary(L10n.s(L10n.s("Profil fotoğrafı seç")), v -> pickAvatar(true));
        if ((personaAvatarDraft != null && !personaAvatarDraft.isEmpty())
                || !storedAvatar.isEmpty()) {
            addDangerButton(L10n.s(L10n.s("Fotoğrafı kaldır")), v -> {
                String path = personaAvatarDraft != null && !personaAvatarDraft.isEmpty()
                        ? personaAvatarDraft : storedAvatar;
                deleteAvatarFile(path);
                personaAvatarDraft = "";
                if (personaPreviewPane != null) {
                    fillPersonaPreview(personaPreviewPane,
                            personaNameField != null ? personaNameField.getText().toString().trim() : name, "");
                }
            });
        }
        if (editing != null && !store.isDefaultPersona(editing.id)) {
            addSecondary(L10n.s(L10n.s("Varsayılan kişilik yap")), v -> {
                store.setDefaultPersonaId(editing.id);
                Toast.makeText(this, L10n.s(L10n.s("Varsayılan kişilik güncellendi.")), Toast.LENGTH_SHORT).show();
                showPersonas();
            });
        }
        addPrimary(L10n.s(L10n.s("Kişiliği kaydet")), v -> {
            String savedName = nameField.getText().toString().trim();
            String savedDescription = descriptionField.getText().toString().trim();
            String savedAvatar = personaAvatarDraft != null && !personaAvatarDraft.isEmpty()
                    ? personaAvatarDraft : storedAvatar;
            // A fresh draft replaces the persisted file; delete the old one only once
            // the save succeeded, so backing out can never orphan the kept avatar.
            String replacedAvatar = personaAvatarDraft != null && !personaAvatarDraft.isEmpty()
                    && !storedAvatar.isEmpty() && !storedAvatar.equals(personaAvatarDraft)
                    ? storedAvatar : null;
            List<Persona> updated = new ArrayList<>(store.personas());
            if (editing != null) {
                for (int i = 0; i < updated.size(); i++) {
                    if (updated.get(i).id.equals(editing.id)) {
                        Persona p = updated.get(i);
                        p.name = savedName;
                        p.description = savedDescription;
                        p.avatarPath = savedAvatar;
                        updated.set(i, p);
                        break;
                    }
                }
            } else {
                String id = "persona-" + System.currentTimeMillis();
                updated.add(new Persona(id, savedName, savedDescription, savedAvatar));
                if (updated.size() == 1) store.setDefaultPersonaId(id); // first identity becomes default
            }
            personaAvatarDraft = null;
            personaDraftOwnerId = null;
            if (!store.savePersonas(updated)) {
                Toast.makeText(this, L10n.s(L10n.s("Profil kayda geçemedi.")), Toast.LENGTH_SHORT).show();
                return;
            }
            if (replacedAvatar != null) deleteAvatarFile(replacedAvatar);
            Toast.makeText(this, L10n.s(L10n.s("Kullanıcı kişiliği kaydedildi.")), Toast.LENGTH_SHORT).show();
            showPersonas();
        });
        if (editing != null) {
            addDangerButton(L10n.s(L10n.s("Kişiliği sil")), v -> confirm(
                    (savedNameSafe(editing) ) + L10n.s(L10n.s(" kişiliği silinsin mi?")), L10n.s(L10n.s("Sil")), () -> {
                List<Persona> remaining = new ArrayList<>(store.personas());
                Persona doomed = null;
                for (Persona p : remaining) if (p.id.equals(editing.id)) { doomed = p; remaining.remove(p); break; }
                if (doomed == null) return;
                if (!remaining.isEmpty() && store.isDefaultPersona(editing.id)) {
                    store.setDefaultPersonaId(remaining.get(0).id);
                }
                if (!store.savePersonas(remaining)) {
                    Toast.makeText(this, L10n.s(L10n.s("Profil kayda geçemedi.")), Toast.LENGTH_SHORT).show();
                    return;
                }
                store.clearChatPersonaReferences(editing.id);
                if (!doomed.avatarPath.isEmpty()) deleteAvatarFile(doomed.avatarPath);
                Toast.makeText(this, L10n.s(L10n.s("Kişilik silindi.")), Toast.LENGTH_SHORT).show();
                showPersonas();
            }));
        }
    }

    /** Persona display name used in confirm dialogs; falls back to "Adsız kişilik". */
    private String savedNameSafe(Persona persona) {
        return persona.name == null || persona.name.trim().isEmpty()
                ? L10n.s(L10n.s("Adsız kişilik")) : persona.name;
    }

    /** One-tap copies a preset into the user's own editable character list. */
    private void addPresetGrid() {
        int rows = (PresetCharacters.PRESETS.length + 1) / 2;
        for (int i = 0; i < rows; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 2; j++) {
                int index = i * 2 + j;
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (j == 1) cell.setMargins(dp(6), 0, 0, 0);
                if (index < PresetCharacters.PRESETS.length) {
                    row.addView(presetCell(PresetCharacters.PRESETS[index]), cell);
                } else {
                    row.addView(new View(this), cell);
                }
            }
            LinearLayout.LayoutParams rowParams = fullWidth();
            rowParams.setMargins(0, 0, 0, dp(8));
            content.addView(row, rowParams);
        }
    }

    /** Vertical preset card mirroring the character grid cells, with a copy affordance. */
    private LinearLayout presetCell(PresetCharacters.Preset preset) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(10), dp(13), dp(10), dp(13));
        card.setBackground(rounded(Design.cardBg(), Design.cardStroke(), 20));
        View avatar = Avatar.forName(this, preset.name, 52);
        card.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView name = label(preset.name, 15, Design.text());
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, dp(8), 0, 0);
        card.addView(name);
        TextView desc = label(brief(preset.description).replace("\n", " "), 12, Design.muted());
        desc.setGravity(Gravity.CENTER);
        desc.setMaxLines(2);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        desc.setPadding(0, dp(3), 0, 0);
        card.addView(desc);
        TextView copyHint = label("＋ Ekle", 13, Design.accent());
        copyHint.setGravity(Gravity.CENTER);
        copyHint.setPadding(0, dp(7), 0, 0);
        card.addView(copyHint);
        card.setOnClickListener(v -> confirm(preset.name + L10n.s(L10n.s(" karakter listene kopyalansın mı? Kopyalandıktan sonra tamamen düzenlenebilir.")), () -> {
            CharacterProfile copied = new CharacterProfile();
            copied.name = preset.name;
            copied.description = preset.description;
            copied.personality = preset.personality;
            copied.instructions = preset.instructions;
            copied.examples = preset.examples;
            if (!store.saveCharacter(copied)) {
                Toast.makeText(this, L10n.s(L10n.s("Karakter kaydedilemedi: verisi okunamıyor.")), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, preset.name + L10n.s(" eklendi."), Toast.LENGTH_SHORT).show();
            showCharacters();
        }));
        return card;
    }

    private void showCharacterEditor(CharacterProfile existing) {
        showCharacterEditor(existing, null, null);
    }

    private void showCharacterEditor(CharacterProfile existing, Runnable afterSave) {
        showCharacterEditor(existing, afterSave, null);
    }

    private void showCharacterEditor(CharacterProfile existing, Runnable afterSave, Runnable onBack) {
        visibleChatId = null;
        backAction = onBack != null ? onBack : this::showCharacters;
        final boolean editing = existing != null;
        editorCharacter = existing;
        editorAfterSave = afterSave;
        editorOnBack = onBack;
        pickedAvatarPath = editing && existing.avatarPath != null ? existing.avatarPath : "";
        setPage(editing ? L10n.s("Karakteri düzenle") : "Yeni karakter", L10n.s("Bilgiler bu telefonda saklanır"), "characters");
        EditText name = field(L10n.s("Karakter adı"), editing ? existing.name : "");
        name.setSingleLine(true);
        EditText description = field(L10n.s("Kısa tanım"), editing ? existing.description : "", 3);
        EditText personality = field(L10n.s("Kişilik ve konuşma tarzı"), editing ? existing.personality : "", 3);
        EditText instructions = field(L10n.s("Karakter talimatları"), editing ? existing.instructions : "", 4);
        EditText examples = field(L10n.s("Örnek diyalog"), editing ? existing.examples : "", 3);

        addSection(L10n.s("Profil fotoğrafı"), null, null);
        addBody(L10n.s("Fotoğraf gizli depoya küçük kopya olarak yazılır; seçtiğin kaynak dosya silinmez. Sohbetlerde karakterin avatarı bu foto olur ve Kaydet ile saklanır."));
        avatarStatusView = label(pickedAvatarPath.isEmpty()
                ? L10n.s("Fotoğraf: seçilmedi") : L10n.s("Fotoğraf: ✓ hazır"), 13, pickedAvatarPath.isEmpty() ? Design.muted() : Design.accent());
        avatarStatusView.setPadding(0, dp(0), 0, dp(6));
        content.addView(avatarStatusView, fullWidth());
        addPrimary(L10n.s("Fotoğraf seç"), v -> pickAvatar(false));
        if (!pickedAvatarPath.isEmpty()) {
            addDangerButton(L10n.s("Fotoğrafı kaldır"), v -> {
                deleteAvatarFile(pickedAvatarPath);
                pickedAvatarPath = "";
                if (avatarStatusView != null) {
                    avatarStatusView.setText(L10n.s(L10n.s("Fotoğraf: seçilmedi")));
                    avatarStatusView.setTextColor(Design.muted());
                }
            });
        }

        addPrimary(L10n.s("Kaydet"), v -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError(L10n.s("Bir ad gerekli")); return; }
            CharacterProfile character = editing ? existing : new CharacterProfile();
            character.name = name.getText().toString().trim();
            character.description = description.getText().toString().trim();
            character.personality = personality.getText().toString().trim();
            character.instructions = instructions.getText().toString().trim();
            character.examples = examples.getText().toString().trim();
            // Delete a replaced persisted avatar only after the save succeeded.
            String replacedAvatar = editing
                    && pickedAvatarPath != null && !pickedAvatarPath.isEmpty()
                    && existing.avatarPath != null && !existing.avatarPath.isEmpty()
                    && !existing.avatarPath.equals(pickedAvatarPath)
                    ? existing.avatarPath : null;
            character.avatarPath = pickedAvatarPath;
            editorCharacter = null;
            pickedAvatarPath = "";
            if (!store.saveCharacter(character)) {
                Toast.makeText(this, L10n.s(L10n.s("Değişiklik kaydedilemedi: karakter verisi okunamıyor.")), Toast.LENGTH_LONG).show();
                return;
            }
            if (replacedAvatar != null) deleteAvatarFile(replacedAvatar);
            if (afterSave != null) afterSave.run(); else showCharacters();
        });
        if (editing && !BUILTIN_ROLEPLAY_ID.equals(existing.id)) {
            addDangerButton(L10n.s("Karakteri sil"), v -> confirm(existing.name + " silinsin mi?", () -> {
                CharacterProfile snapshot = store.character(existing.id); // fresh copy off the stored JSON
                if (!store.deleteCharacter(existing.id)) {
                    Toast.makeText(this, L10n.s(L10n.s("Silinemedi: karakter verisi okunamıyor.")), Toast.LENGTH_LONG).show();
                    return;
                }
                showCharacters();
                // Group members of this character keep working: prompts tolerate a
                // missing member, and the undo path simply re-saves the profile.
                if (snapshot != null) {
                    showUndoBar(existing.name + L10n.s(" silindi."), () -> {
                        if (!store.saveCharacter(snapshot)) {
                            Toast.makeText(this, L10n.s(L10n.s("Geri alınamadı: karakter verisi okunamıyor.")), Toast.LENGTH_LONG).show();
                            return;
                        }
                        Toast.makeText(this, existing.name + L10n.s(" geri getirildi."), Toast.LENGTH_SHORT).show();
                        showCharacters();
                    }, null);
                }
            }));
        }
    }

    private void showGroupEditor(Group existing) {
        showGroupEditor(existing, this::showHome);
    }

    private void showGroupEditor(Group existing, Runnable onBack) {
        visibleChatId = null;
        backAction = onBack != null ? onBack : this::showHome;
        boolean editing = existing != null;
        setPage(editing ? L10n.s("Grubu düzenle") : "Yeni grup", L10n.s("Hikâyene katılacak karakterleri seç"), "home");
        EditText name = field(L10n.s("Sohbet adı"), editing ? existing.name : "");
        name.setSingleLine(true);
        addSection(L10n.s("Karakterler"), L10n.s("Bir veya daha fazla"), null);
        List<CheckBox> choices = new ArrayList<>();
        List<CharacterProfile> characters = store.characters();
        for (CharacterProfile character : characters) {
            CheckBox choice = new CheckBox(this);
            choice.setText("   " + character.name);
            choice.setTextSize(16);
            choice.setPadding(dp(4), dp(5), dp(4), dp(5));
            choice.setChecked(editing && existing.characterIds.contains(character.id));
            content.addView(choice);
            choices.add(choice);
        }
        if (characters.isEmpty()) {
            Runnable returnToEditor = () -> showGroupEditor(existing, onBack);
            addEmpty(L10n.s("Önce karakter oluştur"), L10n.s("RP için en az bir karakter gerekli."), L10n.s("Karakter oluştur"),
                    v -> showCharacterEditor(null, returnToEditor, returnToEditor));
        }
        addPrimary(editing ? L10n.s("Değişiklikleri kaydet") : L10n.s("Sohbeti başlat"), v -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError(L10n.s(L10n.s("Sohbet adı gerekli"))); return; }
            Group group = editing ? existing : new Group();
            group.name = name.getText().toString().trim();
            group.characterIds.clear();
            for (int i = 0; i < choices.size(); i++) if (choices.get(i).isChecked()) group.characterIds.add(characters.get(i).id);
            if (group.characterIds.isEmpty()) { name.setError(L10n.s(L10n.s("En az bir karakter seç"))); return; }
            group.roleplay = true;
            if (!store.saveGroup(group)) {
                Toast.makeText(this, L10n.s(L10n.s("Değişiklik kaydedilemedi: sohbet verisi okunamıyor.")), Toast.LENGTH_LONG).show();
                return;
            }
            showChat(group);
        });
        if (editing) {
            addSection(L10n.s("Sohbet"), null, null);
            addDangerButton(L10n.s("Sohbet geçmişini temizle"), v -> clearChat(existing));
            addDangerButton("Grubu sil", v -> confirm(existing.name + " ve sohbeti silinsin mi?", L10n.s("Sil"), () -> {
                LocalAiRuntime.INSTANCE.stopGeneration();
                // Snapshots must come off the store BEFORE deleteGroup wipes the record;
                // image files are kept until the undo window closes so restore can win.
                Group snapshot = store.group(existing.id);
                List<Message> transcript = new ArrayList<>(store.messages(existing.id));
                store.deleteGroup(existing.id);
                showHome();
                if (snapshot != null) {
                    showUndoBar(existing.name + L10n.s(" silindi."), () -> {
                        if (!store.saveGroup(snapshot) || !store.replaceMessages(snapshot.id, transcript)) {
                            store.deleteGroup(snapshot.id); // compensating: no half-restored chat stays visible
                            Toast.makeText(this, L10n.s(L10n.s("Geri alınamadı: sohbet verisi okunamıyor.")), Toast.LENGTH_LONG).show();
                            return;
                        }
                        Toast.makeText(this, existing.name + L10n.s(" geri getirildi."), Toast.LENGTH_SHORT).show();
                        showChats();
                    }, () -> deleteMessageImages(snapshot.id));
                }
            }));
        }
    }

    private void createSingleCharacterRoleplay() {
        List<CharacterProfile> characters = store.characters();
        if (characters.isEmpty()) {
            showCharacterEditor(null, this::createSingleCharacterRoleplay);
            return;
        }
        String[] names = new String[characters.size()];
        for (int i = 0; i < names.length; i++) names[i] = characters.get(i).name;
        new AlertDialog.Builder(this).setTitle(L10n.s(L10n.s("RP karakterini seç")))
                .setItems(names, (dialog, index) -> {
                    CharacterProfile character = characters.get(index);
                    Group chat = new Group();
                    chat.name = character.name + L10n.s(" ile RP");
                    chat.characterIds.add(character.id);
                    chat.roleplay = true;
                    store.saveGroup(chat);
                    showChat(chat);
                }).setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).show();
    }

    private CharacterProfile chooseSpeaker(Group group, String lastUserText) {
        CharacterProfile manual = manualSpeaker(group);
        return manual != null ? manual : chooseNextSpeaker(group, lastUserText);
    }

    private CharacterProfile manualSpeaker(Group group) {
        String id = prefs().getString("next_speaker_" + group.id, null);
        if (id == null || !group.characterIds.contains(id)) return null;
        return store.character(id);
    }

    private void setManualSpeaker(String groupId, String characterId) {
        prefs().edit().putString("next_speaker_" + groupId, characterId).apply();
    }

    private void clearManualSpeaker(String groupId) {
        prefs().edit().remove("next_speaker_" + groupId).apply();
    }

    private CharacterProfile chooseNextSpeaker(Group group, String lastUserText) {
        List<CharacterProfile> available = new ArrayList<>();
        for (String id : group.characterIds) {
            CharacterProfile profile = store.character(id);
            if (profile != null) available.add(profile);
        }
        if (available.isEmpty()) return null;

        if (lastUserText != null && !lastUserText.trim().isEmpty()) {
            String haystack = searchText(lastUserText);
            CharacterProfile mentioned = null;
            int mentionedAt = Integer.MAX_VALUE;
            for (CharacterProfile profile : available) {
                String name = searchText(profile.name).trim();
                if (name.length() < 2) continue;
                java.util.regex.Matcher matcher = Pattern.compile(
                                "(?<![\\p{L}\\p{N}])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}])")
                        .matcher(haystack);
                if (!matcher.find()) continue;
                int index = matcher.start();
                if (index < mentionedAt || (index == mentionedAt && mentioned != null &&
                        name.length() > searchText(mentioned.name).trim().length())) {
                    mentioned = profile;
                    mentionedAt = index;
                }
            }
            if (mentioned != null) return mentioned;
        }

        List<Message> history = store.messages(group.id);
        for (int i = history.size() - 1; i >= 0; i--) {
            Message previous = history.get(i);
            // Image-only messages carry no turn content; they cannot continue the
            // speaker rotation, so scan past them too.
            if (previous.user || (previous.hasImage() && previous.text.isEmpty())) continue;
            for (int j = 0; j < available.size(); j++) {
                if (available.get(j).name.equals(previous.speaker)) return available.get((j + 1) % available.size());
            }
            break;
        }
        return available.get(0);
    }

    /**
     * Back action follows the origin (GUI review item 1): a chat opened from the home
     * card returns home, one opened from the list returns to the list.
     */
    private void showChat(Group group, Runnable openedFrom) {
        chatBackOrigin = openedFrom != null ? openedFrom : this::showChats;
        showChat(group);
    }

    private void showChat(Group group) {
        visibleChatId = group.id;
        backAction = chatBackOrigin != null ? chatBackOrigin : this::showChats;
        chatBackOrigin = null;
        autoScroll = true;
        String subtitle = chatSubtitle(group);
        setPage(group.name, subtitle, "chat", true);
        chatHeaderAvatarPath = "";
        for (String id : group.characterIds) {
            CharacterProfile face = store.character(id);
            if (face != null && face.avatarPath != null && !face.avatarPath.isEmpty()) {
                chatHeaderAvatarPath = face.avatarPath;
                break;
            }
        }
        chatStatusView.setText(chatStatusText(group));

        // Long chats: opening must not render the whole transcript at once (Task 08).
        // Only the newest messages enter the view; older ones load on demand upward.
        chatOlderButton = null;
        chatHistory = new ArrayList<>(store.messages(group.id));
        chatRenderStart = ChatWindow.initialStart(chatHistory.size(), MAX_INITIAL_RENDER);
        rebuildChatRender(group);
        buildChatComposer(group);
        scrollChatToBottom(true);
        maybeAutoGenerate(group);
    }

    /** Renders the message window (older-marker + balloons) from chatHistory into content. */
    private void rebuildChatRender(Group group) {
        content.removeAllViews();
        chatOlderButton = null;
        chatEmptyState = null;
        if (chatHistory.isEmpty()) {
            chatEmptyState = addEmpty(L10n.s("RP sahnesi burada başlıyor"), L10n.s("Mesajını yaz; seçili karakter otomatik yanıtlasın."), null, null);
            addStarterChips(group);
        } else {
            addOlderMessagesButton();
            for (int i = chatRenderStart; i < chatHistory.size(); i++) {
                addStoredMessage(content, chatHistory.get(i));
            }
        }
    }

    /**
     * Starter chips (GUI review item 14) on the empty chat: tap fills the composer
     * so the line can be edited before sending — the app never sends by itself.
     */
    private void addStarterChips(Group group) {
        String[][] starters = {
                {L10n.s(L10n.s("İlk sahneyi kur")), L10n.s(L10n.s("*Kapıda durur, yorgun bir gülümseme.* Selam… buraya tam zamanında geldin."))},
                {L10n.s(L10n.s("Kısa bir selam")), L10n.s(L10n.s("Selam! Burada seni tekrar görmek güzel."))},
                {L10n.s(L10n.s("Arka planı sor")), L10n.s(L10n.s("Bu sahneyi nasıl kurdun? Baştan anlat."))},
        };
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (String[] starter : starters) {
            TextView chip = label(starter[0], 13, Design.accent());
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setMinimumHeight(dp(40));
            chip.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 20));
            chip.setOnClickListener(v -> {
                if (chatInput == null || !group.id.equals(chatInputChatId)) return;
                chatInput.setText(starter[1]);
                chatInput.setSelection(starter[1].length());
                chatDrafts.put(group.id, starter[1]);
                reopenKeyboard(chatInput);
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (row.getChildCount() > 0) chipParams.setMarginStart(dp(8));
            row.addView(chip, chipParams);
        }
        // Insert the chip row right under the empty-state card and above the composer.
        if (chatEmptyState.getParent() == content) {
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    /** Load-on-demand marker above the rendered message window (Task 08). */
    private void addOlderMessagesButton() {
        if (chatOlderButton != null && chatOlderButton.getParent() == content) content.removeView(chatOlderButton);
        if (chatRenderStart <= 0) {
            chatOlderButton = null;
            return;
        }
        Button view = new Button(this);
        view.setAllCaps(false);
        view.setTextSize(13);
        view.setTextColor(Design.accent());
        view.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 12));
        view.setMinHeight(dp(48));
        view.setPadding(dp(14), 0, dp(14), 0);
        updateOlderLabel(view);
        view.setOnClickListener(v -> renderOlderChunk());
        content.addView(view, fullWidth());
        LinearLayout.LayoutParams olderParams = (LinearLayout.LayoutParams) view.getLayoutParams();
        olderParams.setMargins(0, dp(2), 0, dp(10));
        view.setLayoutParams(olderParams);
        chatOlderButton = view;
    }

    private void updateOlderLabel(Button view) {
        view.setText(L10n.english ? L10n.s("Older messages (") + chatRenderStart + ")"
                : "Daha eski mesajlar (" + chatRenderStart + ")");
    }

    /** Prepends the next older chunk in chronological order, above the marker button. */
    private void renderOlderChunk() {
        if (chatRenderStart <= 0 || chatOlderButton == null) return;
        int next = ChatWindow.olderStart(chatRenderStart, RENDER_CHUNK);
        int insertAt = content.indexOfChild(chatOlderButton);
        for (int i = next; i < chatRenderStart; i++) {
            Message message = chatHistory.get(i);
            TextView balloon = addStoredMessage(content, message);
            // addMessage appends the balloon inside its avatar row at the end of the list;
            // move the whole ROW up (the balloon itself is not a direct child of content).
            LinearLayout wrapper = (LinearLayout) messageRowOf(balloon);
            content.removeView(wrapper);
            content.addView(wrapper, Math.min(insertAt++, content.getChildCount()));
        }
        chatRenderStart = next;
        if (chatRenderStart > 0) {
            updateOlderLabel((Button) chatOlderButton);
        } else {
            content.removeView(chatOlderButton);
            chatOlderButton = null;
        }
    }

    private String chatSubtitle(Group group) {
        if (providerReady()) {
            // Provider-enabled routing always shows what the user opted into, even when
            // a local model also sits loaded (it wins nothing while the provider is on).
            return L10n.s(L10n.s("Online sağlayıcı · veri açık onayınla gider"));
        }
        if (!LocalAiRuntime.INSTANCE.isModelReady()) {
            if (LocalAiRuntime.INSTANCE.loadedModelPath() != null) {
                return L10n.s(L10n.s("Model yeniden hazırlanacak · çevrimdışı"));
            }
            return L10n.s(L10n.s("Çevrimdışı yanıtlar için önce AI ayarlarından model yükle"));
        }
        return group.characterIds.size() == 1 ? L10n.s(L10n.s("Bire bir karakter RP · cihaz içinde")) : L10n.s(L10n.s("Grup RP · cihaz içinde"));
    }

    private boolean modelCanRespond() {
        // Offline llama.cpp is the default; an explicitly enabled, fully configured
        // provider also counts as able to respond (DeepSeek item 14/B).
        return LocalAiRuntime.INSTANCE.isModelReady() || LocalAiRuntime.INSTANCE.loadedModelPath() != null
                || providerReady();
    }

    /** True only when the user explicitly enabled a complete, valid provider config. */
    private boolean providerReady() {
        if (!prefs().getBoolean("provider_enabled", false)) return false;
        if (!ApiKeyStore.INSTANCE.isConfigured(this)) return false;
        return OpenAiCompat.allowsEndpoint(providerEndpoint()) && !providerModel().isEmpty();
    }

    private String providerEndpoint() {
        return prefs().getString("provider_endpoint", "").trim();
    }

    private String providerModel() {
        return prefs().getString("provider_model", "").trim();
    }

    private int providerTurnDepth() {
        return Math.max(2, Math.min(20, prefs().getInt("provider_history_depth", 8)));
    }

    private void buildChatComposer(Group group) {
        if (chatComposer == null) return;
        chatComposer.removeAllViews();

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);

        // Composer hierarchy (GUI review item 13): the speaker chip reads as a quiet
        // secondary control; the input + accent send button carry the visual weight.
        chatSpeakerView = label("", 12, Design.muted());
        chatSpeakerView.setPadding(dp(14), dp(9), dp(14), dp(9));
        chatSpeakerView.setMinHeight(dp(40));
        chatSpeakerView.setBackground(rounded(Design.cardBg(), Design.cardBg(), 24));
        chatSpeakerView.setOnClickListener(v -> showSpeakerPicker(group));
        tools.addView(chatSpeakerView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button menu = button("⋯");
        menu.setTextSize(18);
        menu.setTextColor(Design.text());
        menu.setMinWidth(dp(44));
        menu.setMinHeight(dp(48));
        menu.setPadding(dp(12), 0, dp(12), 0);
        menu.setContentDescription(L10n.s(L10n.s("Sohbet menüsü")));
        menu.setBackground(rounded(Design.cardBg(), Design.cardBg(), 30));
        menu.setOnClickListener(v -> showChatMenu(group));
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        menuParams.setMargins(dp(8), 0, 0, 0);
        tools.addView(menu, menuParams);
        chatComposer.addView(tools, fullWidth());

        if (!modelCanRespond()) {
            chatModelHint = label(selectedModel == null ? L10n.s("Yanıtlar için GGUF modeli seç  ›") : L10n.s("Modeli yükle  ›"), 13, Design.accent());
            chatModelHint.setPadding(0, dp(8), 0, dp(2));
            chatModelHint.setOnClickListener(v -> openModels(() -> openChat(group.id)));
            chatComposer.addView(chatModelHint, fullWidth());
        } else {
            chatModelHint = null;
        }

        chatContinueButton = button("");
        chatContinueButton.setTextSize(14);
        chatContinueButton.setTextColor(Design.accent());
        chatContinueButton.setBackground(rounded(Design.cardBg(), Design.cardBg(), 24));
        chatContinueButton.setMinHeight(dp(44));
        chatContinueButton.setOnClickListener(v -> continueOrRetry(group));
        LinearLayout.LayoutParams continueParams = fullWidth();
        continueParams.setMargins(0, dp(8), 0, 0);
        chatComposer.addView(chatContinueButton, continueParams);

        EditText input = new EditText(this);
        input.setHint(L10n.s(L10n.s("Hikâyeye bir şey yaz…")));
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setGravity(Gravity.TOP);
        input.setTextSize(15);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setTextColor(Design.text());
        input.setHintTextColor(Design.muted());
        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setColor(Design.cardBg());
        inputBackground.setCornerRadius(dp(26));
        input.setBackground(inputBackground);
        chatInput = input;
        chatInputChatId = group.id;
        String draft = chatDrafts.get(group.id);
        if (draft != null && !draft.isEmpty()) {
            input.setText(draft);
            input.setSelection(draft.length());
        }
        // cAI-like composer: rounded pill input with a circular send/stop button on the right.
        LinearLayout composerRow = new LinearLayout(this);
        composerRow.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inputParams.setMargins(0, dp(8), 0, 0);
        composerRow.addView(input, inputParams);

        // Attachment buttons step down visually (muted strokes, not accent fills); the
        // round accent send stays the single prominent action.
        Button photo = button("📷");
        photo.setTextSize(15);
        photo.setPadding(0, 0, 0, 0);
        photo.setMinWidth(dp(44));
        photo.setMinHeight(dp(44));
        photo.setTextColor(Design.muted());
        photo.setContentDescription(L10n.s(L10n.s("Fotoğraf ekle")));
        photo.setBackground(rounded(Design.cardBg(), Design.cardBg(), 30));
        photo.setOnClickListener(v -> {
            imageAttachGroup = group;
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("image/*");
            try {
                startActivityForResult(
                        Intent.createChooser(pick, L10n.s(L10n.s("Fotoğraf seç"))), CHAT_PHOTO_PICK);
            } catch (android.content.ActivityNotFoundException noPicker) {
                Toast.makeText(this, L10n.s(L10n.s("Bu cihazda fotoğraf seçici yok.")), Toast.LENGTH_SHORT).show();
            }
        });
        composerRow.addView(photo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        Button paint = button("✨");
        paint.setTextSize(15);
        paint.setPadding(0, 0, 0, 0);
        paint.setMinWidth(dp(44));
        paint.setMinHeight(dp(44));
        paint.setTextColor(Design.accent());
        paint.setContentDescription(L10n.s(L10n.s("Görsel üret")));
        paint.setBackground(rounded(Design.cardBg(), Design.cardBg(), 30));
        paint.setOnClickListener(v -> showImageGenerateDialog(group));
        LinearLayout.LayoutParams paintParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        paintParams.setMargins(dp(8), 0, 0, 0);
        composerRow.addView(paint, paintParams);

        Button action = button("➤");
        chatActionButton = action;
        action.setTextSize(18);
        action.setPadding(0, 0, 0, 0);
        action.setMinWidth(dp(48));
        action.setMinHeight(dp(48));
        action.setContentDescription(L10n.s(L10n.s("Gönder (veya üretimi durdur)")));
        action.setTextColor(Design.onAccent());
        action.setBackground(rounded(Design.accent(), Design.accent(), 30));
        action.setOnClickListener(v -> {
            if (generatingChatId != null) {
                LocalAiRuntime.INSTANCE.stopGeneration();
                if (providerClient != null) providerClient.stop(); // online turn: cut the SSE stream too
            }
            else if (LocalImageEngine.INSTANCE.isGenerating()) LocalImageEngine.INSTANCE.stopGeneration();
            else sendMessage(group, input);
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        actionParams.setMargins(dp(8), dp(8), 0, 0);
        composerRow.addView(action, actionParams);
        chatComposer.addView(composerRow, fullWidth());

        if (pendingComposerImage != null && pendingComposerImage.isFile()
                && group.id.equals(pendingComposerImageChatId)) {
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(12), 0, dp(12), 0);
            chip.setMinimumHeight(dp(38));
            chip.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 20));
            TextView chipLabel = label(L10n.s(L10n.s("📷 Fotoğraf eklendi · gönder ile birlikte gider")), 13, Design.text());
            TextView chipRemove = label("✕", 15, Design.muted());
            chipRemove.setPadding(dp(10), 0, 0, 0);
            chipRemove.setOnClickListener(v -> {
                saveComposerDraftTo(chatInputChatId);
                removePendingComposerChip();
                if (chatInput != null) reopenKeyboard(chatInput);
            });
            chip.addView(chipLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            chip.addView(chipRemove);
            LinearLayout.LayoutParams chipParams = fullWidth();
            chipParams.setMargins(0, dp(6), 0, 0);
            pendingComposerImageChip = chip;
            chatComposer.addView(chip, chipParams);
        }

        updateChatControls(group);
    }

    private void clearPendingComposerImage() {
        pendingComposerImage = null;
        pendingComposerImageChip = null;
        pendingComposerImageChatId = null;
    }

    /** Removes the photo chip view in place; a send flow must not rebuild the composer. */
    private void removePendingComposerChip() {
        if (pendingComposerImageChip != null) {
            ViewGroup chipContent = (ViewGroup) pendingComposerImageChip.getParent();
            if (chipContent != null) chipContent.removeView(pendingComposerImageChip);
        }
        clearPendingComposerImage();
    }

    /** Keeps the keyboard open after in-place composer changes (send / chip removal). */
    private void reopenKeyboard(EditText input) {
        if (input == null) return;
        input.requestFocus();
        input.setSelection(input.getText().length());
        input.post(() -> {
            if (!uiAlive() || input.getWindowToken() == null) return;
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, 0);
        });
    }

    /** Persists the composer text so a rebuild (photo chip, send) cannot eat a draft. */
    private void saveComposerDraftTo(String chatId) {
        if (chatInput == null || chatId == null) return;
        String draft = chatInput.getText().toString();
        if (draft.trim().isEmpty()) chatDrafts.remove(chatId);
        else chatDrafts.put(chatId, draft);
    }

    /**
     * Prompt sheet for the ✨ composer button. One 512×512 frame is requested; the
     * checkpoint decides style quality. Everything runs on-device.
     */
    private void showImageGenerateDialog(Group group) {
        if (!LocalImageEngine.INSTANCE.isModelReady()) {
            new AlertDialog.Builder(this)
                    .setTitle(L10n.s(L10n.s("Görsel modeli yüklü değil")))
                    .setMessage(L10n.s(L10n.s("Önce Ayarlar → Model önerileri → Görsel bölümünden bir görsel modeli indir ve yükle.")))
                    .setPositiveButton(L10n.s(L10n.s("Model önerilerini aç")), (d, w) -> openModels(null))
                    .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null)
                    .show();
            return;
        }
        if (LocalImageEngine.INSTANCE.isGenerating() || LocalImageEngine.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s(L10n.s("Görsel motoruyla bir işlem sürüyor; bitmesini bekle.")), Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(16), dp(22), 0);
        TextView note = label(L10n.s(L10n.s("Bu cihazda tek bir 512×512 görsel üretilir; CPU ile 1–3 dakika sürebilir. İptal etmek için duraklat düğmesine bas.")), 12, Design.muted());
        form.addView(note);
        // Dialog-scoped inputs: they must never enter the page's `content` tree, so
        // they are built with a detached helper instead of the page-bound field().
        EditText promptInput = dialogField(L10n.s(L10n.s("Görsel fikri")));
        promptInput.setHint(L10n.s(L10n.s("Örn: fırtınada duran deniz feneri")));
        form.addView(promptInput);
        EditText negativeInput = dialogField(L10n.s(L10n.s("İstenmeyenler (boş kalabilir)")));
        negativeInput.setHint(L10n.s(L10n.s("Örn: bulanık, düşük kalite")));
        form.addView(negativeInput);
        // Sampling control panel (DeepSeek item 8): steps / CFG / seed go with the
        // prompt and persist for the next run; empty inputs keep engine defaults.
        SharedPreferences prefs = prefs();
        int preferredSteps = prefs.getInt("img_steps", LocalImageEngine.INSTANCE.preparedStepCount());
        String preferredCfg = prefs.getString("img_cfg", "5");
        EditText stepsInput = dialogField(L10n.s(L10n.s("Adım sayısı (10–50)")));
        stepsInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        stepsInput.setText(String.valueOf(preferredSteps));
        stepsInput.setSingleLine(true);
        form.addView(stepsInput);
        EditText cfgInput = dialogField(L10n.s(L10n.s("CFG ölçeği (1.5–15)")));
        cfgInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        cfgInput.setText(preferredCfg);
        cfgInput.setSingleLine(true);
        form.addView(cfgInput);
        EditText seedInput = dialogField(L10n.s(L10n.s("Tohum (boş = rastgele)")));
        seedInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        seedInput.setSingleLine(true);
        seedInput.setText(prefs.getString("img_seed", ""));
        form.addView(seedInput);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(L10n.s(L10n.s("Görsel üret (")) + LocalImageEngine.INSTANCE.loadedModelName() + ")")
                .setView(form)
                .setPositiveButton(L10n.s(L10n.s("Üret")), null)
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String prompt = promptInput.getText().toString().trim();
            if (prompt.isEmpty()) {
                // Keep the dialog open; a typed "avoid" list must not be lost.
                Toast.makeText(this, L10n.s("Bir fikir yaz."), Toast.LENGTH_SHORT).show();
                return;
            }
            int steps = clampInt(stepsInput.getText().toString(), preferredSteps, 10, 50);
            float cfg = clampFloat(cfgInput.getText().toString(), 5f, 1.5f, 15f);
            long seed = parseSeed(seedInput.getText().toString());
            prefs.edit().putInt("img_steps", steps).putString("img_cfg", String.valueOf(cfg))
                    .putString("img_seed", seedInput.getText().toString().trim()).apply();
            dialog.dismiss();
            startChatImageGeneration(group, prompt, negativeInput.getText().toString().trim(), steps, cfg, seed);
        }));
        tintDialog(dialog);
        dialog.show();
    }

    /**
     * Gallery (DeepSeek item 8): every PNG under the images directory, newest first.
     * Tap opens the fullscreen viewer; long press offers avatar/re-attach/delete.
     */
    private void showGallery(Runnable back) {
        visibleChatId = null;
        backAction = back != null ? back : this::showSettings;
        setPage(L10n.s("Görsel galerisi"), L10n.s("Üretilen ve sohbete eklenen görseller"), "settings");
        File directory = LocalImageEngine.INSTANCE.imagesDirectory(this);
        File[] files = directory.listFiles();
        List<File> images = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase(java.util.Locale.US);
                if (file.isFile() && name.endsWith(".png")) images.add(file);
            }
        }
        images.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        if (images.isEmpty()) {
            addEmpty(L10n.s("Burada henüz görsel yok"), L10n.s("Sohbette ✨ ile üret ya da 📷 ile bir fotoğraf ekle."), null, null);
            return;
        }
        for (File image : images) addGalleryRow(image);
    }

    private void addGalleryRow(File image) {
        LinearLayout card = makeCard();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        setBackgroundThumb(thumb, image.getPath(), dp(56));
        row.addView(thumb, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView name = label(image.getName(), 14, Design.text());
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        texts.addView(name);
        TextView meta = label(new Date(image.lastModified()) + L10n.s("  ·  ") + humanSize(image.length()), 12, Design.muted());
        meta.setPadding(0, dp(3), 0, 0);
        texts.addView(meta);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);
        card.setOnClickListener(v -> showImageViewer(image.getPath()));
        card.setOnLongClickListener(v -> {
            showGalleryActions(image);
            return true;
        });
        addCardToPage(card);
    }

    /** Gallery thumbs decode off the UI thread; the view is replaced when ready. */
    private void setBackgroundThumb(ImageView view, String path, int sizePx) {
        Thread decode = new Thread(() -> {
            Bitmap bitmap = LocalImageEngine.INSTANCE.decode(path, sizePx);
            if (bitmap == null) return;
            runOnUiThread(() -> {
                if (view.isAttachedToWindow()) view.setImageBitmap(bitmap);
                else bitmap.recycle();
            });
        }, "localrp-gallery-thumb");
        decode.start();
    }

    /** Long-press gallery actions: character avatar, re-attach to a chat, delete. */
    private void showGalleryActions(File image) {
        String[] items = {L10n.s(L10n.s("Karakter avatarı yap")), L10n.s("Sohbete ekle"), L10n.s(L10n.s("Sil"))};
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(image.getName())
                .setItems(items, (d, which) -> {
                    if (which == 0) makeAvatarFrom(image);
                    else if (which == 1) attachGalleryImage(image);
                    else if (galleryImageInUse(image)) {
                        Toast.makeText(this, L10n.s(L10n.s("Bu görsel bir sohbette veya karakter avatarında kullanılıyor; önce o kaydı sil.")), Toast.LENGTH_LONG).show();
                    } else confirm(L10n.s(L10n.s("Bu görsel silinsin mi?")), L10n.s(L10n.s("Sil")), () -> {
                        if (image.delete()) {
                            Toast.makeText(this, L10n.s(L10n.s("Görsel silindi.")), Toast.LENGTH_SHORT).show();
                            showGallery(this::showSettings);
                        } else {
                            Toast.makeText(this, L10n.s(L10n.s("Silinemedi: dosya yazılabilir değil.")), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).create();
        tintDialog(dialog);
        dialog.show();
    }

    /** A gallery file referenced by a transcript or a character avatar must not be deleted. */
    private boolean galleryImageInUse(File image) {
        String path = image.getPath();
        for (Group group : store.groups()) {
            for (Message message : store.messages(group.id)) {
                if (message.hasImage() && path.equals(message.image)) return true;
            }
        }
        for (CharacterProfile character : store.characters()) {
            if (character.avatarPath != null && path.equals(character.avatarPath)) return true;
        }
        return false;
    }

    /** Copies the image into the avatar space and lets the user pick the character. */
    private void makeAvatarFrom(File image) {
        List<CharacterProfile> characters = store.characters();
        if (characters.isEmpty()) {
            Toast.makeText(this, L10n.s(L10n.s("Önce bir karakter oluştur.")), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[characters.size()];
        for (int i = 0; i < characters.size(); i++) names[i] = characters.get(i).name;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(L10n.s("Hangi karakter?"))
                .setItems(names, (d, which) -> {
                    CharacterProfile character = characters.get(which);
                    File avatarDirectory = new File(getFilesDir(), "avatars");
                    if (!avatarDirectory.exists() && !avatarDirectory.mkdirs()) {
                        Toast.makeText(this, L10n.s(L10n.s("Avatar kopyası yazılamadı.")), Toast.LENGTH_LONG).show();
                        return;
                    }
                    File target = new File(avatarDirectory,
                            "ava-gen-" + System.currentTimeMillis() + ".png");
                    boolean copied = false;
                    try (java.io.FileInputStream input = new java.io.FileInputStream(image)) {
                        try (java.io.FileOutputStream output = new java.io.FileOutputStream(target)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = input.read(buffer)) > 0) output.write(buffer, 0, read);
                            copied = true;
                        }
                    } catch (java.io.IOException failed) {
                        // copied stays false; reported below
                    }
                    if (!copied) {
                        Toast.makeText(this, L10n.s(L10n.s("Avatar kopyası yazılamadı.")), Toast.LENGTH_LONG).show();
                        return;
                    }
                    character.avatarPath = target.getPath();
                    if (!store.saveCharacter(character)) {
                        target.delete(); // an unsaved copy must not pile up in storage
                        Toast.makeText(this, L10n.s(L10n.s("Avatar kaydedilemedi: karakter verisi okunamıyor.")), Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(this, character.name + L10n.s(L10n.s(" için avatar güncellendi.")), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).create();
        tintDialog(dialog);
        dialog.show();
    }

    /** Re-attaches a gallery image to any chat as the user's photo message. */
    private void attachGalleryImage(File image) {
        List<Group> groups = store.groups();
        if (groups.isEmpty()) {
            Toast.makeText(this, L10n.s(L10n.s("Önce bir sohbet oluştur.")), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) names[i] = groups.get(i).name;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(L10n.s("Hangi sohbet?"))
                .setItems(names, (d, which) -> {
                    Group group = groups.get(which);
                    Message sent = new Message("Sen", "", true, System.currentTimeMillis(), image.getPath());
                    store.addMessage(group.id, sent);
                    Toast.makeText(this, group.name + L10n.s(" sohbetine eklendi."), Toast.LENGTH_SHORT).show();
                    if (group.id.equals(visibleChatId)) showChat(group);
                })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).create();
        tintDialog(dialog);
        dialog.show();
    }

    /** Numeric dialog field clamp; bad or empty input keeps the current value. */
    private int clampInt(String raw, int current, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(minimum, Math.min(maximum, value));
        } catch (NumberFormatException bad) {
            return current;
        }
    }

    private float clampFloat(String raw, float current, float minimum, float maximum) {
        try {
            float value = Float.parseFloat(raw.trim().replace(',', '.'));
            if (Float.isNaN(value) || Float.isInfinite(value)) return current;
            return Math.max(minimum, Math.min(maximum, value));
        } catch (NumberFormatException bad) {
            return current;
        }
    }

    /** Numeric seed; empty or unparseable input maps to -1 = engine-chosen random. */
    private long parseSeed(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException bad) {
            return -1L;
        }
    }

    /** Dialog-safe EditText twin of field(...); detached from the page content tree. */    private EditText dialogField(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(15);
        input.setMinLines(1);
        input.setGravity(Gravity.TOP);
        input.setPadding(dp(14), dp(11), dp(14), dp(11));
        input.setTextColor(Design.text());
        input.setHintTextColor(Design.muted());
        GradientDrawable background = new GradientDrawable();
        background.setColor(Design.cardBg());
        background.setCornerRadius(dp(16));
        input.setBackground(background);
        return input;
    }

    /** Runs the on-device generation and routes its events into the open chat. */
    private void startChatImageGeneration(Group group, String prompt, String negative) {
        SharedPreferences prefs = prefs();
        startChatImageGeneration(group, prompt, negative,
                prefs.getInt("img_steps", LocalImageEngine.INSTANCE.preparedStepCount()), 5f, -1L);
    }

    /** Runs the on-device generation and routes its events into the open chat. */
    private void startChatImageGeneration(Group group, String prompt, String negative,
                                          int steps, float cfg, long seed) {
        if (generatingChatId != null) {
            Toast.makeText(this, L10n.s(L10n.s("Önce yürüyen yanıtı bekleyin.")), Toast.LENGTH_SHORT).show();
            return;
        }
        if (chatStatusView != null) {
            chatStatusView.setText(LocalImageEngine.INSTANCE.loadedModelName() + L10n.s(L10n.s(" · görsel üretiliyor…")));
        }

        boolean accepted = LocalImageEngine.INSTANCE.generate(
                this, prompt, negative, 512, 512, steps, cfg,
                seed, new LocalImageEngine.ImageListener() {
            @Override public void onStatus(String message) {
                if (!uiAlive()) return;
                if (group.id.equals(visibleChatId) && chatStatusView != null) {
                    chatStatusView.setText(message);
                }
            }
            @Override public void onReady(File file) {
                if (!uiAlive()) return;
                stopImageGeneratingTick();
                Group live = store.group(group.id);
                if (live == null) return;
                addGeneratedImage(group, file.getPath());
            }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                stopImageGeneratingTick();
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                if (group.id.equals(visibleChatId) && chatStatusView != null) {
                    Group live = store.group(group.id);
                    if (live != null) chatStatusView.setText(chatStatusText(live));
                }
            }
        });
        if (!accepted) {
            stopImageGeneratingTick();
        } else {
            startImageGeneratingTick(group);
        }
    }

    /** Persists and renders the finished generation in the current chat transcript. */
    private void addGeneratedImage(Group group, String path) {
        CharacterProfile speakerProfile = chooseSpeaker(group, "");
        String speaker = speakerProfile != null
                ? speakerProfile.name
                : L10n.s(L10n.s("Anlatıcı"));
        Message message = new Message(speaker, "", false, System.currentTimeMillis());
        message.image = path;
        store.addMessage(group.id, message);
        if (group.id.equals(visibleChatId)) {
            if (chatEmptyState != null && chatEmptyState.getParent() == content) {
                content.removeView(chatEmptyState);
                chatEmptyState = null;
            }
            addStoredMessage(content, message);
            scrollChatToBottom(true);
            if (chatStatusView != null) chatStatusView.setText(chatStatusText(group));
        }
    }

    /** Live step counter ("12/25") while the image engine works, without chat rebuilds. */
    private void startImageGeneratingTick(Group group) {
        stopImageGeneratingTick();
        final android.os.Handler handler = new android.os.Handler();
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (!LocalImageEngine.INSTANCE.isGenerating()) return;
                if (group.id.equals(visibleChatId) && chatStatusView != null) {
                    String step = LocalImageEngine.INSTANCE.statusText();
                    String progress = step == null ? "" : "  " + step;
                    chatStatusView.setText(L10n.s(L10n.s("Görsel üretiliyor · model bu cihazda çalışıyor")) + progress);
                }
                handler.postDelayed(this, 400);
            }
        };
        imageGeneratingTick = tick;
        handler.postDelayed(tick, 400);
        imageGeneratingTickTickHandler = handler;
    }

    private void stopImageGeneratingTick() {
        if (imageGeneratingTickTickHandler != null) {
            imageGeneratingTickTickHandler.removeCallbacksAndMessages(null);
            imageGeneratingTick = null;
            imageGeneratingTickTickHandler = null;
        }
    }

    private void sendMessage(Group group, EditText input) {
        String text = input.getText().toString().trim();
        boolean attachedHere = pendingComposerImage != null && group.id.equals(pendingComposerImageChatId);
        if ((text.isEmpty() && !attachedHere) || generatingChatId != null) return;
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s(L10n.s("Model yükleniyor; bitmesini bekle.")), Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) {
            Toast.makeText(this, L10n.s(L10n.s("Motor testi sürüyor; bitmesini bekle.")), Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalImageEngine.INSTANCE.isGenerating() || LocalImageEngine.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s(L10n.s("Görsel motoruyla bir işlem sürüyor; bitmesini bekle.")), Toast.LENGTH_SHORT).show();
            return;
        }
        forgetAutoAttempts(group.id);
        // Comfort tools (v0.19.22): a new user turn invalidates both one-shot tools —
        // a stale steer must not leak into an unrelated reply, and the pre-regenerate
        // tail must never be restored over newer messages.
        pendingSteer.remove(group.id);
        discardRegenStash(group.id);
        File photo = null;
        if (pendingComposerImage != null && group.id.equals(pendingComposerImageChatId)) {
            photo = pendingComposerImage;
        }
        boolean withImage = photo != null;
        if (withImage) removePendingComposerChip();
        Message sent = new Message("Sen", text, true);
        if (withImage && photo != null) sent.image = photo.getPath();
        store.addMessage(group.id, sent);
        if (chatEmptyState != null && chatEmptyState.getParent() == content) {
            content.removeView(chatEmptyState);
            chatEmptyState = null;
        }
        addStoredMessage(content, sent);
        if (withImage) {
            // Honesty guard: the bundled text engine cannot see images. Say so once
            // instead of letting the user expect a visual description later.
            Toast.makeText(this, L10n.s(L10n.s("Motor fotoğrafları göremez; görsel yalnızca sohbet kaydında saklanır.")), Toast.LENGTH_LONG).show();
        }
        input.setText("");
        chatDrafts.remove(group.id);
        // Focus-first composer (GUI review item 4): no full rebuild after send — the
        // photo chip leaves in place and the keyboard/focus survives.
        removePendingComposerChip();
        reopenKeyboard(input);
        scrollChatToBottom(true);

        if (text.isEmpty()) {
            // Photo-only message: the engine cannot see images, so no AI turn follows.
            return;
        }
        if (!modelCanRespond()) {
            new AlertDialog.Builder(this)
                    .setTitle(L10n.s(L10n.s("Önce model gerekli")))
                    .setMessage(L10n.s(L10n.s("Yanıt üretmek için AI ayarlarından bir GGUF modeli seçip yükle. Mesajın kaydedildi; model hazır olunca yanıtlanacak.")))
                    .setPositiveButton(L10n.s(L10n.s("AI ayarları")), (dialog, which) -> openModels(() -> openChat(group.id)))
                    .setNegativeButton(L10n.s(L10n.s("Tamam")), null).show();
            return;
        }
        CharacterProfile speaker = chooseSpeaker(group, text);
        if (speaker != null) beginGeneration(group, speaker);
    }

    private void continueOrRetry(Group group) {
        continueOrRetry(group, null);
    }

    /** @param steer one-shot steering note (regenerate-with-note); dropped whenever
     *  this retry aborts, so it can never leak into a later unrelated turn. */
    private void continueOrRetry(Group group, String steer) {
        if (generatingChatId != null) return;
        if (steer != null) {
            // Consume-or-drop: only the live path below re-applies the note.
            pendingSteer.remove(group.id);
        }
        if (LocalImageEngine.INSTANCE.isGenerating() || LocalImageEngine.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s(L10n.s("Görsel motoruyla bir işlem sürüyor; bitmesini bekle.")), Toast.LENGTH_SHORT).show();
            return;
        }
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s(L10n.s("Model yükleniyor; bitmesini bekle.")), Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) {
            Toast.makeText(this, L10n.s(L10n.s("Motor testi sürüyor; bitmesini bekle.")), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!modelCanRespond()) {
            openModels(() -> openChat(group.id));
            return;
        }
        List<Message> history = store.messages(group.id);
        if (history.isEmpty()) return;
        Message last = history.get(history.size() - 1);
        CharacterProfile speaker = chooseSpeaker(group, last.user ? last.text : null);
        if (speaker != null) {
            if (steer != null && !steer.trim().isEmpty()) {
                pendingSteer.put(group.id, clip(steer.trim(), 300));
            }
            beginGeneration(group, speaker);
        }
    }

    private void maybeAutoGenerate(Group group) {
        if (generatingChatId != null) return;
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading() || LocalAiRuntime.INSTANCE.isBenchmarking()) return;
        if (!modelCanRespond()) return;
        if (LocalImageEngine.INSTANCE.isGenerating() || LocalImageEngine.INSTANCE.isModelLoading()) return;
        List<Message> history = store.messages(group.id);
        if (history.isEmpty() || !history.get(history.size() - 1).user) return;
        String key = group.id + ":" + history.size();
        if (autoAttempted.contains(key)) return;
        CharacterProfile speaker = chooseSpeaker(group, history.get(history.size() - 1).text);
        if (speaker == null) return;
        content.post(() -> {
            if (group.id.equals(visibleChatId) && generatingChatId == null && modelCanRespond()) {
                beginGeneration(group, speaker);
            }
        });
    }

    private void forgetAutoAttempts(String chatId) {
        autoAttempted.removeIf(key -> key.startsWith(chatId + ":"));
        chainCounts.remove(chatId);
    }

    /** Auto-continues the RP by letting the next character respond (max chars from Gelişmiş AI). */
    private void maybeBeginChain(Group group) {
        int limit = prefs().getInt("auto_chain", 0);
        if (limit <= 0) return;
        int depth = chainCounts.getOrDefault(group.id, 0);
        if (depth >= limit) return;
        if (generatingChatId != null || !group.id.equals(visibleChatId)) return;
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading() ||
                LocalAiRuntime.INSTANCE.isBenchmarking() || !modelCanRespond()) return;
        content.postDelayed(() -> {
            if (!uiAlive() || !group.id.equals(visibleChatId) || generatingChatId != null) return;
            if (!modelCanRespond() || modelLoading || LocalAiRuntime.INSTANCE.isModelLoading() ||
                    LocalAiRuntime.INSTANCE.isBenchmarking()) return;
            Group live = store.group(group.id);
            if (live == null) return;
            CharacterProfile next = chooseSpeaker(live, null);
            if (next == null) return;
            chainCounts.put(group.id, depth + 1);
            beginGeneration(live, next);
        }, 700);
    }

    private void beginGeneration(Group group, CharacterProfile speaker) {
        if (generatingChatId != null || modelLoading || LocalAiRuntime.INSTANCE.isModelLoading() ||
                LocalAiRuntime.INSTANCE.isBenchmarking()) return;
        List<Message> history = store.messages(group.id);
        Message last = history.isEmpty() ? null : history.get(history.size() - 1);
        boolean continuation = last != null && !last.user;
        if (last != null && last.user) autoAttempted.add(group.id + ":" + history.size());
        clearManualSpeaker(group.id);
        chatMetrics.remove(group.id);
        generatingChatId = group.id;
        generatingSpeaker = speaker.name;
        final long thisGeneration = ++generationSequence;
        final long startedAt = System.currentTimeMillis();
        updateChatControls(group);

        if (chatEmptyState != null && chatEmptyState.getParent() == content) {
            content.removeView(chatEmptyState);
            chatEmptyState = null;
        }
        final TextView initialView = group.id.equals(visibleChatId) && content != null
                ? addMessage(content, speaker.name, "…", false, startedAt) : null;
        generationViews.put(group.id, initialView);
        scrollChatToBottom(true);

        List<OpenAiCompat.Turn> providerTurns = null;
        int maxTokens = chatTokenBudget(group.id);
        // Comfort tool (v0.19.22): consume the one-shot steering note here so only
        // this turn's prompt carries it (chain follow-ups rebuild without it).
        String steer = pendingSteer.remove(group.id);
        if (providerReady()) {
            // Opt-in online engine (DeepSeek item 14/B): the scene identity ships as one
            // system prompt, history as real chat turns; engine context bookkeeping (the
            // llama.cpp shared-context budget) does not apply here.
            providerTurns = buildProviderTurns(group, speaker, continuation, steer);
            providerClient = null;
        } else {
            String prompt = buildRoleplayPrompt(group, speaker, continuation, steer);
            boolean freshContext = !LocalAiRuntime.INSTANCE.hasConversation(group.id);
            int contextBudget = LocalAiRuntime.INSTANCE.contextCharBudget();
            long contextUsed = freshContext ? 0L : Math.max(0L, contextChars.getOrDefault(group.id, 0L));
            if (!freshContext && contextUsed + prompt.length() > contextBudget) {
                // Keep the model's native context bounded; rebuild from stored recent history.
                LocalAiRuntime.INSTANCE.resetConversation(group.id);
                contextUsed = 0L;
                prompt = buildRoleplayPrompt(group, speaker, continuation, steer);
            }
            contextChars.put(group.id, contextUsed + prompt.length());
            LocalAiRuntime.INSTANCE.generate(group.id, prompt, maxTokens, pipe(group, speaker, startedAt, thisGeneration));
        }
        if (providerTurns != null) {
            OpenAiCompat client = new OpenAiCompat(providerEndpoint(),
                    ApiKeyStore.INSTANCE.load(this), providerModel());
            final GenerationBackend.Listener real = pipe(group, speaker, startedAt, thisGeneration);
            // Provider callbacks arrive on the client's reader thread (review finding);
            // every UI touch is forwarded to the main thread like LocalAiRuntime does.
            client.stream(providerTurns, maxTokens, new GenerationBackend.Listener() {
                @Override public void onToken(String token) {
                    runOnUiThread(() -> real.onToken(token));
                }
                @Override public void onComplete() {
                    runOnUiThread(real::onComplete);
                }
                @Override public void onStopped() {
                    runOnUiThread(real::onStopped);
                }
                @Override public void onError(String message) {
                    runOnUiThread(() -> real.onError(message));
                }
            });
            providerClient = client;
        }
    }

    /**
     * Shared turn plumbing for both engines (DeepSeek item 14/B): the same bookkeeping —
     * balloon rendering, throttled draft saves, message persistence, chain continuation,
     * error dialogs — used to live as an anonymous listener inside beginGeneration; the
     * online provider reuses it verbatim.
     */
    private GenerationPipe pipe(Group group, CharacterProfile speaker, long startedAt, long thisGeneration) {
        return new GenerationPipe(group, speaker, startedAt, thisGeneration);
    }

    private final class GenerationPipe implements LocalAiRuntime.GenerationListener, GenerationBackend.Listener {
        private final Group group;
        private final CharacterProfile speaker;
        private final long startedAt;
        private final long thisGeneration;
        private final StringBuilder response = new StringBuilder();
        /** Last draft-save heartbeat (throttled ≥1.5 s; 0 = save on first token). */
        private long lastDraftSave;
        /** The terminal callback ran already (double-finish guard). */
        private boolean finishedGeneration;
        /** Draft-render throttle (~11 fps); see scheduleDraftRender. */
        private long lastRenderAt;
        private boolean renderScheduled;
        /** The queued coalesced render, cancelable on teardown. */
        private Runnable pendingRender;

        GenerationPipe(Group group, CharacterProfile speaker, long startedAt, long thisGeneration) {
            this.group = group;
            this.speaker = speaker;
            this.startedAt = startedAt;
            this.thisGeneration = thisGeneration;
        }

        private void finish(boolean stopped, String error) {
                // Terminal callbacks must run once: the client terminates on one path, but
                // a late stop-race must never re-save or re-render the turn (review finding).
                if (finishedGeneration) return;
                finishedGeneration = true;
                if (pendingRender != null) content.removeCallbacks(pendingRender); // no queued residue
                String finalText = cleanResponse(response.toString(), speaker.name);
                TextView displayedResponse = generationViews.remove(group.id);
                clearGenerationDraft(group.id);
                boolean chatStillExists = store.group(group.id) != null;
                if (finalText.isEmpty()) {
                    if (balloonInContent(displayedResponse)) {
                        content.removeView((View) displayedResponse.getParent());
                    }
                } else if (chatStillExists) {
                    store.addMessage(group.id, new Message(speaker.name, finalText, false, startedAt));
                    if (group.id.equals(visibleChatId) && content != null) {
                        if (!balloonInContent(displayedResponse)) {
                            displayedResponse = addMessage(content, speaker.name, finalText, false, startedAt);
                        } else {
                            renderMessage(displayedResponse, speaker.name, finalText, startedAt);
                        }
                    }
                }
                if (thisGeneration != generationSequence) return;
                generatingChatId = null;
                generatingSpeaker = null;
                providerClient = null;
                Group visible = visibleChatId == null ? null : store.group(visibleChatId);
                if (visible != null) updateChatControls(visible);
                if (group.id.equals(visibleChatId) && chatStatusView != null) {
                    if (error != null) chatStatusView.setText(finalText.isEmpty() ? L10n.s(L10n.s("Yanıt üretilemedi")) : L10n.s(L10n.s("Yanıt yarıda kesildi")));
                    else if (finalText.isEmpty()) chatStatusView.setText(L10n.s(L10n.s("Model boş yanıt döndürdü")));
                    else if (stopped) chatStatusView.setText(L10n.s(L10n.s("Yanıt durduruldu.")));
                }
                if (error != null && finalText.isEmpty() && group.id.equals(visibleChatId) && uiAlive()) {
                    AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this)
                            .setTitle(L10n.s(L10n.s("Yanıt üretilemedi")))
                            .setMessage(error);
                    if (modelCanRespond()) {
                        dialog.setPositiveButton(L10n.s("Yeniden dene"), (d, w) -> continueOrRetry(group))
                                .setNegativeButton(L10n.s(L10n.s("Kapat")), null);
                    } else {
                        dialog.setPositiveButton(L10n.s(L10n.s("AI ayarları")), (d, w) -> openModels(() -> openChat(group.id)))
                                .setNegativeButton(L10n.s(L10n.s("Kapat")), null);
                    }
                    AlertDialog errorDialog = dialog.create();
                    tintDialog(errorDialog);
                    errorDialog.show();
                }
                if (!stopped && error == null && !finalText.isEmpty()) {
                    maybeBeginChain(group);
                }
            }

            @Override public void onStatus(String message) {
                if (group.id.equals(visibleChatId) && chatStatusView != null) {
                    chatStatusView.setText(speaker.name + L10n.s(" · ") + message);
                }
            }

            @Override public void onToken(String token) {
                if (finishedGeneration) return;
                response.append(token);
                contextChars.put(group.id, contextChars.getOrDefault(group.id, 0L) + token.length());
                // Draft persistence (task G0 crash case + G11), rate-limited for B1: the full
                // text is re-serialized per save, so honest per-token writes would make the
                // token path O(n²) and busy the main thread on slower phones. Save at most
                // every 1.5 s (first token always); stop/finish paths clear the key, so a
                // crash after stopping loses at most the last 1.5 s of stream.
                long now = System.currentTimeMillis();
                if (lastDraftSave == 0L || now - lastDraftSave >= 1500) {
                    lastDraftSave = now;
                    saveGenerationDraft(group.id, speaker.name, response.toString());
                }
                scheduleDraftRender();
            }

            /**
             * Draft rendering used to run per token (scan + span rebuild + scroll per
             * token): on a fast stream this flashed the balloon and crowded the main
             * thread. Coalesced to ~11 fps instead — a frame-lean refresh (chy bug report:
             * streaming titremesi).
             */
            private void scheduleDraftRender() {
                long now = System.currentTimeMillis();
                if (now - lastRenderAt >= 90) {
                    lastRenderAt = now;
                    renderDraft();
                    return;
                }
                if (renderScheduled) return;
                renderScheduled = true;
                pendingRender = () -> {
                    renderScheduled = false;
                    pendingRender = null;
                    if (finishedGeneration) return;
                    lastRenderAt = System.currentTimeMillis();
                    renderDraft();
                };
                content.postDelayed(pendingRender, 60);
            }

            private void renderDraft() {
                if (!group.id.equals(visibleChatId) || content == null) return;
                // Streamed interim text uses the SAME cleanup as the final message, so the
                // echoed "Name:" prefix and engine token residue never become visible.
                String streamed = cleanResponse(response.toString(), speaker.name);
                TextView draft = generationViews.get(group.id);
                if (!balloonInContent(draft)) {
                    draft = addMessage(content, speaker.name, streamed, false, startedAt);
                    generationViews.put(group.id, draft);
                } else {
                    renderMessage(draft, speaker.name, streamed, startedAt);
                }
                if (autoScroll) scrollChatToBottom(false);
            }

            @Override public void onComplete() { finish(false, null); }
            @Override public void onStopped() { finish(true, null); }
            @Override public void onError(String message) { finish(true, message); }

            @Override public void onMetrics(long firstTokenMs, long totalMs, int tokenCount) {
                if (finishedGeneration || tokenCount <= 0 || totalMs <= 0) return;
                double seconds = totalMs / 1000.0;
                String summary = String.format(Locale.getDefault(),
                        "%.1f sn · %d token · %.1f tok/s", seconds, tokenCount, tokenCount / seconds);
                chatMetrics.put(group.id, summary);
            }
        }

    private void updateChatControls(Group group) {
        if (!group.id.equals(visibleChatId)) return;
        boolean loading = modelLoading || LocalAiRuntime.INSTANCE.isModelLoading();
        boolean benchmarking = LocalAiRuntime.INSTANCE.isBenchmarking();
        boolean generating = generatingChatId != null;
        boolean busy = generating || loading || benchmarking;
        if (chatActionButton != null) {
            chatActionButton.setEnabled(generating || !(loading || benchmarking));
            chatActionButton.setAlpha((loading || benchmarking) && !generating ? 0.55f : 1f);
            // Circular send/stop button: filled accent when idle, soft danger while generating.
            if (generating) {
                chatActionButton.setText("■");
                chatActionButton.setTextSize(15);
                chatActionButton.setTextColor(Design.danger());
                chatActionButton.setBackground(rounded(Design.dangerSoft(), Design.dangerStroke(), 30));
            } else {
                chatActionButton.setText("➤");
                chatActionButton.setTextSize(17);
                chatActionButton.setTextColor(Design.onAccent());
                chatActionButton.setBackground(rounded(Design.accent(), Design.accent(), 30));
            }
        }
        if (chatSpeakerView != null) {
            CharacterProfile manual = manualSpeaker(group);
            chatSpeakerView.setText(L10n.s(L10n.s("Sıra: ")) + (manual == null ? L10n.s("Otomatik") : manual.name) + "  ▾");
        }
        if (chatStatusView != null) chatStatusView.setText(chatStatusText(group));
        if (chatModelHint != null) chatModelHint.setVisibility(modelCanRespond() ? View.GONE : View.VISIBLE);
        if (chatContinueButton == null) return;

        List<Message> history = store.messages(group.id);
        Message last = history.isEmpty() ? null : history.get(history.size() - 1);
        if (busy || last == null || !modelCanRespond()) {
            chatContinueButton.setVisibility(View.GONE);
            return;
        }
        CharacterProfile speaker = chooseSpeaker(group, last.user ? last.text : null);
        if (speaker == null) {
            chatContinueButton.setVisibility(View.GONE);
            return;
        }
        chatContinueButton.setVisibility(View.VISIBLE);
        chatContinueButton.setText(last.user
                ? L10n.s("↻  ") + speaker.name + L10n.s(L10n.s(" yanıtlasın"))
                : L10n.s(L10n.s("▶  Sıradaki: ")) + speaker.name + L10n.s(L10n.s(" konuşsun")));
    }

    private void showSpeakerPicker(Group group) {
        List<CharacterProfile> characters = new ArrayList<>();
        for (String id : group.characterIds) {
            CharacterProfile character = store.character(id);
            if (character != null) characters.add(character);
        }
        String[] labels = new String[characters.size() + 1];
        labels[0] = L10n.s(L10n.s("Otomatik (sırayla)"));
        for (int i = 0; i < characters.size(); i++) labels[i + 1] = characters.get(i).name;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(L10n.s(L10n.s("Sıradaki konuşmacı")))
                .setItems(labels, (d, which) -> {
                    if (which == 0) clearManualSpeaker(group.id);
                    else setManualSpeaker(group.id, characters.get(which - 1).id);
                    updateChatControls(group);
                })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).create();
        tintDialog(dialog);
        dialog.show();
    }

    private void showChatMenu(Group group) {
        String[] items = {L10n.s(L10n.s("RP ayarları")), L10n.s(L10n.s("AI ayarları")), L10n.s("Sohbette ara"),
                L10n.s(L10n.s("Üretim profili")), L10n.s(L10n.s("Dünya kitabı")), L10n.s(L10n.s("Sohbet geçmişini temizle")), L10n.s(L10n.s("Sohbet listesine dön"))};
        AlertDialog dialog = new AlertDialog.Builder(this).setItems(items, (d, which) -> {
            if (which == 0) showGroupEditor(group, () -> openChat(group.id));
            else if (which == 1) openModels(() -> openChat(group.id));
            else if (which == 2) showChatSearch(group);
            else if (which == 3) showChatProfile(group);
            else if (which == 4) showLorebook(group);
            else if (which == 5) clearChat(group);
            else showChats();
        }).create();
        tintDialog(dialog);
        dialog.show();
    }

    /**
     * World book page (DeepSeek item 12): per-conversation keyword-triggered facts.
     * Entries are user-authored; with no matching key they cost nothing in the prompt.
     */
    private void showLorebook(Group group) {
        backAction = () -> openChat(group.id);
        setPage(L10n.s("Dünya kitabı"), group.name, "chat");
        addInfoCard(L10n.s("Nasıl çalışır?"),
                L10n.s(L10n.s("Her kayıt anahtar kelime(ler) ve bir olgudur. Son 3 mesajda bir anahtar geçerse olgu prompta yazılır; "))
                        + L10n.s(L10n.s("blok en fazla 900 karakter tutar, anahtar hiç geçmezse yer kaplamaz.")), "Lore");
        List<Lorebook.Entry> entries = store.lore(group.id);
        if (entries.isEmpty()) {
            addEmpty(L10n.s("Henüz bilgi yok"),
                    L10n.s("Dünyanın kalıcı bilgileri: yerler, kurallar, geçmiş olaylar, karakterler."),
                    L10n.s("İlk bilgiyi ekle"), v -> editLoreEntry(group, -1));
        } else {
            addPrimary("＋  Yeni bilgi", v -> editLoreEntry(group, -1));
            for (int i = 0; i < entries.size(); i++) {
                Lorebook.Entry entry = entries.get(i);
                int index = i;
                LinearLayout card = makeCard();
                card.setOnClickListener(v -> editLoreEntry(group, index));
                TextView keys = label(entry.keys, 15, Design.accent());
                keys.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
                card.addView(keys);
                TextView fact = label(brief(entry.text), 13, Design.text());
                fact.setPadding(0, dp(3), 0, 0);
                card.addView(fact);
                addCardToPage(card);
            }
            addDangerButton(L10n.s("Dünya kitabını temizle"), v -> confirm(
                    group.name + L10n.s(L10n.s(" dünya kitabı girdilerini silinsin mi?")), L10n.s(L10n.s("Sil")), () -> {
                        store.saveLore(group.id, new ArrayList<>());
                        showLorebook(group);
                    }));
        }
    }

    /** One lore entry editor; index < 0 creates a new record. */
    private void editLoreEntry(Group group, int index) {
        List<Lorebook.Entry> entries = store.lore(group.id);
        Lorebook.Entry editing = index >= 0 && index < entries.size() ? entries.get(index) : null;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), 0, dp(22), 0);
        EditText keysField = dialogField(L10n.s(L10n.s("Anahtar kelimeler (virgülle ayır)")));
        if (editing != null) keysField.setText(editing.keys);
        form.addView(keysField);
        EditText textField = dialogField(L10n.s("Olgu (modelin bilmesi gereken bilgi)"));
        if (editing != null) textField.setText(editing.text);
        textField.setMinLines(3);
        form.addView(textField);
        new AlertDialog.Builder(this)
                .setTitle(editing != null ? L10n.s(L10n.s("Bilgiyi düzenle")) : L10n.s(L10n.s("Yeni bilgi")))
                .setView(form)
                .setPositiveButton(L10n.s(L10n.s("Kaydet")), (d, w) -> {
                    Lorebook.Entry entry = new Lorebook.Entry(
                            keysField.getText().toString(), textField.getText().toString());
                    if (entry.keys.isEmpty() || entry.text.isEmpty()) {
                        Toast.makeText(this, L10n.s(L10n.s("Anahtar kelime ve olgu alanları dolu olmalı.")), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (editing != null) entries.set(index, entry);
                    else entries.add(entry);
                    if (!store.saveLore(group.id, entries)) {
                        Toast.makeText(this, L10n.s(L10n.s("Kaydedilemedi: dünya kitabı verisi okunamıyor.")), Toast.LENGTH_LONG).show();
                        return;
                    }
                    showLorebook(group);
                })
                .setNeutralButton(editing != null ? L10n.s(L10n.s("Sil")) : null, (d, w) -> {
                    entries.remove(index);
                    if (!store.saveLore(group.id, entries)) {
                        Toast.makeText(this, L10n.s(L10n.s("Kaydedilemedi: dünya kitabı verisi okunamıyor.")), Toast.LENGTH_LONG).show();
                        return;
                    }
                    showLorebook(group);
                })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null)
                .show();
    }

    /**
     * Per-chat generation profile (DeepSeek item 7): token budget, history depth and
     * scene theme may override the global settings for this conversation only.
     * Anything left on "follow" keeps using the relevant global preference.
     */
    private void showChatProfile(Group group) {
        backAction = () -> openChat(group.id);
        setPage(L10n.s("Üretim profili"), group.name, "chat");
        SharedPreferences prefs = prefs();
        String id = group.id;
        addInfoCard("Sohbet profili", L10n.s(L10n.s("Bu sayfadaki ayarlar yalnızca bu sohbet için geçerlidir. "))
                + L10n.s(L10n.s("Genel ayarı izleyen öğeler Ayarlar'daki değeri kullanır.")), "AI");

        addSection(L10n.s("Yanıt uzunluğu"), null, null);
        boolean tokensOverride = prefs.getInt("profile_tokens_" + id, -1) != -1;
        addBody((tokensOverride ? L10n.s("Bu sohbet: ") : L10n.s(L10n.s("Genel ayarı izler: ")))
                + chatTokenBudget(id) + L10n.s(" token"));
        addSlider(L10n.s("Üst sınır (token)"), chatTokenBudget(id), 64, 1024, 32,
                value -> prefs.edit().putInt("profile_tokens_" + id, value).apply(),
                value -> value + L10n.s(" token"));
        addSecondary(L10n.s("Uzunluğu genel ayara döndür"), v -> {
            prefs.edit().remove("profile_tokens_" + id).apply();
            showChatProfile(group);
        });

        addSection(L10n.s("Geçmiş derinliği"), null, null);
        boolean depthOverride = prefs.getInt("profile_depth_" + id, -1) != -1;
        addBody((depthOverride ? L10n.s("Bu sohbet: ") : L10n.s(L10n.s("Genel ayarı izler: ")))
                + chatHistoryDepth(id) + L10n.s(" mesaj"));
        addSlider(L10n.s("Geçmiş mesaj sayısı"), chatHistoryDepth(id), 2, 14, 1,
                value -> prefs.edit().putInt("profile_depth_" + id, value).apply(),
                value -> value + L10n.s(" mesaj"));
        addSecondary(L10n.s("Derinliği genel ayara döndür"), v -> {
            prefs.edit().remove("profile_depth_" + id).apply();
            showChatProfile(group);
        });

        addSection(L10n.s("Sahne teması"), null, null);
        boolean themeOverride = prefs.getString("profile_theme_" + id, null) != null;
        addBody((themeOverride ? L10n.s("Bu sohbet: ") : L10n.s(L10n.s("Genel ayarı izler: ")))
                + aiThemeName(chatThemeId(id)));
        addSecondary(L10n.s("Bu sohbet için tema seç"), v -> showChatThemePicker(group));
        if (themeOverride) {
            addSecondary(L10n.s("Temayı genel ayara döndür"), v -> {
                if (chatProfileBusy(group)) return;
                prefs.edit().remove("profile_theme_" + id).apply();
                contextChars.remove(id);
                chatMetrics.remove(id);
                LocalAiRuntime.INSTANCE.resetConversation(id);
                showChatProfile(group);
            });
        }

        // v0.19.16: which user identity plays "Sen" in this conversation.
        addSection(L10n.s("Bu sohbetin kişiliği"), null, null);
        Persona effective = store.personaForChat(id);
        String overrideId = store.chatPersonaId(id);
        addBody(overrideId == null || overrideId.isEmpty()
                ? (effective != null
                        ? L10n.s(L10n.s("Genel varsayılanı izler: ")) + (effective.name.isEmpty() ? L10n.s(L10n.s("Adsız kişilik")) : effective.name)
                        : L10n.s(L10n.s("Henüz bir kişilik tanımlı.")))
                : L10n.s("Bu sohbet: ") + (effective != null && !effective.name.isEmpty() ? effective.name : L10n.s(L10n.s("Adsız kişilik"))) + L10n.s(L10n.s(" (kişisel seçim)")));
        addSecondary(L10n.s("Kişilik seç"), v -> showChatPersonaPicker(group));
        if (overrideId != null && !overrideId.isEmpty()) {
            addSecondary(L10n.s("Kişiliği genel varsayılana döndür"), v -> {
                if (chatProfileBusy(group)) return;
                store.setChatPersona(id, "");
                contextChars.remove(id);
                chatMetrics.remove(id);
                LocalAiRuntime.INSTANCE.resetConversation(id);
                Toast.makeText(this, L10n.s(L10n.s("Bu sohbet genel varsayılan kişiliği izliyor.")), Toast.LENGTH_SHORT).show();
                showChatProfile(group);
            });
        }

        addSection(L10n.s("Profili sıfırla"), null, null);
        addDangerButton(L10n.s("Tüm profili genel ayara döndür"), v -> confirm(
                L10n.s(L10n.s("Bu sohbetin üretim profili sıfırlansın mı?")), L10n.s(L10n.s("Sıfırla")), () -> {
                    if (chatProfileBusy(group)) return;
                    prefs.edit().remove("profile_tokens_" + id)
                            .remove("profile_depth_" + id).remove("profile_theme_" + id).apply();
                    contextChars.remove(id);
                    chatMetrics.remove(id);
                    LocalAiRuntime.INSTANCE.resetConversation(id);
                    Toast.makeText(this, L10n.s(L10n.s("Profil genel ayara döndü.")), Toast.LENGTH_SHORT).show();
                    showChatProfile(group);
                }));
    }

    /** v0.19.16: per-chat persona picker; first row follows the global default. */
    private void showChatPersonaPicker(Group group) {
        List<Persona> personas = store.personas();
        if (personas.isEmpty()) {
            Toast.makeText(this, L10n.s(L10n.s("Önce karakterler ekranından bir kullanıcı kişiliği tanımla.")), Toast.LENGTH_LONG).show();
            return;
        }
        String current = store.chatPersonaId(group.id);
        String[] labels = new String[personas.size() + 1];
        labels[0] = L10n.s(L10n.s("Genel varsayılanı izle")) + ((current == null || current.isEmpty()) ? "  ✓" : "");
        for (int i = 0; i < personas.size(); i++) {
            Persona p = personas.get(i);
            labels[i + 1] = (p.name.isEmpty() ? L10n.s(L10n.s("Adsız kişilik")) : p.name)
                    + (store.isDefaultPersona(p.id) ? L10n.s(L10n.s("  ·  varsayılan")) : "")
                    + (p.id.equals(current) ? "  ✓" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle(L10n.s(L10n.s("Bu sohbetin kişiliği")))
                .setItems(labels, (d, which) -> {
                    if (chatProfileBusy(group)) return; // a running turn must finish on its own prompt
                    if (which == 0) {
                        store.setChatPersona(group.id, "");
                        Toast.makeText(this, L10n.s(L10n.s("Bu sohbet genel varsayılan kişiliği izliyor.")), Toast.LENGTH_SHORT).show();
                    } else {
                        Persona chosen = personas.get(which - 1);
                        store.setChatPersona(group.id, chosen.id);
                        Toast.makeText(this, chosen.name.isEmpty()
                                ? L10n.s(L10n.s("Bu sohbet artık adsız kişilik ile oynanıyor."))
                                : L10n.s(L10n.s("Bu sohbet artık ")) + chosen.name + L10n.s(L10n.s(" ile oynanıyor.")), Toast.LENGTH_SHORT).show();
                    }
                    // The engine's shared context holds the previous identity; a persona
                    // switch must not let the model keep roleplaying the old "Sen".
                    contextChars.remove(group.id);
                    chatMetrics.remove(group.id);
                    LocalAiRuntime.INSTANCE.resetConversation(group.id);
                    showChatProfile(group);
                })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null)
                .show();
    }

    /** Theme picker scoped to one chat; first row drops the override and follows the global theme. */
    private void showChatThemePicker(Group group) {
        SharedPreferences prefs = prefs();
        String id = group.id;
        String overrideKey = "profile_theme_" + id;
        boolean following = prefs.getString(overrideKey, null) == null;
        String current = chatThemeId(id);
        String[] labels = new String[AI_THEMES.length + 2];
        labels[0] = L10n.s(L10n.s("Genel ayarı izler")) + " (" + aiThemeName(prefs.getString("ai_theme", DEFAULT_AI_THEME)) + ")"
                + (following ? "  ✓" : "");
        for (int i = 0; i < AI_THEMES.length; i++) {
            labels[i + 1] = L10n.s(AI_THEMES[i][1]) + (!following && AI_THEMES[i][0].equals(current) ? "  ✓" : "");
        }
        labels[AI_THEMES.length + 1] = L10n.s(L10n.s("Özel tema")) + ("custom".equals(current) && !following ? "  ✓" : "");
        new AlertDialog.Builder(this).setTitle(L10n.s(L10n.s("AI sahne teması")))
                .setItems(labels, (dialog, which) -> {
                    if (chatProfileBusy(group)) return;
                    if (which == 0) {
                        if (following) return;
                        prefs.edit().remove(overrideKey).apply();
                    } else if (which == AI_THEMES.length + 1) {
                        if (prefs.getString("ai_theme_custom_instruction", "").isEmpty()) {
                            // Choosing custom with nothing written behaves like an empty tone hint.
                            Toast.makeText(this, L10n.s(L10n.s("Özel temayı Ayarlar → AI teması → Özel tema ile yaz.")), Toast.LENGTH_LONG).show();
                        }
                        prefs.edit().putString(overrideKey, "custom").apply();
                    } else {
                        String selectedId = AI_THEMES[which - 1][0];
                        if (!following && selectedId.equals(current)) return;
                        prefs.edit().putString(overrideKey, selectedId).apply();
                        Toast.makeText(this, AI_THEMES[which - 1][1] + L10n.s(L10n.s(" teması seçildi.")), Toast.LENGTH_SHORT).show();
                    }
                    // A prompt-style change deserves a clean continuation context.
                    contextChars.remove(id);
                    chatMetrics.remove(id);
                    LocalAiRuntime.INSTANCE.resetConversation(id);
                    showChatProfile(group);
                })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).show();
    }

    /**
     * In-chat search (DeepSeek review item): a query folds through the transcript and
     * results list jumps the chat window to the selected message.
     */
    private void showChatSearch(Group group) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), 0, dp(22), 0);
        EditText query = dialogField(L10n.s("Sohbette ara…"));
        query.setSingleLine(true);
        form.addView(query);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(results);
        form.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(340)));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(group.name)
                .setView(form)
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null)
                .create();

        Runnable[] refreshHolder = new Runnable[1];
        refreshHolder[0] = () -> {
            results.removeAllViews();
            String term = query.getText().toString().trim();
            if (term.length() < 2) {
                results.addView(label(L10n.s(L10n.s("En az iki harf yaz; tüm sohbet geçmişinde aranır.")), 13, Design.muted()));
                return;
            }
            String haystack = searchText(term);
            int found = 0;
            List<Message> history = store.messages(group.id);
            for (int i = 0; i < history.size() && found < 40; i++) {
                Message message = history.get(i);
                String body = (message.text == null || message.text.isEmpty()) && message.hasImage()
                        ? L10n.s(L10n.s("[görsel]")) : message.text;
                String rendered = searchText(body + " " + (message.user ? L10n.s("sen") : message.speaker));
                if (!rendered.contains(haystack)) continue;
                found++;
                String who = message.user ? L10n.speaker("Sen") : message.speaker;
                int match = rendered.indexOf(haystack);
                String snippet = message.text == null || message.text.isEmpty() ? body
                        : oneLine(match > 40 ? body.substring(Math.max(0, Math.min(match - 20, body.length()))) : body, 120);
                final int index = i;
                TextView row = label(who + L10n.s("  ·  ") + clock.format(new Date(message.at)) + "\n" + snippet, 14, Design.text());
                row.setPadding(0, dp(10), 0, dp(10));
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    jumpToMessage(group, index);
                });
                results.addView(row, fullWidth());
            }
            if (found == 0) {
                results.addView(label(L10n.s(L10n.s("Eşleşme yok.")), 13, Design.muted()));
            }
        };
        final android.os.Handler searchDebounce = new android.os.Handler(android.os.Looper.getMainLooper());
        query.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            // A per-keystroke full-transcript scan + up to 40 row rebuilds crowded the
            // main thread on long chats; debounced to at most ~4 scans/second.
            @Override public void afterTextChanged(android.text.Editable s) {
                searchDebounce.removeCallbacks(refreshHolder[0]);
                searchDebounce.postDelayed(refreshHolder[0], 250);
            }
        });
        refreshHolder[0].run();
        // Dismissed dialog must not leave a queued search running on a dead form.
        dialog.setOnDismissListener(d -> searchDebounce.removeCallbacks(refreshHolder[0]));
        tintDialog(dialog);
        dialog.show();
    }

    /** Centers the rendered window on a message index and scrolls it into view. */
    private void jumpToMessage(Group group, int index) {
        if (chatScroll == null || group == null || !group.id.equals(visibleChatId)
                || index < 0 || index >= chatHistory.size()) return;
        autoScroll = false;
        if (index < chatRenderStart) {
            chatRenderStart = Math.max(0, index - 4);
            rebuildChatRender(group);
        }
        content.post(() -> scrollChatByIndex(index));
    }

    private void scrollChatByIndex(int index) {
        if (chatScroll == null) return;
        for (int c = 0; c < content.getChildCount(); c++) {
            View child = content.getChildAt(c);
            if (Integer.valueOf(index).equals(child.getTag())) {
                chatScroll.smoothScrollTo(0, Math.max(0, child.getTop() - dp(90)));
                return;
            }
        }
        chatScroll.smoothScrollTo(0, 0);
    }

    private void clearChat(Group group) {
        if (group.id.equals(generatingChatId)) {
            new AlertDialog.Builder(this).setMessage(L10n.s(L10n.s("Yanıt üretilirken sohbet temizlenemez. Önce yanıtı durdur.")))
                    .setPositiveButton(L10n.s(L10n.s("Tamam")), null).show();
            return;
        }
        confirm(L10n.s(L10n.s("Bu sohbetin tüm mesajları silinsin mi?")), L10n.s(L10n.s("Temizle")), () -> {
            deleteMessageImages(group.id);
            store.clearMessages(group.id);
            forgetAutoAttempts(group.id);
            chatMetrics.remove(group.id);
            contextChars.remove(group.id);
            LocalAiRuntime.INSTANCE.resetConversation(group.id);
            showChat(group);
        });
    }

    /** Deletes the image files that a group's messages reference, before its transcript is wiped. */
    private void deleteMessageImages(String groupId) {
        for (Message message : store.messages(groupId)) {
            if (message.hasImage()) deleteMessageImageFile(message.image);
        }
    }

    /**
     * Only deletes paths inside this app's private storage (models/images or files/);
     * restored backups may reference foreign-quality-device paths and must be left alone.
     */
    private void deleteMessageImageFile(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            File file = new File(path);
            if (!file.isFile()) return;
            String canonical = file.getCanonicalPath();
            if (canonical.startsWith(LocalImageEngine.INSTANCE.imagesDirectory(this)
                    .getParentFile().getCanonicalPath())
                    || canonical.startsWith(getFilesDir().getCanonicalPath())) {
                file.delete();
            }
        } catch (java.io.IOException unreadable) {
            // unreadable path: never delete something we cannot verify is ours
        }
    }

    private String chatStatusText(Group group) {
        if (group.id.equals(generatingChatId)) {
            return L10n.speaker(generatingSpeaker == null ? L10n.s("Bir karakter") : generatingSpeaker) + L10n.s(L10n.s("yanıt yazıyor…"));
        }
        if (generatingChatId != null) return L10n.s(L10n.s("Başka bir sohbette AI yanıt üretiyor…"));
        if (LocalAiRuntime.INSTANCE.isModelLoading()) return L10n.s(L10n.s("Model yükleniyor…"));
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) return L10n.s(L10n.s("Motor testi sürüyor…"));
        if (LocalAiRuntime.INSTANCE.isModelReady()) {
            int percent = contextPercent(group);
            String metrics = chatMetrics.get(group.id);
            if (metrics != null) return L10n.s(L10n.s("Son yanıt: ")) + metrics + L10n.s(L10n.s(" · bağlam %")) + percent;
            return L10n.s(L10n.s("Sahne hazır · bağlam %")) + percent + L10n.s(L10n.s(" · çevrimdışı"));
        }
        if (LocalAiRuntime.INSTANCE.loadedModelPath() != null) {
            return L10n.s(L10n.s("Model yeniden hazırlanacak · yanıt biraz sürebilir"));
        }
        return selectedModel == null ? L10n.s(L10n.s("Yanıtlar için bir GGUF modeli seç")) : L10n.s(L10n.s("Model seçildi · yüklemek için AI ayarları"));
    }

    private int contextPercent(Group group) {
        int budget = Math.max(1, LocalAiRuntime.INSTANCE.contextCharBudget());
        long used = Math.max(0L, contextChars.getOrDefault(group.id, 0L));
        return (int) Math.min(100L, (used * 100L) / budget);
    }

    private void showChats() {
        visibleChatId = null;
        backAction = this::showHome;
        setPage("RP Sohbetleri", "Bire bir ve grup hikâyelerin", "chats");
        addHomeActions();
        List<Group> groups = store.groups();
        if (groups.isEmpty()) addEmpty(L10n.s("Burada henüz RP yok"), L10n.s("Bire bir hikâye başlat veya grup kur."), null, null);
        for (Group group : recentChats(groups, Integer.MAX_VALUE)) addGroupCard(group, this::showChats);
    }

    /**
     * "Son sohbetler" reflects actual activity: groups sorted by their last message
     * time (newest first), falling back to creation order when a chat is empty.
     */
    private List<Group> recentChats(List<Group> groups, int limit) {
        List<Group> sorted = new ArrayList<>(groups);
        sorted.sort((a, b) -> Long.compare(lastChatActivity(b), lastChatActivity(a)));
        if (limit < sorted.size()) return new ArrayList<>(sorted.subList(0, limit));
        return sorted;
    }

    private long lastChatActivity(Group group) {
        Message last = store.lastMessage(group.id);
        return last != null ? last.at : 0L;
    }

    private void openModels(Runnable back) {
        visibleChatId = null;
        modelsBackAction = back != null ? back : this::showSettings;
        showModels();
    }

    /** Single Settings hub: language, model, prompt style, appearance, backup. */
    private void showSettings() {
        visibleChatId = null;
        backAction = null;
        setPage(L10n.s("Ayarlar"), L10n.s("Dil · model · yanıt tarzı · görünüm · veri"), "settings");

        addSection(L10n.s("Dil / Language"), null, null);
        addBody(L10n.s("Uygulama arayüzü ile AI rol yapma dilini birlikte değiştirir. English mode: better RP with English-tuned models."));
        addSecondary(L10n.s(L10n.s("Türkçe")) + (prefs().getString("app_language", "tr").equals("tr") ? "  ✓" : ""), v -> switchAppLanguage("tr"));
        addSecondary("English (recommended: better RP)" + (prefs().getString("app_language", "tr").equals("en") ? "  ✓" : ""), v -> switchAppLanguage("en"));

        addSection(L10n.s("Model"), null, null);
        addInfoCard(selectedModelName, modelStatus, "GGUF");
        addSecondary(L10n.s("Modeli yükle ve yönet"), v -> openModels(this::showSettings));
        addSecondary(L10n.s("Model önerilerini gör ve indir"), v -> showModelCatalog());

        addSection(L10n.s(L10n.s("Çevrimiçi sağlayıcı")) + " (Faz 5)", null, null);
        boolean providerOn = prefs().getBoolean("provider_enabled", false);
        addBody(L10n.s(L10n.s("Opsiyonel ve kapalı başlar: açık değilken hiçbir veri gönderilmez, çevrimdışı model varsayılandır. "))
                + L10n.s(L10n.s("Sağlayıcı açıkken yanıtlar https üzerinden gider: sohbetler yalnızca sizin onayınızla dışarı çıkar.")));
        addSecondary(L10n.s(L10n.s("Online AI sağlayıcı")) + (providerOn ? "  ✓" : ""), v -> showProvider());

        addSection(L10n.s("Yanıt tarzı"), null, null);
        String themeId = prefs().getString("ai_theme", DEFAULT_AI_THEME);
        addInfoCard(aiThemeName(themeId), aiThemeDescription(themeId), L10n.s("Sahne atmosferi"));
        addSecondary("Prompt dilleri ve talimatlar", v -> showPromptStyle());
        addSecondary(L10n.s("AI temasını değiştir"), v -> showThemePicker());
        addSecondary(L10n.s("Sahne hafızası ve zincir"), v -> showAiAdvanced());

        addSection(L10n.s("Görünüm"), null, null);
        addSecondary(L10n.s("GUI temasını değiştir"), v -> showAppearance());
        addSecondary(L10n.s("Görsel galerisi"), v -> showGallery(this::showSettings));

        addSection(L10n.s("Veri yedeği"), null, null);
        addInfoCard(L10n.s("Yedekleme"), L10n.s(L10n.s("Karakterler, sohbetler ve geçmişler tek bir .json dosyasına yazılır. "))
                + L10n.s(L10n.s("Dosya tamamen senin kontrolündedir: uygulama hiçbir veriyi otomatik göndermez.")), "Yerel");
        addPrimary(L10n.s("Yedeği dışa aktar"), v -> {
            clearPendingSelection();
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(new Date());
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "localrp-yedek-" + stamp + ".json");
            startActivityForResult(intent, BACKUP_EXPORT);
        });
        addSecondary(L10n.s("Yedeği dışa aktar (medya dahil)"), v -> {
            clearPendingSelection();
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(new Date());
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "localrp-yedek-" + stamp + ".zip");
            startActivityForResult(intent, BACKUP_EXPORT_MEDIA);
        });
        addSecondary(L10n.s("Seçmeli dışa aktar"), v -> showSelectiveExport());
        addSecondary(L10n.s("Yedeği geri yükle"), v -> confirm(
                L10n.s(L10n.s("Geri yükleme aynı kimlikli karakter/sohbetleri günceller, yeni olanları ekler. ")) +
                        L10n.s(L10n.s("Geçmişler yalnızca yedekteki kopya daha uzunsa değiştirilir. Devam edilsin mi?")),
                () -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(intent, BACKUP_IMPORT);
                }));
    }

    private void showModels() {
        backAction = modelsBackAction != null ? modelsBackAction : this::showSettings;
        setPage(L10n.s("Model yönetimi"), L10n.s("GGUF seç, yükle ve belleği izle"), "settings");
        addModelCard();
        boolean loading = modelLoading || LocalAiRuntime.INSTANCE.isModelLoading();
        boolean benchmarking = LocalAiRuntime.INSTANCE.isBenchmarking();
        boolean busy = loading || benchmarking;
        if (busy) {
            modelProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            modelProgress.setIndeterminate(true);
            LinearLayout.LayoutParams progressParams = fullWidth();
            progressParams.setMargins(0, dp(4), 0, dp(12));
            content.addView(modelProgress, progressParams);
        } else {
            modelProgress = null;
        }

        addSection(L10n.s("Çalışma durumu"), null, null);
        addInfoCard(L10n.s("Cihaz içi AI"), L10n.s("GGUF modeli llama.cpp ile bu telefonda çalışır. Modeli yükledikten sonra sohbet internet olmadan da sürer."), L10n.s("Çevrimdışı"));
        addSection(L10n.s("Model dosyası"), null, null);
        modelStatusView = addInfoCard(selectedModelName, modelStatus, "GGUF modeli");
        LocalAiRuntime.ModelInfo info = LocalAiRuntime.INSTANCE.getModelInfo();
        if (info != null) {
            addInfoCard(modelInfoTitle(info), modelInfoText(info), "GGUF bilgisi");
        }
        addPrimary(L10n.s("Telefondan GGUF seç"), v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, MODEL_PICKER);
        }).setEnabled(!busy);
        if (selectedModel != null) {
            boolean ready = LocalAiRuntime.INSTANCE.isModelReady();
            String label = busy ? (benchmarking ? L10n.s("Motor testi sürüyor…") : L10n.s("Model yükleniyor…"))
                    : (ready ? L10n.s("Modeli bellekten kaldır") : L10n.s("Seçili modeli yükle"));
            Button modelButton = addPrimary(label, v -> {
                if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading() ||
                        LocalAiRuntime.INSTANCE.isBenchmarking()) return;
                if (LocalAiRuntime.INSTANCE.isModelReady()) unloadModel(); else loadSelectedModel();
            });
            modelButton.setEnabled(!busy);
        }
        List<File> storedModels = LocalAiRuntime.INSTANCE.storedModels(this);
        if (!busy && !storedModels.isEmpty()) {
            long totalBytes = 0;
            for (File file : storedModels) totalBytes += file.length();
            addBody(L10n.s(L10n.s("Cihazdaki model kopyaları: ")) + storedModels.size() + L10n.s(" dosya · ") + humanSize(totalBytes));
            addSecondary(L10n.s("Model kopyalarını yönet"), v -> showStoredModels());
        }
        addBody(L10n.s("Model dosyası uygulamanın özel alanına kopyalanır; seçtiğin kaynak dosya silinmez."));

        SharedPreferences prefs = prefs();

        addSection(L10n.s("Yanıt biçimi"), null, null);
        addBody(L10n.s("Yanıt uzunluğu"));
        int savedLength = prefs.getInt("max_output_tokens", 320);
        TextView lengthLabel = label(savedLength + L10n.s(" token"), 14, Design.text());
        content.addView(lengthLabel);
        SeekBar length = new SeekBar(this);
        length.setMax(30);
        length.setProgress(Math.max(0, Math.min(30, (savedLength - 64) / 32)));
        length.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int tokens = 64 + progress * 32;
                lengthLabel.setText(tokens + L10n.s(" token"));
                if (fromUser) prefs.edit().putInt("max_output_tokens", tokens).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(length, fullWidth());
        addBody(L10n.s("Düşük değer daha kısa yanıt verir. Bu motor sürümü temperature/top-p ayarlarını dışa açmıyor; yanıt uzunluğu ve sahne temasıyla yönlendirme yapılabilir."));
        addBody(L10n.s("Sohbet değiştirildiğinde model bağlamı sıfırlanır; o sohbetteki ilk yanıt biraz daha uzun sürebilir."));

        addSection(L10n.s("Motor testi"), null, null);
        if (benchmarking) {
            addBody(L10n.s("Cihaz hızı ölçülüyor. Test bitince sonuç burada gösterilecek."));
        } else if (LocalAiRuntime.INSTANCE.isModelReady()) {
            addSecondary(L10n.s("Hız testini başlat"), v -> runBenchmark());
            addBody(L10n.s("Küçük bir prompt işleme ve 16 token üretme testi çalıştırır; cihazın yaklaşık hızını gösterir."));
        } else {
            addBody(L10n.s("Hız testi için önce modeli yükle."));
        }
    }

    /** Switches the shared UI + prompt language and re-renders cleanly. */
    private void switchAppLanguage(String language) {
        String current = prefs().getString("app_language", "tr");
        if (current.equals(language)) { showSettings(); return; }
        prefs().edit().putString("app_language", language).apply();
        L10n.refresh(language);
        contextChars.clear();
        LocalAiRuntime.INSTANCE.resetConversation(null);
        // All strings change: the header must be rebuilt, not refreshed in place.
        resetPageAnchor();
        showSettings();
        Toast.makeText(this, language.equals("en")
                ? L10n.s("Yanıt dili İngilizce olarak ayarlandı — model promptları artık İngilizce.")
                : L10n.s("Dil Türkçe olarak ayarlandı."), Toast.LENGTH_SHORT).show();
    }

    /** Online provider settings (DeepSeek item 14/B): strictly opt-in, https-only. */
    private void showProvider() {
        visibleChatId = null;
        backAction = this::showSettings;
        SharedPreferences prefs = prefs();
        setPage(L10n.s(L10n.s("Çevrimiçi sağlayıcı")), L10n.s(L10n.s("Faz 5 · opsiyonel · çevrimdışı varsayılan kalır")), "settings");
        addInfoCard(L10n.s(L10n.s("Veri dışarı çıkarır")),
                L10n.s(L10n.s("Bu ayar açıkken ve eksiksizken yanıtlar, seçtiğin OpenAI-uyumlu https adresine gider; "))
                        + L10n.s(L10n.s("sohbet içerik cihazdan çıkmış olur. Kapalıyken hiçbir şey gönderilmez.")), L10n.s("Gizlilik"));

        boolean enabled = prefs.getBoolean("provider_enabled", false);
        addPrimary(enabled ? L10n.s(L10n.s("Sağlayıcı açık — kapata geç")) : L10n.s(L10n.s("Sağlayıcıyı etkinleştir")), v -> {
            boolean turningOn = !prefs.getBoolean("provider_enabled", false);
            if (turningOn && !providerConfigured(prefs)) {
                Toast.makeText(this, L10n.s(L10n.s("Önce adres, model adı ve API anahtarını eksiksiz doldur.")), Toast.LENGTH_LONG).show();
                return;
            }
            prefs.edit().putBoolean("provider_enabled", turningOn).apply();
            Toast.makeText(this, turningOn
                    ? L10n.s(L10n.s("Sağlayıcı etkin: yanıtlar artık seçtiğin servisten gelecek."))
                    : L10n.s(L10n.s("Sağlayıcı kapalı: çevrimdışı model varsayana döndü.")), Toast.LENGTH_SHORT).show();
            showProvider();
        });
        addBody(enabled
                ? L10n.s("Durum: ") + (providerReady() ? L10n.s(L10n.s("hazır")) : L10n.s(L10n.s("eksiksiz değil")))
                : L10n.s(L10n.s("Durum: kapalı")));

        addSection(L10n.s(L10n.s("Bağlantı")), null, null);
        addBody(L10n.s(L10n.s("OpenAI uyumlu sohbet tamamlama adresi. Yalnızca https kabul edilir; örnek: https://api.example.com/v1/chat/completions")));
        EditText endpointField = field(L10n.s("https endpoint"), prefs.getString("provider_endpoint", ""));
        endpointField.setSingleLine(true);
        content.addView(endpointField, fullWidth());
        EditText modelField = field(L10n.s(L10n.s("Model adı")), prefs.getString("provider_model", ""));
        modelField.setSingleLine(true);
        content.addView(modelField, fullWidth());
        int turnDepth = prefs.getInt("provider_history_depth", 8);
        addBody(L10n.s(L10n.s("Prompta kaç mesajlık sohbet geçmişi taşınır (2–20): telefonla ayardan değil, bu sayıdan düzenlenir.")));
        EditText depthField = field(L10n.s(L10n.s("Geçmiş mesaj sayısı")), String.valueOf(turnDepth));
        depthField.setSingleLine(true);
        content.addView(depthField, fullWidth());
        addPrimary(L10n.s(L10n.s("Bağlantı bilgilerini kaydet")), v -> {
            String endpoint = endpointField.getText().toString().trim();
            String model = modelField.getText().toString().trim();
            int depth;
            try {
                depth = Integer.parseInt(depthField.getText().toString().trim());
            } catch (NumberFormatException bad) {
                Toast.makeText(this, L10n.s(L10n.s("Geçmiş mesaj sayısı bir sayı olmalı.")), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!OpenAiCompat.allowsEndpoint(endpoint)) {
                Toast.makeText(this, L10n.s(L10n.s("Adres https ile başlamalı; API anahtarı düz bağlantıda sızabilir.")), Toast.LENGTH_LONG).show();
                return;
            }
            if (model.isEmpty()) {
                Toast.makeText(this, L10n.s(L10n.s("Model adı boş olamaz.")), Toast.LENGTH_SHORT).show();
                return;
            }
            if (depth < 2 || depth > 20) {
                Toast.makeText(this, L10n.s(L10n.s("Geçmiş mesaj sayısı 2 ile 20 arasında olmalı.")), Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("provider_endpoint", endpoint)
                    .putString("provider_model", model)
                    .putInt("provider_history_depth", depth).apply();
            Toast.makeText(this, L10n.s("Kaydedildi."), Toast.LENGTH_SHORT).show();
            showProvider();
        });

        addSection(L10n.s(L10n.s("API anahtarı")), null, null);
        boolean keySet = ApiKeyStore.INSTANCE.isConfigured(this);
        addBody(keySet
                ? L10n.s(L10n.s("Anahtar kayıtlı: cihazdaki anahtarla kriptolu tutulur (AndroidKeyStore AES/GCM); düz metin diskte durmaz."))
                : L10n.s(L10n.s("Anahtar yok. Anahtar yalnızca bu cihazda kriptolu saklanır ve hiçbir yedekte taşınmaz.")));
        final EditText keyField = field(keySet ? L10n.s(L10n.s("Yeni anahtar (kaydetmek için doldur)")) : L10n.s(L10n.s("API anahtarı")), "");
        keyField.setSingleLine(true);
        keyField.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(keyField, fullWidth());
        addSecondary(L10n.s(L10n.s("Anahtarı kaydet")), v -> {
            String plain = keyField.getText().toString().trim();
            if (plain.isEmpty()) {
                Toast.makeText(this, L10n.s(L10n.s("Anahtar alanı boş olamaz.")), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!ApiKeyStore.INSTANCE.save(this, plain)) {
                Toast.makeText(this, L10n.s(L10n.s("Anahtar kaydedilemedi: cihaz güvenlik deposu kullanılamıyor.")), Toast.LENGTH_LONG).show();
                return;
            }
            keyField.setText("");
            Toast.makeText(this, L10n.s("Anahtar kaydedildi."), Toast.LENGTH_SHORT).show();
            showProvider();
        });
        if (keySet) {
            addDangerButton(L10n.s(L10n.s("Anahtarı sil")), v -> confirm(L10n.s(L10n.s("Kayıtlı API anahtarı silinsin mi? Uygulama anahtarı yeniden soracak.")), L10n.s(L10n.s("Sil")), () -> {
                ApiKeyStore.INSTANCE.erase(this);
                Toast.makeText(this, L10n.s("Anahtar silindi."), Toast.LENGTH_SHORT).show();
                if (prefs().getBoolean("provider_enabled", false)) {
                    prefs().edit().putBoolean("provider_enabled", false).apply(); // incomplete config cannot stay "on"
                }
                showProvider();
            }));
        }

        addSection(L10n.s(L10n.s("Bağlantı testi")), null, null);
        addBody(L10n.s(L10n.s("Kısa bir deneme isteği gönderir (birkaç token); bu, açık bir kullanıcı eylemidir.")));
        addPrimary(L10n.s(L10n.s("Bağlantıyı test et")), v -> testProviderConnection(
                endpointField.getText().toString().trim(),
                modelField.getText().toString().trim()));
    }

    /** Endpoint + model + key all present (used to guard turning the provider on). */
    private boolean providerConfigured(SharedPreferences prefs) {
        return ApiKeyStore.INSTANCE.isConfigured(this)
                && OpenAiCompat.allowsEndpoint(prefs.getString("provider_endpoint", "").trim())
                && !prefs.getString("provider_model", "").trim().isEmpty();
    }

    /** One-shot opt-in ping: a few tokens from the configured endpoint; no auto-retry. */
    private void testProviderConnection(String endpoint, String model) {
        if (!OpenAiCompat.allowsEndpoint(endpoint) || model.isEmpty()) {
            Toast.makeText(this, L10n.s(L10n.s("Önce geçerli bir https adresi ve model adı yaz.")), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ApiKeyStore.INSTANCE.isConfigured(this)) {
            Toast.makeText(this, L10n.s(L10n.s("Önce API anahtarını gir.")), Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, L10n.s("Test ediliyor…"), Toast.LENGTH_SHORT).show();
        OpenAiCompat client = new OpenAiCompat(endpoint, ApiKeyStore.INSTANCE.load(this), model);
        client.stream(java.util.List.of(new OpenAiCompat.Turn("user", L10n.english ? "Say 'ok'." : "Sadece 'ok' yaz.")), 8,
                new GenerationBackend.Listener() {
                    private boolean reported;

                    private void ok() {
                        if (reported) return;
                        reported = true;
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                L10n.s(L10n.s("Test başarılı: bağlantı ve anahtar çalışıyor.")), Toast.LENGTH_LONG).show());
                    }

                    @Override public void onToken(String token) {
                        ok();
                        client.stop(); // one-shot ping: cut the stream after the first token
                    }
                    @Override public void onComplete() { ok(); }
                    @Override public void onStopped() { ok(); }
                    @Override public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                L10n.s(L10n.s("Test başarısız: ")) + message, Toast.LENGTH_LONG).show());
                    }
                });
    }

    private void showAppearance() {
        visibleChatId = null;
        backAction = this::showSettings;
        setPage(L10n.s("Görünüm"), L10n.s("Uygulamanın temasını seç"), "settings");
        addInfoCard("Tema sistemi", L10n.s("Her tema kartları, sohbet balonlarını, gezinmeyi ve sistem çubuklarını tek seferde boyar."), "GUI");
        addBody(L10n.s("Seçim anında uygulanır; model bağlamı etkilenmez."));
        for (AppTheme candidate : AppTheme.ALL) {
            final boolean selected = Design.theme.id.equals(candidate.id);
            LinearLayout card = makeCard();
            card.setBackground(rounded(Design.cardBg(), selected ? Design.accent() : Design.cardStroke(), 18));
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = label(candidate.name, 17, Design.text());
            name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            texts.addView(name);
            TextView kind = label(candidate.dark ? "Koyu palet" : L10n.s("Açık palet"), 12, Design.muted());
            kind.setPadding(0, dp(2), 0, 0);
            texts.addView(kind);
            top.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (selected) {
                TextView badge = label(L10n.s("GÜNCEL"), 10, Design.accent());
                badge.setLetterSpacing(.08f);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 10));
                badge.setPadding(dp(8), dp(4), dp(8), dp(4));
                top.addView(badge);
            }
            card.addView(top);
            LinearLayout swatches = new LinearLayout(this);
            swatches.setPadding(0, dp(10), 0, dp(2));
            for (int color : candidate.swatch) {
                View dot = new View(this);
                dot.setBackground(rounded(color, color, 7));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(22), dp(22));
                params.setMargins(0, 0, dp(6), 0);
                swatches.addView(dot, params);
            }
            TextView hero = label(L10n.s("Sahne"), 11, Design.heroSub());
            hero.setGravity(Gravity.CENTER);
            hero.setBackground(rounded(Design.heroA(), Design.heroA(), 14));
            hero.setPadding(dp(10), dp(4), dp(10), dp(4));
            swatches.addView(hero);
            card.addView(swatches);
            card.addView(themePreview(candidate));
            card.setOnClickListener(v -> {
                prefs().edit().putString("ui_theme", candidate.id).apply();
                Design.refresh(this);
                Design.applyBars(this);
                // Palette change must repaint header + nav too: in-place refresh
                // only rebuilds the content list, which would keep old colors.
                resetPageAnchor();
                Toast.makeText(this, candidate.name + L10n.s(L10n.s(" teması uygulandı.")), Toast.LENGTH_SHORT).show();
                showAppearance();
            });
            addCardToPage(card);
        }
    }

    /**
     * Mini live preview (GUI review item 15): hero strip, character balloon and user
     * balloon rendered with the candidate palette's own tokens, not the active theme.
     */
    private LinearLayout themePreview(AppTheme theme) {
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(10), dp(10), dp(10), dp(12));
        preview.setBackground(rounded(theme.pageBg, theme.pageBg, 14));

        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{theme.heroA, theme.heroB});
        heroBg.setCornerRadius(dp(12));
        TextView heroText = new TextView(this);
        heroText.setText(L10n.s(L10n.s("Hikâyen burada başlıyor.")));
        heroText.setTextSize(12);
        heroText.setTextColor(theme.heroText);
        heroText.setPadding(dp(10), dp(7), dp(10), dp(7));
        heroText.setBackground(heroBg);
        preview.addView(heroText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView heroSub = label(L10n.s("Konuşma balonları · sahneler"), 11, theme.heroSub);
        heroSub.setPadding(dp(2), dp(5), 0, 0);
        preview.addView(heroSub);

        TextView charBalloon = new TextView(this);
        charBalloon.setTextSize(12);
        charBalloon.setTextColor(theme.text);
        charBalloon.setPadding(dp(10), dp(7), dp(10), dp(7));
        charBalloon.setBackground(rounded(theme.cardBg, theme.cardBg, 12));
        SpannableString charText = new SpannableString(L10n.s("Nova · 20:41\nGüzel bir gece…"));
        charText.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        charText.setSpan(new android.text.style.ForegroundColorSpan(theme.accent), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        charBalloon.setText(charText);
        LinearLayout.LayoutParams charParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        charParams.setMargins(0, dp(8), dp(24), dp(6));
        preview.addView(charBalloon, charParams);

        LinearLayout userRow = new LinearLayout(this);
        userRow.setGravity(Gravity.END);
        TextView userBalloon = label("Selam!", 12, theme.text);
        userBalloon.setPadding(dp(10), dp(7), dp(10), dp(7));
        userBalloon.setBackground(rounded(theme.bubbleUser, theme.bubbleUserStroke, 12));
        userRow.addView(userBalloon);
        preview.addView(userRow, fullWidth());
        return preview;
    }

    /** Total device RAM (bytes); 0 when unknown — callers treat it as "no verdict". */
    private long deviceTotalMemory() {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null) return 0;
        android.app.ActivityManager.MemoryInfo memory = new android.app.ActivityManager.MemoryInfo();
        manager.getMemoryInfo(memory);
        return memory.totalMem;
    }

    /**
     * Fit verdict (DeepSeek item 11): 0 = comfortable (file + KV + runtime fit),
     * 1 = borderline (tight but usually survivable), 2 = will not fit, -1 = unknown.
     * Loaded GGUF files occupy roughly their file size plus KV-cache/runtime overhead,
     * so ~3× the file should fit in RAM; up to ~2.4× counts as borderline.
     */
    private int fitVerdict(long bytes) {
        long totalMemory = deviceTotalMemory();
        if (bytes <= 0 || totalMemory <= 0) return -1;
        if (totalMemory / 3 >= bytes) return 0;
        if (totalMemory * 5 / 12 >= bytes) return 1;
        return 2;
    }

    /**
     * Fit verdict chip (GUI review item 16): downloaded size + RAM-based comfort.
     * 0 total RAM (unknown) stays neutral.
     */
    private TextView catalogFitChip(long bytes) {
        int verdict = fitVerdict(bytes);
        long totalMemory = deviceTotalMemory();
        boolean known = verdict >= 0;
        TextView chip = new TextView(this);
        chip.setTextSize(12);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(9), dp(4), dp(9), dp(4));
        chip.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 10));
        if (!known) {
            chip.setTextColor(Design.muted());
            chip.setText(L10n.s(L10n.s("İndirme ")) + humanSize(bytes));
            return chip;
        }
        LinearLayout.LayoutParams self = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        self.setMargins(dp(7), 0, 0, 0);
        chip.setLayoutParams(self);
        String size = L10n.s(L10n.s("İndirme ")) + humanSize(bytes);
        if (verdict == 0) {
            chip.setTextColor(Design.accent());
            chip.setText(size + L10n.s("  ·  bu cihazda rahat"));
        } else if (verdict == 1) {
            chip.setTextColor(Design.danger());
            chip.setBackground(rounded(Design.dangerSoft(), Design.dangerStroke(), 10));
            chip.setText(size + L10n.s(L10n.s("  ·  sınırda")));
        } else {
            chip.setTextColor(Design.danger());
            chip.setBackground(rounded(Design.dangerSoft(), Design.dangerStroke(), 10));
            chip.setText(size + L10n.s(L10n.s("  ·  RAM'e göre riskli")));
        }
        return chip;
    }

    private void showModelCatalog() {
        visibleChatId = null;
        // During the guided first-launch flow the catalog must step back into it
        // instead of dropping the user onto the settings page (DeepSeek item 13).
        backAction = onboardingActive ? this::showOnboardingModel : this::showModels;
        setPage(L10n.s("Model önerileri"), L10n.s("CPU ve GPU dengelerine göre GGUF seç ve indir"), "settings");
        addDeviceCard();
        addSegmentedCatalog();
        if (catalogFilter == 0) {
            ModelCatalog.Model pick = ModelCatalog.cpuRecommendation();
            addCatalogCard(pick, true);
            for (ModelCatalog.Model model : ModelCatalog.CPU_MODELS) if (model != pick) addCatalogCard(model, false);
        } else if (catalogFilter == 1) {
            ModelCatalog.Model pick = ModelCatalog.gpuRecommendation();
            addCatalogCard(pick, true);
            for (ModelCatalog.Model model : ModelCatalog.GPU_MODELS) if (model != pick) addCatalogCard(model, false);
        } else if (catalogFilter == 2) {
            addBody(L10n.s(L10n.s("Görsel modeller bu cihazda çalışır: model yüklendikten sonra sohbette ✨ düğmesiyle resim üretebilirsin. Her şey çevrimdışı işler.")));
            ModelCatalog.ImageModel pick = ModelCatalog.IMAGE_MODELS[0];
            addImageCatalogCard(pick, true, false);
            for (ModelCatalog.ImageModel model : ModelCatalog.IMAGE_MODELS) if (model != pick) addImageCatalogCard(model, false, false);
        } else {
            TextView warning = label(L10n.s(L10n.s("Sansürsüz modeller abliterated (filtre katmanı çıkarılmış) eğitimlidir; 18 yaş üstü içindir.")), 13, Design.danger());
            warning.setPadding(0, dp(4), 0, dp(8));
            content.addView(warning);
            addBody(L10n.s(L10n.s("Uygulama hiçbir sohbet verisini genel ağa göndermez; çıktı işleme tamamen bu cihazda çalışır. Sorumluluk kullanıcıya aittir.")));
            ModelCatalog.Model pick = ModelCatalog.UNCENSORED_MODELS[0];
            addCatalogCard(pick, true);
            for (ModelCatalog.Model model : ModelCatalog.UNCENSORED_MODELS) if (model != pick) addCatalogCard(model, false);
            TextView imageHeader = label(L10n.s(L10n.s("Görsel modeller (18+)")), 13, Design.text());
            imageHeader.setPadding(0, dp(14), 0, dp(8));
            imageHeader.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            content.addView(imageHeader);
            ModelCatalog.ImageModel imagePick = ModelCatalog.UNCENSORED_IMAGE_MODELS[0];
            addImageCatalogCard(imagePick, true, true);
            for (ModelCatalog.ImageModel model : ModelCatalog.UNCENSORED_IMAGE_MODELS) if (model != imagePick) addImageCatalogCard(model, false, true);
        }
        addBody(L10n.s("İndirmeler Hugging Face'den doğrudan olur ve uygulamanın özel alanına kaydedilir. Parola veya hesap gerekmez."));
        addBody(L10n.s("GPU hızları * Vulkan/MLC tabanlı motorlar içindir; bu sürümün motoru llama.cpp CPU ile çalışır."));
    }

    private void addDeviceCard() {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo memory = new android.app.ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(memory);
        long totalMb = memory.totalMem / 1_048_576L;
        long freeMb = memory.availMem / 1_048_576L;
        int cores = Runtime.getRuntime().availableProcessors();
        addInfoCard(L10n.s("Cihaz analizi"),
                L10n.s("Bellek: ") + humanSize(totalMb * 1_048_576L) + L10n.s(L10n.s(" · boş: ")) + humanSize(freeMb * 1_048_576L)
                        + "\n" + L10n.s(L10n.s("CPU çekirdeği: ")) + cores,
                "Bu cihaz");
    }

    private void addSegmentedCatalog() {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(2), 0, dp(8));
        String[] labels = {L10n.s("CPU önerileri"), L10n.s("GPU önerileri"),
                L10n.s(L10n.s("Görsel")),
                L10n.s(L10n.s("Sansürsüz")) + (uncensoredUnlocked() ? "" : "  🔒")};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = label(labels[i], 14, i == catalogFilter ? Design.onAccent() : Design.muted());
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            if (i == catalogFilter) chip.setBackground(rounded(Design.accent(), Design.accent(), 14));
            else chip.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 14));
            chip.setOnClickListener(v -> {
                if (catalogFilter != index) {
                    if (index == 3 && !uncensoredUnlocked()) {
                        pendingCatalogFilter = index;
                        showUncensoredGate();
                        return;
                    }
                    catalogFilter = index;
                    showModelCatalog();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1);
            params.setMargins(0, 0, i == labels.length - 1 ? 0 : dp(8), 0);
            row.addView(chip, params);
        }
        content.addView(row, fullWidth());
    }

    private boolean uncensoredUnlocked() {
        // v0.17: the acknowledgement deliberately does not persist — every app start
        // re-locks the uncensored section, so the ceremony cannot be a one-time thing.
        return uncensoredSessionUnlock;
    }

    /**
     * Age gate for the uncensored model catalog, hardened in v0.17: four checkpoints —
     * a mandatory warning checkbox, a numeric age (18+), an exact typed confirmation
     * phrase, and a 10-second cooldown before the confirm button activates. The unlock
     * lasts only for this session; the dialog stays open on any invalid state.
     */
    private void showUncensoredGate() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(18), dp(22), 0);
        TextView warning = label(L10n.s(L10n.s("UYARI: Bu modellerde güvenlik filtresi yoktur; 18 yaş altına uygun içerik koruması içermez.")), 13, Design.danger());
        warning.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        box.addView(warning);
        TextView notice = label(L10n.s(L10n.s("İşleme tamamen bu cihazda, çevrimdışı çalışır; sorumluluk sana aittir.")),
                14, Design.text());
        notice.setPadding(0, dp(10), 0, 0);
        box.addView(notice);
        final CheckBox acknowledged = new CheckBox(this);
        acknowledged.setText(L10n.s(L10n.s("Uyarıyı okudum; 18 yaş üstü olduğumu onaylıyorum.")));
        acknowledged.setTextSize(13);
        acknowledged.setTextColor(Design.text());
        box.addView(acknowledged);
        final EditText ageInput = new EditText(this);
        ageInput.setHint(L10n.s(L10n.s("Yaşın")));
        ageInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        ageInput.setTextSize(15);
        ageInput.setTextColor(Design.text());
        box.addView(ageInput);
        final String phrase = L10n.s(L10n.s("onaylıyorum"));
        TextView phraseHint = label(L10n.s(L10n.s("Onaylamak için kelimeyi yaz: ")) + phrase, 13, Design.text());
        phraseHint.setPadding(0, dp(12), 0, dp(4));
        box.addView(phraseHint);
        final EditText phraseInput = new EditText(this);
        phraseInput.setHint(phrase);
        phraseInput.setTextSize(15);
        phraseInput.setTextColor(Design.text());
        phraseInput.setHintTextColor(Design.muted());
        box.addView(phraseInput);
        TextView sessionNote = label(L10n.s(L10n.s("Açılma bu oturum için geçerlidir; uygulama kapandığında yeniden sorulur.")), 12, Design.muted());
        sessionNote.setPadding(0, dp(10), 0, 0);
        box.addView(sessionNote);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(L10n.s(L10n.s("Sansürsüz modeller (18+)")))
                .setView(box)
                .setPositiveButton(L10n.s(L10n.s("Onayla")), null)
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null)
                .create();
        final android.os.Handler countdown = new android.os.Handler();
        dialog.setOnShowListener(d -> {
            final Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            final int[] secondsLeft = {10};
            confirm.setEnabled(false);
            confirm.setText(L10n.s(L10n.s("Onayla")) + " (" + secondsLeft[0] + ")");
            final Runnable tick = new Runnable() {
                @Override public void run() {
                    secondsLeft[0]--;
                    if (secondsLeft[0] <= 0) {
                        confirm.setEnabled(true);
                        confirm.setText(L10n.s(L10n.s("Onayla")));
                    } else {
                        confirm.setText(L10n.s(L10n.s("Onayla")) + " (" + secondsLeft[0] + ")");
                        countdown.postDelayed(this, 1000);
                    }
                }
            };
            countdown.postDelayed(tick, 1000);
            confirm.setOnClickListener(v -> {
                if (!acknowledged.isChecked()) {
                    Toast.makeText(this, L10n.s(L10n.s("Önce uyarıyı onaylaman gerekli.")), Toast.LENGTH_SHORT).show();
                    return;
                }
                int age = -1;
                try {
                    String value = ageInput.getText().toString().trim();
                    if (!value.isEmpty()) age = Integer.parseInt(value);
                } catch (NumberFormatException ignored) {}
                if (age < 18) {
                    Toast.makeText(this, L10n.s(L10n.s("Bu bölüm yalnızca 18 yaş üstüne açık.")), Toast.LENGTH_LONG).show();
                    return;
                }
                if (!phraseInput.getText().toString().trim().equalsIgnoreCase(phrase)) {
                    Toast.makeText(this, L10n.s(L10n.s("Onay kelimesini tam yazmalısın.")), Toast.LENGTH_SHORT).show();
                    return;
                }
                uncensoredSessionUnlock = true;
                catalogFilter = pendingCatalogFilter >= 0 ? pendingCatalogFilter : 2;
                pendingCatalogFilter = -1;
                dialog.dismiss();
                showModelCatalog();
                Toast.makeText(this, L10n.s(L10n.s("Sansürsüz bölümü bu oturum için açıldı.")), Toast.LENGTH_SHORT).show();
            });
        });
        dialog.setOnDismissListener(d -> countdown.removeCallbacksAndMessages(null));
        tintDialog(dialog);
        dialog.show();
        ageInput.requestFocus();
    }

    private boolean isUncensoredCard(ModelCatalog.Model model) {
        for (ModelCatalog.Model m : ModelCatalog.UNCENSORED_MODELS) if (m == model) return true;
        return false;
    }

    private File catalogFile(ModelCatalog.Model model) {
        File directory = LocalAiRuntime.INSTANCE.modelsDirectory(this);
        File file = new File(directory, model.fileHint);
        if (!file.isFile()) return null;
        // Do not trust the filename alone: a stale or hand-copied file of the wrong
        // quant/colors must not be presented as "İndirildi".
        if (model.bytes > 0 && file.length() != model.bytes) return null;
        return file;
    }

    private File catalogPartialFile(ModelCatalog.Model model) {
        File directory = LocalAiRuntime.INSTANCE.modelsDirectory(this);
        File partial = new File(directory, model.fileHint + ".part");
        return partial.isFile() ? partial : null;
    }

    /** True for cards listed in the GPU tab (task G5): the bundled engine is CPU-only. */
    private boolean isGpuOnlyCard(ModelCatalog.Model model) {
        for (ModelCatalog.Model m : ModelCatalog.GPU_MODELS) if (m == model) return true;
        return false;
    }

    private void addCatalogCard(ModelCatalog.Model model, boolean highlight) {
        LinearLayout card = makeCard();
        card.setBackground(rounded(Design.cardBg(), highlight ? Design.accent() : Design.cardStroke(), 18));
        boolean uncensored = isUncensoredCard(model);
        if (uncensored) {
            TextView badge = label(L10n.s(L10n.s("SANSÜRSÜZ  ·  18+")), 10, Design.danger());
            badge.setLetterSpacing(.09f);
            card.addView(badge);
        }
        if (highlight) {
            TextView badge = label(L10n.s("BU CİHAZ İÇİN ÖNERİ"), 10, Design.accent());
            badge.setLetterSpacing(.09f);
            card.addView(badge);
        }
        TextView name = label(model.name, 17, Design.text());
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        name.setPadding(0, highlight || uncensored ? dp(5) : dp(2), 0, 0);
        card.addView(name);
        TextView meta = label(model.family + L10n.s("  ·  ") + model.quant + L10n.s("  ·  ") + humanSize(model.bytes)
                + L10n.s(L10n.s("  ·  bağlam ")) + (model.contextTokens >= 1024 ? (model.contextTokens / 1024) + "K" : String.valueOf(model.contextTokens)) + " token"
                + L10n.s("  ·  ") + model.lang, 12, Design.muted());
        meta.setPadding(0, dp(3), 0, 0);
        card.addView(meta);
        boolean gpuOnly = isGpuOnlyCard(model);
        if (gpuOnly) {
            // Task G5: the bundled closed engine has no Vulkan/GPU backend; these cards
            // must not pretend otherwise. Warning + disabled download/load below.
            TextView warning = label(L10n.s("⚠ Bu motorda çalışmaz — paketli motor yalnızca CPU; Vulkan/MLC motoru gerekir."), 12, Design.danger());
            warning.setPadding(0, dp(6), 0, 0);
            card.addView(warning);
        }
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, dp(9), 0, dp(2));
        TextView cpuChip = label("CPU  ~" + model.cpuRange, 12, Design.accent());
        cpuChip.setGravity(Gravity.CENTER);
        cpuChip.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 10));
        cpuChip.setPadding(dp(9), dp(4), dp(9), dp(4));
        chips.addView(cpuChip);
        TextView gpuChip = label(gpuOnly ? "GPU  motor yolu yok" : "GPU  ~" + model.gpuRange, 12, Design.muted());
        gpuChip.setGravity(Gravity.CENTER);
        gpuChip.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 10));
        gpuChip.setPadding(dp(9), dp(4), dp(9), dp(4));
        LinearLayout.LayoutParams gpuParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gpuParams.setMargins(dp(7), 0, 0, 0);
        chips.addView(gpuChip, gpuParams);
        chips.addView(catalogFitChip(model.bytes));
        card.addView(chips);
        TextView why = label(model.why, 13, Design.muted());
        why.setPadding(0, dp(7), 0, dp(2));
        card.addView(why);

        boolean downloading = ModelDownload.INSTANCE.isActive()
                && model.url.equals(ModelDownload.INSTANCE.getCurrentUrl());
        File stored = catalogFile(model);
        File partial = catalogPartialFile(model);
        if (downloading) {
            downloadProgressView = addBodyWithin(card, downloadProgressText(model));
            Button cancel = button(L10n.s("İndirmeyi durdur"));
            cancel.setAllCaps(false);
            cancel.setTextColor(Design.danger());
            cancel.setTextSize(14);
            cancel.setBackground(rounded(Design.dangerSoft(), Design.dangerStroke(), 12));
            cancel.setOnClickListener(v -> {
                ModelDownload.INSTANCE.cancel();
                downloadProgressView = null;
                showModelCatalog();
            });
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cancelParams.setMargins(0, dp(8), 0, 0);
            card.addView(cancel, cancelParams);
        } else if (stored != null) {
            TextView ready = label(L10n.s(L10n.s("İndirildi  ·  ")) + humanSize(stored.length()) + L10n.s(L10n.s(" hazır")), 12, Design.accent());
            ready.setPadding(0, dp(7), 0, dp(2));
            card.addView(ready);
            Button load = button(L10n.s("Modeli yükle"));
            load.setAllCaps(false);
            load.setTextSize(15);
            load.setTextColor(Design.onAccent());
            load.setBackground(rounded(Design.accent(), Design.accent(), 14));
            if (gpuOnly) {
                load.setEnabled(false);
                load.setAlpha(.55f);
            } else {
                load.setOnClickListener(v -> loadStoredModelFile(stored));
            }
            LinearLayout.LayoutParams loadParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            loadParams.setMargins(0, dp(10), 0, 0);
            card.addView(load, loadParams);
        } else {
            Button download = button(gpuOnly ? L10n.s(L10n.s("İndirme kapalı  ·  ")) + humanSize(model.bytes) : L10n.s(L10n.s("İndir  ·  ")) + humanSize(model.bytes));
            download.setAllCaps(false);
            download.setTextSize(15);
            download.setTextColor(Design.onAccent());
            download.setBackground(rounded(Design.accent(), Design.accent(), 14));
            if (gpuOnly) {
                download.setEnabled(false);
                download.setAlpha(.55f);
            } else {
                download.setOnClickListener(v -> {
                    if (partial != null) {
                        confirm(L10n.s(L10n.s("Yarı kalan bir indirme var (")) + humanSize(partial.length())
                                + L10n.s(L10n.s("). İndirme kaldığı yerden devam etsin mi?")), L10n.s("Kaldığı yerden devam"), () -> startCatalogDownload(model));
                    } else {
                        startCatalogDownload(model);
                    }
                });
            }
            LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            downloadParams.setMargins(0, dp(10), 0, 0);
            card.addView(download, downloadParams);
        }
        if (partial != null && !downloading) {
            addBodyWithin(card, L10n.s(L10n.s("Kaydedilen parça: ")) + humanSize(partial.length()) + L10n.s(L10n.s(" · devam edince indirilmiş sayılır")));
        }
        addCardToPage(card);
    }

    private TextView addBodyWithin(LinearLayout parent, String text) {
        TextView view = label(text, 12, Design.muted());
        view.setPadding(0, dp(6), 0, 0);
        parent.addView(view);
        return view;
    }

    /** Progress line for the downloading card's inline label (updated in place, no screen rebuild). */
    private String downloadProgressText(ModelCatalog.Model model) {
        StringBuilder text = new StringBuilder(model.name).append(" · indiriliyor…");
        if (downloadTotal > 0) {
            text.append(L10n.s("  %")).append(Math.round(downloadBytes * 100.0 / downloadTotal))
                    .append(L10n.s("  (")).append(humanSize(downloadBytes)).append(L10n.s(" / ")).append(humanSize(downloadTotal)).append(")");
        } else if (downloadBytes > 0) {
            text.append("  ").append(humanSize(downloadBytes));
        }
        return text.toString();
    }

    private void startCatalogDownload(ModelCatalog.Model model) {
        boolean metered = false;
        try {
            android.net.ConnectivityManager manager = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (manager != null) metered = manager.isActiveNetworkMetered();
        } catch (Exception ignored) {}
        Runnable start = () -> {
            File directory = LocalAiRuntime.INSTANCE.modelsDirectory(this);
            if (!directory.exists() && !directory.mkdirs()) {
                Toast.makeText(this, L10n.s(L10n.s("Model klasörü açılamadı.")), Toast.LENGTH_SHORT).show();
                return;
            }
            modelStatus = L10n.s(L10n.s("İndiriliyor: ")) + model.name;
            boolean accepted = ModelDownload.INSTANCE.start(this, model.url, model.fileHint, new ModelDownload.Listener() {
                @Override public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                    if (!uiAlive()) return;
                    downloadBytes = downloadedBytes;
                    downloadTotal = totalBytes;
                    if (!"settings".equals(currentTab)) return;
                    if (downloadProgressView == null) {
                        // Download started from the catalog; rebuild once to mount the
                        // downloading card (label + cancel), then update in place.
                        showModelCatalog();
                        return;
                    }
                    downloadProgressView.setText(downloadProgressText(model));
                }
                @Override public void onDone(File file) {
                    if (!uiAlive()) return;
                    downloadProgressView = null;
                    downloadBytes = 0L;
                    downloadTotal = -1L;
                    Toast.makeText(MainActivity.this, model.name + L10n.s(" indirildi."), Toast.LENGTH_SHORT).show();
                    if ("settings".equals(currentTab)) showModelCatalog();
                }
                @Override public void onError(String message) {
                    if (!uiAlive()) return;
                    downloadProgressView = null;
                    Toast.makeText(MainActivity.this, L10n.s(L10n.s("İndirme hatası: ")) + message, Toast.LENGTH_SHORT).show();
                    if ("settings".equals(currentTab)) showModelCatalog();
                }
            }, model.bytes, model.sha256, null);
            if (!accepted) {
                Toast.makeText(this, L10n.s(L10n.s("Önceki indirme hâlâ sonlanıyor; bir saniye sonra tekrar dene.")), Toast.LENGTH_SHORT).show();
            } else {
                if ("settings".equals(currentTab)) {
                    // Swap the card into its downloading state right away
                    // (progress label + cancel button) instead of waiting for %1.
                    showModelCatalog();
                }
            }
        };
        if (metered) {
            new AlertDialog.Builder(this).setTitle(L10n.s(L10n.s("Mobil veri uyarısı")))
                    .setMessage(model.name + L10n.s(" (") + humanSize(model.bytes) + L10n.s(L10n.s(") mobil veriyle indirilecek. Wi-Fi önerilir.")))
                    .setPositiveButton(L10n.s(L10n.s("İndir")), (d, w) -> start.run())
                    .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).show();
        } else {
            start.run();
        }
    }

    // ---- image model catalog (Görsel tab) ----

    private File imageCatalogFile(ModelCatalog.ImageModel model) {
        File directory = LocalImageEngine.INSTANCE.imagesDirectory(this);
        File file = new File(directory, model.fileHint);
        if (!file.isFile()) return null;
        if (model.bytes > 0 && file.length() != model.bytes) return null;
        return file;
    }

    private boolean isImageCatalogDownloading(ModelCatalog.ImageModel model) {
        return ModelDownload.INSTANCE.isActive() && model.url.equals(ModelDownload.INSTANCE.getCurrentUrl());
    }

    /** Download-target subdirectory for image checkpoints ("images", catalog-fixed). */
    private static final String IMAGE_SUBDIRECTORY = "images";

    private void addImageCatalogCard(ModelCatalog.ImageModel model, boolean highlight, boolean uncensored) {
        LinearLayout card = makeCard();
        card.setBackground(rounded(Design.cardBg(), highlight ? Design.accent() : Design.cardStroke(), 18));
        if (uncensored) {
            TextView badge = label(L10n.s(L10n.s("SANSÜRSÜZ  ·  18+")), 10, Design.danger());
            badge.setLetterSpacing(.09f);
            card.addView(badge);
        }
        if (highlight) {
            TextView badge = label(L10n.s(L10n.s("BU CİHAZ İÇİN ÖNERİ")), 10, Design.accent());
            badge.setLetterSpacing(.09f);
            card.addView(badge);
        }
        TextView name = label(model.name, 17, Design.text());
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        name.setPadding(0, highlight || uncensored ? dp(5) : dp(2), 0, 0);
        card.addView(name);
        TextView meta = label(model.style + L10n.s("  ·  ") + humanSize(model.bytes)
                + "  ·  ~" + model.steps + L10n.s(" adım"), 12, Design.muted());
        meta.setPadding(0, dp(3), 0, 0);
        card.addView(meta);
        TextView why = label(model.why, 13, Design.muted());
        why.setPadding(0, dp(7), 0, dp(2));
        card.addView(why);
        LinearLayout fitRow = new LinearLayout(this);
        fitRow.setPadding(0, dp(9), 0, dp(2));
        fitRow.addView(catalogFitChip(model.bytes));
        card.addView(fitRow);

        boolean downloading = isImageCatalogDownloading(model);
        File stored = imageCatalogFile(model);
        File partial = new File(LocalImageEngine.INSTANCE.imagesDirectory(this), model.fileHint + ".part");
        final File partialFile = partial.isFile() ? partial : null;
        if (downloading) {
            downloadProgressView = addBodyWithin(card, imageDownloadProgressText(model));
            Button cancel = button(L10n.s(L10n.s("İndirmeyi durdur")));
            cancel.setTextColor(Design.danger());
            cancel.setBackground(rounded(Design.dangerSoft(), Design.dangerStroke(), 12));
            cancel.setOnClickListener(v -> {
                ModelDownload.INSTANCE.cancel();
                downloadProgressView = null;
                showModelCatalog();
            });
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cancelParams.setMargins(0, dp(8), 0, 0);
            card.addView(cancel, cancelParams);
        } else if (stored != null) {
            TextView ready = label(L10n.s(L10n.s("İndirildi  ·  ")) + humanSize(stored.length()) + L10n.s(L10n.s(" hazır")), 12, Design.accent());
            ready.setPadding(0, dp(7), 0, dp(2));
            card.addView(ready);
            Button load = button(L10n.s(L10n.s("Görsel modelini yükle")));
            load.setTextColor(Design.onAccent());
            load.setBackground(rounded(Design.accent(), Design.accent(), 14));
            boolean active = LocalImageEngine.INSTANCE.isModelLoading()
                    || LocalImageEngine.INSTANCE.isGenerating();
            // Already loaded: the load button must not silently re-load the same file.
            boolean loadedThis = LocalImageEngine.INSTANCE.loadedModelName() != null
                    && model.fileHint.equals(LocalImageEngine.INSTANCE.loadedModelName());
            if (active || loadedThis) {
                load.setEnabled(false);
                load.setAlpha(.55f);
            } else {
                load.setOnClickListener(v -> startImageModelLoad(model, stored));
            }
            if (loadedThis) {
                TextView loaded = label(L10n.s(L10n.s("Yüklü  ·  sohbette ✨ ile üret")), 12, Design.accent());
                loaded.setPadding(0, dp(6), 0, 0);
                card.addView(loaded);
            }
            LinearLayout.LayoutParams loadParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            loadParams.setMargins(0, dp(10), 0, 0);
            card.addView(load, loadParams);
            Button delete = button(L10n.s(L10n.s("Sil")));
            delete.setTextColor(Design.danger());
            delete.setBackground(rounded(Design.dangerSoft(), Design.dangerStroke(), 12));
            if (active || loadedThis) {
                // Unload first (or wait for generation) before deleting a stored model.
                delete.setEnabled(false);
                delete.setAlpha(.55f);
            } else {
                delete.setOnClickListener(v -> confirm(
                        model.name + L10n.s(L10n.s(" cihazdan silinsin mi? Tekrar kullanmak için yeniden indirilir.")),
                        L10n.s(L10n.s("Sil")), () -> {
                            if (LocalImageEngine.INSTANCE.deleteStoredModel(this, stored)) {
                                Toast.makeText(this, model.name + L10n.s(" silindi."), Toast.LENGTH_SHORT).show();
                                if (imageLoadStatus != null) imageLoadStatus = null;
                                showModelCatalog();
                            } else {
                                Toast.makeText(this, L10n.s(L10n.s("Görsel motoruyla bir işlem sürüyor; bitmesini bekle.")), Toast.LENGTH_SHORT).show();
                            }
                        }));
            }
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            deleteParams.setMargins(0, dp(8), 0, 0);
            card.addView(delete, deleteParams);
        } else {
            Button download = button(L10n.s(L10n.s("İndir  ·  ")) + humanSize(model.bytes));
            download.setTextColor(Design.onAccent());
            download.setBackground(rounded(Design.accent(), Design.accent(), 14));
            download.setOnClickListener(v -> {
                if (partialFile != null) {
                    confirm(L10n.s(L10n.s("Yarı kalan bir indirme var (")) + humanSize(partialFile.length())
                            + L10n.s(L10n.s("). İndirme kaldığı yerden devam etsin mi?")), L10n.s(L10n.s("Kaldığı yerden devam")),
                            () -> startCatalogImageDownload(model));
                } else {
                    startCatalogImageDownload(model);
                }
            });
            LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            downloadParams.setMargins(0, dp(10), 0, 0);
            card.addView(download, downloadParams);
        }
        if (imageLoadStatus != null && (stored != null || LocalImageEngine.INSTANCE.isModelLoading())) {
            addBodyWithin(card, imageLoadStatus);
        }
        if (partialFile != null && !downloading) {
            addBodyWithin(card, L10n.s(L10n.s("Kaydedilen parça: ")) + humanSize(partialFile.length()) + L10n.s(L10n.s(" · devam edince indirilmiş sayılır")));
        }
        addCardToPage(card);
    }

    private String imageDownloadProgressText(ModelCatalog.ImageModel model) {
        StringBuilder text = new StringBuilder(model.name).append(L10n.s(" · indiriliyor…"));
        if (downloadTotal > 0) {
            text.append(L10n.s("  %")).append(Math.round(downloadBytes * 100.0 / downloadTotal))
                    .append(L10n.s("  (")).append(humanSize(downloadBytes)).append(L10n.s(" / ")).append(humanSize(downloadTotal)).append(")");
        } else if (downloadBytes > 0) {
            text.append("  ").append(humanSize(downloadBytes));
        }
        return text.toString();
    }

    private void startCatalogImageDownload(ModelCatalog.ImageModel model) {
        boolean metered = false;
        try {
            android.net.ConnectivityManager manager = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (manager != null) metered = manager.isActiveNetworkMetered();
        } catch (Exception ignored) {}
        Runnable start = () -> {
            LocalImageEngine.INSTANCE.imagesDirectory(this);
            modelStatus = L10n.s(L10n.s("İndiriliyor: ")) + model.name;
            boolean accepted = ModelDownload.INSTANCE.start(this, model.url, model.fileHint, new ModelDownload.Listener() {
                @Override public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                    if (!uiAlive()) return;
                    downloadBytes = downloadedBytes;
                    downloadTotal = totalBytes;
                    if (!"settings".equals(currentTab)) return;
                    if (downloadProgressView == null) {
                        showModelCatalog();
                        return;
                    }
                    downloadProgressView.setText(imageDownloadProgressText(model));
                }
                @Override public void onDone(File file) {
                    if (!uiAlive()) return;
                    downloadProgressView = null;
                    downloadBytes = 0L;
                    downloadTotal = -1L;
                    Toast.makeText(MainActivity.this, model.name + L10n.s(" indirildi."), Toast.LENGTH_SHORT).show();
                    if ("settings".equals(currentTab)) showModelCatalog();
                }
                @Override public void onError(String message) {
                    if (!uiAlive()) return;
                    downloadProgressView = null;
                    Toast.makeText(MainActivity.this, L10n.s(L10n.s("İndirme hatası: ")) + message, Toast.LENGTH_SHORT).show();
                    if ("settings".equals(currentTab)) showModelCatalog();
                }
            }, model.bytes, model.sha256, IMAGE_SUBDIRECTORY);
            if (!accepted) {
                Toast.makeText(this, L10n.s(L10n.s("Önceki indirme hâlâ sonlanıyor; bir saniye sonra tekrar dene.")), Toast.LENGTH_SHORT).show();
            } else if ("settings".equals(currentTab)) {
                showModelCatalog();
            }
        };
        if (metered) {
            new AlertDialog.Builder(this).setTitle(L10n.s(L10n.s("Mobil veri uyarısı")))
                    .setMessage(model.name + L10n.s(" (") + humanSize(model.bytes) + L10n.s(L10n.s(") mobil veriyle indirilecek. Wi-Fi önerilir.")))
                    .setPositiveButton(L10n.s(L10n.s("İndir")), (d, w) -> start.run())
                    .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).show();
        } else {
            start.run();
        }
    }

    private void startImageModelLoad(ModelCatalog.ImageModel model, File stored) {
        if (LocalImageEngine.INSTANCE.isModelLoading() || LocalImageEngine.INSTANCE.isGenerating()) {
            Toast.makeText(this, L10n.s(L10n.s("Görsel motoruyla bir işlem sürüyor; bitmesini bekle.")), Toast.LENGTH_SHORT).show();
            return;
        }
        imageLoadStatus = L10n.s(L10n.s("Yükleniyor… ilk üretimden önce birkaç dakika sürebilir."));
        if ("settings".equals(currentTab)) showModelCatalog();
        LocalImageEngine.INSTANCE.loadStoredModel(this, stored, new LocalImageEngine.ImageListener() {
            @Override public void onStatus(String message) { }
            @Override public void onReady(File file) {
                if (!uiAlive()) return;
                imageLoadStatus = null;
                LocalImageEngine.INSTANCE.setPreparedStepCount(model.steps);
                Toast.makeText(MainActivity.this,
                        model.name + L10n.s(L10n.s(" yüklendi; sohbette ✨ düğmesiyle görsel üretebilirsin.")), Toast.LENGTH_LONG).show();
                if ("settings".equals(currentTab)) showModelCatalog();
            }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                imageLoadStatus = null;
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                if ("settings".equals(currentTab)) showModelCatalog();
            }
        });
    }

    private void loadStoredModelFile(File file) {
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading()) return;
        modelLoading = true;
        modelStatus = L10n.s("Yükleme başlatılıyor…");
        if ("settings".equals(currentTab)) showModels();
        LocalAiRuntime.INSTANCE.loadStoredModel(this, file, new LocalAiRuntime.ModelListener() {
            @Override public void onStatus(String message) {
                if (!uiAlive()) return;
                modelStatus = message;
                if ("settings".equals(currentTab) && modelStatusView != null) modelStatusView.setText(message);
            }
            @Override public void onLoaded(String fileName, long bytes) {
                if (!uiAlive()) return;
                modelLoading = false;
                selectedModelName = fileName;
                contextChars.clear();
                chatMetrics.clear();
                modelStatus = L10n.s(L10n.s("Hazır · ")) + humanSize(bytes) + L10n.s(L10n.s(" · çevrimdışı"));
                if ("settings".equals(currentTab)) showModels();
                else if (onboardingActive && "model".equals(onboardingStep)) showOnboardingModel();
                Toast.makeText(MainActivity.this, fileName + L10n.s(L10n.s(" yüklendi.")), Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                modelLoading = false;
                modelStatus = L10n.s(L10n.s("Yüklenemedi: ")) + message;
                if ("settings".equals(currentTab)) showModels();
                else if (onboardingActive && "model".equals(onboardingStep)) showOnboardingModel();
                new AlertDialog.Builder(MainActivity.this).setTitle(L10n.s(L10n.s("Model yüklenemedi")))
                        .setMessage(message).setPositiveButton(L10n.s(L10n.s("Tamam")), null).show();
            }
        });
    }

    /**
     * Prompt language home (user request): pick between shipped Turkish / English
     * defaults and a custom text for BOTH the general AI instruction and the engine
     * system prompt. Selections reset the model context so changes take effect.
     */
    private void showPromptStyle() {
        visibleChatId = null;
        backAction = this::showSettings;
        setPage("Prompt dilleri", "Genel talimat ve sistem promptu", "settings");
        SharedPreferences prefs = prefs();
        String generalMode = prefs.getString("general_prompt_source", "default");
        String sysMode = prefs.getString("sys_prompt_source", "engine");

        addSection(L10n.s("Genel AI talimatı"), null, null);
        addBody(L10n.s("Her promptun \"Genel AI talimatı\" satırını seçer: varsayılanlar dile göre belirlenir; özel seçtiğinde kendi metnin kullanılır."));
        addSecondary(L10n.s(L10n.s("Varsayılan (Türkçe)")) + ("default".equals(generalMode) && prefs().getString("app_language", "tr").equals("tr") ? "  ✓" : ""), v -> setGeneralPromptSource("default"));
        addSecondary(L10n.s(L10n.s("Varsayılan (English)")) + ("default".equals(generalMode) && prefs().getString("app_language", "tr").equals("en") ? "  ✓" : ""), v -> setGeneralPromptSource("default"));
        addSecondary(L10n.s(L10n.s("Özel")) + ("custom".equals(generalMode) ? "  ✓" : ""), v -> {
            prefs.edit().putString("general_prompt_source", "custom").apply();
            LocalAiRuntime.INSTANCE.resetConversation(null);
            showPromptStyle();
        });
        if ("custom".equals(generalMode)) {
            EditText instructions = field(L10n.s("AI'nin genel yanıt yönergesi"), prefs.getString("ai_instructions", DEFAULT_AI_INSTRUCTIONS), 4);
            addPrimary(L10n.s("Özel talimatı kaydet"), v -> {
                prefs.edit().putString("ai_instructions", instructions.getText().toString().trim()).apply();
                Toast.makeText(this, L10n.s(L10n.s("Özel talimat kaydedildi.")), Toast.LENGTH_SHORT).show();
            });
        }

        addSection(L10n.s("Sistem promptu"), null, null);
        addBody(L10n.s("Motorun dünya görüşünü belirleyen çekirdek prompt; karakter tanımından önce gelir."));
        addSecondary(L10n.s(L10n.s("Motor varsayılanı")) + ("engine".equals(sysMode) ? "  ✓" : ""), v -> setSystemPromptSource("engine"));
        addSecondary(L10n.s(L10n.s("Türkçe")) + ("tr".equals(sysMode) ? "  ✓" : ""), v -> setSystemPromptSource("tr"));
        addSecondary("English" + ("en".equals(sysMode) ? "  ✓" : ""), v -> setSystemPromptSource("en"));
        addSecondary(L10n.s(L10n.s("Özel")) + ("custom".equals(sysMode) ? "  ✓" : ""), v -> {
            prefs.edit().putString("sys_prompt_source", "custom").apply();
            applySystemPrompt();
            LocalAiRuntime.INSTANCE.resetConversation(null);
            showPromptStyle();
        });
        if ("custom".equals(sysMode)) {
            EditText systemEditor = field(L10n.s("Motor kavrayışının çekirdeği"),
                    prefs.getString("custom_sys_prompt", LocalAiRuntime.INSTANCE.currentSystemPrompt()), 4);
            addPrimary(L10n.s("Özel sistem promptunu kaydet"), v -> {
                prefs.edit().putString("custom_sys_prompt", systemEditor.getText().toString().trim()).apply();
                applySystemPrompt();
                LocalAiRuntime.INSTANCE.resetConversation(null);
                Toast.makeText(this, L10n.s(L10n.s("Sistem promptu kaydedildi; model bağlamı yeniden kurulacak.")), Toast.LENGTH_SHORT).show();
                showPromptStyle();
            });
        }
        addBody(L10n.s("Değişiklikler model bağlamını sıfırlar; ilk yanıt biraz daha uzun sürebilir."));
    }

    private void setGeneralPromptSource(String mode) {
        prefs().edit().putString("general_prompt_source", mode).apply();
        LocalAiRuntime.INSTANCE.resetConversation(null);
        Toast.makeText(this, L10n.s(L10n.s("Genel talimat tercihleri güncellendi.")), Toast.LENGTH_SHORT).show();
        showPromptStyle();
    }

    private void setSystemPromptSource(String mode) {
        prefs().edit().putString("sys_prompt_source", mode).apply();
        applySystemPrompt();
        LocalAiRuntime.INSTANCE.resetConversation(null);
        Toast.makeText(this, L10n.s(L10n.s("Sistem promptu tercihi güncellendi.")), Toast.LENGTH_SHORT).show();
        showPromptStyle();
    }

    private void showAiAdvanced() {
        visibleChatId = null;
        backAction = this::showSettings;
        setPage(L10n.s("Sahne hafızası ve zincir"), L10n.s("Geçmiş derinliği, bağlam ve kendiliğinden devam"), "settings");
        SharedPreferences prefs = prefs();
        addSection(L10n.s("Sahne hafızası"), L10n.s("Model bağlamının derinliği"), null);
        int depth = prefs.getInt("history_depth", 8);
        addSlider(L10n.s("Geçmiş mesaj sayısı"), depth, 2, 14, 1, value -> {
            prefs.edit().putInt("history_depth", value).apply();
        }, value -> value + L10n.s(" mesaj"));
        int chars = prefs.getInt("history_chars", 2600);
        addSlider(L10n.s("Geçmiş karakter bütçesi"), chars, 1200, 3600, 200, value -> {
            prefs.edit().putInt("history_chars", value).apply();
        }, value -> humanSize(value) + L10n.s(" metin"));

        addSection(L10n.s("Bağlam davranışı"), null, null);
        addBudgetRow(prefs);

        addSection(L10n.s("Karakter zinciri"), null, null);
        int autoChain = prefs.getInt("auto_chain", 0);
        addSlider(L10n.s("Kendiliğinden devam sayısı"), autoChain, 0, 3, 1, value -> {
            prefs.edit().putInt("auto_chain", value).apply();
        }, value -> value == 0 ? L10n.s("Kapalı") : value + " zincir");
        addBody(L10n.s("Zincir açıkken karakter, kendi yanıtından sonra sıradaki karakteri otomatik çağırır. Sahne palazlanır ama token tüketimi artar."));

        addSection(L10n.s("Metin görüntüsü"), null, null);
        addToggleRow(L10n.s("Markdown biçimlendirme"), L10n.s("*italik* ve **kalın** baloncuklarda uygulanır"),
                "markdown_lite", true);
        addBody(L10n.s("Değişiklikler bir sonraki yanıtta uygulanır."));
        addPrimary(L10n.s("Vazgeç"), v -> showSettings());
    }

    /** Simple on/off preference row bound to a boolean pref; returns the current state. */
    private void addToggleRow(String title, String subtitle, String key, boolean defaultValue) {
        Button row = new Button(this);
        row.setAllCaps(false);
        row.setBackground(null);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setTextSize(15);
        row.setTextColor(Design.text());
        row.setPadding(dp(4), dp(14), dp(4), dp(14));
        java.util.function.BooleanSupplier render = () -> prefs().getBoolean(key, defaultValue);
        Runnable refresh = () -> {
            boolean on = render.getAsBoolean();
            row.setText(title + L10n.s("  ·  ") + (on ? L10n.s(L10n.s("Açık")) : L10n.s(L10n.s("Kapalı")))
                    + (subtitle.isEmpty() ? "" : "\n" + subtitle));
        };
        refresh.run();
        row.setOnClickListener(v -> {
            boolean now = !render.getAsBoolean();
            prefs().edit().putBoolean(key, now).apply();
            refresh.run();
        });
        LinearLayout.LayoutParams params = fullWidth();
        content.addView(row, params);
    }

    private void addSlider(String caption, int current, int min, int max, int step,
                           java.util.function.IntConsumer save, java.util.function.IntFunction<String> labeler) {
        addBody(caption);
        TextView valueLabel = label(labeler.apply(current), 14, Design.text());
        valueLabel.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        content.addView(valueLabel);
        SeekBar bar = new SeekBar(this);
        int span = Math.max(1, (max - min) / step);
        bar.setMax(span);
        bar.setProgress(Math.max(0, Math.min(span, (current - min) / step)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int progress, boolean fromUser) {
                int value = min + progress * step;
                valueLabel.setText(labeler.apply(value));
                if (fromUser) save.accept(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seek) { }
            @Override public void onStopTrackingTouch(SeekBar seek) { }
        });
        content.addView(bar, fullWidth());
    }

    /** Manual/auto context budget row shared by Gelişmiş AI screen. */
    private void addBudgetRow(SharedPreferences prefs) {
        int saved = prefs.getInt("context_budget_chars", -1);
        boolean manual = saved > 0;
        addBody(L10n.s("Bağlam bütçesi"));
        LinearLayout row = new LinearLayout(this);
        TextView auto = label(L10n.s("Otomatik"), 13, !manual ? Design.onAccent() : Design.muted());
        auto.setGravity(Gravity.CENTER);
        auto.setBackground(rounded(manual ? Design.accentSoft() : Design.accent(),
                manual ? Design.accentStroke() : Design.accent(), 12));
        auto.setPadding(dp(10), dp(6), dp(10), dp(6));
        row.addView(auto);
        TextView manualChip = label(L10n.s("Elle"), 13, manual ? Design.onAccent() : Design.muted());
        manualChip.setGravity(Gravity.CENTER);
        manualChip.setBackground(rounded(manual ? Design.accent() : Design.accentSoft(),
                manual ? Design.accent() : Design.accentStroke(), 12));
        manualChip.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.setMargins(dp(8), 0, 0, 0);
        row.addView(manualChip, chipParams);
        content.addView(row, fullWidth());
        TextView valueLabel = label(manual ? humanSize(saved) + " karakter" : L10n.s("modele göre otomatik"), 13, Design.text());
        valueLabel.setPadding(0, dp(6), 0, dp(2));
        content.addView(valueLabel);
        SeekBar bar = new SeekBar(this);
        int min = 3072, max = 12000, step = 512;
        int span = (max - min) / step;
        bar.setMax(span);
        bar.setProgress(manual ? Math.max(0, Math.min(span, (saved - min) / step)) : 0);
        bar.setAlpha(manual ? 1f : .45f);
        bar.setEnabled(manual);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int progress, boolean fromUser) {
                if (!fromUser) return;
                int value = min + progress * step;
                valueLabel.setText(humanSize(value) + L10n.s(" karakter"));
                prefs.edit().putInt("context_budget_chars", value).apply();
                LocalAiRuntime.INSTANCE.setManualContextBudget(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seek) { }
            @Override public void onStopTrackingTouch(SeekBar seek) { }
        });
        content.addView(bar, fullWidth());
        auto.setOnClickListener(v -> {
            prefs.edit().putInt("context_budget_chars", -1).apply();
            LocalAiRuntime.INSTANCE.setManualContextBudget(-1);
            valueLabel.setText(L10n.s(L10n.s("modele göre otomatik")));
            bar.setEnabled(false);
            bar.setAlpha(.45f);
            manualChip.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 12));
            manualChip.setTextColor(Design.muted());
            auto.setBackground(rounded(Design.accent(), Design.accent(), 12));
            auto.setTextColor(Design.onAccent());
            Toast.makeText(this, L10n.s(L10n.s("Bağlam bütçesi modele göre hesaplanacak.")), Toast.LENGTH_SHORT).show();
        });
        manualChip.setOnClickListener(v -> {
            int initial = min;
            prefs.edit().putInt("context_budget_chars", initial).apply();
            LocalAiRuntime.INSTANCE.setManualContextBudget(initial);
            valueLabel.setText(humanSize(initial) + L10n.s(" karakter"));
            bar.setEnabled(true);
            bar.setAlpha(1f);
            bar.setProgress(0);
            auto.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 12));
            auto.setTextColor(Design.muted());
            manualChip.setBackground(rounded(Design.accent(), Design.accent(), 12));
            manualChip.setTextColor(Design.onAccent());
        });
    }


    private String modelInfoTitle(LocalAiRuntime.ModelInfo info) {
        String name = info.getName();
        return name == null || name.trim().isEmpty() ? "Model bilgisi" : oneLine(name, 90);
    }

    private String modelInfoText(LocalAiRuntime.ModelInfo info) {
        StringBuilder details = new StringBuilder();
        if (info.getArchitecture() != null && !info.getArchitecture().trim().isEmpty()) {
            details.append(L10n.s("Mimari: ")).append(info.getArchitecture());
        }
        if (info.getQuantization() != null && !"unknown".equalsIgnoreCase(info.getQuantization())) {
            if (details.length() > 0) details.append(L10n.s(" · "));
            details.append(L10n.s("Nicemleme: ")).append(info.getQuantization());
        }
        if (info.getContextLength() != null) {
            if (details.length() > 0) details.append(L10n.s(" · "));
            details.append(L10n.s(L10n.s("Eğitim bağlamı: "))).append(info.getContextLength()).append(L10n.s(" token"));
        }
        if (info.getSizeLabel() != null && !info.getSizeLabel().trim().isEmpty()
                && !"unknown".equalsIgnoreCase(info.getSizeLabel())) {
            if (details.length() > 0) details.append(L10n.s(" · "));
            details.append(L10n.s(L10n.s("Boyut sınıfı: "))).append(info.getSizeLabel());
        }
        if (details.length() == 0) details.append(L10n.s(L10n.s("GGUF başlığı okundu; ayrıntılı alan bulunamadı.")));
        details.append("\n").append(L10n.s(L10n.s("Motor bağlamı en fazla 8192 token; uygulama bütçesi ")))
                .append(LocalAiRuntime.INSTANCE.contextCharBudget()).append(L10n.s(" karakter."));
        if (info.getContextLength() != null && info.getContextLength() < 4096) {
            details.append("\n").append(L10n.s(L10n.s("Modelin eğitim bağlamı düşük; uzun sohbetlerde bağlam otomatik sıfırlanabilir.")));
        }
        return details.toString();
    }

    private void runBenchmark() {
        if (!LocalAiRuntime.INSTANCE.isModelReady() || modelLoading ||
                LocalAiRuntime.INSTANCE.isModelLoading() || LocalAiRuntime.INSTANCE.isBenchmarking()) return;
        if (generatingChatId != null || LocalAiRuntime.INSTANCE.isGenerating()) {
            Toast.makeText(this, L10n.s(L10n.s("Yanıt üretilirken motor testi başlatılamaz.")), Toast.LENGTH_SHORT).show();
            return;
        }
        modelStatus = L10n.s("Motor testi çalışıyor…");
        if ("settings".equals(currentTab)) showModels();
        LocalAiRuntime.INSTANCE.benchmark(new LocalAiRuntime.BenchmarkListener() {
            @Override public void onStatus(String message) {
                if (!uiAlive()) return;
                modelStatus = message;
                if ("settings".equals(currentTab)) showModels();
                refreshVisibleChat(false);
            }
            @Override public void onResult(String result) {
                if (!uiAlive()) return;
                modelStatus = L10n.s("Motor testi tamamlandı.");
                if ("settings".equals(currentTab)) showModels();
                refreshVisibleChat(false);
                String text = result == null || result.trim().isEmpty()
                        ? L10n.s("Motor sonuç döndürmedi.") : result.trim();
                new AlertDialog.Builder(MainActivity.this).setTitle(L10n.s("Motor testi sonucu"))
                        .setMessage(text).setPositiveButton(L10n.s(L10n.s("Tamam")), null).show();
            }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                modelStatus = L10n.s(L10n.s("Motor testi hatası: ")) + message;
                if ("settings".equals(currentTab)) showModels();
                refreshVisibleChat(false);
                new AlertDialog.Builder(MainActivity.this).setTitle(L10n.s(L10n.s("Motor testi tamamlanamadı")))
                        .setMessage(message).setPositiveButton(L10n.s(L10n.s("Tamam")), null).show();
            }
        });
    }

    /** Opens the system image picker for either the user persona or the open character editor. */
    private void pickAvatar(boolean forPersona) {
        avatarPickPersona = forPersona;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, AVATAR_PICK);
    }

    /** Copies the picked image into private storage as a ~512px PNG copy; null on failure. */
    private File saveAvatarCopy(Uri picked) {
        try {
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream first = getContentResolver().openInputStream(picked)) {
                Bitmap rawBounds = BitmapFactory.decodeStream(first, null, bounds);
                if (rawBounds != null) rawBounds.recycle();
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2;
            Bitmap bitmap;
            try (java.io.InputStream second = getContentResolver().openInputStream(picked)) {
                android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                options.inSampleSize = sample;
                bitmap = BitmapFactory.decodeStream(second, null, options);
            }
            if (bitmap == null) return null;
            File directory = new File(getFilesDir(), "avatars");
            if (!directory.exists() && !directory.mkdirs()) return null;
            File output = new File(directory, "pic-" + java.util.UUID.randomUUID() + ".png");
            try (java.io.FileOutputStream stream = new java.io.FileOutputStream(output)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }
            bitmap.recycle();
            return output;
        } catch (Exception e) {
            return null;
        }
    }

    /** Best-effort removal of a replaced/removed private avatar copy. */
    private void deleteAvatarFile(String path) {
        if (path == null || path.isEmpty()) return;
        new File(path).delete();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BACKUP_EXPORT || requestCode == BACKUP_EXPORT_MEDIA) {
            if (resultCode != RESULT_OK) { // picker cancelled: a staged selection must not leak
                clearPendingSelection();
                return;
            }
            if (data == null || data.getData() == null) { clearPendingSelection(); return; }
            if (requestCode == BACKUP_EXPORT) exportBackup(data.getData());
            else exportBackupMedia(data.getData());
            return;
        }
        if (requestCode == CHAR_EXPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            exportCharacter(data.getData());
            return;
        }
        if (requestCode == BACKUP_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importBackup(data.getData());
            return;
        }
        if (requestCode == MODEL_PICKER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedModel = data.getData();
            try { getContentResolver().takePersistableUriPermission(selectedModel, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
            String name = queryName(selectedModel);
            selectedModelName = name == null || name.trim().isEmpty() ? L10n.s("Seçilen model dosyası") : name;
            modelStatus = L10n.s("Seçildi · yükleme başlatılıyor…");
            contextChars.clear();
            chatMetrics.clear();
            LocalAiRuntime.INSTANCE.resetConversation(null);
            prefs().edit()
                    .putString("selected_model_uri", selectedModel.toString())
                    .putString("selected_model_name", selectedModelName).apply();
            showModels();
            loadSelectedModel();
            return;
        }
        if (requestCode == AVATAR_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            File saved = saveAvatarCopy(data.getData());
            if (saved == null) {
                Toast.makeText(this, L10n.s(L10n.s("Fotoğraf açılamadı; başka bir dosya deneyin.")), Toast.LENGTH_SHORT).show();
                return;
            }
            if (avatarPickPersona) {
                // Only discard a previous DRAFT file; the persisted persona avatar may
                // still be referenced if the user backs out without saving.
                if (personaAvatarDraft != null && !personaAvatarDraft.isEmpty()) deleteAvatarFile(personaAvatarDraft);
                personaAvatarDraft = saved.getPath();
                if (personaPreviewPane != null) {
                    String labelName = personaNameField != null && !personaNameField.getText().toString().trim().isEmpty()
                            ? personaNameField.getText().toString().trim() : "Sen";
                    fillPersonaPreview(personaPreviewPane, labelName, saved.getPath());
                }
            } else {
                if (!pickedAvatarPath.isEmpty()) deleteAvatarFile(pickedAvatarPath);
                pickedAvatarPath = saved.getPath();
                if (avatarStatusView != null) {
                    avatarStatusView.setText(L10n.s(L10n.s("Fotoğraf: ✓ hazır (Kaydet ile saklanır)")));
                    avatarStatusView.setTextColor(Design.accent());
                }
            }
            return;
        }
        if (requestCode == CHAT_PHOTO_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            File saved = saveAvatarCopy(data.getData());
            if (saved == null) {
                Toast.makeText(this, L10n.s(L10n.s("Fotoğraf açılamadı; başka bir dosya deneyin.")), Toast.LENGTH_SHORT).show();
                return;
            }
            pendingComposerImage = saved;
            Group attachGroup = imageAttachGroup != null ? store.group(imageAttachGroup.id) : null;
            if (attachGroup != null) {
                pendingComposerImageChatId = attachGroup.id;
                // The composer is about to be rebuilt for the chip; keep typed text.
                saveComposerDraftTo(attachGroup.id);
                if (attachGroup.id.equals(visibleChatId)) buildChatComposer(attachGroup);
            }
        }
    }

    /** Rebuilds the persona preview avatar inside its card without resetting the editor form. */
    private void fillPersonaPreview(LinearLayout pane, String name, String avatarPath) {
        pane.removeAllViews();
        View face = Avatar.forIdentity(this,
                name == null || name.isEmpty() ? L10n.s("Sen") : name, avatarPath, 64);
        pane.addView(face, new LinearLayout.LayoutParams(dp(64), dp(64)));
    }

    /** Bottom toast-like bar for reversible deletions; finalizes when dismissed or timed out. */
    private void showUndoBar(String text, Runnable restore, Runnable finalizeDelete) {
        hideUndoBar(true); // at most one pending deletion at a time
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(14);
        bar.setPadding(pad, pad, pad, pad);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Design.cardBg());
        background.setCornerRadius(dp(18));
        bar.setBackground(background);
        TextView message = label(text, 13, Design.text());
        bar.addView(message, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView undoButton = label(L10n.s(L10n.s("Geri al")), 13, Design.accent());
        undoButton.setTypeface(null, android.graphics.Typeface.BOLD);
        undoButton.setPadding(dp(16), dp(6), dp(4), dp(6));
        undoButton.setOnClickListener(v -> {
            hideUndoBar(false);
            restore.run();
        });
        bar.addView(undoButton);
        int margin = dp(14);
        android.widget.FrameLayout.LayoutParams layout = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.gravity = Gravity.BOTTOM;
        layout.setMargins(margin, margin, margin, margin + dp(26)); // clears the gesture bar
        Runnable finalizer = () -> {
            if (bar.getParent() != null) ((ViewGroup) bar.getParent()).removeView(bar);
            undoBar = null;
            if (finalizeDelete != null) finalizeDelete.run();
        };
        undoFinalize = finalizer;
        undoBar = bar;
        ((ViewGroup) getWindow().getDecorView()).addView(bar, layout);
        undoClock.postDelayed(finalizer, 8000);
    }

    /** Removes the deletion bar; with finalize, the deletion becomes permanent. */
    private void hideUndoBar(boolean finalize) {
        Runnable finalizer = undoFinalize;
        if (finalizer != null) undoClock.removeCallbacks(finalizer);
        LinearLayout bar = undoBar;
        undoFinalize = null;
        undoBar = null;
        if (bar != null && bar.getParent() != null) ((ViewGroup) bar.getParent()).removeView(bar);
        if (finalize && finalizer != null) finalizer.run();
    }

    /** Navigation or page swap finalizes a pending reversible deletion. */
    private void dismissUndoBar() {
        if (undoBar != null) hideUndoBar(true);
    }

    /** Staged selection for the next SAF export; null = full backup (DeepSeek item 9b). */
    private java.util.Set<String> pendingExportCharacters;
    private java.util.Set<String> pendingExportGroups;

    private LinearLayout undoBar;
    private Runnable undoFinalize;
    private final android.os.Handler undoClock = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingExportCharacters != null) {
            outState.putStringArrayList("pendingExportChars", new ArrayList<>(pendingExportCharacters));
        }
        if (pendingExportGroups != null) {
            outState.putStringArrayList("pendingExportGroups", new ArrayList<>(pendingExportGroups));
        }
    }

    /** Drops a staged selection the moment it is consumed or the picker is cancelled. */
    private void clearPendingSelection() {
        pendingExportCharacters = null;
        pendingExportGroups = null;
    }

    /**
     * Collects the current dataset into a bundle. JSON exports get version 1 with null
     * roots; ZIP exports get version 2 with the device's media roots for @ markers.
     * A staged selective export narrows the bundle before either encoding.
     */
    private PersistenceCodec.Bundle buildFullBundle(String avatarRoot, String imageRoot) {
        java.util.Map<String, List<Message>> chats = new java.util.HashMap<>();
        List<Group> groups = store.groups();
        for (Group group : groups) chats.put(group.id, new ArrayList<>(store.messages(group.id)));
        PersistenceCodec.Bundle bundle = new PersistenceCodec.Bundle();
        bundle.characters.addAll(store.characters());
        bundle.groups.addAll(groups);
        bundle.chats.putAll(chats);
        bundle.personas.addAll(store.personas());
        Persona defaultPersona = store.defaultPersona();
        if (defaultPersona != null) bundle.defaultPersonaId = defaultPersona.id;
        for (Group group : groups) {
            String override = store.chatPersonaId(group.id);
            if (override != null && !override.isEmpty()) bundle.chatPersonas.put(group.id, override);
        }
        for (Group group : groups) {
            List<Lorebook.Entry> lore = store.lore(group.id);
            if (!lore.isEmpty()) bundle.lorebooks.put(group.id, lore);
        }
        if (pendingExportCharacters != null && pendingExportGroups != null) {
            bundle = PersistenceCodec.selectBundle(bundle, pendingExportCharacters, pendingExportGroups);
        }
        if (avatarRoot != null) { // ZIP path: media markers need the real device roots
            bundle.version = 2;
            bundle.avatarsRoot = avatarRoot;
            bundle.imagesRoot = imageRoot;
        }
        return bundle;
    }

    /** Selective export (DeepSeek item 9b): choose characters + chats, then pick the format. */
    private void showSelectiveExport() {
        backAction = this::showSettings;
        setPage(L10n.s("Seçmeli dışa aktar"), L10n.s("Yalnızca seçtiğin karakterler ve sohbetler yazılır"), "settings");
        List<CharacterProfile> characters = store.characters();
        List<Group> groups = store.groups();
        if (characters.isEmpty() && groups.isEmpty()) {
            addEmpty(L10n.s("Yazılacak bir şey yok"), L10n.s("Önce karakter ya da sohbet oluştur."), null, null);
            return;
        }
        List<CheckBox> characterBoxes = new ArrayList<>();
        addSection(L10n.s("Karakterler"), L10n.s("Seçtiğin sohbetlerin karakterleri otomatik yazılır"), null);
        for (CharacterProfile character : characters) {
            CheckBox choice = new CheckBox(this);
            choice.setText("   " + character.name);
            choice.setTextSize(16);
            choice.setPadding(dp(4), dp(5), dp(4), dp(5));
            choice.setTextColor(Design.text());
            content.addView(choice);
            characterBoxes.add(choice);
        }
        List<CheckBox> groupBoxes = new ArrayList<>();
        addSection(L10n.s("Sohbetler"), L10n.s("Geçmişleriyle birlikte yazılır"), null);
        for (Group group : groups) {
            CheckBox choice = new CheckBox(this);
            choice.setText("   " + group.name);
            choice.setTextSize(16);
            choice.setPadding(dp(4), dp(5), dp(4), dp(5));
            choice.setTextColor(Design.text());
            content.addView(choice);
            groupBoxes.add(choice);
        }
        addPrimary(L10n.s("Devam et"), v -> {
            java.util.Set<String> characterIds = new java.util.HashSet<>();
            for (int i = 0; i < characterBoxes.size(); i++) {
                if (characterBoxes.get(i).isChecked()) characterIds.add(characters.get(i).id);
            }
            java.util.Set<String> groupIds = new java.util.HashSet<>();
            for (int i = 0; i < groupBoxes.size(); i++) {
                if (groupBoxes.get(i).isChecked()) groupIds.add(groups.get(i).id);
            }
            if (characterIds.isEmpty() && groupIds.isEmpty()) {
                Toast.makeText(this, L10n.s(L10n.s("En az bir karakter veya sohbet seç.")), Toast.LENGTH_SHORT).show();
                return;
            }
            pendingExportCharacters = characterIds;
            pendingExportGroups = groupIds;
            showExportFormatPicker();
        });
    }

    private void showExportFormatPicker() {
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(new Date());
        new AlertDialog.Builder(this)
                .setTitle(L10n.s(L10n.s("Yedek biçimi")))
                .setItems(new String[]{L10n.s(L10n.s("JSON (küçük, görsellersiz)")), L10n.s(L10n.s("ZIP (görsellerle)"))},
                        (d, which) -> {
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            if (which == 0) {
                                intent.setType("application/json");
                                intent.putExtra(Intent.EXTRA_TITLE, "localrp-yedek-" + stamp + ".json");
                                startActivityForResult(intent, BACKUP_EXPORT);
                            } else {
                                intent.setType("application/zip");
                                intent.putExtra(Intent.EXTRA_TITLE, "localrp-yedek-" + stamp + ".zip");
                                startActivityForResult(intent, BACKUP_EXPORT_MEDIA);
                            }
                        })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), (d, w) -> clearPendingSelection())
                .create()
                .show();
    }

    /** Streams the full local dataset (characters + groups + transcripts) to the chosen file. */
    private void exportBackup(android.net.Uri target) {
        new Thread(() -> {
            PersistenceCodec.Bundle bundle = buildFullBundle(null, null);
            clearPendingSelection();
            String payload = PersistenceCodec.encodeBundle(bundle);
            if (payload.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Yedek oluşturulamadı (veri okunamıyor).")), Toast.LENGTH_LONG).show());
                return;
            }
            try (java.io.OutputStream output = getContentResolver().openOutputStream(target)) {
                if (output == null) throw new IllegalStateException(L10n.s("Dosya açılamadı."));
                output.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.flush();
            } catch (Exception failure) {
                runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Yedek yazılamadı: ")) + failure.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            runOnUiThread(() -> Toast.makeText(this,
                    L10n.s(L10n.s("Yedek yazıldı · ")) + bundle.characters.size() + L10n.s(" karakter · ") + bundle.groups.size() + L10n.s(" sohbet"),
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    /**
     * Writes the v2 backup: one ZIP containing bundle.json (media paths as @ markers),
     * checksum.txt (SHA-256 of the JSON payload) and the referenced media bytes under
     * media/avatars/ and media/images/. Missing local files are skipped; their markers
     * degrade to no reference on import, mirroring v1's off-origin rule.
     */
    private void exportBackupMedia(android.net.Uri target) {
        new Thread(() -> {
            String avatarRoot, imageRoot;
            try {
                avatarRoot = new File(getFilesDir(), "avatars").getCanonicalPath();
                imageRoot = LocalImageEngine.INSTANCE.imagesDirectory(this).getCanonicalPath();
            } catch (java.io.IOException failure) {
                runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Yedek oluşturulamadı (veri okunamıyor).")), Toast.LENGTH_LONG).show());
                return;
            }
            PersistenceCodec.Bundle bundle = buildFullBundle(avatarRoot, imageRoot);
            clearPendingSelection();
            String payload = PersistenceCodec.encodeBundle(bundle);
            if (payload.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Yedek oluşturulamadı (veri okunamıyor).")), Toast.LENGTH_LONG).show());
                return;
            }
            java.util.LinkedHashSet<String> markers = new java.util.LinkedHashSet<>();
            for (CharacterProfile c : bundle.characters) {
                String marker = PersistenceCodec.toMediaRelative(c.avatarPath, avatarRoot, imageRoot);
                if (!marker.isEmpty()) markers.add(marker);
            }
            for (Persona persona : bundle.personas) {
                String marker = PersistenceCodec.toMediaRelative(persona.avatarPath, avatarRoot, imageRoot);
                if (!marker.isEmpty()) markers.add(marker);
            }
            for (List<Message> transcript : bundle.chats.values()) {
                for (Message m : transcript) {
                    String marker = PersistenceCodec.toMediaRelative(m.image, avatarRoot, imageRoot);
                    if (!marker.isEmpty()) markers.add(marker);
                }
            }
            byte[] json = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String checksum = PersistenceCodec.sha256Hex(json);
            // Buffer the referenced files so the media checksum covers exact bytes,
            // and identical entry names can never duplicate inside the ZIP.
            java.util.SortedMap<String, byte[]> packedMedia = new java.util.TreeMap<>();
            for (String marker : markers) {
                String sourcePath = PersistenceCodec.resolveMediaPath(marker, avatarRoot, imageRoot);
                File source = new File(sourcePath);
                if (!source.isFile()) continue; // deleted on this device; import will degrade
                byte[] fileBytes;
                try (java.io.FileInputStream fileInput = new java.io.FileInputStream(source);
                     java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream()) {
                    byte[] chunk = new byte[8192];
                    int read;
                    while ((read = fileInput.read(chunk)) >= 0) sink.write(chunk, 0, read);
                    fileBytes = sink.toByteArray();
                } catch (java.io.IOException failure) {
                    continue;
                }
                String entry = (marker.startsWith("@avatars/") ? "media/avatars/" : "media/images/") + source.getName();
                packedMedia.put(entry, fileBytes);
            }
            String mediaChecksum = PersistenceCodec.mediaDigestHex(packedMedia);
            try (java.io.OutputStream output = getContentResolver().openOutputStream(target);
                 java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(output)) {
                if (output == null) throw new IllegalStateException(L10n.s("Dosya açılamadı."));
                zip.putNextEntry(new java.util.zip.ZipEntry("bundle.json"));
                zip.write(json);
                zip.closeEntry();
                zip.putNextEntry(new java.util.zip.ZipEntry("checksum.txt"));
                zip.write((checksum + "\n" + mediaChecksum + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
                for (java.util.Map.Entry<String, byte[]> packed : packedMedia.entrySet()) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(packed.getKey()));
                    zip.write(packed.getValue());
                    zip.closeEntry();
                }
                zip.flush();
            } catch (Exception failure) {
                runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Yedek yazılamadı: ")) + failure.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            int characterCount = bundle.characters.size();
            int groupCount = bundle.groups.size();
            int packedCount = packedMedia.size();
            runOnUiThread(() -> Toast.makeText(this,
                    L10n.s(L10n.s("Yedek yazıldı · ")) + characterCount + L10n.s(" karakter · ") + groupCount
                            + L10n.s(" sohbet · ") + packedCount + L10n.s(L10n.s(" medya dosyası")),
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    private void importBackup(android.net.Uri source) {
        new Thread(() -> {
            byte[] bytes;
            try (java.io.InputStream input = getContentResolver().openInputStream(source)) {
                if (input == null) throw new IllegalStateException(L10n.s("Dosya açılamadı."));
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = input.read(chunk)) >= 0) {
                    buffer.write(chunk, 0, read);
                    if (buffer.size() > 256L * 1024 * 1024) { // sanity cap; real backups stay far smaller
                        throw new IllegalStateException(L10n.s("Dosya beklenenden çok büyük."));
                    }
                }
                bytes = buffer.toByteArray();
            } catch (Exception failure) {
                runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Yedek okunamadı: ")) + failure.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            // ZIP bundles (backup v2) start with the "PK" magic; JSON v1 files cannot.
            if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
                importBackupZip(bytes);
                return;
            }
            String raw = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            PersistenceCodec.Bundle bundle = PersistenceCodec.decodeBundle(raw);
            if (bundle == null) {
                runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Bu dosya geçerli bir LocalRP yedeği değil.")), Toast.LENGTH_LONG).show());
                return;
            }
            // Backups carry paths, not image bytes. A path that does not exist on THIS
            // device (cross-device restore) degrades gracefully instead of shipping
            // references to files that can never appear later.
            try {
                String filesRoot = getFilesDir().getCanonicalPath();
                for (CharacterProfile c : bundle.characters) {
                    if (c.avatarPath != null && !c.avatarPath.isEmpty()
                            && !new File(c.avatarPath).getCanonicalPath().startsWith(filesRoot)) {
                        c.avatarPath = "";
                    }
                }
                for (Persona persona : bundle.personas) {
                    if (persona.avatarPath != null && !persona.avatarPath.isEmpty()
                            && !new File(persona.avatarPath).getCanonicalPath().startsWith(filesRoot)) {
                        persona.avatarPath = "";
                    }
                }
                for (List<Message> transcript : bundle.chats.values()) {
                    for (Message m : transcript) {
                        if (m.hasImage()
                                && !new File(m.image).getCanonicalPath().startsWith(filesRoot)) {
                            m.image = "";
                        }
                    }
                }
            } catch (java.io.IOException ignored) { }
            applyImportedBundle(bundle);
        }).start();
    }

    /** Restores a decoded bundle and reports the outcome on the UI thread (both import paths share this). */
    private void applyImportedBundle(PersistenceCodec.Bundle bundle) {
        final boolean restored = store.restoreBundle(bundle.characters, bundle.groups, bundle.chats,
                bundle.lorebooks, bundle.personas, bundle.defaultPersonaId, bundle.chatPersonas);
        runOnUiThread(() -> {
            // The restore thread may finish after the user left the app; touching a
            // dead activity's window crashes (chy bug report: geri yükleme çökmesi).
            if (!uiAlive()) return;
            if (!restored) {
                Toast.makeText(this, L10n.s(L10n.s("Geri yükleme yapılamadı: mevcut veriler okunamıyor.")), Toast.LENGTH_LONG).show();
                if (!store.integrityIssues().isEmpty()) reportIntegrityIssues();
                return;
            }
            Toast.makeText(this,
                    L10n.s(L10n.s("Geri yüklendi · ")) + bundle.characters.size() + L10n.s(" karakter · ") + bundle.groups.size() + L10n.s(" sohbet"),
                    Toast.LENGTH_LONG).show();
            showHome();
        });
    }

    /**
     * Imports a backup v2 ZIP bundle: verifies the SHA-256 checksum, copies packaged
     * media into the local avatar/image directories, then restores through the normal
     * merge path. Unsafe or unknown entries are ignored; an @ marker without packaged
     * bytes degrades to no reference (the same rule as off-origin v1 paths).
     */
    private void importBackupZip(byte[] zipBytes) {
        String payload = null;
        String checksumLine = null;
        java.util.Map<String, byte[]> media = new java.util.HashMap<>();
        try (java.io.InputStream input = new java.io.ByteArrayInputStream(zipBytes);
             java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(input, java.nio.charset.StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] bytes = null;
                if ("bundle.json".equals(name)) {
                    bytes = readZipEntry(zip);
                    payload = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                } else if ("checksum.txt".equals(name)) {
                    checksumLine = new String(readZipEntry(zip), java.nio.charset.StandardCharsets.UTF_8);
                } else if (name.startsWith("media/avatars/") && isSingleSegment(name.substring("media/avatars/".length()))) {
                    media.put(name, readZipEntry(zip));
                } else if (name.startsWith("media/images/") && isSingleSegment(name.substring("media/images/".length()))) {
                    media.put(name, readZipEntry(zip));
                }
                zip.closeEntry();
            }
        } catch (Exception failure) {
            runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Yedek okunamadı: ")) + failure.getMessage(), Toast.LENGTH_LONG).show());
            return;
        }
        if (payload == null || checksumLine == null) {
            runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Bu dosya geçerli bir LocalRP yedeği değil.")), Toast.LENGTH_LONG).show());
            return;
        }
        // Two lines: first the JSON payload digest, then the packaged-media digest.
        String[] checksumLines = checksumLine.trim().split("\\r?\\n");
        if (checksumLines.length < 2
                || !checksumLines[0].trim().matches("[0-9a-f]{64}")
                || !checksumLines[1].trim().matches("[0-9a-f]{64}")) {
            runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Bu dosya geçerli bir LocalRP yedeği değil.")), Toast.LENGTH_LONG).show());
            return;
        }
        java.util.SortedMap<String, byte[]> sortedMedia = new java.util.TreeMap<>(media);
        String computed = PersistenceCodec.sha256Hex(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String computedMedia = PersistenceCodec.mediaDigestHex(sortedMedia);
        if (!computed.equals(checksumLines[0].trim())
                || !computedMedia.equals(checksumLines[1].trim())
                || computedMedia.isEmpty()) {
            runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Yedek doğrulamadı: sağlama farkı.")), Toast.LENGTH_LONG).show());
            return;
        }
        PersistenceCodec.Bundle bundle = PersistenceCodec.decodeBundle(payload);
        if (bundle == null) {
            runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Bu dosya geçerli bir LocalRP yedeği değil.")), Toast.LENGTH_LONG).show());
            return;
        }
        try {
            String avatarRoot = new File(getFilesDir(), "avatars").getCanonicalPath();
            String imageRoot = LocalImageEngine.INSTANCE.imagesDirectory(this).getCanonicalPath();
            for (CharacterProfile c : bundle.characters) {
                if (c.avatarPath != null && c.avatarPath.startsWith("@")) {
                    c.avatarPath = placeBundleMedia(c.avatarPath, avatarRoot, imageRoot, media);
                }
            }
            for (Persona persona : bundle.personas) {
                if (persona.avatarPath != null && persona.avatarPath.startsWith("@")) {
                    persona.avatarPath = placeBundleMedia(persona.avatarPath, avatarRoot, imageRoot, media);
                }
            }
            for (List<Message> transcript : bundle.chats.values()) {
                for (Message m : transcript) {
                    if (m.hasImage() && m.image.startsWith("@")) {
                        m.image = placeBundleMedia(m.image, avatarRoot, imageRoot, media);
                    }
                }
            }
        } catch (java.io.IOException failure) {
            runOnUiThread(() -> Toast.makeText(this, L10n.s(L10n.s("Yedek okunamadı: ")) + failure.getMessage(), Toast.LENGTH_LONG).show());
            return;
        }
        applyImportedBundle(bundle);
    }

    /** Maps one @ marker to a local file, unpackaging its media bytes when missing. */
    private String placeBundleMedia(String marker, String avatarRoot, String imageRoot,
                                    java.util.Map<String, byte[]> media) {
        String localPath = PersistenceCodec.resolveMediaPath(marker, avatarRoot, imageRoot);
        if (localPath.isEmpty()) return "";
        File target = new File(localPath);
        String entry = (marker.startsWith("@avatars/") ? "media/avatars/" : "media/images/") + target.getName();
        byte[] bytes = media.get(entry);
        if (bytes == null) return target.isFile() ? localPath : "";
        File directory = target.getParentFile();
        if (directory == null || (!directory.exists() && !directory.mkdirs())) return "";
        // Packaged bytes are authoritative for their own path: the bundle was lifted
        // from a device where this path meant exactly this image.
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(target)) {
            output.write(bytes);
        } catch (java.io.IOException failure) {
            return "";
        }
        return localPath;
    }

    /** A media entry name must be one plain path segment; anything else is ignored. */
    private static boolean isSingleSegment(String name) {
        return !name.isEmpty() && name.indexOf('/') < 0 && name.indexOf('\\') < 0
                && name.indexOf('\0') < 0 && !".".equals(name) && !"..".equals(name);
    }

    /** Reads one ZIP entry, refusing anything that decompresses beyond a sane cap. */
    private static byte[] readZipEntry(java.util.zip.ZipInputStream zip) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = zip.read(chunk)) >= 0) {
            buffer.write(chunk, 0, read);
            if (buffer.size() > 64L * 1024 * 1024) { // a 512px PNG lands far below this
                throw new IllegalStateException(L10n.s("Yedek girdisi beklenenden çok büyük."));
            }
        }
        return buffer.toByteArray();
    }

    private void loadSelectedModel() {
        if (selectedModel == null) { showModels(); return; }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) return;
        modelLoading = true;
        modelStatus = L10n.s("Yükleme başlatılıyor…");
        if ("settings".equals(currentTab)) showModels();
        LocalAiRuntime.INSTANCE.loadModel(this, selectedModel, new LocalAiRuntime.ModelListener() {
            @Override public void onStatus(String message) {
                if (!uiAlive()) return;
                modelStatus = message;
                if ("settings".equals(currentTab) && modelStatusView != null) modelStatusView.setText(message);
                refreshVisibleChat(false);
            }
            @Override public void onLoaded(String fileName, long bytes) {
                if (!uiAlive()) return;
                modelLoading = false;
                selectedModelName = fileName;
                contextChars.clear();
                chatMetrics.clear();
                modelStatus = L10n.s(L10n.s("Hazır · ")) + String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0) + L10n.s(L10n.s(" · çevrimdışı"));
                if ("settings".equals(currentTab)) showModels();
                Toast.makeText(MainActivity.this, L10n.s(L10n.s("Model hazır.")), Toast.LENGTH_SHORT).show();
                refreshVisibleChat(true);
            }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                modelLoading = false;
                modelStatus = L10n.s(L10n.s("Yüklenemedi: ")) + message;
                if ("settings".equals(currentTab)) showModels();
                refreshVisibleChat(false);
                new AlertDialog.Builder(MainActivity.this).setTitle(L10n.s(L10n.s("Model yüklenemedi")))
                        .setMessage(message).setPositiveButton(L10n.s(L10n.s("Tamam")), null).show();
            }
        });
    }

    private void refreshVisibleChat(boolean allowAutoGenerate) {
        if (!"chat".equals(currentTab) || visibleChatId == null) return;
        Group group = store.group(visibleChatId);
        if (group == null) return;
        if (pageSubtitleView != null) pageSubtitleView.setText(chatSubtitle(group));
        updateChatControls(group);
        if (allowAutoGenerate) maybeAutoGenerate(group);
    }

    private void showStoredModels() {
        List<File> files = LocalAiRuntime.INSTANCE.storedModels(this);
        if (files.isEmpty()) {
            Toast.makeText(this, L10n.s(L10n.s("Cihazda kayıtlı model kopyası yok.")), Toast.LENGTH_SHORT).show();
            showModels();
            return;
        }
        String loadedPath = LocalAiRuntime.INSTANCE.loadedModelPath();
        String[] labels = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            boolean loaded = file.getAbsolutePath().equals(loadedPath);
            int verdict = fitVerdict(file.length());
            String verdictTag = verdict < 0 ? ""
                    : verdict == 0 ? L10n.s(L10n.s("  ·  sığar"))
                    : verdict == 1 ? L10n.s(L10n.s("  ·  sınırda"))
                    : L10n.s("  ·  zor");
            labels[i] = file.getName() + L10n.s("  ·  ") + humanSize(file.length()) + verdictTag
                    + (loaded ? L10n.s(L10n.s("  ·  yüklü")) : "");
        }
        new AlertDialog.Builder(this).setTitle(L10n.s(L10n.s("Model kopyaları")))
                .setItems(labels, (dialog, which) -> showStoredModelProfile(files.get(which)))
                .setNegativeButton(L10n.s(L10n.s("Kapat")), null).show();
    }

    /**
     * Model profile (DeepSeek item 11): RAM verdict, load action and the
     * launch-time auto-load pin for one stored model copy.
     */
    private void showStoredModelProfile(File file) {
        SharedPreferences prefs = prefs();
        int verdict = fitVerdict(file.length());
        String verdictText = verdict < 0 ? L10n.s(L10n.s("RAM bilgisi okunamadı; gösterge bilinmiyor."))
                : verdict == 0 ? L10n.s(L10n.s("Bu cihazda rahat sığar."))
                : verdict == 1 ? L10n.s(L10n.s("Sınırda: çalışır ama bellek dar."))
                : L10n.s(L10n.s("Bu cihazda zor; büyük ihtimalle açılmaz."));
        String autoFile = prefs.getString("auto_load_file", null);
        boolean auto = prefs.getBoolean("auto_load_model", false) && file.getName().equals(autoFile);
        String detail = L10n.s("Boyut: ") + humanSize(file.length()) + "\n"
                + L10n.s(L10n.s("Tahmini bellek kullanımı ~1,3× dosya.\n"))
                + verdictText;
        new AlertDialog.Builder(this).setTitle(file.getName())
                .setMessage(detail)
                .setItems(new String[]{
                        L10n.s(L10n.s("Modeli yükle")),
                        auto ? L10n.s(L10n.s("Açılışta otomatik yükleme: AÇIK (kapat)"))
                                : L10n.s(L10n.s("Açılışta otomatik yükle")),
                        L10n.s(L10n.s("Kopyayı sil"))}, (d, choice) -> {
                    if (choice == 0) {
                        loadStoredModelFile(file);
                    } else if (choice == 1) {
                        if (auto) {
                            prefs.edit().putBoolean("auto_load_model", false).apply();
                            Toast.makeText(this, L10n.s(L10n.s("Açılışta otomatik yükleme kapatıldı.")), Toast.LENGTH_SHORT).show();
                        } else {
                            prefs.edit().putString("auto_load_file", file.getName())
                                    .putBoolean("auto_load_model", true).apply();
                            Toast.makeText(this, file.getName() + L10n.s(L10n.s(" açılışta otomatik yüklenecek.")), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        confirm(file.getName() + " silinsin mi?", L10n.s("Sil"), () -> {
                            if (LocalAiRuntime.INSTANCE.deleteStoredModel(this, file)) {
                                // Only a successful deletion may drop the auto-load pin;
                                // a failed delete keeps the file and the user's choice.
                                if (file.getName().equals(prefs.getString("auto_load_file", null))) {
                                    prefs.edit().putBoolean("auto_load_model", false).apply();
                                }
                                Toast.makeText(this, L10n.s(L10n.s("Model kopyası silindi.")), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, L10n.s(L10n.s("Model dosyası silinemedi.")), Toast.LENGTH_SHORT).show();
                            }
                            showModels();
                        });
                    }
                })
                .setNegativeButton(L10n.s(L10n.s("Kapat")), null).show();
    }

    /** Launch-time auto-load (DeepSeek item 11): silently restores the pinned model. */
    private void maybeAutoLoadModelAtStartup() {
        if (isFinishing() || isDestroyed()) return;
        SharedPreferences prefs = prefs();
        if (!prefs.getBoolean("auto_load_model", false)) return;
        String wanted = prefs.getString("auto_load_file", null);
        if (wanted == null || wanted.isEmpty()) {
            // A pin without a file cannot exist: drop the dangling flag.
            prefs.edit().putBoolean("auto_load_model", false).apply();
            return;
        }
        // Busy-by-state: leave the flag alone and retry on the next launch.
        if (LocalAiRuntime.INSTANCE.isModelReady() || LocalAiRuntime.INSTANCE.isModelLoading() || modelLoading) return;
        for (File file : LocalAiRuntime.INSTANCE.storedModels(this)) {
            if (file.getName().equals(wanted)) {
                Toast.makeText(this, L10n.s(L10n.s("Açılışta otomatik yükleniyor…")), Toast.LENGTH_SHORT).show();
                modelStatus = L10n.s(L10n.s("Açılışta otomatik yükleniyor…"));
                loadStoredModelFile(file);
                return;
            }
        }
        // The pinned file is gone (deleted): stop promising an impossible load.
        prefs.edit().putBoolean("auto_load_model", false).apply();
    }

    private String humanSize(long bytes) {
        if (bytes >= 1_000_000_000L) return String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0);
        if (bytes >= 1_000_000L) return String.format(Locale.getDefault(), "%.0f MB", bytes / 1_000_000.0);
        return String.format(Locale.getDefault(), "%.0f KB", bytes / 1_000.0);
    }

    private void unloadModel() {
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) return;
        modelLoading = true;
        modelStatus = L10n.s("Model bellekten kaldırılıyor…");
        if ("settings".equals(currentTab)) showModels();
        LocalAiRuntime.INSTANCE.unload(new LocalAiRuntime.ModelListener() {
            @Override public void onStatus(String message) {
                if (!uiAlive()) return;
                modelStatus = message;
                if ("settings".equals(currentTab) && modelStatusView != null) modelStatusView.setText(message);
                // Success is decided by runtime state, not by parsing the status text.
                if (!LocalAiRuntime.INSTANCE.isModelReady() && LocalAiRuntime.INSTANCE.loadedModelPath() == null) {
                    modelLoading = false;
                    contextChars.clear();
                    chatMetrics.clear();
                    if ("settings".equals(currentTab)) showModels();
                }
                refreshVisibleChat(false);
            }
            @Override public void onLoaded(String name, long bytes) { }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                modelLoading = false;
                modelStatus = L10n.s("Hata: ") + message;
                if ("settings".equals(currentTab)) showModels();
                refreshVisibleChat(false);
            }
        });
    }

    private String queryName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return L10n.s("Seçilen model dosyası");
    }

    private String buildRoleplayPrompt(Group group, CharacterProfile speaker, boolean continuation) {
        return buildRoleplayPrompt(group, speaker, continuation, null);
    }

    /** Steer-aware variant (v0.19.22 comfort tools); {@code steer} may be null. */
    private String buildRoleplayPrompt(Group group, CharacterProfile speaker, boolean continuation, String steer) {
        return buildRoleplayPrompt(group, speaker, continuation,
                LocalAiRuntime.INSTANCE.hasConversation(group.id), chatHistoryDepth(group.id), steer);
    }

    /** Provider mode (DeepSeek item 14/B) assembles a history-free system prompt:
     *  history travels as real chat turns, so {@code historyDepth} is 0 and the
     *  engine's shared-context flag is forced fresh. */
    private String buildRoleplayPrompt(Group group, CharacterProfile speaker, boolean continuation,
                                       boolean continuingFlag, int historyDepth) {
        return buildRoleplayPrompt(group, speaker, continuation, continuingFlag, historyDepth, null);
    }

    private String buildRoleplayPrompt(Group group, CharacterProfile speaker, boolean continuation,
                                       boolean continuingFlag, int historyDepth, String steer) {
        // Prompt assembly lives in the pure, unit-tested RoleplayPrompt; MainActivity only
        // resolves the Android-backed inputs (store lookups, prefs, engine context flag).
        boolean english = L10n.english;
        String instructions = resolveGeneralInstructions(english);
        String themeId = chatThemeId(group.id);
        // v0.19.16: the chat's own persona wins; otherwise the global default identity.
        String[] personaText = store.personaTextForChat(group.id);
        return RoleplayPrompt.build(
                group.name,
                speaker,
                group.characterIds,
                id -> store.character(id),
                store.messages(group.id),
                continuingFlag,
                continuation,
                instructions,
                aiThemeName(themeId),
                aiThemeInstruction(themeId),
                historyDepth,
                historyCharBudget(),
                personaText,
                english,
                store.lore(group.id),
                steer);
    }

    /** System prompt + transcript turns for the online provider (DeepSeek item 14/B). */
    private List<OpenAiCompat.Turn> buildProviderTurns(Group group, CharacterProfile speaker, boolean continuation) {
        return buildProviderTurns(group, speaker, continuation, null);
    }

    private List<OpenAiCompat.Turn> buildProviderTurns(Group group, CharacterProfile speaker,
                                                       boolean continuation, String steer) {
        boolean english = L10n.english;
        List<Message> history = store.messages(group.id);
        String continuationPrompt = english
                ? "The scene continues; react naturally to this moment and speak as " + speaker.name + "."
                : "Sahne devam ediyor; bu tur " + speaker.name + L10n.s(" olarak doğal bir tepki ver.");
        String finalUserText;
        if (!history.isEmpty() && history.get(history.size() - 1).user) {
            Message trailing = history.get(history.size() - 1);
            finalUserText = trailing.text;
            if (finalUserText == null || finalUserText.isEmpty()) {
                // Image-only trailing note keeps the same placeholder the transcript uses.
                finalUserText = trailing.hasImage() ? L10n.s("[görsel]") : continuationPrompt;
            }
        } else {
            finalUserText = continuationPrompt;
        }
        return OpenAiCompat.assembleTurns(
                buildRoleplayPrompt(group, speaker, continuation, false, 0, steer), history, finalUserText, providerTurnDepth());
    }

    /** General AI instruction: shipped default (chosen by language) or the user's custom text. */
    private String resolveGeneralInstructions(boolean english) {
        String mode = prefs().getString("general_prompt_source", "default");
        if ("custom".equals(mode)) return clip(prefs().getString("ai_instructions", ""), 600);
        return clip(english ? DEFAULT_AI_INSTRUCTIONS_EN : DEFAULT_AI_INSTRUCTIONS, 600);
    }

    /** System prompt: engine default / shipped TR / shipped EN / user's custom text. */
    private void applySystemPrompt() {
        String mode = prefs().getString("sys_prompt_source", "engine");
        String text;
        if ("tr".equals(mode)) text = SYS_PROMPT_TR;
        else if ("en".equals(mode)) text = SYS_PROMPT_EN;
        else if ("custom".equals(mode)) text = prefs().getString("custom_sys_prompt", null);
        else text = null;
        LocalAiRuntime.INSTANCE.setCustomSystemPrompt(text);
    }

    private String aiThemeName(String id) {
        if ("custom".equals(id)) return L10n.s(clip(prefs().getString("ai_theme_custom_name", ""), 60).isEmpty()
                ? L10n.s("Özel tema") : prefs().getString("ai_theme_custom_name", ""));
        for (String[] theme : AI_THEMES) if (theme[0].equals(id)) return L10n.s(theme[1]);
        return L10n.s(AI_THEMES[0][1]);
    }

    private String aiThemeInstruction(String id) {
        if ("custom".equals(id)) return L10n.s(prefs().getString("ai_theme_custom_instruction", ""));
        for (String[] theme : AI_THEMES) if (theme[0].equals(id)) return L10n.s(theme[2]);
        return L10n.s(AI_THEMES[0][2]);
    }

    private String aiThemeDescription(String id) {
        String instruction = aiThemeInstruction(id);
        return instruction.isEmpty()
                ? L10n.s("Tema kısıtlaması yok; karakterin doğal dünyası ve sahnenin tonu korunur.")
                : instruction;
    }

    private void showThemePicker() {
        if (generatingChatId != null) {
            Toast.makeText(this, L10n.s(L10n.s("Yanıt üretilirken tema değiştirilemez.")), Toast.LENGTH_SHORT).show();
            return;
        }
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s(L10n.s("Model yüklenirken tema değiştirilemez.")), Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) {
            Toast.makeText(this, L10n.s(L10n.s("Motor testi sürerken tema değiştirilemez.")), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[AI_THEMES.length + 1];
        String current = prefs().getString("ai_theme", DEFAULT_AI_THEME);
        for (int i = 0; i < AI_THEMES.length; i++) {
            labels[i] = L10n.s(AI_THEMES[i][1]) + (AI_THEMES[i][0].equals(current) ? "  ✓" : "");
        }
        labels[AI_THEMES.length] = L10n.s(L10n.s("Özel tema (kendin yaz)")) + ("custom".equals(current) ? "  ✓" : "");
        new AlertDialog.Builder(this).setTitle(L10n.s(L10n.s("AI sahne teması")))
                .setItems(labels, (dialog, which) -> {
                    if (which == AI_THEMES.length) {
                        showCustomThemeEditor();
                        return;
                    }
                    String selectedId = AI_THEMES[which][0];
                    if (selectedId.equals(prefs().getString("ai_theme", DEFAULT_AI_THEME))) return;
                    prefs().edit().putString("ai_theme", selectedId).apply();
                    contextChars.clear();
                    chatMetrics.clear();
                    LocalAiRuntime.INSTANCE.resetConversation(null);
                    Toast.makeText(this, AI_THEMES[which][1] + L10n.s(L10n.s(" teması seçildi.")), Toast.LENGTH_SHORT).show();
                    showSettings();
                })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).show();
    }

    /** Full editor for the user's own AI theme: a name plus a prompt instruction. */
    private void showCustomThemeEditor() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(18), dp(22), 0);
        EditText name = new EditText(this);
        name.setHint(L10n.s(L10n.s("Tema adı")));
        name.setText(prefs().getString("ai_theme_custom_name", ""));
        name.setSingleLine(true);
        name.setTextColor(Design.text());
        name.setHintTextColor(Design.muted());
        box.addView(name);
        EditText instruction = new EditText(this);
        instruction.setHint(L10n.s(L10n.s("Sahne talimatı (prompta girer)")));
        instruction.setText(prefs().getString("ai_theme_custom_instruction", ""));
        instruction.setMinLines(3);
        instruction.setGravity(Gravity.TOP);
        instruction.setTextColor(Design.text());
        instruction.setHintTextColor(Design.muted());
        box.addView(instruction);
        new AlertDialog.Builder(this)
                .setTitle(L10n.s(L10n.s("Özel tema")))
                .setView(box)
                .setPositiveButton(L10n.s(L10n.s("Özel temayı kaydet ve uygula")), (d, w) -> {
                    String themeName = name.getText().toString().trim();
                    if (themeName.isEmpty()) {
                        Toast.makeText(this, L10n.s(L10n.s("Bir tema adı gerekli.")), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs().edit()
                            .putString("ai_theme_custom_name", themeName)
                            .putString("ai_theme_custom_instruction", instruction.getText().toString().trim())
                            .putString("ai_theme", "custom")
                            .apply();
                    contextChars.clear();
                    chatMetrics.clear();
                    LocalAiRuntime.INSTANCE.resetConversation(null);
                    Toast.makeText(this, themeName + L10n.s(L10n.s(" teması uygulandı.")), Toast.LENGTH_SHORT).show();
                    showSettings();
                })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null)
                .show();
    }

    /** Draft text of an in-flight generation; guaranteed against process death (task G0/G11). */
    private void saveGenerationDraft(String chatId, String speaker, String rawText) {
        if (rawText == null || rawText.isEmpty()) return;
        try {
            JSONObject draft = new JSONObject();
            draft.put("speaker", speaker == null ? "" : speaker);
            draft.put("text", rawText);
            prefs().edit().putString("gen_draft_" + chatId, draft.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private void clearGenerationDraft(String chatId) {
        prefs().edit().remove("gen_draft_" + chatId).apply();
    }

    /**
     * Crash recovery (task G0): a leftover draft means the generation never reached
     * finish(); restore it as a regular message exactly once and delete the draft.
     * Runs once at startup, before any screen is rendered.
     */
    private void restoreInterruptedDrafts() {
        SharedPreferences prefs = prefs();
        java.util.Map<String, ?> all = prefs.getAll();
        for (String key : all.keySet()) {
            if (!key.startsWith("gen_draft_")) continue;
            String chatId = key.substring("gen_draft_".length());
            prefs.edit().remove(key).apply();
            try {
                JSONObject draft = new JSONObject(String.valueOf(all.get(key)));
                String speaker = draft.optString("speaker", "");
                String text = ResponseCleanup.clean(draft.optString("text", ""), speaker);
                if (store.group(chatId) == null || speaker.isEmpty() || text.isEmpty()) continue;
                Message last = store.lastMessage(chatId);
                boolean alreadySaved = last != null && !last.user
                        && last.speaker.equals(speaker) && last.text.equals(text);
                if (!alreadySaved) {
                    store.addMessage(chatId, new Message(speaker, text, false, System.currentTimeMillis()));
                }
            } catch (Exception broken) {
                // A corrupt draft key was already removed; no risk to stored chat data.
            }
        }
    }

    private String cleanResponse(String raw, String speakerName) {
        // Single shared cleanup (task G0): the identical helper also renders the
        // streaming interim balloon so the echoed label never becomes visible.
        return ResponseCleanup.clean(raw, speakerName);
    }

    private String searchText(String value) {
        if (value == null) return "";
        String folded = value.toLowerCase(Locale.ROOT).replace("\u0307", "");
        return folded
                .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
    }

    private String oneLine(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max - 1)) + "…";
    }

    private String clip(String value, int max) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max - 1)) + "…";
    }

    @Override public void onBackPressed() {
        if (backAction != null) backAction.run();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (isFinishing()) {
            visibleChatId = null;
            stopImageGeneratingTick();
            if (LocalImageEngine.INSTANCE.isGenerating()) {
                LocalImageEngine.INSTANCE.stopGeneration();
            }
            LocalAiRuntime.INSTANCE.stopGeneration();
            // An online turn must not keep its SSE reader running past teardown;
            // its callbacks would resurrect this dead activity's views (chy bug report).
            if (providerClient != null) providerClient.stop();
            LocalAiRuntime.INSTANCE.resetConversation(null);
            hideUndoBar(false); // teardown without firing the file-delete finalizer
        }
        super.onDestroy();
    }

    private boolean uiAlive() {
        return !isFinishing() && !isDestroyed();
    }

    private void openChat(String groupId) {
        Group group = store.group(groupId);
        if (group == null) showHome(); else showChat(group);
    }

    private void setPage(String title, String subtitle, String tab) {
        // Same-page re-render (lore save, settings toggle, model status refresh...):
        // rebuild only the content list and keep header + nav + window. A full
        // setContentView here re-created the whole view tree on every field change
        // and read as a flash on slower phones (chy bug report, follow-up to the
        // fade removal in v0.19.13).
        if (pageRoot != null && content != null
                && title.equals(pageTitle) && subtitle.equals(pageSubtitle)
                && tab.equals(currentTab) && (backAction != null) == pageHadBack) {
            if (pageBackButton != null) pageBackButton.setOnClickListener(v -> backAction.run());
            content.removeAllViews();
            return;
        }
        setPage(title, subtitle, tab, false);
    }

    private void setPage(String title, String subtitle, String tab, boolean chat) {
        currentTab = tab;
        if (chat) pageTitle = null; // chat pages always rebuild; never match a stale non-chat anchor
        dismissUndoBar(); // leaving a page finalizes any pending reversible deletion
        if (chatInput != null && chatInputChatId != null) {
            String draft = chatInput.getText().toString();
            if (draft.trim().isEmpty()) chatDrafts.remove(chatInputChatId);
            else chatDrafts.put(chatInputChatId, draft);
        }
        chatInput = null;
        chatInputChatId = null;
        if (!chat) visibleChatId = null;
        if (!chat) recycleChatImageCache();
        pageRoot = new LinearLayout(this);
        pageRoot.setOrientation(LinearLayout.VERTICAL);
        pageRoot.setBackgroundColor(Design.pageBg());
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            // Edge-to-edge insets (GUI review item 3): the keyboard's IME inset must
            // count too — adjustResize alone no longer lifts the composer above the
            // keyboard on Android 15, so pad to max(systemBars, ime).
            pageRoot.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                android.graphics.Insets ime = insets.getInsets(android.view.WindowInsets.Type.ime());
                int top = bars.top;
                int bottom = Math.max(bars.bottom, ime.bottom);
                if (top > 0 || bottom > 0) view.setPadding(0, top, 0, bottom);
                return insets;
            });
        }
        if (chat) {
            LinearLayout header = new LinearLayout(this);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(14), dp(10), dp(18), dp(10));
            if (backAction != null) {
                TextView back = label("‹", 22, Design.text());
                back.setBackground(rounded(Design.cardBg(), Design.cardBg(), 22));
                back.setGravity(Gravity.CENTER);
                back.setOnClickListener(v -> backAction.run());
                back.setContentDescription(L10n.s(L10n.s("Geri")));
                header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
            }
            // cAI-style leading avatar on the chat header; ids stay legible next to it.
            View selfAvatar = Avatar.forIdentity(this, title, chatHeaderAvatarPath, 38);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(38), dp(38));
            avatarParams.setMargins(dp(9), 0, 0, 0);
            header.addView(selfAvatar, avatarParams);
            LinearLayout titles = new LinearLayout(this);
            titles.setOrientation(LinearLayout.VERTICAL);
            TextView heading = label(title, 18, Design.text());
            heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            heading.setSingleLine(true);
            heading.setEllipsize(TextUtils.TruncateAt.END);
            titles.addView(heading);
            TextView sub = label(subtitle, 12, Design.muted());
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setPadding(0, dp(2), 0, 0);
            titles.addView(sub);
            pageSubtitleView = sub;
            header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            pageRoot.addView(header, fullWidth());
        } else {
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            header.setPadding(dp(22), dp(18), dp(22), dp(10));
            if (backAction != null) {
                TextView back = label("‹", 22, Design.text());
                back.setBackground(rounded(Design.cardBg(), Design.cardBg(), 22));
                back.setGravity(Gravity.CENTER);
                back.setOnClickListener(v -> backAction.run());
                back.setContentDescription(L10n.s(L10n.s("Geri")));
                header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
                pageBackButton = back;
                LinearLayout headerTexts = new LinearLayout(this);
                headerTexts.setOrientation(LinearLayout.VERTICAL);
                headerTexts.setPadding(dp(14), 0, dp(4), 0);
                TextView heading = label(title, 22, Design.text());
                heading.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
                heading.setSingleLine(true);
                heading.setEllipsize(TextUtils.TruncateAt.END);
                headerTexts.addView(heading);
                TextView sub = label(subtitle, 13, Design.muted());
                sub.setPadding(0, dp(3), 0, 0);
                headerTexts.addView(sub);
                pageSubtitleView = sub;
                header.addView(headerTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            } else {
                TextView heading = label(title, 26, Design.text());
                heading.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
                heading.setLetterSpacing(.01f);
                header.addView(heading);
                TextView sub = label(subtitle, 13, Design.muted());
                sub.setPadding(0, dp(4), 0, 0);
                header.addView(sub);
                pageSubtitleView = sub;
                pageBackButton = null;
            }
            pageRoot.addView(header, fullWidth());
            pageTitle = title;
            pageSubtitle = subtitle;
            pageHadBack = backAction != null;
        }

        if (chat) {
            chatEmptyState = null;
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            chatStatusView = label("", 12, Design.muted());
            chatStatusView.setPadding(dp(16), 0, dp(16), dp(4));
            body.addView(chatStatusView, fullWidth());

            chatScroll = new ScrollView(this);
            chatScroll.setFillViewport(true);
            chatScroll.setOnScrollChangeListener((view, scrollX, scrollY, oldX, oldY) -> {
                View child = chatScroll.getChildAt(0);
                autoScroll = child == null ||
                        chatScroll.getScrollY() + chatScroll.getHeight() >= child.getBottom() - dp(120);
            });
            content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(20), dp(6), dp(20), dp(12));
            chatScroll.addView(content);
            body.addView(chatScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

            chatComposer = new LinearLayout(this);
            chatComposer.setOrientation(LinearLayout.VERTICAL);
            chatComposer.setPadding(dp(14), dp(8), dp(14), dp(8));
            chatComposer.setBackgroundColor(Design.pageBg());
            body.addView(chatComposer, fullWidth());
            pageRoot.addView(body, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        } else {
            chatStatusView = null;
            chatComposer = null;
            chatScroll = null;
            chatActionButton = null;
            chatContinueButton = null;
            chatSpeakerView = null;
            chatModelHint = null;
            chatEmptyState = null;
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            // Extra bottom padding so the last card clears the floating nav bar.
            content.setPadding(dp(22), dp(8), dp(22), dp(104));
            scroll.addView(content);
            pageRoot.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }

        if (!chat) {
            // Floating bottom navigation: a detached rounded bar with quiet text-only
            // items — the selected label takes the accent, everyone else stays muted.
            FrameLayout navWrap = new FrameLayout(this);
            navWrap.setPadding(dp(14), dp(4), dp(14), dp(12));
            LinearLayout nav = new LinearLayout(this);
            nav.setOrientation(LinearLayout.HORIZONTAL);
            nav.setPadding(dp(10), dp(9), dp(10), dp(9));
            nav.setBackground(rounded(Design.cardBg(), Design.cardBg(), 26));
            nav.setElevation(dp(3));
            addNavItem(nav, "⌂", L10n.s("Ana sayfa"), "home".equals(tab), this::showHome);
            addNavItem(nav, "◉", L10n.s("Karakter"), "characters".equals(tab), this::showCharacters);
            addNavItem(nav, "▤", L10n.s("Sohbetler"), "chats".equals(tab), this::showChats);
            addNavItem(nav, "⚙", L10n.s("Ayarlar"), "settings".equals(tab), this::showSettings);
            navWrap.addView(nav, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            pageRoot.addView(navWrap, fullWidth());
        }
        setContentView(pageRoot);
        // The old 120ms fade (GUI review item 18) was removed: on slower phones every
        // navigation read as a "flash", and in-place page rebuilds already look smooth.
        Design.applyBars(this);
    }

    /** Dark palettes get the dark cold-start window theme (see onCreate). */
    private boolean isDarkThemeId(String themeId) {
        return "golge".equals(themeId) || "deniz-gecesi".equals(themeId) || "gun-batimi".equals(themeId);
    }

    /** Forces the next setPage call into a full window rebuild (theme / language changes). */
    private void resetPageAnchor() {
        pageTitle = null;
        pageSubtitle = null;
    }

    private void addNavItem(LinearLayout nav, String icon, String title, boolean selected, Runnable action) {
        // Text-only item: the icon parameter is kept for callers but intentionally unused.
        TextView item = label(title, 13, selected ? Design.accent() : Design.muted());
        item.setGravity(Gravity.CENTER);
        item.setTypeface(android.graphics.Typeface.SANS_SERIF, selected
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        if (selected) {
            LinearLayout.LayoutParams highlight = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.35f);
            item.setBackground(rounded(Design.accentSoft(), Design.accentSoft(), 22));
            item.setPadding(dp(4), dp(9), dp(4), dp(9));
            item.setOnClickListener(v -> action.run());
            nav.addView(item, highlight);
        } else {
            item.setPadding(dp(4), dp(9), dp(4), dp(9));
            item.setOnClickListener(v -> action.run());
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
    }

    private void addHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(19), dp(20), dp(19));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Design.heroA(), Design.heroB()});
        bg.setCornerRadius(dp(24));
        hero.setBackground(bg);
        TextView eyebrow = label(L10n.s("KENDİ DÜNYANI KUR"), 10, Design.heroSub());
        eyebrow.setLetterSpacing(.12f);
        eyebrow.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
        hero.addView(eyebrow);
        TextView title = label(L10n.s("Hikâyen\nburada başlıyor."), 26, Design.heroText());
        title.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(9), 0, dp(8));
        hero.addView(title);
        TextView detail = label(store.characters().size() + L10n.s(" karakter  ·  ") + store.groups().size() + L10n.s(" sohbet"), 14, Design.heroSub());
        hero.addView(detail);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(20));
        content.addView(hero, p);
    }

    private void addSection(String title, String actionText, Runnable action) {
        // Quiet section headers: small, letterspaced, muted — content breathes around them.
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(title, 13, Design.muted());
        heading.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
        heading.setLetterSpacing(.06f);
        row.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (actionText != null && action != null) {
            TextView link = label(actionText + "  ›", 13, Design.accent());
            link.setOnClickListener(v -> action.run());
            row.addView(link);
        }
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, dp(16), 0, dp(8));
        content.addView(row, p);
    }

    private LinearLayout addEmpty(String title, String detail, String actionText, View.OnClickListener action) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(26), dp(20), dp(24));
        box.setBackground(rounded(Design.cardBg(), Design.cardBg(), 22));
        TextView heading = label(title, 16, Design.text());
        heading.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        box.addView(heading);
        TextView body = label(detail, 13, Design.muted());
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(5), 0, 0);
        box.addView(body);
        if (actionText != null && action != null) {
            Button button = button(actionText);
            stylePrimary(button);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46));
            p.setMargins(0, dp(14), 0, 0);
            box.addView(button, p);
            button.setOnClickListener(action);
        }
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(14));
        content.addView(box, p);
        return box;
    }

    private void addGroupCard(Group group, Runnable openBack) {
        Message last = store.lastMessage(group.id);
        String preview;
        String when = "";
        if (group.id.equals(generatingChatId)) {
            preview = L10n.speaker(generatingSpeaker == null ? L10n.s("Bir karakter") : generatingSpeaker) + L10n.s(L10n.s("yanıt yazıyor…"));
        } else if (last == null) {
            preview = L10n.s("Sahneyi başlatmak için dokun");
        } else {
            // Image-only last message would render as "Speaker: "; show a placeholder.
            String body = (last.text == null || last.text.isEmpty()) && last.hasImage()
                    ? L10n.s(L10n.s("[görsel]")) : oneLine(last.text, 90);
            preview = (last.user ? L10n.speaker("Sen") : last.speaker) + ": " + body;
            when = clock.format(new Date(last.at));
        }
        CharacterProfile face = group.characterIds.isEmpty() ? null : store.character(group.characterIds.get(0));

        // cAI-style recent-chat card: leading avatar, bold title, muted preview + time.
        LinearLayout card = makeCard();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View faceView = Avatar.forIdentity(this,
                face != null ? face.name : group.name,
                face != null && face.avatarPath != null ? face.avatarPath : "", 44);
        row.addView(faceView, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView name = label(group.name, 17, Design.text());
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);
        TextView meta = label(preview, 13, Design.muted());
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(0, dp(3), 0, 0);
        texts.addView(meta);
        TextView cast = label(face != null && !when.isEmpty() ? when : "RP", 11, Design.muted());
        cast.setPadding(0, dp(3), 0, 0);
        texts.addView(cast);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);
        card.setOnClickListener(v -> showChat(group, openBack));
        addCardToPage(card);
    }

    private void addCharacterGrid(List<CharacterProfile> characters) {
        // Two-column cAI-like cards; simple pairs of weight-1 cards, no complex layout.
        int rows = (characters.size() + 1) / 2;
        for (int i = 0; i < rows; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 2; j++) {
                int index = i * 2 + j;
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (j == 1) cell.setMargins(dp(6), 0, 0, 0);
                if (index < characters.size()) {
                    row.addView(characterCell(characters.get(index)), cell);
                } else {
                    row.addView(new View(this), cell); // keeps rows square when the count is odd
                }
            }
            LinearLayout.LayoutParams rowParams = fullWidth();
            rowParams.setMargins(0, 0, 0, dp(8));
            content.addView(row, rowParams);
        }
    }

    private LinearLayout characterCell(CharacterProfile character) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(10), dp(14), dp(10), dp(14));
        card.setBackground(rounded(Design.cardBg(), Design.cardBg(), 22));
        View avatar = Avatar.forIdentity(this, character.name, character.avatarPath, 52);
        card.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView name = label(character.name, 15, Design.text());
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, dp(8), 0, 0);
        card.addView(name);
        TextView desc = label(brief(character.description).replace("\n", " "), 12, Design.muted());
        desc.setGravity(Gravity.CENTER);
        desc.setMaxLines(2);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        desc.setPadding(0, dp(3), 0, 0);
        card.addView(desc);
        // Tap opens the editor; long-press opens quick actions (copy / JSON export),
        // restoring the interaction the earlier list→grid rework had orphaned.
        card.setOnClickListener(v -> showCharacterEditor(character));
        card.setOnLongClickListener(v -> {
            showCharacterActions(character);
            return true;
        });
        return card;
    }

    /**
     * Character card extras (DeepSeek review item): quick variant copies and a single
     * character JSON export — the full backup stays the standard path.
     */
    private void showCharacterActions(CharacterProfile character) {
        String[] items = {L10n.s(L10n.s("Kopya oluştur")), L10n.s(L10n.s("JSON dışa aktar"))};
        new AlertDialog.Builder(this).setTitle(character.name)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) duplicateCharacter(character);
                    else exportCharacterPicker(character);
                })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).show();
    }

    private void duplicateCharacter(CharacterProfile original) {
        CharacterProfile copy = new CharacterProfile();
        copy.name = original.name + L10n.s(" (kopya)");
        copy.description = original.description;
        copy.personality = original.personality;
        copy.instructions = original.instructions;
        copy.examples = original.examples;
        copy.avatarPath = original.avatarPath; // the file keeps existing; both cards may show it
        if (!store.saveCharacter(copy)) {
            Toast.makeText(this, L10n.s(L10n.s("Kopya oluşturulamadı: karakter verisi okunamıyor.")), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, L10n.s(L10n.s("Kopya oluşturuldu.")), Toast.LENGTH_SHORT).show();
        showCharacters();
    }

    private void exportCharacterPicker(CharacterProfile character) {
        charExportTarget = character.id;
        String safe = character.name.replaceAll("[^\\p{L}\\p{N} _-]+", "").trim();
        if (safe.isEmpty()) safe = "karakter";
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "localrp-karakter-" + safe + "-" + stamp + ".json");
        startActivityForResult(intent, CHAR_EXPORT);
    }

    /** Writes one character card as a PersistenceCodec characters JSON document. */
    private void exportCharacter(Uri uri) {
        CharacterProfile character = charExportTarget == null ? null : store.character(charExportTarget);
        charExportTarget = null;
        if (character == null) {
            Toast.makeText(this, L10n.s(L10n.s("Dışa aktaramadı: karakter bulunamadı.")), Toast.LENGTH_LONG).show();
            return;
        }
        List<CharacterProfile> single = new ArrayList<>();
        single.add(character);
        String encoded = PersistenceCodec.encodeCharacters(single);
        if (encoded.isEmpty()) {
            Toast.makeText(this, L10n.s(L10n.s("Dışa aktaramadı: karakter verisi okunamıyor.")), Toast.LENGTH_LONG).show();
            return;
        }
        try (java.io.OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new java.io.IOException();
            output.write(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.flush();
            Toast.makeText(this, L10n.s(L10n.s("Karakter dışa aktarıldı.")), Toast.LENGTH_SHORT).show();
        } catch (java.io.IOException writeFailed) {
            Toast.makeText(this, L10n.s(L10n.s("Dışa aktarılamadı: dosyaya yazılamadı.")), Toast.LENGTH_LONG).show();
        }
    }

    private void addModelCard() {
        LinearLayout card = makeCard();
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = label("◈", 25, Design.accent());
        line.addView(icon);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView name = label(L10n.s("GGUF  ·  Cihaz içi yapay zekâ"), 15, Design.text());
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        String statusText = LocalAiRuntime.INSTANCE.isModelReady()
                ? L10n.s(L10n.s("Model hazır · çevrimdışı"))
                : (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading() ? L10n.s("Model yükleniyor…") : modelStatus);
        TextView status = label(statusText, 13, Design.muted());
        status.setPadding(0, dp(3), 0, 0);
        texts.addView(name);
        texts.addView(status);
        line.addView(texts);
        card.addView(line);
        addCardToPage(card);
    }

    private TextView addInfoCard(String title, String detail, String tag) {
        // Minimal refresh: the uppercase tag badge is gone — title leads, muted body follows.
        LinearLayout card = makeCard();
        TextView heading = label(title, 15, Design.text());
        heading.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
        card.addView(heading);
        if (tag != null) {
            TextView side = label(tag, 12, Design.accent());
            side.setPadding(0, dp(3), 0, 0);
            card.addView(side);
        }
        TextView body = label(detail, 13, Design.muted());
        body.setPadding(0, dp(5), 0, 0);
        card.addView(body);
        addCardToPage(card);
        return body;
    }

    /** Two side-by-side start tiles (C.AI "create" pattern) replacing stacked full-width buttons. */
    private void addHomeActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[][] defs = {
                {"＋", "Tek karakterli RP", L10n.s("Karakter seç, hikâyeyi başlat")},
                {"☷", "Grup RP", L10n.s("Birden çok karakter aynı sahnede")},
        };
        Runnable[] actions = {this::createSingleCharacterRoleplay, () -> showGroupEditor(null)};
        for (int i = 0; i < 2; i++) {
            final int index = i;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(14), dp(13), dp(14), dp(13));
            tile.setBackground(rounded(Design.cardBg(), Design.cardBg(), 20));
            tile.setOnClickListener(v -> actions[index].run());
            TextView icon = label(defs[i][0], 20, Design.accent());
            tile.addView(icon);
            TextView title = label(defs[i][1], 14, Design.text());
            title.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
            title.setPadding(0, dp(6), 0, 0);
            tile.addView(title);
            TextView detail = label(defs[i][2], 11, Design.muted());
            detail.setPadding(0, dp(3), 0, 0);
            tile.addView(detail);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i == 1) params.setMargins(dp(8), 0, 0, 0);
            row.addView(tile, params);
        }
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(20));
        content.addView(row, p);
    }

    private Button addPrimary(String text, View.OnClickListener listener) {
        Button view = button(text);
        stylePrimary(view);
        view.setOnClickListener(listener);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(12));
        content.addView(view, p);
        return view;
    }

    private void addSecondary(String text, View.OnClickListener listener) {
        Button view = button(text);
        view.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL);
        view.setTextColor(Design.text());
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setPadding(dp(16), 0, dp(16), 0);
        view.setMinHeight(dp(46));
        view.setBackground(rounded(Design.cardBg(), Design.cardBg(), 18));
        view.setOnClickListener(listener);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(8));
        content.addView(view, p);
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        // Flat, calm surfaces: same fill on both sides of the drawable so no stroke is drawn.
        card.setBackground(rounded(Design.cardBg(), Design.cardBg(), 22));
        return card;
    }

    private void addCardToPage(View card) {
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(10));
        content.addView(card, p);
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(L10n.s(text));
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable rounded(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != color) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void stylePrimary(Button button) {
        button.setAllCaps(false);
        button.setTextColor(Design.onAccent());
        button.setTextSize(15);
        button.setLetterSpacing(0f);
        button.setMinHeight(dp(50));
        button.setBackground(rounded(Design.accent(), Design.accent(), 20));
    }

    private EditText field(String hint, String value) { return field(hint, value, 1); }
    private EditText field(String hint, String value, int lines) {
        EditText input = new EditText(this);
        input.setHint(L10n.s(hint));
        input.setText(value);
        input.setTextSize(15);
        input.setMinLines(lines);
        input.setGravity(Gravity.TOP);
        input.setPadding(dp(14), dp(11), dp(14), dp(11));
        input.setTextColor(Design.text());
        input.setHintTextColor(Design.muted());
        GradientDrawable background = new GradientDrawable();
        background.setColor(Design.cardBg());
        background.setCornerRadius(dp(16));
        input.setBackground(background);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(12));
        content.addView(input, p);
        return input;
    }

    private void addBody(String text) {
        TextView view = label(text, 13, Design.muted());
        view.setPadding(0, dp(4), 0, dp(12));
        view.setLineSpacing(dp(1), 1f);
        content.addView(view);
    }

    private void addDangerButton(String text, View.OnClickListener listener) {
        Button view = button(text);
        view.setTextColor(Design.danger());
        view.setBackground(rounded(Design.dangerSoft(), Design.dangerSoft(), 18));
        view.setOnClickListener(listener);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(8));
        content.addView(view, p);
    }

    private Button button(String text) {
        Button view = new Button(this);
        view.setText(L10n.s(text));
        view.setAllCaps(false);
        view.setTextSize(15);
        view.setMinHeight(dp(48));
        return view;
    }

    /**
     * True when a message balloon TextView is still mounted in the current chat content.
     * Balloons wrap inside their avatar row, so the TextView's parent is the ROW — never
     * `content` itself. Bug fix (alpha2 device report): the old `getParent() == content`
     * checks could never match, so every streaming token re-created the draft balloon
     * below the frozen ones (only the last one held the full text).
     */
    private boolean balloonInContent(TextView balloon) {
        if (balloon == null || content == null) return false;
        ViewParent row = balloon.getParent();
        return row instanceof View && ((View) row).getParent() == content;
    }

    /**
     * Renders a persisted message; messages that carry an image get the image balloon
     * layout, everything else keeps the classic text path.
     */
    private TextView addStoredMessage(LinearLayout parent, Message message) {
        int index = chatHistory.indexOf(message);
        TextView balloon;
        if (message.hasImage()) balloon = addImageMessage(parent, message);
        else balloon = addMessage(parent, message.speaker, message.text, message.user, message.at, message);
        if (index >= 0) {
            ViewGroup row = messageRowOf(balloon);
            if (row != null) row.setTag(index); // search/jump anchor
        }
        return balloon;
    }

    /** Text balloon TextViews sit inside their avatar row; image balloons wrap one level deeper. */
    private ViewGroup messageRowOf(View balloon) {
        ViewParent parent = balloon.getParent();
        while (parent instanceof View && ((View) parent).getParent() != content) {
            parent = ((View) parent).getParent();
        }
        return parent instanceof LinearLayout ? (LinearLayout) parent : null;
    }

    /** Frees cached chat images when no chat page is on screen. */
    private void recycleChatImageCache() {
        for (Bitmap bitmap : imageDecodeCache.values()) {
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
        imageDecodeCache.clear();
    }

    /**
     * Image message balloon: the picture first (decoded off the UI thread), a text
     * caption beneath it when one exists, then the standard time stamping.
     */
    private TextView addImageMessage(LinearLayout parent, Message message) {
        boolean user = message.user;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(10), dp(10), dp(10), dp(10));
        wrap.setBackground(user
                ? rounded(Design.bubbleUser(), Design.bubbleUserStroke(), 20)
                : rounded(Design.cardBg(), Design.cardBg(), 20));

        // Identity (GUI review item 9): image balloons used to be anonymous; the same
        // bold accent header as text balloons now names the speaker. The header is the
        // single timestamp source — the row-side duplicate clock is gone.
        TextView imageHead = new TextView(this);
        imageHead.setTextSize(15);
        imageHead.setTextColor(Design.text());
        imageHead.setText(balloonHeader(message.speaker, message.at));
        imageHead.setPadding(0, 0, 0, dp(8));
        wrap.addView(imageHead, fullWidth());

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        image.setMaxHeight(dp(260));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        String imagePath = message.image;
        final int maxSide = dp(240);
        // Same (path,size) rendered repeatedly while scrolling: reuse the decoded
        // bitmap instead of spawning a fresh decode thread per re-render.
        String cacheKey = imagePath + "@" + maxSide;
        Bitmap cached = imageDecodeCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            image.setImageBitmap(cached);
        } else {
            if (cached != null) imageDecodeCache.remove(cacheKey, cached);
            Thread decode = new Thread(() -> {
                Bitmap bitmap = LocalImageEngine.INSTANCE.decode(imagePath, maxSide);
                if (bitmap == null) return;
                runOnUiThread(() -> {
                    if (!image.isAttachedToWindow()) return;
                    Bitmap known = imageDecodeCache.get(cacheKey);
                    if (known != null && !known.isRecycled()) {
                        bitmap.recycle();
                        image.setImageBitmap(known);
                        return;
                    }
                    if (visibleChatId != null) imageDecodeCache.put(cacheKey, bitmap);
                    image.setImageBitmap(bitmap);
                });
            }, "localrp-chat-image");
            decode.start();
        }
        imageParams.setMargins(0, 0, 0, message.text == null || message.text.isEmpty() ? 0 : dp(8));
        wrap.addView(image, imageParams);

        TextView caption = null;
        TextView fallback = null;
        if (message.text != null && !message.text.isEmpty()) {
            caption = label(message.text, 15, Design.text());
            caption.setLineSpacing(dp(2), 1f);
            caption.setPadding(0, 0, 0, 0);
            wrap.addView(caption, fullWidth());
        } else {
            fallback = label(user ? L10n.s(L10n.s("Fotoğraf eklendi")) : L10n.s(L10n.s("Görsel üretildi")), 12, Design.muted());
            wrap.addView(fallback, fullWidth());
        }
        final TextView captionFinal = caption != null ? caption : fallback;
        wrap.setOnLongClickListener(v -> {
            showMessageActions(message);
            return true;
        });
        // Tap-to-view: the full-screen zoomable viewer replaces the inert tap.
        image.setOnClickListener(v -> showImageViewer(message.image));

        // Same wrap sizing as text balloons; the image itself gets the maxWidth.
        int imageMax = (int) (getResources().getDisplayMetrics().widthPixels * (user ? 0.64f : 0.76f));
        image.setMaxWidth(Math.max(dp(140), imageMax));

        LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapParams.setMargins(user ? dp(10) : 0, 0, user ? 0 : dp(10), dp(10));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? Gravity.END | Gravity.BOTTOM : Gravity.START | Gravity.TOP);
        if (!user) {
            String speakerAvatar = speechAvatarPath(message.speaker);
            View companion = Avatar.forIdentity(this, message.speaker, speakerAvatar, 30);
            companion.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams companionParams = new LinearLayout.LayoutParams(dp(30), dp(30));
            companionParams.setMargins(0, dp(3), 0, 0);
            row.addView(companion, companionParams);
        }
        row.addView(wrap, wrapParams);
        // The header carries the single timestamp for image balloons.
        parent.addView(row, fullWidth());
        return caption != null ? caption : fallback;
    }

    /** Bold accent speaker name with a muted time — reused by text and image balloons. */
    private CharSequence balloonHeader(String speaker, long at) {
        String display = speaker != null ? L10n.speaker(speaker) : "";
        String header = at > 0 ? display + L10n.s("  ·  ") + clock.format(new Date(at)) : display;
        SpannableString styled = new SpannableString(header);
        styled.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, display.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new ForegroundColorSpan(Design.accent()), 0, display.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (at > 0) {
            styled.setSpan(new ForegroundColorSpan(Design.muted()), display.length(), header.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return styled;
    }

    /** Full-screen image viewer (GUI review item 9): pinch-zoom, drag-pan, tap or ✕ closes. */
    private void showImageViewer(String path) {
        if (path == null || path.isEmpty()) return;
        android.app.Dialog viewer = new android.app.Dialog(this);
        viewer.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Design.pageBg());
        ZoomableImage image = new ZoomableImage(viewer);
        root.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView close = label("✕", 16, Design.text());
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(L10n.s(L10n.s("Kapat")));
        close.setBackground(rounded(Design.cardBg(), Design.cardStroke(), 22));
        close.setOnClickListener(v -> viewer.dismiss());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(44), dp(44));
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeParams.setMargins(0, dp(18), dp(18), 0);
        root.addView(close, closeParams);
        viewer.setContentView(root);
        viewer.show();
        new Thread(() -> {
            Bitmap bitmap = LocalImageEngine.INSTANCE.decode(path, dp(1024));
            if (bitmap == null) {
                runOnUiThread(() -> {
                    if (!viewer.isShowing()) return;
                    Toast.makeText(this, L10n.s(L10n.s("Görsel açılamadı.")), Toast.LENGTH_SHORT).show();
                });
                return;
            }
            runOnUiThread(() -> {
                if (!viewer.isShowing()) {
                    bitmap.recycle(); // dialog already closed on a slow decode
                    return;
                }
                image.setBitmap(bitmap);
            });
        }, "localrp-image-viewer").start();
    }

    /** Matrix-zoomable full-screen ImageView: pinch zoom, drag pan, tap closes. */
    private final class ZoomableImage extends android.widget.ImageView {
        private final android.app.Dialog viewer;
        private final android.graphics.Matrix matrix = new android.graphics.Matrix();
        private android.graphics.Bitmap baseBitmap;
        private float fitScale = 1f;
        private float currentScale = 1f;
        private float lastX, lastY;
        private final float[] matrixValues = new float[9];

        ZoomableImage(android.app.Dialog viewer) {
            super(MainActivity.this);
            this.viewer = viewer;
            setScaleType(android.widget.ImageView.ScaleType.MATRIX);
            android.view.ScaleGestureDetector scaleDetector = new android.view.ScaleGestureDetector(
                    MainActivity.this, new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(android.view.ScaleGestureDetector detector) {
                    // Scale bounds are relative to the fitted size, not a hard 1.0.
                    float target = Math.max(fitScale, Math.min(fitScale * 6, currentScale * detector.getScaleFactor()));
                    float applied = target / currentScale;
                    matrix.postScale(applied, applied, detector.getFocusX(), detector.getFocusY());
                    currentScale = target;
                    clampPan();
                    setImageMatrix(matrix);
                    invalidate();
                    return true;
                }
            });
            android.view.GestureDetector gestures = new android.view.GestureDetector(MainActivity.this,
                    new android.view.GestureDetector.SimpleOnGestureListener() {
                        @Override public boolean onSingleTapConfirmed(android.view.MotionEvent event) {
                            viewer.dismiss();
                            return true;
                        }

                        @Override public boolean onDoubleTap(android.view.MotionEvent event) {
                            float target = currentScale > fitScale * 1.5f ? fitScale : fitScale * 2.5f;
                            matrix.postScale(target / currentScale, target / currentScale, event.getX(), event.getY());
                            currentScale = target;
                            clampPan();
                            setImageMatrix(matrix);
                            invalidate();
                            return true;
                        }
                    });
            setOnTouchListener(new android.view.View.OnTouchListener() {
                @Override public boolean onTouch(View v, android.view.MotionEvent event) {
                    boolean zooming = scaleDetector.onTouchEvent(event) && scaleDetector.isInProgress();
                    gestures.onTouchEvent(event);
                    switch (event.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            lastX = event.getRawX();
                            lastY = event.getRawY();
                            break;
                        case android.view.MotionEvent.ACTION_MOVE:
                            if (!zooming && currentScale > fitScale * 1.01f) {
                                matrix.postTranslate(event.getRawX() - lastX, event.getRawY() - lastY);
                                clampPan();
                                setImageMatrix(matrix);
                                invalidate();
                            }
                            lastX = event.getRawX();
                            lastY = event.getRawY();
                            break;
                    }
                    return true;
                }
            });
        }

        void setBitmap(android.graphics.Bitmap bitmap) {
            baseBitmap = bitmap;
            setImageBitmap(bitmap);
            fit();
        }

        private void fit() {
            if (baseBitmap == null) return;
            int width = getWidth();
            int height = getHeight();
            if (width == 0 || height == 0) {
                post(this::fit);
                return;
            }
            matrix.reset();
            float scale = Math.min(
                    width / (float) baseBitmap.getWidth(),
                    height / (float) baseBitmap.getHeight());
            fitScale = scale;
            currentScale = scale;
            matrix.setScale(scale, scale, 0, 0);
            matrix.postTranslate((width - baseBitmap.getWidth() * scale) / 2f,
                    (height - baseBitmap.getHeight() * scale) / 2f);
            setImageMatrix(matrix);
            invalidate();
        }

        /** Keeps the scaled image on screen: it can never be panned fully away. */
        private void clampPan() {
            if (baseBitmap == null) return;
            matrix.getValues(matrixValues);
            float scaledWidth = baseBitmap.getWidth() * currentScale;
            float scaledHeight = baseBitmap.getHeight() * currentScale;
            float x = matrixValues[Matrix.MTRANS_X];
            float y = matrixValues[Matrix.MTRANS_Y];
            int width = getWidth();
            int height = getHeight();
            if (scaledWidth > width) x = Math.max(width - scaledWidth, Math.min(x, 0));
            else x = (width - scaledWidth) / 2f;
            if (scaledHeight > height) y = Math.max(height - scaledHeight, Math.min(y, 0));
            else y = (height - scaledHeight) / 2f;
            matrixValues[Matrix.MTRANS_X] = x;
            matrixValues[Matrix.MTRANS_Y] = y;
            matrix.setValues(matrixValues);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            fit();
        }
    }

    private TextView addMessage(LinearLayout parent, String speaker, String text, boolean user, long at) {
        return addMessage(parent, speaker, text, user, at, null);
    }

    /** {@code stored} routes long-press to the full transcript actions; drafts copy only. */
    private TextView addMessage(LinearLayout parent, String speaker, String text, boolean user, long at, Message stored) {
        // C.AI-modern rebuild: characters speak from a soft, flat card with a leading
        // 30dp avatar and an accent-colored name; the user's own lines stay compact,
        // right-weighted bubbles in a softened accent — metrics wider than the rest.
        TextView view = label("", 15, Design.text());
        view.setPadding(dp(14), dp(10), dp(14), dp(10));
        view.setLineSpacing(dp(2), 1f);
        view.setBackground(user
                ? rounded(Design.bubbleUser(), Design.bubbleUserStroke(), 20)
                : rounded(Design.cardBg(), Design.cardBg(), 20));
        renderMessage(view, speaker, text, at);
        view.setOnLongClickListener(v -> {
            if (stored != null) {
                showMessageActions(stored);
                return true;
            }
            String value = view.getTag() instanceof String ? (String) view.getTag() : view.getText().toString();
            if (value.trim().isEmpty()) return false;
            showMessageActions(speaker, value);
            return true;
        });
        // WRAP_CONTENT + maxWidth gives real balloon sizing; weight on a lone weighted
        // child distributed the whole row, so every bubble ran near-full width.
        int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * (user ? 0.62f : 0.74f));
        view.setMaxWidth(Math.max(dp(140), maxWidth));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? Gravity.END | Gravity.BOTTOM : Gravity.START | Gravity.TOP);
        if (!user) {
            String speakerAvatar = speechAvatarPath(speaker);
            View companion = Avatar.forIdentity(this, speaker, speakerAvatar, 30);
            companion.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams companionParams = new LinearLayout.LayoutParams(dp(30), dp(30));
            companionParams.setMargins(0, dp(3), 0, 0);
            row.addView(companion, companionParams);
        }
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bubbleParams.setMargins(user ? dp(10) : 0, 0, user ? 0 : dp(10), dp(10));
        row.addView(view, bubbleParams);
        // Timestamp stays only in the balloon header for text messages; the duplicate
        // row-side clock squeezed narrow bubbles.
        parent.addView(row, fullWidth());
        return view;
    }

    /** Local photo for a character, resolved by name (message balloons carry only labels). */
    private String speechAvatarPath(String speaker) {
        if (speaker == null) return "";
        for (CharacterProfile character : store.characters()) {
            if (character.name.equals(speaker)) return character.avatarPath == null ? "" : character.avatarPath;
        }
        return "";
    }

    /** Clipboard helper shared by copy-only and full message actions. */
    private void copyTextToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, L10n.s(L10n.s("Mesaj kopyalandı.")), Toast.LENGTH_SHORT).show();
        }
    }

    /** Copy-only menu for balloons that are not stored messages (streaming drafts). */
    private void showMessageActions(String speaker, String text) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(speaker + L10n.s(" · mesaj"))
                .setItems(new String[]{L10n.s("Metni kopyala")}, (d, which) ->
                        copyTextToClipboard(L10n.s(L10n.s("LocalRP mesajı")), text))
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).create();
        tintDialog(dialog);
        dialog.show();
    }

    /**
     * Full actions for stored messages: copy, edit, delete — and regenerate when the
     * message is the transcript's last AI turn. Draft balloons (no transcript index)
     * keep the copy-only menu.
     */
    private void showMessageActions(Message message) {
        int index = chatHistory.indexOf(message);
        if (index < 0) {
            String body = (message.text == null || message.text.isEmpty()) && message.hasImage()
                    ? L10n.s(L10n.s("[görsel]")) : message.text;
            showMessageActions(message.speaker, body);
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(L10n.s("Metni kopyala"));
        actions.add(() -> {
            String body = (message.text == null || message.text.isEmpty()) && message.hasImage()
                    ? "" : message.text;
            if (body.isEmpty() && message.hasImage()) {
                Toast.makeText(this, L10n.s(L10n.s("Bu mesajın metni yok; görsel kayıtta saklanır.")), Toast.LENGTH_SHORT).show();
                return;
            }
            copyTextToClipboard(L10n.s(L10n.s("LocalRP mesajı")), body);
        });
        labels.add(L10n.s(L10n.s("Düzenle")));
        actions.add(() -> editMessage(index));
        labels.add(L10n.s(L10n.s("Sil")));
        actions.add(() -> confirmDeleteMessage(index));
        if (!message.user && index == chatHistory.size() - 1 && !isGeneratingHere()) {
            labels.add(L10n.s(L10n.s("Yeniden üret")));
            actions.add(() -> regenerateLastAction(index, null));
            labels.add(L10n.s(L10n.s("Yönlendirerek yeniden üret")));
            actions.add(() -> regenerateWithNote(index));
            // Comfort tool (v0.19.22): the tail a regenerate removed can come back
            // until the next user turn invalidates it. The chat id is captured here —
            // the menu is modal, but the action must never touch a different chat.
            String stashChatId = visibleChatId;
            RegenStash stash = regenStashes.get(stashChatId);
            Group stashGroup = store.group(stashChatId);
            if (stash != null && stashGroup != null && RegenUndo.canRestore(
                    stash.index, stash.anchorText, stash.anchorAt, store.messages(stashGroup.id))) {
                labels.add(L10n.s(L10n.s("Eski yanıtı geri getir")));
                actions.add(() -> undoRegenerate(stashChatId));
            }
        }
        String[] items = labels.toArray(new String[0]);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(message.speaker + L10n.s(" · mesaj"))
                .setItems(items, (d, which) -> {
                    if (which >= 0 && which < actions.size()) actions.get(which).run();
                })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).create();
        tintDialog(dialog);
        dialog.show();
    }

    /** A generation is either running anywhere or targeting the visible chat. */
    private boolean isGeneratingHere() {
        return generatingChatId != null;
    }

    private void editMessage(int index) {
        if (index < 0 || index >= chatHistory.size()) return;
        Group group = store.group(visibleChatId);
        if (group == null) return;
        if (group.id.equals(generatingChatId)) {
            Toast.makeText(this, L10n.s(L10n.s("Yanıt üretilirken mesaj değiştirilemez. Önce yanıtı durdur.")), Toast.LENGTH_SHORT).show();
            return;
        }
        Message message = chatHistory.get(index);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(16), dp(22), 0);
        EditText input = dialogField(L10n.s(L10n.s("Düzenle: ")) + message.speaker);
        input.setText(message.text == null ? "" : message.text);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        form.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(L10n.s(L10n.s("Mesajı düzenle")))
                .setView(form)
                .setPositiveButton(L10n.s(L10n.s("Kaydet")), (dialog, which) -> {
                    String newText = input.getText().toString().trim();
                    if (newText.isEmpty() && !message.hasImage()) {
                        Toast.makeText(this, L10n.s(L10n.s("Metin boş kalamaz; silmek için Sil'i kullan.")), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!store.updateMessage(group.id, index, newText)) {
                        Toast.makeText(this, L10n.s(L10n.s("Değişiklik kaydedilemedi: sohbet verisi okunamıyor.")), Toast.LENGTH_LONG).show();
                        return;
                    }
                    chatHistory.set(index, new Message(message.speaker, newText, message.user, message.at, message.image));
                    // The engine context no longer matches the transcript; a fresh
                    // generation must rebuild it from the edited history.
                    LocalAiRuntime.INSTANCE.resetConversation(group.id);
                    contextChars.remove(group.id);
                    chatMetrics.remove(group.id);
                    chainCounts.remove(group.id);
                    Toast.makeText(this, L10n.s(L10n.s("Mesaj güncellendi.")), Toast.LENGTH_SHORT).show();
                    jumpToMessage(group, index);
                })
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).show();
    }

    private void confirmDeleteMessage(int index) {
        if (index < 0 || index >= chatHistory.size()) return;
        Group group = store.group(visibleChatId);
        if (group == null) return;
        if (group.id.equals(generatingChatId)) {
            Toast.makeText(this, L10n.s(L10n.s("Yanıt üretilirken mesaj silinemez. Önce yanıtı durdur.")), Toast.LENGTH_SHORT).show();
            return;
        }
        Message message = chatHistory.get(index);
        confirm((message.hasImage()
                ? L10n.s(L10n.s("Bu mesaj ve görseli silinsin mi?"))
                : L10n.s("Bu mesaj silinsin mi?")), L10n.s(L10n.s("Sil")), () -> {
                    if (message.hasImage()) deleteMessageImageFile(message.image);
                    if (!store.removeMessage(group.id, index)) {
                        Toast.makeText(this, L10n.s(L10n.s("Silinemedi: sohbet verisi okunamıyor.")), Toast.LENGTH_LONG).show();
                        return;
                    }
                    chatMetrics.remove(group.id);
                    contextChars.remove(group.id);
                    chainCounts.remove(group.id);
                    LocalAiRuntime.INSTANCE.resetConversation(group.id);
                    forgetAutoAttempts(group.id);
                    Toast.makeText(this, L10n.s("Mesaj silindi."), Toast.LENGTH_SHORT).show();
                    showChat(group);
                });
    }

    /** Plain regenerate (no steering note). */
    private void regenerateLastAction(int index) {
        regenerateLastAction(index, null);
    }

    /** Comfort tool (v0.19.22): regenerate with an optional one-shot steering note
     *  that guides only the regenerated reply ("daha kısa yaz", …). */
    private void regenerateWithNote(int index) {
        if (index < 0 || index >= chatHistory.size()) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(16), dp(22), 0);
        TextView hint = label(L10n.s(L10n.s("Boş bırakılırsa sıradan yeniden üretim yapılır; not yalnızca bu yanıtı yönlendirir, kayda yazılmaz.")), 13, Design.muted());
        hint.setPadding(0, 0, 0, dp(6));
        form.addView(hint);
        EditText input = dialogField(L10n.s(L10n.s("Örn: daha kısa yaz; aksiyona odaklan")));
        input.setMinLines(2);
        form.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(L10n.s(L10n.s("Yönlendirme notu (yalnızca bu yanıt)")))
                .setView(form)
                .setPositiveButton(L10n.s(L10n.s("Yeniden üret")), (dialog, which) ->
                        regenerateLastAction(index, input.getText().toString()))
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null).show();
    }

    /** Restores the tail a previous regenerate removed (v0.19.22): the regenerated
     *  reply leaves the transcript, the stashed pre-regenerate tail returns as-is. */
    private void undoRegenerate(String chatId) {
        Group group = store.group(chatId);
        if (group == null) return;
        if (group.id.equals(generatingChatId)) {
            Toast.makeText(this, L10n.s(L10n.s("Yanıt üretilirken mesaj değiştirilemez. Önce yanıtı durdur.")), Toast.LENGTH_SHORT).show();
            return;
        }
        RegenStash stash = regenStashes.get(group.id);
        if (stash == null) return;
        List<Message> all = store.messages(group.id);
        if (!RegenUndo.canRestore(stash.index, stash.anchorText, stash.anchorAt, all)) {
            discardRegenStash(group.id);
            Toast.makeText(this, L10n.s(L10n.s("Geri alma artık geçerli değil: sohbet değişti.")), Toast.LENGTH_LONG).show();
            return;
        }
        List<Message> restored = new ArrayList<>(all.subList(0, stash.index));
        restored.addAll(stash.tail);
        if (!store.replaceMessages(group.id, restored)) {
            Toast.makeText(this, L10n.s(L10n.s("Eski yanıt geri getirilemedi: sohbet verisi okunamıyor.")), Toast.LENGTH_LONG).show();
            return;
        }
        // Replace succeeded: the discarded post-regenerate tail (and its image
        // files) leaves now — deleting earlier would orphan the transcript's
        // references if the write had failed.
        for (int i = stash.index; i < all.size(); i++) {
            if (all.get(i).hasImage()) deleteMessageImageFile(all.get(i).image);
        }
        regenStashes.remove(group.id); // files stay: the images are back in the transcript
        chatMetrics.remove(group.id);
        contextChars.remove(group.id);
        chainCounts.remove(group.id);
        LocalAiRuntime.INSTANCE.resetConversation(group.id);
        forgetAutoAttempts(group.id);
        Toast.makeText(this, L10n.s(L10n.s("Eski yanıt geri getirildi.")), Toast.LENGTH_SHORT).show();
        showChat(group);
    }

    /**
     * True regenerate: the chosen last AI message and everything after it leaves the
     * transcript, then the turn restarts from the last user message.
     */
    private void regenerateLastAction(int index, String steer) {
        Group group = store.group(visibleChatId);
        if (group == null) return;
        if (group.id.equals(generatingChatId)) return;
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s(L10n.s("Model yükleniyor; bitmesini bekle.")), Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) {
            Toast.makeText(this, L10n.s(L10n.s("Motor testi sürüyor; bitmesini bekle.")), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!modelCanRespond()) {
            openModels(() -> openChat(group.id));
            return;
        }
        List<Message> stored = store.messages(group.id);
        // Comfort tools (v0.19.22): the removed tail is stashed for "Eski yanıtı geri
        // getir" — image files stay on disk until the stash is discarded, so a
        // restore can put them back into the transcript.
        stashForRegenerate(group.id, index, stored);
        if (!store.removeMessagesFrom(group.id, index)) {
            // The transcript is unchanged, so the stash tail is still live in it —
            // drop the stash WITHOUT deleting its image files.
            regenStashes.remove(group.id);
            Toast.makeText(this, L10n.s(L10n.s("Yeniden üretilemedi: sohbet verisi okunamıyor.")), Toast.LENGTH_LONG).show();
            return;
        }
        chatMetrics.remove(group.id);
        contextChars.remove(group.id);
        chainCounts.remove(group.id);
        LocalAiRuntime.INSTANCE.resetConversation(group.id);
        forgetAutoAttempts(group.id);
        showChat(group);
        // continueOrRetry carries the steering note; it applies the note only when
        // a generation actually starts, so an aborted retry never leaks it into a
        // later unrelated turn.
        continueOrRetry(group, steer);
    }

    private void renderMessage(TextView view, String speaker, String text, long at) {
        view.setTag(text);
        // Corrupt/foreign records could carry a null speaker; L10n.speaker passes it
        // through, so guard here instead of NPE-ing inside the span assembly.
        String display = speaker != null ? L10n.speaker(speaker) : "";
        String header = at > 0 ? display + L10n.s("  ·  ") + clock.format(new Date(at)) : display;
        String value = header + "\n" + text;
        SpannableString styled = new SpannableString(value);
        styled.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, display.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // Character names pop as accent-on-accentSoft like the cAI chat card, not
        // invisible-on-invisible; the span colors the whole "Name  ·  12:04" head line.
        int headLength = header.length();
        styled.setSpan(new ForegroundColorSpan(Design.accent()), 0, display.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (at > 0) {
            styled.setSpan(new ForegroundColorSpan(Design.muted()), display.length(), headLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // Markdown-lite body: *italic* / **bold** fold into the same balloon.
        applyMarkdown(styled, text, headLength + 1);
        view.setText(styled);
    }

    /**
     * Maps MarkdownLite segments into {@code styled} beginning at {@code start};
     * segment lengths advance the cursor, so offsets stay aligned without re-parsing.
     * Off by preference (mkdown_lite).
     */
    private void applyMarkdown(SpannableString styled, String text, int start) {
        if (text == null || text.isEmpty()) return;
        boolean enabled = prefs().getBoolean("markdown_lite", true);
        int cursor = start;
        for (MarkdownLite.Segment segment : MarkdownLite.parse(text)) {
            int begin = cursor;
            int end = begin + segment.text.length();
            cursor = end;
            if (!enabled || (!segment.bold && !segment.italic)) continue;
            if (begin < end && end <= styled.length()) {
                styled.setSpan(new StyleSpan(
                        segment.bold && segment.italic
                                ? android.graphics.Typeface.BOLD_ITALIC
                                : segment.bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.ITALIC),
                        begin, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private void scrollChatToBottom(boolean force) {
        if (chatScroll == null || (!force && !autoScroll)) return;
        if (force) autoScroll = true;
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int px) { return (int) (px * getResources().getDisplayMetrics().density + .5f); }
    private String brief(String s) { return s == null || s.trim().isEmpty() ? L10n.s("Açıklama yok") : oneLine(s, 120); }

    private void confirm(String message, Runnable action) { confirm(message, L10n.s("Sil"), action); }

    private void confirm(String message, String positive, Runnable action) {
        tintDialog(new AlertDialog.Builder(this).setMessage(L10n.s(message))
                .setNegativeButton(L10n.s(L10n.s("Vazgeç")), null)
                .setPositiveButton(L10n.s(positive), (d, w) -> action.run()).show());
    }

    /**
     * Dialog theming (GUI review item 7, pragmatic pass): palette-aware dialog window
     * and text/button colors for the dialogs that route through this helper — the
     * fixed lavender framework theme is gone on dark palettes.
     */
    private void tintDialog(AlertDialog dialog) {
        try {
            android.view.Window window = dialog.getWindow();
            if (window == null) return;
            window.setBackgroundDrawable(rounded(Design.cardBg(), Design.cardStroke(), 24));
            window.getDecorView().post(() -> {
                if (!uiAlive()) return;
                window.getDecorView().setBackgroundColor(Design.cardBg());
                for (int id : new int[]{android.R.id.button1, android.R.id.button2, android.R.id.button3}) {
                    View buttonView = window.findViewById(id);
                    if (buttonView instanceof TextView) {
                        ((TextView) buttonView).setTextColor(Design.accent());
                        ((TextView) buttonView).setAllCaps(false);
                    }
                }
                View title = window.findViewById(android.R.id.title);
                if (title instanceof TextView) ((TextView) title).setTextColor(Design.text());
                View message = window.findViewById(android.R.id.message);
                if (message instanceof TextView) ((TextView) message).setTextColor(Design.text());
                View list = window.findViewById(android.R.id.list);
                if (list instanceof android.widget.ListView) {
                    ((android.widget.ListView) list).setDivider(null);
                }
            });
        } catch (Exception tintFailure) {
            // system styling attempt must never break the dialog itself
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

}
