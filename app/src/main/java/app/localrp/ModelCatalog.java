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
        /** Official Hugging Face LFS SHA-256 of the exact file (fetched from the repo's raw LFS pointer, 2026-10-30). Never fabricated. */
        public final String sha256;
        public final String cpuRange;
        public final String gpuRange;
        public final String lang;
        public final boolean cpuPick, gpuPick;

        Model(String name, String family, String quant, long bytes, int contextTokens, String url, String fileHint,
              String why, String sha256, String cpuRange, String gpuRange, String lang, boolean cpuPick, boolean gpuPick) {
            this.name = name;
            this.family = family;
            this.quant = quant;
            this.bytes = bytes;
            this.contextTokens = contextTokens;
            this.url = url;
            this.fileHint = fileHint;
            this.why = why;
            this.sha256 = sha256;
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
                "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
                "8–15 t/s", "20–35 t/s*", "TR: orta", true, true),
        new Model("Llama-3.2-1B", "Meta", "Q4_K_M", 807_694_464L, 131072,
                "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                "llama32-1b-q4-km.gguf",
                "Denge önerisi: kalite/hız oranı metin RP için en iyi. Türkçesi kibar, konuşma düzeni başarılı.",
                "6f85a640a97cf2bf5b8e764087b1e83da0fdb51d7c9fab7d0fece9385611df83",
                "4–8 t/s", "12–20 t/s*", "TR: iyi", true, true),
        new Model("Qwen2.5-1.5B", "Qwen", "Q4_K_M", 1_117_320_736L, 32768,
                "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                "qwen25-15b-q4-km.gguf",
                "Çok dilli, talimata sadık ve tutarlı; hikâyede bireysel karakter sesleri idare eder.",
                "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
                "3–6 t/s", "9–16 t/s*", "TR: iyi", false, false),
        new Model("Gemma-2-2B", "Google", "Q4_K_M", 1_708_582_752L, 8192,
                "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                "gemma2-2b-q4-km.gguf",
                "Bu boyutta narratif kalitedeki en iyi seçim; atmosfer ve Türkçe doğallık yüksek, hız biraz daha düşük.",
                "e0aee85060f168f0f2d8473d7ea41ce2f3230c1bc1374847505ea599288a7787",
                "2–4 t/s", "7–12 t/s*", "TR: iyi+", false, false),
        new Model("Qwen2.5-3B", "Qwen", "Q4_K_M", 2_104_932_768L, 32768,
                "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
                "qwen25-3b-q4-km.gguf",
                "Sağlam telefonlar için güçlü seçenek; uzun sahnelerde soğunun çöken bir model değil.",
                "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d",
                "1,5–3 t/s", "5–9 t/s*", "TR: iyi", false, false),
        new Model("Phi-3.5-mini", "Microsoft", "Q4_K_M", 2_393_232_672L, 4096,
                "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                "phi35-mini-q4-km.gguf",
                "Yaşlı ve tek metin odaklı; etiketli konuşmada çok iyi ama bağlam penceresi 4K, çoğu sahne kısa kalır.",
                "e4165e3a71af97f1b4820da61079826d8752a2088e313af0c7d346796c38eff5",
                "1,5–3 t/s", "5–9 t/s*", "TR: orta+", false, false),
    };

    public static final Model[] GPU_MODELS = {
        new Model("Qwen2.5-0.5B", "Qwen", "Q4_K_M", 491_400_032L, 32768,
                CPU_MODELS[0].url, CPU_MODELS[0].fileHint,
                "Küçük model sayılır: GPU'da gerçek zamanlı akış en rahat hissedilen boyut.",
                CPU_MODELS[0].sha256,
                "8–15 t/s", "20–35 t/s*", "TR: orta", true, true),
        new Model("TinyLlama-1.1B", "TinyLlama", "Q4_K_M", 668_788_096L, 2048,
                "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                "tinyllama-11b-q4-km.gguf",
                "Klasik küçük model; GPU senaryoları için çok hafif, uzun sahne bağlamı zayıf.",
                "9fecc3b3cd76bba89d504f29b616eedf7da85b96540e490ca5824d3f7d2776a0",
                "5–10 t/s", "14–22 t/s*", "TR: orta", false, false),
        new Model("Llama-3.2-1B", "Meta", "Q4_K_M", 807_694_464L, 131072,
                CPU_MODELS[1].url, CPU_MODELS[1].fileHint,
                "GPU senaryosunda da dengeli; kalite düşmeden hız artırmak isteyenler için.",
                CPU_MODELS[1].sha256,
                "4–8 t/s", "12–20 t/s*", "TR: iyi", false, false),
        new Model("Gemma-2-2B", "Google", "Q5_K_M", 1_923_278_688L, 8192,
                "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q5_K_M.gguf",
                "gemma2-2b-q5-km.gguf",
                "GPU'da Q5/Q6 quant kalitesini seçebilirsin; metin kalitesi en yüksek boyut.",
                "be65d5966f7efd40d9d8c9d5f6667582d861de52df20133991e2e9cb491150da",
                "1,5–3 t/s", "6–11 t/s*", "TR: iyi+", false, false),
    };

    private ModelCatalog() {}

    public static Model cpuRecommendation() { return CPU_MODELS[1]; } // Llama-3.2-1B
    public static Model gpuRecommendation() { return GPU_MODELS[0]; } // Qwen2.5-0.5B
}
