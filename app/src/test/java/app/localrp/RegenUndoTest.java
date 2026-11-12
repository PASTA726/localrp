package app.localrp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Restore-safety rules for the regenerate-undo comfort tool (v0.19.22). */
public class RegenUndoTest {
    private List<Message> transcript(String... texts) {
        List<Message> list = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            // Alternating speakers: user turns are marked with a leading "#u".
            boolean user = texts[i] != null && texts[i].startsWith("#u");
            list.add(new Message(user ? "Sen" : "Elif",
                    user ? texts[i].substring(2) : texts[i], user, i + 1L));
        }
        return list;
    }

    @Test
    public void untouchedTranscript_restores() {
        // Stash: index 2 (tail removed), anchor = text of message 1.
        List<Message> live = transcript("selam", "merhaba!", "yeni yanıt");
        assertTrue(RegenUndo.canRestore(2, "merhaba!", 2L, live));
    }

    @Test
    public void anchorMismatch_blocksRestore() {
        List<Message> live = transcript("selam", "düzenlenmiş metin", "yeni yanıt");
        assertFalse(RegenUndo.canRestore(2, "merhaba!", 2L, live));
    }

    @Test
    public void anchorTimestampMismatch_blocksRestore() {
        // Same anchor text but a different timestamp (the message was replaced);
        // text alone must not be enough to pass the guard.
        List<Message> live = transcript("selam", "merhaba!");
        live.set(1, new Message("Elif", "merhaba!", false, 99L));
        assertFalse(RegenUndo.canRestore(2, "merhaba!", 2L, live));
    }

    @Test
    public void shrunkenTranscript_blocksRestore() {
        // Messages above the stash were deleted; the index no longer points where it did.
        List<Message> live = transcript("selam");
        assertFalse(RegenUndo.canRestore(2, "merhaba!", 2L, live));
    }

    @Test
    public void newUserTurnInTail_blocksRestore() {
        // The user wrote again after the regenerate; an undo must not erase that turn.
        List<Message> live = transcript("selam", "merhaba!", "yeni yanıt", "#utekrar");
        assertFalse(RegenUndo.canRestore(2, "merhaba!", 2L, live));
    }

    @Test
    public void emptyTail_stillRestores() {
        // Regenerate produced nothing (silent model); the stash can still come back.
        List<Message> live = transcript("selam", "merhaba!");
        assertTrue(RegenUndo.canRestore(2, "merhaba!", 2L, live));
    }

    @Test
    public void stashAtTranscriptStart_restoresWhenEmpty() {
        // Regenerate on a first AI message: no anchor, cleared transcript is restorable.
        assertTrue(RegenUndo.canRestore(0, null, 0L, new ArrayList<>()));
        // A non-empty anchor at index 0 is inconsistent → blocked.
        assertFalse(RegenUndo.canRestore(0, "merhaba!", 1L, transcript("selam")));
    }

    @Test
    public void degenerateInputs_blockRestore() {
        assertFalse(RegenUndo.canRestore(-1, null, 0L, transcript("selam")));
        assertFalse(RegenUndo.canRestore(1, null, 0L, null));
        List<Message> live = transcript("selam");
        assertFalse(RegenUndo.canRestore(2, "merhaba!", 2L, live)); // index beyond size
    }
}
