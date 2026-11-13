package app.localrp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Regression tests for the extracted roleplay prompt assembly (Task 07). The prompt
 * IS user-visible model behavior — these lock the v0.7.3 group-identity fix so later
 * refactors cannot silently alter what the engine is told. Pure strings, no Android.
 */
public class RoleplayPromptTest {

    private static final java.util.function.Function<String, CharacterProfile> EMPTY_LOOKUP = id -> null;

    private CharacterProfile speaker(String name) {
        CharacterProfile c = new CharacterProfile();
        c.id = "spk";
        c.name = name;
        c.description = "Denizci kaptan";
        c.personality = "Atılgan";
        c.instructions = "Doğal konuş";
        c.examples = "Elif: Rüzgar iyi.";
        return c;
    }

    private List<Message> history() {
        List<Message> history = new ArrayList<>();
        history.add(new Message("Sen", "merhaba kaptan", true, 1L));
        history.add(new Message("Elif", "selam! Anlat.", false, 2L));
        return history;
    }

    @Test
    public void freshScene_includesPersonaCastAndRules() {
        Map<String, CharacterProfile> registry = new HashMap<>();
        registry.put("spk", speaker("Elif"));
        CharacterProfile other = speaker("Mira");
        other.id = "mira";
        registry.put("mira", other);

        String prompt = RoleplayPrompt.build(
                "Korsan gemisi", speaker("Elif"), java.util.List.of("spk", "mira"), registry::get,
                new ArrayList<>(), false, false, "Talimatlar.", "Karanlık fantezi", "Karanlık atmosfer.",
                8, 2600);

        assertTrue(prompt.contains("Genel AI talimatı: Talimatlar."));
        assertTrue(prompt.contains("Sahne teması (Karanlık fantezi): Karanlık atmosfer."));
        assertTrue(prompt.contains("Sahne: Korsan gemisi"));
        assertTrue(prompt.contains("Karakter tanımı: Denizci kaptan"));
        assertTrue(prompt.contains("Örnek diyalog:"));
        assertTrue(prompt.contains("- Mira: Denizci kaptan"));
        // Single-speaker rule from the v0.7.3 identity fix
        assertTrue(prompt.contains("Kurallar: Yalnızca Elif olarak tek bir rol içi yanıt yaz."));
        assertTrue(prompt.contains("kullanıcı veya diğer karakterler adına konuşma."));
        assertFalse(prompt.contains("Bağlam devam ediyor"));
    }

    @Test
    public void continuingScene_reinjectsIdentityInsteadOfFullPersona() {
        Map<String, CharacterProfile> registry = new HashMap<>();
        registry.put("spk", speaker("Elif"));
        String prompt = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                history(), true, false, "", "", "", 8, 2600);

        assertTrue(prompt.contains("Bağlam devam ediyor"));
        assertTrue(prompt.contains("Rol kimliği: Elif — Denizci kaptan"));
        assertFalse(prompt.contains("Karakter tanımı:")); // full persona block only on fresh scenes
        // The stored last message is the character's reply, so no "last user message"
        // line is injected this turn (it only appears right after a user message).
        assertFalse(prompt.contains("Kullanıcının son mesajı:"));
    }

    @Test
    public void userPersona_isInjectedOnFreshScenes() {
        Map<String, CharacterProfile> registry = new HashMap<>();
        registry.put("spk", speaker("Elif"));
        String prompt = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                new ArrayList<>(), false, false, "", "", "", 8, 2600,
                new String[]{"Evren", "Huzurevinde çalışan sessiz bir bahçıvan; dedektif hikâyelerinde yardımcı ister."});

        assertTrue(prompt.contains("Kullanıcı kimliği: Evren — Huzurevinde çalışan sessiz bir bahçıvan"));
        assertTrue(prompt.contains("Kullanıcı kimliği: Evren"));
    }

    @Test
    public void userPersona_isCompactOnContinuingScenes_omittedWhenNull() {
        Map<String, CharacterProfile> registry = new HashMap<>();
        registry.put("spk", speaker("Elif"));
        String continuing = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                history(), true, false, "", "", "", 8, 2600,
                new String[]{"Evren", ""});
        assertTrue(continuing.contains("Kullanıcı kimliği: Evren"));
        // The compact re-injection must not carry the fresh-scene full blocks either.
        assertFalse(continuing.contains("Karakter tanımı:"));

        String withoutPersona = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                new ArrayList<>(), false, false, "", "", "", 8, 2600, null);
        assertFalse(withoutPersona.contains("Kullanıcı kimliği:"));
    }

    @Test
    public void lorebook_injectsOnlyTriggeredEntriesOnFreshScenes() {
        Map<String, CharacterProfile> registry = new HashMap<>();
        registry.put("spk", speaker("Elif"));
        List<Lorebook.Entry> lore = new ArrayList<>();
        lore.add(new Lorebook.Entry("kız kulesi", "Kulenin feneri geceleri yeşil yanar."));
        lore.add(new Lorebook.Entry("ejder", "Buz ejderi yalnızca kuzey dağlarında uyanır."));

        // Fresh turn mentioning the tower: only the tower fact ships.
        List<Message> mentioning = new ArrayList<>();
        mentioning.add(new Message("Elif", "Bugün kız kulesi'ne yanaştık.", false, 3L));
        String prompt = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                mentioning, false, false, "", "", "", 8, 2600, null, false, lore);
        assertTrue(prompt.contains("Dünya kitabı"));
        assertTrue(prompt.contains("Kulenin feneri geceleri yeşil yanar."));
        assertFalse(prompt.contains("Buz ejderi yalnızca"));

        // No keyword match anywhere → no world book line at all.
        String silent = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                history(), false, false, "", "", "", 8, 2600, null, false, lore);
        assertFalse(silent.contains("Dünya kitabı"));
    }

    @Test
    public void lorebook_isSkippedOnContinuingTurns() {
        // The engine's saved context already carries the world book from the fresh build;
        // continuing turns must not duplicate it.
        Map<String, CharacterProfile> registry = new HashMap<>();
        registry.put("spk", speaker("Elif"));
        List<Message> mentioning = new ArrayList<>();
        mentioning.add(new Message("Elif", "kız kulesi", false, 3L));
        List<Lorebook.Entry> lore = new ArrayList<>();
        lore.add(new Lorebook.Entry("kız kulesi", "Fener yeşil yanar."));
        String prompt = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                mentioning, true, false, "", "", "", 8, 2600, null, true, lore);
        assertFalse(prompt.contains("World book"));
    }

    @Test
    public void memory_injectsRecapAndRelevantEvents_onFreshScenesOnly() {
        Map<String, CharacterProfile> registry = new HashMap<>();
        registry.put("spk", speaker("Elif"));
        List<String> memories = new ArrayList<>();
        memories.add("Elif: fenerin fenercisiyle konuştu.");
        String prompt = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                history(), false, false, "", "", "", 2, 2600, null, false, null,
                null, "Elif: Gece limana yanaştı.", memories);
        assertTrue(prompt.contains("Hikâyede şimdiye kadar olanlar"));
        assertTrue(prompt.contains("Gece limana yanaştı."));
        assertTrue(prompt.contains("Hatırlanan önemli anlar:"));
        assertTrue(prompt.contains("- Elif: fenerin fenercisiyle konuştu."));

        // English mode swaps the headers.
        String english = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                history(), false, false, "", "", "", 2, 2600, null, true, null,
                null, "Elif: They docked at night.", memories);
        assertTrue(english.contains("Story so far"));
        assertTrue(english.contains("Remembered important events:"));

        // Continuing turns must not duplicate: the engine's saved context carries them.
        String continuing = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                history(), true, false, "", "", "", 2, 2600, null, false, null,
                null, "Elif: Gece limana yanaştı.", memories);
        assertFalse(continuing.contains("Hatırlanan önemli anlar"));
        assertFalse(continuing.contains("Hikâyede şimdiye kadar olanlar"));
    }

    @Test
    public void steerNote_shipsOnlyWhenProvided_inBothLanguages() {
        Map<String, CharacterProfile> registry = new HashMap<>();
        registry.put("spk", speaker("Elif"));

        // Turkish build (english=false): one-shot steering note is present.
        String steered = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                history(), true, false, "", "", "", 8, 2600, null, false, null,
                "Daha kısa yaz; aksiyona odaklan");
        assertTrue(steered.contains("Yönlendirme notu (yalnızca bu yanıt): Daha kısa yaz; aksiyona odaklan"));

        // English build: the note travels with an English label.
        String steeredEn = RoleplayPrompt.build(
                "Ship", speaker("Elif"), java.util.List.of("spk"), registry::get,
                history(), true, false, "", "", "", 8, 2600, null, true, null,
                "Keep it short");
        assertTrue(steeredEn.contains("Steering note (this reply only): Keep it short"));
        assertFalse(steeredEn.contains("Yönlendirme notu"));

        // No note (null) and a blank note both leave no steering line at all.
        String plain = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                history(), true, false, "", "", "", 8, 2600, null, false, null, null);
        assertFalse(plain.contains("Yönlendirme notu"));
        String blank = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), registry::get,
                history(), true, false, "", "", "", 8, 2600, null, false, null, "   ");
        assertFalse(blank.contains("Yönlendirme notu"));
    }

    @Test
    public void englishMode_assemblesEnglishPromptWithUserReplyRule() {
        Map<String, CharacterProfile> registry = new HashMap<>();
        registry.put("spk", speaker("Elif"));
        String prompt = RoleplayPrompt.build(
                "Pirate ship", speaker("Elif"), java.util.List.of("spk"), registry::get,
                history(), false, false, "Be brief.", "Dark fantasy", "Gothic mood.", 8, 2600,
                new String[]{"Evren", "A quiet gardener."}, true);

        assertTrue(prompt.contains("General AI instruction: Be brief."));
        assertTrue(prompt.contains("Scene tone (Dark fantasy): Gothic mood."));
        assertTrue(prompt.contains("Scene: Pirate ship"));
        assertTrue(prompt.contains("This turn, reply speaking ONLY as Elif."));
        assertTrue(prompt.contains("Character description: Denizci kaptan"));
        assertTrue(prompt.contains("User identity: Evren — A quiet gardener."));
        assertTrue(prompt.contains("\nRecent scene history:\n"));
        assertTrue(prompt.contains("User: merhaba kaptan"));
        // The last stored message is the character's own reply, so no last-user line.
        assertFalse(prompt.contains("The user's last message:"));
        assertTrue(prompt.contains("Rules: Write one in-character reply ONLY as Elif."));
        assertTrue(prompt.contains("Reply in English and stay within the scene."));
        // English mode must not leak the Turkish scaffolding.
        assertFalse(prompt.contains("Kurallar"));
        assertFalse(prompt.contains("Kullanıcı"));
    }

    @Test
    public void englishMode_continuingTurnReinjectsIdentityInEnglish() {
        Map<String, CharacterProfile> registry = new HashMap<>();
        registry.put("spk", speaker("Elif"));
        String prompt = RoleplayPrompt.build(
                "Pirate ship", speaker("Elif"), java.util.List.of("spk"), registry::get,
                history(), true, false, "", "", "", 8, 2600, null, true);

        assertTrue(prompt.contains("Context continues; keep the scene"));
        assertTrue(prompt.contains("Role identity: Elif — Denizci kaptan"));
        assertFalse(prompt.contains("Character description:"));
        assertFalse(prompt.contains("Rol kimliği"));
        assertTrue(prompt.contains("Reply in English and stay within the scene."));
    }

    @Test
    public void historyLabelsUserAsKullanıcıNotSen() {
        // History lines are only assembled for FRESH scenes (continuing=false); on a
        // continuing scene the model keeps its own context and sees only the last user message.
        String prompt = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), EMPTY_LOOKUP,
                history(), false, false, "", "", "", 8, 2600);
        assertTrue(prompt.contains("Kullanıcı: merhaba kaptan"));
        assertTrue(prompt.contains("Elif: selam! Anlat."));
        assertFalse(prompt.contains("Sen: "));
    }

    @Test
    public void historyBudgetTrimsOldestLinesInsideBudget() {
        List<Message> longHistory = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String text = i % 2 == 0 ? "Kullanıcı mesajı " + i : "Karakter mesajı " + i;
            longHistory.add(new Message(i % 2 == 0 ? "Sen" : "Elif", text, i % 2 == 0, i));
        }
        String prompt = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), EMPTY_LOOKUP,
                longHistory, false, false, "", "", "", 8, 200);

        // depth 8 → at most 8 history lines; budget 200 keeps far fewer
        assertTrue(prompt.contains("Yakın sahne geçmişi"));
        assertTrue(prompt.contains("Kullanıcı mesajı 59") || prompt.contains(" mesajı 5"));
        assertFalse(prompt.contains("Kullanıcı mesajı 0\n")); // oldest trimmed away
    }

    @Test
    public void continuationTurn_afterCharacterMessageRequestsNaturalReaction() {
        List<Message> history = new ArrayList<>();
        history.add(new Message("Elif", "Rüzgar iyi.", false, 2L));
        String prompt = RoleplayPrompt.build(
                "Gemi", speaker("Mira"), java.util.List.of("spk"), EMPTY_LOOKUP,
                history, true, true, "", "", "", 8, 2600);
        assertTrue(prompt.contains("Sahne devam ediyor; sıradaki karakter olarak"));
    }

    @Test
    public void zeroHistoryDepth_shipsNoHistoryBlock() {
        // v0.27.00 edge: provider mode passes depth 0 (history travels as real
        // turns) — the fresh-build history block must disappear entirely, not
        // degrade into a heading with no lines.
        List<Message> history = new ArrayList<>();
        history.add(new Message("Sen", "merhaba", true, 1L));
        history.add(new Message("Elif", "selam", false, 2L));
        String prompt = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), EMPTY_LOOKUP,
                history, false, false, "", "", "", 0, 2600);
        assertFalse(prompt.contains("Yakın sahne geçmişi"));
    }

    @Test
    public void tinyBudget_dropsWholeLinesNeverMidSentence() {
        // v0.27.00 edge: with a budget too small for even one line, the history
        // block must disappear entirely — no dangling header, no truncated line.
        // (The final user message legitimately echoes as the turn's input.)
        List<Message> history = new ArrayList<>();
        history.add(new Message("Sen", "birinci satır bütçeden uzun", true, 1L));
        history.add(new Message("Elif", "ikinci satır da bütçeden uzun", false, 2L));
        history.add(new Message("Sen", "kısa", true, 3L));
        String prompt = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk"), EMPTY_LOOKUP,
                history, false, false, "", "", "", 8, 10);
        assertFalse(prompt.contains("Yakın sahne geçmişi"));
        assertFalse(prompt.contains("birinci satır bütçeden uzun"));
        assertFalse(prompt.contains("ikinci satır da bütçeden uzun"));
        assertTrue(prompt.contains("kısa"));
    }

    @Test
    public void deletedCastMemberIsSkippedWithoutCrash() {
        // cast id points at a deleted profile → lookup yields null → prompt still builds
        String prompt = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk", "ghost"), EMPTY_LOOKUP,
                new ArrayList<>(), false, false, "", "", "", 8, 2600);
        assertTrue(prompt.contains("- Yok (bire bir sahne)"));
        assertTrue(prompt.contains("Kurallar: Yalnızca Elif olarak"));
    }
}
