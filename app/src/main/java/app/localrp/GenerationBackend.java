package app.localrp;

/**
 * The single seam every reply generator plugs into (DeepSeek item 14, Phase 5).
 * The offline llama.cpp engine stays the default backend; a remote OpenAI-compatible
 * provider becomes an explicitly opt-in alternative routed through the same listener
 * contract, so chat code can treat either as one thing starting from the routing batch.
 */
interface GenerationBackend {

    /** Mirrors LocalAiRuntime.GenerationListener so both engines wire identically. */
    interface Listener {
        void onToken(String token);

        void onComplete();

        void onStopped();

        void onError(String message);
    }

    void generate(String prompt, int maxTokens, Listener listener);

    void stop();

    /** Short user-facing readiness line; empty string when this backend is unusable. */
    String status();
}
