package app.localrp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.view.Window;

/**
 * Static access to the active GUI theme. MainActivity refreshes the theme before building
 * screens so every component renders with the current palette.
 */
public final class Design {
    public static AppTheme theme = AppTheme.byId(null);

    private Design() {}

    public static void refresh(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences("localrp", Activity.MODE_PRIVATE);
        theme = AppTheme.byId(prefs.getString("ui_theme", "lavanta"));
    }

    public static AppTheme byIdOrNull(String id) {
        for (AppTheme candidate : AppTheme.ALL) if (candidate.id.equals(id)) return candidate;
        return null;
    }

    /** Applies the theme to the system bars so the chrome follows the screen palette. */
    public static void applyBars(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) return;
        int barColor = theme.pageBg;
        window.setStatusBarColor(barColor);
        window.setNavigationBarColor(theme.dark ? theme.cardBg : theme.pageBg);
        int decor = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= 30) {
            window.setStatusBarColor(barColor);
        }
        if (!theme.dark) {
            decor |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(decor);
    }

    public static int text() { return theme.text; }
    public static int muted() { return theme.muted; }
    public static int pageBg() { return theme.pageBg; }
    public static int cardBg() { return theme.cardBg; }
    public static int cardStroke() { return theme.cardStroke; }
    public static int divider() { return theme.divider; }
    public static int accent() { return theme.accent; }
    public static int accentSoft() { return theme.accentSoft; }
    public static int accentStroke() { return theme.accentStroke; }
    public static int onAccent() { return theme.onAccent; }
    public static int danger() { return theme.danger; }
    public static int dangerSoft() { return theme.dangerSoft; }
    public static int dangerStroke() { return theme.dangerStroke; }
    public static int bubbleUser() { return theme.bubbleUser; }
    public static int bubbleUserStroke() { return theme.bubbleUserStroke; }
    public static int heroA() { return theme.heroA; }
    public static int heroB() { return theme.heroB; }
    public static int heroText() { return theme.heroText; }
    public static int heroSub() { return theme.heroSub; }
}
