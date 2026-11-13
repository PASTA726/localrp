package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Sampler resolution rules for v0.25.00: character > chat > global, null-safe. */
public class SamplerSettingsTest {
    @Test
    public void resolve_precedenceCharacterOverChatOverGlobal() {
        SamplerSettings.Values full = SamplerSettings.resolve(
                "0.9", "0.95", "0.7", "0.9", "0.4");
        assertEquals(0.4, full.temperature, 1e-9);   // character wins
        assertEquals(0.9, full.topP, 1e-9);         // chat wins
        assertFalse(full.isEmpty());

        // Chat profile still overrides the global when no character override.
        SamplerSettings.Values chat = SamplerSettings.resolve(
                "0.9", "0.95", "0.7", "", "");
        assertEquals(0.7, chat.temperature, 1e-9);
        assertEquals(0.95, chat.topP, 1e-9);        // chat empty → global

        // Nothing set anywhere: everything null (provider defaults apply).
        SamplerSettings.Values none = SamplerSettings.resolve(null, null, null, null, null);
        assertNull(none.temperature);
        assertNull(none.topP);
        assertTrue(none.isEmpty());
    }

    @Test
    public void resolve_partiallySetFields_stayIndependent() {
        SamplerSettings.Values partial = SamplerSettings.resolve(
                null, "0.8", "1.3", null, "");
        assertEquals(1.3, partial.temperature, 1e-9); // chat override
        assertEquals(0.8, partial.topP, 1e-9);        // global only
    }

    @Test
    public void parse_rejectsBlankInvalidAndOutOfRange() {
        assertNull(SamplerSettings.parse(null, 0, 2));
        assertNull(SamplerSettings.parse("", 0, 2));
        assertNull(SamplerSettings.parse("  ", 0, 2));
        assertNull(SamplerSettings.parse("abc", 0, 2));
        assertNull(SamplerSettings.parse("2.1", 0, 2));   // temperature cap
        assertNull(SamplerSettings.parse("-0.1", 0, 2));
        assertEquals(0.0, SamplerSettings.parse("0.0", 0, 2), 1e-9);
        assertEquals(2.0, SamplerSettings.parse("2.0", 0, 2), 1e-9);
        assertNull(SamplerSettings.parse("1.1", 0, 1));   // top-p cap
        assertEquals(1.0, SamplerSettings.parse("1.0", 0, 1), 1e-9);
    }
}
