package app.localrp;

/**
 * v0.36.00: user-selectable interface scale options. The scale rides Android's
 * sp-based text sizing (fontScale), so every label, balloon and list row scales
 * together while dp-based spacing keeps the layout stable.
 * Pure logic, no Android imports — JVM-tested.
 */
final class UiScale {
    private UiScale() {}

    static final float MIN = 0.85f;
    static final float MAX = 1.30f;
    static final float DEFAULT = 1.0f;

    /** Choice values shown in the appearance screen (percent labels). */
    static final float[] CHOICES = {0.85f, 1.0f, 1.15f, 1.30f};

    /** Clamps any stored value into the supported range; NaN falls back to 1.0. */
    static float clamp(float value) {
        if (Float.isNaN(value)) return DEFAULT;
        return Math.max(MIN, Math.min(MAX, value));
    }

    /** Language-neutral percent label body (e.g. 1.15f → 115). */
    static int percent(float value) {
        return Math.round(clamp(value) * 100);
    }
}
