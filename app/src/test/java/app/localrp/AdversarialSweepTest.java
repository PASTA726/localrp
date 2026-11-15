package app.localrp;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Scratch adversarial sweep (v0.31.02 audit): extreme inputs against pure classes. */
public class AdversarialSweepTest {

    private CharacterProfile speaker(String name) {
        CharacterProfile p = new CharacterProfile();
        p.id = "spk";
        p.name = name;
        p.description = "d";
        p.personality = "p";
        p.instructions = "i";
        p.examples = "e";
        return p;
    }

    @Test
    public void prompt_nullAndExtremeInputs_doNotThrow() {
        // v0.31.02 audit: null sceneName + null castIds must not throw; the builder
        // normalizes the cast list (Group.characterIds is always initialized in the
        // app, but the pure builder stays total for any future caller).
        String a = RoleplayPrompt.build(
                null, speaker("Elif"), null, id -> null,
                new ArrayList<>(), false, false, null, null, null, 0, 0,
                null, false, null, null, null, null, false);
        assertTrue(a.contains("Sahne: "));
        assertTrue(a.contains("- Yok (bire bir sahne)"));
    }

    @Test
    public void prompt_personaShortArrayAndNullMemoryEntries_doNotThrow() {
        String[] persona = new String[1]; // length-2 expected path; short array
        persona[0] = "Deniz";
        List<String> memories = new ArrayList<>();
        memories.add(null);
        memories.add("");
        String a = RoleplayPrompt.build(
                "Sahne", speaker("Elif"), java.util.List.of("spk"), id -> speaker("Elif"),
                new ArrayList<>(), false, false, "", "", "", 8, 2600,
                persona, false, null, null, null, memories, true, null, "deep");
        assertTrue(a.contains("derinlemesine"));
    }

    @Test
    public void prompt_providerPath_depth0_freshBuild_noHistoryHeader() {
        // Provider mode calls buildRoleplayPrompt(..., continuingFlag=false, historyDepth=0):
        // history travels as real chat turns, so the fresh build must carry no
        // transcript header even though it is a fresh (non-continuing) prompt.
        List<Message> history = new ArrayList<>();
        history.add(new Message("Sen", "merhaba", true, 1L));
        history.add(new Message("Elif", "selam", false, 2L));
        String a = RoleplayPrompt.build(
                "Sahne", speaker("Elif"), java.util.List.of("spk"), id -> speaker("Elif"),
                history, false, true, "", "", "", 0, 2600,
                null, false, null, null, null, null, false, null, "short");
        assertFalse(a.contains("Yakın sahne geçmişi"));
        assertFalse("history must never be inlined at depth 0", a.contains("Kullanıcı: merhaba"));
    }

    @Test
    public void cleanup_nullSpeakerAndWeirdLabels_stillTrim() {
        assertEquals("", ResponseCleanup.clean(null, null));
        assertEquals("x", ResponseCleanup.clean("x", null));
        assertEquals("x", ResponseCleanup.clean("x", "   "));
        String out = ResponseCleanup.clean("！", "Momy -Ashe-");
        assertEquals("！", out);
    }

    @Test
    public void lorebook_pathologicalKeys_doNotThrow() {
        List<Lorebook.Entry> entries = new ArrayList<>();
        // regex-special keys, empty key, extremely long key
        Lorebook.Entry special = new Lorebook.Entry("((", "x");
        Lorebook.Entry empty = new Lorebook.Entry("", "y");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) sb.append('a');
        Lorebook.Entry longKey = new Lorebook.Entry(sb.toString(), "z");
        entries.add(special);
        entries.add(empty);
        entries.add(longKey);
        String out = Lorebook.assemble(entries, Lorebook.BLOCK_CHAR_BUDGET);
        assertNotNull(out);
        List<Lorebook.Entry> triggered = Lorebook.triggered(entries, "bir metin (( içinde");
        assertNotNull(triggered);
    }
}
