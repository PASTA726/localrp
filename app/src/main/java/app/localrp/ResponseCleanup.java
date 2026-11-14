package app.localrp;

import java.util.regex.Pattern;

/**
 * Pure response cleanup, extracted verbatim from MainActivity (task G0): the exact
 * same helper is used during streaming renders and for the final stored message, so
 * the model's echoed speaker label ("<Ad>: ") and special-token residue are never
 * shown in an interim balloon either. No Android framework calls.
 *
 * v0.30.00 optimization: {@link #clean} runs on EVERY streaming chunk, but the old
 * code compiled the speaker-label regex and the blank-line pattern on each call.
 * Both are now cached (label regex per speaker, bounded; collapse as a constant).
 */
final class ResponseCleanup {
    private ResponseCleanup() {}

    /** One compiled instance, reused across every call (was recompiled per chunk). */
    private static final Pattern BLANK_LINE_RUNS = Pattern.compile("\n{3,}");

    /** Per-speaker compiled label regexes; bounded so a long session cannot grow it. */
    private static final java.util.Map<String, Pattern> LABEL_PATTERNS = new java.util.HashMap<>();
    private static final int LABEL_CACHE_MAX = 8;

    private static Pattern labelPattern(String speakerName) {
        synchronized (LABEL_PATTERNS) {
            Pattern pattern = LABEL_PATTERNS.get(speakerName);
            if (pattern != null) return pattern;
            if (LABEL_PATTERNS.size() >= LABEL_CACHE_MAX) LABEL_PATTERNS.clear();
            // Optional delimiter: "Name:", "Name-", "Name", and asterisk-wrapped forms
            // ("*Name*: x", "*Name* x") all count as an echoed label (task G0).
            pattern = Pattern.compile("(?is)^\\s*\\*{0,2}" + Pattern.quote(speakerName) + "\\*{0,2}([:：-]\\s*)?");
            LABEL_PATTERNS.put(speakerName, pattern);
            return pattern;
        }
    }

    static String clean(String raw, String speakerName) {
        if (raw == null) return "";
        String text = raw.trim();
        text = text.replace("<|im_end|>", "").replace("<|eot_id|>", "")
                .replace("<|end_of_text|>", "").replace("</s>", "").trim();
        // Think yeteneği (v0.28.00): reasoning models emit <think>…</think> blocks in the
        // visible reply. Closed blocks are removed wherever they appear; an unclosed
        // <think> (stream cut mid-thought) hides everything from it on.
        int open = text.indexOf("<think>");
        while (open >= 0) {
            int close = text.indexOf("</think>", open);
            if (close < 0) {
                text = text.substring(0, open);
                break;
            }
            text = text.substring(0, open) + text.substring(close + "</think>".length());
            open = text.indexOf("<think>");
        }
        text = text.trim();
        if (!text.isEmpty() && speakerName != null && !speakerName.trim().isEmpty()) {
            text = labelPattern(speakerName.trim()).matcher(text).replaceFirst("");
        }
        text = BLANK_LINE_RUNS.matcher(text).replaceAll("\n\n");
        return text.trim();
    }
}
