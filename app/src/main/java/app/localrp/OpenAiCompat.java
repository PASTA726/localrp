package app.localrp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible chat-completions streaming client (DeepSeek item 14, Phase 5).
 * Streaming JSON / SSE parsing and request assembly is split out as pure static
 * functions so the fragile parts are unit-regulated without any network. Never
 * transmit without the user explicitly enabling a provider in the next batch.
 * Callbacks arrive on the reader thread; the routing batch posts them to the UI.
 */
final class OpenAiCompat implements GenerationBackend {

    /** One request message: role is "system", "user" or "assistant". */
    static final class Turn {
        final String role;
        final String content;
        /** Optional data URL ("data:image/jpeg;base64,…") for vision turns; null = text-only. */
        final String imageDataUrl;

        Turn(String role, String content) {
            this(role, content, null);
        }

        Turn(String role, String content, String imageDataUrl) {
            this.role = role == null ? "user" : role;
            this.content = content == null ? "" : content;
            this.imageDataUrl = (imageDataUrl == null || imageDataUrl.isEmpty()) ? null : imageDataUrl;
        }

        boolean hasImage() { return imageDataUrl != null; }
    }

    private final String endpoint;   // full URL, e.g. https://host/v1/chat/completions
    private final String apiKey;
    private final String model;
    private volatile HttpURLConnection connection;
    private volatile boolean stopped;

    OpenAiCompat(String endpoint, String apiKey, String model) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null ? "" : model.trim();
    }

    // ---- GenerationBackend ----

    @Override
    public void generate(String prompt, int maxTokens, Listener listener) {
        List<Turn> turns = List.of(new Turn("user", prompt == null ? "" : prompt));
        stream(turns, maxTokens, listener);
    }

    /** Streams a full turn list; the routing batch maps history onto turns. */
    public void stream(List<Turn> turns, int maxTokens, Listener listener) {
        streamWith(turns, maxTokens, null, listener);
    }

    /** Sampler overload (v0.25.00): provider-side sampling knobs for this turn. */
    public void stream(List<Turn> turns, int maxTokens, SamplerSettings.Values sampler, Listener listener) {
        streamWith(turns, maxTokens, sampler, listener);
    }

    /** Shared implementation; the sampler travels as a local — never an instance
     *  field — so concurrent stream calls can never see each other's knobs. */
    private void streamWith(List<Turn> turns, int maxTokens,
                            SamplerSettings.Values sampler, Listener listener) {
        if (!allowsEndpoint(endpoint)) {
            listener.onError("Adres güvenli değil: yalnızca https:// kabul edilir; API anahtarı düz bağlantıda sızabilir.");
            return;
        }
        if (endpoint.isEmpty() || model.isEmpty()) {
            listener.onError("Sağlayıcı yapılandırması eksik; Ayarlar'dan adres ve model adını gir.");
            return;
        }
        stopped = false; // one instance per turn; stop() before stream() is a requester bug
        Thread worker = new Thread(() -> {
            try {
                HttpURLConnection opened = connect(turns, maxTokens, sampler);
                connection = opened;
                if (stopped) { // stop() won the race before the request body was even sent
                    opened.disconnect();
                    listener.onStopped();
                    return;
                }
                int code = opened.getResponseCode();
                if (code >= 400) {
                    String raw = readAll(opened.getErrorStream());
                    listener.onError("Sağlayıcı hatası (" + code + "): " + errorMessage(raw));
                    return;
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(opened.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (!stopped && (line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.startsWith("data:")) continue;   // SSE comments/whitespace
                        String data = line.substring(5).trim();
                        if (data.isEmpty()) continue;
                        if (isDone(data)) {
                            listener.onComplete();
                            return;
                        }
                        if (hasError(data)) {   // a mid-stream error payload must not pass as success
                            listener.onError("Sağlayıcı hatası: " + errorMessage(data));
                            return;
                        }
                        String delta = deltaFromSseData(data);
                        if (delta != null && !delta.isEmpty()) listener.onToken(delta);
                    }
                    // Stream closed without an explicit [DONE]: the reply is still complete.
                    if (!stopped) listener.onComplete();
                    else listener.onStopped();
                }
            } catch (Exception failure) {
                if (stopped) listener.onStopped();
                // Never forward a raw library message: provider exception text can carry
                // the endpoint URL or other connection details the user never chose to see.
                else listener.onError("Bağlantı hatası (" + failure.getClass().getSimpleName() + "); adresi ve bağlantını kontrol et.");
            } finally {
                HttpURLConnection open = connection;
                if (open != null) open.disconnect();   // every path, success or not
            }
        }, "localrp-online");
        worker.start();
    }

    @Override
    public void stop() {
        stopped = true;
        HttpURLConnection open = connection;
        if (open != null) open.disconnect();
    }

    @Override
    public String status() {
        if (endpoint.isEmpty() || model.isEmpty()) return "Sağlayıcı eksik yapılandırılmış; adres ve model adı gerekli.";
        return "Hazır · " + model + " · sorun: onay gerektirir";
    }

    private HttpURLConnection connect(List<Turn> turns, int maxTokens,
                                      SamplerSettings.Values sampler) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection opened = (HttpURLConnection) url.openConnection();
        opened.setRequestMethod("POST");
        opened.setConnectTimeout(10_000);
        opened.setReadTimeout(90_000);
        opened.setDoOutput(true);
        opened.setRequestProperty("Content-Type", "application/json");
        if (!apiKey.isEmpty()) opened.setRequestProperty("Authorization", "Bearer " + apiKey);
        byte[] body = requestBody(model, turns, maxTokens, sampler).getBytes(StandardCharsets.UTF_8);
        // Streaming length must precede getOutputStream: it implicitly connects, and
        // afterwards the streaming mode throws IllegalStateException (review finding).
        opened.setFixedLengthStreamingMode(body.length);
        try (java.io.OutputStream output = opened.getOutputStream()) {
            output.write(body);
            output.flush();
        }
        return opened;
    }

    private static String readAll(java.io.InputStream stream) throws java.io.IOException {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder raw = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) raw.append(line);
            return raw.toString();
        }
    }

    // ---- pure, unit-tested pieces ----

    /** Request body for chat/completions with streaming on. */
    static String requestBody(String model, List<Turn> turns, int maxTokens) {
        return requestBody(model, turns, maxTokens, null);
    }

    /** Sampler overload (v0.25.00): non-null fields ride on the request body. */
    static String requestBody(String model, List<Turn> turns, int maxTokens, SamplerSettings.Values sampler) {
        try {
            JSONObject root = new JSONObject();
            root.put("model", model == null ? "" : model);
            root.put("stream", true);
            root.put("max_tokens", maxTokens);
            if (sampler != null) {
                // Omitted when unset: the provider's own default then applies.
                if (sampler.temperature != null) root.put("temperature", sampler.temperature);
                if (sampler.topP != null) root.put("top_p", sampler.topP);
            }
            JSONArray messages = new JSONArray();
            if (turns != null) {
                for (Turn turn : turns) {
                    JSONObject o = new JSONObject();
                    o.put("role", turn.role);
                    if (turn.hasImage()) {
                        // Vision shape (v0.24.00): content is a typed parts array —
                        // text plus the image as a base64 data URL.
                        JSONArray parts = new JSONArray();
                        JSONObject textPart = new JSONObject();
                        textPart.put("type", "text");
                        textPart.put("text", turn.content);
                        parts.put(textPart);
                        JSONObject imageUrlValue = new JSONObject();
                        imageUrlValue.put("url", turn.imageDataUrl);
                        JSONObject imagePart = new JSONObject();
                        imagePart.put("type", "image_url");
                        imagePart.put("image_url", imageUrlValue);
                        parts.put(imagePart);
                        o.put("content", parts);
                    } else {
                        o.put("content", turn.content);
                    }
                    messages.put(o);
                }
            }
            root.put("messages", messages);
            return root.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /** SSE terminator check; some servers also end without [DONE], handled upstream. */
    static boolean isDone(String data) {
        return "[DONE]".equals(data);
    }

    /**
     * Absolutely https: the API key must never ride an unencrypted link. Parsed with
     * {@link URL} (v0.19.19) instead of a string prefix check, so scheme casing
     * ("HTTPS://") is judged the way the actual connection will be and any malformed
     * or non-https address fails closed. {@link java.net.URL} lowercases the scheme,
     * matching {@code DownloadPolicy.isHttps} on the download path.
     */
    static boolean allowsEndpoint(String endpoint) {
        if (endpoint == null) return false;
        try {
            return new URL(endpoint.trim()).getProtocol().equals("https");
        } catch (Exception failure) {
            return false;
        }
    }

    /**
     * Turn list for one provider turn (DeepSeek item 14/B): the system prompt first,
     * then the recent transcript as alternating user/assistant turns, then the final
     * user text. A trailing user message is excluded from the middle turns — it is
     * passed again as {@code finalUserText} so the request always ends with a user
     * turn without duplicating it in the transcript body. Image-only records keep
     * the same "[görsel]/[image]" placeholder the engine transcript uses.
     */
    static List<Turn> assembleTurns(String systemPrompt, List<Message> history, String finalUserText, int depth) {
        return assembleTurns(systemPrompt, history, finalUserText, depth, null);
    }

    /**
     * Vision overload (v0.24.00): {@code trailingImageDataUrl} (may be null) rides on
     * the final user turn, so an attached photo reaches vision-capable providers as a
     * real image part instead of a placeholder. Mid-history images keep placeholders.
     */
    static List<Turn> assembleTurns(String systemPrompt, List<Message> history, String finalUserText,
                                    int depth, String trailingImageDataUrl) {
        List<Turn> turns = new ArrayList<>();
        turns.add(new Turn("system", systemPrompt == null ? "" : systemPrompt));
        int end = history == null ? 0 : history.size();
        if (end > 0 && history.get(end - 1).user) end--;
        int start = Math.max(0, end - Math.max(0, depth));
        for (int i = start; i < end; i++) {
            Message message = history.get(i);
            String body = (message.text == null || message.text.isEmpty()) && message.hasImage()
                    ? "[görsel]" : (message.text == null ? "" : message.text);
            turns.add(new Turn(message.user ? "user" : "assistant", body));
        }
        turns.add(new Turn("user", finalUserText == null ? "" : finalUserText, trailingImageDataUrl));
        return turns;
    }

    /** Mid-stream error chunk detection (OpenAI error object shape). */
    static boolean hasError(String data) {
        try {
            return new JSONObject(data).has("error");
        } catch (JSONException broken) {
            return false;
        }
    }

    /**
     * Delta text of one streamed chunk. Handles the streaming shape (delta.content),
     * non-streaming copies some servers return anyway (message.content), and reasoning
     * chunks that carry no text. Null when the chunk has nothing to append.
     */
    static String deltaFromSseData(String data) {
        try {
            JSONObject root = new JSONObject(data);
            if (root.has("error")) return null;   // caller should stop; keep stream silent
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return null;
            JSONObject choice = choices.getJSONObject(0);
            JSONObject delta = choice.optJSONObject("delta");
            if (delta != null && delta.has("content")) {
                // Explicit JSON null arrives as the NULL sentinel, not a Java null.
                if (delta.isNull("content")) return null;
                Object text = delta.opt("content");
                return text == null ? null : text.toString();
            }
            JSONObject message = choice.optJSONObject("message");
            if (message != null) {
                if (message.isNull("content")) return null;
                Object text = message.opt("content");
                return text == null ? null : text.toString();
            }
            return null;
        } catch (JSONException broken) {
            return null;   // keep-alive comments and provider quirks never crash a reply
        }
    }

    /** Human message from a provider error body; falls back to a generic line. */
    static String errorMessage(String rawErrorBody) {
        if (rawErrorBody != null && !rawErrorBody.isEmpty()) {
            try {
                JSONObject root = new JSONObject(rawErrorBody);
                JSONObject error = root.optJSONObject("error");
                String message = error != null ? error.optString("message", "") : "";
                if (message.isEmpty()) message = root.optString("message", "");
                if (!message.isEmpty()) return message;
            } catch (JSONException ignored) { }
        }
        return "Ayrıntı gönderilmedi.";
    }
}
