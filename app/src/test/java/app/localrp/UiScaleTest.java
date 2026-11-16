package app.localrp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** v0.36.00: user-selectable interface scale options. */
public class UiScaleTest {

    @Test
    public void choices_areTheAdvertisedSteps() {
        assertEquals(4, UiScale.CHOICES.length);
        assertEquals(0.85f, UiScale.MIN, 0.0001f);
        assertEquals(0.85f, UiScale.CHOICES[0], 0.0001f);
        assertEquals(1.0f, UiScale.CHOICES[1], 0.0001f);
        assertEquals(1.15f, UiScale.CHOICES[2], 0.0001f);
        assertEquals(1.30f, UiScale.MAX, 0.0001f);
        assertEquals(1.30f, UiScale.CHOICES[3], 0.0001f);
    }

    @Test
    public void clamp_boundsAndNaN() {
        assertEquals(UiScale.MIN, UiScale.clamp(0.5f), 0.0001f);
        assertEquals(UiScale.MAX, UiScale.clamp(2.0f), 0.0001f);
        assertEquals(1.0f, UiScale.clamp(1.0f), 0.0001f);
        assertEquals(UiScale.DEFAULT, UiScale.clamp(Float.NaN), 0.0001f);
    }

    @Test
    public void percent_isLanguageNeutral() {
        assertEquals(85, UiScale.percent(0.85f));
        assertEquals(100, UiScale.percent(1.0f));
        assertEquals(115, UiScale.percent(1.15f));
        assertEquals(130, UiScale.percent(1.3f));
        // Out-of-range values clamp before formatting.
        assertEquals(130, UiScale.percent(5.0f));
    }
}
