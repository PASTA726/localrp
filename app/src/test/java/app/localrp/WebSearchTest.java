package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Web araması (v0.29.00): saf DDG lite HTML ayrıştırma + bağlam bloğu kuralları. */
public class WebSearchTest {

    private static String page(String firstHref) {
        // DDG lite structure: rows with result-link (href + title) then result-snippet.
        return "<html><table>"
                + "<tr><td><a class=\"result-link\" href=\"" + firstHref
                + "\">Rüzgâr Gemisi <b>hikâyesi</b></a></td></tr>"
                + "<tr><td class=\"result-snippet\">Geminin &amp; ilk yolculuğu; fırtına gecesi. &nbsp;Devam…</td></tr>"
                + "<tr><td><a class=\"result-link\" href=\"https://direct.org/page\">Direct link</a></td></tr>"
                + "<tr><td class=\"result-snippet\">Kısa özet.</td></tr>"
                + "</table></html>";
    }

    @Test
    public void parse_extractsTitleSnippetUrl_fromDdgLitePage() {
        List<WebSearch.Result> results = WebSearch.parse(
                page("https://lite.duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fwind1%3Futm%3Dx"));
        assertEquals(2, results.size());
        assertEquals("Rüzgâr Gemisi hikâyesi", results.get(0).title);
        assertEquals("https://example.com/wind1?utm=x", results.get(0).url);
        assertEquals("Geminin & ilk yolculuğu; fırtına gecesi. Devam…", results.get(0).snippet);
        assertEquals("Direct link", results.get(1).title);
        assertEquals("https://direct.org/page", results.get(1).url);
        assertEquals("Kısa özet.", results.get(1).snippet);
    }

    @Test
    public void parse_capsAtFiveResults() {
        StringBuilder page = new StringBuilder("<html><table>");
        for (int i = 0; i < 8; i++) {
            page.append("<tr><td><a class=\"result-link\" href=\"https://e.org/").append(i)
                    .append("\">Başlık ").append(i).append("</a></td></tr>");
        }
        page.append("</table></html>");
        List<WebSearch.Result> results = WebSearch.parse(page.toString());
        assertEquals(5, results.size());
    }

    @Test
    public void parse_skipsNonHttpAndEmptyAndHandlesGarbage() {
        // javascript:/about links are not results; no snippet rows → empty snippets.
        String page = "<tr><td><a class=\"result-link\" href=\"javascript:void(0)\">JS</a></td></tr>"
                + "<tr><td><a class=\"result-link\" href=\"https://ok.org/x\">OK</a></td></tr>";
        List<WebSearch.Result> results = WebSearch.parse(page);
        assertEquals(1, results.size());
        assertEquals("https://ok.org/x", results.get(0).url);
        // Garbage/empty/null pages → empty list, never a crash.
        assertEquals(0, WebSearch.parse("").size());
        assertEquals(0, WebSearch.parse("<html>düz metin</html>").size());
        assertEquals(0, WebSearch.parse(null).size());
    }

    @Test
    public void parse_snippetClippedAndNextLinkBoundaryRespected() {
        StringBuilder snippet = new StringBuilder();
        for (int i = 0; i < 100; i++) snippet.append("uzun ");
        String page = "<tr><td><a class=\"result-link\" href=\"https://a.org/1\">A</a></td></tr>"
                + "<tr><td class=\"result-snippet\">" + snippet + "</td></tr>"
                + "<tr><td><a class=\"result-link\" href=\"https://b.org/2\">B</a></td></tr>"
                + "<tr><td class=\"result-snippet\">ikinci</td></tr>";
        List<WebSearch.Result> results = WebSearch.parse(page);
        assertEquals(2, results.size());
        assertEquals(WebSearch.SNIPPET_CHARS, results.get(0).snippet.length());
        assertEquals("ikinci", results.get(1).snippet);
    }

    @Test
    public void contextBlock_formatsNumberedLinesAndCapsBudget() {
        List<WebSearch.Result> results = WebSearch.parse(page("https://example.com/wind1?utm=x"));
        String block = WebSearch.contextBlock(results);
        assertTrue(block.startsWith("- "));
        assertTrue(block.contains("https://example.com/wind1"));
        // Over-budget results are dropped, never an oversized block.
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            huge.append("<tr><td><a class=\"result-link\" href=\"https://e.org/").append(i)
                    .append("\">Başlık ").append(i).append("</a></td></tr>")
                    .append("<tr><td class=\"result-snippet\">")
                    .append("kelime ".repeat(30)).append("</td></tr>");
        }
        String bigBlock = WebSearch.contextBlock(WebSearch.parse(huge.toString()));
        assertTrue(bigBlock.length() <= WebSearch.BLOCK_CHAR_BUDGET + 200); // last line may overshoot before the break
        // Empty input → "" (nothing injected).
        assertEquals("", WebSearch.contextBlock(null));
        assertEquals("", WebSearch.contextBlock(java.util.Collections.emptyList()));
    }

    @Test
    public void urlDecode_percentAndPlus() {
        assertEquals("a b/c?d=e", WebSearch.urlDecode("a+b%2Fc%3Fd%3De"));
        assertEquals("merhaba dünya", WebSearch.urlDecode("merhaba d%C3%BCnya"));
        // No escapes → untouched.
        assertEquals("plain", WebSearch.urlDecode("plain"));
        assertEquals(null == WebSearch.urlDecode(null), true);
    }
}
