package app.localrp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

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
