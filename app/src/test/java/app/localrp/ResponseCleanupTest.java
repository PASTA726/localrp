package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Streaming + final response cleanup (task G0). The interim balloon must never show
 * the echoed speaker label or engine special tokens; the same output is stored finally.
 */
public class ResponseCleanupTest {

    private static final String SPEAKER = "Momy -Ashe-";

    @Test
    public void stripsEchoedSpeakerLabelWithColon() {
        assertEquals("Ah, Ashe! Bir rüya değilsin.",
                ResponseCleanup.clean("Momy -Ashe-: Ah, Ashe! Bir rüya değilsin.", SPEAKER));
        assertEquals("Kısa yanıt.", ResponseCleanup.clean(SPEAKER + ": Kısa yanıt.", SPEAKER));
        assertEquals("Yanıt", ResponseCleanup.clean(SPEAKER + ":Yanıt", SPEAKER));
        assertEquals("Yanıt", ResponseCleanup.clean(SPEAKER + "- Yanıt", SPEAKER));
        assertEquals("Yanıt", ResponseCleanup.clean(SPEAKER + "：Yanıt", SPEAKER));
    }

    @Test
    public void stripsAsteriskDecoratedLabel() {
        assertEquals("Sırıtıyor.", ResponseCleanup.clean("*Momy -Ashe-* Sırıtıyor.", SPEAKER));
        assertEquals("Sırıtıyor.", ResponseCleanup.clean("**Momy -Ashe-**: Sırıtıyor.", SPEAKER));
    }

    @Test
    public void labelOnlyWhenCaseIsExact() {
        // A DIFFERENT character name may legitimately start the reply; nothing is stripped.
        String reply = "Ashe burada, iyi misin?";
        assertEquals(reply, ResponseCleanup.clean(reply, SPEAKER));
    }

    @Test
    public void stripsEngineSpecialTokens() {
        String cleaned = ResponseCleanup.clean("Üzgün müsün?<|im_end|> Merhaba</s>", SPEAKER);
        assertFalse(cleaned.contains("<|"));
        assertFalse(cleaned.contains("</s>"));
        assertTrue(cleaned.contains("Üzgün müsün?"));
        assertTrue(cleaned.contains("Merhaba"));
    }

    @Test
    public void collapsesRunsOfBlankLines() {
        assertEquals("a\n\nb", ResponseCleanup.clean("a\n\n\n\n\nb", SPEAKER));
    }

    @Test
    public void emptyAndNullInputs() {
        assertEquals("", ResponseCleanup.clean(null, SPEAKER));
        assertEquals("", ResponseCleanup.clean("", SPEAKER));
        assertEquals("", ResponseCleanup.clean(SPEAKER + ":", SPEAKER)); // label with no body
        // No speaker → text passes through untouched
        assertEquals("Selam", ResponseCleanup.clean("Selam", null));
    }

    // ---- Think yeteneği (v0.28.00): <think>…<think> temizliği ----

    @Test
    public void thinkBlocks_removedWhereverTheyAppear() {
        String reply = "Merhaba.";
        assertEquals(reply, ResponseCleanup.clean("<think> Sahne devam ediyor, konu: rüzgar gemisi. </think>" + reply, SPEAKER));
        assertEquals(reply, ResponseCleanup.clean(reply + "<think> düşüncem burada. </think>", SPEAKER));
        assertEquals(reply, ResponseCleanup.clean("<think> a </think>" + reply + "<think> b </think>", SPEAKER));
        // Multi-line block with surrounding newlines collapses cleanly.
        assertEquals(reply, ResponseCleanup.clean("<think>\nsatır1\nsatır2\n</think>\n" + reply, SPEAKER));
        // No think content → unchanged.
        assertEquals(reply, ResponseCleanup.clean(reply, SPEAKER));
    }

    @Test
    public void unclosedThinkTag_hidesEverythingFromItOn() {
        // Stream cut mid-thought: an unclosed <think> must not leak the reasoning into
        // the balloon — everything from the tag on disappears.
        assertEquals("Görünür yanıt.", ResponseCleanup.clean("Görünür yanıt. <think> yarım düşün", SPEAKER));
        // Closed blocks elsewhere still strip even when an unclosed one exists.
        assertEquals("A.B.", ResponseCleanup.clean("A.<think>x</think>B.<think>yarım", SPEAKER));
        // Unclosed tag as the whole message → empty.
        assertEquals("", ResponseCleanup.clean("<think>yarım", SPEAKER));
        // A BARE tag IS an unclosed think tag: everything from it on (the whole
        // message) hides by design — no false-positive exceptions needed.
        assertEquals("a", ResponseCleanup.clean("a <think> b", SPEAKER));
    }

    @Test
    public void thinkLabelEchoedAfterBlock_alsoStripped() {
        // Some models echo the speaker label right after the think block.
        assertEquals("Selam!", ResponseCleanup.clean("<think> plan </think>Momy -Ashe-: Selam!", SPEAKER));
    }

    // ---- v0.30.00 stability: hot-path + stress ----

    @Test
    public void stress_streamingChunks_stayBoundedAndCorrect() {
        // The cleanup runs on EVERY streaming chunk; a long session must not slow
        // down or corrupt. 500 chunks, each with a think block + label + blanks.
        long start = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            String out = ResponseCleanup.clean(
                    "<think> partial reasoning " + i + " </think>" + SPEAKER + ": Yanıt " + i + "\n\n\n\nDevam.",
                    SPEAKER);
            assertEquals("Yanıt " + i + "\n\nDevam.", out);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue("500 chunks took " + ms + "ms — hot path regressed", ms < 2000);
    }

    @Test
    public void stress_manySpeakers_cacheNeverGrowsUnbounded() {
        // > LABEL_CACHE_MAX distinct speakers: cache evicts, results stay correct.
        for (int i = 0; i < 40; i++) {
            String speaker = "Karakter" + i;
            String out = ResponseCleanup.clean(speaker + ": selam", speaker);
            assertEquals("selam", out);
        }
        // Original speaker still correct after the eviction churn.
        assertEquals("Yanıt.", ResponseCleanup.clean(SPEAKER + ": Yanıt.", SPEAKER));
    }

    @Test
    public void stress_labelCache_lruCapAndReuse() {
        // v0.31.00: the per-speaker cache is LRU-capped at 16 (recently used entries
        // survive; the oldest are dropped instead of wiping the whole cache). Churn
        // beyond the cap must keep results correct and the size bounded.
        assertTrue("cache starts over cap: " + ResponseCleanup.cachedLabelCount(),
                ResponseCleanup.cachedLabelCount() <= 16);
        for (int i = 0; i < 30; i++) {
            String speaker = "LruKarakter" + i;
            assertEquals("merhaba", ResponseCleanup.clean(speaker + ": merhaba", speaker));
        }
        assertTrue("cache exceeded cap: " + ResponseCleanup.cachedLabelCount(),
                ResponseCleanup.cachedLabelCount() <= 16);
        // A re-used speaker recompiles cleanly after being evicted.
        assertEquals("selam", ResponseCleanup.clean("LruKarakter0: selam", "LruKarakter0"));
        assertTrue(ResponseCleanup.cachedLabelCount() <= 16);
    }

    @Test
    public void stress_hugeThinkFlood_noBlowup() {
        // A model that opens many think blocks in one reply (worst-case quadratic
        // substring concern) must stay fast and clean.
        StringBuilder raw = new StringBuilder("Görünür başlangıç.");
        for (int i = 0; i < 200; i++) raw.append("<think> blok ").append(i).append(" </think> ara metin");
        raw.append("Görünür son.");
        long start = System.nanoTime();
        String out = ResponseCleanup.clean(raw.toString(), SPEAKER);
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue("filler survives closed blocks", out.contains("ara metin") && out.startsWith("Görünür başlangıç.") && out.endsWith("Görünür son."));
        assertTrue("200 think blocks took " + ms + "ms", ms < 1000);
    }
}
