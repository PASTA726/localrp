package app.localrp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.CRC32;

/**
 * SillyTavern-compatible character card (v0.21.00, "Karakter Card V2").
 *
 * Export/import in two shapes: plain JSON (spec {@code chara_card_v2}) and PNG
 * with the base64 JSON embedded in a {@code tEXt} chunk keyed {@code chara} —
 * the chunk SillyTavern itself reads and writes. V1 flat cards (no {@code data}
 * wrapper) are accepted on import. Pure logic, no Android imports — JVM-tested.
 */
final class CharacterCard {
    private CharacterCard() {
    }

    static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    /** Hard caps keep hostile/huge inputs from exhausting memory. */
    private static final int MAX_PNG_BYTES = 12 * 1024 * 1024;
    private static final int MAX_CHUNK_BYTES = 4 * 1024 * 1024;

    // ---- JSON ----

    /** LocalRP profile → SillyTavern V2 card JSON. */
    static String toJson(CharacterProfile profile) {
        if (profile == null) return "";
        try {
            JSONObject data = new JSONObject();
            data.put("name", nullSafe(profile.name));
            data.put("description", nullSafe(profile.description));
            data.put("personality", nullSafe(profile.personality));
            data.put("scenario", "");
            data.put("first_mes", "");
            data.put("mes_example", nullSafe(profile.examples));
            data.put("creator_notes", "");
            data.put("system_prompt", nullSafe(profile.instructions));
            data.put("post_history_instructions", "");
            data.put("alternate_greetings", new JSONArray());
            data.put("tags", new JSONArray());
            data.put("creator", "");
            data.put("character_version", "");
            data.put("extensions", new JSONObject());
            JSONObject root = new JSONObject();
            root.put("spec", "chara_card_v2");
            root.put("spec_version", "2.0");
            root.put("data", data);
            return root.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Card JSON → LocalRP profile. Accepts V2 ({@code data} wrapper) and V1 flat
     * cards. {@code scenario} and {@code first_mes} have no dedicated LocalRP
     * field yet, so they are appended to the description instead of discarded.
     * Returns null when the input is not a parseable card with a name.
     */
    static CharacterProfile fromJson(String json) {
        if (json == null) return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            if (data == null) data = root;
            String name = data.optString("name", "").trim();
            if (name.isEmpty()) return null;
            CharacterProfile profile = new CharacterProfile();
            profile.name = name;
            profile.description = joinNonEmpty(
                    data.optString("description", ""),
                    data.optString("scenario", ""),
                    data.optString("first_mes", ""));
            profile.personality = data.optString("personality", "");
            profile.instructions = data.optString("system_prompt", "");
            profile.examples = data.optString("mes_example", "");
            return profile;
        } catch (Exception e) {
            return null;
        }
    }

    private static String joinNonEmpty(String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (out.length() > 0) out.append("\n\n");
            out.append(part.trim());
        }
        return out.toString();
    }

    private static String nullSafe(String text) {
        return text == null ? "" : text;
    }

    // ---- PNG tEXt chunk ----

    static boolean isPng(byte[] bytes) {
        if (bytes == null || bytes.length < PNG_SIGNATURE.length || bytes.length > MAX_PNG_BYTES) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }

    /**
     * A card PNG = the image bytes with one extra {@code tEXt} chunk inserted
     * before IEND (SillyTavern's own storage format). Returns null when the
     * input is not a well-formed PNG.
     */
    static byte[] buildCardPng(byte[] pngImage, CharacterProfile profile) {
        String json = toJson(profile);
        if (json.isEmpty()) return null;
        return insertTextChunk(pngImage, "chara",
                Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
    }

    /** Extracts the card JSON from a PNG ({@code chara} first, then {@code ccv3}); null if absent. */
    static String cardFromPng(byte[] png) {
        if (!isPng(png)) return null;
        String card = findTextChunk(png, "chara");
        if (card == null) card = findTextChunk(png, "ccv3");
        return card;
    }

    /**
     * Inserts a {@code tEXt} chunk (keyword + NUL + value, latin-1 as per spec —
     * both keyword and value are ASCII here) before the IEND chunk.
     */
    static byte[] insertTextChunk(byte[] png, String keyword, String value) {
        if (!isPng(png) || keyword == null || value == null) return null;
        byte[] keywordBytes = keyword.getBytes(StandardCharsets.ISO_8859_1);
        byte[] valueBytes = value.getBytes(StandardCharsets.ISO_8859_1);
        byte[] payload = new byte[keywordBytes.length + 1 + valueBytes.length];
        System.arraycopy(keywordBytes, 0, payload, 0, keywordBytes.length);
        payload[keywordBytes.length] = 0;
        System.arraycopy(valueBytes, 0, payload, keywordBytes.length + 1, valueBytes.length);
        byte[] chunk = chunk("tEXt", payload);
        int insertAt = iendOffset(png);
        if (insertAt < 0) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream(png.length + chunk.length);
        out.write(png, 0, insertAt);
        out.write(chunk, 0, chunk.length);
        out.write(png, insertAt, png.length - insertAt);
        return out.toByteArray();
    }

    /** Reads one tEXt chunk's value (base64-decoded) by keyword; null if absent. */
    static String findTextChunk(byte[] png, String keyword) {
        if (!isPng(png) || keyword == null) return null;
        byte[] keywordBytes = keyword.getBytes(StandardCharsets.ISO_8859_1);
        int pos = 8;
        while (pos + 8 <= png.length) {
            long length = readLength(png, pos);
            if (length < 0 || length > MAX_CHUNK_BYTES || pos + 12 + length > png.length) return null;
            boolean isText = type(png, pos).equals("tEXt");
            int dataStart = pos + 8;
            if (isText && length > keywordBytes.length) {
                boolean keywordMatches = true;
                for (int i = 0; i < keywordBytes.length; i++) {
                    if (png[dataStart + i] != keywordBytes[i]) {
                        keywordMatches = false;
                        break;
                    }
                }
                if (keywordMatches && png[dataStart + keywordBytes.length] == 0) {
                    try {
                        int valueLength = (int) (length - keywordBytes.length - 1);
                        byte[] valueBytes = new byte[valueLength];
                        System.arraycopy(png, dataStart + keywordBytes.length + 1,
                                valueBytes, 0, valueLength);
                        return new String(Base64.getDecoder().decode(valueBytes),
                                StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException badBase64) {
                        return null;
                    }
                }
            }
            pos = dataStart + (int) length + 4; // data + CRC
        }
        return null;
    }

    private static byte[] chunk(String type, byte[] payload) {
        byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
        byte[] out = new byte[12 + payload.length];
        writeLength(out, 0, payload.length);
        System.arraycopy(typeBytes, 0, out, 4, 4);
        System.arraycopy(payload, 0, out, 8, payload.length);
        CRC32 crc = new CRC32();
        crc.update(out, 4, 4 + payload.length);
        int crcValue = (int) crc.getValue();
        out[out.length - 4] = (byte) (crcValue >>> 24);
        out[out.length - 3] = (byte) (crcValue >>> 16);
        out[out.length - 2] = (byte) (crcValue >>> 8);
        out[out.length - 1] = (byte) crcValue;
        return out;
    }

    /** Offset of the IEND chunk header, or -1 when the PNG is malformed. */
    private static int iendOffset(byte[] png) {
        int pos = 8;
        while (pos + 8 <= png.length) {
            long length = readLength(png, pos);
            if (length < 0 || length > MAX_CHUNK_BYTES || pos + 12 + length > png.length) return -1;
            if (type(png, pos).equals("IEND")) return pos;
            pos += 8 + (int) length + 4;
        }
        return -1;
    }

    private static String type(byte[] png, int pos) {
        return new String(png, pos + 4, 4, StandardCharsets.ISO_8859_1);
    }

    private static long readLength(byte[] png, int pos) {
        long length = 0;
        for (int i = 0; i < 4; i++) {
            length = (length << 8) | (png[pos + i] & 0xFFL);
        }
        return length;
    }

    private static void writeLength(byte[] out, int pos, int length) {
        out[pos] = (byte) (length >>> 24);
        out[pos + 1] = (byte) (length >>> 16);
        out[pos + 2] = (byte) (length >>> 8);
        out[pos + 3] = (byte) length;
    }
}
