package app.localrp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure decision helpers for the v0.20.00 home screen: how many conversations a
 * character appears in, whether those chats are group or solo, and how the grid
 * reacts to the search pill and the usage filter chips. No Android imports —
 * JVM-tested in {@code HomeCardsTest}.
 */
final class HomeCards {
    private HomeCards() {
    }

    /** Number of conversations that feature the character. */
    static int chatCount(String characterId, List<Group> groups) {
        if (characterId == null || groups == null) return 0;
        int count = 0;
        for (Group group : groups) {
            if (group != null && group.characterIds != null && group.characterIds.contains(characterId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * "grup" when the character shares at least one conversation with someone,
     * "tek" when it only appears in solo chats, {@code null} when it has no chat.
     */
    static String usageTag(String characterId, List<Group> groups) {
        if (characterId == null || groups == null) return null;
        boolean solo = false;
        for (Group group : groups) {
            if (group == null || group.characterIds == null || !group.characterIds.contains(characterId)) continue;
            if (group.characterIds.size() > 1) return "grup";
            solo = true;
        }
        return solo ? "tek" : null;
    }

    /**
     * Home grid filter: a search query (name or description, case-insensitive,
     * locale-independent) and a usage chip ("grup", "tek" or anything else for all).
     */
    static List<CharacterProfile> filterCharacters(List<CharacterProfile> characters,
                                                   List<Group> groups, String query, String chip) {
        List<CharacterProfile> result = new ArrayList<>();
        if (characters == null) return result;
        String q = lower(query);
        for (CharacterProfile character : characters) {
            if (character == null) continue;
            if (!q.isEmpty()) {
                String name = lower(character.name);
                String desc = lower(character.description);
                if (!name.contains(q) && !desc.contains(q)) continue;
            }
            if ("grup".equals(chip) && !"grup".equals(usageTag(character.id, groups))) continue;
            if ("tek".equals(chip) && !"tek".equals(usageTag(character.id, groups))) continue;
            result.add(character);
        }
        return result;
    }

    /**
     * Locale-independent lowercase for matching. Turkish capital İ keeps a combining
     * dot after {@code Locale.ROOT} lowering ("ELİF" → "eli̇f"); stripping U+0307
     * makes a plain query ("elif") match again without locale-dependent behaviour.
     */
    private static String lower(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase(Locale.ROOT).replace("\u0307", "");
    }
}
