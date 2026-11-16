package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;

/** v0.35.00 (audit 1): card fields clip in the prompt — the editor must say so. */
public class CharacterFieldLimitsTest {

    @Test
    public void limits_matchThePromptClips() {
        // These must stay byte-identical to what RoleplayPrompt clips at; the
        // editor counters would otherwise advertise a different truncation point.
        assertEquals(80, CharacterFieldLimits.NAME);
        assertEquals(700, CharacterFieldLimits.DESCRIPTION);
        assertEquals(900, CharacterFieldLimits.PERSONALITY);
        assertEquals(900, CharacterFieldLimits.INSTRUCTIONS);
        assertEquals(1200, CharacterFieldLimits.EXAMPLES);
        assertEquals(80, CharacterFieldLimits.PERSONA_NAME);
        assertEquals(400, CharacterFieldLimits.PERSONA_DESCRIPTION);
    }

    @Test
    public void over_detectsExcess_nullSafe() {
        assertFalse(CharacterFieldLimits.over(900, null));
        assertFalse(CharacterFieldLimits.over(900, ""));
        assertFalse(CharacterFieldLimits.over(900, "a".repeat(900)));
        assertTrue(CharacterFieldLimits.over(900, "a".repeat(901)));
        // Empty-limit safety: even 1 char over 0 reports over.
        assertTrue(CharacterFieldLimits.over(0, "x"));
        assertFalse(CharacterFieldLimits.over(0, ""));
    }

    @Test
    public void counter_isLanguageNeutralNumbers() {
        assertEquals("0/900", CharacterFieldLimits.counter(900, null));
        assertEquals("12/700", CharacterFieldLimits.counter(700, "abcdefghijkl"));
        assertEquals("901/900", CharacterFieldLimits.counter(900, "a".repeat(901)));
    }

    @Test
    public void promptActuallyClipsAtTheAdvertisedLimits() {
        // End-to-end honesty: an over-limit card enters the prompt clipped to
        // exactly the advertised limit (clip appends an ellipsis inside the cap).
        CharacterProfile card = new CharacterProfile();
        card.id = "spk";
        card.name = "Elif";
        card.description = "d".repeat(2000);
        card.personality = "p".repeat(2000);
        card.instructions = "i".repeat(2000);
        card.examples = "e".repeat(2000);
        String prompt = RoleplayPrompt.build(
                "Sahne", card, new ArrayList<>(), null, new ArrayList<>(),
                false, false, null, null, null, 8, 2600,
                null, false, null, null, null, null, false, null, null);
        assertTrue(prompt.contains("Karakter tanımı: " + "d".repeat(CharacterFieldLimits.DESCRIPTION - 1) + "…"));
        assertTrue(prompt.contains("Kişilik ve konuşma tarzı: " + "p".repeat(CharacterFieldLimits.PERSONALITY - 1) + "…"));
        assertTrue(prompt.contains("Karakter talimatları: " + "i".repeat(CharacterFieldLimits.INSTRUCTIONS - 1) + "…"));
        // Examples render as a block ("Örnek diyalog:\n…"), so assert the clipped body.
        assertTrue(prompt.contains("Örnek diyalog:\n" + "e".repeat(CharacterFieldLimits.EXAMPLES - 1) + "…"));
    }
}
