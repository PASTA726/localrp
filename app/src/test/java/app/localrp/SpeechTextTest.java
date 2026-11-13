package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** TTS utterance splitting rules for the v0.22.00 voice reading feature. */
public class SpeechTextTest {
    @Test
    public void shortText_isOneChunk() {
        List<String> parts = SpeechText.split("Kısa bir cümle.");
        assertEquals(1, parts.size());
        assertEquals("Kısa bir cümle.", parts.get(0));
    }

    @Test
    public void blankInput_yieldsNoChunks() {
        assertEquals(0, SpeechText.split(null).size());
        assertEquals(0, SpeechText.split("   \n").size());
    }

    @Test
    public void longText_splitsOnSentences_withinCap() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 400; i++) builder.append("Cümle ").append(i).append(" burada biter. ");
        List<String> parts = SpeechText.split(builder.toString());
        assertTrue(parts.size() > 1);
        for (String part : parts) {
            assertTrue(part.length() <= SpeechText.MAX_CHUNK);
        }
        // No text lost, ignoring the whitespace the splitter adds between parts.
        String rejoined = String.join(" ", parts).replaceAll("\\s+", " ").trim();
        String original = builder.toString().replaceAll("\\s+", " ").trim();
        assertEquals(original, rejoined);
    }

    @Test
    public void singleHugeSentence_hardWrapsOnSpaces() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 600; i++) builder.append("kelime ");
        List<String> parts = SpeechText.split(builder.toString());
        assertTrue(parts.size() > 1);
        for (String part : parts) {
            assertTrue(part.length() <= SpeechText.MAX_CHUNK);
            assertTrue(part.contains("kelime"));
        }
    }

    @Test
    public void paragraphBreaks_cutCleanly() {
        List<String> parts = SpeechText.split("Birinci paragraf.\n\nİkinci paragraf.");
        // Paragraph breaks are candidate cut points, but a short text still
        // belongs in a single utterance; what matters is that no text is lost.
        String rejoined = String.join(" ", parts).replaceAll("\\s+", " ").trim();
        assertEquals("Birinci paragraf. İkinci paragraf.", rejoined);
    }

    // ---- v0.27.00 hardening tests ----

    @Test
    public void decimalsAndUrls_stayInOneUtterance() {
        // v0.27.00: a terminator followed by a non-whitespace char must not cut —
        // decimals and URLs used to be split mid-term by the old boundary rule.
        // Short input re-merges into one chunk, so flood past the cap and assert
        // that no part carries the split signature of a broken "3.14" token.
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 200; i++) builder.append("Fiyat 3.14 lira. Yol github.com/a/b. ");
        List<String> parts = SpeechText.split(builder.toString());
        assertTrue(parts.size() > 1);
        for (String part : parts) {
            assertFalse("decimal split mid-term", part.endsWith("3."));
            assertFalse("decimal split mid-term", part.startsWith("14 "));
            // The URL's dots must stay whole too: a part may not end on a bare
            // "github." or begin mid-URL with "com/".
            assertFalse("url split mid-term", part.endsWith("github."));
            assertFalse("url split mid-term", part.startsWith("com/"));
        }
    }

    @Test
    public void emojiOverflow_neverSplitsSurrogatePairs() {
        // A spaceless emoji flood forces the hard-wrap fallback: the cut point may
        // land inside a surrogate pair, which would corrupt the text for TTS.
        // NOTE: a '😀' char literal compiles to the high surrogate only — the pair
        // must be written as a string escape to build real emoji.
        // Spaceless input re-joins with no added whitespace: join("") == original.
        StringBuilder builder = new StringBuilder();
        String emoji = "\uD83D\uDE00"; // 😀 as a real 2-char pair
        for (int i = 0; i < 2000; i++) builder.append(emoji); // 4000 chars
        String original = builder.toString();
        List<String> parts = SpeechText.split(original);
        assertTrue(parts.size() > 1);
        assertEquals(original, String.join("", parts)); // nothing lost, nothing doubled
        for (String part : parts) {
            assertFalse(part.isEmpty());
            // Uniform emoji content: no chunk may end on a lone high surrogate or
            // start on a lone low surrogate — that would be a pair cut in half.
            assertFalse("lone high surrogate at end",
                    Character.isHighSurrogate(part.charAt(part.length() - 1)));
            assertFalse("lone low surrogate at start",
                    Character.isLowSurrogate(part.charAt(0)));
            assertTrue(part.length() <= SpeechText.MAX_CHUNK);
        }
    }

    @Test
    public void noSpaceOverflow_hardCutsWithinCap() {
        // A spaceless ASCII flood exercises the cut==cap fallback: every chunk must
        // respect the cap and the text must survive byte-for-byte (no whitespace
        // exists in the input, so join("") reconstructs it exactly).
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 3000; i++) builder.append('x');
        String original = builder.toString();
        List<String> parts = SpeechText.split(original);
        assertTrue(parts.size() > 1);
        assertEquals(original, String.join("", parts));
        for (String part : parts) assertTrue(part.length() <= SpeechText.MAX_CHUNK);
    }
}
