package app.localrp;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
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
    private static final String PREFS = "localrp";
    private static final String DEFAULT_AI_INSTRUCTIONS = "Her zaman sahnenin içinde kal. Seçili karakter olarak konuş ve kullanıcının rolünü üstlenme.";
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
                    "Gerilim, belirsizlik ve görünmeyen tehdit yarat; kan ve şiddeti ölçülü tut."}
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
    private LinearLayout chatComposer;
    private View chatEmptyState;
    private boolean autoScroll = true;
    private Runnable backAction;
    private Runnable modelsBackAction;
    private final Set<String> autoAttempted = new HashSet<>();
    private final java.util.Map<String, Long> contextChars = new java.util.HashMap<>();
    private final java.util.Map<String, String> chatMetrics = new java.util.HashMap<>();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private int catalogFilter;
    private TextView downloadProgressView;
    private long downloadBytes, downloadTotal = -1L;
    private final java.util.Map<String, Integer> chainCounts = new java.util.HashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this, BUILTIN_ROLEPLAY_ID);
        store.migrateToRoleplay();
        if (!store.integrityIssues().isEmpty()) reportIntegrityIssues();
        // Leftover generation drafts (crash mid-stream) become normal messages once, up front.
        restoreInterruptedDrafts();
        Design.refresh(this);
        modelsBackAction = this::showHome;
        SharedPreferences prefs = prefs();
        LocalAiRuntime.INSTANCE.setCustomSystemPrompt(prefs.getString("custom_sys_prompt", null));
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
            if ("characters".equals(key)) parts.add("karakter listesi");
            else if ("groups".equals(key)) parts.add("sohbet listesi");
            else if (key.startsWith("chat_")) parts.add("bir sohbetin geçmişi");
        }
        if (parts.isEmpty()) parts.add("bazı kayıtlar");
        String detail = String.join(", ", parts);
        new AlertDialog.Builder(this)
                .setTitle("Veri okunamadı")
                .setCancelable(false)
                .setMessage("Saklı verilerde bozuk bölümler bulundu (" + detail + "). "
                        + "Koruma gereği bu bölümler okunana kadar yazmaya kapatıldı; hiçbir veri üzerine yazılmadı. "
                        + "İlgili sohbeti temizleyerek o bölümü onarabilirsin.")
                .setPositiveButton("Anlaşıldı", null)
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
            for (Group group : groups) addGroupCard(group);
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
        addPrimary("＋  Yeni karakter", v -> showCharacterEditor(null));
        List<CharacterProfile> characters = store.characters();
        if (characters.isEmpty()) addEmpty("İlk karakterini oluştur", "Adını, kişiliğini ve hikâyedeki yerini belirle.", "Karakter oluştur", v -> showCharacterEditor(null));
        else addCharacterGrid(characters);
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
        setPage(editing ? "Karakteri düzenle" : "Yeni karakter", "Bilgiler bu telefonda saklanır", "characters");
        EditText name = field("Karakter adı", editing ? existing.name : "");
        name.setSingleLine(true);
        EditText description = field("Kısa tanım", editing ? existing.description : "", 3);
        EditText personality = field("Kişilik ve konuşma tarzı", editing ? existing.personality : "", 3);
        EditText instructions = field("Karakter talimatları", editing ? existing.instructions : "", 4);
        EditText examples = field("Örnek diyalog", editing ? existing.examples : "", 3);
        addPrimary("Kaydet", v -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Bir ad gerekli"); return; }
            CharacterProfile character = editing ? existing : new CharacterProfile();
            character.name = name.getText().toString().trim();
            character.description = description.getText().toString().trim();
            character.personality = personality.getText().toString().trim();
            character.instructions = instructions.getText().toString().trim();
            character.examples = examples.getText().toString().trim();
            if (!store.saveCharacter(character)) {
                Toast.makeText(this, "Değişiklik kaydedilemedi: karakter verisi okunamıyor.", Toast.LENGTH_LONG).show();
            }
            if (afterSave != null) afterSave.run(); else showCharacters();
        });
        if (editing && !BUILTIN_ROLEPLAY_ID.equals(existing.id)) {
            addDangerButton("Karakteri sil", v -> confirm(existing.name + " silinsin mi?", () -> {
                if (!store.deleteCharacter(existing.id)) {
                    Toast.makeText(this, "Silinemedi: karakter verisi okunamıyor.", Toast.LENGTH_LONG).show();
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
            if (name.getText().toString().trim().isEmpty()) { name.setError("Sohbet adı gerekli"); return; }
            Group group = editing ? existing : new Group();
            group.name = name.getText().toString().trim();
            group.characterIds.clear();
            for (int i = 0; i < choices.size(); i++) if (choices.get(i).isChecked()) group.characterIds.add(characters.get(i).id);
            if (group.characterIds.isEmpty()) { name.setError("En az bir karakter seç"); return; }
            group.roleplay = true;
            if (!store.saveGroup(group)) {
                Toast.makeText(this, "Değişiklik kaydedilemedi: sohbet verisi okunamıyor.", Toast.LENGTH_LONG).show();
                return;
            }
            showChat(group);
        });
        if (editing) {
            addSection("Sohbet", null, null);
            addDangerButton("Sohbet geçmişini temizle", v -> clearChat(existing));
            addDangerButton("Grubu sil", v -> confirm(existing.name + " ve sohbeti silinsin mi?", "Sil", () -> {
                LocalAiRuntime.INSTANCE.stopGeneration();
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
        new AlertDialog.Builder(this).setTitle("RP karakterini seç")
                .setItems(names, (dialog, index) -> {
                    CharacterProfile character = characters.get(index);
                    Group chat = new Group();
                    chat.name = character.name + " ile RP";
                    chat.characterIds.add(character.id);
                    chat.roleplay = true;
                    store.saveGroup(chat);
                    showChat(chat);
                }).setNegativeButton("Vazgeç", null).show();
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
            if (previous.user) continue;
            for (int j = 0; j < available.size(); j++) {
                if (available.get(j).name.equals(previous.speaker)) return available.get((j + 1) % available.size());
            }
            break;
        }
        return available.get(0);
    }

    private void showChat(Group group) {
        visibleChatId = group.id;
        backAction = this::showChats;
        autoScroll = true;
        String subtitle = chatSubtitle(group);
        setPage(group.name, subtitle, "chat", true);
        chatStatusView.setText(chatStatusText(group));

        // Long chats: opening must not render the whole transcript at once (Task 08).
        // Only the newest messages enter the view; older ones load on demand upward.
        chatOlderButton = null;
        chatHistory = new ArrayList<>(store.messages(group.id));
        chatRenderStart = ChatWindow.initialStart(chatHistory.size(), MAX_INITIAL_RENDER);
        chatEmptyState = null;
        if (chatHistory.isEmpty()) {
            chatEmptyState = addEmpty("RP sahnesi burada başlıyor", "Mesajını yaz; seçili karakter otomatik yanıtlasın.", null, null);
        } else {
            addOlderMessagesButton();
            for (int i = chatRenderStart; i < chatHistory.size(); i++) {
                Message message = chatHistory.get(i);
                addMessage(content, message.speaker, message.text, message.user, message.at);
            }
        }
        buildChatComposer(group);
        scrollChatToBottom(true);
        maybeAutoGenerate(group);
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
        view.setMinHeight(dp(38));
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
        view.setText("Daha eski mesajlar (" + chatRenderStart + ")");
    }

    /** Prepends the next older chunk in chronological order, above the marker button. */
    private void renderOlderChunk() {
        if (chatRenderStart <= 0 || chatOlderButton == null) return;
        int next = ChatWindow.olderStart(chatRenderStart, RENDER_CHUNK);
        int insertAt = content.indexOfChild(chatOlderButton);
        for (int i = next; i < chatRenderStart; i++) {
            Message message = chatHistory.get(i);
            TextView row = addMessage(content, message.speaker, message.text, message.user, message.at);
            content.removeView(row);
            content.addView(row, Math.min(insertAt++, content.getChildCount()));
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
                return "Model yeniden hazırlanacak · çevrimdışı";
            }
            return "Çevrimdışı yanıtlar için önce AI ayarlarından model yükle";
        }
        return group.characterIds.size() == 1 ? "Bire bir karakter RP · cihaz içinde" : "Grup RP · cihaz içinde";
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

        chatSpeakerView = label("", 13, Design.accent());
        chatSpeakerView.setPadding(dp(14), dp(8), dp(14), dp(8));
        chatSpeakerView.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 30));
        chatSpeakerView.setOnClickListener(v -> showSpeakerPicker(group));
        tools.addView(chatSpeakerView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button menu = button("⋯");
        menu.setTextSize(18);
        menu.setTextColor(Design.text());
        menu.setMinWidth(dp(44));
        menu.setMinHeight(dp(38));
        menu.setPadding(dp(12), 0, dp(12), 0);
        menu.setBackground(rounded(Design.cardBg(), Design.cardStroke(), 30));
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
        chatContinueButton.setBackground(rounded(Design.cardBg(), Design.accentStroke(), 30));
        chatContinueButton.setMinHeight(dp(44));
        chatContinueButton.setOnClickListener(v -> continueOrRetry(group));
        LinearLayout.LayoutParams continueParams = fullWidth();
        continueParams.setMargins(0, dp(8), 0, 0);
        chatComposer.addView(chatContinueButton, continueParams);

        EditText input = new EditText(this);
        input.setHint("Hikâyeye bir şey yaz…");
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setGravity(Gravity.TOP);
        input.setTextSize(15);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setTextColor(Design.text());
        input.setHintTextColor(Design.muted());
        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setColor(Design.cardBg());
        inputBackground.setStroke(dp(1), Design.cardStroke());
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

        Button action = button("➤");
        chatActionButton = action;
        action.setTextSize(17);
        action.setPadding(0, 0, 0, 0);
        action.setMinWidth(dp(46));
        action.setMinHeight(dp(46));
        action.setTextColor(Design.onAccent());
        action.setBackground(rounded(Design.accent(), Design.accent(), 30));
        action.setOnClickListener(v -> {
            if (generatingChatId != null) LocalAiRuntime.INSTANCE.stopGeneration();
            else sendMessage(group, input);
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        actionParams.setMargins(dp(8), dp(8), 0, 0);
        composerRow.addView(action, actionParams);
        chatComposer.addView(composerRow, fullWidth());

        updateChatControls(group);
    }

    private void sendMessage(Group group, EditText input) {
        String text = input.getText().toString().trim();
        if (text.isEmpty() || generatingChatId != null) return;
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading()) {
            Toast.makeText(this, "Model yükleniyor; bitmesini bekle.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) {
            Toast.makeText(this, "Motor testi sürüyor; bitmesini bekle.", Toast.LENGTH_SHORT).show();
            return;
        }
        forgetAutoAttempts(group.id);
        store.addMessage(group.id, new Message("Sen", text, true));
        if (chatEmptyState != null && chatEmptyState.getParent() == content) {
            content.removeView(chatEmptyState);
            chatEmptyState = null;
        }
        addMessage(content, "Sen", text, true, System.currentTimeMillis());
        input.setText("");
        chatDrafts.remove(group.id);
        scrollChatToBottom(true);
        updateChatControls(group);

        if (!modelCanRespond()) {
            new AlertDialog.Builder(this)
                    .setTitle("Önce model gerekli")
                    .setMessage("Yanıt üretmek için AI ayarlarından bir GGUF modeli seçip yükle. Mesajın kaydedildi; model hazır olunca yanıtlanacak.")
                    .setPositiveButton("AI ayarları", (dialog, which) -> openModels(() -> openChat(group.id)))
                    .setNegativeButton("Tamam", null).show();
            return;
        }
        CharacterProfile speaker = chooseSpeaker(group, text);
        if (speaker != null) beginGeneration(group, speaker);
    }

    private void continueOrRetry(Group group) {
        if (generatingChatId != null) return;
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading()) {
            Toast.makeText(this, "Model yükleniyor; bitmesini bekle.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) {
            Toast.makeText(this, "Motor testi sürüyor; bitmesini bekle.", Toast.LENGTH_SHORT).show();
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
                    if (displayedResponse != null && displayedResponse.getParent() == content) {
                        content.removeView(displayedResponse);
                    }
                } else if (chatStillExists) {
                    store.addMessage(group.id, new Message(speaker.name, finalText, false, startedAt));
                    if (group.id.equals(visibleChatId) && content != null) {
                        if (displayedResponse == null || displayedResponse.getParent() != content) {
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
                    if (error != null) chatStatusView.setText(finalText.isEmpty() ? "Yanıt üretilemedi" : "Yanıt yarıda kesildi");
                    else if (finalText.isEmpty()) chatStatusView.setText("Model boş yanıt döndürdü");
                    else if (stopped) chatStatusView.setText("Yanıt durduruldu.");
                }
                if (error != null && finalText.isEmpty() && group.id.equals(visibleChatId) && uiAlive()) {
                    AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Yanıt üretilemedi")
                            .setMessage(error);
                    if (modelCanRespond()) {
                        dialog.setPositiveButton("Yeniden dene", (d, w) -> continueOrRetry(group))
                                .setNegativeButton("Kapat", null);
                    } else {
                        dialog.setPositiveButton("AI ayarları", (d, w) -> openModels(() -> openChat(group.id)))
                                .setNegativeButton("Kapat", null);
                    }
                    dialog.show();
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
                if (draft == null || draft.getParent() != content) {
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
            chatSpeakerView.setText("Sıra: " + (manual == null ? "Otomatik" : manual.name) + "  ▾");
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
                ? "↻  " + speaker.name + " yanıtlasın"
                : "▶  Sıradaki: " + speaker.name + " konuşsun");
    }

    private void showSpeakerPicker(Group group) {
        List<CharacterProfile> characters = new ArrayList<>();
        for (String id : group.characterIds) {
            CharacterProfile character = store.character(id);
            if (character != null) characters.add(character);
        }
        String[] labels = new String[characters.size() + 1];
        labels[0] = "Otomatik (sırayla)";
        for (int i = 0; i < characters.size(); i++) labels[i + 1] = characters.get(i).name;
        new AlertDialog.Builder(this).setTitle("Sıradaki konuşmacı")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) clearManualSpeaker(group.id);
                    else setManualSpeaker(group.id, characters.get(which - 1).id);
                    updateChatControls(group);
                })
                .setNegativeButton("Vazgeç", null).show();
    }

    private void showChatMenu(Group group) {
        String[] items = {"RP ayarları", "AI ayarları", "Sohbet geçmişini temizle", "Sohbet listesine dön"};
        new AlertDialog.Builder(this).setItems(items, (dialog, which) -> {
            if (which == 0) showGroupEditor(group, () -> openChat(group.id));
            else if (which == 1) openModels(() -> openChat(group.id));
            else if (which == 2) clearChat(group);
            else showChats();
        }).show();
    }

    private void clearChat(Group group) {
        if (group.id.equals(generatingChatId)) {
            new AlertDialog.Builder(this).setMessage("Yanıt üretilirken sohbet temizlenemez. Önce yanıtı durdur.")
                    .setPositiveButton("Tamam", null).show();
            return;
        }
        confirm("Bu sohbetin tüm mesajları silinsin mi?", "Temizle", () -> {
            store.clearMessages(group.id);
            forgetAutoAttempts(group.id);
            chatMetrics.remove(group.id);
            contextChars.remove(group.id);
            LocalAiRuntime.INSTANCE.resetConversation(group.id);
            showChat(group);
        });
    }

    private String chatStatusText(Group group) {
        if (group.id.equals(generatingChatId)) {
            return (generatingSpeaker == null ? "Karakter" : generatingSpeaker) + " yanıt yazıyor…";
        }
        if (generatingChatId != null) return "Başka bir sohbette AI yanıt üretiyor…";
        if (LocalAiRuntime.INSTANCE.isModelLoading()) return "Model yükleniyor…";
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) return "Motor testi sürüyor…";
        if (LocalAiRuntime.INSTANCE.isModelReady()) {
            int percent = contextPercent(group);
            String metrics = chatMetrics.get(group.id);
            if (metrics != null) return "Son yanıt: " + metrics + " · bağlam %" + percent;
            return "Sahne hazır · bağlam %" + percent + " · çevrimdışı";
        }
        if (LocalAiRuntime.INSTANCE.loadedModelPath() != null) {
            return "Model yeniden hazırlanacak · yanıt biraz sürebilir";
        }
        return selectedModel == null ? "Yanıtlar için bir GGUF modeli seç" : "Model seçildi · yüklemek için AI ayarları";
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
        for (Group group : groups) addGroupCard(group);
    }

    private void openModels(Runnable back) {
        visibleChatId = null;
        modelsBackAction = back != null ? back : this::showHome;
        showModels();
    }

    private void showModels() {
        backAction = modelsBackAction != null ? modelsBackAction : this::showHome;
        setPage("AI ayarları", "Modelini ve yanıt biçimini buradan yönet", "models");
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
            addBody("Cihazdaki model kopyaları: " + storedModels.size() + " dosya · " + humanSize(totalBytes));
            addSecondary("Model kopyalarını yönet", v -> showStoredModels());
        }
        addBody("Model dosyası uygulamanın özel alanına kopyalanır; seçtiğin kaynak dosya silinmez.");

        addSection("Model önerileri", "Katalog", () -> showModelCatalog());
        addInfoCard(ModelCatalog.cpuRecommendation().name + "  ·  CPU önerisi",
                "Bu cihazda metin RP için hız/kalite dengesi iyi. Katalogda GPU önerileri de var; indirme tamamen çevrimiçi bir kez yapılır, oynatma çevrimdışı sürer.",
                "Öneri");
        addSecondary("Model önerilerini gör ve indir", v -> showModelCatalog());

        addSection("Gelişmiş AI", null, null);
        addSecondary("Gelişmiş AI ayarları", v -> showAiAdvanced());
        addBody("Sistem promptunu, geçmiş derinliğini, bağlam bütçesini ve karakter zinciri davranışını buradan ayarla.");

        addSecondary("GUI temasını değiştir", v -> showAppearance());

        addSection("AI teması", null, null);
        SharedPreferences prefs = prefs();
        String themeId = prefs.getString("ai_theme", DEFAULT_AI_THEME);
        addInfoCard(aiThemeName(themeId), aiThemeDescription(themeId), "Sahne atmosferi");
        addSecondary("AI temasını değiştir", v -> showThemePicker());
        addBody("Tema, tüm RP sohbetlerinde sahnenin atmosferini belirler. Değiştirince model bağlamı sıfırlanır.");

        addSection("Yanıt ayarları", null, null);
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
        addBody("Genel AI talimatı");
        EditText instructions = field("AI'nin genel yanıt yönergesi", prefs.getString("ai_instructions", DEFAULT_AI_INSTRUCTIONS), 4);
        addPrimary("Talimatı kaydet", v -> {
            prefs.edit().putString("ai_instructions", instructions.getText().toString().trim()).apply();
            Toast.makeText(this, "AI talimatı kaydedildi.", Toast.LENGTH_SHORT).show();
        });

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

    private void showAppearance() {
        visibleChatId = null;
        backAction = this::showHome;
        setPage("Görünüm", "Uygulamanın temasını seç", "appearance");
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
            card.setOnClickListener(v -> {
                prefs().edit().putString("ui_theme", candidate.id).apply();
                Design.refresh(this);
                Design.applyBars(this);
                Toast.makeText(this, candidate.name + " teması uygulandı.", Toast.LENGTH_SHORT).show();
                showAppearance();
            });
            addCardToPage(card);
        }
        addSection("Veri yedeği", null, null);
        addInfoCard("Yedekleme", "Karakterler, sohbetler ve geçmişler tek bir .json dosyasına yazılır. "
                + "Dosya tamamen senin kontrolündedir: uygulama hiçbir veriyi otomatik göndermez.", "Yerel");
        addPrimary("Yedeği dışa aktar", v -> {
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(new Date());
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "localrp-yedek-" + stamp + ".json");
            startActivityForResult(intent, BACKUP_EXPORT);
        });
        addSecondary("Yedeği geri yükle", v -> confirm(
                "Geri yükleme aynı kimlikli karakter/sohbetleri günceller, yeni olanları ekler. " +
                        "Geçmişler yalnızca yedekteki kopya daha uzunsa değiştirilir. Devam edilsin mi?",
                () -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(intent, BACKUP_IMPORT);
                }));
    }

    private void showModelCatalog() {
        visibleChatId = null;
        backAction = this::showModels;
        setPage("Model önerileri", "CPU ve GPU dengelerine göre GGUF seç ve indir", "models");
        addDeviceCard();
        addSegmentedCatalog();
        if (catalogFilter == 0) {
            ModelCatalog.Model pick = ModelCatalog.cpuRecommendation();
            addCatalogCard(pick, true);
            for (ModelCatalog.Model model : ModelCatalog.CPU_MODELS) if (model != pick) addCatalogCard(model, false);
        } else {
            ModelCatalog.Model pick = ModelCatalog.gpuRecommendation();
            addCatalogCard(pick, true);
            for (ModelCatalog.Model model : ModelCatalog.GPU_MODELS) if (model != pick) addCatalogCard(model, false);
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
                "Bellek: " + humanSize(totalMb * 1_048_576L) + " · boş: " + humanSize(freeMb * 1_048_576L)
                        + "\nCPU çekirdeği: " + cores,
                "Bu cihaz");
    }

    private void addSegmentedCatalog() {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(2), 0, dp(8));
        String[] labels = {"CPU önerileri", "GPU önerileri"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = label(labels[i], 14, i == catalogFilter ? Design.onAccent() : Design.muted());
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            if (i == catalogFilter) chip.setBackground(rounded(Design.accent(), Design.accent(), 14));
            else chip.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 14));
            chip.setOnClickListener(v -> {
                if (catalogFilter != index) {
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
        if (highlight) {
            TextView badge = label("BU CİHAZ İÇİN ÖNERİ", 10, Design.accent());
            badge.setLetterSpacing(.09f);
            card.addView(badge);
        }
        TextView name = label(model.name, 17, Design.text());
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        name.setPadding(0, highlight ? dp(5) : dp(2), 0, 0);
        card.addView(name);
        TextView meta = label(model.family + "  ·  " + model.quant + "  ·  " + humanSize(model.bytes)
                + "  ·  bağlam " + (model.contextTokens >= 1024 ? (model.contextTokens / 1024) + "K" : String.valueOf(model.contextTokens)) + " token"
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
            TextView ready = label("İndirildi  ·  " + humanSize(stored.length()) + " hazır", 12, Design.accent());
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
            Button download = button(gpuOnly ? "İndirme kapalı  ·  " + humanSize(model.bytes) : "İndir  ·  " + humanSize(model.bytes));
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
                        confirm("Yarı kalan bir indirme var (" + humanSize(partial.length())
                                + "). İndirme kaldığı yerden devam etsin mi?", "Kaldığı yerden devam", () -> startCatalogDownload(model));
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
            addBodyWithin(card, "Kaydedilen parça: " + humanSize(partial.length()) + " · devam edince indirilmiş sayılır");
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
                Toast.makeText(this, "Model klasörü açılamadı.", Toast.LENGTH_SHORT).show();
                return;
            }
            modelStatus = "İndiriliyor: " + model.name;
            boolean accepted = ModelDownload.INSTANCE.start(this, model.url, model.fileHint, new ModelDownload.Listener() {
                @Override public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                    if (!uiAlive()) return;
                    downloadBytes = downloadedBytes;
                    downloadTotal = totalBytes;
                    if (!"models".equals(currentTab)) return;
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
                    Toast.makeText(MainActivity.this, model.name + " indirildi.", Toast.LENGTH_SHORT).show();
                    if ("models".equals(currentTab)) showModelCatalog();
                }
                @Override public void onError(String message) {
                    if (!uiAlive()) return;
                    downloadProgressView = null;
                    Toast.makeText(MainActivity.this, "İndirme hatası: " + message, Toast.LENGTH_SHORT).show();
                    if ("models".equals(currentTab)) showModelCatalog();
                }
            }, model.bytes, model.sha256);
            if (!accepted) {
                Toast.makeText(this, "Önceki indirme hâlâ sonlanıyor; bir saniye sonra tekrar dene.", Toast.LENGTH_SHORT).show();
            } else {
                if ("models".equals(currentTab)) {
                    // Swap the card into its downloading state right away
                    // (progress label + cancel button) instead of waiting for %1.
                    showModelCatalog();
                }
            }
        };
        if (metered) {
            new AlertDialog.Builder(this).setTitle("Mobil veri uyarısı")
                    .setMessage(model.name + " (" + humanSize(model.bytes) + ") mobil veriyle indirilecek. Wi-Fi önerilir.")
                    .setPositiveButton("İndir", (d, w) -> start.run())
                    .setNegativeButton("Vazgeç", null).show();
        } else {
            start.run();
        }
    }

    private void loadStoredModelFile(File file) {
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading()) return;
        modelLoading = true;
        modelStatus = "Yükleme başlatılıyor…";
        if ("models".equals(currentTab)) showModels();
        LocalAiRuntime.INSTANCE.loadStoredModel(this, file, new LocalAiRuntime.ModelListener() {
            @Override public void onStatus(String message) {
                if (!uiAlive()) return;
                modelStatus = message;
                if ("models".equals(currentTab) && modelStatusView != null) modelStatusView.setText(message);
            }
            @Override public void onLoaded(String fileName, long bytes) {
                if (!uiAlive()) return;
                modelLoading = false;
                selectedModelName = fileName;
                contextChars.clear();
                chatMetrics.clear();
                modelStatus = "Hazır · " + humanSize(bytes) + " · çevrimdışı";
                if ("models".equals(currentTab)) showModels();
                Toast.makeText(MainActivity.this, fileName + " yüklendi.", Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                modelLoading = false;
                modelStatus = "Yüklenemedi: " + message;
                if ("models".equals(currentTab)) showModels();
                new AlertDialog.Builder(MainActivity.this).setTitle("Model yüklenemedi")
                        .setMessage(message).setPositiveButton("Tamam", null).show();
            }
        });
    }

    private void showAiAdvanced() {
        visibleChatId = null;
        backAction = this::showModels;
        setPage("Gelişmiş AI", "Sistem promptu, geçmiş ve zincir davranışı", "models");
        SharedPreferences prefs = prefs();
        addInfoCard("AI çekirdeği", "Motorun dünya görüşünü belirleyen sistem promptu; karakter tanımından önce gelir.", "Sistem");
        addBody("Çekirdek sistem talimatı");
        EditText systemEditor = field("Motor kavrayışının çekirdeği",
                LocalAiRuntime.INSTANCE.currentSystemPrompt(), 4);
        addPrimary("Sistem promptu kaydet", v -> {
            prefs.edit().putString("custom_sys_prompt", systemEditor.getText().toString().trim()).apply();
            LocalAiRuntime.INSTANCE.setCustomSystemPrompt(systemEditor.getText().toString().trim());
            Toast.makeText(this, "Sistem promptu kaydedildi; model bağlamı yeniden kurulacak.", Toast.LENGTH_SHORT).show();
            showAiAdvanced();
        });

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
        addBody("Değişiklikler bir sonraki yanıtta uygulanır.");
        addPrimary("Vazgeç", v -> showModels());
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
            valueLabel.setText("modele göre otomatik");
            bar.setEnabled(false);
            bar.setAlpha(.45f);
            manualChip.setBackground(rounded(Design.accentSoft(), Design.accentStroke(), 12));
            manualChip.setTextColor(Design.muted());
            auto.setBackground(rounded(Design.accent(), Design.accent(), 12));
            auto.setTextColor(Design.onAccent());
            Toast.makeText(this, "Bağlam bütçesi modele göre hesaplanacak.", Toast.LENGTH_SHORT).show();
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
            details.append("Eğitim bağlamı: ").append(info.getContextLength()).append(" token");
        }
        if (info.getSizeLabel() != null && !info.getSizeLabel().trim().isEmpty()
                && !"unknown".equalsIgnoreCase(info.getSizeLabel())) {
            if (details.length() > 0) details.append(" · ");
            details.append("Boyut sınıfı: ").append(info.getSizeLabel());
        }
        if (details.length() == 0) details.append("GGUF başlığı okundu; ayrıntılı alan bulunamadı.");
        details.append("\nMotor bağlamı en fazla 8192 token; uygulama bütçesi ")
                .append(LocalAiRuntime.INSTANCE.contextCharBudget()).append(" karakter.");
        if (info.getContextLength() != null && info.getContextLength() < 4096) {
            details.append("\nModelin eğitim bağlamı düşük; uzun sohbetlerde bağlam otomatik sıfırlanabilir.");
        }
        return details.toString();
    }

    private void runBenchmark() {
        if (!LocalAiRuntime.INSTANCE.isModelReady() || modelLoading ||
                LocalAiRuntime.INSTANCE.isModelLoading() || LocalAiRuntime.INSTANCE.isBenchmarking()) return;
        if (generatingChatId != null || LocalAiRuntime.INSTANCE.isGenerating()) {
            Toast.makeText(this, "Yanıt üretilirken motor testi başlatılamaz.", Toast.LENGTH_SHORT).show();
            return;
        }
        modelStatus = "Motor testi çalışıyor…";
        if ("models".equals(currentTab)) showModels();
        LocalAiRuntime.INSTANCE.benchmark(new LocalAiRuntime.BenchmarkListener() {
            @Override public void onStatus(String message) {
                if (!uiAlive()) return;
                modelStatus = message;
                if ("models".equals(currentTab)) showModels();
                refreshVisibleChat(false);
            }
            @Override public void onResult(String result) {
                if (!uiAlive()) return;
                modelStatus = "Motor testi tamamlandı.";
                if ("models".equals(currentTab)) showModels();
                refreshVisibleChat(false);
                String text = result == null || result.trim().isEmpty()
                        ? "Motor sonuç döndürmedi." : result.trim();
                new AlertDialog.Builder(MainActivity.this).setTitle("Motor testi sonucu")
                        .setMessage(text).setPositiveButton("Tamam", null).show();
            }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                modelStatus = "Motor testi hatası: " + message;
                if ("models".equals(currentTab)) showModels();
                refreshVisibleChat(false);
                new AlertDialog.Builder(MainActivity.this).setTitle("Motor testi tamamlanamadı")
                        .setMessage(message).setPositiveButton("Tamam", null).show();
            }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BACKUP_EXPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            exportBackup(data.getData());
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
        }
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
                runOnUiThread(() -> Toast.makeText(this, "Yedek oluşturulamadı (veri okunamıyor).", Toast.LENGTH_LONG).show());
                return;
            }
            try (java.io.OutputStream output = getContentResolver().openOutputStream(target)) {
                if (output == null) throw new IllegalStateException("Dosya açılamadı.");
                output.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.flush();
            } catch (Exception failure) {
                runOnUiThread(() -> Toast.makeText(this, "Yedek yazılamadı: " + failure.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            runOnUiThread(() -> Toast.makeText(this,
                    "Yedek yazıldı · " + bundle.characters.size() + " karakter · " + bundle.groups.size() + " sohbet",
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
                runOnUiThread(() -> Toast.makeText(this, "Yedek okunamadı: " + failure.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            PersistenceCodec.Bundle bundle = PersistenceCodec.decodeBundle(raw);
            if (bundle == null) {
                runOnUiThread(() -> Toast.makeText(this, "Bu dosya geçerli bir LocalRP yedeği değil.", Toast.LENGTH_LONG).show());
                return;
            }
            final boolean restored = store.restoreBundle(bundle.characters, bundle.groups, bundle.chats);
            runOnUiThread(() -> {
                if (!restored) {
                    Toast.makeText(this, "Geri yükleme yapılamadı: mevcut veriler okunamıyor.", Toast.LENGTH_LONG).show();
                    if (!store.integrityIssues().isEmpty()) reportIntegrityIssues();
                    return;
                }
                Toast.makeText(this,
                        "Geri yüklendi · " + bundle.characters.size() + " karakter · " + bundle.groups.size() + " sohbet",
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
        if ("models".equals(currentTab)) showModels();
        LocalAiRuntime.INSTANCE.loadModel(this, selectedModel, new LocalAiRuntime.ModelListener() {
            @Override public void onStatus(String message) {
                if (!uiAlive()) return;
                modelStatus = message;
                if ("models".equals(currentTab) && modelStatusView != null) modelStatusView.setText(message);
                refreshVisibleChat(false);
            }
            @Override public void onLoaded(String fileName, long bytes) {
                if (!uiAlive()) return;
                modelLoading = false;
                selectedModelName = fileName;
                contextChars.clear();
                chatMetrics.clear();
                modelStatus = "Hazır · " + String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0) + " · çevrimdışı";
                if ("models".equals(currentTab)) showModels();
                Toast.makeText(MainActivity.this, "Model hazır.", Toast.LENGTH_SHORT).show();
                refreshVisibleChat(true);
            }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                modelLoading = false;
                modelStatus = "Yüklenemedi: " + message;
                if ("models".equals(currentTab)) showModels();
                refreshVisibleChat(false);
                new AlertDialog.Builder(MainActivity.this).setTitle("Model yüklenemedi")
                        .setMessage(message).setPositiveButton("Tamam", null).show();
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
            Toast.makeText(this, "Cihazda kayıtlı model kopyası yok.", Toast.LENGTH_SHORT).show();
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
        new AlertDialog.Builder(this).setTitle("Model kopyaları")
                .setItems(labels, (dialog, which) -> {
                    File file = files.get(which);
                    if (file.getAbsolutePath().equals(loadedPath)) {
                        Toast.makeText(this, "Bu model şu anda yüklü.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(this).setTitle(file.getName())
                            .setItems(new String[]{"Modeli yükle", "Kopyayı sil"}, (d, choice) -> {
                                if (choice == 0) loadStoredModelFile(file);
                                else confirm(file.getName() + " silinsin mi?", "Sil", () -> {
                                    if (LocalAiRuntime.INSTANCE.deleteStoredModel(this, file)) {
                                        Toast.makeText(this, "Model kopyası silindi.", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(this, "Model dosyası silinemedi.", Toast.LENGTH_SHORT).show();
                                    }
                                    showModels();
                                });
                            }).show();
                })
                .setNegativeButton("Kapat", null).show();
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
        if ("models".equals(currentTab)) showModels();
        LocalAiRuntime.INSTANCE.unload(new LocalAiRuntime.ModelListener() {
            @Override public void onStatus(String message) {
                if (!uiAlive()) return;
                modelStatus = message;
                if ("models".equals(currentTab) && modelStatusView != null) modelStatusView.setText(message);
                // Success is decided by runtime state, not by parsing the status text.
                if (!LocalAiRuntime.INSTANCE.isModelReady() && LocalAiRuntime.INSTANCE.loadedModelPath() == null) {
                    modelLoading = false;
                    contextChars.clear();
                    chatMetrics.clear();
                    if ("models".equals(currentTab)) showModels();
                }
                refreshVisibleChat(false);
            }
            @Override public void onLoaded(String name, long bytes) { }
            @Override public void onError(String message) {
                if (!uiAlive()) return;
                modelLoading = false;
                modelStatus = "Hata: " + message;
                if ("models".equals(currentTab)) showModels();
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
        String instructions = clip(prefs().getString("ai_instructions", DEFAULT_AI_INSTRUCTIONS), 600);
        String themeId = prefs().getString("ai_theme", DEFAULT_AI_THEME);
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
                historyCharBudget());
    }

    private String aiThemeName(String id) {
        for (String[] theme : AI_THEMES) if (theme[0].equals(id)) return theme[1];
        return AI_THEMES[0][1];
    }

    private String aiThemeInstruction(String id) {
        for (String[] theme : AI_THEMES) if (theme[0].equals(id)) return theme[2];
        return "";
    }

    private String aiThemeDescription(String id) {
        String instruction = aiThemeInstruction(id);
        return instruction.isEmpty()
                ? "Tema kısıtlaması yok; karakterin doğal dünyası ve sahnenin tonu korunur."
                : instruction;
    }

    private void showThemePicker() {
        if (generatingChatId != null) {
            Toast.makeText(this, "Yanıt üretilirken tema değiştirilemez.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (modelLoading || LocalAiRuntime.INSTANCE.isModelLoading()) {
            Toast.makeText(this, "Model yüklenirken tema değiştirilemez.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (LocalAiRuntime.INSTANCE.isBenchmarking()) {
            Toast.makeText(this, "Motor testi sürerken tema değiştirilemez.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[AI_THEMES.length];
        String current = prefs().getString("ai_theme", DEFAULT_AI_THEME);
        for (int i = 0; i < AI_THEMES.length; i++) {
            labels[i] = AI_THEMES[i][1] + (AI_THEMES[i][0].equals(current) ? "  ✓" : "");
        }
        new AlertDialog.Builder(this).setTitle("AI sahne teması")
                .setItems(labels, (dialog, which) -> {
                    String selectedId = AI_THEMES[which][0];
                    if (selectedId.equals(prefs().getString("ai_theme", DEFAULT_AI_THEME))) return;
                    prefs().edit().putString("ai_theme", selectedId).apply();
                    contextChars.clear();
                    chatMetrics.clear();
                    LocalAiRuntime.INSTANCE.resetConversation(null);
                    Toast.makeText(this, AI_THEMES[which][1] + " teması seçildi.", Toast.LENGTH_SHORT).show();
                    showModels();
                })
                .setNegativeButton("Vazgeç", null).show();
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
        pageRoot = new LinearLayout(this);
        pageRoot.setOrientation(LinearLayout.VERTICAL);
        pageRoot.setBackgroundColor(Design.pageBg());
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            // Android 15+ enforces edge-to-edge; pad content under the system bars (page tint still shows through).
            pageRoot.setOnApplyWindowInsetsListener((view, insets) -> {
                int top = insets.getSystemWindowInsetTop();
                int bottom = insets.getSystemWindowInsetBottom();
                if (top > 0 || bottom > 0) view.setPadding(0, top, 0, bottom);
                return insets;
            });
        }
        if (chat) {
            LinearLayout header = new LinearLayout(this);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(12), dp(8), dp(16), dp(8));
            if (backAction != null) {
                TextView back = label("‹", 22, Design.text());
                back.setBackground(rounded(Design.cardBg(), Design.cardStroke(), 22));
                back.setGravity(Gravity.CENTER);
                back.setOnClickListener(v -> backAction.run());
                header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
            }
            // cAI-style leading avatar on the chat header; ids stay legible next to it.
            TextView selfAvatar = Avatar.forName(this, title, 38);
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
            header.setPadding(dp(18), dp(14), dp(18), dp(6));
            if (backAction != null) {
                TextView back = label("‹", 22, Design.text());
                back.setBackground(rounded(Design.cardBg(), Design.cardStroke(), 22));
                back.setGravity(Gravity.CENTER);
                back.setOnClickListener(v -> backAction.run());
                header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
                LinearLayout headerTexts = new LinearLayout(this);
                headerTexts.setOrientation(LinearLayout.VERTICAL);
                headerTexts.setPadding(dp(14), 0, dp(4), 0);
                TextView heading = label(title, 25, Design.text());
                heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
                heading.setSingleLine(true);
                heading.setEllipsize(TextUtils.TruncateAt.END);
                headerTexts.addView(heading);
                TextView sub = label(subtitle, 13, Design.muted());
                sub.setPadding(0, dp(3), 0, 0);
                headerTexts.addView(sub);
                pageSubtitleView = sub;
                header.addView(headerTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            } else {
                TextView heading = label(title, 27, Design.text());
                heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
                header.addView(heading);
                TextView sub = label(subtitle, 13, Design.muted());
                sub.setPadding(0, dp(6), 0, 0);
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
            content.setPadding(dp(20), dp(4), dp(20), dp(12));
            chatScroll.addView(content);
            body.addView(chatScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

            View divider = new View(this);
            divider.setBackgroundColor(Design.divider());
            body.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

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
            content.setPadding(dp(20), dp(6), dp(20), dp(24));
            scroll.addView(content);
            pageRoot.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }

        if (!chat) {
            // Floating bottom navigation (modern chat-app pattern): a detached,
            // rounded bar instead of a full-width strip.
            FrameLayout navWrap = new FrameLayout(this);
            navWrap.setPadding(dp(14), dp(4), dp(14), dp(10));
            LinearLayout nav = new LinearLayout(this);
            nav.setOrientation(LinearLayout.HORIZONTAL);
            nav.setPadding(dp(6), dp(7), dp(6), dp(7));
            nav.setBackground(rounded(Design.cardBg(), Design.cardStroke(), 28));
            addNavItem(nav, "⌂", "Ana sayfa", "home".equals(tab), this::showHome);
            addNavItem(nav, "◉", "Karakter", "characters".equals(tab), this::showCharacters);
            addNavItem(nav, "▤", "Sohbetler", "chats".equals(tab), this::showChats);
            addNavItem(nav, "⚙", "AI", "models".equals(tab), () -> openModels(null));
            addNavItem(nav, "◐", "Görünüm", "appearance".equals(tab), this::showAppearance);
            navWrap.addView(nav, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            pageRoot.addView(navWrap, fullWidth());
        }
        setContentView(pageRoot);
        Design.applyBars(this);
    }

    private void addNavItem(LinearLayout nav, String icon, String title, boolean selected, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER);
        item.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(6), vertical = dp(6);
        item.setPadding(horizontal, vertical, horizontal, vertical);
        TextView glyph = label(icon, 18, selected ? Design.accent() : Design.muted());
        glyph.setGravity(Gravity.CENTER);
        TextView name = label(title, 10, selected ? Design.accent() : Design.muted());
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(2), 0, 0);
        item.addView(glyph);
        item.addView(name);
        if (selected) {
            // Small dot indicator instead of a highlighted box: quieter, floating-bar idiom.
            View dot = new View(this);
            dot.setBackground(rounded(Design.accent(), Design.accent(), 3));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(5), dp(5));
            dotParams.setMargins(0, dp(3), 0, 0);
            item.addView(dot, dotParams);
        }
        item.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        nav.addView(item, params);
    }

    private void addHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(17), dp(18), dp(17));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Design.heroA(), Design.heroB()});
        bg.setCornerRadius(dp(20));
        hero.setBackground(bg);
        TextView eyebrow = label("KENDİ DÜNYANI KUR", 11, Design.heroSub());
        eyebrow.setLetterSpacing(.08f);
        hero.addView(eyebrow);
        TextView title = label("Hikâyen\nburada başlıyor.", 25, Design.heroText());
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(9), 0, dp(8));
        hero.addView(title);
        TextView detail = label(store.characters().size() + " karakter  ·  " + store.groups().size() + " sohbet", 14, Design.heroSub());
        hero.addView(detail);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(20));
        content.addView(hero, p);
    }

    private void addSection(String title, String actionText, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(title, 18, Design.text());
        heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        row.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (actionText != null && action != null) {
            TextView link = label(actionText + "  ›", 13, Design.accent());
            link.setOnClickListener(v -> action.run());
            row.addView(link);
        }
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, dp(2), 0, dp(10));
        content.addView(row, p);
    }

    private LinearLayout addEmpty(String title, String detail, String actionText, View.OnClickListener action) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(22), dp(20), dp(20));
        box.setBackground(rounded(Design.cardBg(), Design.cardStroke(), 18));
        TextView symbol = label("✦", 27, Design.accent());
        box.addView(symbol);
        TextView heading = label(title, 17, Design.text());
        heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(5), 0, dp(4));
        box.addView(heading);
        TextView body = label(detail, 13, Design.muted());
        body.setGravity(Gravity.CENTER);
        box.addView(body);
        if (actionText != null && action != null) {
            Button button = button(actionText);
            stylePrimary(button);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46));
            p.setMargins(0, dp(13), 0, 0);
            box.addView(button, p);
            button.setOnClickListener(action);
        }
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(16));
        content.addView(box, p);
        return box;
    }

    private void addGroupCard(Group group) {
        Message last = store.lastMessage(group.id);
        String preview;
        String when = "";
        if (group.id.equals(generatingChatId)) {
            preview = (generatingSpeaker == null ? "Karakter" : generatingSpeaker) + " yanıt yazıyor…";
        } else if (last == null) {
            preview = "Sahneyi başlatmak için dokun";
        } else {
            preview = (last.user ? "Sen" : last.speaker) + ": " + oneLine(last.text, 90);
            when = clock.format(new Date(last.at));
        }
        CharacterProfile face = group.characterIds.isEmpty() ? null : store.character(group.characterIds.get(0));

        // cAI-style recent-chat card: leading avatar, bold title, muted preview + time.
        LinearLayout card = makeCard();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = Avatar.forName(this, face != null ? face.name : group.name, 44);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));
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
        card.setOnClickListener(v -> showChat(group));
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
        card.setPadding(dp(10), dp(13), dp(10), dp(13));
        card.setBackground(rounded(Design.cardBg(), Design.cardStroke(), 16));
        TextView avatar = Avatar.forName(this, character.name, 52);
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
        TextView avatar = Avatar.forName(this, character.name, 42);
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
        addCardToPage(card);
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
                ? "Model hazır · çevrimdışı"
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
        LinearLayout card = makeCard();
        TextView badge = label(tag.toUpperCase(), 10, Design.accent());
        badge.setLetterSpacing(.07f);
        card.addView(badge);
        TextView heading = label(title, 16, Design.text());
        heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(6), 0, dp(4));
        card.addView(heading);
        TextView body = label(detail, 13, Design.muted());
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
            tile.setBackground(rounded(Design.cardBg(), Design.cardStroke(), 18));
            tile.setOnClickListener(v -> actions[index].run());
            TextView icon = label(defs[i][0], 20, Design.accent());
            tile.addView(icon);
            TextView title = label(defs[i][1], 14, Design.text());
            title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
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
        view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setTextColor(Design.accent());
        view.setBackground(rounded(Design.cardBg(), Design.accentStroke(), 26));
        view.setOnClickListener(listener);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(12));
        content.addView(view, p);
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(rounded(Design.cardBg(), Design.cardStroke(), 20));
        return card;
    }

    private void addCardToPage(View card) {
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(9));
        content.addView(card, p);
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
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
        button.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        button.setLetterSpacing(.01f);
        button.setMinHeight(dp(48));
        button.setBackground(rounded(Design.accent(), Design.accent(), 26));
    }

    private EditText field(String hint, String value) { return field(hint, value, 1); }
    private EditText field(String hint, String value, int lines) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(15);
        input.setMinLines(lines);
        input.setGravity(Gravity.TOP);
        input.setPadding(dp(14), dp(11), dp(14), dp(11));
        input.setTextColor(Design.text());
        input.setHintTextColor(Design.muted());
        GradientDrawable background = new GradientDrawable();
        background.setColor(Design.cardBg());
        background.setStroke(dp(1), Design.cardStroke());
        background.setCornerRadius(dp(18));
        input.setBackground(background);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(12));
        content.addView(input, p);
        return input;
    }

    private void addBody(String text) {
        TextView view = label(text, 13, Design.muted());
        view.setPadding(0, dp(4), 0, dp(10));
        content.addView(view);
    }

    private void addDangerButton(String text, View.OnClickListener listener) {
        Button view = button(text);
        view.setTextColor(Design.danger());
        view.setBackground(rounded(Design.dangerSoft(), Design.dangerStroke(), 26));
        view.setOnClickListener(listener);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, 0, 0, dp(10));
        content.addView(view, p);
    }

    private Button button(String text) {
        Button view = new Button(this);
        view.setText(text);
        view.setAllCaps(false);
        view.setTextSize(15);
        view.setMinHeight(dp(48));
        return view;
    }

    private TextView addMessage(LinearLayout parent, String speaker, String text, boolean user, long at) {
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
            String value = view.getTag() instanceof String ? (String) view.getTag() : view.getText().toString();
            if (value.trim().isEmpty()) return false;
            showMessageActions(speaker, value);
            return true;
        });
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                user ? 0.15f : 0.62f);
        bubbleParams.setMargins(user ? dp(10) : 0, 0, user ? 0 : dp(10), dp(10));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? Gravity.BOTTOM : Gravity.TOP);
        if (!user) {
            TextView companion = Avatar.forName(this, speaker, 30);
            companion.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams companionParams = new LinearLayout.LayoutParams(dp(30), dp(30));
            companionParams.setMargins(0, dp(3), 0, 0);
            row.addView(companion, companionParams);
        }
        row.addView(view, bubbleParams);
        TextView companionTime = label(clock.format(new Date(at)), 10, Design.muted());
        companionTime.setPadding(dp(3), 0, dp(3), dp(3));
        row.addView(companionTime, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        parent.addView(row, fullWidth());
        return view;
    }

    private void showMessageActions(String speaker, String text) {
        new AlertDialog.Builder(this).setTitle(speaker + " · mesaj")
                .setItems(new String[]{"Metni kopyala"}, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("LocalRP mesajı", text));
                        Toast.makeText(this, "Mesaj kopyalandı.", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("Vazgeç", null).show();
    }

    private void renderMessage(TextView view, String speaker, String text, long at) {
        view.setTag(text);
        String header = at > 0 ? speaker + "  ·  " + clock.format(new Date(at)) : speaker;
        String value = header + "\n" + text;
        SpannableString styled = new SpannableString(value);
        styled.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, speaker.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // Character names pop as accent-on-accentSoft like the cAI chat card, not
        // invisible-on-invisible; the span colors the whole "Name  ·  12:04" head line.
        int headLength = header.length();
        styled.setSpan(new ForegroundColorSpan(Design.accent()), 0, speaker.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (at > 0) {
            styled.setSpan(new ForegroundColorSpan(Design.muted()), speaker.length(), headLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        view.setText(styled);
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
        new AlertDialog.Builder(this).setMessage(message)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton(positive, (d, w) -> action.run()).show();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

}
