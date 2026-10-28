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
    boolean saveCharacter(CharacterProfile edited) {
        String raw = preferences.getString("characters", null);
        if (!PersistenceCodec.overwriteAllowed(raw)) { integrityIssues.add("characters"); return false; }
        List<CharacterProfile> all = PersistenceCodec.decodeCharacters(raw).items;
        boolean found = false;
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(edited.id)) { all.set(i, edited); found = true; }
        if (!found) all.add(edited);
        return putCharacters(all);
    }
    boolean deleteCharacter(String id) {
        if (defaultCharacterId.equals(id)) return true;
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
    void clearMessages(String id) {
        preferences.edit().remove("chat_" + id).apply();
        chatCache.remove(id);
        integrityIssues.remove("chat_" + id); // user-initiated clear repairs the slot
    }
    private void writeMessages(String id, List<Message> all) {
        String encoded = PersistenceCodec.encodeMessages(all);
        if (encoded.isEmpty()) return; // encode failure must not replace valid data
        preferences.edit().putString("chat_" + id, encoded).apply();
        chatCache.put(id, all);
    }
    private boolean putCharacters(List<CharacterProfile> all) {
        String encoded = PersistenceCodec.encodeCharacters(all);
        if (encoded.isEmpty()) return false;
        preferences.edit().putString("characters", encoded).apply();
        return true;
    }

    /**
     * User-initiated backup restore. Refuses while any catalog is unreadable
     * (the repair path is still the startup report / chat clear), merges by id,
     * and replaces a transcript only when the imported copy is strictly longer.
     */
    boolean restoreBundle(List<CharacterProfile> characters, List<Group> groups,
                          java.util.Map<String, List<Message>> chats) {
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
            List<Message> stored = PersistenceCodec.decodeMessages(preferences.getString("chat_" + id, "[]")).items;
            List<Message> incoming = importedChats.get(id);
            mergedChats.put(id, incoming != null && incoming.size() > stored.size() ? incoming : stored);
        }
        String encodedChars = PersistenceCodec.encodeCharacters(mergedChars);
        String encodedGroups = PersistenceCodec.encodeGroups(mergedGroups);
        if (encodedChars.isEmpty() || encodedGroups.isEmpty()) return false;
        android.content.SharedPreferences.Editor editor = preferences.edit();
        editor.putString("characters", encodedChars).putString("groups", encodedGroups);
        for (java.util.Map.Entry<String, List<Message>> entry : mergedChats.entrySet()) {
            String encoded = PersistenceCodec.encodeMessages(entry.getValue());
            if (encoded.isEmpty()) return false;
            editor.putString("chat_" + entry.getKey(), encoded);
            chatCache.put(entry.getKey(), entry.getValue());
            integrityIssues.remove("chat_" + entry.getKey());
        }
        editor.apply();
        return true;
    }
}
