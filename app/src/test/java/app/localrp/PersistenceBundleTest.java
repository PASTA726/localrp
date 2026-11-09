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
        assertNull(PersistenceCodec.decodeBundle("{}")); // no version marker at all
        assertNull(PersistenceCodec.decodeBundle("{\"version\":0}"));
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

    // ---- backup v2: media markers, SHA-256, version gating ----

    @Test
    public void mediaMarkers_roundTripBetweenAbsolutePathAndMarker() {
        assertEquals("@avatars/ava-1.png",
                PersistenceCodec.toMediaRelative("/data/user/0/app.localrp/files/avatars/ava-1.png",
                        "/data/user/0/app.localrp/files/avatars",
                        "/data/user/0/app.localrp/files/models/images"));
        assertEquals("@images/gen-3.png",
                PersistenceCodec.toMediaRelative("/data/user/0/app.localrp/files/models/images/gen-3.png",
                        "/data/user/0/app.localrp/files/avatars",
                        "/data/user/0/app.localrp/files/models/images"));
        // Outside both roots, empty and null inputs must all degrade to "".
        assertEquals("", PersistenceCodec.toMediaRelative("/storage/emulated/0/DCIM/pic.jpg",
                "/data/user/0/app.localrp/files/avatars",
                "/data/user/0/app.localrp/files/models/images"));
        assertEquals("", PersistenceCodec.toMediaRelative("/data/user/0/app.localrp/files",
                "/data/user/0/app.localrp/files/avatars",
                "/data/user/0/app.localrp/files/models/images"));
        assertEquals("", PersistenceCodec.toMediaRelative("", "/r/avatars", "/r/images"));
        assertEquals("", PersistenceCodec.toMediaRelative(null, "/r/avatars", "/r/images"));
    }

    @Test
    public void mediaMarkers_resolve_withTraversalProtection() {
        String avatars = "/data/user/0/app.localrp/files/avatars";
        String images = "/data/user/0/app.localrp/files/models/images";
        assertEquals(avatars + "/ava-1.png",
                PersistenceCodec.resolveMediaPath("@avatars/ava-1.png", avatars, images));
        assertEquals(images + "/gen-3.png",
                PersistenceCodec.resolveMediaPath("@images/gen-3.png", avatars, images));
        // traversal, nested segments, empty and unknown spaces collapse to nothing
        assertEquals("", PersistenceCodec.resolveMediaPath("@avatars/../x.png", avatars, images));
        assertEquals("", PersistenceCodec.resolveMediaPath("@avatars/a/b.png", avatars, images));
        assertEquals("", PersistenceCodec.resolveMediaPath("@avatars/", avatars, images));
        assertEquals("", PersistenceCodec.resolveMediaPath("@other/x.png", avatars, images));
        assertEquals("", PersistenceCodec.resolveMediaPath("@avatars", avatars, images));
        // version-1 absolute paths pass through unchanged, and empty stays empty
        assertEquals("/sdcard/pic.png", PersistenceCodec.resolveMediaPath("/sdcard/pic.png", avatars, images));
        assertEquals("", PersistenceCodec.resolveMediaPath("", avatars, images));
    }

    @Test
    public void mediaMarkers_rejectBackslashAndDotNames(){
        String avatars = "/r/avatars";
        String images = "/r/images";
        assertEquals("", PersistenceCodec.toMediaRelative("/r/avatars/a\\b.png", avatars, images));
        assertEquals("", PersistenceCodec.toMediaRelative("/r/avatars/..", avatars, images));
        assertEquals("", PersistenceCodec.toMediaRelative("/r/images/x\\y.png", avatars, images));
    }

    @Test
    public void mediaDigestHex_coversNamesAndBytesDeterministically() {
        assertTrue(PersistenceCodec.mediaDigestHex(new java.util.TreeMap<>())
                .equals(PersistenceCodec.sha256Hex(new byte[0])));
        java.util.SortedMap<String, byte[]> one = new java.util.TreeMap<>();
        one.put("media/avatars/a.png", new byte[]{'x'});
        assertEquals(
                PersistenceCodec.sha256Hex("media/avatars/a.png:x".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                PersistenceCodec.mediaDigestHex(one));
        // different byte content must flip the digest
        java.util.SortedMap<String, byte[]> other = new java.util.TreeMap<>();
        other.put("media/avatars/a.png", new byte[]{'y'});
        assertFalse(PersistenceCodec.mediaDigestHex(one).equals(PersistenceCodec.mediaDigestHex(other)));
    }

    @Test
    public void sha256Hex_matchesKnownDigest() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                PersistenceCodec.sha256Hex("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(64, PersistenceCodec.sha256Hex(new byte[0]).length());
    }

    @Test
    public void bundleV2_keepsMediaMarkersWithAvatarIncluded() {
        PersistenceCodec.Bundle bundle = new PersistenceCodec.Bundle();
        bundle.version = 2;
        bundle.avatarsRoot = "/data/local/files/avatars";
        bundle.imagesRoot = "/data/local/files/images";
        CharacterProfile c = character("c1", "Elif");
        c.avatarPath = "/data/local/files/avatars/ava-1.png";
        bundle.characters.add(c);
        bundle.groups.add(group("g1"));
        List<Message> transcript = new ArrayList<>();
        transcript.add(new Message("Sen", "", true, 1700_000_000_000L, "/data/local/files/images/gen-1.png"));
        bundle.chats.put("g1", transcript);

        String encoded = PersistenceCodec.encodeBundle(bundle);
        assertTrue(encoded.contains("\"version\":2"));
        assertTrue(encoded.contains("@avatars/ava-1.png"));
        assertTrue(encoded.contains("@images/gen-1.png"));
        // Absolute device paths must never travel in a v2 payload.
        assertFalse(encoded.contains("/data/local/files/avatars/ava-1.png"));
        assertFalse(encoded.contains("/data/local/files/images/gen-1.png"));

        PersistenceCodec.Bundle decoded = PersistenceCodec.decodeBundle(encoded);
        assertNotNull(decoded);
        assertEquals(2, decoded.version);
        assertEquals("@avatars/ava-1.png", decoded.characters.get(0).avatarPath);
        assertEquals("@images/gen-1.png", decoded.chats.get("g1").get(0).image);
    }

    @Test
    public void bundleV1_stillDecodesWithAbsolutePath() {
        PersistenceCodec.Bundle bundle = new PersistenceCodec.Bundle();
        bundle.characters.add(character("c1", "Elif"));
        bundle.groups.add(group("g1"));
        List<Message> transcript = new ArrayList<>();
        transcript.add(new Message("Sen", "", true, 1700_000_000_000L, "/data/local/images/gen-1.png"));
        bundle.chats.put("g1", transcript);

        String encoded = PersistenceCodec.encodeBundle(bundle); // default version = 1
        assertTrue(encoded.contains("\"version\":1"));
        assertTrue(encoded.contains("/data/local/images/gen-1.png"));

        PersistenceCodec.Bundle decoded = PersistenceCodec.decodeBundle(encoded);
        assertNotNull(decoded);
        assertEquals(1, decoded.version);
        assertEquals("/data/local/images/gen-1.png", decoded.chats.get("g1").get(0).image);
        assertEquals("", decoded.characters.get(0).avatarPath); // v1 packs no avatar key
    }

    @Test
    public void decodeBundle_rejectsUnknownVersions() {
        assertNull(PersistenceCodec.decodeBundle("{\"version\":3,\"characters\":[],\"groups\":[],\"chats\":{}}"));
        assertNull(PersistenceCodec.decodeBundle("{\"characters\":[],\"groups\":[],\"chats\":{}}"));
    }

    @Test
    public void bundleV2_withoutRootsDropsMediaSafely() {
        PersistenceCodec.Bundle bundle = new PersistenceCodec.Bundle();
        bundle.version = 2; // roots left null: defensive encode must not leak absolute paths
        CharacterProfile c = character("c1", "Elif");
        c.avatarPath = "/data/local/files/avatars/ava-1.png";
        bundle.characters.add(c);
        List<Message> transcript = new ArrayList<>();
        transcript.add(new Message("Sen", "", true, 1L, "/data/local/files/images/gen-1.png"));
        bundle.chats.put("g1", transcript);

        String encoded = PersistenceCodec.encodeBundle(bundle);
        assertFalse(encoded.contains("ava-1.png"));
        assertFalse(encoded.contains("gen-1.png"));
        PersistenceCodec.Bundle decoded = PersistenceCodec.decodeBundle(encoded);
        assertNotNull(decoded);
        assertEquals("", decoded.characters.get(0).avatarPath);
        assertEquals("", decoded.chats.get("g1").get(0).image);
    }
}
