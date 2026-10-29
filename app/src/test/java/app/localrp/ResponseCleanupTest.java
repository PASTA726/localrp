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
}
