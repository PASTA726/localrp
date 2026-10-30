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
                ColorUtils.argb(0xFF17171C), ColorUtils.argb(0xFF6E6E78),
                ColorUtils.argb(0xFFF7F7F9), ColorUtils.argb(0xFFFFFFFF), ColorUtils.argb(0xFFE9E9EF), ColorUtils.argb(0xFFE9E9EF),
                ColorUtils.argb(0xFF7C5CFC), ColorUtils.argb(0xFFF1EDFF), ColorUtils.argb(0xFFDDD4FF), ColorUtils.argb(0xFFFFFFFF),
                ColorUtils.argb(0xFFE5484D), ColorUtils.argb(0xFFFEECED), ColorUtils.argb(0xFFF6D5D7),
                ColorUtils.argb(0xFFF1EDFF), ColorUtils.argb(0xFFE0D7FF),
                ColorUtils.argb(0xFF5B3FE0), ColorUtils.argb(0xFF9B7BFF), ColorUtils.argb(0xFFFFFFFF), ColorUtils.argb(0xFFE6DEFF),
                new int[]{ColorUtils.argb(0xFF7C5CFC), ColorUtils.argb(0xFFF1EDFF)}),
        new AppTheme("okyanus", "Okyanus", false,
                ColorUtils.argb(0xFF101A20), ColorUtils.argb(0xFF5E707B),
                ColorUtils.argb(0xFFF6FAFB), ColorUtils.argb(0xFFFFFFFF), ColorUtils.argb(0xFFE3ECEF), ColorUtils.argb(0xFFE3ECEF),
                ColorUtils.argb(0xFF12A5B7), ColorUtils.argb(0xFFE8F7F9), ColorUtils.argb(0xFFCFE9EC), ColorUtils.argb(0xFFFFFFFF),
                ColorUtils.argb(0xFFE5484D), ColorUtils.argb(0xFFFEECED), ColorUtils.argb(0xFFF6D5D7),
                ColorUtils.argb(0xFFE7F6F8), ColorUtils.argb(0xFFCFE7EA),
                ColorUtils.argb(0xFF06333C), ColorUtils.argb(0xFF0CA2B6), ColorUtils.argb(0xFFFFFFFF), ColorUtils.argb(0xFFBCEFF4),
                new int[]{ColorUtils.argb(0xFF12A5B7), ColorUtils.argb(0xFFE8F7F9)}),
        new AppTheme("orman", "Orman", false,
                ColorUtils.argb(0xFF141C15), ColorUtils.argb(0xFF61755F),
                ColorUtils.argb(0xFFF6F8F5), ColorUtils.argb(0xFFFFFFFF), ColorUtils.argb(0xFFE5EBE3), ColorUtils.argb(0xFFE5EBE3),
                ColorUtils.argb(0xFF3E9B5F), ColorUtils.argb(0xFFEBF5ED), ColorUtils.argb(0xFFCEE8D6), ColorUtils.argb(0xFFFFFFFF),
                ColorUtils.argb(0xFFE5484D), ColorUtils.argb(0xFFFEECED), ColorUtils.argb(0xFFF6D5D7),
                ColorUtils.argb(0xFFECF5EC), ColorUtils.argb(0xFFD3E8D7),
                ColorUtils.argb(0xFF12301C), ColorUtils.argb(0xFF3E9B5F), ColorUtils.argb(0xFFFFFFFF), ColorUtils.argb(0xFFC4F0D2),
                new int[]{ColorUtils.argb(0xFF3E9B5F), ColorUtils.argb(0xFFEBF5ED)}),
        new AppTheme("golge", "Gölge", true,
                ColorUtils.argb(0xFFECECF2), ColorUtils.argb(0xFF8F92A3),
                ColorUtils.argb(0xFF0E0F13), ColorUtils.argb(0xFF181B22), ColorUtils.argb(0xFF262A33), ColorUtils.argb(0xFF21242C),
                ColorUtils.argb(0xFF9B7BFF), ColorUtils.argb(0xFF241F3A), ColorUtils.argb(0xFF3E3468), ColorUtils.argb(0xFFFFFFFF),
                ColorUtils.argb(0xFFFF6B78), ColorUtils.argb(0xFF3A1E24), ColorUtils.argb(0xFF5C2F38),
                ColorUtils.argb(0xFF241F3A), ColorUtils.argb(0xFF3E3468),
                ColorUtils.argb(0xFF12081F), ColorUtils.argb(0xFF4A2ECB), ColorUtils.argb(0xFFF3EEFF), ColorUtils.argb(0xFFC9B8FF),
                new int[]{ColorUtils.argb(0xFF9B7BFF), ColorUtils.argb(0xFF241F3A)}),
        new AppTheme("deniz-gecesi", "Deniz Gecesi", true,
                ColorUtils.argb(0xFFE4EFEC), ColorUtils.argb(0xFF8DA5A1),
                ColorUtils.argb(0xFF0B1417), ColorUtils.argb(0xFF141F24), ColorUtils.argb(0xFF223237), ColorUtils.argb(0xFF1C2A2E),
                ColorUtils.argb(0xFF46C8BC), ColorUtils.argb(0xFF0F2B28), ColorUtils.argb(0xFF23544E), ColorUtils.argb(0xFF04110F),
                ColorUtils.argb(0xFFFF6B78), ColorUtils.argb(0xFF33171C), ColorUtils.argb(0xFF55262C),
                ColorUtils.argb(0xFF102824), ColorUtils.argb(0xFF23544E),
                ColorUtils.argb(0xFF03211F), ColorUtils.argb(0xFF178278), ColorUtils.argb(0xFFE0F7F3), ColorUtils.argb(0xFFA5E5DC),
                new int[]{ColorUtils.argb(0xFF46C8BC), ColorUtils.argb(0xFF0F2B28)}),
        new AppTheme("gun-batimi", "Gün Batımı", true,
                ColorUtils.argb(0xFFF2EAE2), ColorUtils.argb(0xFFB4A19A),
                ColorUtils.argb(0xFF121011), ColorUtils.argb(0xFF1B171A), ColorUtils.argb(0xFF2B2226), ColorUtils.argb(0xFF26201F),
                ColorUtils.argb(0xFFFF9E57), ColorUtils.argb(0xFF33211A), ColorUtils.argb(0xFF5C3A26), ColorUtils.argb(0xff1A0E08),
                ColorUtils.argb(0xFFFF6B78), ColorUtils.argb(0xFF331716), ColorUtils.argb(0xFF55262A),
                ColorUtils.argb(0xFF291912), ColorUtils.argb(0xFF54371F),
                ColorUtils.argb(0xFF230D05), ColorUtils.argb(0xFFC2650F), ColorUtils.argb(0xFFFFEEDA), ColorUtils.argb(0xFFF0BE8C),
                new int[]{ColorUtils.argb(0xFFFF9E57), ColorUtils.argb(0xFF33211A)}),
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
