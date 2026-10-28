package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for the backup bundle (Task: backup/restore via local .json export).
 * Round trips, malformed bundle rejection, and the deterministic merge rules.
 * No device storage is touched by these tests.
 */
public class PersistenceBundleTest {

    private CharacterProfile character(String id, String name) {
        CharacterProfile c = new CharacterProfile();
        c.id = id;
        c.name = name;
        c.description = "tanım-" + id;
        return c;
    }

    private Group group(String id) {
        Group g = new Group();
        g.id = id;
        g.name = "Sahne-" + id;
        g.characterIds.add("c1");
        return g;
    }

    @Test
    public void bundleRoundTrip_keepsAllSections() {
        PersistenceCodec.Bundle bundle = new PersistenceCodec.Bundle();
        bundle.characters.add(character("c1", "Elif"));
        bundle.groups.add(group("g1"));
        List<Message> transcript = new ArrayList<>();
        transcript.add(new Message("Sen", "merhaba", true, 1700_000_000_000L));
        transcript.add(new Message("Elif", "selam!", false, 1700_000_100_000L));
        bundle.chats.put("g1", transcript);

        String encoded = PersistenceCodec.encodeBundle(bundle);
        PersistenceCodec.Bundle decoded = PersistenceCodec.decodeBundle(encoded);
        assertNotNull(decoded);
        assertEquals(1, decoded.characters.size());
        assertEquals("Elif", decoded.characters.get(0).name);
        assertEquals(1, decoded.groups.size());
        assertEquals("c1", decoded.groups.get(0).characterIds.get(0));
        assertEquals(2, decoded.chats.get("g1").size());
        assertTrue(decoded.chats.get("g1").get(0).user);
        assertEquals(1700_000_100_000L, decoded.chats.get("g1").get(1).at);
    }

    @Test
    public void decodeBundle_rejectsNonBackupValues() {
        assertNull(PersistenceCodec.decodeBundle(null));
        assertNull(PersistenceCodec.decodeBundle("[]")); // old array backups are not bundles
        assertNull(PersistenceCodec.decodeBundle("{\"version\":2}"));
        assertNull(PersistenceCodec.decodeBundle("{broken"));
    }

    @Test
    public void decodeBundle_skipsBrokenRecordsButSurvives() {
        // Transcript record without "text" is dropped; the rest of the bundle loads.
        String raw = "{\"version\":1,\"characters\":[{\"id\":\"c1\",\"name\":\"N\"}],"
                + "\"groups\":[{\"id\":\"g1\",\"name\":\"S\",\"characters\":[\"c1\"],\"roleplay\":false}],"
                + "\"chats\":{\"g1\":[{\"speaker\":\"Sen\",\"user\":true},{\"speaker\":\"N\",\"text\":\"ok\",\"user\":false}]}}";
        PersistenceCodec.Bundle decoded = PersistenceCodec.decodeBundle(raw);
        assertNotNull(decoded);
        assertEquals(1, decoded.characters.size());
        assertFalse(decoded.groups.get(0).roleplay);
        assertEquals(1, decoded.chats.get("g1").size());
        assertEquals("ok", decoded.chats.get("g1").get(0).text);
    }

    @Test
    public void mergeByIds_updatesExistingAndAppendsNew() {
        List<CharacterProfile> stored = new ArrayList<>();
        stored.add(character("c1", "Elif-eski"));
        stored.add(character("c2", "Mira"));
        List<CharacterProfile> imported = new ArrayList<>();
        imported.add(character("c1", "Elif-yeni"));
        imported.add(character("c3", "Zeynep"));
        List<CharacterProfile> merged = PersistenceCodec.mergeCharacters(stored, imported);
        assertEquals(3, merged.size());
        assertEquals("Elif-yeni", merged.get(0).name);
        assertEquals("Mira", merged.get(1).name);
        assertEquals("Zeynep", merged.get(2).name);

        List<Group> storedGroups = new ArrayList<>();
        storedGroups.add(group("g1"));
        List<Group> importedGroups = new ArrayList<>();
        importedGroups.add(group("g2"));
        List<Group> mergedGroups = PersistenceCodec.mergeGroups(storedGroups, importedGroups);
        assertEquals(2, mergedGroups.size());
    }

    @Test
    public void mergeChats_replacesOnlyLongerImportedTranscripts() {
        Map<String, List<Message>> stored = new HashMap<>();
        List<Message> shortStored = new ArrayList<>();
        shortStored.add(new Message("Sen", "bir", true));
        stored.put("g1", shortStored);

        Map<String, List<Message>> imported = new HashMap<>();
        List<Message> shorter = new ArrayList<>();
        shorter.add(new Message("Sen", "bir", true));
        imported.put("g1", shorter); // same material, must NOT duplicate or shrink
        List<Message> longer = new ArrayList<>();
        longer.add(new Message("Sen", "bir", true));
        longer.add(new Message("N", "iki", false, 1L));
        imported.put("g2", longer); // brand-new chat arrives fully
        List<Message> equal = new ArrayList<>();
        equal.add(new Message("Sen", "farklı", true));
        imported.put("g3", equal);

        Map<String, List<Message>> merged = PersistenceCodec.mergeChats(stored, imported);
        assertEquals(3, merged.size());
        assertEquals(1, merged.get("g1").size());
        assertEquals("bir", merged.get("g1").get(0).text);
        assertEquals(2, merged.get("g2").size());
        assertEquals(1, merged.get("g3").size()); // equal length: stored copy wins, no duplication
        assertEquals("farklı", merged.get("g3").get(0).text);
    }
}
