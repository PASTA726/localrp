package app.localrp;

import java.util.ArrayList;
import java.util.List;

/**
 * Web araması (v0.29.00): DuckDuckGo lite HTML sonucunu saf Java ile ayrıştırır —
 * Android framework çağrısı yok, JVM'de test edilir. Ağ kısmı MainActivity'de
 * (HttpsURLConnection + 10 sn zaman aşımı); burada yalnızca HTML → sonuç kartları.
 * Sonuçlar tek seferlik bağlam enjeksiyonu için sınırlanır (≤5 başlık+özet).
 */
final class WebSearch {
    private WebSearch() {}

    /** One parsed result: title, snippet, url. */
    static final class Result {
        final String title;
        final String snippet;
        final String url;

        Result(String title, String snippet, String url) {
            this.title = title;
            this.snippet = snippet;
            this.url = url;
        }
    }

    static final int MAX_RESULTS = 5;
    /** Per-result snippet ceiling (chars). */
    static final int SNIPPET_CHARS = 200;
    /** Whole injected block ceiling (chars) — one-shot context must stay cheap. */
    static final int BLOCK_CHAR_BUDGET = 800;

    /** Extracts result links from a DDG lite results page. */
    static List<Result> parse(String html) {
        List<Result> results = new ArrayList<>();
        if (html == null || html.isEmpty()) return results;
        // DDG lite marks organic results with the class "result-link"; the snippet
        // sits in the following "result-snippet" cell. Parse pairwise.
        int index = 0;
        while (results.size() < MAX_RESULTS) {
            int linkStart = html.indexOf("class=\"result-link\"", index);
            if (linkStart < 0) break;
            int href = html.indexOf("href=\"", linkStart);
            if (href < 0) break;
            int hrefEnd = html.indexOf('"', href + 6);
            if (hrefEnd < 0) break;
            String url = decodeAmp(html.substring(href + 6, hrefEnd));
            int titleStart = html.indexOf('>', hrefEnd) + 1;
            int titleEnd = html.indexOf("</a>", titleStart);
            if (titleEnd < 0) break;
            String title = stripTags(html.substring(titleStart, Math.min(titleEnd, titleStart + 300)));
            // Snippet: the next result-snippet cell after this link (before the next link).
            int nextLink = html.indexOf("class=\"result-link\"", hrefEnd);
            int snippetTag = html.indexOf("class=\"result-snippet\"", hrefEnd);
            String snippet = "";
            if (snippetTag >= 0 && snippetTag < (nextLink < 0 ? html.length() : nextLink)) {
                int snipStart = html.indexOf('>', snippetTag) + 1;
                int snipEnd = html.indexOf("</td>", snipStart);
                if (snipEnd < 0) snipEnd = Math.min(snipStart + 400, html.length());
                snippet = localClip(stripTags(html.substring(snipStart, Math.min(snipEnd, snipStart + 400))), SNIPPET_CHARS);
            }
            if (!title.isEmpty() && !url.isEmpty() && url.startsWith("http")) {
                // DDG wraps external links through /l/?uddg=<encoded>; unwrap when present.
                int uddg = url.indexOf("uddg=");
                if (uddg >= 0) {
                    String unwrapped = decodeAmp(url.substring(uddg + 5));
                    int amp = unwrapped.indexOf('&');
                    if (amp >= 0) unwrapped = unwrapped.substring(0, amp);
                    url = urlDecode(unwrapped);
                }
                results.add(new Result(title, snippet, url));
            }
            index = hrefEnd;
        }
        return results;
    }

    /**
     * Builds the one-shot context block injected like a steering note: consumed on
     * the very next turn only. Empty input yields "" (nothing is injected).
     */
    static String contextBlock(List<Result> results) {
        if (results == null || results.isEmpty()) return "";
        StringBuilder block = new StringBuilder();
        for (Result result : results) {
            if (block.length() > 0) block.append("\n");
            String line = "- " + result.title
                    + (result.snippet.isEmpty() ? "" : ": " + result.snippet)
                    + " (" + result.url + ")";
            if (block.length() + line.length() > BLOCK_CHAR_BUDGET) break;
            block.append(line);
        }
        if (block.length() == 0) return "";
        return block.toString();
    }

    /** Local char clip: pure, no RoleplayPrompt dependency. */
    private static String localClip(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** Very small HTML text cleanup: tags off, entities decoded, whitespace folded. */
    static String stripTags(String html) {
        if (html == null) return "";
        String text = html.replaceAll("(?s)<[^>]*>", " ");
        text = decodeAmp(text);
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    /** Decodes the handful of entities DDG lite actually emits. */
    static String decodeAmp(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }

    /** Minimal %XX decoder for DDG's /l/?uddg= wrappers (UTF-8). */
    static String urlDecode(String text) {
        if (text == null || text.indexOf('%') < 0 && text.indexOf('+') < 0) return text;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '%' && i + 2 < text.length()) {
                int hi = Character.digit(text.charAt(++i), 16);
                int lo = Character.digit(text.charAt(++i), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) | lo);
                    continue;
                }
                out.write('%');
            }
            out.write(c == '+' ? ' ' : c);
        }
        return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
