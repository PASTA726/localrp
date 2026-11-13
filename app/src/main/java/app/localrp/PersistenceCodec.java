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
                if (c.samplerTemp != null && !c.samplerTemp.isEmpty()) o.put("samplerTemp", c.samplerTemp);
                if (c.avatarPath != null && !c.avatarPath.isEmpty()) o.put("avatar", c.avatarPath);
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
        c.samplerTemp = o.optString("samplerTemp");
        c.avatarPath = o.optString("avatar");
        return c;
    }

    /** Legacy single-persona record (v0.14–v0.15 format): read-only today — the
     *  v0.19.16 migration decodes it into the persona list; nothing writes it. */
    static String[] decodePersona(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(raw);
            String name = o.isNull("name") ? "" : o.optString("name");
            String description = o.isNull("description") ? "" : o.optString("description");
            String avatar = o.isNull("avatar") ? "" : o.optString("avatar");
            return new String[]{name, description, avatar};
        } catch (JSONException e) {
            return null;
        }
    }

    /** v0.19.16 multi-persona list: [{id, name, description, avatar}]. */
    static String encodePersonas(List<Persona> items) {
        try {
            JSONArray data = new JSONArray();
            for (Persona p : items) {
                JSONObject o = new JSONObject();
                o.put("id", p.id == null ? "" : p.id);
                o.put("name", p.name == null ? "" : p.name);
                o.put("description", p.description == null ? "" : p.description);
                o.put("avatar", p.avatarPath == null ? "" : p.avatarPath);
                data.put(o);
            }
            return data.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /** Tolerant list decode: single corrupt records are dropped, not fatal. */
    static Decode<Persona> decodePersonas(String raw) {
        Decode<Persona> result = new Decode<>();
        try {
            JSONArray data = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < data.length(); i++) {
                try {
                    JSONObject o = data.getJSONObject(i);
                    Persona p = new Persona();
                    p.id = o.isNull("id") ? "" : o.optString("id");
                    p.name = o.isNull("name") ? "" : o.optString("name");
                    p.description = o.isNull("description") ? "" : o.optString("description");
                    p.avatarPath = o.isNull("avatar") ? "" : o.optString("avatar");
                    if (p.id.isEmpty()) continue; // an identity without an id cannot be addressed
                    result.items.add(p);
                } catch (JSONException badRecord) {
                    result.droppedRecords++;
                }
            }
        } catch (JSONException root) {
            result.rootCorrupt = true;
        }
        return result;
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
                if (m.hasImage()) o.put("image", m.image);
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
                    result.items.add(new Message(o.getString("speaker"), o.getString("text"), o.getBoolean("user"), o.optLong("at", 0L), o.optString("image")));
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
        return new Message(o.getString("speaker"), o.getString("text"), o.getBoolean("user"), o.optLong("at", 0L), o.optString("image"));
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
     * version 1 = { version:1, characters:[], groups:[], chats:{ "<groupId>":[messages...] } }
     * with absolute file paths (the original SAF backup).
     * version 2 = same shape, but media paths are "@avatars/<name>" / "@images/<name>"
     * markers relative to the device's avatar/image directories, so a ZIP bundle can
     * carry the bytes under media/avatars/ and media/images/.
     */
    static final class Bundle {
        int version = 1;
        /** Device-local roots used to rewrite absolute paths into @ markers (optional, v2 exports only). */
        String avatarsRoot, imagesRoot;
        final List<CharacterProfile> characters = new ArrayList<>();
        final List<Group> groups = new ArrayList<>();
        final java.util.Map<String, List<Message>> chats = new java.util.HashMap<>();
        /** World book (DeepSeek item 12): per-group lore entries, optional in both versions. */
        final java.util.Map<String, List<Lorebook.Entry>> lorebooks = new java.util.HashMap<>();
        /** v0.19.18: user personas (v0.19.16 feature) now travel in the bundle too. */
        final List<Persona> personas = new ArrayList<>();
        /** Id of the persona marked default; "" = none (restore then keeps the local default). */
        String defaultPersonaId = "";
        /** Per-chat persona override, only for chats that travel; "" entries are never exported. */
        final java.util.Map<String, String> chatPersonas = new java.util.HashMap<>();
    }

    // ---- media markers (backup v2) ----

    /**
     * Maps a device-local absolute path under the avatar or image directory to a
     * "@avatars/<name>" / "@images/<name>" marker. Returns "" for empty paths and
     * paths outside both roots (they cannot travel inside a bundle).
     */
    static String toMediaRelative(String path, String avatarsRoot, String imagesRoot) {
        if (path == null || path.isEmpty()) return "";
        String prefix0 = avatarsRoot == null ? null : avatarsRoot + "/";
        String prefix1 = imagesRoot == null ? null : imagesRoot + "/";
        int space = -1;
        String rootPrefix = null;
        if (prefix0 != null && path.startsWith(prefix0)) { space = 0; rootPrefix = prefix0; }
        else if (prefix1 != null && path.startsWith(prefix1)) { space = 1; rootPrefix = prefix1; }
        if (rootPrefix == null) return "";
        String name = path.substring(rootPrefix.length());
        if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || ".".equals(name) || "..".equals(name)) {
            return "";
        }
        return space == 0 ? "@avatars/" + name : "@images/" + name;
    }

    /**
     * Inverse of {@link #toMediaRelative}: turns a media marker into a device-local
     * absolute path. Non-marker input passes through unchanged (version-1 absolute
     * paths stay absolute); unknown spaces, unsafe names or ".." collapse to "".
     */
    static String resolveMediaPath(String input, String avatarsRoot, String imagesRoot) {
        if (input == null || input.isEmpty()) return "";
        if (!input.startsWith("@")) return input;
        int slash = input.indexOf('/', 1);
        if (slash < 0) return "";
        String space = input.substring(1, slash);
        String name = input.substring(slash + 1);
        String root = "avatars".equals(space) ? avatarsRoot : "images".equals(space) ? imagesRoot : null;
        if (root == null || name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || ".".equals(name) || "..".equals(name)) {
            return "";
        }
        return root.endsWith("/") ? root + name : root + "/" + name;
    }

    /** Lowercase hex SHA-256 of the given bytes; "" only when the JRE has no SHA-256. */
    static String sha256Hex(byte[] bytes) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return hexLower(digest.digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            return "";
        }
    }

    /**
     * Digest over the whole packaged-media set, not just the JSON: every sorted entry
     * contributes name + ':' + bytes, so swapping or reordering media flips the value.
     * An empty map digests like any other empty input.
     */
    static String mediaDigestHex(java.util.SortedMap<String, byte[]> media) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            for (java.util.Map.Entry<String, byte[]> entry : media.entrySet()) {
                digest.update(entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update((byte) ':');
                digest.update(entry.getValue() == null ? new byte[0] : entry.getValue());
            }
            return hexLower(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            return "";
        }
    }

    private static String hexLower(byte[] hash) {
        StringBuilder text = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            text.append("0123456789abcdef".charAt((b >> 4) & 0xF));
            text.append("0123456789abcdef".charAt(b & 0xF));
        }
        return text.toString();
    }

    static String encodeBundle(Bundle bundle) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", bundle.version);
            JSONArray chars = new JSONArray();
            for (CharacterProfile c : bundle.characters) {
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("name", c.name);
                o.put("description", c.description);
                o.put("personality", c.personality);
                o.put("instructions", c.instructions);
                o.put("examples", c.examples);
                if (bundle.version >= 2 && c.samplerTemp != null && !c.samplerTemp.isEmpty()) {
                    o.put("samplerTemp", c.samplerTemp);
                }
                if (bundle.version >= 2) {
                    String avatar = toMediaRelative(c.avatarPath, bundle.avatarsRoot, bundle.imagesRoot);
                    if (!avatar.isEmpty()) o.put("avatar", avatar);
                }
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
                    if (m.hasImage()) {
                        String image = bundle.version >= 2
                                ? toMediaRelative(m.image, bundle.avatarsRoot, bundle.imagesRoot)
                                : m.image;
                        if (!image.isEmpty()) o.put("image", image);
                    }
                    messages.put(o);
                }
                chats.put(entry.getKey(), messages);
            }
            root.put("chats", chats);
            JSONObject loreRoot = new JSONObject();
            for (java.util.Map.Entry<String, List<Lorebook.Entry>> entry : bundle.lorebooks.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
                loreRoot.put(entry.getKey(), new JSONArray(Lorebook.encode(entry.getValue())));
            }
            root.put("lore", loreRoot);
            JSONArray personaArray = new JSONArray();
            for (Persona p : bundle.personas) {
                JSONObject o = new JSONObject();
                o.put("id", p.id == null ? "" : p.id);
                o.put("name", p.name == null ? "" : p.name);
                o.put("description", p.description == null ? "" : p.description);
                if (bundle.version >= 2) {
                    String avatar = toMediaRelative(p.avatarPath, bundle.avatarsRoot, bundle.imagesRoot);
                    if (!avatar.isEmpty()) o.put("avatar", avatar);
                }
                personaArray.put(o);
            }
            if (personaArray.length() > 0) root.put("personas", personaArray);
            if (bundle.defaultPersonaId != null && !bundle.defaultPersonaId.isEmpty()) {
                root.put("defaultPersona", bundle.defaultPersonaId);
            }
            if (!bundle.chatPersonas.isEmpty()) {
                JSONObject overrides = new JSONObject();
                for (java.util.Map.Entry<String, String> entry : bundle.chatPersonas.entrySet()) {
                    if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
                    overrides.put(entry.getKey(), entry.getValue());
                }
                if (overrides.length() > 0) root.put("chatPersonas", overrides);
            }
            return root.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /** Returns null when the value is not a version-1 or version-2 backup object. */
    static Bundle decodeBundle(String raw) {
        if (raw == null) return null;
        try {
            JSONObject root = new JSONObject(raw.trim());
            int version = root.optInt("version", -1);
            if (version != 1 && version != 2) return null;
            Bundle bundle = new Bundle();
            bundle.version = version;
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
                        c.samplerTemp = o.optString("samplerTemp");
                        c.avatarPath = o.optString("avatar");
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
                                    o.getBoolean("user"), o.optLong("at", 0L), o.optString("image")));
                        } catch (JSONException badMessage) { }
                    }
                    bundle.chats.put(groupId, list);
                }
            }
            JSONObject loreRoot = root.optJSONObject("lore");
            if (loreRoot != null) {
                java.util.Iterator<String> loreKeys = loreRoot.keys();
                while (loreKeys.hasNext()) {
                    String groupId = loreKeys.next();
                    JSONArray entries = loreRoot.optJSONArray(groupId);
                    if (entries == null) continue;
                    bundle.lorebooks.put(groupId, Lorebook.decode(entries.toString()));
                }
            }
            // v0.19.18: personas + default id + per-chat overrides. Tolerant: records
            // without an id are dropped, broken roots leave the bundle without personas.
            JSONArray personaArray = root.optJSONArray("personas");
            if (personaArray != null) {
                for (int i = 0; i < personaArray.length(); i++) {
                    try {
                        JSONObject o = personaArray.getJSONObject(i);
                        Persona p = new Persona();
                        p.id = o.optString("id");
                        p.name = o.optString("name");
                        p.description = o.optString("description");
                        p.avatarPath = o.optString("avatar");
                        if (p.id.isEmpty()) continue;
                        bundle.personas.add(p);
                    } catch (JSONException badRecord) { }
                }
            }
            bundle.defaultPersonaId = root.optString("defaultPersona", "");
            JSONObject overrides = root.optJSONObject("chatPersonas");
            if (overrides != null) {
                java.util.Iterator<String> overrideKeys = overrides.keys();
                while (overrideKeys.hasNext()) {
                    String chatId = overrideKeys.next();
                    String personaId = overrides.optString(chatId, "");
                    if (!chatId.isEmpty() && !personaId.isEmpty()) bundle.chatPersonas.put(chatId, personaId);
                }
            }
            return bundle;
        } catch (JSONException broken) {
            return null;
        }
    }

    /**
     * Selective export: reduces a full bundle to the chosen groups and characters.
     * Characters referenced by a chosen group always travel too, so restored chats
     * stay coherent; transcript entries outside the kept groups are dropped.
     */
    static Bundle selectBundle(Bundle source, java.util.Set<String> characterIds, java.util.Set<String> groupIds) {
        Bundle selected = new Bundle();
        selected.version = source.version;
        selected.avatarsRoot = source.avatarsRoot;
        selected.imagesRoot = source.imagesRoot;
        java.util.Set<String> wanted = new java.util.HashSet<>(characterIds);
        for (Group g : source.groups) {
            if (!groupIds.contains(g.id)) continue;
            selected.groups.add(g);
            wanted.addAll(g.characterIds);
        }
        for (CharacterProfile c : source.characters) {
            if (wanted.contains(c.id)) selected.characters.add(c);
        }
        for (java.util.Map.Entry<String, List<Message>> entry : source.chats.entrySet()) {
            if (groupIds.contains(entry.getKey())) selected.chats.put(entry.getKey(), entry.getValue());
        }
        for (java.util.Map.Entry<String, List<Lorebook.Entry>> entry : source.lorebooks.entrySet()) {
            if (groupIds.contains(entry.getKey())) selected.lorebooks.put(entry.getKey(), entry.getValue());
        }
        // Personas always travel whole (small, and the default choice must stay
        // meaningful); per-chat overrides only for chats that survive the selection.
        selected.personas.addAll(source.personas);
        selected.defaultPersonaId = source.defaultPersonaId;
        for (java.util.Map.Entry<String, String> entry : source.chatPersonas.entrySet()) {
            if (groupIds.contains(entry.getKey())) selected.chatPersonas.put(entry.getKey(), entry.getValue());
        }
        return selected;
    }

    /** Insert-or-update over a stored persona list, matching on the id field. */
    static List<Persona> mergePersonas(List<Persona> stored, List<Persona> imported) {
        List<Persona> merged = new ArrayList<>(stored);
        for (Persona incoming : imported) {
            if (incoming.id == null || incoming.id.isEmpty()) continue;
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

