package app.localrp;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits long messages into TTS-sized utterances: the Android TTS engine is
 * unreliable past a few thousand characters. Cuts preferentially on sentence
 * and pause marks, then hard-wraps any single oversized sentence on a space.
 * Pure logic, no Android imports — JVM-tested.
 */
final class SpeechText {
    private SpeechText() {
    }

    /** Conservative utterance ceiling well under the engine's soft limits. */
    static final int MAX_CHUNK = 2800;

    /** One utterance per chunk; null/blank input yields an empty list. */
    static List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return chunks;
        List<String> sentences = sentences(text);
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (current.length() + sentence.length() > MAX_CHUNK && current.length() > 0) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            if (sentence.length() > MAX_CHUNK) {
                // A single huge sentence: hard-wrap on the last space before the cap.
                String rest = sentence;
                while (rest.length() > MAX_CHUNK) {
                    int cut = safeCut(rest, MAX_CHUNK);
                    if (current.length() > 0) {
                        chunks.add(current.toString().trim());
                        current.setLength(0);
                    }
                    chunks.add(rest.substring(0, cut).trim());
                    rest = rest.substring(cut).trim();
                }
                current.append(rest).append(' ');
            } else {
                current.append(sentence).append(' ');
            }
        }
        if (current.toString().trim().length() > 0) chunks.add(current.toString().trim());
        return chunks;
    }

    /** Splits into sentence-ish runs, keeping the terminator with its sentence.
     *  v0.27.00 hardening: a terminator splits only when followed by whitespace or
     *  the end — decimals ("3.14") and URLs ("a.b/c") stay in one utterance; the
     *  blank-line ("\n\n") break still cuts unconditionally. */
    private static List<String> sentences(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            run.append(c);
            boolean blankLineBreak = c == '\n' && i + 1 < text.length() && text.charAt(i + 1) == '\n';
            boolean terminator = c == '.' || c == '!' || c == '?' || c == '…' || blankLineBreak;
            boolean boundary = blankLineBreak || i + 1 >= text.length()
                    || Character.isWhitespace(text.charAt(i + 1));
            if (terminator && boundary) {
                if (run.toString().trim().length() > 0) parts.add(run.toString().trim());
                run.setLength(0);
            }
        }
        if (run.toString().trim().length() > 0) parts.add(run.toString().trim());
        return parts;
    }

    /** Hard-wrap cut point that never splits a surrogate pair (emoji) in half. */
    private static int safeCut(String rest, int cap) {
        int cut = rest.lastIndexOf(' ', cap);
        if (cut <= 0) cut = cap;
        if (cut < rest.length() && cut > 0
                && Character.isHighSurrogate(rest.charAt(cut - 1))
                && Character.isLowSurrogate(rest.charAt(cut))) {
            cut--; // back off one char so the emoji stays whole
        }
        return cut;
    }
}
