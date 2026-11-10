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
