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
    public void bundleRoundTrip_unicodeSurvivesEveryField() {
        // v0.27.00: this app's own corruption scans exist because non-ASCII text is
        // a risk surface — assert emoji, Turkish, CJK, newlines and quotes survive
        // the full v2 bundle round trip in every text field they can appear in.
        PersistenceCodec.Bundle bundle = new PersistenceCodec.Bundle();
        CharacterProfile c = character("c1", "Elif 🎭");
        c.description = "Denizci — \"kaptan\"\n2. satır ✨ 日本語も";
        c.personality = "Atılgan… 🤔 'alıntı' \\ters\\";
        c.instructions = "Türkçe konuş ⇄ 简体中文";
        c.examples = "Elif: (E+ 😀 → 🙂) emoji testi";
        bundle.characters.add(c);
        bundle.groups.add(group("g1"));
        List<Message> transcript = new ArrayList<>();
        transcript.add(new Message("Elif 😀", "merhaba\nikinci satır 🌊", false, 1L));
        transcript.add(new Message("Sen", "сюрприз 『括弧』 \"tırnak\"", true, 2L));
        bundle.chats.put("g1", transcript);

        String encoded = PersistenceCodec.encodeBundle(bundle);
        PersistenceCodec.Bundle decoded = PersistenceCodec.decodeBundle(encoded);
        assertNotNull(decoded);
        assertEquals(1, decoded.characters.size());
        CharacterProfile out = decoded.characters.get(0);
        assertEquals("Elif 🎭", out.name);
        assertEquals("Denizci — \"kaptan\"\n2. satır ✨ 日本語も", out.description);
        assertEquals("Atılgan… 🤔 'alıntı' \\ters\\", out.personality);
        assertEquals("Türkçe konuş ⇄ 简体中文", out.instructions);
        assertEquals("Elif: (E+ 😀 → 🙂) emoji testi", out.examples);
        List<Message> messages = decoded.chats.get("g1");
        assertEquals(2, messages.size());
        assertEquals("Elif 😀", messages.get(0).speaker);
        assertEquals("merhaba\nikinci satır 🌊", messages.get(0).text);
        assertEquals("сюрприз 『括弧』 \"tırnak\"", messages.get(1).text);
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

    // ---- selective export (DeepSeek item 9b) ----

    @Test
    public void selectBundle_keepsChosenChatsAndTheirCharacters() {
        PersistenceCodec.Bundle source = new PersistenceCodec.Bundle();
        source.characters.add(character("c1", "Elif"));
        source.characters.add(character("c2", "Kaan"));
        source.characters.add(character("c3", "Derin"));
        Group kept = group("g1");          // references c1 (from group()/default setup)
        Group dropped = group("g2");
        dropped.characterIds.clear();
        dropped.characterIds.add("c2");
        source.groups.add(kept);
        source.groups.add(dropped);
        source.chats.put("g1", new ArrayList<>(java.util.Collections.singletonList(
                new Message("Elif", "selam", false, 1L))));
        source.chats.put("g2", new ArrayList<>(java.util.Collections.singletonList(
                new Message("Kaan", "hoş geldin", false, 2L))));

        // The user explicitly picks only c3; c1 must auto-attach because g1 references
        // it, and c2 (whose only membership is the dropped g2) must stay out.
        PersistenceCodec.Bundle selected = PersistenceCodec.selectBundle(
                source, new java.util.HashSet<>(java.util.Collections.singletonList("c3")),
                new java.util.HashSet<>(java.util.Collections.singletonList("g1")));
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (CharacterProfile c : selected.characters) ids.add(c.id);
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("c1", "c3")), ids);
        assertEquals(1, selected.groups.size());
        assertEquals("g1", selected.groups.get(0).id);
        assertEquals(1, selected.chats.size());
        assertTrue(selected.chats.containsKey("g1"));
    }

    @Test
    public void selectBundle_autoIncludesCharactersReferencedByChat() {
        PersistenceCodec.Bundle source = new PersistenceCodec.Bundle();
        source.characters.add(character("c1", "Elif"));
        Group kept = group("g1"); // references c1 although the user only picked the chat
        source.groups.add(kept);
        source.chats.put("g1", new ArrayList<>());

        PersistenceCodec.Bundle selected = PersistenceCodec.selectBundle(
                source, new java.util.HashSet<>(),
                new java.util.HashSet<>(java.util.Collections.singletonList("g1")));
        assertEquals(1, selected.characters.size());
        assertEquals("c1", selected.characters.get(0).id);
        assertEquals(1, selected.groups.size());
    }

    @Test
    public void selectBundle_emptySelectionProducesEmptyBundle() {
        PersistenceCodec.Bundle source = new PersistenceCodec.Bundle();
        source.characters.add(character("c1", "Elif"));
        source.groups.add(group("g1"));
        source.chats.put("g1", new ArrayList<>());

        PersistenceCodec.Bundle selected = PersistenceCodec.selectBundle(
                source, new java.util.HashSet<>(), new java.util.HashSet<>());
        assertTrue(selected.characters.isEmpty());
        assertTrue(selected.groups.isEmpty());
        assertTrue(selected.chats.isEmpty());
        assertEquals(source.version, selected.version);
    }

    @Test
    public void selectBundle_reducesWorldBookToKeptGroups() {
        PersistenceCodec.Bundle source = new PersistenceCodec.Bundle();
        source.groups.add(group("g1"));
        source.groups.add(group("g2"));
        source.chats.put("g1", new ArrayList<>());
        source.chats.put("g2", new ArrayList<>());
        List<Lorebook.Entry> keptLore = new ArrayList<>();
        keptLore.add(new Lorebook.Entry("kız kulesi", "efsanenin merkezi"));
        List<Lorebook.Entry> droppedLore = new ArrayList<>();
        droppedLore.add(new Lorebook.Entry("kral", "kuzeyin kadim yöneticisi"));
        source.lorebooks.put("g1", keptLore);
        source.lorebooks.put("g2", droppedLore);

        PersistenceCodec.Bundle selected = PersistenceCodec.selectBundle(
                source, new java.util.HashSet<>(),
                new java.util.HashSet<>(java.util.Collections.singletonList("g1")));
        assertEquals(1, selected.lorebooks.size());
        assertEquals("efsanenin merkezi", selected.lorebooks.get("g1").get(0).text);
        assertFalse(selected.lorebooks.containsKey("g2"));
    }

    @Test
    public void bundle_roundTripsWorldBookEntries() {
        PersistenceCodec.Bundle source = new PersistenceCodec.Bundle();
        source.characters.add(character("c1", "Elif"));
        source.groups.add(group("g1"));
        source.chats.put("g1", new ArrayList<>());
        List<Lorebook.Entry> lore = new ArrayList<>();
        lore.add(new Lorebook.Entry("kız kulesi, fener", "Kulede bir bekçi yaşar."));
        source.lorebooks.put("g1", lore);

        PersistenceCodec.Bundle decoded = PersistenceCodec.decodeBundle(
                PersistenceCodec.encodeBundle(source));
        assertEquals(1, decoded.lorebooks.get("g1").size());
        assertEquals("Kulede bir bekçi yaşar.", decoded.lorebooks.get("g1").get(0).text);

        // A lore-less bundle keeps decoding cleanly and simply reads back with no book.
        PersistenceCodec.Bundle plain = new PersistenceCodec.Bundle();
        plain.groups.add(group("g2"));
        PersistenceCodec.Bundle plainDecoded = PersistenceCodec.decodeBundle(
                PersistenceCodec.encodeBundle(plain));
        assertTrue(plainDecoded.lorebooks.isEmpty());
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

    // ---- v0.19.18 personas in the bundle ----

    @Test
    public void bundle_roundTripsPersonasAndOverrides() {
        PersistenceCodec.Bundle bundle = new PersistenceCodec.Bundle();
        Group g = new Group();
        g.id = "g1";
        g.name = "RP";
        bundle.groups.add(g);
        bundle.chats.put("g1", new ArrayList<>());
        bundle.personas.add(new Persona("p1", "Evren", "Sessiz bahçıvan", ""));
        bundle.personas.add(new Persona("p2", "Denizci", "", ""));
        bundle.defaultPersonaId = "p2";
        bundle.chatPersonas.put("g1", "p1");

        String encoded = PersistenceCodec.encodeBundle(bundle);
        PersistenceCodec.Bundle decoded = PersistenceCodec.decodeBundle(encoded);
        assertNotNull(decoded);
        assertEquals(2, decoded.personas.size());
        assertEquals("Evren", decoded.personas.get(0).name);
        assertEquals("p2", decoded.defaultPersonaId);
        assertEquals("p1", decoded.chatPersonas.get("g1"));
    }

    @Test
    public void bundle_v2PersonaAvatarBecomesMarker() {
        PersistenceCodec.Bundle bundle = new PersistenceCodec.Bundle();
        bundle.version = 2;
        bundle.avatarsRoot = "/data/local/files/avatars";
        bundle.personas.add(new Persona("p1", "Evren", "", "/data/local/files/avatars/pic-9.png"));

        String encoded = PersistenceCodec.encodeBundle(bundle);
        assertTrue(encoded.contains("@avatars/pic-9.png"));
        assertFalse(encoded.contains("/data/local/files/avatars"));
        PersistenceCodec.Bundle decoded = PersistenceCodec.decodeBundle(encoded);
        assertEquals("@avatars/pic-9.png", decoded.personas.get(0).avatarPath);
        // Resolve back through the standard media resolver.
        assertEquals("/data/local/files/avatars/pic-9.png",
                PersistenceCodec.resolveMediaPath(decoded.personas.get(0).avatarPath,
                        "/data/local/files/avatars", "/data/local/files/images"));
    }

    @Test
    public void bundle_toleratesBrokenPersonaSections() {
        PersistenceCodec.Bundle decoded = PersistenceCodec.decodeBundle(
                "{\"version\":1,\"characters\":[],\"groups\":[],\"chats\":{},"
                        + "\"personas\":[{\"name\":\"idsiz\"},{\"id\":\"p1\",\"name\":\"Tam\"},42],"
                        + "\"chatPersonas\":{\"g1\":\"\"}}");
        assertNotNull(decoded);
        assertEquals(1, decoded.personas.size());
        assertEquals("Tam", decoded.personas.get(0).name);
        assertTrue(decoded.chatPersonas.isEmpty()); // empty values are never exported
        // Missing persona sections (older backups) decode to empty, never null.
        PersistenceCodec.Bundle legacy = PersistenceCodec.decodeBundle(
                "{\"version\":1,\"characters\":[],\"groups\":[],\"chats\":{}}");
        assertNotNull(legacy);
        assertTrue(legacy.personas.isEmpty());
        assertEquals("", legacy.defaultPersonaId);
    }

    @Test
    public void selectBundle_keepsAllPersonas_filtersOverrides() {
        PersistenceCodec.Bundle source = new PersistenceCodec.Bundle();
        Group kept = new Group();
        kept.id = "g1";
        Group dropped = new Group();
        dropped.id = "g2";
        source.groups.add(kept);
        source.groups.add(dropped);
        source.chats.put("g1", new ArrayList<>());
        source.chats.put("g2", new ArrayList<>());
        source.personas.add(new Persona("p1", "Evren", "", ""));
        source.defaultPersonaId = "p1";
        source.chatPersonas.put("g1", "p1");
        source.chatPersonas.put("g2", "p1");

        PersistenceCodec.Bundle selected = PersistenceCodec.selectBundle(source,
                new java.util.HashSet<>(), new java.util.HashSet<>(java.util.Collections.singletonList("g1")));
        assertEquals(1, selected.personas.size());
        assertEquals("p1", selected.defaultPersonaId);
        assertTrue(selected.chatPersonas.containsKey("g1"));
        assertFalse(selected.chatPersonas.containsKey("g2"));
    }

    @Test
    public void mergePersonas_updatesById_andAppendsNew() {
        List<Persona> stored = new ArrayList<>();
        stored.add(new Persona("p1", "Eski ad", "", ""));
        List<Persona> imported = new ArrayList<>();
        imported.add(new Persona("p1", "Yeni ad", "yeni", ""));
        imported.add(new Persona("p2", "Yeni kişi", "", ""));
        imported.add(new Persona("", "idsiz reddedilir", "", ""));

        List<Persona> merged = PersistenceCodec.mergePersonas(stored, imported);
        assertEquals(2, merged.size());
        assertEquals("Yeni ad", merged.get(0).name);
        assertEquals("yeni", merged.get(0).description);
        assertEquals("p2", merged.get(1).id);
    }
}
