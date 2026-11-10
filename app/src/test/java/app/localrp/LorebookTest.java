package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Unit tests for the world book (DeepSeek item 12): codec, triggering, budget. */
public class LorebookTest {

    private Lorebook.Entry entry(String keys, String text) {
        return new Lorebook.Entry(keys, text);
    }

    @Test
    public void encodeDecode_roundTripsKeysAndText() {
        List<Lorebook.Entry> entries = Arrays.asList(
                entry("Kız Kulesi, deniz feneri", "Şehrin efsanesine göre kulede yaşayan bekçi yolcuları yön bulur."),
                entry("buz kraliçesi", "Kuzeyin kadim yöneticisi; yaprakları buzlu taç takar."));
        String encoded = Lorebook.encode(entries);
        List<Lorebook.Entry> decoded = Lorebook.decode(encoded);
        assertEquals(entries.size(), decoded.size());
        assertEquals(entries.get(0).keys, decoded.get(0).keys);
        assertEquals(entries.get(1).text, decoded.get(1).text);
    }

    @Test
    public void decode_rejectsGarbageAndSkipsBadRecords() {
        assertTrue(Lorebook.decode(null).isEmpty());
        assertTrue(Lorebook.decode("not json at all").isEmpty());
        // A record missing a field is skipped, the whole array is not dropped.
        List<Lorebook.Entry> decoded = Lorebook.decode("[{\"k\":\"anahtar\"},{\"k\":\"a\",\"t\":\"olgu\"}]");
        assertEquals(1, decoded.size());
        assertEquals("a", decoded.get(0).keys);
        assertEquals("olgu", decoded.get(0).text);
    }

    @Test
    public void encode_skipsBlankEntries() {
        String encoded = Lorebook.encode(Arrays.asList(
                entry("", "no keys"),
                entry("keys only", ""),
                entry("geçerli", "olgu")));
        // Field order inside records is an implementation detail; round-trip check instead.
        List<Lorebook.Entry> decoded = Lorebook.decode(encoded);
        assertEquals(1, decoded.size());
        assertEquals("geçerli", decoded.get(0).keys);
        assertEquals("olgu", decoded.get(0).text);
    }

    @Test
    public void triggered_matchesCaseInsensitivelyAcrossCommaKeys() {
        List<Lorebook.Entry> entries = Arrays.asList(
                entry("Kız Kulesi, fener", "kule bilgi"),
                entry("KRAL", "büyük harf bilgi"),
                entry("", "anahtarsız olgu hiç tetiklenmez"),
                entry("boş metin", ""));
        List<Lorebook.Entry> hits = Lorebook.triggered(entries,
                "Gemi kız kulesi'nin önünden geçti.");
        assertEquals(1, hits.size());
        assertEquals("kule bilgi", hits.get(0).text);

        assertEquals(1, Lorebook.triggered(entries, "kRAL herkesi dondurdu.").size());
        assertTrue(Lorebook.triggered(entries, "alakasız bir cümle").isEmpty());
        assertTrue(Lorebook.triggered(entries, null).isEmpty());
    }

    @Test
    public void triggered_foldsTurkishDotlessAndDottedVariantsSymmetrically() {
        List<Lorebook.Entry> uppercaseKeys = Arrays.asList(entry("IZMIR", "liman şehri"));
        List<Lorebook.Entry> lowercaseKeys = Arrays.asList(entry("izmir", "liman şehri"));
        List<Lorebook.Entry> dotlessTextKeys = Arrays.asList(entry("ızmir", "liman şehri"));
        // All four spelling variants collapse onto the same folded form on both sides.
        assertEquals(1, Lorebook.triggered(uppercaseKeys, "izmir limanina geldi").size());
        assertEquals(1, Lorebook.triggered(lowercaseKeys, "IZMIR").size());
        assertEquals(1, Lorebook.triggered(dotlessTextKeys, "ızmir'e döndü.").size());
        assertEquals(1, Lorebook.triggered(dotlessTextKeys, "İzmir'e döndü.").size());
    }

    @Test
    public void assemble_respectsCharacterBudget() {
        List<Lorebook.Entry> hits = Arrays.asList(
                entry("a", "kısa olgu"),
                entry("b", repeatedWord("uzun", 600)),
                entry("c", "sığmayan olgu"));
        String block = Lorebook.assemble(hits, 120);
        assertFalse(block.isEmpty());
        assertTrue(block.length() <= 120);
        assertTrue(block.contains("kısa olgu"));
        // The third entry never fits; only the budget-fitting prefix is shipped.
        assertFalse(block.contains("sığmayan olgu"));
    }

    @Test
    public void assemble_zeroBudgetProducesEmptyBlock() {
        assertEquals("", Lorebook.assemble(Arrays.asList(entry("a", "olgu")), 0));
        assertEquals("", Lorebook.assemble(new ArrayList<>(), 900));
    }

    /** Repeats a word n times joined by spaces, for budget tests. */
    private static String repeatedWord(String word, int repeats) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < repeats; i++) {
            if (i > 0) s.append(' ');
            s.append(word);
        }
        return s.toString();
    }
}
