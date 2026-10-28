package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for the pure runtime decision layer (Task 05): context budget
 * derivation and clamping, prediction-length clamping, and the reload matrix.
 * These are deterministic decisions; no engine, model files or Android runtime.
 */
public class RuntimePolicyTest {

    // ---- context budget ----

    @Test
    public void budget_withoutModelInfoCapsAtEngineContext() {
        // 8192 engine tokens * 2.5 chars = 20480 chars, clamped to the app ceiling.
        assertEquals(12000, RuntimePolicy.INSTANCE.contextCharBudget(-1, null));
    }

    @Test
    public void budget_scalesWithKnownModelContext() {
        // 4096 training tokens * 2.5 = 10240 chars, inside the clamp.
        assertEquals(10240, RuntimePolicy.INSTANCE.contextCharBudget(-1, 4096));
    }

    @Test
    public void budget_tinyModelTrainingContextStillRespectsFloor() {
        // 512 tokens * 2.5 = 1280 chars, clamped up to the 2048 floor.
        assertEquals(2048, RuntimePolicy.INSTANCE.contextCharBudget(-1, 512));
    }

    @Test
    public void budget_modelLargerThanEngineUsesEngineContext() {
        assertEquals(12000, RuntimePolicy.INSTANCE.contextCharBudget(-1, 131072));
    }

    @Test
    public void budget_manualOverridesWinsInsideHardBounds() {
        assertEquals(6000, RuntimePolicy.INSTANCE.contextCharBudget(6000, null));
        // below the hard floor / above the ceiling
        assertEquals(1024, RuntimePolicy.INSTANCE.contextCharBudget(500, 4096));
        assertEquals(12000, RuntimePolicy.INSTANCE.contextCharBudget(999999, 4096));
        // non-positive manual values are ignored (auto budget is derived instead)
        assertEquals(12000, RuntimePolicy.INSTANCE.contextCharBudget(0, null));
    }

    // ---- prediction length ----

    @Test
    public void predictLength_isClampedToEngineBounds() {
        assertEquals(320, RuntimePolicy.INSTANCE.predictLength(320));
        assertEquals(32, RuntimePolicy.INSTANCE.predictLength(0));
        assertEquals(32, RuntimePolicy.INSTANCE.predictLength(-50));
        assertEquals(2048, RuntimePolicy.INSTANCE.predictLength(100000));
    }

    // ---- reload decision ----

    @Test
    public void reload_userResetAlwaysReloads() {
        assertTrue(RuntimePolicy.INSTANCE.reloadNeeded(true, "chatA", "chatA", "ready"));
    }

    @Test
    public void reload_differentConversationReloads() {
        assertTrue(RuntimePolicy.INSTANCE.reloadNeeded(false, "chatA", "chatB", "ready"));
    }

    @Test
    public void reload_sameConversationWithReadyContextStays() {
        assertFalse(RuntimePolicy.INSTANCE.reloadNeeded(false, "chatA", "chatA", "ready"));
    }

    @Test
    public void reload_brokenOrFreshEngineStatesReload() {
        assertTrue(RuntimePolicy.INSTANCE.reloadNeeded(false, "chatA", "chatA", "error"));
        assertTrue(RuntimePolicy.INSTANCE.reloadNeeded(false, "chatA", "chatA", "initialized"));
        // A bench that never recovered must not let generation prompt a dead engine.
        assertTrue(RuntimePolicy.INSTANCE.reloadNeeded(false, "chatA", "chatA", "benchmarking"));
    }

    @Test
    public void reload_systemPromptProcessingWaitsInsteadOfReloading() {
        // The caller waits (first {}) for this state; the policy never treats it as reload.
        assertFalse(RuntimePolicy.INSTANCE.reloadNeeded(false, "chatA", "chatA", "system-prompt"));
        assertFalse(RuntimePolicy.INSTANCE.reloadNeeded(false, null, "chatA", "ready"));
    }
}
