package app.localrp;

/**
 * Sampler settings for the OpenAI-compatible provider path (v0.25.00,
 * "Gelişmiş prompt kontrolü"). Values are carried as nullable boxed doubles:
 * null = field is omitted from the request body so the provider's own default
 * applies. The local llama.cpp engine exposes no sampler API, so these apply
 * to provider turns only. Pure logic, no Android imports — JVM-tested.
 */
final class SamplerSettings {
    private SamplerSettings() {
    }

    /** A resolved, request-ready set; every field null = nothing to add. */
    static final class Values {
        final Double temperature;
        final Double topP;

        Values(Double temperature, Double topP) {
            this.temperature = temperature;
            this.topP = topP;
        }

        boolean isEmpty() {
            return temperature == null && topP == null;
        }
    }

    static final double TEMP_MIN = 0.0, TEMP_MAX = 2.0;
    static final double TOPP_MIN = 0.0, TOPP_MAX = 1.0;

    /**
     * Resolution order per field (first non-empty wins): the speaking character's
     * temperature override, then the chat profile, then the global default.
     * Unparseable or out-of-range input resolves to null — never a crash.
     */
    static Values resolve(String globalTemp, String globalTopP,
                          String chatTemp, String chatTopP,
                          String characterTemp) {
        return new Values(
                first(parse(characterTemp, TEMP_MIN, TEMP_MAX),
                        parse(chatTemp, TEMP_MIN, TEMP_MAX),
                        parse(globalTemp, TEMP_MIN, TEMP_MAX)),
                first(parse(chatTopP, TOPP_MIN, TOPP_MAX),
                        parse(globalTopP, TOPP_MIN, TOPP_MAX)));
    }

    /** Parses a stored setting; blank or out-of-range input → null. */
    static Double parse(String raw, double min, double max) {
        if (raw == null) return null;
        String clean = raw.trim();
        if (clean.isEmpty()) return null;
        try {
            double value = Double.parseDouble(clean);
            if (value < min || value > max) return null;
            return value;
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    private static Double first(Double... candidates) {
        for (Double candidate : candidates) {
            if (candidate != null) return candidate;
        }
        return null;
    }
}
