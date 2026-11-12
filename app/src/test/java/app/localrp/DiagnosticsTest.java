package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Crash-record + report formatting rules for the v0.20.01 diagnostics card. */
public class DiagnosticsTest {
    private Throwable sample() {
        try {
            throw new IllegalStateException("kurulum bozuldu");
        } catch (IllegalStateException caught) {
            return caught;
        }
    }

    @Test
    public void fromThrowable_capturesTypeMessageAndStack() {
        Diagnostics.CrashRecord crash = Diagnostics.fromThrowable(42L, sample());
        assertEquals(42L, crash.at);
        assertTrue(crash.type.endsWith("IllegalStateException"));
        assertEquals("kurulum bozuldu", crash.message);
        assertTrue(crash.stack.contains("DiagnosticsTest.sample:"));
    }

    @Test
    public void fromThrowable_handlesNullAndNullMessage() {
        Diagnostics.CrashRecord none = Diagnostics.fromThrowable(1L, null);
        assertEquals("unknown", none.type);
        assertEquals("", none.message);
        assertEquals("", none.stack);
        Diagnostics.CrashRecord silent = Diagnostics.fromThrowable(1L, new RuntimeException());
        assertEquals("", silent.message);
    }

    @Test
    public void longMessage_isCapped() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 400; i++) big.append('x');
        Diagnostics.CrashRecord crash = Diagnostics.fromThrowable(1L, new RuntimeException(big.toString()));
        assertEquals(300, crash.message.length());
    }

    @Test
    public void stackTail_limitsFramesAndHandlesNull() {
        StackTraceElement[] frames = new StackTraceElement[30];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new StackTraceElement("pkg.C" + i, "m", "C.java", i);
        }
        String tail = Diagnostics.stackTail(frames, 8);
        assertEquals(8, tail.split("\n").length);
        assertTrue(tail.contains("pkg.C0.m:0"));
        assertFalse(tail.contains("pkg.C8.m:8"));
        assertEquals("", Diagnostics.stackTail(null, 8));
    }

    @Test
    public void buildReport_containsDeviceInfoAndCrashBlock() {
        Diagnostics.CrashRecord crash = Diagnostics.fromThrowable(1L, sample());
        String report = Diagnostics.buildReport("0.20.01-alpha1", 34, "Reeder S71",
                2L * 1024L * 1024L * 1024L, crash);
        assertTrue(report.contains("LocalRP 0.20.01-alpha1"));
        assertTrue(report.contains("Android 34"));
        assertTrue(report.contains("Reeder S71"));
        assertTrue(report.contains("2048 MB boş depolama"));
        assertTrue(report.contains("Son çökme: "));
        assertTrue(report.contains("kurulum bozuldu"));
    }

    @Test
    public void buildReport_withoutCrash_omitsCrashBlock() {
        String report = Diagnostics.buildReport("0.20.01-alpha1", 34, "test", 1024L * 1024L, null);
        assertFalse(report.contains("Son çökme"));
        assertTrue(report.contains("1 MB boş depolama"));
    }
}
