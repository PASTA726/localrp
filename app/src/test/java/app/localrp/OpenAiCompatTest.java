package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for the OpenAI-compatible streaming seam (DeepSeek item 14, batch A). */
public class OpenAiCompatTest {

    private static OpenAiCompat.Turn turn(String role, String content) {
        return new OpenAiCompat.Turn(role, content);
    }

    @Test
    public void requestBody_carriesModelStreamTokensAndMessages() throws Exception {
        String body = OpenAiCompat.requestBody("gpt-x", List.of(
                turn("system", "Sahne kuralları"), turn("user", "Merhaba")), 128);
        JSONObject parsed = new JSONObject(body);
        assertEquals("gpt-x", parsed.getString("model"));
        assertTrue(parsed.getBoolean("stream"));
        assertEquals(128, parsed.getInt("max_tokens"));
        assertEquals(2, parsed.getJSONArray("messages").length());
        assertEquals("system", parsed.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("Merhaba", parsed.getJSONArray("messages").getJSONObject(1).getString("content"));
    }

    @Test
    public void requestBody_garbageSizingNeverCrashes() throws Exception {
        // Null-safe assembly always yields a parseable body, never an exception.
        JSONObject parsed = new JSONObject(OpenAiCompat.requestBody(null, null, 128));
        assertEquals("", parsed.getString("model"));
        assertEquals(0, parsed.getJSONArray("messages").length());
        assertTrue(parsed.getBoolean("stream"));
    }

    @Test
    public void requestBody_visionTurn_sendsTypedContentParts() throws Exception {
        // v0.24.00: a turn with an image ships content as a typed parts array —
        // text part plus image_url with the data URL; text-only turns stay strings.
        OpenAiCompat.Turn vision = new OpenAiCompat.Turn("user", "Bu resmi incele",
                "data:image/jpeg;base64,QUJD");
        String body = OpenAiCompat.requestBody("gpt-x", List.of(turn("system", "s"), vision), 256);
        JSONObject parsed = new JSONObject(body);
        JSONObject visionTurn = parsed.getJSONArray("messages").getJSONObject(1);
        assertEquals("user", visionTurn.getString("role"));
        assertEquals(2, visionTurn.getJSONArray("content").length());
        JSONObject textPart = visionTurn.getJSONArray("content").getJSONObject(0);
        assertEquals("text", textPart.getString("type"));
        assertEquals("Bu resmi incele", textPart.getString("text"));
        JSONObject imagePart = visionTurn.getJSONArray("content").getJSONObject(1);
        assertEquals("image_url", imagePart.getString("type"));
        assertEquals("data:image/jpeg;base64,QUJD",
                imagePart.getJSONObject("image_url").getString("url"));
        // Text-only turns keep the plain-string shape providers already know.
        assertEquals("s", parsed.getJSONArray("messages").getJSONObject(0).getString("content"));
    }

    @Test
    public void requestBody_blankDataUrl_isTextOnly() throws Exception {
        OpenAiCompat.Turn blank = new OpenAiCompat.Turn("user", "metin", "");
        JSONObject parsed = new JSONObject(OpenAiCompat.requestBody("m", List.of(blank), 64));
        assertEquals("metin", parsed.getJSONArray("messages").getJSONObject(0).getString("content"));
    }

    @Test
    public void requestBody_sampler_fieldsRideOnlyWhenSet() throws Exception {
        // v0.25.00: set sampler fields are serialized; unset fields are omitted so
        // the provider's own default applies.
        SamplerSettings.Values full = SamplerSettings.resolve(null, null, "0.7", "0.9", null);
        JSONObject parsed = new JSONObject(OpenAiCompat.requestBody("m", List.of(turn("user", "x")), 64, full));
        assertEquals(0.7, parsed.getDouble("temperature"), 1e-9);
        assertEquals(0.9, parsed.getDouble("top_p"), 1e-9);

        JSONObject plain = new JSONObject(OpenAiCompat.requestBody("m", List.of(turn("user", "x")), 64, null));
        assertFalse(plain.has("temperature"));
        assertFalse(plain.has("top_p"));

        SamplerSettings.Values empty = SamplerSettings.resolve(null, null, null, null, null);
        JSONObject none = new JSONObject(OpenAiCompat.requestBody("m", List.of(turn("user", "x")), 64, empty));
        assertFalse(none.has("temperature"));
        assertFalse(none.has("top_p"));
    }

    @Test
    public void sseData_recognizesTerminator() {
        assertTrue(OpenAiCompat.isDone("[DONE]"));
        assertFalse(OpenAiCompat.isDone("{\"choices\":[]}"));
    }

    @Test
    public void sseData_extractsStreamingDelta() {
        assertEquals("Merh", OpenAiCompat.deltaFromSseData(
                "{\"choices\":[{\"delta\":{\"content\":\"Merh\"}}]}"));
        // Role announcement chunks carry no text.
        assertNull(OpenAiCompat.deltaFromSseData("{\"choices\":[{\"delta\":{}}]}"));
        // Content explicitly null (some providers pad this way).
        assertNull(OpenAiCompat.deltaFromSseData("{\"choices\":[{\"delta\":{\"content\":null}}]}"));
        // Reasoning chunks and keep-alive comments never crash a reply.
        assertNull(OpenAiCompat.deltaFromSseData("{\"choices\":[{\"delta\":{\"reasoning\":\"x\"}}]}"));
        assertNull(OpenAiCompat.deltaFromSseData("not json"));
        assertNull(OpenAiCompat.deltaFromSseData(""));
    }

    @Test
    public void sseData_fallsBackToWholeMessageForNonStreamingServers() {
        assertEquals("Tek parça yanıt", OpenAiCompat.deltaFromSseData(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Tek parça yanıt\"}}]}"));
    }

    @Test
    public void sseData_ignoresErrorPayload() {
        assertNull(OpenAiCompat.deltaFromSseData("{\"error\":{\"message\":\"no quota\"}}"));
    }

    @Test
    public void errorMessage_digsNestedAndFlatPayloads() {
        assertEquals("no quota", OpenAiCompat.errorMessage("{\"error\":{\"message\":\"no quota\"}}"));
        assertEquals("flat message", OpenAiCompat.errorMessage("{\"message\":\"flat message\"}"));
        assertEquals("Ayrıntı gönderilmedi.", OpenAiCompat.errorMessage(null));
        assertEquals("Ayrıntı gönderilmedi.", OpenAiCompat.errorMessage("not json"));
    }

    @Test
    public void endpointPolicy_requiresHttpsForTheApiKey() {
        assertTrue(OpenAiCompat.allowsEndpoint("https://api.example.com/v1/chat/completions"));
        assertFalse(OpenAiCompat.allowsEndpoint("http://api.example.com/v1/chat/completions"));
        assertFalse(OpenAiCompat.allowsEndpoint("api.example.com/v1/chat/completions"));
        assertFalse(OpenAiCompat.allowsEndpoint(null));
        assertFalse(OpenAiCompat.allowsEndpoint(""));
    }

    @Test
    public void endpointPolicy_judgesSchemeLikeTheConnectionWill() {
        // v0.19.19: URL-parse based scheme check, consistent with the downloader.
        assertTrue("uppercase scheme is still https (URL lowercases it)",
                OpenAiCompat.allowsEndpoint("HTTPS://api.example.com/v1"));
        assertTrue("whitespace around the endpoint is trimmed",
                OpenAiCompat.allowsEndpoint("  https://api.example.com/v1  "));
        // A space inside the path is accepted by java.net.URL (no exception) but the
        // scheme and host stay untouched — no scheme downgrade is possible.
        assertFalse("other schemes fail closed: ftp://",
                OpenAiCompat.allowsEndpoint("ftp://api.example.com"));
        assertFalse("file scheme fails closed",
                OpenAiCompat.allowsEndpoint("file:///etc/passwd"));
        assertFalse("javascript scheme fails closed",
                OpenAiCompat.allowsEndpoint("javascript:alert(1)"));
        assertFalse("gopher scheme fails closed",
                OpenAiCompat.allowsEndpoint("gopher://host"));
    }

    @Test
    public void assembleTurns_systemFirst_historyAlternating_finalUserTurn() {
        List<Message> history = new ArrayList<>();
        history.add(new Message("Elif", "Selam", false, 1L));          // assistant (assistant turn)
        history.add(new Message("Evren", "Nasılsın?", true, 2L));      // user turn
        history.add(new Message("Elif", "İyiyim", false, 3L));         // assistant turn
        history.add(new Message("Evren", "Devam et", true, 4L));       // trailing user → final turn only

        List<OpenAiCompat.Turn> turns = OpenAiCompat.assembleTurns("SYSTEM", history, "Devam et", 8);
        assertEquals("system", turns.get(0).role);
        assertEquals("SYSTEM", turns.get(0).content);
        assertEquals(5, turns.size());
        assertEquals("assistant", turns.get(1).role);
        assertEquals("Selam", turns.get(1).content);
        assertEquals("user", turns.get(2).role);
        assertEquals("Nasılsın?", turns.get(2).content);
        assertEquals("assistant", turns.get(3).role);
        assertEquals("İyiyim", turns.get(3).content);
        OpenAiCompat.Turn finalTurn = turns.get(turns.size() - 1);
        assertEquals("user", finalTurn.role);
        assertEquals("Devam et", finalTurn.content);
        // The trailing user text never duplicates inside the transcript body.
        long userTextCount = turns.stream().filter(t -> "Devam et".equals(t.content)).count();
        assertEquals(1, userTextCount);
    }

    @Test
    public void assembleTurns_historyDepthBoundsAndImagePlaceholder() {
        List<Message> history = new ArrayList<>();
        for (int i = 0; i < 6; i++) history.add(new Message("A", "m" + i, false, i));
        history.add(new Message("Evren", "yeni", true, 9L));
        List<OpenAiCompat.Turn> turns = OpenAiCompat.assembleTurns("s", history, "yeni", 3);
        assertEquals(5, turns.size()); // system + 3 kept turns + final user
        assertEquals("m3", turns.get(1).content);

        // A non-trailing image-only user record keeps the engine's placeholder.
        List<Message> withImage = new ArrayList<>();
        withImage.add(new Message("Evren", "", true, 1L, "/tmp/x.jpg"));
        withImage.add(new Message("A", "bak kedi", false, 2L));
        List<OpenAiCompat.Turn> imageTurns = OpenAiCompat.assembleTurns("s", withImage, "ne bu?", 8);
        assertEquals("user", imageTurns.get(1).role);
        assertEquals("[görsel]", imageTurns.get(1).content);
        assertEquals("assistant", imageTurns.get(2).role);
        assertEquals("bak kedi", imageTurns.get(2).content);
    }

    @Test
    public void assembleTurns_trailingImage_ridesOnFinalUserTurn() {
        // v0.24.00 vision: only the final user turn carries the data URL; mid-history
        // images stay placeholders (token economy).
        List<Message> history = new ArrayList<>();
        history.add(new Message("Evren", "", true, 1L, "/tmp/old.jpg"));
        history.add(new Message("A", "güzel", false, 2L));
        history.add(new Message("Evren", "Bu kedi nereye bakıyor?", true, 3L, "/tmp/new.jpg"));
        List<OpenAiCompat.Turn> turns = OpenAiCompat.assembleTurns(
                "s", history, "Bu kedi nereye bakıyor?", 8, "data:image/jpeg;base64,QUJD");
        OpenAiCompat.Turn finalTurn = turns.get(turns.size() - 1);
        assertTrue(finalTurn.hasImage());
        assertEquals("data:image/jpeg;base64,QUJD", finalTurn.imageDataUrl);
        assertEquals("Bu kedi nereye bakıyor?", finalTurn.content);
        assertTrue(turns.stream().limit(turns.size() - 1).noneMatch(OpenAiCompat.Turn::hasImage));
        // Null data URL keeps the old text-only shape.
        List<OpenAiCompat.Turn> plain = OpenAiCompat.assembleTurns("s", history, "merhaba", 8, null);
        assertFalse(plain.get(plain.size() - 1).hasImage());
    }

    @Test
    public void assembleTurns_garbageInputsNeverCrash() {
        List<OpenAiCompat.Turn> turns = OpenAiCompat.assembleTurns(null, null, null, 0);
        assertEquals(2, turns.size()); // system turn + final user turn (chat continuity)
        assertEquals("system", turns.get(0).role);
        assertEquals("", turns.get(0).content);
        assertEquals("user", turns.get(1).role);
        assertEquals("", turns.get(1).content);
    }

    @Test
    public void errorChunks_areDetectedInsideTheStream() {
        assertTrue(OpenAiCompat.hasError("{\"error\":{\"message\":\"rate limited\"}}"));
        assertFalse(OpenAiCompat.hasError("{\"choices\":[{\"delta\":{\"content\":\"x\"}}]}"));
        assertFalse(OpenAiCompat.hasError("not json"));
    }

    @Test
    public void requestBody_neverEmbedsTheApiKey() {
        String body = OpenAiCompat.requestBody("m", List.of(turn("user", "hi")), 32);
        assertFalse(body.contains("api_key"));
        assertFalse(body.contains("Bearer"));
        assertTrue(body.contains("stream"));
    }
}
