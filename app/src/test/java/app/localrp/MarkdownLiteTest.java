package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** JVM tests for the markdown-lite selection logic behind chat balloons. */
public class MarkdownLiteTest {
    private String joined(List<MarkdownLite.Segment> segments) {
        StringBuilder out = new StringBuilder();
        for (MarkdownLite.Segment segment : segments) out.append(segment.text);
        return out.toString();
    }

    private MarkdownLite.Segment only(List<MarkdownLite.Segment> segments) {
        assertEquals(1, segments.size());
        return segments.get(0);
    }

    @Test public void plainTextStaysPlain() {
        MarkdownLite.Segment segment = only(MarkdownLite.parse("Merhaba dünya"));
        assertEquals("Merhaba dünya", segment.text);
        assertFalse(segment.bold);
        assertFalse(segment.italic);
    }

    @Test public void boldPairBecomesBoldSegment() {
        MarkdownLite.Segment segment = only(MarkdownLite.parse("**vurgu**"));
        assertEquals("vurgu", segment.text);
        assertTrue(segment.bold);
        assertFalse(segment.italic);
    }

    @Test public void italicPairBecomesItalicSegment() {
        MarkdownLite.Segment segment = only(MarkdownLite.parse("*eylem*"));
        assertEquals("eylem", segment.text);
        assertTrue(segment.italic);
        assertFalse(segment.bold);
    }

    @Test public void markersAreRemovedFromOutput() {
        assertEquals("kaba italik", joined(MarkdownLite.parse("**kaba** *italik*")));
    }

    @Test public void unmatchedMarkersStayLiteral() {
        MarkdownLite.Segment segment = only(MarkdownLite.parse("tek * yıldız"));
        assertEquals("tek * yıldız", segment.text);
        assertFalse(segment.italic);
    }

    @Test public void lonePairOpenerStaysLiteral() {
        assertEquals("**açık", joined(MarkdownLite.parse("**açık")));
        assertEquals("kapalı*", joined(MarkdownLite.parse("kapalı*")));
    }

    @Test public void boldInsideItalicKeepsBothStyles() {
        List<MarkdownLite.Segment> segments = MarkdownLite.parse("*a **b** c*");
        assertEquals("a b c", joined(segments));
        assertTrue(segments.get(0).italic);            // "a "
        assertTrue(segments.get(1).bold && segments.get(1).italic); // "b"
        assertTrue(segments.get(2).italic);            // " c"
    }

    @Test public void mixedRunsSplitCorrectly() {
        List<MarkdownLite.Segment> segments = MarkdownLite.parse("önce **kalın** sonra *italik*");
        assertEquals(4, segments.size()); // plain, bold, plain, italic
        assertFalse(segments.get(0).bold);
        assertTrue(segments.get(1).bold);
        assertFalse(segments.get(2).bold);
        assertTrue(segments.get(3).italic);
        assertFalse(segments.get(3).bold);
        assertEquals("önce kalın sonra italik", joined(segments));
    }

    @Test public void emptyContentsStayLiteral() {
        // "**" with nothing between has no empty-content pair; the raw text survives.
        assertEquals("****", joined(MarkdownLite.parse("****")));
        assertEquals("**", joined(MarkdownLite.parse("**")));
    }

    @Test public void nullAndEmptyAreSafe() {
        assertTrue(MarkdownLite.parse(null).isEmpty());
        assertEquals("", only(MarkdownLite.parse("")).text);
    }

    @Test public void repeatedPairsAreAllMatched() {
        List<MarkdownLite.Segment> segments = MarkdownLite.parse("*a* ara *b*");
        assertEquals(3, segments.size());
        assertTrue(segments.get(0).italic);
        assertFalse(segments.get(1).italic);
        assertTrue(segments.get(2).italic);
        assertEquals("a ara b", joined(segments));
    }
}
