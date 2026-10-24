package app.localrp;

/**
 * Curated catalog of directly downloadable GGUF models with CPU/GPU expectations.
 * URLs were verified to be resumable (HTTP 206) on Hugging Face CDN.
 */
public final class ModelCatalog {

    /** Single catalog entry shown in the download screen. */
    public static final class Model {
        public final String name;
        public final String family;
        public final String quant;
        public final long bytes;
        public final int contextTokens;
        public final String url;
        public final String fileHint;
        public final String why;
        public final String cpuRange;
        public final String gpuRange;
        public final String lang;
        public final boolean cpuPick, gpuPick;

        Model(String name, String family, String quant, long bytes, int contextTokens, String url, String fileHint,
              String why, String cpuRange, String gpuRange, String lang, boolean cpuPick, boolean gpuPick) {
            this.name = name;
            this.family = family;
            this.quant = quant;
            this.bytes = bytes;
            this.contextTokens = contextTokens;
            this.url = url;
            this.fileHint = fileHint;
            this.why = why;
            this.cpuRange = cpuRange;
            this.gpuRange = gpuRange;
            this.lang = lang;
            this.cpuPick = cpuPick;
            this.gpuPick = gpuPick;
        }
    }

    public static final Model[] CPU_MODELS = {
        new Model("Qwen2.5-0.5B", "Qwen", "Q4_K_M", 491_400_032L, 32768,
                "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                "qwen25-05b-q4-km.gguf",
                "En hızlı acemi model. Uzun sahnelerde hız gerçekten hissedilir, Türkçe düzeyi orta.",
                "8–15 t/s", "20–35 t/s*", "TR: orta", true, true),
        new Model("Llama-3.2-1B", "Meta", "Q4_K_M", 807_694_464L, 131072,
                "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                "llama32-1b-q4-km.gguf",
                "Denge önerisi: kalite/hız oranı metin RP için en iyi. Türkçesi kibar, konuşma düzeni başarılı.",
                "4–8 t/s", "12–20 t/s*", "TR: iyi", true, true),
        new Model("Qwen2.5-1.5B", "Qwen", "Q4_K_M", 1_117_320_736L, 32768,
                "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                "qwen25-15b-q4-km.gguf",
                "Çok dilli, talimata sadık ve tutarlı; hikâyede bireysel karakter sesleri idare eder.",
                "3–6 t/s", "9–16 t/s*", "TR: iyi", false, false),
        new Model("Gemma-2-2B", "Google", "Q4_K_M", 1_705_858_272L, 8192,
                "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                "gemma2-2b-q4-km.gguf",
                "Bu boyutta narratif kalitedeki en iyi seçim; atmosfer ve Türkçe doğallık yüksek, hız biraz daha düşük.",
                "2–4 t/s", "7–12 t/s*", "TR: iyi+", false, false),
        new Model("Qwen2.5-3B", "Qwen", "Q4_K_M", 2_102_250_048L, 32768,
                "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
                "qwen25-3b-q4-km.gguf",
                "Sağlam telefonlar için güçlü seçenek; uzun sahnelerde soğunun çöken bir model değil.",
                "1,5–3 t/s", "5–9 t/s*", "TR: iyi", false, false),
        new Model("Phi-3.5-mini", "Microsoft", "Q4_K_M", 2_393_232_672L, 4096,
                "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                "phi35-mini-q4-km.gguf",
                "Yaşlı ve tek metin odaklı; etiketli konuşmada çok iyi ama bağlam penceresi 4K, çoğu sahne kısa kalır.",
                "1,5–3 t/s", "5–9 t/s*", "TR: orta+", false, false),
    };

    public static final Model[] GPU_MODELS = {
        new Model("Qwen2.5-0.5B", "Qwen", "Q4_K_M", 491_400_032L, 32768,
                CPU_MODELS[0].url, CPU_MODELS[0].fileHint,
                "Küçük model sayılır: GPU'da gerçek zamanlı akış en rahat hissedilen boyut.",
                "8–15 t/s", "20–35 t/s*", "TR: orta", true, true),
        new Model("TinyLlama-1.1B", "TinyLlama", "Q4_K_M", 668_788_096L, 2048,
                "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                "tinyllama-11b-q4-km.gguf",
                "Klasik küçük model; GPU senaryoları için çok hafif, uzun sahne bağlamı zayıf.",
                "5–10 t/s", "14–22 t/s*", "TR: orta", false, false),
        new Model("Llama-3.2-1B", "Meta", "Q4_K_M", 807_694_464L, 131072,
                CPU_MODELS[1].url, CPU_MODELS[1].fileHint,
                "GPU senaryosunda da dengeli; kalite düşmeden hız artırmak isteyenler için.",
                "4–8 t/s", "12–20 t/s*", "TR: iyi", false, false),
        new Model("Gemma-2-2B", "Google", "Q5_K_M", 1_923_278_688L, 8192,
                "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q5_K_M.gguf",
                "gemma2-2b-q5-km.gguf",
                "GPU'da Q5/Q6 quant kalitesini seçebilirsin; metin kalitesi en yüksek boyut.",
                "1,5–3 t/s", "6–11 t/s*", "TR: iyi+", false, false),
    };

    private ModelCatalog() {}

    public static Model cpuRecommendation() { return CPU_MODELS[1]; } // Llama-3.2-1B
    public static Model gpuRecommendation() { return GPU_MODELS[0]; } // Qwen2.5-0.5B
}
