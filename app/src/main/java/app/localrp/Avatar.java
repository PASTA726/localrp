package app.localrp;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Avatars are derived, never stored: the first letter plus a hue pair seeded by the
 * character (or group) name. No images ship in the APK and nothing is downloaded;
 * light/dark styling follows the theme so every palette reads well.
 */
public final class Avatar {
    private Avatar() {}

    /** Round TextView avatar with the first letter of the seed (a name). */
    public static TextView forName(Context context, String seed, int sizeDp) {
        TextView view = new TextView(context);
        view.setText(String.valueOf(letter(seed)));
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(sizeDp < 34 ? 14 : 18);
        view.setGravity(Gravity.CENTER);
        view.setBackground(tintedCircle(deriveHue(seed), Design.theme.dark));
        return view;
    }

    private static GradientDrawable tintedCircle(int hue, boolean dark) {
        int top = hsl(hue, dark ? 0.54f : 0.46f, dark ? 0.50f : 0.42f);
        int bottom = hsl((hue + 22) % 360, dark ? 0.60f : 0.54f, dark ? 0.34f : 0.54f);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        circle.setShape(GradientDrawable.OVAL);
        return circle;
    }

    /** Minimal HSL → int color (no AndroidX dependency). */
    private static int hsl(float h, float s, float l) {
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = l - c / 2f;
        float r, g, b;
        if (h < 60)      { r = c; g = x; b = 0; }
        else if (h < 120){ r = x; g = c; b = 0; }
        else if (h < 180){ r = 0; g = c; b = x; }
        else if (h < 240){ r = 0; g = x; b = c; }
        else if (h < 300){ r = x; g = 0; b = c; }
        else             { r = c; g = 0; b = x; }
        return android.graphics.Color.rgb(
                Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    private static int deriveHue(String seed) {
        if (seed == null || seed.isEmpty()) return 40;
        int acc = 7;
        for (int i = 0; i < seed.length(); i++) acc = (acc * 31 + seed.charAt(i)) & 0x7FFFFFFF;
        return acc % 360;
    }

    private static char letter(String seed) {
        if (seed == null) return '★';
        String trimmed = seed.trim();
        if (trimmed.isEmpty()) return '★';
        int codePoint = trimmed.codePointAt(0);
        int upper = Character.toUpperCase(codePoint);
        return Character.isLetterOrDigit(upper) ? (char) upper : '★';
    }
}
