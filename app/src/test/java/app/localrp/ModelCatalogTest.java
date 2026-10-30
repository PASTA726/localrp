package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Deterministic catalog integrity checks (Task 06): the curated download list must
 * stay internally consistent — https-only URLs pointing at the reviewed source host,
 * trusted byte sizes, and file names the downloader will actually accept. No network
 * calls and no downloads: only the shipped metadata is validated.
 */
public class ModelCatalogTest {

    @Test
    public void catalogs_areNotEmpty() {
        assertTrue(ModelCatalog.CPU_MODELS.length > 0);
        assertTrue(ModelCatalog.GPU_MODELS.length > 0);
    }

    @Test
    public void everyModel_hasHttpsHuggingFaceUrl() {
        for (ModelCatalog.Model model : allModels()) {
            assertTrue("https URL required: " + model.name, model.url.startsWith("https://"));
            assertTrue("reviewed host only: " + model.url, model.url.startsWith("https://huggingface.co/"));
            assertNotEquals("expected byte size required: " + model.name, 0L, model.bytes);
            assertTrue("positive byte size: " + model.name, model.bytes > 0);
            assertTrue("sensible context length: " + model.name, model.contextTokens >= 512);
        }
    }

    @Test
    public void everyModel_fileHintIsDownloaderSafe() {
        for (ModelCatalog.Model model : allModels()) {
            String safe = DownloadPolicy.INSTANCE.sanitizeFileName(model.fileHint);
            assertEquals("fileHint must pass downloader hardening (" + model.name + "): " + model.fileHint,
                    model.fileHint, safe);
        }
    }

    @Test
    public void namesAndHints_areUniqueWithinEachVisibleList() {
        // MainActivity shows either the CPU list or the GPU list, never both — so the
        // invariant is per-list uniqueness. The same file intentionally appears in
        // both lists (identical url + fileHint); cross-list repeats are fine.
        checkUnique(ModelCatalog.CPU_MODELS);
        checkUnique(ModelCatalog.GPU_MODELS);
    }

    private static void checkUnique(ModelCatalog.Model[] models) {
        java.util.HashSet<String> names = new java.util.HashSet<>();
        java.util.HashSet<String> hints = new java.util.HashSet<>();
        for (ModelCatalog.Model model : models) {
            assertTrue("duplicate model name in one list: " + model.name, names.add(model.name));
            assertTrue("duplicate filehint in one list: " + model.fileHint, hints.add(model.fileHint));
        }
        assertEquals(models.length, names.size());
        assertEquals(models.length, hints.size());
    }

    @Test
    public void everyModel_carriesUserFacingGuidance() {
        for (ModelCatalog.Model model : allModels()) {
            assertFalse("why text required: " + model.name, model.why == null || model.why.trim().isEmpty());
            assertFalse("speed range required: " + model.name, model.cpuRange.trim().isEmpty());
            assertFalse("language note required: " + model.name, model.lang.trim().isEmpty());
        }
    }

    @Test
    public void everyModel_shipsTrustedSha256() {
        // Task G4/B3: the downloader now verifies the finished file against the catalog
        // digest. Every entry must carry a full lowercase hex digest so no download can
        // be verified by size alone.
        for (ModelCatalog.Model model : allModels()) {
            assertTrue("sha256 required: " + model.name,
                    model.sha256 != null && model.sha256.matches("[a-f0-9]{64}"));
        }
    }

    private static ModelCatalog.Model[] allModels() {
        ModelCatalog.Model[] merged = new ModelCatalog.Model[ModelCatalog.CPU_MODELS.length + ModelCatalog.GPU_MODELS.length];
        System.arraycopy(ModelCatalog.CPU_MODELS, 0, merged, 0, ModelCatalog.CPU_MODELS.length);
        System.arraycopy(ModelCatalog.GPU_MODELS, 0, merged, ModelCatalog.CPU_MODELS.length, ModelCatalog.GPU_MODELS.length);
        return merged;
    }
}
