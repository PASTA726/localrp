package app.localrp

/**
 * Pure decision layer for the inference runtime: context budgets, prediction-length
 * clamping and the reload decision. No Android imports so it stays unit-testable on
 * the JVM. LocalAiRuntime consults it instead of duplicating the numbers inline.
 */
object RuntimePolicy {

    /** Native engine builds a fixed 8192-token context regardless of the GGUF's training length. */
    const val ENGINE_CONTEXT_TOKENS = 8192

    /** Turkish text is roughly 2.5 characters per token. */
    private const val CHARS_PER_TOKEN = 2.5

    /**
     * Character budget for prompts: honours a manual override, otherwise derives
     * from the model's training context safely capped at the engine's native size.
     */
    fun contextCharBudget(manual: Int, modelContextTokens: Int?): Int {
        if (manual > 0) return manual.coerceIn(1024, 12000)
        val modelTokens = modelContextTokens?.coerceAtLeast(512) ?: ENGINE_CONTEXT_TOKENS
        val effectiveTokens = minOf(modelTokens, ENGINE_CONTEXT_TOKENS)
        return (effectiveTokens * CHARS_PER_TOKEN).toInt().coerceIn(2048, 12000)
    }

    /** Engine-compatible prediction length: always inside 32..2048 tokens. */
    fun predictLength(request: Int): Int = request.coerceIn(32, 2048)

    /**
     * Whether the next generation must reload the model cleanly before prompting:
     * a user-triggered reset, a different conversation than the one in context, or
     * an engine state that lost the loaded context (fresh init / error / benching).
     * The blocked ProcessingSystemPrompt state is handled by the caller waiting.
     */
    fun reloadNeeded(
        resetRequested: Boolean,
        activeChatId: String?,
        chatId: String,
        engineState: String,
    ): Boolean {
        if (resetRequested) return true
        if (activeChatId != null && activeChatId != chatId) return true
        return engineState == "initialized" || engineState == "error" || engineState == "benchmarking"
    }
}
