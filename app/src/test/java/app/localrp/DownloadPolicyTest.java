package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;

/**
 * Unit tests for the pure download decision layer (Task 04 scenarios): https policy,
 * file-name hardening, Content-Range resume validation, restart on ignored/inconsistent
 * Range handling and SHA-256 verification. No network access and no model downloads.
 */
public class DownloadPolicyTest {

    // ---- URL policy ----

    @Test
    public void httpsPolicy_acceptsHttpsAndRejectsPlainHttpOrGarbage() {
        assertTrue(DownloadPolicy.INSTANCE.isHttps("https://huggingface.co/x.gguf"));
        assertFalse(DownloadPolicy.INSTANCE.isHttps("http://huggingface.co/x.gguf"));
        assertFalse(DownloadPolicy.INSTANCE.isHttps("ftp://example.org/x.gguf"));
        assertFalse(DownloadPolicy.INSTANCE.isHttps("not a url"));
        assertFalse(DownloadPolicy.INSTANCE.isHttps(""));
    }

    // ---- file names ----

    @Test
    public void sanitize_acceptsPlainModelName() {
        assertEquals("llama32-1b-q4-km.gguf", DownloadPolicy.INSTANCE.sanitizeFileName("llama32-1b-q4-km.gguf"));
        assertEquals("model .gguf", DownloadPolicy.INSTANCE.sanitizeFileName("  model .gguf  "));
    }

    @Test
    public void sanitize_rejectsTraversalSeparatorsAndNonGguf() {
        assertNull(DownloadPolicy.INSTANCE.sanitizeFileName("sub/dir.gguf"));
        assertNull(DownloadPolicy.INSTANCE.sanitizeFileName("sub\\dir.gguf"));
        assertNull(DownloadPolicy.INSTANCE.sanitizeFileName("..gguf"));
        assertNull(DownloadPolicy.INSTANCE.sanitizeFileName("a..b.gguf"));
        assertNull(DownloadPolicy.INSTANCE.sanitizeFileName(".hidden.gguf"));
        assertNull(DownloadPolicy.INSTANCE.sanitizeFileName("model.bin"));
        assertNull(DownloadPolicy.INSTANCE.sanitizeFileName(""));
        assertNull(DownloadPolicy.INSTANCE.sanitizeFileName("  "));
        assertNull(DownloadPolicy.INSTANCE.sanitizeFileName("bad\u0000name.gguf"));
    }

    // ---- resume decisions ----

    @Test
    public void resume_freshFetchIsAlwaysResumable() {
        DownloadPolicy.Decision decision = DownloadPolicy.INSTANCE.resumeDecision(200, 0L, null, 123L);
        assertEquals(DownloadPolicy.Verdict.RESUME, decision.getVerdict());
        assertEquals(123L, decision.getTotal().longValue());
        // no Content-Length: still fine, total just unknown
        DownloadPolicy.Decision unknown = DownloadPolicy.INSTANCE.resumeDecision(206, 0L, "bytes 0-0/1", -1L);
        assertEquals(DownloadPolicy.Verdict.RESUME, unknown.getVerdict());
        assertNull(unknown.getTotal());
    }

    @Test
    public void resume_206WithMatchingLiteralOffsetAppends() {
        DownloadPolicy.Decision decision = DownloadPolicy.INSTANCE.resumeDecision(
                206, 4096L, "bytes 4096-8000/10000", 1000L);
        assertEquals(DownloadPolicy.Verdict.RESUME, decision.getVerdict());
        assertEquals(10000L, decision.getTotal().longValue());
    }

    @Test
    public void resume_206WithInconsistentOffsetRestartInsteadOfBlindAppend() {
        // Server resumed from a different offset: appending would corrupt the file.
        DownloadPolicy.Decision mismatch = DownloadPolicy.INSTANCE.resumeDecision(206, 4096L, "bytes 512-8000/10000", 7489L);
        assertEquals(DownloadPolicy.Verdict.RESTART, mismatch.getVerdict());
        // Total smaller than what we already hold is equally inconsistent.
        DownloadPolicy.Decision shorter = DownloadPolicy.INSTANCE.resumeDecision(206, 5000L, "bytes 5000-8000/4000", 3000L);
        assertEquals(DownloadPolicy.Verdict.RESTART, shorter.getVerdict());
    }

    @Test
    public void resume_206WithoutParsableContentRangeRestart() {
        DownloadPolicy.Decision missing = DownloadPolicy.INSTANCE.resumeDecision(206, 4096L, null, 1000L);
        assertEquals(DownloadPolicy.Verdict.RESTART, missing.getVerdict());
        DownloadPolicy.Decision garbage = DownloadPolicy.INSTANCE.resumeDecision(206, 4096L, "wat", 0L);
        assertEquals(DownloadPolicy.Verdict.RESTART, garbage.getVerdict());
    }

    @Test
    public void resume_serverIgnoringRangeForcesRestart() {
        // 200 answer to a Range request would duplicate bytes if appended.
        DownloadPolicy.Decision decision = DownloadPolicy.INSTANCE.resumeDecision(200, 4096L, null, 4096L);
        assertEquals(DownloadPolicy.Verdict.RESTART, decision.getVerdict());
    }

    @Test
    public void parseContentRange_validatesShape() {
        assertEquals(0L, DownloadPolicy.INSTANCE.parseContentRange("bytes 0-9/100").getFirst().longValue());
        assertEquals(9L, DownloadPolicy.INSTANCE.parseContentRange("bytes 0-9/100").getSecond().longValue());
        assertEquals(100L, DownloadPolicy.INSTANCE.parseContentRange("bytes 0-9/100").getThird().longValue());
        assertNull(DownloadPolicy.INSTANCE.parseContentRange(null));
        assertNull(DownloadPolicy.INSTANCE.parseContentRange("bytes 10-5/100")); // start > end
        assertNull(DownloadPolicy.INSTANCE.parseContentRange("bytes 5-99/99")); // end >= total
    }

    // ---- SHA-256 ----

    @Test
    public void sha256_matchesKnownDigestForSmallFile() throws Exception {
        File file = File.createTempFile("dlp", ".tmp", new File(System.getProperty("java.io.tmpdir")));
        file.deleteOnExit();
        FileWriter writer = new FileWriter(file);
        writer.write("abc");
        writer.close();
        // Canonical SHA-256 of the bytes "abc"
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                DownloadPolicy.INSTANCE.sha256Hex(file));
        file.delete();
    }

    @Test
    public void shaMatches_isCaseInsensitiveAndRequiresFullLength() {
        String real = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertTrue(DownloadPolicy.INSTANCE.shaMatches(real, real));
        assertTrue(DownloadPolicy.INSTANCE.shaMatches(real.toUpperCase(), real));
        assertFalse(DownloadPolicy.INSTANCE.shaMatches(real, "0000000000000000000000000000000000000000000000000000000000000000"));
        // truncated/trusted-slot misuse: a non-64-char "expected" never verifies
        assertFalse(DownloadPolicy.INSTANCE.shaMatches("abc", "abc"));
    }

    @Test
    public void sha256_returnsNullForMissingFile() {
        File missing = new File(System.getProperty("java.io.tmpdir"), "localrp-definitely-missing-" + System.nanoTime());
        assertNull(DownloadPolicy.INSTANCE.sha256Hex(missing));
    }
}
