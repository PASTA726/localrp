package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for the SharedPreferences-persisted data codec (Task 03 scenarios):
 * valid legacy data, empty data, malformed JSON root, malformed individual records,
 * missing optional fields, and round trips for characters, groups and conversations.
 */
public class PersistenceCodecTest {

    // ---- characters ----

    @Test
    public void characterRoundTrip_keepsAllFields() {
        CharacterProfile c = new CharacterProfile();
        c.id = "c1";
        c.name = "Elif";
        c.description = "Denizci kaptan";
        c.personality = "Atılgan";
        c.instructions = "Kendine has gülüş";
        c.examples = "Elif: Rüzgar iyi.";
        String encoded = PersistenceCodec.encodeCharacters(java.util.Collections.singletonList(c));
        PersistenceCodec.Decode<CharacterProfile> out = PersistenceCodec.decodeCharacters(encoded);
        assertFalse(out.rootCorrupt);
        assertEquals(1, out.items.size());
        assertEquals("c1", out.items.get(0).id);
        assertEquals("Elif", out.items.get(0).name);
        assertEquals("Denizci kaptan", out.items.get(0).description);
        assertEquals("Atılgan", out.items.get(0).personality);
        assertEquals("Kendine has gülüş", out.items.get(0).instructions);
        assertEquals("Elif: Rüzgar iyi.", out.items.get(0).examples);
    }

    @Test
    public void characterDecode_missingOptionalFieldsYieldDefaults() {
        PersistenceCodec.Decode<CharacterProfile> out = PersistenceCodec.decodeCharacters(
                "[{\"id\":\"c2\",\"name\":\"Mira\"}]");
        assertFalse(out.rootCorrupt);
        assertEquals(1, out.items.size());
        assertEquals("Mira", out.items.get(0).name);
        assertEquals("", out.items.get(0).description);
        assertEquals("", out.items.get(0).instructions);
    }

    @Test
    public void characterDecode_emptyArrayIsEmptyCorruptFree() {
        PersistenceCodec.Decode<CharacterProfile> out = PersistenceCodec.decodeCharacters("[]");
        assertTrue(out.items.isEmpty());
        assertFalse(out.rootCorrupt);
        assertTrue(PersistenceCodec.overwriteAllowed("[]"));
    }

    @Test
    public void characterDecode_nullAndMissingValueTreatedAsEmpty() {
        PersistenceCodec.Decode<CharacterProfile> a = PersistenceCodec.decodeCharacters(null);
        PersistenceCodec.Decode<CharacterProfile> b = PersistenceCodec.decodeCharacters("[]");
        assertEquals(0, a.items.size());
        assertEquals(0, b.items.size());
        assertFalse(a.rootCorrupt);
        assertFalse(b.rootCorrupt);
    }

    @Test
    public void characterDecode_malformedRootFlagsCorruptionAndBlocksOverwrite() {
        PersistenceCodec.Decode<CharacterProfile> out = PersistenceCodec.decodeCharacters("{\"broken\":true");
        assertTrue(out.rootCorrupt);
        assertTrue(out.items.isEmpty());
        assertFalse(PersistenceCodec.overwriteAllowed("{\"broken\":true"));
        assertTrue(PersistenceCodec.overwriteAllowed((String) null));
    }

    @Test
    public void characterDecode_malformedRecordIsSkippedNotFatal() {
        // Record 2 is not an object: it must be dropped while records 1 and 3 survive.
        // Because a broken element also breaks the whole root parse, this lands in the
        // rescue path: items are salvaged, but the storage stays rootCorrupt (write-free).
        String raw = "[{\"id\":\"c1\",\"name\":\"A\"},{\"notAnObject\"},{\"id\":\"c3\",\"name\":\"C\"}]";
        PersistenceCodec.Decode<CharacterProfile> out = PersistenceCodec.decodeCharacters(raw);
        assertEquals(2, out.items.size());
        assertEquals(1, out.droppedRecords);
        assertTrue(out.rootCorrupt);
        assertFalse(PersistenceCodec.overwriteAllowed(raw));
    }

    @Test
    public void characterDecode_recordMissingRequiredFieldIsSkipped() {
        PersistenceCodec.Decode<CharacterProfile> out = PersistenceCodec.decodeCharacters(
                "[{\"name\":\"NoId\"},{\"id\":\"ok\",\"name\":\"Ok\"}]");
        assertEquals(1, out.items.size());
        assertEquals("Ok", out.items.get(0).name);
    }

    // ---- groups ----

    @Test
    public void groupRoundTrip_keepsIdsAndCharacters() {
        Group g = new Group();
        g.id = "g1";
        g.name = "Korsan gemisi";
        g.roleplay = true;
        g.characterIds.add("c1");
        g.characterIds.add("c2");
        String encoded = PersistenceCodec.encodeGroups(java.util.Collections.singletonList(g));
        PersistenceCodec.Decode<Group> out = PersistenceCodec.decodeGroups(encoded);
        assertFalse(out.rootCorrupt);
        assertEquals(1, out.items.size());
        assertEquals("Korsan gemisi", out.items.get(0).name);
        assertEquals(java.util.Arrays.asList("c1", "c2"), out.items.get(0).characterIds);
    }

    @Test
    public void groupDecode_missingRoleplayDefaultsTrue() {
        PersistenceCodec.Decode<Group> out = PersistenceCodec.decodeGroups(
                "[{\"id\":\"g2\",\"name\":\"Sahne\",\"characters\":[]}]");
        assertEquals(1, out.items.size());
        assertTrue(out.items.get(0).roleplay);
    }

    @Test
    public void groupDecode_malformedRootFlagsCorruption() {
        // A root value that is not an array at all must never be overwritten.
        String raw = "{\"broken\":true";
        PersistenceCodec.Decode<Group> out = PersistenceCodec.decodeGroups(raw);
        assertTrue(out.rootCorrupt);
        assertFalse(PersistenceCodec.overwriteAllowed(raw));
    }

    // ---- conversations ----

    @Test
    public void messageRoundTrip_keepsUserFlagAndTimestamp() {
        java.util.List<Message> list = new java.util.ArrayList<>();
        list.add(new Message("Sen", "merhaba", true, 1700_000_000_000L));
        list.add(new Message("Mira", "selam", false, 1700_000_100_000L));
        String encoded = PersistenceCodec.encodeMessages(list);
        PersistenceCodec.Decode<Message> out = PersistenceCodec.decodeMessages(encoded);
        assertEquals(2, out.items.size());
        assertTrue(out.items.get(0).user);
        assertEquals("Sen", out.items.get(0).speaker);
        assertEquals(1700_000_000_000L, out.items.get(0).at);
        assertFalse(out.items.get(1).user);
        assertEquals("Mira", out.items.get(1).speaker);
        assertEquals(1700_000_100_000L, out.items.get(1).at);
    }

    @Test
    public void messageDecode_recordWithoutTextIsDropped() {
        PersistenceCodec.Decode<Message> out = PersistenceCodec.decodeMessages(
                "[{\"speaker\":\"A\"},{\"speaker\":\"B\",\"text\":\"ok\",\"user\":true}]");
        assertEquals(1, out.items.size());
        assertEquals("B", out.items.get(0).speaker);
    }

    @Test
    public void messageDecode_malformedRootFlagsCorruption() {
        PersistenceCodec.Decode<Message> out = PersistenceCodec.decodeMessages("]nope[");
        assertTrue(out.rootCorrupt);
        assertTrue(PersistenceCodec.overwriteAllowed("[]"));
        assertFalse(PersistenceCodec.overwriteAllowed("]nope["));
    }

    @Test
    public void messageDecode_rescuesReadableRecordsWhenRootParseFails() {
        // The broken second element kills the root parse, so the rescue scanner must
        // salvage records 1 and 3; storage stays rootCorrupt (write-free).
        String raw = "[{\"speaker\":\"A\",\"text\":\"bir\",\"user\":true},{\"broken,\"},{\"speaker\":\"C\",\"text\":\"üç\",\"user\":false}]";
        PersistenceCodec.Decode<Message> out = PersistenceCodec.decodeMessages(raw);
        assertTrue(out.rootCorrupt);
        assertEquals(2, out.items.size());
        assertEquals("A", out.items.get(0).speaker);
        assertEquals("bir", out.items.get(0).text);
        assertTrue(out.items.get(0).user);
        assertEquals("C", out.items.get(1).speaker);
    }

    @Test
    public void characterDecode_rescuesRecordsAroundBrokenElement() {
        String raw = "[{\"id\":\"c1\",\"name\":\"A\",\"description\":\"tam\"},{\\},{\"id\":\"c3\",\"name\":\"C\"}]";
        PersistenceCodec.Decode<CharacterProfile> out = PersistenceCodec.decodeCharacters(raw);
        assertTrue(out.rootCorrupt);
        assertEquals(2, out.items.size());
        assertEquals("A", out.items.get(0).name);
        assertEquals("tam", out.items.get(0).description);
        assertEquals("C", out.items.get(1).name);
    }
}
