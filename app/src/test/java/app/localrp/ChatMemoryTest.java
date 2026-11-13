package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Memory rules for v0.23.00: pinning, recall scoring, JSON round-trip, recap. */
public class ChatMemoryTest {
    @Test
    public void add_trimsDedupesAndCaps() {
        List<String> memory = new ArrayList<>();
        memory = ChatMemory.add(memory, "Elif:  feneri yaktı ");
        memory = ChatMemory.add(memory, "Elif: feneri yaktı"); // whitespace-normalized duplicate
        assertEquals(1, memory.size());
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 1000; i++) big.append('x');
        memory = ChatMemory.add(memory, big.toString());
        assertEquals(ChatMemory.MAX_ENTRY_CHARS, memory.get(1).length());
        assertTrue(memory.get(1).endsWith("…"));
        for (int i = 0; i < ChatMemory.MAX_ENTRIES + 5; i++) memory = ChatMemory.add(memory, "olay " + i);
        assertEquals(ChatMemory.MAX_ENTRIES, memory.size());
        // The first two pins overflowed away; the newest additions stay.
        assertFalse(memory.contains("Elif: feneri yaktı"));
        assertEquals("olay " + (ChatMemory.MAX_ENTRIES + 4), memory.get(ChatMemory.MAX_ENTRIES - 1));
    }

    @Test
    public void add_addIgnoresBlank() {
        List<String> memory = ChatMemory.add(null, "   ");
        assertTrue(memory.isEmpty());
        assertEquals(0, ChatMemory.add(null, null).size());
    }

    @Test
    public void encode_decode_roundTrip() {
        List<String> memory = ChatMemory.add(null, "Elif: feneri yaktı");
        memory = ChatMemory.add(memory, "Kapı arka bahçeye çıkar.");
        List<String> decoded = ChatMemory.decode(ChatMemory.encode(memory));
        assertEquals(memory, decoded);
        assertTrue(ChatMemory.decode(null).isEmpty());
        assertTrue(ChatMemory.decode("bozuk{").isEmpty());
    }

    @Test
    public void relevant_ranksKeywordOverlap() {
        List<String> memory = new ArrayList<>();
        memory = ChatMemory.add(memory, "Fener kız kulesi'nin tepesinde yeşil yanar.");
        memory = ChatMemory.add(memory, "Mira arka bahçe kapısının anahtarını sakladı.");
        List<String> hit = ChatMemory.relevant(memory, "Kuleye yaklaşıyoruz; fener yeşil görünüyor.");
        assertEquals(1, hit.size());
        assertTrue(hit.get(0).contains("Fener"));
        assertTrue(ChatMemory.relevant(memory, "selam").isEmpty());
        assertTrue(ChatMemory.relevant(null, "fener").isEmpty());
        // Higher overlap ranks first even when the stronger match was pinned later;
        // the weaker entry shares no words with this query, so only one returns.
        List<String> ranked = new ArrayList<>();
        ranked = ChatMemory.add(ranked, "Anahtar arka bahçede gizli.");
        ranked = ChatMemory.add(ranked, "Fener yeşil yanar ve fenerin fenercisi gece bekçidir.");
        List<String> order = ChatMemory.relevant(ranked, "fenerin fenercisiyle konuştuk, fener yeşildi");
        assertEquals(1, order.size());
        assertTrue(order.get(0).contains("fenercisi"));
    }

    @Test
    public void relevant_respectsEntryCapAndBudget() {
        List<String> memory = new ArrayList<>();
        for (int i = 0; i < 20; i++) memory = ChatMemory.add(memory, "fener olay " + i + " kayıt");
        List<String> hits = ChatMemory.relevant(memory, "fener bugün parlıyor");
        assertEquals(ChatMemory.RECALL_MAX, hits.size());
    }

    @Test
    public void relevant_turkishTokensCaseFoldAndStopwords() {
        // v0.27.00: recall must work on real Turkish text — case folding through
        // toLowerCase(Locale.ROOT) and the shipped stopword list both matter.
        List<String> memory = new ArrayList<>();
        memory = ChatMemory.add(memory, "Kapı ARKA BAHÇE'de; anahtar saksının altında."); // stopwords: ile/için yok ama arka+bahçe+anahtar kelimeler var
        List<String> hit = ChatMemory.relevant(memory, "arka bahçe anahtarı nerede?");
        assertEquals(1, hit.size());
        assertTrue(hit.get(0).contains("anahtar"));
        // Pure-stopword query matches nothing (words shorter than 3 chars drop too).
        assertTrue(ChatMemory.relevant(memory, "ve ile de o da bu için").isEmpty());
    }

    @Test
    public void relevant_budgetBoundary_fitsExactlyThenStops() {
        // v0.27.00: the exact boundary — an entry whose length equals the remaining
        // budget must be taken (<=), and after it nothing else ships. Oversized
        // entries only enter through decode(), so the entries are built raw.
        String filler = "a"; // reused to hit exact lengths
        StringBuilder exact = new StringBuilder("fener "); // 6 chars
        while (exact.length() < ChatMemory.RECALL_CHAR_BUDGET) exact.append(filler);
        assertEquals(ChatMemory.RECALL_CHAR_BUDGET, exact.length()); // exactly 700

        StringBuilder raw = new StringBuilder("[");
        raw.append('"').append(exact).append('"');
        raw.append(",\"fener küçük yedek\"]");
        List<String> memory = ChatMemory.decode(raw.toString());

        List<String> hits = ChatMemory.relevant(memory, "fener");
        assertEquals(1, hits.size());
        assertEquals(exact.toString(), hits.get(0)); // the exact-fit entry is taken
        assertFalse(hits.contains("fener küçük yedek")); // budget exactly exhausted
    }

    @Test
    public void relevant_oversizedEntry_skippedNotBreaking() {
        // An entry longer than the whole budget is skipped, but later smaller
        // candidates still ship (the loop must not break on one big entry).
        // Oversized entries can only enter through decode() — add() caps at
        // MAX_ENTRY_CHARS (600 < 700 budget) by design.
        StringBuilder raw = new StringBuilder("[");
        raw.append('"').append("fener ");
        for (int i = 0; i < 100; i++) raw.append("zzzzzzzzzz");
        raw.append('"');
        raw.append(",\"fener küçük kayıt\"]");
        List<String> memory = ChatMemory.decode(raw.toString());
        assertEquals(2, memory.size());
        List<String> hits = ChatMemory.relevant(memory, "fener bugün");
        assertEquals(1, hits.size());
        assertEquals("fener küçük kayıt", hits.get(0));
    }

    @Test
    public void recap_coversOnlyOlderMessages_withinBudget() {
        List<Message> history = new ArrayList<>();
        history.add(new Message("Elif", "Gece limana yanaştık. Fener yeşildi.", false, 1L));
        history.add(new Message("Kullanıcı", "Feneri inceledik, kule kapalıydı.", true, 2L));
        history.add(new Message("Elif", "Pusula güneyi gösteriyor.", false, 3L));
        history.add(new Message("Kullanıcı", "Güneye rotayı çevir.", true, 4L));
        String recap = ChatMemory.recap(history, 2, ChatMemory.RECAP_CHAR_BUDGET);
        assertTrue(recap.contains("Gece limana yanaştık."));
        assertTrue(recap.contains("Kullanıcı: Feneri inceledik"));
        assertFalse(recap.contains("Pusula")); // inside the window
        assertTrue(recap.length() <= ChatMemory.RECAP_CHAR_BUDGET);
        // Small budgets cut early instead of producing one endless line.
        String tiny = ChatMemory.recap(history, 0, 30);
        assertFalse(tiny.contains("Güneye rotayı çevir"));
    }

    @Test
    public void recap_handlesEmptyAndShortHistories() {
        assertEquals("", ChatMemory.recap(null, 8, 900));
        assertEquals("", ChatMemory.recap(new ArrayList<>(), 8, 900));
        List<Message> history = new ArrayList<>();
        history.add(new Message("Elif", "selam", false, 1L));
        assertEquals("", ChatMemory.recap(history, 8, 900)); // everything fits the window
        // Image-only old messages keep a placeholder instead of an empty line.
        history.add(new Message("Elif", null, false, 2L, "/img.png"));
        String recap = ChatMemory.recap(history, 0, 900); // window 0 → both messages are "older"
        assertTrue(recap.contains("[görsel]"));
    }
}
