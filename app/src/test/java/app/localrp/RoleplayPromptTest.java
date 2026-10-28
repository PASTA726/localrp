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
    public void deletedCastMemberIsSkippedWithoutCrash() {
        // cast id points at a deleted profile → lookup yields null → prompt still builds
        String prompt = RoleplayPrompt.build(
                "Gemi", speaker("Elif"), java.util.List.of("spk", "ghost"), EMPTY_LOOKUP,
                new ArrayList<>(), false, false, "", "", "", 8, 2600);
        assertTrue(prompt.contains("- Yok (bire bir sahne)"));
        assertTrue(prompt.contains("Kurallar: Yalnızca Elif olarak"));
    }
}
