package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Preset character catalog integrity (v0.14.0): every preset must be a self-contained,
 * copy-paste-safe template with a distinct Turkish name and non-empty coaching fields,
 * since tapping a preset creates a real CharacterProfile without further validation.
 */
public class PresetCharactersTest {

    @Test
    public void presets_areNotEmpty() {
        assertTrue(PresetCharacters.PRESETS.length >= 5);
    }

    @Test
    public void presetNames_areUniqueAndClean() {
        for (int i = 0; i < PresetCharacters.PRESETS.length; i++) {
            PresetCharacters.Preset a = PresetCharacters.PRESETS[i];
            assertNotEquals("empty name: " + i, "", a.name.trim());
            for (int j = i + 1; j < PresetCharacters.PRESETS.length; j++) {
                assertNotEquals("duplicate name", a.name, PresetCharacters.PRESETS[j].name);
            }
        }
    }

    @Test
    public void presetFields_areMeaningful() {
        for (PresetCharacters.Preset preset : PresetCharacters.PRESETS) {
            assertNotNull(preset.description);
            assertNotNull(preset.personality);
            assertNotNull(preset.instructions);
            assertNotNull(preset.examples);
            assertTrue("description: " + preset.name, preset.description.trim().length() >= 20);
            assertTrue("personality: " + preset.name, preset.personality.trim().length() >= 20);
            // A preset that would let the model speak for the user breaks the RP contract.
            assertTrue("user-restriction rule: " + preset.name,
                    preset.instructions.contains("kullanıcının"));
            assertTrue("examples start with the speaker label: " + preset.name,
                    preset.examples.startsWith(preset.name + ":"));
        }
    }
}
