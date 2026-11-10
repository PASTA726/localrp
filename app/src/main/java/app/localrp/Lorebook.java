package app.localrp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * World book (DeepSeek item 12): keyword-triggered scene knowledge. Each entry holds
 * comma-separated trigger keys and a fact text; a generation injects only the entries
 * whose keys appear in the recent transcript, under a total character budget, so the
 * model keeps world facts without always paying for them in the prompt.
 * Lore is stored per conversation under the "lore_<groupId>" preference (Store) and
 * travels inside the v2 backup bundle as optional "lore" records.
 */
final class Lorebook {
    private Lorebook() {}

    /** One world-book record: comma-separated trigger keys plus the fact text. */
    static final class Entry {
        final String keys;
        final String text;

        Entry(String keys, String text) {
            this.keys = keys == null ? "" : keys.trim();
            this.text = text == null ? "" : text.trim();
        }
    }

    /** Fixed budget of the injected knowledge block, keep the prompt lean by design. */
    static final int BLOCK_CHAR_BUDGET = 900;

    static String encode(List<Entry> items) {
        try {
            JSONArray data = new JSONArray();
            for (Entry entry : items) {
                if (entry.keys.isEmpty() || entry.text.isEmpty()) continue;
                JSONObject o = new JSONObject();
                o.put("k", entry.keys);
                o.put("t", entry.text);
                data.put(o);
            }
            return data.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /** Fault-tolerant decode: malformed records are skipped; null/garbage → empty list. */
    static List<Entry> decode(String raw) {
        List<Entry> items = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < data.length(); i++) {
                try {
                    JSONObject o = data.getJSONObject(i);
                    Object keys = o.opt("k");
                    Object text = o.opt("t");
                    if (keys == null || text == null) continue;
                    items.add(new Entry(keys.toString(), text.toString()));
                } catch (JSONException badRecord) { /* skip one bad record */ }
            }
        } catch (JSONException root) { /* garbage or null: the conversation simply has no lore */ }
        return items;
    }

    /**
     * Entries whose any key appears case-insensitively anywhere in the scan text,
     * in book order. The fold is a deliberate two-way canonicalization: dotted and
     * dotless i variants all collapse to the same character on BOTH sides, so a key
     * typed as "IZMIR" matches text typed as "izmir" without locale ambiguity.
     */
    static List<Entry> triggered(List<Entry> entries, String scanText) {
        List<Entry> hits = new ArrayList<>();
        String haystack = fold(scanText);
        if (haystack.isEmpty()) return hits;
        for (Entry entry : entries) {
            if (entry.keys.isEmpty() || entry.text.isEmpty()) continue;
            for (String key : entry.keys.split(",")) {
                key = fold(key);
                if (!key.isEmpty() && haystack.contains(key)) {
                    hits.add(entry);
                    break;
                }
            }
        }
        return hits;
    }

    /** Fold helper: ı/İ/I/i all become i, then a plain root lowercase; symmetric. */
    private static String fold(String value) {
        if (value == null) return "";
        return value.replace('ı', 'i').replace('İ', 'i')
                .replace('I', 'i').toLowerCase(Locale.ROOT);
    }

    /**
     * Bullet lines for the prompt, within the given character budget. Entries that no
     * longer fit are clipped in order; a tiny remainder drops the line entirely rather
     * than shipping an unreadable fragment.
     */
    static String assemble(List<Entry> hitEntries, int budget) {
        StringBuilder block = new StringBuilder();
        int left = Math.max(0, budget);
        for (Entry entry : hitEntries) {
            int lineMax = left - 3; // "- " prefix + "\n" suffix
            if (lineMax <= 40) break;
            String text = entry.text;
            if (text.length() > lineMax) text = text.substring(0, lineMax - 1) + "…";
            String line = "- " + text + "\n";
            block.append(line);
            left -= line.length();
        }
        return block.toString();
    }
}
