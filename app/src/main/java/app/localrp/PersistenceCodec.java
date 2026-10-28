package app.localrp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure JSON codec for LocalRP's SharedPreferences storage layer. UI-independent so it
 * can be unit-tested on the JVM (org.json is provided as a test dependency).
 *
 * Decode results carry a {@code rootCorrupt} flag: true when the stored value could
 * not be parsed as a JSON array at all. Writers must refuse to overwrite a rootCorrupt
 * value — otherwise an unparseable store would be silently replaced by an empty default
 * and the original data would be unrecoverable. Malformed individual records are skipped
 * so one bad record cannot truncate the surviving list; when the root parse itself fails
 * (one broken element breaks the whole array), a token-level rescue pass salvages every
 * still-readable record without ever permitting an overwrite. */
final class PersistenceCodec {
    private PersistenceCodec() {}

    /** Decode result: decoded items plus corruption diagnostics. */
    static final class Decode<T> {
        final List<T> items = new ArrayList<>();
        boolean rootCorrupt = false;
        int droppedRecords = 0;
    }

    /** A write is only allowed over storage that parses cleanly (or never existed). */
    static boolean overwriteAllowed(String raw) {
        if (raw == null) return true;
        try {
            new JSONArray(raw);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /** Maps one already-parsed array element onto a model object. */
    private interface Mapper<T> {
        T map(Object value) throws JSONException;
    }

    static String encodeCharacters(List<CharacterProfile> items) {
        try {
            JSONArray data = new JSONArray();
            for (CharacterProfile c : items) {
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("name", c.name);
                o.put("description", c.description);
                o.put("personality", c.personality);
                o.put("instructions", c.instructions);
                o.put("examples", c.examples);
                data.put(o);
            }
            return data.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    static Decode<CharacterProfile> decodeCharacters(String raw) {
        Decode<CharacterProfile> result = new Decode<>();
        try {
            JSONArray data = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < data.length(); i++) {
                try {
                    result.items.add(characterFrom(data.getJSONObject(i)));
                } catch (JSONException badRecord) {
                    result.droppedRecords++;
                }
            }
            return result;
        } catch (JSONException root) {
            result.rootCorrupt = true;
            rescueArray(raw, result, PersistenceCodec::characterFromLoose);
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    private static CharacterProfile characterFromLoose(Object value) throws JSONException {
        return characterFrom((JSONObject) value);
    }

    private static CharacterProfile characterFrom(JSONObject o) throws JSONException {
        CharacterProfile c = new CharacterProfile();
        c.id = o.getString("id");
        c.name = o.getString("name");
        c.description = o.optString("description");
        c.personality = o.optString("personality");
        c.instructions = o.optString("instructions");
        c.examples = o.optString("examples");
        return c;
    }

    static String encodeGroups(List<Group> items) {
        try {
            JSONArray data = new JSONArray();
            for (Group g : items) {
                JSONObject o = new JSONObject();
                o.put("id", g.id);
                o.put("name", g.name);
                o.put("roleplay", g.roleplay);
                o.put("characters", new JSONArray(g.characterIds));
                data.put(o);
            }
            return data.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    static Decode<Group> decodeGroups(String raw) {
        Decode<Group> result = new Decode<>();
        try {
            JSONArray data = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < data.length(); i++) {
                try {
                    result.items.add(groupFrom(data.getJSONObject(i)));
                } catch (JSONException badRecord) {
                    result.droppedRecords++;
                }
            }
            return result;
        } catch (JSONException root) {
            result.rootCorrupt = true;
            rescueArray(raw, result, value -> groupFromLoose(value));
            return result;
        }
    }

    private static Group groupFromLoose(Object value) throws JSONException {
        return groupFrom((JSONObject) value);
    }

    private static Group groupFrom(JSONObject o) throws JSONException {
        Group g = new Group();
        g.id = o.getString("id");
        g.name = o.getString("name");
        g.roleplay = o.optBoolean("roleplay", true);
        JSONArray chars = o.optJSONArray("characters");
        if (chars != null) for (int i = 0; i < chars.length(); i++) g.characterIds.add(chars.getString(i));
        return g;
    }

    static String encodeMessages(List<Message> items) {
        try {
            JSONArray data = new JSONArray();
            for (Message m : items) {
                JSONObject o = new JSONObject();
                o.put("speaker", m.speaker);
                o.put("text", m.text);
                o.put("user", m.user);
                o.put("at", m.at);
                data.put(o);
            }
            return data.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    static Decode<Message> decodeMessages(String raw) {
        Decode<Message> result = new Decode<>();
        try {
            JSONArray data = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < data.length(); i++) {
                try {
                    JSONObject o = data.getJSONObject(i);
                    result.items.add(new Message(o.getString("speaker"), o.getString("text"), o.getBoolean("user"), o.optLong("at", 0L)));
                } catch (JSONException badRecord) {
                    result.droppedRecords++;
                }
            }
            return result;
        } catch (JSONException root) {
            result.rootCorrupt = true;
            rescueArray(raw, result, PersistenceCodec::messageFromLoose);
            return result;
        }
    }

    private static Message messageFromLoose(Object value) throws JSONException {
        if (!(value instanceof JSONObject)) throw new JSONException("not an object");
        JSONObject o = (JSONObject) value;
        return new Message(o.getString("speaker"), o.getString("text"), o.getBoolean("user"), o.optLong("at", 0L));
    }

    /**
     * Recovery pass over a stored value whose array parse failed. Reads elements one at a
     * time with a token scanner, skipping whatever cannot be parsed. Read-only: callers
     * still refuse to overwrite rootCorrupt storage, salvaged data is read-only relief.
     */
    private static <T> void rescueArray(String raw, Decode<T> result, Mapper<T> mapper) {
        if (raw == null || raw.isEmpty()) return;
        JSONTokener tokens = new JSONTokener(raw);
        try {
            if (tokens.nextClean() != '[') return;
            char c = tokens.nextClean();
            if (c == ']') return;
            if (c == '\0') return; // handled org.json versions may throw here instead
            tokens.back();
        } catch (JSONException unterminated) {
            return;
        }
        while (true) {
            boolean wrapped = false;
            try {
                Object value = tokens.nextValue();
                wrapped = true;
                try {
                    result.items.add(mapper.map(value));
                } catch (JSONException badItem) {
                    result.droppedRecords++;
                }
            } catch (JSONException brokenElement) {
                result.droppedRecords++;
            }
            if (!wrapped && !skipElement(tokens)) return; // scanner reached end of input
            char delimiter;
            try {
                delimiter = tokens.nextClean();
            } catch (JSONException endOfInput) {
                return;
            }
            if (delimiter == ']' || delimiter == '\0') return;
            if (delimiter != ',') {
                // stray garbage between records: scan ahead to the next boundary
                if (!skipElement(tokens)) return;
            }
        }
    }

    /** Skips to the boundary of the current array element (comma or closing bracket). */
    private static boolean skipElement(JSONTokener tokens) {
        int depth = 0;
        boolean inString = false, escaped = false;
        while (true) {
            char c = tokens.next();
            if (c == '\0') return false;
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') {
                if (depth == 0) return true; // boundary reached
                depth--;
            } else if (c == ',' && depth == 0) {
                tokens.back();
                return true;
            }
        }
    }

    // ---- backup bundle (characters + groups + transcripts in one JSON object) ----

    /**
     * version 1 = { characters:[], groups:[], chats:{ "<groupId>":[messages...] } }.
     */
    static final class Bundle {
        final List<CharacterProfile> characters = new ArrayList<>();
        final List<Group> groups = new ArrayList<>();
        final java.util.Map<String, List<Message>> chats = new java.util.HashMap<>();
    }

    static String encodeBundle(Bundle bundle) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            JSONArray chars = new JSONArray();
            for (CharacterProfile c : bundle.characters) {
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("name", c.name);
                o.put("description", c.description);
                o.put("personality", c.personality);
                o.put("instructions", c.instructions);
                o.put("examples", c.examples);
                chars.put(o);
            }
            root.put("characters", chars);
            JSONArray groups = new JSONArray();
            for (Group g : bundle.groups) {
                JSONObject o = new JSONObject();
                o.put("id", g.id);
                o.put("name", g.name);
                o.put("roleplay", g.roleplay);
                o.put("characters", new JSONArray(g.characterIds));
                groups.put(o);
            }
            root.put("groups", groups);
            JSONObject chats = new JSONObject();
            for (java.util.Map.Entry<String, List<Message>> entry : bundle.chats.entrySet()) {
                JSONArray messages = new JSONArray();
                for (Message m : entry.getValue()) {
                    JSONObject o = new JSONObject();
                    o.put("speaker", m.speaker);
                    o.put("text", m.text);
                    o.put("user", m.user);
                    o.put("at", m.at);
                    messages.put(o);
                }
                chats.put(entry.getKey(), messages);
            }
            root.put("chats", chats);
            return root.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /** Returns null when the value is not a version-1 backup object. */
    static Bundle decodeBundle(String raw) {
        if (raw == null) return null;
        try {
            JSONObject root = new JSONObject(raw.trim());
            if (root.optInt("version", -1) != 1) return null;
            Bundle bundle = new Bundle();
            JSONArray chars = root.optJSONArray("characters");
            if (chars != null) {
                for (int i = 0; i < chars.length(); i++) {
                    try {
                        JSONObject o = chars.getJSONObject(i);
                        CharacterProfile c = new CharacterProfile();
                        c.id = o.getString("id");
                        c.name = o.getString("name");
                        c.description = o.optString("description");
                        c.personality = o.optString("personality");
                        c.instructions = o.optString("instructions");
                        c.examples = o.optString("examples");
                        bundle.characters.add(c);
                    } catch (JSONException badRecord) { }
                }
            }
            JSONArray groupArray = root.optJSONArray("groups");
            if (groupArray != null) {
                for (int i = 0; i < groupArray.length(); i++) {
                    try {
                        JSONObject o = groupArray.getJSONObject(i);
                        Group g = new Group();
                        g.id = o.getString("id");
                        g.name = o.getString("name");
                        g.roleplay = o.optBoolean("roleplay", true);
                        JSONArray ids = o.optJSONArray("characters");
                        if (ids != null) for (int k = 0; k < ids.length(); k++) g.characterIds.add(ids.getString(k));
                        bundle.groups.add(g);
                    } catch (JSONException badRecord) { }
                }
            }
            JSONObject chats = root.optJSONObject("chats");
            if (chats != null) {
                java.util.Iterator<String> keys = chats.keys();
                while (keys.hasNext()) {
                    String groupId = keys.next();
                    JSONArray messages = chats.optJSONArray(groupId);
                    if (messages == null) continue;
                    List<Message> list = new ArrayList<>();
                    for (int i = 0; i < messages.length(); i++) {
                        try {
                            JSONObject o = messages.getJSONObject(i);
                            list.add(new Message(o.getString("speaker"), o.getString("text"),
                                    o.getBoolean("user"), o.optLong("at", 0L)));
                        } catch (JSONException badMessage) { }
                    }
                    bundle.chats.put(groupId, list);
                }
            }
            return bundle;
        } catch (JSONException broken) {
            return null;
        }
    }

    /** Insert-or-update over a stored list, matching on the id field. */
    static List<CharacterProfile> mergeCharacters(List<CharacterProfile> stored, List<CharacterProfile> imported) {
        List<CharacterProfile> merged = new ArrayList<>(stored);
        for (CharacterProfile incoming : imported) {
            boolean replaced = false;
            for (int i = 0; i < merged.size(); i++) {
                if (merged.get(i).id.equals(incoming.id)) {
                    merged.set(i, incoming);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) merged.add(incoming);
        }
        return merged;
    }

    static List<Group> mergeGroups(List<Group> stored, List<Group> imported) {
        List<Group> merged = new ArrayList<>(stored);
        for (Group incoming : imported) {
            boolean replaced = false;
            for (int i = 0; i < merged.size(); i++) {
                if (merged.get(i).id.equals(incoming.id)) {
                    merged.set(i, incoming);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) merged.add(incoming);
        }
        return merged;
    }

    /**
     * Deterministic chat union: a transcript is replaced only when the imported copy is
     * strictly longer (a genuine continuation); otherwise the stored one stays.
     * Concatenating message lists would duplicate every message after a re-import, so
     * the decision is whole-transcript and rule-based.
     */
    static java.util.Map<String, List<Message>> mergeChats(
            java.util.Map<String, List<Message>> stored, java.util.Map<String, List<Message>> imported) {
        java.util.Map<String, List<Message>> merged = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, List<Message>> entry : stored.entrySet()) {
            List<Message> incoming = imported.get(entry.getKey());
            if (incoming != null && incoming.size() > entry.getValue().size()) {
                merged.put(entry.getKey(), new ArrayList<>(incoming));
            } else {
                merged.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        for (java.util.Map.Entry<String, List<Message>> entry : imported.entrySet()) {
            if (!merged.containsKey(entry.getKey())) merged.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return merged;
    }
}

