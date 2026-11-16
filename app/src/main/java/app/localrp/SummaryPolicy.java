package app.localrp;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto summary memory (v0.33.00): messages that fall out of the prompt's history
 * window are pinned into the conversation's memory as one compact extractive
 * entry, so deep conversations keep a permanent record of earlier events even
 * after the per-turn recap budget can no longer cover them. Pure logic, no
 * Android imports — JVM-tested.
 *
 * The policy is conservative on purpose: a new entry is only produced once at
 * least {@link #CHUNK_MIN} unsummarized messages sit outside the window, which
 * keeps the memory list from filling with near-duplicate per-turn entries
 * (MAX_ENTRIES is 40 and manual pins share the cap).
 */
final class SummaryPolicy {
    private SummaryPolicy() {}

    /** Minimum out-of-window messages before a new auto entry is worth writing. */
    static final int CHUNK_MIN = 4;

    /** Longest single extractive line inside an entry. */
    static final int LINE_MAX = 160;

    /** Total entry budget, including the "🔁 Olay özeti: " prefix. */
    static final int ENTRY_CHAR_BUDGET = 520;

    /**
     * v0.34.00 (audit 20): shared marker for auto-summary entries — manual memory
     * pins are anything that does NOT start with this, and ChatMemory evicts
     * auto entries first so a machine summary can never push out a user pin.
     */
    static final String AUTO_PREFIX = "🔁 Olay özeti: ";

    private static final String PREFIX = AUTO_PREFIX;

    /**
     * The messages that left the history window since the last auto-summary:
     * indices {@code [summarizedCount, history.size() - recentCount)}. Returns an
     * empty list when fewer than {@link #CHUNK_MIN} unsummarized messages wait,
     * or when the counts make the slice empty (clamped, never out of bounds —
     * message deletions shift indexes, so the count is a best-effort cursor).
     *
     * @param summarizedCount how many oldest messages an earlier auto entry covered
     * @param recentCount     how many newest messages the prompt window carries
     */
    static List<Message> pendingChunk(List<Message> history, int summarizedCount, int recentCount) {
        List<Message> out = new ArrayList<>();
        if (history == null || history.isEmpty() || recentCount < 0) return out;
        int olderCount = history.size() - Math.max(recentCount, 0);
        int start = Math.max(0, summarizedCount);
        if (olderCount - start < CHUNK_MIN) return out;
        for (int i = start; i < olderCount && i < history.size(); i++) {
            out.add(history.get(i));
        }
        return out;
    }

    /**
     * One compact entry for the chunk: "🔁 Olay özeti: Kullanıcı: … | Elif: …".
     * Image-only messages contribute the same "[görsel]" placeholder the prompt
     * transcript uses; a chunk that produces no text yields an empty string.
     */
    static String entry(List<Message> chunk) {
        if (chunk == null || chunk.isEmpty()) return "";
        StringBuilder out = new StringBuilder(PREFIX);
        for (Message message : chunk) {
            String body = message.text == null || message.text.isEmpty()
                    ? (message.hasImage() ? "[görsel]" : "")
                    : message.text;
            if (body.isEmpty()) continue;
            String who = message.user ? "Kullanıcı" : message.speaker;
            String line = oneLine(who + ": " + body, LINE_MAX);
            if (out.length() + line.length() + 3 > ENTRY_CHAR_BUDGET) break;
            if (out.length() > PREFIX.length()) out.append(" | ");
            out.append(line);
        }
        return out.length() <= PREFIX.length() ? "" : out.toString();
    }

    private static String oneLine(String value, int max) {
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max - 1)) + "…";
    }
}
