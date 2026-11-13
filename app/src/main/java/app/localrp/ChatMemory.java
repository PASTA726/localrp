package app.localrp;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Advanced per-conversation memory (v0.23.00, "Gelişmiş hafıza").
 *
 * Three pieces, all offline and deterministic:
 * <ul>
 *   <li><b>Important events</b> — the user pins messages ("Hafızaya ekle");
 *       stored per conversation as a plain string list (like the lorebook).</li>
 *   <li><b>Relevant recall</b> — keyword-overlap scoring picks the pinned
 *       memories that matter for the current user message.</li>
 *   <li><b>Automatic recap</b> — an extractive summary of the transcript that
 *       falls outside the prompt's history window, so deep conversations keep
 *       a compact sense of earlier events without a model round-trip.</li>
 * </ul>
 * Pure logic, no Android imports — JVM-tested.
 */
final class ChatMemory {
    private ChatMemory() {
    }

    static final int MAX_ENTRIES = 40;
    static final int MAX_ENTRY_CHARS = 600;
    static final int RECALL_MAX = 5;
    static final int RECALL_CHAR_BUDGET = 700;
    static final int RECAP_LINE_MAX = 140;
    static final int RECAP_CHAR_BUDGET = 900;

    // ---- Important events ----

    /** Appends a pinned event, trimming to caps; dedupes byte-identical entries. */
    static List<String> add(List<String> memories, String text) {
        List<String> out = memories == null ? new ArrayList<>() : new ArrayList<>(memories);
        String clean = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) return out;
        if (clean.length() > MAX_ENTRY_CHARS) clean = clean.substring(0, MAX_ENTRY_CHARS - 1) + "…";
        for (String existing : out) {
            if (clean.equals(existing)) return out; // no duplicates
        }
        out.add(clean);
        while (out.size() > MAX_ENTRIES) out.remove(0); // oldest fall off first
        return out;
    }

    /** JSON round-trip used by the store ({@code memory_<chatId>} pref key). */
    static String encode(List<String> memories) {
        return new JSONArray(memories == null
                ? new ArrayList<String>() : memories).toString();
    }

    /** Decodes stored JSON; unreadable payloads become an empty list, never a crash. */
    static List<String> decode(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length() && i < MAX_ENTRIES; i++) {
                String text = array.optString(i, "");
                if (!text.isEmpty()) out.add(text);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---- Relevant recall ----

    /**
     * Picks the pinned memories most relevant to {@code currentText} by word
     * overlap. Ties keep stored order (earlier pin = stronger position). Result
     * respects both the entry cap and a character budget.
     */
    static List<String> relevant(List<String> memories, String currentText) {
        List<String> out = new ArrayList<>();
        if (memories == null || memories.isEmpty()) return out;
        Set<String> query = words(currentText);
        if (query.isEmpty()) return out;
        List<String> scored = new ArrayList<>();
        int bestScore = 0;
        for (String memory : memories) {
            Set<String> memoryWords = words(memory);
            memoryWords.retainAll(query);
            int score = memoryWords.size();
            if (score > bestScore) bestScore = score;
            if (score > 0) scored.add(memory);
        }
        // Only memories that actually share words qualify; keep stored order.
        if (scored.isEmpty() || bestScore <= 0) return out;
        int budget = RECALL_CHAR_BUDGET;
        for (String memory : scored) {
            if (out.size() >= RECALL_MAX || budget <= 20) break;
            if (memory.length() <= budget) {
                out.add(memory);
                budget -= memory.length() + 1;
            }
        }
        return out;
    }

    /** Lowercase word tokens (≥3 chars), non-alphanumeric separators. */
    private static Set<String> words(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+")) {
            if (token.length() >= 3) out.add(token);
        }
        out.removeAll(STOPWORDS);
        return out;
    }

    private static final Set<String> STOPWORDS = new HashSet<>(java.util.Arrays.asList(
            "the", "and", "was", "her", "him", "she", "his", "that", "this", "with",
            "bir", "ile", "için", "gibi", "ama", "çok", "daha", "sonra", "olarak", "kadar"));

    // ---- Automatic recap ----

    /**
     * Extractive recap of the transcript beyond the prompt's recent window:
     * one compact line per older message ("speaker: first sentence"), capped by
     * budget. Empty when every message still fits the window.
     *
     * @param recentCount how many newest messages the prompt window already carries
     */
    static String recap(List<Message> history, int recentCount, int charBudget) {
        if (history == null || history.isEmpty() || recentCount < 0) return "";
        int olderCount = history.size() - recentCount;
        if (olderCount <= 0) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < olderCount; i++) {
            Message message = history.get(i);
            String body = message.text == null || message.text.isEmpty()
                    ? (message.hasImage() ? "[görsel]" : "")
                    : message.text;
            if (body.isEmpty()) continue;
            String who = message.user ? "Kullanıcı" : message.speaker;
            String line = oneLine(who + ": " + body, RECAP_LINE_MAX);
            if (out.length() + line.length() + 1 > charBudget) break;
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        return out.toString();
    }

    private static String oneLine(String value, int max) {
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max - 1)) + "…";
    }
}
