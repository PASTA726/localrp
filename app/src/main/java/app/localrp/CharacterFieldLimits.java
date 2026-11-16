package app.localrp;

/**
 * v0.35.00 (audit 1): the single source of truth for character-card field
 * limits. {@link RoleplayPrompt} clips these fields when building the prompt;
 * the character editor shows live counters against the same constants, so a
 * card can never be silently truncated without the user seeing it.
 * Pure logic, no Android imports — JVM-tested.
 */
final class CharacterFieldLimits {
    private CharacterFieldLimits() {}

    /** Character display name (prompt clip + cast lists). */
    static final int NAME = 80;

    /**
     * Codex finding (v0.35.00): other characters' names inside the shared-cast
     * line clip tighter (a 280-char budget covers the whole list). The name
     * counter advertises {@link #NAME} (the speaker clip); this constant keeps
     * the cast path from hiding a second literal.
     */
    static final int CAST_NAME = 60;

    /** Character description ("Kısa tanım"). */
    static final int DESCRIPTION = 700;

    /** Personality and speech style. */
    static final int PERSONALITY = 900;

    /** Character instructions. */
    static final int INSTRUCTIONS = 900;

    /** Example dialogue. */
    static final int EXAMPLES = 1200;

    /** Persona identity limits (RoleplayPrompt persona block). */
    static final int PERSONA_NAME = 80;
    static final int PERSONA_DESCRIPTION = 400;

    /** True when the text exceeds the prompt clip limit (null-safe). */
    static boolean over(int limit, String text) {
        return text != null && text.length() > limit;
    }

    /** Language-neutral "n/limit" counter body; the label text wraps it. */
    static String counter(int limit, String text) {
        return (text == null ? 0 : text.length()) + "/" + limit;
    }
}
