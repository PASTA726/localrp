package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Home grid decisions for the v0.20.00 home screen: counts, usage tags, filters. */
public class HomeCardsTest {
    private Group chat(String characterId, String... moreIds) {
        Group group = new Group();
        group.characterIds.add(characterId);
        group.characterIds.addAll(Arrays.asList(moreIds));
        return group;
    }

    private CharacterProfile character(String id, String name, String description) {
        CharacterProfile profile = new CharacterProfile();
        profile.id = id;
        profile.name = name;
        profile.description = description;
        return profile;
    }

    @Test
    public void chatCount_countsEveryConversationFeaturingTheCharacter() {
        List<Group> groups = Arrays.asList(chat("elif"), chat("mira", "elif"), chat("sarp"));
        assertEquals(2, HomeCards.chatCount("elif", groups));
        assertEquals(1, HomeCards.chatCount("sarp", groups));
        assertEquals(0, HomeCards.chatCount("yok", groups));
    }

    @Test
    public void chatCount_toleratesNullInputs() {
        assertEquals(0, HomeCards.chatCount(null, new ArrayList<>()));
        assertEquals(0, HomeCards.chatCount("elif", null));
        Group broken = new Group();
        broken.characterIds = null;
        assertEquals(0, HomeCards.chatCount("elif", Arrays.asList(broken, null)));
    }

    @Test
    public void usageTag_distinguishesGroupSoloAndUnused() {
        List<Group> groups = Arrays.asList(chat("elif"), chat("mira", "elif"));
        assertEquals("grup", HomeCards.usageTag("elif", groups));
        assertEquals("grup", HomeCards.usageTag("mira", groups));
        assertEquals("tek", HomeCards.usageTag("sarp", Arrays.asList(chat("sarp"))));
        assertNull(HomeCards.usageTag("yok", groups));
    }

    @Test
    public void usageTag_toleratesNullInputs() {
        assertNull(HomeCards.usageTag(null, null));
        assertNull(HomeCards.usageTag("elif", null));
        Group broken = new Group();
        broken.characterIds = null;
        assertNull(HomeCards.usageTag("elif", Arrays.asList(broken)));
    }

    @Test
    public void filter_byQueryMatchesNameOrDescription() {
        List<CharacterProfile> characters = Arrays.asList(
                character("1", "Elif Kaya", "Kuzeyin kaptanı"),
                character("2", "Kaptan Mira", "Gün batımı korsanı"),
                character("3", "Dr. Sarp", "Gece vardiyası"));
        List<Group> groups = Arrays.asList(chat("1", "2"), chat("3"));
        assertEquals(2, HomeCards.filterCharacters(characters, groups, "kaptan", "all").size());
        assertEquals(1, HomeCards.filterCharacters(characters, groups, "elif", "all").size());
        assertEquals("Elif Kaya", HomeCards.filterCharacters(characters, groups, "ELİF", "all").get(0).name);
        assertEquals(0, HomeCards.filterCharacters(characters, groups, "robot", "all").size());
    }

    @Test
    public void filter_byUsageChip() {
        List<CharacterProfile> characters = Arrays.asList(
                character("1", "Elif", ""), character("2", "Mira", ""), character("3", "Sarp", ""));
        List<Group> groups = Arrays.asList(chat("1", "2"), chat("3"));
        List<CharacterProfile> grouped = HomeCards.filterCharacters(characters, groups, "", "grup");
        assertEquals(2, grouped.size());
        List<CharacterProfile> solo = HomeCards.filterCharacters(characters, groups, "", "tek");
        assertEquals(1, solo.size());
        assertEquals("Sarp", solo.get(0).name);
        assertEquals(3, HomeCards.filterCharacters(characters, groups, "", "all").size());
    }

    @Test
    public void filter_combinesQueryAndChip() {
        List<CharacterProfile> characters = Arrays.asList(
                character("1", "Elif", "kaptan"), character("2", "Mira", "kaptan"), character("3", "Sarp", ""));
        List<Group> groups = Arrays.asList(chat("1", "2"), chat("3"));
        List<CharacterProfile> both = HomeCards.filterCharacters(characters, groups, "kaptan", "tek");
        assertTrue(both.isEmpty());
    }

    @Test
    public void filter_toleratesNullInputsAndBlankQuery() {
        List<CharacterProfile> characters = Arrays.asList(
                character("1", "Elif", ""), null);
        List<Group> groups = Arrays.asList(chat("1"));
        assertEquals(1, HomeCards.filterCharacters(characters, groups, "   ", "all").size());
        assertEquals(0, HomeCards.filterCharacters(null, groups, "", "all").size());
        List<CharacterProfile> broken = new ArrayList<>();
        broken.add(new CharacterProfile());
        broken.get(0).name = null;
        // Null name must not crash; the query simply does not match it.
        assertEquals(0, HomeCards.filterCharacters(broken, groups, "elif", "all").size());
    }
}
