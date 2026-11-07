package app.localrp;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown-lite selection logic for roleplay text. Supports {@code **bold**} and
 * {@code *italic*} pairs; unmatched marker characters stay literal, and a bold run
 * nested inside an italic run keeps both styles. Pure logic only — the view layer
 * turns the returned segments into span ranges — so unit tests run on the JVM
 * without Android classes.
 */
final class MarkdownLite {
    private MarkdownLite() {}

    /** A styled run; segments concatenate back to the input minus pair markers. */
    static final class Segment {
        final String text;
        final boolean bold;
        final boolean italic;

        Segment(String text, boolean bold, boolean italic) {
            this.text = text;
            this.bold = bold;
            this.italic = italic;
        }
    }

    /**
     * Parses the value into styled segments. Marker characters of successful pairs
     * are dropped from the output; any unmatched marker survives literally.
     */
    static List<Segment> parse(String value) {
        List<Segment> result = new ArrayList<>();
        if (value == null) return result;
        int n = value.length();
        if (n == 0) {
            result.add(new Segment("", false, false));
            return result;
        }
        int[][] boldMarks = markers(value, "**");
        // Italic candidates live in a length-preserving copy where bold pair markers
        // are masked, so a stray "*" can never pair across a bold run's markers.
        int[][] italicMarks = markers(mask(value, boldMarks), "*");

        boolean[] bold = new boolean[n];
        boolean[] italic = new boolean[n];
        boolean[] marker = new boolean[n];
        markRange(value, bold, marker, boldMarks, 2);
        markRange(mask(value, boldMarks), italic, marker, italicMarks, 1);

        StringBuilder current = new StringBuilder();
        boolean currentBold = false;
        boolean currentItalic = false;
        boolean started = false;
        for (int i = 0; i < n; i++) {
            if (marker[i]) continue; // pair markers never appear in the output
            if (!started || bold[i] != currentBold || italic[i] != currentItalic) {
                if (started) result.add(new Segment(current.toString(), currentBold, currentItalic));
                current.setLength(0);
                currentBold = bold[i];
                currentItalic = italic[i];
                started = true;
            }
            current.append(value.charAt(i));
        }
        if (started) result.add(new Segment(current.toString(), currentBold, currentItalic));
        if (result.isEmpty()) result.add(new Segment(value, false, false));
        return result;
    }

    /** Pair bounds as {openMarkerStart, closeMarkerStart}; empty content pairs are refused. */
    private static int[] pair(String value, String marker, int open, int close) {
        // minimum "a" between the markers; markers themselves are never content
        if (open < 0 || close < 0 || close + marker.length() > value.length()) return null;
        if (close - (open + marker.length()) < 1) return null;
        return new int[]{open, close};
    }

    /** Scans left to right for non-nested, non-overlapping marker pairs. */
    private static int[][] markers(String value, String marker) {
        List<int[]> pairs = new ArrayList<>();
        int from = 0;
        while (from <= value.length() - marker.length()) {
            int open = value.indexOf(marker, from);
            if (open < 0) break;
            int close = value.indexOf(marker, open + marker.length());
            int[] bound = pair(value, marker, open, close);
            if (bound == null) break;
            pairs.add(bound);
            from = close + marker.length();
        }
        return pairs.toArray(new int[0][]);
    }

    /** Flags the text between the pair markers as styled and the marker cells as dropped. */
    private static void markRange(String value, boolean[] flag, boolean[] marker,
                                  int[][] ranges, int markerLength) {
        for (int[] range : ranges) {
            for (int i = range[0]; i < range[0] + markerLength && i < marker.length; i++) marker[i] = true;
            for (int i = range[1]; i < range[1] + markerLength && i < marker.length; i++) marker[i] = true;
            for (int i = range[0] + markerLength; i < range[1] && i < flag.length; i++) flag[i] = true;
        }
    }

    /** Copy of value with every character inside the given marker ranges replaced by '\0'. */
    private static String mask(String value, int[][] ranges) {
        if (ranges.length == 0) return value;
        char[] chars = value.toCharArray();
        for (int[] range : ranges) {
            for (int i = range[0]; i < range[1] + 2 && i < chars.length; i++) chars[i] = '\0';
        }
        return new String(chars);
    }
}
