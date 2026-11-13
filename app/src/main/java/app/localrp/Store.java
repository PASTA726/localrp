package app.localrp;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SharedPreferences-backed persistence for characters, conversations and transcripts.
 * Extracted verbatim from MainActivity (Task 07); MainActivity keeps a single
 * {@code Store} field, so no call sites change. The write-free integrity guards and
 * the rescue-capable {@link PersistenceCodec} stay exactly as in v0.7.4.
 */
class Store {
    private final String defaultCharacterId;
    private final SharedPreferences preferences;
    // In-memory chat transcripts: messages() used to re-parse the whole JSON array on
    // every read, making large conversations O(n) main-thread work per message.
    private final java.util.Map<String, List<Message>> chatCache = new java.util.HashMap<>();
    // Keys of stored values that exist but cannot be parsed. Writes to them are refused
    // so the store can never be replaced by an empty default while unreadable.
    private final java.util.Set<String> integrityIssues = new java.util.HashSet<>();

    Store(Context c, String defaultCharacterId) { this.defaultCharacterId = defaultCharacterId; preferences = c.getSharedPreferences("localrp", Context.MODE_PRIVATE); }

    java.util.Set<String> integrityIssues() { return integrityIssues; }

    void migrateToRoleplay() {
        List<Group> all = groups();
        boolean needsDefault = false;
        boolean changed = false;
        for (Group group : all) {
            int originalSize = group.characterIds.size();
            group.characterIds.removeIf(id -> character(id) == null);
            if (group.characterIds.size() != originalSize) changed = true;
            if (!group.roleplay || group.characterIds.isEmpty()) needsDefault = true;
        }
        CharacterProfile defaultCharacter = null;
        if (needsDefault) {
            defaultCharacter = character(defaultCharacterId);
            if (defaultCharacter == null) {
                defaultCharacter = new CharacterProfile();
                defaultCharacter.id = defaultCharacterId;
                defaultCharacter.name = "Mira";
                defaultCharacter.description = "Kullanıcının hikâyesindeki karşı karakter.";
                defaultCharacter.personality = "Sıcak, meraklı ve sahneye duyarlı; duygularını doğal biçimde gösterir.";
                defaultCharacter.instructions = "Daima Mira olarak, hikâyenin içinde yanıt ver. Kullanıcının eylemlerini veya sözlerini yazma.";
                saveCharacter(defaultCharacter);
            }
        }
        if (defaultCharacter != null) {
            for (Group group : all) {
                if (!group.roleplay || group.characterIds.isEmpty()) {
                    group.roleplay = true;
                    changed = true;
                    if (group.characterIds.isEmpty()) group.characterIds.add(defaultCharacter.id);
                }
            }
        }
        if (changed) for (Group group : all) saveGroup(group);
    }
    List<CharacterProfile> characters() {
        String raw = preferences.getString("characters", null);
        PersistenceCodec.Decode<CharacterProfile> decoded = PersistenceCodec.decodeCharacters(raw);
        if (decoded.rootCorrupt) integrityIssues.add("characters");
        return decoded.items;
    }
    CharacterProfile character(String id) { for (CharacterProfile c : characters()) if (c.id.equals(id)) return c; return null; }
    boolean saveCharacter(CharacterProfile edited) {        String raw = preferences.getString("characters", null);
        if (!PersistenceCodec.overwriteAllowed(raw)) { integrityIssues.add("characters"); return false; }
        List<CharacterProfile> all = PersistenceCodec.decodeCharacters(raw).items;
        boolean found = false;
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(edited.id)) { all.set(i, edited); found = true; }
        if (!found) all.add(edited);
        return putCharacters(all);
    }

    // ---- v0.19.16 multi-persona system ----

    static final String DEFAULT_PERSONA_ID = "default";

    /** All personas with one-time migration from the legacy single-persona key. */
    java.util.List<Persona> personas() {
        String raw = preferences.getString("user_personas", null);
        if (raw == null) {
            // Legacy install: lift the single record into the list under a fixed id.
            String legacy = preferences.getString("user_persona", null);
            if (legacy != null) {
                String[] fields = PersistenceCodec.decodePersona(legacy);
                // An unreadable legacy record must not be deleted: keep the key so a
                // later read can still rescue it, and leave the migration undone.
                if (fields != null) {
                    java.util.List<Persona> migrated = new java.util.ArrayList<>();
                    migrated.add(new Persona(DEFAULT_PERSONA_ID, fields[0], fields[1], fields[2]));
                    setDefaultPersonaId(DEFAULT_PERSONA_ID);
                    preferences.edit().putString("user_personas", PersistenceCodec.encodePersonas(migrated))
                            .remove("user_persona").apply();
                }
            }
            return raw == null ? personasFromLegacyOnly() : PersistenceCodec.decodePersonas(raw).items;
        }
        PersistenceCodec.Decode<Persona> decoded = PersistenceCodec.decodePersonas(raw);
        return decoded.items;
    }

    /** Legacy key without user_personas: an unreadable record yields an empty list. */
    private java.util.List<Persona> personasFromLegacyOnly() {
        String legacy = preferences.getString("user_persona", null);
        if (legacy == null) return new java.util.ArrayList<>();
        String[] fields = PersistenceCodec.decodePersona(legacy);
        return fields != null
                ? java.util.Collections.singletonList(new Persona(DEFAULT_PERSONA_ID, fields[0], fields[1], fields[2]))
                : new java.util.ArrayList<>();
    }

    boolean savePersonas(java.util.List<Persona> items) {
        String raw = PersistenceCodec.encodePersonas(items);
        if (raw.isEmpty()) return false;
        // A write is allowed over storage that parses cleanly (or migrated legacy data).
        String current = preferences.getString("user_personas", null);
        if (current != null && PersistenceCodec.decodePersonas(current).rootCorrupt) return false;
        preferences.edit().putString("user_personas", raw).apply();
        return true;
    }

    /** Default persona: the one marked default, else the first, else null. A preferred
     *  id that names a deleted persona is corrected on the spot (self-healing), so the
     *  default tag and the effective default never drift apart. */
    Persona defaultPersona() {
        java.util.List<Persona> all = personas();
        if (all.isEmpty()) return null;
        String preferred = preferences.getString("default_persona_id", "");
        for (Persona p : all) if (p.id.equals(preferred)) return p;
        setDefaultPersonaId(all.get(0).id);
        return all.get(0);
    }

    boolean isDefaultPersona(String id) {
        Persona d = defaultPersona();
        return d != null && d.id.equals(id);
    }

    void setDefaultPersonaId(String id) {
        preferences.edit().putString("default_persona_id", id).apply();
    }

    Persona personaById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Persona p : personas()) if (p.id.equals(id)) return p;
        return null;
    }

    /** Per-chat persona override; "" (or missing) = follow the global default. */
    String chatPersonaId(String chatId) {
        return preferences.getString("chat_persona_" + chatId, "");
    }

    void setChatPersona(String chatId, String personaId) {
        preferences.edit().putString("chat_persona_" + chatId, personaId == null ? "" : personaId).apply();
    }

    /** Effective persona for a chat: its override wins, else the global default. */
    Persona personaForChat(String chatId) {
        Persona override = personaById(chatPersonaId(chatId));
        if (override != null) return override;
        return defaultPersona();
    }

    /** Drops every per-chat override that points at a deleted persona. */
    void clearChatPersonaReferences(String personaId) {
        android.content.SharedPreferences prefs = preferences;
        java.util.Map<String, ?> all = prefs.getAll();
        java.util.List<String> doomed = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
            if (e.getKey().startsWith("chat_persona_") && personaId.equals(e.getValue())) doomed.add(e.getKey());
        }
        if (doomed.isEmpty()) return;
        android.content.SharedPreferences.Editor editor = prefs.edit();
        for (String key : doomed) editor.remove(key);
        editor.apply();
    }

    /** RoleplayPrompt-compatible {name, description} pair, or null when none exists. */
    String[] personaTextForChat(String chatId) {
        Persona p = personaForChat(chatId);
        return p != null ? new String[]{p.name, p.description} : null;
    }

    boolean deleteCharacter(String id) {        if (defaultCharacterId.equals(id)) return true;
        String raw = preferences.getString("characters", null);
        if (!PersistenceCodec.overwriteAllowed(raw)) { integrityIssues.add("characters"); return false; }
        List<CharacterProfile> all = PersistenceCodec.decodeCharacters(raw).items;
        all.removeIf(c -> c.id.equals(id));
        putCharacters(all);
        List<Group> chats = groups();
        boolean needsDefault = false;
        for (Group group : chats) {
            if (group.characterIds.remove(id) && group.characterIds.isEmpty()) needsDefault = true;
            if (id.equals(preferences.getString("next_speaker_" + group.id, null))) {
                preferences.edit().remove("next_speaker_" + group.id).apply();
            }
        }
        CharacterProfile fallback = null;
        if (needsDefault) {
            fallback = character(defaultCharacterId);
            if (fallback == null) {
                fallback = new CharacterProfile();
                fallback.id = defaultCharacterId;
                fallback.name = "Mira";
                fallback.description = "Kullanıcının hikâyesindeki karşı karakter.";
                fallback.personality = "Sıcak, meraklı ve sahneye duyarlı; duygularını doğal biçimde gösterir.";
                fallback.instructions = "Daima Mira olarak, hikâyenin içinde yanıt ver. Kullanıcının eylemlerini veya sözlerini yazma.";
                saveCharacter(fallback);
            }
        }
        for (Group group : chats) {
            if (group.characterIds.isEmpty() && fallback != null) group.characterIds.add(fallback.id);
            group.roleplay = true;
            saveGroup(group);
        }
        return true;
    }
    List<Group> groups() {
        String raw = preferences.getString("groups", null);
        PersistenceCodec.Decode<Group> decoded = PersistenceCodec.decodeGroups(raw);
        if (decoded.rootCorrupt) integrityIssues.add("groups");
        return decoded.items;
    }
    Group group(String id) {
        for (Group group : groups()) if (group.id.equals(id)) return group;
        return null;
    }
    boolean saveGroup(Group edited) {
        String raw = preferences.getString("groups", null);
        if (!PersistenceCodec.overwriteAllowed(raw)) { integrityIssues.add("groups"); return false; }
        List<Group> all = PersistenceCodec.decodeGroups(raw).items;
        boolean found = false;
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(edited.id)) { all.set(i, edited); found = true; }
        if (!found) all.add(edited);
        String encoded = PersistenceCodec.encodeGroups(all);
        if (encoded.isEmpty()) return false;
        preferences.edit().putString("groups", encoded).apply();
        return true;
    }
    void deleteGroup(String id) {
        String raw = preferences.getString("groups", null);
        if (PersistenceCodec.overwriteAllowed(raw)) {
            List<Group> all = PersistenceCodec.decodeGroups(raw).items;
            if (all.removeIf(g -> g.id.equals(id))) {
                String encoded = PersistenceCodec.encodeGroups(all);
                if (!encoded.isEmpty()) preferences.edit().putString("groups", encoded).apply();
            }
        } else {
            // Catalog unreadable: refuse to rewrite it, but the chat payload of the
            // group being deleted is intentionally removed either way.
            integrityIssues.add("groups");
        }
        preferences.edit().remove("chat_" + id).remove("next_speaker_" + id).apply();
        chatCache.remove(id);
        integrityIssues.remove("chat_" + id);
    }
    List<Message> messages(String groupId) {
        List<Message> cached = chatCache.get(groupId);
        if (cached != null) return cached;
        String raw = preferences.getString("chat_" + groupId, null);
        PersistenceCodec.Decode<Message> decoded = PersistenceCodec.decodeMessages(raw);
        if (decoded.rootCorrupt) integrityIssues.add("chat_" + groupId);
        chatCache.put(groupId, decoded.items);
        return decoded.items;
    }
    Message lastMessage(String groupId) {
        List<Message> all = messages(groupId);
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    void addMessage(String id, Message message) {
        String raw = preferences.getString("chat_" + id, null);
        if (!PersistenceCodec.overwriteAllowed(raw)) { integrityIssues.add("chat_" + id); return; }
        List<Message> updated = new ArrayList<>(messages(id));
        updated.add(message);
        writeMessages(id, updated);
    }

    /** Wholesale transcript replace (undo-restore path); refuses to clobber corrupt payloads. */
    boolean replaceMessages(String groupId, List<Message> transcript) {
        String raw = preferences.getString("chat_" + groupId, null);
        if (!PersistenceCodec.overwriteAllowed(raw)) { integrityIssues.add("chat_" + groupId); return false; }
        return writeMessages(groupId, transcript);
    }

    /** World book (DeepSeek item 12): this conversation's keyword-triggered facts. */
    List<Lorebook.Entry> lore(String groupId) {
        return Lorebook.decode(preferences.getString("lore_" + groupId, null));
    }

    /** Overwrites the conversation's lore; refuses when the stored payload is unreadable. */
    boolean saveLore(String groupId, List<Lorebook.Entry> entries) {
        String raw = preferences.getString("lore_" + groupId, null);
        if (!PersistenceCodec.overwriteAllowed(raw)) { integrityIssues.add("lore_" + groupId); return false; }
        preferences.edit().putString("lore_" + groupId, Lorebook.encode(entries)).apply();
        return true;
    }

    /** Per-conversation pinned memories (v0.23.00); empty list when nothing is pinned. */
    List<String> memory(String groupId) {
        return ChatMemory.decode(preferences.getString("memory_" + groupId, null));
    }

    /** Overwrites the conversation's pinned memories; refuses on unreadable stored state. */
    boolean saveMemory(String groupId, List<String> memories) {
        String raw = preferences.getString("memory_" + groupId, null);
        if (!PersistenceCodec.overwriteAllowed(raw)) { integrityIssues.add("memory_" + groupId); return false; }
        preferences.edit().putString("memory_" + groupId, ChatMemory.encode(memories)).apply();
        return true;
    }

    /** Replaces one stored message's text (speaker/reader/image/timestamp preserved). */
    boolean updateMessage(String id, int index, String newText) {
        String raw = preferences.getString("chat_" + id, null);
        if (!PersistenceCodec.overwriteAllowed(raw)) { integrityIssues.add("chat_" + id); return false; }
        List<Message> all = messages(id);
        if (index < 0 || index >= all.size()) return false;
        Message original = all.get(index);
        List<Message> updated = new ArrayList<>(all);
        updated.set(index, new Message(original.speaker, newText, original.user, original.at, original.image));
        return writeMessages(id, updated);
    }

    boolean removeMessage(String id, int index) {
        String raw = preferences.getString("chat_" + id, null);
        if (!PersistenceCodec.overwriteAllowed(raw)) { integrityIssues.add("chat_" + id); return false; }
        List<Message> all = messages(id);
        if (index < 0 || index >= all.size()) return false;
        List<Message> updated = new ArrayList<>(all);
        updated.remove(index);
        return writeMessages(id, updated);
    }

    /** Drops everything at and after an index (regenerate needs to forget the last turn). */
    boolean removeMessagesFrom(String id, int index) {
        String raw = preferences.getString("chat_" + id, null);
        if (!PersistenceCodec.overwriteAllowed(raw)) { integrityIssues.add("chat_" + id); return false; }
        List<Message> all = messages(id);
        if (index < 0 || index >= all.size()) return false;
        return writeMessages(id, new ArrayList<>(all.subList(0, index)));
    }

    void clearMessages(String id) {
        preferences.edit().remove("chat_" + id).apply();
        chatCache.remove(id);
        integrityIssues.remove("chat_" + id); // user-initiated clear repairs the slot
    }
    private boolean writeMessages(String id, List<Message> all) {
        String encoded = PersistenceCodec.encodeMessages(all);
        if (encoded.isEmpty()) return false; // encode failure must not replace valid data
        preferences.edit().putString("chat_" + id, encoded).apply();
        chatCache.put(id, all);
        return true;
    }
    private boolean putCharacters(List<CharacterProfile> all) {
        String encoded = PersistenceCodec.encodeCharacters(all);
        if (encoded.isEmpty()) return false;
        preferences.edit().putString("characters", encoded).apply();
        return true;
    }

    boolean restoreBundle(List<CharacterProfile> characters, List<Group> groups,
                          java.util.Map<String, List<Message>> chats) {
        return restoreBundle(characters, groups, chats, null, null, null, null);
    }

    boolean restoreBundle(List<CharacterProfile> characters, List<Group> groups,
                          java.util.Map<String, List<Message>> chats,
                          java.util.Map<String, List<Lorebook.Entry>> lorebooks) {
        return restoreBundle(characters, groups, chats, lorebooks, null, null, null);
    }

    /**
     * User-initiated backup restore. Refuses while any catalog is unreadable
     * (the repair path is still the startup report / chat clear), merges by id,
     * and replaces a transcript only when the imported copy is strictly longer.
     * Lorebook entries (DeepSeek item 12) travel with the bundle; non-empty
     * imported lore replaces this conversation's stored book, empty drops nothing.
     * v0.19.18: personas merge by id; the imported default persona applies when it
     * exists after the merge, otherwise the local default is kept; per-chat overrides
     * apply only to restored chats whose referenced persona exists after the merge.
     */
    boolean restoreBundle(List<CharacterProfile> characters, List<Group> groups,
                          java.util.Map<String, List<Message>> chats,
                          java.util.Map<String, List<Lorebook.Entry>> lorebooks,
                          List<Persona> personas, String importedDefaultPersonaId,
                          java.util.Map<String, String> importedChatPersonas) {
        String rawChars = preferences.getString("characters", null);
        String rawGroups = preferences.getString("groups", null);
        if (!PersistenceCodec.overwriteAllowed(rawChars) || !PersistenceCodec.overwriteAllowed(rawGroups)) {
            integrityIssues.add(PersistenceCodec.overwriteAllowed(rawChars) ? "groups" : "characters");
            return false;
        }
        List<CharacterProfile> mergedChars = PersistenceCodec.mergeCharacters(
                PersistenceCodec.decodeCharacters(rawChars).items, characters);
        List<Group> mergedGroups = PersistenceCodec.mergeGroups(
                PersistenceCodec.decodeGroups(rawGroups).items, groups);
        java.util.Map<String, List<Message>> importedChats = new java.util.HashMap<>();
        java.util.Set<String> knownGroups = new java.util.HashSet<>();
        for (Group g : mergedGroups) knownGroups.add(g.id);
        for (java.util.Map.Entry<String, List<Message>> entry : chats.entrySet()) {
            if (knownGroups.contains(entry.getKey())) importedChats.put(entry.getKey(), entry.getValue());
        }
        java.util.Map<String, List<Message>> mergedChats = new java.util.HashMap<>();
        for (String id : knownGroups) {
            String storedRaw = preferences.getString("chat_" + id, "[]");
            PersistenceCodec.Decode<Message> storedDecode = PersistenceCodec.decodeMessages(storedRaw);
            List<Message> incoming = importedChats.get(id);
            // Codex bug-hunt v0.26.00: a corrupt local payload must never be re-encoded
            // over itself. The pure decision returns null → keep the raw bytes and
            // surface it through integrity reporting; only a strictly longer incoming
            // transcript (a good backup) may repair it.
            List<Message> decision = PersistenceCodec.mergeStoredTranscript(storedDecode, incoming);
            if (decision == null) {
                integrityIssues.add("chat_" + id);
                continue;
            }
            mergedChats.put(id, decision);
        }
        String encodedChars = PersistenceCodec.encodeCharacters(mergedChars);
        String encodedGroups = PersistenceCodec.encodeGroups(mergedGroups);
        if (encodedChars.isEmpty() || encodedGroups.isEmpty()) return false;
        android.content.SharedPreferences.Editor editor = preferences.edit();
        editor.putString("characters", encodedChars).putString("groups", encodedGroups);
        List<Persona> mergedPersonas = null;
        if (personas != null && !personas.isEmpty()) {
            mergedPersonas = PersistenceCodec.mergePersonas(personas(), personas);
            String encodedPersonas = PersistenceCodec.encodePersonas(mergedPersonas);
            if (encodedPersonas.isEmpty()) return false;
            editor.putString("user_personas", encodedPersonas);
            // Imported default wins only when it still names a persona after the merge;
            // otherwise the local default preference is left untouched.
            if (importedDefaultPersonaId != null && !importedDefaultPersonaId.isEmpty()) {
                for (Persona p : mergedPersonas) {
                    if (p.id.equals(importedDefaultPersonaId)) {
                        editor.putString("default_persona_id", importedDefaultPersonaId);
                        break;
                    }
                }
            }
        }
        if (importedChatPersonas != null && !importedChatPersonas.isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : importedChatPersonas.entrySet()) {
                if (!knownGroups.contains(entry.getKey())) continue;
                String personaId = entry.getValue();
                if (personaId == null || personaId.isEmpty()) continue;
                // An override is applied only when its persona exists after the merge,
                // so a restore can never point a chat at an identity that cannot resolve.
                // The merged list (not the not-yet-applied store) is authoritative here.
                boolean exists = false;
                if (mergedPersonas != null) {
                    for (Persona p : mergedPersonas) {
                        if (p.id.equals(personaId)) { exists = true; break; }
                    }
                }
                if (exists) editor.putString("chat_persona_" + entry.getKey(), personaId);
            }
        }
        for (java.util.Map.Entry<String, List<Message>> entry : mergedChats.entrySet()) {
            String encoded = PersistenceCodec.encodeMessages(entry.getValue());
            if (encoded.isEmpty()) return false;
            editor.putString("chat_" + entry.getKey(), encoded);
            chatCache.put(entry.getKey(), entry.getValue());
            integrityIssues.remove("chat_" + entry.getKey());
        }
        if (lorebooks != null) {
            for (java.util.Map.Entry<String, List<Lorebook.Entry>> entry : lorebooks.entrySet()) {
                if (!knownGroups.contains(entry.getKey())) continue;
                List<Lorebook.Entry> imported = entry.getValue();
                if (imported == null || imported.isEmpty()) continue;
                editor.putString("lore_" + entry.getKey(), Lorebook.encode(imported));
            }
        }
        editor.apply();
        return true;
    }
}
