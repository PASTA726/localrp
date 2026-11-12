package app.localrp;

import static org.junit.Assert.assertEquals;
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
}
