package app.localrp;

/** Hand-built color system for the app-wide GUI themes (light + dark, several accents). */
public final class AppTheme {
    public final String id;
    public final String name;
    public final boolean dark;

    public final int text, muted;
    public final int pageBg, cardBg, cardStroke, divider;
    public final int accent, accentSoft, accentStroke, onAccent;
    public final int danger, dangerSoft, dangerStroke;
    public final int bubbleUser, bubbleUserStroke;
    public final int heroA, heroB, heroText, heroSub;
    public final int[] swatch;

    public AppTheme(String id, String name, boolean dark,
                    int text, int muted,
                    int pageBg, int cardBg, int cardStroke, int divider,
                    int accent, int accentSoft, int accentStroke, int onAccent,
                    int danger, int dangerSoft, int dangerStroke,
                    int bubbleUser, int bubbleUserStroke,
                    int heroA, int heroB, int heroText, int heroSub,
                    int[] swatch) {
        this.id = id;
        this.name = name;
        this.dark = dark;
        this.text = text;
        this.muted = muted;
        this.pageBg = pageBg;
        this.cardBg = cardBg;
        this.cardStroke = cardStroke;
        this.divider = divider;
        this.accent = accent;
        this.accentSoft = accentSoft;
        this.accentStroke = accentStroke;
        this.onAccent = onAccent;
        this.danger = danger;
        this.dangerSoft = dangerSoft;
        this.dangerStroke = dangerStroke;
        this.bubbleUser = bubbleUser;
        this.bubbleUserStroke = bubbleUserStroke;
        this.heroA = heroA;
        this.heroB = heroB;
        this.heroText = heroText;
        this.heroSub = heroSub;
        this.swatch = swatch;
    }

    public static final AppTheme[] ALL = {
        new AppTheme("lavanta", "Lavanta", false,
                ColorUtils.argb(0xFF1B1A23), ColorUtils.argb(0xFF6F6C7A),
                ColorUtils.argb(0xFFF8F7FB), ColorUtils.argb(0xFFFFFFFF), ColorUtils.argb(0xFFEDEAF2), ColorUtils.argb(0xFFEDEAF2),
                ColorUtils.argb(0xFF694DD6), ColorUtils.argb(0xFFF4F1FC), ColorUtils.argb(0xFFDEDEF6), ColorUtils.argb(0xFFFFFFFF),
                ColorUtils.argb(0xFFB4323F), ColorUtils.argb(0xFFFBEDEE), ColorUtils.argb(0xFFEED5D8),
                ColorUtils.argb(0xFFEBE5FF), ColorUtils.argb(0xFFD8CFED),
                ColorUtils.argb(0xFF2F2258), ColorUtils.argb(0xFF684CBE), ColorUtils.argb(0xFFFFFFFF), ColorUtils.argb(0xFFDACFFF),
                new int[]{ColorUtils.argb(0xFF694DD6), ColorUtils.argb(0xFFF4F1FC)}),
        new AppTheme("okyanus", "Okyanus", false,
                ColorUtils.argb(0xFF11212B), ColorUtils.argb(0xFF60707F),
                ColorUtils.argb(0xFFF4F9FA), ColorUtils.argb(0xFFFFFFFF), ColorUtils.argb(0xFFE1EBEE), ColorUtils.argb(0xFFE1EBEE),
                ColorUtils.argb(0xFF0E8A9B), ColorUtils.argb(0xFFE7F4F6), ColorUtils.argb(0xFFCBE4E8), ColorUtils.argb(0xFFFFFFFF),
                ColorUtils.argb(0xFFB4323F), ColorUtils.argb(0xFFFBEDEE), ColorUtils.argb(0xFFEED5D8),
                ColorUtils.argb(0xFFDFF0F2), ColorUtils.argb(0xFFC4DDE1),
                ColorUtils.argb(0xFF083744), ColorUtils.argb(0xFF0E97AA), ColorUtils.argb(0xFFFFFFFF), ColorUtils.argb(0xFFB7ECF3),
                new int[]{ColorUtils.argb(0xFF0E8A9B), ColorUtils.argb(0xFFE7F4F6)}),
        new AppTheme("orman", "Orman", false,
                ColorUtils.argb(0xFF172318), ColorUtils.argb(0xFF647A66),
                ColorUtils.argb(0xFFF4F8F4), ColorUtils.argb(0xFFFFFFFF), ColorUtils.argb(0xFFE2EBE2), ColorUtils.argb(0xFFE2EBE2),
                ColorUtils.argb(0xFF2E8049), ColorUtils.argb(0xFFE9F4EB), ColorUtils.argb(0xFFCDE7D3), ColorUtils.argb(0xFFFFFFFF),
                ColorUtils.argb(0xFFB4323F), ColorUtils.argb(0xFFFBEDEE), ColorUtils.argb(0xFFEED5D8),
                ColorUtils.argb(0xFFDFEFE2), ColorUtils.argb(0xFFC8E4CE),
                ColorUtils.argb(0xFF12331E), ColorUtils.argb(0xFF359F58), ColorUtils.argb(0xFFFFFFFF), ColorUtils.argb(0xFFB9F0C9),
                new int[]{ColorUtils.argb(0xFF2E8049), ColorUtils.argb(0xFFE9F4EB)}),
        new AppTheme("golge", "Gölge", true,
                ColorUtils.argb(0xFFEBEAF4), ColorUtils.argb(0xFFA3A1B0),
                ColorUtils.argb(0xFF13121A), ColorUtils.argb(0xFF1E1D28), ColorUtils.argb(0xFF2B2A38), ColorUtils.argb(0xFF2B2A38),
                ColorUtils.argb(0xFFA78BFA), ColorUtils.argb(0xFF2E2949), ColorUtils.argb(0xFF544487), ColorUtils.argb(0xFF13121A),
                ColorUtils.argb(0xFFE86A77), ColorUtils.argb(0xFF31202A), ColorUtils.argb(0xFF63343C),
                ColorUtils.argb(0xFF342C58), ColorUtils.argb(0xFF594885),
                ColorUtils.argb(0xFF16102C), ColorUtils.argb(0xFF4C3A94), ColorUtils.argb(0xFFEDE6FF), ColorUtils.argb(0xFFB9A7F0),
                new int[]{ColorUtils.argb(0xFFA78BFA), ColorUtils.argb(0xFF2E2949)}),
        new AppTheme("deniz-gecesi", "Deniz Gecesi", true,
                ColorUtils.argb(0xFFE7F0EE), ColorUtils.argb(0xFF9BB0AE),
                ColorUtils.argb(0xFF0E1619), ColorUtils.argb(0xFF182329), ColorUtils.argb(0xFF283A40), ColorUtils.argb(0xFF283A40),
                ColorUtils.argb(0xFF3BBFB4), ColorUtils.argb(0xFF13342F), ColorUtils.argb(0xFF2A5D56), ColorUtils.argb(0xFF081B18),
                ColorUtils.argb(0xFFE86A77), ColorUtils.argb(0xFF2E2024), ColorUtils.argb(0xFF5E3A42),
                ColorUtils.argb(0xFF1C3934), ColorUtils.argb(0xFF2F5E58),
                ColorUtils.argb(0xFF062726), ColorUtils.argb(0xFF16776F), ColorUtils.argb(0xFFDDF6F2), ColorUtils.argb(0xFFA8E5DC),
                new int[]{ColorUtils.argb(0xFF3BBFB4), ColorUtils.argb(0xFF13342F)}),
        new AppTheme("gun-batimi", "Gün Batımı", true,
                ColorUtils.argb(0xFFF4EEE8), ColorUtils.argb(0xFFBEA9A0),
                ColorUtils.argb(0xFF191210), ColorUtils.argb(0xFF261C18), ColorUtils.argb(0xFF3E2D26), ColorUtils.argb(0xFF3E2D26),
                ColorUtils.argb(0xFFFB923C), ColorUtils.argb(0xFF3B271B), ColorUtils.argb(0xFF7A5436), ColorUtils.argb(0xFF211510),
                ColorUtils.argb(0xFFE86A77), ColorUtils.argb(0xFF2E2024), ColorUtils.argb(0xFF603A40),
                ColorUtils.argb(0xFF3E2A1B), ColorUtils.argb(0xFF6E4B2C),
                ColorUtils.argb(0xFF2C1206), ColorUtils.argb(0xFFB45A18), ColorUtils.argb(0xFFFFEBD9), ColorUtils.argb(0xFFE5B183),
                new int[]{ColorUtils.argb(0xFFFB923C), ColorUtils.argb(0xFF3B271B)}),
    };

    public static AppTheme byId(String id) {
        if (id != null) for (AppTheme theme : ALL) if (theme.id.equals(id)) return theme;
        return ALL[0];
    }

    private static final class ColorUtils {
        static int argb(int argb) {
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            return android.graphics.Color.rgb(r, g, b);
        }
    }
}
