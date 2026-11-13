package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

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
        // v0.25.00: per-character sampler temperature survives the round trip.
        c.samplerTemp = "0.6";
        PersistenceCodec.Decode<CharacterProfile> withSampler =
                PersistenceCodec.decodeCharacters(PersistenceCodec.encodeCharacters(java.util.Collections.singletonList(c)));
        assertFalse(withSampler.rootCorrupt);
        assertEquals("0.6", withSampler.items.get(0).samplerTemp);
        // Unset stays empty, never a crash.
        c.samplerTemp = "";
        PersistenceCodec.Decode<CharacterProfile> withoutSampler =
                PersistenceCodec.decodeCharacters(PersistenceCodec.encodeCharacters(java.util.Collections.singletonList(c)));
        assertEquals("", withoutSampler.items.get(0).samplerTemp);
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

    @Test
    public void avatarField_roundTripsThroughCharacters() {
        List<CharacterProfile> items = new ArrayList<>();
        CharacterProfile c = new CharacterProfile();
        c.id = "c1";
        c.name = "Foto";
        c.avatarPath = "/data/app/avatars/pic-1.png";
        items.add(c);

        String raw = PersistenceCodec.encodeCharacters(items);
        assertTrue(raw.contains("\"avatar\""));
        PersistenceCodec.Decode<CharacterProfile> out = PersistenceCodec.decodeCharacters(raw);
        assertEquals(1, out.items.size());
        assertEquals("/data/app/avatars/pic-1.png", out.items.get(0).avatarPath);

        // Absence stays valid: an old record (no avatar key) decodes with an empty path.
        PersistenceCodec.Decode<CharacterProfile> legacy = PersistenceCodec.decodeCharacters(
                "[{\"id\":\"c2\",\"name\":\"Eski\"}]");
        assertEquals("", legacy.items.get(0).avatarPath);
    }

    @Test
    public void persona_legacyRecordDecodes_andFailsSoftly() {
        // The legacy single-persona key was produced by the v0.14–v0.15 app; nothing
        // writes it anymore, but the migration must keep decoding it faithfully.
        String legacy = "{\"name\":\"Evren\",\"description\":\"Sessiz bahçıvan\",\"avatar\":\"/x/p.png\"}";
        String[] persona = PersistenceCodec.decodePersona(legacy);
        assertEquals("Evren", persona[0]);
        assertEquals("Sessiz bahçıvan", persona[1]);
        assertEquals("/x/p.png", persona[2]);
        // Missing keys collapse to empty fields, never nulls.
        String[] sparse = PersistenceCodec.decodePersona("{\"name\":\"Sade\"}");
        assertEquals("Sade", sparse[0]);
        assertEquals("", sparse[1]);
        assertEquals("", sparse[2]);

        assertNull(PersistenceCodec.decodePersona(null));
        assertNull(PersistenceCodec.decodePersona(""));
        assertNull(PersistenceCodec.decodePersona("{garbage"));
    }

    // ---- v0.19.16 multi-persona codec ----

    @Test
    public void personas_roundTrip() {
        List<Persona> personas = new ArrayList<>();
        Persona first = new Persona("p1", "Evren", "Sessiz bahçıvan", "/x/a.png");
        Persona second = new Persona("p2", "Denizci", "Rüzgârlı ses", "");
        personas.add(first);
        personas.add(second);

        String raw = PersistenceCodec.encodePersonas(personas);
        PersistenceCodec.Decode<Persona> out = PersistenceCodec.decodePersonas(raw);
        assertEquals(0, out.droppedRecords);
        assertFalse(out.rootCorrupt);
        assertEquals(2, out.items.size());
        assertEquals("p1", out.items.get(0).id);
        assertEquals("Evren", out.items.get(0).name);
        assertEquals("Sessiz bahçıvan", out.items.get(0).description);
        assertEquals("/x/a.png", out.items.get(0).avatarPath);
        assertEquals("Denizci", out.items.get(1).name);
        assertEquals("", out.items.get(1).avatarPath);
    }

    @Test
    public void personas_decodeTolerantly_dropsBrokenRecords() {
        PersistenceCodec.Decode<Persona> out = PersistenceCodec.decodePersonas(
                "[{\"id\":\"p1\",\"name\":\"Tam\"},42,{\"name\":\"idsiz\"},{\"id\":\"p2\",\"name\":\"Devam\"}]");
        assertEquals(1, out.droppedRecords);
        assertEquals(2, out.items.size());
        assertEquals("Tam", out.items.get(0).name);
        assertEquals("p2", out.items.get(1).id);
    }

    @Test
    public void personas_emptyAndCorruptRootsFailSoftly() {
        assertTrue(PersistenceCodec.decodePersonas(null).items.isEmpty());
        assertTrue(PersistenceCodec.decodePersonas("").items.isEmpty());
        PersistenceCodec.Decode<Persona> corrupt = PersistenceCodec.decodePersonas("{not-an-array");
        assertTrue(corrupt.rootCorrupt);
        assertTrue(corrupt.items.isEmpty());
        // Encoding null-safe fields never throws; empty list round-trips to "[]".
        List<Persona> nulls = new ArrayList<>();
        nulls.add(new Persona(null, null, null, null));
        PersistenceCodec.Decode<Persona> out = PersistenceCodec.decodePersonas(
                PersistenceCodec.encodePersonas(nulls));
        assertTrue(out.items.isEmpty()); // id-less records are rejected by design
    }
}
