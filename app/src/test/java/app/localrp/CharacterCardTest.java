package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

/** SillyTavern character card rules: JSON mapping and PNG tEXt chunk storage. */
public class CharacterCardTest {
    private CharacterProfile profile() {
        CharacterProfile profile = new CharacterProfile();
        profile.name = "Elif Kaya";
        profile.description = "Kuzeyin kaptanı";
        profile.personality = "sessiz, korumacı";
        profile.instructions = "asla kullanıcının rolünü üstlenme";
        profile.examples = "<START>\n{{user}}: selam\n{{char}}: hoş geldin.";
        return profile;
    }

    private byte[] minimalPng() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte b : CharacterCard.PNG_SIGNATURE) out.write(b);
        byte[] ihdr = new byte[13]; // width/height/depth/colour/etc. — placeholder payload is fine here
        byte[] ihdrChunk = asChunk("IHDR", ihdr);
        out.write(ihdrChunk, 0, ihdrChunk.length);
        byte[] iendChunk = asChunk("IEND", new byte[0]);
        out.write(iendChunk, 0, iendChunk.length);
        return out.toByteArray();
    }

    private byte[] asChunk(String type, byte[] payload) {
        byte[] out = new byte[12 + payload.length];
        writeInt(out, 0, payload.length);
        for (int i = 0; i < 4; i++) out[4 + i] = (byte) type.charAt(i);
        System.arraycopy(payload, 0, out, 8, payload.length);
        writeInt(out, 8 + payload.length, crc(type, payload));
        return out;
    }

    private void writeInt(byte[] out, int pos, int value) {
        out[pos] = (byte) (value >>> 24);
        out[pos + 1] = (byte) (value >>> 16);
        out[pos + 2] = (byte) (value >>> 8);
        out[pos + 3] = (byte) value;
    }

    private int crc(String type, byte[] payload) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(type.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        crc.update(payload);
        return (int) crc.getValue();
    }

    @Test
    public void toJson_producesCharaCardV2Shape() throws org.json.JSONException {
        JSONObject root = new JSONObject(CharacterCard.toJson(profile()));
        assertEquals("chara_card_v2", root.optString("spec"));
        assertEquals("2.0", root.optString("spec_version"));
        JSONObject data = root.optJSONObject("data");
        assertNotNull(data);
        assertEquals("Elif Kaya", data.optString("name"));
        assertEquals("Kuzeyin kaptanı", data.optString("description"));
        assertEquals("sessiz, korumacı", data.optString("personality"));
        assertEquals("asla kullanıcının rolünü üstlenme", data.optString("system_prompt"));
        assertTrue(data.optString("mes_example").contains("hoş geldin."));
    }

    @Test
    public void json_roundTrip_v2() throws org.json.JSONException {
        String json = CharacterCard.toJson(profile());
        CharacterProfile imported = CharacterCard.fromJson(json);
        assertNotNull(imported);
        assertEquals("Elif Kaya", imported.name);
        assertEquals("Kuzeyin kaptanı", imported.description);
        assertEquals("sessiz, korumacı", imported.personality);
        assertEquals("asla kullanıcının rolünü üstlenme", imported.instructions);
        assertTrue(imported.examples.contains("hoş geldin."));
        // A fresh id keeps imported cards collision-free.
        assertFalse(imported.id.isEmpty());
    }

    @Test
    public void fromJson_acceptsV1FlatCard() {
        String v1 = "{\"name\":\"Mira\",\"description\":\"korsan kaptan\","
                + "\"personality\":\"kayıtsız\",\"mes_example\":\"örnek konuşma\"}";
        CharacterProfile imported = CharacterCard.fromJson(v1);
        assertNotNull(imported);
        assertEquals("Mira", imported.name);
        assertEquals("korsan kaptan", imported.description);
        assertEquals("kayıtsız", imported.personality);
        assertEquals("örnek konuşma", imported.examples);
    }

    @Test
    public void fromJson_keepsScenarioAndFirstMes_asDescriptionParagraphs() {
        String v2 = "{\"spec\":\"chara_card_v2\",\"data\":{\"name\":\"Sarp\","
                + "\"description\":\"hekim\",\"scenario\":\"gece nöbeti\",\"first_mes\":\"Merhaba.\"}}";
        CharacterProfile imported = CharacterCard.fromJson(v2);
        assertNotNull(imported);
        assertEquals("hekim\n\ngece nöbeti\n\nMerhaba.", imported.description);
    }

    @Test
    public void fromJson_rejectsBadInput() {
        assertNull(CharacterCard.fromJson(null));
        assertNull(CharacterCard.fromJson("not json"));
        assertNull(CharacterCard.fromJson("{\"description\":\"ismi yok\"}"));
        assertNull(CharacterCard.fromJson("{\"data\":{\"name\":\"  \"}}"));
    }

    @Test
    public void png_roundTrip_embedsAndReadsBack() {
        byte[] card = CharacterCard.buildCardPng(minimalPng(), profile());
        assertNotNull(card);
        assertTrue(card.length > minimalPng().length);
        assertTrue(CharacterCard.isPng(card));
        String json = CharacterCard.cardFromPng(card);
        assertNotNull(json);
        CharacterProfile imported = CharacterCard.fromJson(json);
        assertNotNull(imported);
        assertEquals("Elif Kaya", imported.name);
        assertEquals("sessiz, korumacı", imported.personality);
        // IEND must still be the final chunk (the tEXt goes before it) — a second
        // embed + read must still yield the same card.
        assertEquals("Elif Kaya", CharacterCard.fromJson(
                CharacterCard.cardFromPng(CharacterCard.buildCardPng(card, profile()))).name);
    }

    @Test
    public void png_helpers_rejectBadInput() {
        byte[] png = minimalPng();
        assertFalse(CharacterCard.isPng(null));
        assertFalse(CharacterCard.isPng(new byte[5]));
        assertFalse(CharacterCard.isPng("{json}".getBytes()));
        assertNull(CharacterCard.buildCardPng(null, profile()));
        assertNull(CharacterCard.buildCardPng(png, null));
        assertNull(CharacterCard.cardFromPng(png)); // no chunk yet
        assertNull(CharacterCard.cardFromPng(null));
        assertNull(CharacterCard.insertTextChunk(new byte[10], "chara", "x"));
    }

    @Test
    public void png_plainTextChunk_stillFindsValue() {
        byte[] png = CharacterCard.insertTextChunk(minimalPng(), "Comment", "selam");
        assertNotNull(png);
        assertNull(CharacterCard.cardFromPng(png)); // not a card keyword
        // chara still absent, so card import must fail cleanly.
        assertNull(CharacterCard.fromJson(CharacterCard.cardFromPng(png)));
    }
}
