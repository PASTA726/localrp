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
import android.graphics.Color;
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

import org.json.JSONArray;
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
    private static final String PREFS = "localrp";
    private static final String DEFAULT_AI_INSTRUCTIONS = "Her zaman sahnenin içinde kal. Seçili karakter olarak konuş ve kullanıcının rolünü üstlenme.";
    private static final String DEFAULT_AI_INSTRUCTIONS_EN = "Stay inside the scene at all times. Speak as the selected character and never take over the user's role.";
    private static final String SYS_PROMPT_TR = "Sen bir rol yapma asistanısın. Daima sahnenin içinde kal; seçili karakter olarak konuş; kullanıcı ve diğer karakterler adına yazma; anlatı, aksiyon ve diyalogu tutarlı sürdür.";
    private static final String SYS_PROMPT_EN = "You are a roleplay assistant. Stay inside the scene at all times; speak as the selected character; never write for the user or other characters; keep narration, action and dialogue consistent.";
    private static final String BUILTIN_ROLEPLAY_ID = "localrp_builtin_roleplay_character";
    private static final String DEFAULT_AI_THEME = "serbest";
    private static final String[][] AI_THEMES = {
            {"serbest", "Serbest", ""},
            {"karanlik-fantezi", "Karanlık fantezi",
                    "Gotik atmosfer, tehlikeli büyü ve ahlaki belirsizlik kullan; umut ile karanlık arasında denge kur."},
            {"bilim-kurgu", "Bilim kurgu",
                    "Teknoloji, uzay, yapay zekâ ve etik gerilim üzerinden somut, tutarlı bir gelecek kur."},
            {"romantik", "Romantik drama",
                    "Duygusal yakınlık, kırılganlık ve gerilimi öne çıkar; duyguları abartmadan göster."},
            {"gizem", "Gizem ve dedektiflik",
                    "İpuçları, şüpheler ve yavaş açığa çıkan sırlar kullan; her yanıt yeni bir iz bıraksın."},
            {"tarihi", "Tarihî dönem",
                    "Dönemin dili, kıyafetleri ve toplumsal kurallarına sadık kal; modern ifadelerden kaçın."},
            {"anime", "Anime / hafif roman",
                    "Canlı duygular, iç monolog ve komik zamanlama kullan; sahneyi abartısız sürdür."},
            {"korku", "Korku",
                    "Gerilim, belirsizlik ve görünmeyen tehdit yarat; kan ve şiddeti ölçülü tut."},
            {"buhar", "Buhar punk",
                    "Pirinç, buhar ve mekanik ayrıntılar; endüstriyel devrim atmosferini abartmadan koru."},
            {"sur-real", "Sürreal rüya",
                    "Mantık yerine çağrışım kullan: kayan nesneler, değişen mekânlar, rüya mantığı."},
            {"savas", "Savaş ve entrika",
                    "Siyasal baskı, askerî zorunluluk ve güven bunalımı; kimseye sığınak sağlama."},
            {"razo", "Sıcak slice of life",
                    "Gündelik küçük anlar, sıcak davranışlar; dram yerine samimiyet taşı."},
            {"macera", "Uzun yol macerası",
                    "Bir hedefe giden gezi sahnesi: yol arkadaşları, bulmacalar ve zorlayıcı seçimler."}
    };
    private static final int MAX_HISTORY_MESSAGES = 8;
    private static final int MAX_HISTORY_CHARS = 2600;

    private int historyDepth() { return prefs().getInt("history_depth", MAX_HISTORY_MESSAGES); }
    private int historyCharBudget() { return prefs().getInt("history_chars", MAX_HISTORY_CHARS); }
    private Store store;
    private LinearLayout content;
    private LinearLayout pageRoot;
    private TextView pageSubtitleView;
    private String currentTab = "home";
    private Uri selectedModel;
    private String selectedModelName = "Henüz model seçilmedi";
    private String modelStatus = "Model seçilmedi";
    private String visibleChatId;
    private String generatingChatId;
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
        String modelUri = prefs.getString("selected_model_uri", null);
        if (modelUri != null) selectedModel = Uri.parse(modelUri);
        selectedModelName = prefs.getString("selected_model_name", selectedModelName);
        if (LocalAiRuntime.INSTANCE.isModelReady()) modelStatus = "Hazır · bu işlem için internet gerekmez";
        else if (LocalAiRuntime.INSTANCE.isModelLoading()) modelStatus = "Model yükleniyor…";
        else if (selectedModel != null) modelStatus = "Seçildi · yüklemeye hazır";
        showHome();
    }

    private void reportIntegrityIssues() {
        java.util.List<String> parts = new ArrayList<>();
        for (String key : store.integrityIssues()) {
            if ("characters".equals(key)) parts.add(L10n.s("karakter listesi"));
            else if ("groups".equals(key)) parts.add(L10n.s("sohbet listesi"));
            else if (key.startsWith("chat_")) parts.add(L10n.s("bir sohbetin geçmişi"));
        }
        if (parts.isEmpty()) parts.add(L10n.s("bazı kayıtlar"));
        String detail = String.join(", ", parts);
        new AlertDialog.Builder(this)
                .setTitle(L10n.s("Veri okunamadı"))
                .setCancelable(false)
                .setMessage(L10n.s("Saklı verilerde bozuk bölümler bulundu (") + detail + L10n.s("). ")
                        + L10n.s("Koruma gereği bu bölümler okunana kadar yazmaya kapatıldı; hiçbir veri üzerine yazılmadı. ")
                        + L10n.s("İlgili sohbeti temizleyerek o bölümü onarabilirsin."))
                .setPositiveButton(L10n.s("Anlaşıldı"), null)
                .show();
    }

    private void showHome() {
        visibleChatId = null;
        backAction = null;
        setPage("LocalRP", "Yerel hikâyelerin, senin kontrolünde", "home");
        addHero();
        addHomeActions();
        List<Group> groups = store.groups();
        if (groups.isEmpty()) addEmpty("Henüz bir RP yok", "Bir karakter seçerek bire bir hikâye başlat veya grup kur.", null, null);
        else {
            addSection("Son sohbetler", "Tümünü gör", this::showChats);
            for (Group group : recentChats(groups, 4)) addGroupCard(group, this::showHome);
        }
        addSection("Karakterlerin", "Yönet", this::showCharacters);
        List<CharacterProfile> characters = store.characters();
        if (characters.isEmpty()) addEmpty("Hikâyene karakter ekle", "Kişiliğini ve konuşma tarzını birlikte tanımla.", "＋  Karakter oluştur", v -> showCharacterEditor(null));
        else addCharacterGrid(characters);
    }

    private void showCharacters() {
        visibleChatId = null;
        backAction = this::showHome;
        setPage("Karakterler", "Hikâyendeki kişileri yönet", "characters");
        addUserProfileCard();
        addPrimary("＋  Yeni karakter", v -> showCharacterEditor(null));
        List<CharacterProfile> characters = store.characters();
        if (characters.isEmpty()) addEmpty("İlk karakterini oluştur", "Adını, kişiliğini ve hikâyedeki yerini belirle.", "Karakter oluştur", v -> showCharacterEditor(null));
        else addCharacterGrid(characters);
        addSection("Hazır karakterler", "Tek dokunuşla ekle", null);
        addBody("Beğendiğin hazır karakter kopyalanıp tamamen düzenlenebilir bir karakter olarak listene geçer.");
        addPresetGrid();
    }

    /** v0.14.0: "Sen (kullanıcı profili)" card leading the characters screen. */
    private void addUserProfileCard() {
        String[] persona = store.persona();
        String name = persona != null ? persona[0] : "";
        String description = persona != null ? persona[1] : "";
        String avatar = personaAvatarDraft != null ? personaAvatarDraft
                : (persona != null ? persona[2] : "");
        LinearLayout card = makeCard();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View face = Avatar.forIdentity(this, name.isEmpty() ? "Sen" : name, avatar, 44);
        row.addView(face, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView title = label(name.isEmpty() ? "Sen" : name + L10n.s("  (kullanıcı)"), 16, Design.text());
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        texts.addView(title);
        TextView detail = label(description.isEmpty()
                ? "Kullanıcı personası tanımlı değil — dokunup kendini tanıt"
                : brief(description), 12, Design.muted());
        detail.setPadding(0, dp(3), 0, 0);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(detail);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);
        card.setOnClickListener(v -> showUserProfile());
        addCardToPage(card);
    }

    /** v0.14.0: user persona editor (name + self-description + optional photo). */
    private void showUserProfile() {
        visibleChatId = null;
        backAction = this::showCharacters;
        setPage("Sen (kullanıcı profili)", "Yapay zekâ sana bu kimlikle davranır", "characters");
        String[] persona = store.persona();
        String name = persona != null ? persona[0] : "";
        String description = persona != null ? persona[1] : "";
        final String storedAvatar = persona != null ? persona[2] : "";

        LinearLayout preview = makeCard();
        preview.setGravity(Gravity.CENTER);
        personaPreviewPane = preview;
        fillPersonaPreview(preview, name,
                personaAvatarDraft != null && !personaAvatarDraft.isEmpty() ? personaAvatarDraft : storedAvatar);
        addCardToPage(preview);

        EditText nameField = field("Adın (boş bırakılırsa \"Sen\" sayılır)", name, 1);
        nameField.setSingleLine(true);
        personaNameField = nameField;
        EditText descriptionField = field("Kendini tanıt (davranışını ve sesini etkiler)", description, 3);
        addBody("RP sohbetlerinde mesajların \"Sen\" etiketiyle kalır; model sana \"Kullanıcı kimliği\" bloğunda burada yazdığın bilgilerle davranır.");
        addPrimary("Profil fotoğrafı seç", v -> pickAvatar(true));
        if ((personaAvatarDraft != null && !personaAvatarDraft.isEmpty())
                || !storedAvatar.isEmpty()) {
            addDangerButton("Fotoğrafı kaldır", v -> {
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
        addPrimary("Profili kaydet", v -> {
            String savedName = nameField.getText().toString().trim();
            String savedDescription = descriptionField.getText().toString().trim();
            String savedAvatar = personaAvatarDraft != null && !personaAvatarDraft.isEmpty()
                    ? personaAvatarDraft : storedAvatar;
            // A fresh draft replaces the persisted file; delete the old one only once
            // the save succeeded, so backing out can never orphan the kept avatar.
            String replacedAvatar = personaAvatarDraft != null && !personaAvatarDraft.isEmpty()
                    && storedAvatar != null && !storedAvatar.isEmpty()
                    && !storedAvatar.equals(personaAvatarDraft)
                    ? storedAvatar : null;
            personaAvatarDraft = null;
            if (!store.savePersona(savedName, savedDescription, savedAvatar)) {
                Toast.makeText(this, L10n.s("Profil kayda geçemedi."), Toast.LENGTH_SHORT).show();
                return;
            }
            if (replacedAvatar != null) deleteAvatarFile(replacedAvatar);
            Toast.makeText(this, L10n.s("Kullanıcı profili kaydedildi."), Toast.LENGTH_SHORT).show();
            showCharacters();
        });
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
        card.setOnClickListener(v -> confirm(preset.name + L10n.s(" karakter listene kopyalansın mı? Kopyalandıktan sonra tamamen düzenlenebilir."), () -> {
            CharacterProfile copied = new CharacterProfile();
            copied.name = preset.name;
            copied.description = preset.description;
            copied.personality = preset.personality;
            copied.instructions = preset.instructions;
            copied.examples = preset.examples;
            if (!store.saveCharacter(copied)) {
                Toast.makeText(this, L10n.s("Karakter kaydedilemedi: verisi okunamıyor."), Toast.LENGTH_LONG).show();
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
        setPage(editing ? "Karakteri düzenle" : "Yeni karakter", "Bilgiler bu telefonda saklanır", "characters");
        EditText name = field("Karakter adı", editing ? existing.name : "");
        name.setSingleLine(true);
        EditText description = field("Kısa tanım", editing ? existing.description : "", 3);
        EditText personality = field("Kişilik ve konuşma tarzı", editing ? existing.personality : "", 3);
        EditText instructions = field("Karakter talimatları", editing ? existing.instructions : "", 4);
        EditText examples = field("Örnek diyalog", editing ? existing.examples : "", 3);

        addSection("Profil fotoğrafı", null, null);
        addBody("Fotoğraf gizli depoya küçük kopya olarak yazılır; seçtiğin kaynak dosya silinmez. Sohbetlerde karakterin avatarı bu foto olur ve Kaydet ile saklanır.");
        avatarStatusView = label(pickedAvatarPath.isEmpty()
                ? "Fotoğraf: seçilmedi" : "Fotoğraf: ✓ hazır", 13, pickedAvatarPath.isEmpty() ? Design.muted() : Design.accent());
        avatarStatusView.setPadding(0, dp(0), 0, dp(6));
        content.addView(avatarStatusView, fullWidth());
        addPrimary("Fotoğraf seç", v -> pickAvatar(false));
        if (!pickedAvatarPath.isEmpty()) {
            addDangerButton("Fotoğrafı kaldır", v -> {
                deleteAvatarFile(pickedAvatarPath);
                pickedAvatarPath = "";
                if (avatarStatusView != null) {
                    avatarStatusView.setText(L10n.s("Fotoğraf: seçilmedi"));
                    avatarStatusView.setTextColor(Design.muted());
                }
            });
        }

        addPrimary("Kaydet", v -> {
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
                Toast.makeText(this, L10n.s("Değişiklik kaydedilemedi: karakter verisi okunamıyor."), Toast.LENGTH_LONG).show();
                return;
            }
            if (replacedAvatar != null) deleteAvatarFile(replacedAvatar);
            if (afterSave != null) afterSave.run(); else showCharacters();
        });
        if (editing && !BUILTIN_ROLEPLAY_ID.equals(existing.id)) {
            addDangerButton("Karakteri sil", v -> confirm(existing.name + " silinsin mi?", () -> {
                if (!store.deleteCharacter(existing.id)) {
                    Toast.makeText(this, L10n.s("Silinemedi: karakter verisi okunamıyor."), Toast.LENGTH_LONG).show();
                    return;
                }
                showCharacters();
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
        setPage(editing ? "Grubu düzenle" : "Yeni grup", "Hikâyene katılacak karakterleri seç", "home");
        EditText name = field("Sohbet adı", editing ? existing.name : "");
        name.setSingleLine(true);
        addSection("Karakterler", "Bir veya daha fazla", null);
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
            addEmpty("Önce karakter oluştur", "RP için en az bir karakter gerekli.", "Karakter oluştur",
                    v -> showCharacterEditor(null, returnToEditor, returnToEditor));
        }
        addPrimary(editing ? "Değişiklikleri kaydet" : "Sohbeti başlat", v -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError(L10n.s("Sohbet adı gerekli")); return; }
            Group group = editing ? existing : new Group();
            group.name = name.getText().toString().trim();
            group.characterIds.clear();
            for (int i = 0; i < choices.size(); i++) if (choices.get(i).isChecked()) group.characterIds.add(characters.get(i).id);
            if (group.characterIds.isEmpty()) { name.setError(L10n.s("En az bir karakter seç")); return; }
            group.roleplay = true;
            if (!store.saveGroup(group)) {
                Toast.makeText(this, L10n.s("Değişiklik kaydedilemedi: sohbet verisi okunamıyor."), Toast.LENGTH_LONG).show();
                return;
            }
            showChat(group);
        });
        if (editing) {
            addSection("Sohbet", null, null);
            addDangerButton("Sohbet geçmişini temizle", v -> clearChat(existing));
            addDangerButton("Grubu sil", v -> confirm(existing.name + " ve sohbeti silinsin mi?", "Sil", () -> {
                LocalAiRuntime.INSTANCE.stopGeneration();
                deleteMessageImages(existing.id);
                store.deleteGroup(existing.id);
                showHome();
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
        new AlertDialog.Builder(this).setTitle(L10n.s("RP karakterini seç"))
                .setItems(names, (dialog, index) -> {
                    CharacterProfile character = characters.get(index);
                    Group chat = new Group();
                    chat.name = character.name + " ile RP";
                    chat.characterIds.add(character.id);
                    chat.roleplay = true;
                    store.saveGroup(chat);
                    showChat(chat);
                }).setNegativeButton(L10n.s("Vazgeç"), null).show();
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
            chatEmptyState = addEmpty("RP sahnesi burada başlıyor", "Mesajını yaz; seçili karakter otomatik yanıtlasın.", null, null);
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
                {L10n.s("İlk sahneyi kur"), L10n.s("*Kapıda durur, yorgun bir gülümseme.* Selam… buraya tam zamanında geldin.")},
                {L10n.s("Kısa bir selam"), L10n.s("Selam! Burada seni tekrar görmek güzel.")},
                {L10n.s("Arka planı sor"), L10n.s("Bu sahneyi nasıl kurdun? Baştan anlat.")},
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
        view.setText(L10n.english ? "Older messages (" + chatRenderStart + ")"
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
        if (!LocalAiRuntime.INSTANCE.isModelReady()) {
            if (LocalAiRuntime.INSTANCE.loadedModelPath() != null) {
                return L10n.s("Model yeniden hazırlanacak · çevrimdışı");
            }
            return L10n.s("Çevrimdışı yanıtlar için önce AI ayarlarından model yükle");
        }
        return group.characterIds.size() == 1 ? L10n.s("Bire bir karakter RP · cihaz içinde") : L10n.s("Grup RP · cihaz içinde");
    }

    private boolean modelCanRespond() {
        return LocalAiRuntime.INSTANCE.isModelReady() || LocalAiRuntime.INSTANCE.loadedModelPath() != null;
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
        menu.setContentDescription(L10n.s("Sohbet menüsü"));
        menu.setBackground(rounded(Design.cardBg(), Design.cardBg(), 30));
        menu.setOnClickListener(v -> showChatMenu(group));
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        menuParams.setMargins(dp(8), 0, 0, 0);
        tools.addView(menu, menuParams);
        chatComposer.addView(tools, fullWidth());

        if (!modelCanRespond()) {
            chatModelHint = label(selectedModel == null ? "Yanıtlar için GGUF modeli seç  ›" : "Modeli yükle  ›", 13, Design.accent());
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
        input.setHint(L10n.s("Hikâyeye bir şey yaz…"));
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
        photo.setContentDescription(L10n.s("Fotoğraf ekle"));
        photo.setBackground(rounded(Design.cardBg(), Design.cardBg(), 30));
        photo.setOnClickListener(v -> {
            imageAttachGroup = group;
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("image/*");
            try {
                startActivityForResult(
                        Intent.createChooser(pick, L10n.s("Fotoğraf seç")), CHAT_PHOTO_PICK);
            } catch (android.content.ActivityNotFoundException noPicker) {
                Toast.makeText(this, L10n.s("Bu cihazda fotoğraf seçici yok."), Toast.LENGTH_SHORT).show();
            }
        });
        composerRow.addView(photo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        Button paint = button("✨");
        paint.setTextSize(15);
        paint.setPadding(0, 0, 0, 0);
        paint.setMinWidth(dp(44));
        paint.setMinHeight(dp(44));
        paint.setTextColor(Design.accent());
        paint.setContentDescription(L10n.s("Görsel üret"));
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
        action.setContentDescription(L10n.s("Gönder (veya üretimi durdur)"));
        action.setTextColor(Design.onAccent());
        action.setBackground(rounded(Design.accent(), Design.accent(), 30));
        action.setOnClickListener(v -> {
            if (generatingChatId != null) LocalAiRuntime.INSTANCE.stopGeneration();
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
            TextView chipLabel = label(L10n.s("📷 Fotoğraf eklendi · gönder ile birlikte gider"), 13, Design.text());
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
                    .setTitle(L10n.s("Görsel modeli yüklü değil"))
                    .setMessage(L10n.s("Önce Ayarlar → Model önerileri → Görsel bölümünden bir görsel modeli indir ve yükle."))
                    .setPositiveButton(L10n.s("Model önerilerini aç"), (d, w) -> openModels(null))
                    .setNegativeButton(L10n.s("Vazgeç"), null)
                    .show();
            return;
        }
        if (LocalImageEngine.INSTANCE.isGenerating() || LocalImageEngine.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s("Görsel motoruyla bir işlem sürüyor; bitmesini bekle."), Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(16), dp(22), 0);
        TextView note = label(L10n.s("Bu cihazda tek bir 512×512 görsel üretilir; CPU ile 1–3 dakika sürebilir. İptal etmek için duraklat düğmesine bas."), 12, Design.muted());
        form.addView(note);
        // Dialog-scoped inputs: they must never enter the page's `content` tree, so
        // they are built with a detached helper instead of the page-bound field().
        EditText promptInput = dialogField(L10n.s("Görsel fikri"));
        promptInput.setHint(L10n.s("Örn: fırtınada duran deniz feneri"));
        form.addView(promptInput);
        EditText negativeInput = dialogField(L10n.s("İstenmeyenler (boş kalabilir)"));
        negativeInput.setHint(L10n.s("Örn: bulanık, düşük kalite"));
        form.addView(negativeInput);
        // Sampling control panel (DeepSeek item 8): steps / CFG / seed go with the
        // prompt and persist for the next run; empty inputs keep engine defaults.
        SharedPreferences prefs = prefs();
        int preferredSteps = prefs.getInt("img_steps", LocalImageEngine.INSTANCE.preparedStepCount());
        String preferredCfg = prefs.getString("img_cfg", "5");
        EditText stepsInput = dialogField(L10n.s("Adım sayısı (10–50)"));
        stepsInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        stepsInput.setText(String.valueOf(preferredSteps));
        stepsInput.setSingleLine(true);
        form.addView(stepsInput);
        EditText cfgInput = dialogField(L10n.s("CFG ölçeği (1.5–15)"));
        cfgInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        cfgInput.setText(preferredCfg);
        cfgInput.setSingleLine(true);
        form.addView(cfgInput);
        EditText seedInput = dialogField(L10n.s("Tohum (boş = rastgele)"));
        seedInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        seedInput.setSingleLine(true);
        seedInput.setText(prefs.getString("img_seed", ""));
        form.addView(seedInput);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(L10n.s("Görsel üret (") + LocalImageEngine.INSTANCE.loadedModelName() + ")")
                .setView(form)
                .setPositiveButton(L10n.s("Üret"), null)
                .setNegativeButton(L10n.s("Vazgeç"), null)
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
        setPage("Görsel galerisi", "Üretilen ve sohbete eklenen görseller", "settings");
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
            addEmpty("Burada henüz görsel yok", "Sohbette ✨ ile üret ya da 📷 ile bir fotoğraf ekle.", null, null);
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
        TextView meta = label(new Date(image.lastModified()) + "  ·  " + humanSize(image.length()), 12, Design.muted());
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
        String[] items = {L10n.s("Karakter avatarı yap"), L10n.s("Sohbete ekle"), L10n.s("Sil")};
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(image.getName())
                .setItems(items, (d, which) -> {
                    if (which == 0) makeAvatarFrom(image);
                    else if (which == 1) attachGalleryImage(image);
                    else if (galleryImageInUse(image)) {
                        Toast.makeText(this, L10n.s("Bu görsel bir sohbette veya karakter avatarında kullanılıyor; önce o kaydı sil."), Toast.LENGTH_LONG).show();
                    } else confirm(L10n.s("Bu görsel silinsin mi?"), L10n.s("Sil"), () -> {
                        if (image.delete()) {
                            Toast.makeText(this, L10n.s("Görsel silindi."), Toast.LENGTH_SHORT).show();
                            showGallery(this::showSettings);
                        } else {
                            Toast.makeText(this, L10n.s("Silinemedi: dosya yazılabilir değil."), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(L10n.s("Vazgeç"), null).create();
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
            Toast.makeText(this, L10n.s("Önce bir karakter oluştur."), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[characters.size()];
        for (int i = 0; i < characters.size(); i++) names[i] = characters.get(i).name;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(L10n.s("Hangi karakter?"))
                .setItems(names, (d, which) -> {
                    CharacterProfile character = characters.get(which);
                    File avatarDirectory = new File(getFilesDir(), "avatars");
                    if (!avatarDirectory.exists() && !avatarDirectory.mkdirs()) {
                        Toast.makeText(this, L10n.s("Avatar kopyası yazılamadı."), Toast.LENGTH_LONG).show();
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
                        Toast.makeText(this, L10n.s("Avatar kopyası yazılamadı."), Toast.LENGTH_LONG).show();
                        return;
                    }
                    character.avatarPath = target.getPath();
                    if (!store.saveCharacter(character)) {
                        target.delete(); // an unsaved copy must not pile up in storage
                        Toast.makeText(this, L10n.s("Avatar kaydedilemedi: karakter verisi okunamıyor."), Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(this, character.name + L10n.s(" için avatar güncellendi."), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(L10n.s("Vazgeç"), null).create();
        tintDialog(dialog);
        dialog.show();
    }

    /** Re-attaches a gallery image to any chat as the user's photo message. */
    private void attachGalleryImage(File image) {
        List<Group> groups = store.groups();
        if (groups.isEmpty()) {
            Toast.makeText(this, L10n.s("Önce bir sohbet oluştur."), Toast.LENGTH_SHORT).show();
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
                .setNegativeButton(L10n.s("Vazgeç"), null).create();
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
            Toast.makeText(this, L10n.s("Önce yürüyen yanıtı bekleyin."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (chatStatusView != null) {
            chatStatusView.setText(LocalImageEngine.INSTANCE.loadedModelName() + L10n.s(" · görsel üretiliyor…"));
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
                : L10n.s("Anlatıcı");
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
                    chatStatusView.setText(L10n.s("Görsel üretiliyor · model bu cihazda çalışıyor") + progress);
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
            Toast.makeText(this, L10n.s("Model yükleniyor; bitmesini bekle."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) {
            Toast.makeText(this, L10n.s("Motor testi sürüyor; bitmesini bekle."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalImageEngine.INSTANCE.isGenerating() || LocalImageEngine.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s("Görsel motoruyla bir işlem sürüyor; bitmesini bekle."), Toast.LENGTH_SHORT).show();
            return;
        }
        forgetAutoAttempts(group.id);
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
            Toast.makeText(this, L10n.s("Motor fotoğrafları göremez; görsel yalnızca sohbet kaydında saklanır."), Toast.LENGTH_LONG).show();
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
                    .setTitle(L10n.s("Önce model gerekli"))
                    .setMessage(L10n.s("Yanıt üretmek için AI ayarlarından bir GGUF modeli seçip yükle. Mesajın kaydedildi; model hazır olunca yanıtlanacak."))
                    .setPositiveButton(L10n.s("AI ayarları"), (dialog, which) -> openModels(() -> openChat(group.id)))
                    .setNegativeButton(L10n.s("Tamam"), null).show();
            return;
        }
        CharacterProfile speaker = chooseSpeaker(group, text);
        if (speaker != null) beginGeneration(group, speaker);
    }

    private void continueOrRetry(Group group) {
        if (generatingChatId != null) return;
        if (LocalImageEngine.INSTANCE.isGenerating() || LocalImageEngine.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s("Görsel motoruyla bir işlem sürüyor; bitmesini bekle."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s("Model yükleniyor; bitmesini bekle."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) {
            Toast.makeText(this, L10n.s("Motor testi sürüyor; bitmesini bekle."), Toast.LENGTH_SHORT).show();
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
        if (speaker != null) beginGeneration(group, speaker);
    }

    private void maybeAutoGenerate(Group group) {
        if (!LocalAiRuntime.INSTANCE.isModelReady() || generatingChatId != null) return;
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading() || LocalAiRuntime.INSTANCE.isBenchmarking()) return;
        if (LocalImageEngine.INSTANCE.isGenerating() || LocalImageEngine.INSTANCE.isModelLoading()) return;
        List<Message> history = store.messages(group.id);
        if (history.isEmpty() || !history.get(history.size() - 1).user) return;
        String key = group.id + ":" + history.size();
        if (autoAttempted.contains(key)) return;
        CharacterProfile speaker = chooseSpeaker(group, history.get(history.size() - 1).text);
        if (speaker == null) return;
        content.post(() -> {
            if (group.id.equals(visibleChatId) && generatingChatId == null && LocalAiRuntime.INSTANCE.isModelReady()) {
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

        String prompt = buildRoleplayPrompt(group, speaker, continuation);
        boolean freshContext = !LocalAiRuntime.INSTANCE.hasConversation(group.id);
        int contextBudget = LocalAiRuntime.INSTANCE.contextCharBudget();
        long contextUsed = freshContext ? 0L : Math.max(0L, contextChars.getOrDefault(group.id, 0L));
        if (!freshContext && contextUsed + prompt.length() > contextBudget) {
            // Keep the model's native context bounded; rebuild from stored recent history.
            LocalAiRuntime.INSTANCE.resetConversation(group.id);
            contextUsed = 0L;
            prompt = buildRoleplayPrompt(group, speaker, continuation);
        }
        contextChars.put(group.id, contextUsed + prompt.length());
        int maxTokens = prefs().getInt("max_output_tokens", 320);
        LocalAiRuntime.INSTANCE.generate(group.id, prompt, maxTokens, new LocalAiRuntime.GenerationListener() {
            private final StringBuilder response = new StringBuilder();
            /** Last draft-save heartbeat (throttled ≥1.5 s; 0 = save on first token). */
            private long lastDraftSave;

            private void finish(boolean stopped, String error) {
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
                Group visible = visibleChatId == null ? null : store.group(visibleChatId);
                if (visible != null) updateChatControls(visible);
                if (group.id.equals(visibleChatId) && chatStatusView != null) {
                    if (error != null) chatStatusView.setText(finalText.isEmpty() ? L10n.s("Yanıt üretilemedi") : L10n.s("Yanıt yarıda kesildi"));
                    else if (finalText.isEmpty()) chatStatusView.setText(L10n.s("Model boş yanıt döndürdü"));
                    else if (stopped) chatStatusView.setText(L10n.s("Yanıt durduruldu."));
                }
                if (error != null && finalText.isEmpty() && group.id.equals(visibleChatId) && uiAlive()) {
                    AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this)
                            .setTitle(L10n.s("Yanıt üretilemedi"))
                            .setMessage(error);
                    if (modelCanRespond()) {
                        dialog.setPositiveButton(L10n.s("Yeniden dene"), (d, w) -> continueOrRetry(group))
                                .setNegativeButton(L10n.s("Kapat"), null);
                    } else {
                        dialog.setPositiveButton(L10n.s("AI ayarları"), (d, w) -> openModels(() -> openChat(group.id)))
                                .setNegativeButton(L10n.s("Kapat"), null);
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
                    chatStatusView.setText(speaker.name + " · " + message);
                }
            }

            @Override public void onToken(String token) {
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
                // Streamed interim text uses the SAME cleanup as the final message, so the
                // echoed "Name:" prefix and engine token residue never become visible.
                String streamed = cleanResponse(response.toString(), speaker.name);
                if (!group.id.equals(visibleChatId) || content == null) return;
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
                if (tokenCount <= 0 || totalMs <= 0) return;
                double seconds = totalMs / 1000.0;
                String summary = String.format(Locale.getDefault(),
                        "%.1f sn · %d token · %.1f tok/s", seconds, tokenCount, tokenCount / seconds);
                chatMetrics.put(group.id, summary);
            }
        });
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
            chatSpeakerView.setText(L10n.s("Sıra: ") + (manual == null ? L10n.s("Otomatik") : manual.name) + "  ▾");
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
                ? L10n.s("↻  ") + speaker.name + L10n.s(" yanıtlasın")
                : L10n.s("▶  Sıradaki: ") + speaker.name + L10n.s(" konuşsun"));
    }

    private void showSpeakerPicker(Group group) {
        List<CharacterProfile> characters = new ArrayList<>();
        for (String id : group.characterIds) {
            CharacterProfile character = store.character(id);
            if (character != null) characters.add(character);
        }
        String[] labels = new String[characters.size() + 1];
        labels[0] = L10n.s("Otomatik (sırayla)");
        for (int i = 0; i < characters.size(); i++) labels[i + 1] = characters.get(i).name;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(L10n.s("Sıradaki konuşmacı"))
                .setItems(labels, (d, which) -> {
                    if (which == 0) clearManualSpeaker(group.id);
                    else setManualSpeaker(group.id, characters.get(which - 1).id);
                    updateChatControls(group);
                })
                .setNegativeButton(L10n.s("Vazgeç"), null).create();
        tintDialog(dialog);
        dialog.show();
    }

    private void showChatMenu(Group group) {
        String[] items = {L10n.s("RP ayarları"), L10n.s("AI ayarları"), L10n.s("Sohbette ara"),
                L10n.s("Sohbet geçmişini temizle"), L10n.s("Sohbet listesine dön")};
        AlertDialog dialog = new AlertDialog.Builder(this).setItems(items, (d, which) -> {
            if (which == 0) showGroupEditor(group, () -> openChat(group.id));
            else if (which == 1) openModels(() -> openChat(group.id));
            else if (which == 2) showChatSearch(group);
            else if (which == 3) clearChat(group);
            else showChats();
        }).create();
        tintDialog(dialog);
        dialog.show();
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
                .setNegativeButton(L10n.s("Vazgeç"), null)
                .create();

        Runnable[] refreshHolder = new Runnable[1];
        refreshHolder[0] = () -> {
            results.removeAllViews();
            String term = query.getText().toString().trim();
            if (term.length() < 2) {
                results.addView(label(L10n.s("En az iki harf yaz; tüm sohbet geçmişinde aranır."), 13, Design.muted()));
                return;
            }
            String haystack = searchText(term);
            int found = 0;
            List<Message> history = store.messages(group.id);
            for (int i = 0; i < history.size() && found < 40; i++) {
                Message message = history.get(i);
                String body = (message.text == null || message.text.isEmpty()) && message.hasImage()
                        ? L10n.s("[görsel]") : message.text;
                String rendered = searchText(body + " " + (message.user ? "sen" : message.speaker));
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
                results.addView(label(L10n.s("Eşleşme yok."), 13, Design.muted()));
            }
        };
        query.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) { refreshHolder[0].run(); }
        });
        refreshHolder[0].run();
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
            new AlertDialog.Builder(this).setMessage(L10n.s("Yanıt üretilirken sohbet temizlenemez. Önce yanıtı durdur."))
                    .setPositiveButton(L10n.s("Tamam"), null).show();
            return;
        }
        confirm(L10n.s("Bu sohbetin tüm mesajları silinsin mi?"), L10n.s("Temizle"), () -> {
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
            return L10n.speaker(generatingSpeaker == null ? L10n.s("Bir karakter") : generatingSpeaker) + L10n.s("yanıt yazıyor…");
        }
        if (generatingChatId != null) return L10n.s("Başka bir sohbette AI yanıt üretiyor…");
        if (LocalAiRuntime.INSTANCE.isModelLoading()) return L10n.s("Model yükleniyor…");
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) return L10n.s("Motor testi sürüyor…");
        if (LocalAiRuntime.INSTANCE.isModelReady()) {
            int percent = contextPercent(group);
            String metrics = chatMetrics.get(group.id);
            if (metrics != null) return L10n.s("Son yanıt: ") + metrics + L10n.s(" · bağlam %") + percent;
            return L10n.s("Sahne hazır · bağlam %") + percent + L10n.s(" · çevrimdışı");
        }
        if (LocalAiRuntime.INSTANCE.loadedModelPath() != null) {
            return L10n.s("Model yeniden hazırlanacak · yanıt biraz sürebilir");
        }
        return selectedModel == null ? L10n.s("Yanıtlar için bir GGUF modeli seç") : L10n.s("Model seçildi · yüklemek için AI ayarları");
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
        if (groups.isEmpty()) addEmpty("Burada henüz RP yok", "Bire bir hikâye başlat veya grup kur.", null, null);
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
        setPage("Ayarlar", "Dil · model · yanıt tarzı · görünüm · veri", "settings");

        addSection("Dil / Language", null, null);
        addBody("Uygulama arayüzü ile AI rol yapma dilini birlikte değiştirir. English mode: better RP with English-tuned models.");
        addSecondary(L10n.s("Türkçe") + (prefs().getString("app_language", "tr").equals("tr") ? "  ✓" : ""), v -> switchAppLanguage("tr"));
        addSecondary("English (recommended: better RP)" + (prefs().getString("app_language", "tr").equals("en") ? "  ✓" : ""), v -> switchAppLanguage("en"));

        addSection("Model", null, null);
        addInfoCard(selectedModelName, modelStatus, "GGUF");
        addSecondary("Modeli yükle ve yönet", v -> openModels(this::showSettings));
        addSecondary("Model önerilerini gör ve indir", v -> showModelCatalog());

        addSection("Yanıt tarzı", null, null);
        String themeId = prefs().getString("ai_theme", DEFAULT_AI_THEME);
        addInfoCard(aiThemeName(themeId), aiThemeDescription(themeId), "Sahne atmosferi");
        addSecondary("Prompt dilleri ve talimatlar", v -> showPromptStyle());
        addSecondary("AI temasını değiştir", v -> showThemePicker());
        addSecondary("Sahne hafızası ve zincir", v -> showAiAdvanced());

        addSection("Görünüm", null, null);
        addSecondary("GUI temasını değiştir", v -> showAppearance());
        addSecondary("Görsel galerisi", v -> showGallery(this::showSettings));

        addSection("Veri yedeği", null, null);
        addInfoCard("Yedekleme", L10n.s("Karakterler, sohbetler ve geçmişler tek bir .json dosyasına yazılır. ")
                + L10n.s("Dosya tamamen senin kontrolündedir: uygulama hiçbir veriyi otomatik göndermez."), "Yerel");
        addPrimary("Yedeği dışa aktar", v -> {
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(new Date());
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "localrp-yedek-" + stamp + ".json");
            startActivityForResult(intent, BACKUP_EXPORT);
        });
        addSecondary("Yedeği geri yükle", v -> confirm(
                L10n.s("Geri yükleme aynı kimlikli karakter/sohbetleri günceller, yeni olanları ekler. ") +
                        L10n.s("Geçmişler yalnızca yedekteki kopya daha uzunsa değiştirilir. Devam edilsin mi?"),
                () -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(intent, BACKUP_IMPORT);
                }));
    }

    private void showModels() {
        backAction = modelsBackAction != null ? modelsBackAction : this::showSettings;
        setPage("Model yönetimi", "GGUF seç, yükle ve belleği izle", "settings");
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

        addSection("Çalışma durumu", null, null);
        addInfoCard("Cihaz içi AI", "GGUF modeli llama.cpp ile bu telefonda çalışır. Modeli yükledikten sonra sohbet internet olmadan da sürer.", "Çevrimdışı");
        addSection("Model dosyası", null, null);
        modelStatusView = addInfoCard(selectedModelName, modelStatus, "GGUF modeli");
        LocalAiRuntime.ModelInfo info = LocalAiRuntime.INSTANCE.getModelInfo();
        if (info != null) {
            addInfoCard(modelInfoTitle(info), modelInfoText(info), "GGUF bilgisi");
        }
        addPrimary("Telefondan GGUF seç", v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, MODEL_PICKER);
        }).setEnabled(!busy);
        if (selectedModel != null) {
            boolean ready = LocalAiRuntime.INSTANCE.isModelReady();
            String label = busy ? (benchmarking ? "Motor testi sürüyor…" : "Model yükleniyor…")
                    : (ready ? "Modeli bellekten kaldır" : "Seçili modeli yükle");
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
            addBody(L10n.s("Cihazdaki model kopyaları: ") + storedModels.size() + L10n.s(" dosya · ") + humanSize(totalBytes));
            addSecondary("Model kopyalarını yönet", v -> showStoredModels());
        }
        addBody("Model dosyası uygulamanın özel alanına kopyalanır; seçtiğin kaynak dosya silinmez.");

        SharedPreferences prefs = prefs();

        addSection("Yanıt biçimi", null, null);
        addBody("Yanıt uzunluğu");
        int savedLength = prefs.getInt("max_output_tokens", 320);
        TextView lengthLabel = label(savedLength + " token", 14, Design.text());
        content.addView(lengthLabel);
        SeekBar length = new SeekBar(this);
        length.setMax(30);
        length.setProgress(Math.max(0, Math.min(30, (savedLength - 64) / 32)));
        length.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int tokens = 64 + progress * 32;
                lengthLabel.setText(tokens + " token");
                if (fromUser) prefs.edit().putInt("max_output_tokens", tokens).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(length, fullWidth());
        addBody("Düşük değer daha kısa yanıt verir. Bu motor sürümü temperature/top-p ayarlarını dışa açmıyor; yanıt uzunluğu ve sahne temasıyla yönlendirme yapılabilir.");
        addBody("Sohbet değiştirildiğinde model bağlamı sıfırlanır; o sohbetteki ilk yanıt biraz daha uzun sürebilir.");

        addSection("Motor testi", null, null);
        if (benchmarking) {
            addBody("Cihaz hızı ölçülüyor. Test bitince sonuç burada gösterilecek.");
        } else if (LocalAiRuntime.INSTANCE.isModelReady()) {
            addSecondary("Hız testini başlat", v -> runBenchmark());
            addBody("Küçük bir prompt işleme ve 16 token üretme testi çalıştırır; cihazın yaklaşık hızını gösterir.");
        } else {
            addBody("Hız testi için önce modeli yükle.");
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
        showSettings();
        Toast.makeText(this, language.equals(L10n.s("en"))
                ? "Yanıt dili İngilizce olarak ayarlandı — model promptları artık İngilizce."
                : "Dil Türkçe olarak ayarlandı.", Toast.LENGTH_SHORT).show();
    }

    private void showAppearance() {
        visibleChatId = null;
        backAction = this::showSettings;
        setPage("Görünüm", "Uygulamanın temasını seç", "settings");
        addInfoCard("Tema sistemi", "Her tema kartları, sohbet balonlarını, gezinmeyi ve sistem çubuklarını tek seferde boyar.", "GUI");
        addBody("Seçim anında uygulanır; model bağlamı etkilenmez.");
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
            TextView kind = label(candidate.dark ? "Koyu palet" : "Açık palet", 12, Design.muted());
            kind.setPadding(0, dp(2), 0, 0);
            texts.addView(kind);
            top.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (selected) {
                TextView badge = label("GÜNCEL", 10, Design.accent());
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
            TextView hero = label("Sahne", 11, Design.heroSub());
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
                Toast.makeText(this, candidate.name + L10n.s(" teması uygulandı."), Toast.LENGTH_SHORT).show();
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
        heroText.setText(L10n.s("Hikâyen burada başlıyor."));
        heroText.setTextSize(12);
        heroText.setTextColor(theme.heroText);
        heroText.setPadding(dp(10), dp(7), dp(10), dp(7));
        heroText.setBackground(heroBg);
        preview.addView(heroText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView heroSub = label("Konuşma balonları · sahneler", 11, theme.heroSub);
        heroSub.setPadding(dp(2), dp(5), 0, 0);
        preview.addView(heroSub);

        TextView charBalloon = new TextView(this);
        charBalloon.setTextSize(12);
        charBalloon.setTextColor(theme.text);
        charBalloon.setPadding(dp(10), dp(7), dp(10), dp(7));
        charBalloon.setBackground(rounded(theme.cardBg, theme.cardBg, 12));
        SpannableString charText = new SpannableString("Nova · 20:41\nGüzel bir gece…");
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
     * Fit verdict chip (GUI review item 16): downloaded size + RAM-based comfort.
     * Loaded GGUF files occupy roughly their file size plus KV-cache/runtime overhead,
     * so ~3× the file must fit in RAM. 0 total RAM (unknown) stays neutral.
     */
    private TextView catalogFitChip(long bytes) {
        long totalMemory = deviceTotalMemory();
        boolean known = bytes > 0 && totalMemory > 0;
        boolean fits = known && totalMemory / 3 >= bytes;
        TextView chip = new TextView(this);
        chip.setTextSize(12);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(9), dp(4), dp(9), dp(4));
        chip.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 10));
        if (!known) {
            chip.setTextColor(Design.muted());
            chip.setText(L10n.s("İndirme ") + humanSize(bytes));
            return chip;
        }
        LinearLayout.LayoutParams self = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        self.setMargins(dp(7), 0, 0, 0);
        chip.setLayoutParams(self);
        if (fits) {
            chip.setTextColor(Design.accent());
            chip.setText(L10n.s("İndirme ") + humanSize(bytes) + L10n.s("  ·  bu cihazda rahat"));
        } else {
            chip.setTextColor(Design.danger());
            chip.setBackground(rounded(Design.dangerSoft(), Design.dangerStroke(), 10));
            chip.setText(L10n.s("İndirme ") + humanSize(bytes) + L10n.s("  ·  RAM'e göre riskli"));
        }
        return chip;
    }

    private void showModelCatalog() {
        visibleChatId = null;
        backAction = this::showModels;
        setPage("Model önerileri", "CPU ve GPU dengelerine göre GGUF seç ve indir", "settings");
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
            addBody(L10n.s("Görsel modeller bu cihazda çalışır: model yüklendikten sonra sohbette ✨ düğmesiyle resim üretebilirsin. Her şey çevrimdışı işler."));
            ModelCatalog.ImageModel pick = ModelCatalog.IMAGE_MODELS[0];
            addImageCatalogCard(pick, true, false);
            for (ModelCatalog.ImageModel model : ModelCatalog.IMAGE_MODELS) if (model != pick) addImageCatalogCard(model, false, false);
        } else {
            TextView warning = label(L10n.s("Sansürsüz modeller abliterated (filtre katmanı çıkarılmış) eğitimlidir; 18 yaş üstü içindir."), 13, Design.danger());
            warning.setPadding(0, dp(4), 0, dp(8));
            content.addView(warning);
            addBody(L10n.s("Uygulama hiçbir sohbet verisini genel ağa göndermez; çıktı işleme tamamen bu cihazda çalışır. Sorumluluk kullanıcıya aittir."));
            ModelCatalog.Model pick = ModelCatalog.UNCENSORED_MODELS[0];
            addCatalogCard(pick, true);
            for (ModelCatalog.Model model : ModelCatalog.UNCENSORED_MODELS) if (model != pick) addCatalogCard(model, false);
            TextView imageHeader = label(L10n.s("Görsel modeller (18+)"), 13, Design.text());
            imageHeader.setPadding(0, dp(14), 0, dp(8));
            imageHeader.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            content.addView(imageHeader);
            ModelCatalog.ImageModel imagePick = ModelCatalog.UNCENSORED_IMAGE_MODELS[0];
            addImageCatalogCard(imagePick, true, true);
            for (ModelCatalog.ImageModel model : ModelCatalog.UNCENSORED_IMAGE_MODELS) if (model != imagePick) addImageCatalogCard(model, false, true);
        }
        addBody("İndirmeler Hugging Face'den doğrudan olur ve uygulamanın özel alanına kaydedilir. Parola veya hesap gerekmez.");
        addBody("GPU hızları * Vulkan/MLC tabanlı motorlar içindir; bu sürümün motoru llama.cpp CPU ile çalışır.");
    }

    private void addDeviceCard() {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo memory = new android.app.ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(memory);
        long totalMb = memory.totalMem / 1_048_576L;
        long freeMb = memory.availMem / 1_048_576L;
        int cores = Runtime.getRuntime().availableProcessors();
        addInfoCard("Cihaz analizi",
                L10n.s("Bellek: ") + humanSize(totalMb * 1_048_576L) + L10n.s(" · boş: ") + humanSize(freeMb * 1_048_576L)
                        + "\n" + L10n.s("CPU çekirdeği: ") + cores,
                "Bu cihaz");
    }

    private void addSegmentedCatalog() {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(2), 0, dp(8));
        String[] labels = {"CPU önerileri", "GPU önerileri",
                L10n.s("Görsel"),
                L10n.s("Sansürsüz") + (uncensoredUnlocked() ? "" : "  🔒")};
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
        TextView warning = label(L10n.s("UYARI: Bu modellerde güvenlik filtresi yoktur; 18 yaş altına uygun içerik koruması içermez."), 13, Design.danger());
        warning.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        box.addView(warning);
        TextView notice = label(L10n.s("İşleme tamamen bu cihazda, çevrimdışı çalışır; sorumluluk sana aittir."),
                14, Design.text());
        notice.setPadding(0, dp(10), 0, 0);
        box.addView(notice);
        final CheckBox acknowledged = new CheckBox(this);
        acknowledged.setText(L10n.s("Uyarıyı okudum; 18 yaş üstü olduğumu onaylıyorum."));
        acknowledged.setTextSize(13);
        acknowledged.setTextColor(Design.text());
        box.addView(acknowledged);
        final EditText ageInput = new EditText(this);
        ageInput.setHint(L10n.s("Yaşın"));
        ageInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        ageInput.setTextSize(15);
        ageInput.setTextColor(Design.text());
        box.addView(ageInput);
        final String phrase = L10n.s("onaylıyorum");
        TextView phraseHint = label(L10n.s("Onaylamak için kelimeyi yaz: ") + phrase, 13, Design.text());
        phraseHint.setPadding(0, dp(12), 0, dp(4));
        box.addView(phraseHint);
        final EditText phraseInput = new EditText(this);
        phraseInput.setHint(phrase);
        phraseInput.setTextSize(15);
        phraseInput.setTextColor(Design.text());
        phraseInput.setHintTextColor(Design.muted());
        box.addView(phraseInput);
        TextView sessionNote = label(L10n.s("Açılma bu oturum için geçerlidir; uygulama kapandığında yeniden sorulur."), 12, Design.muted());
        sessionNote.setPadding(0, dp(10), 0, 0);
        box.addView(sessionNote);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(L10n.s("Sansürsüz modeller (18+)"))
                .setView(box)
                .setPositiveButton(L10n.s("Onayla"), null)
                .setNegativeButton(L10n.s("Vazgeç"), null)
                .create();
        final android.os.Handler countdown = new android.os.Handler();
        dialog.setOnShowListener(d -> {
            final Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            final int[] secondsLeft = {10};
            confirm.setEnabled(false);
            confirm.setText(L10n.s("Onayla") + " (" + secondsLeft[0] + ")");
            final Runnable tick = new Runnable() {
                @Override public void run() {
                    secondsLeft[0]--;
                    if (secondsLeft[0] <= 0) {
                        confirm.setEnabled(true);
                        confirm.setText(L10n.s("Onayla"));
                    } else {
                        confirm.setText(L10n.s("Onayla") + " (" + secondsLeft[0] + ")");
                        countdown.postDelayed(this, 1000);
                    }
                }
            };
            countdown.postDelayed(tick, 1000);
            confirm.setOnClickListener(v -> {
                if (!acknowledged.isChecked()) {
                    Toast.makeText(this, L10n.s("Önce uyarıyı onaylaman gerekli."), Toast.LENGTH_SHORT).show();
                    return;
                }
                int age = -1;
                try {
                    String value = ageInput.getText().toString().trim();
                    if (!value.isEmpty()) age = Integer.parseInt(value);
                } catch (NumberFormatException ignored) {}
                if (age < 18) {
                    Toast.makeText(this, L10n.s("Bu bölüm yalnızca 18 yaş üstüne açık."), Toast.LENGTH_LONG).show();
                    return;
                }
                if (!phraseInput.getText().toString().trim().equalsIgnoreCase(phrase)) {
                    Toast.makeText(this, L10n.s("Onay kelimesini tam yazmalısın."), Toast.LENGTH_SHORT).show();
                    return;
                }
                uncensoredSessionUnlock = true;
                catalogFilter = pendingCatalogFilter >= 0 ? pendingCatalogFilter : 2;
                pendingCatalogFilter = -1;
                dialog.dismiss();
                showModelCatalog();
                Toast.makeText(this, L10n.s("Sansürsüz bölümü bu oturum için açıldı."), Toast.LENGTH_SHORT).show();
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
            TextView badge = label(L10n.s("SANSÜRSÜZ  ·  18+"), 10, Design.danger());
            badge.setLetterSpacing(.09f);
            card.addView(badge);
        }
        if (highlight) {
            TextView badge = label("BU CİHAZ İÇİN ÖNERİ", 10, Design.accent());
            badge.setLetterSpacing(.09f);
            card.addView(badge);
        }
        TextView name = label(model.name, 17, Design.text());
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        name.setPadding(0, highlight || uncensored ? dp(5) : dp(2), 0, 0);
        card.addView(name);
        TextView meta = label(model.family + "  ·  " + model.quant + "  ·  " + humanSize(model.bytes)
                + L10n.s("  ·  bağlam ") + (model.contextTokens >= 1024 ? (model.contextTokens / 1024) + "K" : String.valueOf(model.contextTokens)) + " token"
                + "  ·  " + model.lang, 12, Design.muted());
        meta.setPadding(0, dp(3), 0, 0);
        card.addView(meta);
        boolean gpuOnly = isGpuOnlyCard(model);
        if (gpuOnly) {
            // Task G5: the bundled closed engine has no Vulkan/GPU backend; these cards
            // must not pretend otherwise. Warning + disabled download/load below.
            TextView warning = label("⚠ Bu motorda çalışmaz — paketli motor yalnızca CPU; Vulkan/MLC motoru gerekir.", 12, Design.danger());
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
            Button cancel = button("İndirmeyi durdur");
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
            TextView ready = label(L10n.s("İndirildi  ·  ") + humanSize(stored.length()) + L10n.s(" hazır"), 12, Design.accent());
            ready.setPadding(0, dp(7), 0, dp(2));
            card.addView(ready);
            Button load = button("Modeli yükle");
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
            Button download = button(gpuOnly ? L10n.s("İndirme kapalı  ·  ") + humanSize(model.bytes) : L10n.s("İndir  ·  ") + humanSize(model.bytes));
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
                        confirm(L10n.s("Yarı kalan bir indirme var (") + humanSize(partial.length())
                                + L10n.s("). İndirme kaldığı yerden devam etsin mi?"), "Kaldığı yerden devam", () -> startCatalogDownload(model));
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
            addBodyWithin(card, L10n.s("Kaydedilen parça: ") + humanSize(partial.length()) + L10n.s(" · devam edince indirilmiş sayılır"));
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
            text.append("  %").append(Math.round(downloadBytes * 100.0 / downloadTotal))
                    .append("  (").append(humanSize(downloadBytes)).append(" / ").append(humanSize(downloadTotal)).append(")");
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
                Toast.makeText(this, L10n.s("Model klasörü açılamadı."), Toast.LENGTH_SHORT).show();
                return;
            }
            modelStatus = L10n.s("İndiriliyor: ") + model.name;
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
                    Toast.makeText(MainActivity.this, L10n.s("İndirme hatası: ") + message, Toast.LENGTH_SHORT).show();
                    if ("settings".equals(currentTab)) showModelCatalog();
                }
            }, model.bytes, model.sha256, null);
            if (!accepted) {
                Toast.makeText(this, L10n.s("Önceki indirme hâlâ sonlanıyor; bir saniye sonra tekrar dene."), Toast.LENGTH_SHORT).show();
            } else {
                if ("settings".equals(currentTab)) {
                    // Swap the card into its downloading state right away
                    // (progress label + cancel button) instead of waiting for %1.
                    showModelCatalog();
                }
            }
        };
        if (metered) {
            new AlertDialog.Builder(this).setTitle(L10n.s("Mobil veri uyarısı"))
                    .setMessage(model.name + L10n.s(" (") + humanSize(model.bytes) + L10n.s(") mobil veriyle indirilecek. Wi-Fi önerilir."))
                    .setPositiveButton(L10n.s("İndir"), (d, w) -> start.run())
                    .setNegativeButton(L10n.s("Vazgeç"), null).show();
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
            TextView badge = label(L10n.s("SANSÜRSÜZ  ·  18+"), 10, Design.danger());
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
        TextView meta = label(model.style + "  ·  " + humanSize(model.bytes)
                + "  ·  ~" + model.steps + " adım", 12, Design.muted());
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
            Button cancel = button(L10n.s("İndirmeyi durdur"));
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
            TextView ready = label(L10n.s("İndirildi  ·  ") + humanSize(stored.length()) + L10n.s(" hazır"), 12, Design.accent());
            ready.setPadding(0, dp(7), 0, dp(2));
            card.addView(ready);
            Button load = button(L10n.s("Görsel modelini yükle"));
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
                TextView loaded = label(L10n.s("Yüklü  ·  sohbette ✨ ile üret"), 12, Design.accent());
                loaded.setPadding(0, dp(6), 0, 0);
                card.addView(loaded);
            }
            LinearLayout.LayoutParams loadParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            loadParams.setMargins(0, dp(10), 0, 0);
            card.addView(load, loadParams);
            Button delete = button(L10n.s("Sil"));
            delete.setTextColor(Design.danger());
            delete.setBackground(rounded(Design.dangerSoft(), Design.dangerStroke(), 12));
            if (active || loadedThis) {
                // Unload first (or wait for generation) before deleting a stored model.
                delete.setEnabled(false);
                delete.setAlpha(.55f);
            } else {
                delete.setOnClickListener(v -> confirm(
                        model.name + L10n.s(" cihazdan silinsin mi? Tekrar kullanmak için yeniden indirilir."),
                        L10n.s("Sil"), () -> {
                            if (LocalImageEngine.INSTANCE.deleteStoredModel(this, stored)) {
                                Toast.makeText(this, model.name + L10n.s(" silindi."), Toast.LENGTH_SHORT).show();
                                if (imageLoadStatus != null) imageLoadStatus = null;
                                showModelCatalog();
                            } else {
                                Toast.makeText(this, L10n.s("Görsel motoruyla bir işlem sürüyor; bitmesini bekle."), Toast.LENGTH_SHORT).show();
                            }
                        }));
            }
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            deleteParams.setMargins(0, dp(8), 0, 0);
            card.addView(delete, deleteParams);
        } else {
            Button download = button(L10n.s("İndir  ·  ") + humanSize(model.bytes));
            download.setTextColor(Design.onAccent());
            download.setBackground(rounded(Design.accent(), Design.accent(), 14));
            download.setOnClickListener(v -> {
                if (partialFile != null) {
                    confirm(L10n.s("Yarı kalan bir indirme var (") + humanSize(partialFile.length())
                            + L10n.s("). İndirme kaldığı yerden devam etsin mi?"), L10n.s("Kaldığı yerden devam"),
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
            addBodyWithin(card, L10n.s("Kaydedilen parça: ") + humanSize(partialFile.length()) + L10n.s(" · devam edince indirilmiş sayılır"));
        }
        addCardToPage(card);
    }

    private String imageDownloadProgressText(ModelCatalog.ImageModel model) {
        StringBuilder text = new StringBuilder(model.name).append(L10n.s(" · indiriliyor…"));
        if (downloadTotal > 0) {
            text.append("  %").append(Math.round(downloadBytes * 100.0 / downloadTotal))
                    .append("  (").append(humanSize(downloadBytes)).append(" / ").append(humanSize(downloadTotal)).append(")");
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
            modelStatus = L10n.s("İndiriliyor: ") + model.name;
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
                    Toast.makeText(MainActivity.this, L10n.s("İndirme hatası: ") + message, Toast.LENGTH_SHORT).show();
                    if ("settings".equals(currentTab)) showModelCatalog();
                }
            }, model.bytes, model.sha256, IMAGE_SUBDIRECTORY);
            if (!accepted) {
                Toast.makeText(this, L10n.s("Önceki indirme hâlâ sonlanıyor; bir saniye sonra tekrar dene."), Toast.LENGTH_SHORT).show();
            } else if ("settings".equals(currentTab)) {
                showModelCatalog();
            }
        };
        if (metered) {
            new AlertDialog.Builder(this).setTitle(L10n.s("Mobil veri uyarısı"))
                    .setMessage(model.name + L10n.s(" (") + humanSize(model.bytes) + L10n.s(") mobil veriyle indirilecek. Wi-Fi önerilir."))
                    .setPositiveButton(L10n.s("İndir"), (d, w) -> start.run())
                    .setNegativeButton(L10n.s("Vazgeç"), null).show();
        } else {
            start.run();
        }
    }

    private void startImageModelLoad(ModelCatalog.ImageModel model, File stored) {
        if (LocalImageEngine.INSTANCE.isModelLoading() || LocalImageEngine.INSTANCE.isGenerating()) {
            Toast.makeText(this, L10n.s("Görsel motoruyla bir işlem sürüyor; bitmesini bekle."), Toast.LENGTH_SHORT).show();
            return;
        }
        imageLoadStatus = L10n.s("Yükleniyor… ilk üretimden önce birkaç dakika sürebilir.");
        if ("settings".equals(currentTab)) showModelCatalog();
        LocalImageEngine.INSTANCE.loadStoredModel(this, stored, new LocalImageEngine.ImageListener() {
            @Override public void onStatus(String message) { }
            @Override public void onReady(File file) {
                if (!uiAlive()) return;
                imageLoadStatus = null;
                LocalImageEngine.INSTANCE.setPreparedStepCount(model.steps);
                Toast.makeText(MainActivity.this,
                        model.name + L10n.s(" yüklendi; sohbette ✨ düğmesiyle görsel üretebilirsin."), Toast.LENGTH_LONG).show();
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
        modelStatus = "Yükleme başlatılıyor…";
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
                modelStatus = L10n.s("Hazır · ") + humanSize(bytes) + L10n.s(" · çevrimdışı");
                if ("settings".equals(currentTab)) showModels();
                Toast.makeText(MainActivity.this, fileName + L10n.s(" yüklendi."), Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                modelLoading = false;
                modelStatus = L10n.s("Yüklenemedi: ") + message;
                if ("settings".equals(currentTab)) showModels();
                new AlertDialog.Builder(MainActivity.this).setTitle(L10n.s("Model yüklenemedi"))
                        .setMessage(message).setPositiveButton(L10n.s("Tamam"), null).show();
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

        addSection("Genel AI talimatı", null, null);
        addBody("Her promptun \"Genel AI talimatı\" satırını seçer: varsayılanlar dile göre belirlenir; özel seçtiğinde kendi metnin kullanılır.");
        addSecondary(L10n.s("Varsayılan (Türkçe)") + ("default".equals(generalMode) && prefs().getString("app_language", "tr").equals("tr") ? "  ✓" : ""), v -> setGeneralPromptSource("default"));
        addSecondary(L10n.s("Varsayılan (English)") + ("default".equals(generalMode) && prefs().getString("app_language", "tr").equals("en") ? "  ✓" : ""), v -> setGeneralPromptSource("default"));
        addSecondary(L10n.s("Özel") + ("custom".equals(generalMode) ? "  ✓" : ""), v -> {
            prefs.edit().putString("general_prompt_source", "custom").apply();
            LocalAiRuntime.INSTANCE.resetConversation(null);
            showPromptStyle();
        });
        if ("custom".equals(generalMode)) {
            EditText instructions = field("AI'nin genel yanıt yönergesi", prefs.getString("ai_instructions", DEFAULT_AI_INSTRUCTIONS), 4);
            addPrimary("Özel talimatı kaydet", v -> {
                prefs.edit().putString("ai_instructions", instructions.getText().toString().trim()).apply();
                Toast.makeText(this, L10n.s("Özel talimat kaydedildi."), Toast.LENGTH_SHORT).show();
            });
        }

        addSection("Sistem promptu", null, null);
        addBody("Motorun dünya görüşünü belirleyen çekirdek prompt; karakter tanımından önce gelir.");
        addSecondary(L10n.s("Motor varsayılanı") + ("engine".equals(sysMode) ? "  ✓" : ""), v -> setSystemPromptSource("engine"));
        addSecondary(L10n.s("Türkçe") + ("tr".equals(sysMode) ? "  ✓" : ""), v -> setSystemPromptSource("tr"));
        addSecondary("English" + ("en".equals(sysMode) ? "  ✓" : ""), v -> setSystemPromptSource("en"));
        addSecondary(L10n.s("Özel") + ("custom".equals(sysMode) ? "  ✓" : ""), v -> {
            prefs.edit().putString("sys_prompt_source", "custom").apply();
            applySystemPrompt();
            LocalAiRuntime.INSTANCE.resetConversation(null);
            showPromptStyle();
        });
        if ("custom".equals(sysMode)) {
            EditText systemEditor = field("Motor kavrayışının çekirdeği",
                    prefs.getString("custom_sys_prompt", LocalAiRuntime.INSTANCE.currentSystemPrompt()), 4);
            addPrimary("Özel sistem promptunu kaydet", v -> {
                prefs.edit().putString("custom_sys_prompt", systemEditor.getText().toString().trim()).apply();
                applySystemPrompt();
                LocalAiRuntime.INSTANCE.resetConversation(null);
                Toast.makeText(this, L10n.s("Sistem promptu kaydedildi; model bağlamı yeniden kurulacak."), Toast.LENGTH_SHORT).show();
                showPromptStyle();
            });
        }
        addBody("Değişiklikler model bağlamını sıfırlar; ilk yanıt biraz daha uzun sürebilir.");
    }

    private void setGeneralPromptSource(String mode) {
        prefs().edit().putString("general_prompt_source", mode).apply();
        LocalAiRuntime.INSTANCE.resetConversation(null);
        Toast.makeText(this, L10n.s("Genel talimat tercihleri güncellendi."), Toast.LENGTH_SHORT).show();
        showPromptStyle();
    }

    private void setSystemPromptSource(String mode) {
        prefs().edit().putString("sys_prompt_source", mode).apply();
        applySystemPrompt();
        LocalAiRuntime.INSTANCE.resetConversation(null);
        Toast.makeText(this, L10n.s("Sistem promptu tercihi güncellendi."), Toast.LENGTH_SHORT).show();
        showPromptStyle();
    }

    private void showAiAdvanced() {
        visibleChatId = null;
        backAction = this::showSettings;
        setPage("Sahne hafızası ve zincir", "Geçmiş derinliği, bağlam ve kendiliğinden devam", "settings");
        SharedPreferences prefs = prefs();
        addSection("Sahne hafızası", "Model bağlamının derinliği", null);
        int depth = prefs.getInt("history_depth", 8);
        addSlider("Geçmiş mesaj sayısı", depth, 2, 14, 1, value -> {
            prefs.edit().putInt("history_depth", value).apply();
        }, value -> value + " mesaj");
        int chars = prefs.getInt("history_chars", 2600);
        addSlider("Geçmiş karakter bütçesi", chars, 1200, 3600, 200, value -> {
            prefs.edit().putInt("history_chars", value).apply();
        }, value -> humanSize(value) + " metin");

        addSection("Bağlam davranışı", null, null);
        addBudgetRow(prefs);

        addSection("Karakter zinciri", null, null);
        int autoChain = prefs.getInt("auto_chain", 0);
        addSlider("Kendiliğinden devam sayısı", autoChain, 0, 3, 1, value -> {
            prefs.edit().putInt("auto_chain", value).apply();
        }, value -> value == 0 ? "Kapalı" : value + " zincir");
        addBody("Zincir açıkken karakter, kendi yanıtından sonra sıradaki karakteri otomatik çağırır. Sahne palazlanır ama token tüketimi artar.");

        addSection("Metin görüntüsü", null, null);
        addToggleRow("Markdown biçimlendirme", "*italik* ve **kalın** baloncuklarda uygulanır",
                "markdown_lite", true);
        addBody("Değişiklikler bir sonraki yanıtta uygulanır.");
        addPrimary("Vazgeç", v -> showSettings());
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
            row.setText(title + L10n.s("  ·  ") + (on ? L10n.s("Açık") : L10n.s("Kapalı"))
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
        addBody("Bağlam bütçesi");
        LinearLayout row = new LinearLayout(this);
        TextView auto = label("Otomatik", 13, !manual ? Design.onAccent() : Design.muted());
        auto.setGravity(Gravity.CENTER);
        auto.setBackground(rounded(manual ? Design.accentSoft() : Design.accent(),
                manual ? Design.accentStroke() : Design.accent(), 12));
        auto.setPadding(dp(10), dp(6), dp(10), dp(6));
        row.addView(auto);
        TextView manualChip = label("Elle", 13, manual ? Design.onAccent() : Design.muted());
        manualChip.setGravity(Gravity.CENTER);
        manualChip.setBackground(rounded(manual ? Design.accent() : Design.accentSoft(),
                manual ? Design.accent() : Design.accentStroke(), 12));
        manualChip.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.setMargins(dp(8), 0, 0, 0);
        row.addView(manualChip, chipParams);
        content.addView(row, fullWidth());
        TextView valueLabel = label(manual ? humanSize(saved) + " karakter" : "modele göre otomatik", 13, Design.text());
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
                valueLabel.setText(humanSize(value) + " karakter");
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
            valueLabel.setText(L10n.s("modele göre otomatik"));
            bar.setEnabled(false);
            bar.setAlpha(.45f);
            manualChip.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 12));
            manualChip.setTextColor(Design.muted());
            auto.setBackground(rounded(Design.accent(), Design.accent(), 12));
            auto.setTextColor(Design.onAccent());
            Toast.makeText(this, L10n.s("Bağlam bütçesi modele göre hesaplanacak."), Toast.LENGTH_SHORT).show();
        });
        manualChip.setOnClickListener(v -> {
            int initial = min;
            prefs.edit().putInt("context_budget_chars", initial).apply();
            LocalAiRuntime.INSTANCE.setManualContextBudget(initial);
            valueLabel.setText(humanSize(initial) + " karakter");
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
            details.append("Mimari: ").append(info.getArchitecture());
        }
        if (info.getQuantization() != null && !"unknown".equalsIgnoreCase(info.getQuantization())) {
            if (details.length() > 0) details.append(" · ");
            details.append("Nicemleme: ").append(info.getQuantization());
        }
        if (info.getContextLength() != null) {
            if (details.length() > 0) details.append(" · ");
            details.append(L10n.s("Eğitim bağlamı: ")).append(info.getContextLength()).append(" token");
        }
        if (info.getSizeLabel() != null && !info.getSizeLabel().trim().isEmpty()
                && !"unknown".equalsIgnoreCase(info.getSizeLabel())) {
            if (details.length() > 0) details.append(" · ");
            details.append(L10n.s("Boyut sınıfı: ")).append(info.getSizeLabel());
        }
        if (details.length() == 0) details.append(L10n.s("GGUF başlığı okundu; ayrıntılı alan bulunamadı."));
        details.append("\n").append(L10n.s("Motor bağlamı en fazla 8192 token; uygulama bütçesi "))
                .append(LocalAiRuntime.INSTANCE.contextCharBudget()).append(" karakter.");
        if (info.getContextLength() != null && info.getContextLength() < 4096) {
            details.append("\n").append(L10n.s("Modelin eğitim bağlamı düşük; uzun sohbetlerde bağlam otomatik sıfırlanabilir."));
        }
        return details.toString();
    }

    private void runBenchmark() {
        if (!LocalAiRuntime.INSTANCE.isModelReady() || modelLoading ||
                LocalAiRuntime.INSTANCE.isModelLoading() || LocalAiRuntime.INSTANCE.isBenchmarking()) return;
        if (generatingChatId != null || LocalAiRuntime.INSTANCE.isGenerating()) {
            Toast.makeText(this, L10n.s("Yanıt üretilirken motor testi başlatılamaz."), Toast.LENGTH_SHORT).show();
            return;
        }
        modelStatus = "Motor testi çalışıyor…";
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
                modelStatus = "Motor testi tamamlandı.";
                if ("settings".equals(currentTab)) showModels();
                refreshVisibleChat(false);
                String text = result == null || result.trim().isEmpty()
                        ? "Motor sonuç döndürmedi." : result.trim();
                new AlertDialog.Builder(MainActivity.this).setTitle(L10n.s("Motor testi sonucu"))
                        .setMessage(text).setPositiveButton(L10n.s("Tamam"), null).show();
            }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                modelStatus = L10n.s("Motor testi hatası: ") + message;
                if ("settings".equals(currentTab)) showModels();
                refreshVisibleChat(false);
                new AlertDialog.Builder(MainActivity.this).setTitle(L10n.s("Motor testi tamamlanamadı"))
                        .setMessage(message).setPositiveButton(L10n.s("Tamam"), null).show();
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
        if (requestCode == BACKUP_EXPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            exportBackup(data.getData());
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
            selectedModelName = name == null || name.trim().isEmpty() ? "Seçilen model dosyası" : name;
            modelStatus = "Seçildi · yükleme başlatılıyor…";
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
                Toast.makeText(this, L10n.s("Fotoğraf açılamadı; başka bir dosya deneyin."), Toast.LENGTH_SHORT).show();
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
                    avatarStatusView.setText(L10n.s("Fotoğraf: ✓ hazır (Kaydet ile saklanır)"));
                    avatarStatusView.setTextColor(Design.accent());
                }
            }
            return;
        }
        if (requestCode == CHAT_PHOTO_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            File saved = saveAvatarCopy(data.getData());
            if (saved == null) {
                Toast.makeText(this, L10n.s("Fotoğraf açılamadı; başka bir dosya deneyin."), Toast.LENGTH_SHORT).show();
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
                name == null || name.isEmpty() ? "Sen" : name, avatarPath, 64);
        pane.addView(face, new LinearLayout.LayoutParams(dp(64), dp(64)));
    }

    /** Streams the full local dataset (characters + groups + transcripts) to the chosen file. */
    private void exportBackup(android.net.Uri target) {
        new Thread(() -> {
            java.util.Map<String, List<Message>> chats = new java.util.HashMap<>();
            List<Group> groups = store.groups();
            for (Group group : groups) chats.put(group.id, new ArrayList<>(store.messages(group.id)));
            PersistenceCodec.Bundle bundle = new PersistenceCodec.Bundle();
            bundle.characters.addAll(store.characters());
            bundle.groups.addAll(groups);
            bundle.chats.putAll(chats);
            String payload = PersistenceCodec.encodeBundle(bundle);
            if (payload.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, L10n.s("Yedek oluşturulamadı (veri okunamıyor)."), Toast.LENGTH_LONG).show());
                return;
            }
            try (java.io.OutputStream output = getContentResolver().openOutputStream(target)) {
                if (output == null) throw new IllegalStateException("Dosya açılamadı.");
                output.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.flush();
            } catch (Exception failure) {
                runOnUiThread(() -> Toast.makeText(this, L10n.s("Yedek yazılamadı: ") + failure.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            runOnUiThread(() -> Toast.makeText(this,
                    L10n.s("Yedek yazıldı · ") + bundle.characters.size() + L10n.s(" karakter · ") + bundle.groups.size() + L10n.s(" sohbet"),
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    private void importBackup(android.net.Uri source) {
        new Thread(() -> {
            String raw;
            try (java.io.InputStream input = getContentResolver().openInputStream(source)) {
                if (input == null) throw new IllegalStateException("Dosya açılamadı.");
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = input.read(chunk)) >= 0) {
                    buffer.write(chunk, 0, read);
                    if (buffer.size() > 256L * 1024 * 1024) { // sanity cap; real backups stay far smaller
                        throw new IllegalStateException("Dosya beklenenden çok büyük.");
                    }
                }
                raw = buffer.toString("UTF-8");
            } catch (Exception failure) {
                runOnUiThread(() -> Toast.makeText(this, L10n.s("Yedek okunamadı: ") + failure.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            PersistenceCodec.Bundle bundle = PersistenceCodec.decodeBundle(raw);
            if (bundle == null) {
                runOnUiThread(() -> Toast.makeText(this, L10n.s("Bu dosya geçerli bir LocalRP yedeği değil."), Toast.LENGTH_LONG).show());
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
                for (List<Message> transcript : bundle.chats.values()) {
                    for (Message m : transcript) {
                        if (m.hasImage()
                                && !new File(m.image).getCanonicalPath().startsWith(filesRoot)) {
                            m.image = "";
                        }
                    }
                }
            } catch (java.io.IOException ignored) { }
            final boolean restored = store.restoreBundle(bundle.characters, bundle.groups, bundle.chats);
            runOnUiThread(() -> {
                if (!restored) {
                    Toast.makeText(this, L10n.s("Geri yükleme yapılamadı: mevcut veriler okunamıyor."), Toast.LENGTH_LONG).show();
                    if (!store.integrityIssues().isEmpty()) reportIntegrityIssues();
                    return;
                }
                Toast.makeText(this,
                        L10n.s("Geri yüklendi · ") + bundle.characters.size() + L10n.s(" karakter · ") + bundle.groups.size() + L10n.s(" sohbet"),
                        Toast.LENGTH_LONG).show();
                showHome();
            });
        }).start();
    }

    private void loadSelectedModel() {
        if (selectedModel == null) { showModels(); return; }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) return;
        modelLoading = true;
        modelStatus = "Yükleme başlatılıyor…";
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
                modelStatus = L10n.s("Hazır · ") + String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0) + L10n.s(" · çevrimdışı");
                if ("settings".equals(currentTab)) showModels();
                Toast.makeText(MainActivity.this, L10n.s("Model hazır."), Toast.LENGTH_SHORT).show();
                refreshVisibleChat(true);
            }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                modelLoading = false;
                modelStatus = L10n.s("Yüklenemedi: ") + message;
                if ("settings".equals(currentTab)) showModels();
                refreshVisibleChat(false);
                new AlertDialog.Builder(MainActivity.this).setTitle(L10n.s("Model yüklenemedi"))
                        .setMessage(message).setPositiveButton(L10n.s("Tamam"), null).show();
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
            Toast.makeText(this, L10n.s("Cihazda kayıtlı model kopyası yok."), Toast.LENGTH_SHORT).show();
            showModels();
            return;
        }
        String loadedPath = LocalAiRuntime.INSTANCE.loadedModelPath();
        String[] labels = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            boolean loaded = file.getAbsolutePath().equals(loadedPath);
            labels[i] = file.getName() + "  ·  " + humanSize(file.length()) +
                    (loaded ? "  ·  yüklü" : "");
        }
        new AlertDialog.Builder(this).setTitle(L10n.s("Model kopyaları"))
                .setItems(labels, (dialog, which) -> {
                    File file = files.get(which);
                    if (file.getAbsolutePath().equals(loadedPath)) {
                        Toast.makeText(this, L10n.s("Bu model şu anda yüklü."), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(this).setTitle(file.getName())
                            .setItems(new String[]{L10n.s("Modeli yükle"), L10n.s("Kopyayı sil")}, (d, choice) -> {
                                if (choice == 0) loadStoredModelFile(file);
                                else confirm(file.getName() + " silinsin mi?", "Sil", () -> {
                                    if (LocalAiRuntime.INSTANCE.deleteStoredModel(this, file)) {
                                        Toast.makeText(this, L10n.s("Model kopyası silindi."), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(this, L10n.s("Model dosyası silinemedi."), Toast.LENGTH_SHORT).show();
                                    }
                                    showModels();
                                });
                            }).show();
                })
                .setNegativeButton(L10n.s("Kapat"), null).show();
    }

    private String humanSize(long bytes) {
        if (bytes >= 1_000_000_000L) return String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0);
        if (bytes >= 1_000_000L) return String.format(Locale.getDefault(), "%.0f MB", bytes / 1_000_000.0);
        return String.format(Locale.getDefault(), "%.0f KB", bytes / 1_000.0);
    }

    private void unloadModel() {
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) return;
        modelLoading = true;
        modelStatus = "Model bellekten kaldırılıyor…";
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
                modelStatus = "Hata: " + message;
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
        return "Seçilen model dosyası";
    }

    private String buildRoleplayPrompt(Group group, CharacterProfile speaker, boolean continuation) {
        // Prompt assembly lives in the pure, unit-tested RoleplayPrompt; MainActivity only
        // resolves the Android-backed inputs (store lookups, prefs, engine context flag).
        boolean english = L10n.english;
        String instructions = resolveGeneralInstructions(english);
        String themeId = prefs().getString("ai_theme", DEFAULT_AI_THEME);
        String[] persona = store.persona();
        String[] personaText = persona != null ? new String[]{persona[0], persona[1]} : null;
        return RoleplayPrompt.build(
                group.name,
                speaker,
                group.characterIds,
                id -> store.character(id),
                store.messages(group.id),
                LocalAiRuntime.INSTANCE.hasConversation(group.id),
                continuation,
                instructions,
                aiThemeName(themeId),
                aiThemeInstruction(themeId),
                historyDepth(),
                historyCharBudget(),
                personaText,
                english);
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
                ? "Özel tema" : prefs().getString("ai_theme_custom_name", ""));
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
                ? "Tema kısıtlaması yok; karakterin doğal dünyası ve sahnenin tonu korunur."
                : instruction;
    }

    private void showThemePicker() {
        if (generatingChatId != null) {
            Toast.makeText(this, L10n.s("Yanıt üretilirken tema değiştirilemez."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s("Model yüklenirken tema değiştirilemez."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) {
            Toast.makeText(this, L10n.s("Motor testi sürerken tema değiştirilemez."), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[AI_THEMES.length + 1];
        String current = prefs().getString("ai_theme", DEFAULT_AI_THEME);
        for (int i = 0; i < AI_THEMES.length; i++) {
            labels[i] = L10n.s(AI_THEMES[i][1]) + (AI_THEMES[i][0].equals(current) ? "  ✓" : "");
        }
        labels[AI_THEMES.length] = L10n.s("Özel tema (kendin yaz)") + ("custom".equals(current) ? "  ✓" : "");
        new AlertDialog.Builder(this).setTitle(L10n.s("AI sahne teması"))
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
                    Toast.makeText(this, AI_THEMES[which][1] + L10n.s(" teması seçildi."), Toast.LENGTH_SHORT).show();
                    showSettings();
                })
                .setNegativeButton(L10n.s("Vazgeç"), null).show();
    }

    /** Full editor for the user's own AI theme: a name plus a prompt instruction. */
    private void showCustomThemeEditor() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(18), dp(22), 0);
        EditText name = new EditText(this);
        name.setHint(L10n.s("Tema adı"));
        name.setText(prefs().getString("ai_theme_custom_name", ""));
        name.setSingleLine(true);
        name.setTextColor(Design.text());
        name.setHintTextColor(Design.muted());
        box.addView(name);
        EditText instruction = new EditText(this);
        instruction.setHint(L10n.s("Sahne talimatı (prompta girer)"));
        instruction.setText(prefs().getString("ai_theme_custom_instruction", ""));
        instruction.setMinLines(3);
        instruction.setGravity(Gravity.TOP);
        instruction.setTextColor(Design.text());
        instruction.setHintTextColor(Design.muted());
        box.addView(instruction);
        new AlertDialog.Builder(this)
                .setTitle(L10n.s("Özel tema"))
                .setView(box)
                .setPositiveButton(L10n.s("Özel temayı kaydet ve uygula"), (d, w) -> {
                    String themeName = name.getText().toString().trim();
                    if (themeName.isEmpty()) {
                        Toast.makeText(this, L10n.s("Bir tema adı gerekli."), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, themeName + L10n.s(" teması uygulandı."), Toast.LENGTH_SHORT).show();
                    showSettings();
                })
                .setNegativeButton(L10n.s("Vazgeç"), null)
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
            LocalAiRuntime.INSTANCE.resetConversation(null);
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
        setPage(title, subtitle, tab, false);
    }

    private void setPage(String title, String subtitle, String tab, boolean chat) {
        currentTab = tab;
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
                back.setContentDescription(L10n.s("Geri"));
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
                back.setContentDescription(L10n.s("Geri"));
                header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
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
            }
            pageRoot.addView(header, fullWidth());
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
        // Light page transition (GUI review item 18): ~120ms fade unifies navigation
        // without inventing a new design language.
        pageRoot.setAlpha(0f);
        pageRoot.animate().alpha(1f).setDuration(120).start();
        Design.applyBars(this);
    }

    /** Dark palettes get the dark cold-start window theme (see onCreate). */
    private boolean isDarkThemeId(String themeId) {
        return "golge".equals(themeId) || "deniz-gecesi".equals(themeId) || "gun-batimi".equals(themeId);
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
        TextView eyebrow = label("KENDİ DÜNYANI KUR", 10, Design.heroSub());
        eyebrow.setLetterSpacing(.12f);
        eyebrow.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
        hero.addView(eyebrow);
        TextView title = label("Hikâyen\nburada başlıyor.", 26, Design.heroText());
        title.setTypeface(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(9), 0, dp(8));
        hero.addView(title);
        TextView detail = label(store.characters().size() + " karakter  ·  " + store.groups().size() + " sohbet", 14, Design.heroSub());
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
            preview = L10n.speaker(generatingSpeaker == null ? L10n.s("Bir karakter") : generatingSpeaker) + L10n.s("yanıt yazıyor…");
        } else if (last == null) {
            preview = "Sahneyi başlatmak için dokun";
        } else {
            // Image-only last message would render as "Speaker: "; show a placeholder.
            String body = (last.text == null || last.text.isEmpty()) && last.hasImage()
                    ? L10n.s("[görsel]") : oneLine(last.text, 90);
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
        // Tapping a grid cell opens the character editor, matching the previous long list.
        card.setOnClickListener(v -> showCharacterEditor(character));
        return card;
    }

    private void addCharacterStrip(List<CharacterProfile> characters) {
        for (CharacterProfile character : characters) addCharacterCard(character);
    }

    private void addCharacterCard(CharacterProfile character) {
        LinearLayout card = makeCard();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View avatar = Avatar.forIdentity(this, character.name, character.avatarPath, 42);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView name = label(character.name, 17, Design.text());
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        texts.addView(name);
        TextView desc = label(brief(character.description), 13, Design.muted());
        desc.setPadding(0, dp(3), 0, 0);
        texts.addView(desc);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);
        card.setOnClickListener(v -> showCharacterEditor(character));
        card.setOnLongClickListener(v -> {
            showCharacterActions(character);
            return true;
        });
        addCardToPage(card);
    }

    /**
     * Character card extras (DeepSeek review item): quick variant copies and a single
     * character JSON export — the full backup stays the standard path.
     */
    private void showCharacterActions(CharacterProfile character) {
        String[] items = {L10n.s("Kopya oluştur"), L10n.s("JSON dışa aktar")};
        new AlertDialog.Builder(this).setTitle(character.name)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) duplicateCharacter(character);
                    else exportCharacterPicker(character);
                })
                .setNegativeButton(L10n.s("Vazgeç"), null).show();
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
            Toast.makeText(this, L10n.s("Kopya oluşturulamadı: karakter verisi okunamıyor."), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, L10n.s("Kopya oluşturuldu."), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, L10n.s("Dışa aktaramadı: karakter bulunamadı."), Toast.LENGTH_LONG).show();
            return;
        }
        List<CharacterProfile> single = new ArrayList<>();
        single.add(character);
        String encoded = PersistenceCodec.encodeCharacters(single);
        if (encoded.isEmpty()) {
            Toast.makeText(this, L10n.s("Dışa aktaramadı: karakter verisi okunamıyor."), Toast.LENGTH_LONG).show();
            return;
        }
        try (java.io.OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new java.io.IOException();
            output.write(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.flush();
            Toast.makeText(this, L10n.s("Karakter dışa aktarıldı."), Toast.LENGTH_SHORT).show();
        } catch (java.io.IOException writeFailed) {
            Toast.makeText(this, L10n.s("Dışa aktarılamadı: dosyaya yazılamadı."), Toast.LENGTH_LONG).show();
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
        TextView name = label("GGUF  ·  Cihaz içi yapay zekâ", 15, Design.text());
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        String statusText = LocalAiRuntime.INSTANCE.isModelReady()
                ? L10n.s("Model hazır · çevrimdışı")
                : (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading() ? "Model yükleniyor…" : modelStatus);
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
                {"＋", "Tek karakterli RP", "Karakter seç, hikâyeyi başlat"},
                {"☷", "Grup RP", "Birden çok karakter aynı sahnede"},
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
            fallback = label(user ? L10n.s("Fotoğraf eklendi") : L10n.s("Görsel üretildi"), 12, Design.muted());
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
        String display = L10n.speaker(speaker);
        String header = at > 0 ? display + "  ·  " + clock.format(new Date(at)) : display;
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
        close.setContentDescription(L10n.s("Kapat"));
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
                    Toast.makeText(this, L10n.s("Görsel açılamadı."), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, L10n.s("Mesaj kopyalandı."), Toast.LENGTH_SHORT).show();
        }
    }

    /** Copy-only menu for balloons that are not stored messages (streaming drafts). */
    private void showMessageActions(String speaker, String text) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(speaker + L10n.s(" · mesaj"))
                .setItems(new String[]{L10n.s("Metni kopyala")}, (d, which) ->
                        copyTextToClipboard(L10n.s("LocalRP mesajı"), text))
                .setNegativeButton(L10n.s("Vazgeç"), null).create();
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
                    ? L10n.s("[görsel]") : message.text;
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
                Toast.makeText(this, L10n.s("Bu mesajın metni yok; görsel kayıtta saklanır."), Toast.LENGTH_SHORT).show();
                return;
            }
            copyTextToClipboard(L10n.s("LocalRP mesajı"), body);
        });
        labels.add(L10n.s("Düzenle"));
        actions.add(() -> editMessage(index));
        labels.add(L10n.s("Sil"));
        actions.add(() -> confirmDeleteMessage(index));
        if (!message.user && index == chatHistory.size() - 1 && !isGeneratingHere()) {
            labels.add(L10n.s("Yeniden üret"));
            actions.add(() -> regenerateLastAction(index));
        }
        String[] items = labels.toArray(new String[0]);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(message.speaker + L10n.s(" · mesaj"))
                .setItems(items, (d, which) -> {
                    if (which >= 0 && which < actions.size()) actions.get(which).run();
                })
                .setNegativeButton(L10n.s("Vazgeç"), null).create();
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
            Toast.makeText(this, L10n.s("Yanıt üretilirken mesaj değiştirilemez. Önce yanıtı durdur."), Toast.LENGTH_SHORT).show();
            return;
        }
        Message message = chatHistory.get(index);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(16), dp(22), 0);
        EditText input = dialogField(L10n.s("Düzenle: ") + message.speaker);
        input.setText(message.text == null ? "" : message.text);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        form.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(L10n.s("Mesajı düzenle"))
                .setView(form)
                .setPositiveButton(L10n.s("Kaydet"), (dialog, which) -> {
                    String newText = input.getText().toString().trim();
                    if (newText.isEmpty() && !message.hasImage()) {
                        Toast.makeText(this, L10n.s("Metin boş kalamaz; silmek için Sil'i kullan."), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!store.updateMessage(group.id, index, newText)) {
                        Toast.makeText(this, L10n.s("Değişiklik kaydedilemedi: sohbet verisi okunamıyor."), Toast.LENGTH_LONG).show();
                        return;
                    }
                    chatHistory.set(index, new Message(message.speaker, newText, message.user, message.at, message.image));
                    // The engine context no longer matches the transcript; a fresh
                    // generation must rebuild it from the edited history.
                    LocalAiRuntime.INSTANCE.resetConversation(group.id);
                    contextChars.remove(group.id);
                    chatMetrics.remove(group.id);
                    chainCounts.remove(group.id);
                    Toast.makeText(this, L10n.s("Mesaj güncellendi."), Toast.LENGTH_SHORT).show();
                    jumpToMessage(group, index);
                })
                .setNegativeButton(L10n.s("Vazgeç"), null).show();
    }

    private void confirmDeleteMessage(int index) {
        if (index < 0 || index >= chatHistory.size()) return;
        Group group = store.group(visibleChatId);
        if (group == null) return;
        if (group.id.equals(generatingChatId)) {
            Toast.makeText(this, L10n.s("Yanıt üretilirken mesaj silinemez. Önce yanıtı durdur."), Toast.LENGTH_SHORT).show();
            return;
        }
        Message message = chatHistory.get(index);
        confirm((message.hasImage()
                ? L10n.s("Bu mesaj ve görseli silinsin mi?")
                : L10n.s("Bu mesaj silinsin mi?")), L10n.s("Sil"), () -> {
                    if (message.hasImage()) deleteMessageImageFile(message.image);
                    if (!store.removeMessage(group.id, index)) {
                        Toast.makeText(this, L10n.s("Silinemedi: sohbet verisi okunamıyor."), Toast.LENGTH_LONG).show();
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

    /**
     * True regenerate: the chosen last AI message and everything after it leaves the
     * transcript, then the turn restarts from the last user message.
     */
    private void regenerateLastAction(int index) {
        Group group = store.group(visibleChatId);
        if (group == null) return;
        if (group.id.equals(generatingChatId)) return;
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading()) {
            Toast.makeText(this, L10n.s("Model yükleniyor; bitmesini bekle."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) {
            Toast.makeText(this, L10n.s("Motor testi sürüyor; bitmesini bekle."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!modelCanRespond()) {
            openModels(() -> openChat(group.id));
            return;
        }
        List<Message> stored = store.messages(group.id);
        for (int i = index; i < stored.size(); i++) {
            if (stored.get(i).hasImage()) deleteMessageImageFile(stored.get(i).image);
        }
        if (!store.removeMessagesFrom(group.id, index)) {
            Toast.makeText(this, L10n.s("Yeniden üretilemedi: sohbet verisi okunamıyor."), Toast.LENGTH_LONG).show();
            return;
        }
        chatMetrics.remove(group.id);
        contextChars.remove(group.id);
        chainCounts.remove(group.id);
        LocalAiRuntime.INSTANCE.resetConversation(group.id);
        forgetAutoAttempts(group.id);
        showChat(group);
        // continueOrRetry starts the fresh turn from the (now last) user message;
        // it stays synchronous, so the auto-generate post queued by showChat sees
        // generatingChatId != null and aborts.
        continueOrRetry(group);
    }

    private void renderMessage(TextView view, String speaker, String text, long at) {
        view.setTag(text);
        String display = L10n.speaker(speaker);
        String header = at > 0 ? display + "  ·  " + clock.format(new Date(at)) : display;
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
    private String brief(String s) { return s == null || s.trim().isEmpty() ? "Açıklama yok" : oneLine(s, 120); }

    private void confirm(String message, Runnable action) { confirm(message, "Sil", action); }

    private void confirm(String message, String positive, Runnable action) {
        tintDialog(new AlertDialog.Builder(this).setMessage(L10n.s(message))
                .setNegativeButton(L10n.s("Vazgeç"), null)
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
