package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.33.00 auto summary policy: the out-of-window slice becomes one compact
 * 🔁 memory entry so deep conversations keep a permanent record of old events.
 */
public class SummaryPolicyTest {

    private Message user(String text, long at) { return new Message("Sen", text, true, at); }
    private Message ai(String text, long at) { return new Message("Elif", text, false, at); }

    private List<Message> transcript(int total) {
        List<Message> out = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            out.add(i % 2 == 1 ? user("mesaj " + i, i) : ai("yanıt " + i, i));
        }
        return out;
    }

    @Test
    public void pendingChunk_waitsUntilChunkMin() {
        // Depth 8, 9 messages: only 1 message is out of window — below CHUNK_MIN.
        List<Message> history = transcript(9);
        assertTrue(SummaryPolicy.pendingChunk(history, 0, 8).isEmpty());
        // Depth 8, 12 messages: 4 out-of-window messages → exactly the chunk.
        List<Message> twelve = transcript(12);
        List<Message> chunk = SummaryPolicy.pendingChunk(twelve, 0, 8);
        assertEquals(4, chunk.size());
        assertEquals("mesaj 1", chunk.get(0).text);
        assertEquals("yanıt 4", chunk.get(3).text);
    }

    @Test
    public void pendingChunk_cursorAdvances_andClampsSafely() {
        List<Message> history = transcript(20);
        // 8 already summarized → next chunk is messages 9..12.
        List<Message> chunk = SummaryPolicy.pendingChunk(history, 8, 8);
        assertEquals(4, chunk.size());
        assertEquals("mesaj 9", chunk.get(0).text);
        // A cursor beyond the slice yields nothing (deletion-shift safety).
        assertTrue(SummaryPolicy.pendingChunk(history, 20, 8).isEmpty());
        assertTrue(SummaryPolicy.pendingChunk(history, 99, 8).isEmpty());
        assertTrue(SummaryPolicy.pendingChunk(new ArrayList<>(), 0, 8).isEmpty());
        // recentCount 0 (never produced by the app — depth is clamped ≥2) means the
        // whole transcript is out of window: the policy treats it as one big chunk.
        assertEquals(20, SummaryPolicy.pendingChunk(history, 0, 0).size());
    }

    @Test
    public void entry_compactSingleLine_withUserAndSpeakerLabels() {
        List<Message> chunk = new ArrayList<>();
        chunk.add(user("Gemiye döndük ve kasayı aradık.", 1));
        chunk.add(ai("Kasa kırmızı mühürlüydü; açtım, içinde bir harita vardı.", 2));
        String entry = SummaryPolicy.entry(chunk);
        assertTrue(entry.startsWith("🔁 Olay özeti: "));
        assertTrue(entry.contains("Kullanıcı: Gemiye döndük"));
        assertTrue(entry.contains("Elif: Kasa kırmızı mühürlüydü"));
        assertFalse(entry.contains("\n")); // one line, recall-block friendly
        assertEquals(entry, entry.trim());
    }

    @Test
    public void entry_budgetCapped_andImagePlaceholder() {
        List<Message> chunk = new ArrayList<>();
        for (int i = 0; i < 10; i++) chunk.add(ai("Çok uzun bir olay anlatımı numara " + i + " — bu satır bütçeyi doldurmaya çalışıyor.", i));
        String entry = SummaryPolicy.entry(chunk);
        assertTrue(entry.length() <= SummaryPolicy.ENTRY_CHAR_BUDGET);

        List<Message> withImage = new ArrayList<>();
        Message imageOnly = new Message("Elif", "", false, 5);
        imageOnly.image = "img/x.png";
        withImage.add(imageOnly);
        withImage.add(user("Bu fotoğraftaki ne?", 6));
        String entry2 = SummaryPolicy.entry(withImage);
        assertTrue(entry2.contains("[görsel]"));
        assertTrue(entry2.contains("Kullanıcı: Bu fotoğraftaki ne?"));
    }

    @Test
    public void entry_emptyChunkAndRideWithChatMemory() {
        assertEquals("", SummaryPolicy.entry(new ArrayList<>()));
        assertEquals("", SummaryPolicy.entry(null));
        // The entry rides the existing memory path: add() dedupes byte-identical
        // entries, so a repeated summarize of the same slice writes one entry.
        List<Message> chunk = SummaryPolicy.pendingChunk(transcript(12), 0, 8);
        String entry = SummaryPolicy.entry(chunk);
        List<String> memories = ChatMemory.add(new ArrayList<>(), entry);
        memories = ChatMemory.add(memories, entry);
        assertEquals(1, memories.size());
        assertTrue(memories.get(0).startsWith("🔁 Olay özeti: "));
    }
}
