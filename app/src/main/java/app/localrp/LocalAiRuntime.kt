package app.localrp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.StatFs
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.gguf.FileType
import com.arm.aichat.gguf.GgufMetadataReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Owns the single llama.cpp engine and keeps its blocking work off the UI thread. */
object LocalAiRuntime {
    interface ModelListener {
        fun onStatus(message: String)
        fun onLoaded(fileName: String, bytes: Long)
        fun onError(message: String)
    }

    interface GenerationListener {
        fun onStatus(message: String)
        fun onToken(token: String)
        fun onComplete()
        fun onStopped()
        fun onError(message: String)
        fun onMetrics(firstTokenMs: Long, totalMs: Long, tokenCount: Int)
    }

    interface BenchmarkListener {
        fun onStatus(message: String)
        fun onResult(result: String)
        fun onError(message: String)
    }

    /** GGUF header information used by the settings screen. */
    data class ModelInfo(
        val name: String?,
        val architecture: String?,
        val quantization: String?,
        val contextLength: Int?,
        val sizeLabel: String?
    )

    private const val SYSTEM_PROMPT =
        "Sen LocalRP'nin daima karakter içinde kalan rol yapma motorusun. Kullanıcıyla " +
            "ortak hikâyeyi sürdür. Kullanıcının karakterinin kararlarını veya sözlerini yazma. " +
            "Birden fazla karakterli sahnelerde her turda yalnızca istenen karakterin repliğini yaz; " +
            "başka karakterlere veya kullanıcıya ait replikler yazma. " +
            "Türkçe yanıt ver ve sahnenin dışına çıkma."

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var engine: InferenceEngine? = null
    @Volatile private var modelPath: String? = null
    @Volatile private var activeChatId: String? = null
    @Volatile private var reloadBeforeNextGeneration = false
    @Volatile private var modelJob: Job? = null
    @Volatile private var generationJob: Job? = null
    @Volatile private var benchmarkJob: Job? = null
    @Volatile private var loadingModel = false
    @Volatile private var benchmarking = false
    @Volatile var modelInfo: ModelInfo? = null
        private set

    fun isModelReady(): Boolean = engine?.state?.value is InferenceEngine.State.ModelReady

    fun isModelLoading(): Boolean = loadingModel

    fun isBenchmarking(): Boolean = benchmarking

    /**
     * Keeps the app's character budget inside both the GGUF training context and the
     * native engine's 8192-token context. Turkish text is roughly 2.5 chars/token.
     */
    fun contextCharBudget(): Int {
        val manual = manualContextBudget
        if (manual > 0) return manual.coerceIn(1024, 12000)
        val modelTokens = modelInfo?.contextLength?.coerceAtLeast(512) ?: 8192
        val effectiveTokens = minOf(modelTokens, 8192)
        return (effectiveTokens * 2.5).toInt().coerceIn(2048, 12000)
    }

    @Volatile var manualContextBudget = -1
        private set

    fun setManualContextBudget(chars: Int) {
        manualContextBudget = chars
    }

    @Volatile private var customSystemPrompt: String? = null

    /** Text sent to the engine as its global roleplay system prompt. */
    fun currentSystemPrompt(): String = customSystemPrompt ?: SYSTEM_PROMPT

    fun setCustomSystemPrompt(value: String?) {
        val normalized = value?.trim()
        customSystemPrompt = if (normalized.isNullOrEmpty()) null else normalized
        reloadBeforeNextGeneration = true
        activeChatId = null
    }

    fun modelsDirectory(context: Context): File = modelDirectory(context.applicationContext)

    fun storedModels(context: Context): List<File> {
        val directory = modelDirectory(context.applicationContext)
        return (directory.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
    }

    fun loadedModelPath(): String? = modelPath

    fun deleteStoredModel(context: Context, file: File): Boolean {
        if (file.absolutePath == modelPath) return false
        val directory = modelDirectory(context.applicationContext)
        if (file.parentFile?.absolutePath != directory.absolutePath) return false
        return file.delete()
    }

    fun isGenerating(): Boolean = generationJob?.isActive == true

    /**
     * True only when the engine still has this chat's conversation in memory.
     * If false, the caller should include a short history in the prompt.
     */
    fun hasConversation(chatId: String): Boolean = activeChatId == chatId && !reloadBeforeNextGeneration

    /** Marks the conversation as stale. The next generation reloads the model with a clean context. */
    fun resetConversation(chatId: String? = null) {
        if (chatId == null || activeChatId == null || activeChatId == chatId) {
            activeChatId = null
            reloadBeforeNextGeneration = true
        }
    }

    fun loadModel(context: Context, uri: Uri, listener: ModelListener) {
        if (modelJob?.isActive == true) return
        loadingModel = true
        modelJob = scope.launch {
            try {
                generationJob?.cancelAndJoin()
                val app = context.applicationContext
                val modelEngine = engine ?: AiChat.getInferenceEngine(app).also { engine = it }
                withContext(Dispatchers.Main) { listener.onStatus("AI motoru başlatılıyor…") }
                val initial = modelEngine.state.first {
                    it is InferenceEngine.State.Initialized ||
                        it is InferenceEngine.State.Error ||
                        it is InferenceEngine.State.ModelReady
                }
                if (initial is InferenceEngine.State.Error) modelEngine.cleanUp()

                val name = try {
                    displayName(app, uri)
                } catch (_: Exception) {
                    app.getSharedPreferences("localrp", Context.MODE_PRIVATE)
                        .getString("selected_model_name", "model.gguf") ?: "model.gguf"
                }
                val directory = modelDirectory(app)
                    .apply { if (!exists() && !mkdirs()) error("Model klasörü oluşturulamadı.") }
                val modelKey = Integer.toHexString(uri.toString().hashCode())
                val storedName = name.dropLast(5) + "-" + modelKey + ".gguf"
                val destination = File(directory, storedName)
                val temporary = File(directory, "$storedName.importing")
                val appPreferences = app.getSharedPreferences("localrp", Context.MODE_PRIVATE)

                val expectedSize = contentSize(app, uri)
                val cachedCopy = appPreferences.getString("copied_model_uri", null) == uri.toString() &&
                    destination.exists() &&
                    (expectedSize == null || destination.length() == expectedSize)

                withContext(Dispatchers.Main) { listener.onStatus("GGUF dosyası doğrulanıyor…") }
                val reader = GgufMetadataReader.create()
                val validModel = if (cachedCopy) {
                    reader.ensureSourceFileFormat(destination)
                } else {
                    reader.ensureSourceFileFormat(app, uri)
                }
                if (!validModel) error("Seçilen dosya geçerli bir GGUF modeli değil.")

                val available = StatFs(directory.absolutePath).availableBytes
                if (!cachedCopy && expectedSize != null && expectedSize > available - 128L * 1024 * 1024) {
                    error("Model için depolama alanı yetersiz. Gerekli: ${formatSize(expectedSize)}, boş: ${formatSize(available)}.")
                }

                if (!cachedCopy) {
                    withContext(Dispatchers.Main) { listener.onStatus("Model dosyası uygulamanın özel alanına kopyalanıyor…") }
                    temporary.delete()
                    try {
                        var copied = 0L
                        var lastPercent = -1
                        var lastMegabytes = -1L
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(temporary).use { output ->
                                val buffer = ByteArray(1024 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    copied += read
                                    if (expectedSize != null && expectedSize > 0) {
                                        val percent = ((copied * 100) / expectedSize).toInt()
                                        if (percent >= lastPercent + 2 || percent == 100) {
                                            lastPercent = percent
                                            withContext(Dispatchers.Main) {
                                                listener.onStatus("Model kopyalanıyor… %$percent")
                                            }
                                        }
                                    } else {
                                        val megabytes = copied / (128L * 1024 * 1024)
                                        if (megabytes != lastMegabytes) {
                                            lastMegabytes = megabytes
                                            withContext(Dispatchers.Main) {
                                                listener.onStatus("Model kopyalanıyor… ${formatSize(copied)}")
                                            }
                                        }
                                    }
                                }
                                output.fd.sync()
                            }
                        } ?: error("Model dosyası açılamadı.")
                        if (expectedSize != null && temporary.length() != expectedSize) {
                            error("Model dosyası tam kopyalanamadı.")
                        }
                        Files.move(
                            temporary.toPath(),
                            destination.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    } catch (failure: Throwable) {
                        temporary.delete()
                        throw failure
                    }
                    appPreferences.edit().putString("copied_model_uri", uri.toString()).apply()
                }

                benchmarkJob?.cancelAndJoin()
                benchmarking = false
                generationJob?.cancelAndJoin()
                val state = modelEngine.state.value
                if (state is InferenceEngine.State.ModelReady || state is InferenceEngine.State.Error) {
                    withContext(Dispatchers.Main) { listener.onStatus("Önceki model bellekte boşaltılıyor…") }
                    modelEngine.cleanUp()
                    modelPath = null
                }

                modelInfo = null
                withContext(Dispatchers.Main) { listener.onStatus("GGUF model bilgileri okunuyor…") }
                modelInfo = readModelInfo(destination)
                attachModel(modelEngine, destination, name, listener)
            } catch (cancelled: CancellationException) {
                loadingModel = false
                modelInfo = null
                throw cancelled
            } catch (failure: Throwable) {
                loadingModel = false
                modelInfo = null
                withContext(Dispatchers.Main) {
                    listener.onError(failure.message ?: "Model yüklenemedi.")
                }
            }
        }
    }

    /**
     * Loads a GGUF file that already lives in the app's model directory
     * (downloaded through the catalog or kept by "model kopyaları").
     */
    fun loadStoredModel(context: Context, file: File, listener: ModelListener) {
        if (modelJob?.isActive == true) return
        loadingModel = true
        modelJob = scope.launch {
            try {
                if (!file.isFile) error("Model dosyası bulunamadı: ${file.name}")
                generationJob?.cancelAndJoin()
                val app = context.applicationContext
                val modelEngine = engine ?: AiChat.getInferenceEngine(app).also { engine = it }
                withContext(Dispatchers.Main) { listener.onStatus("AI motoru başlatılıyor…") }
                val initial = modelEngine.state.first {
                    it is InferenceEngine.State.Initialized ||
                        it is InferenceEngine.State.Error ||
                        it is InferenceEngine.State.ModelReady
                }
                if (initial is InferenceEngine.State.Error) modelEngine.cleanUp()
                attachModel(modelEngine, file, file.name, listener)
            } catch (cancelled: CancellationException) {
                loadingModel = false
                modelInfo = null
                throw cancelled
            } catch (failure: Throwable) {
                loadingModel = false
                modelInfo = null
                withContext(Dispatchers.Main) {
                    listener.onError(failure.message ?: "Model yüklenemedi.")
                }
            }
        }
    }

    /** Shared tail of load flows: unload previous model, read metadata, load, set prompt. */
    private suspend fun attachModel(
        modelEngine: InferenceEngine,
        destination: File,
        name: String,
        listener: ModelListener
    ) {
        benchmarkJob?.cancelAndJoin()
        benchmarking = false
        generationJob?.cancelAndJoin()
        val state = modelEngine.state.value
        if (state is InferenceEngine.State.ModelReady || state is InferenceEngine.State.Error) {
            withContext(Dispatchers.Main) { listener.onStatus("Önceki model bellekte boşaltılıyor…") }
            modelEngine.cleanUp()
            modelPath = null
        }

        modelInfo = null
        withContext(Dispatchers.Main) { listener.onStatus("GGUF model bilgileri okunuyor…") }
        modelInfo = readModelInfo(destination)
        withContext(Dispatchers.Main) {
            listener.onStatus("Model belleğe yükleniyor. Bu işlem model boyutuna göre zaman alabilir…")
        }
        modelEngine.loadModel(destination.absolutePath)
        modelEngine.setSystemPrompt(currentSystemPrompt())
        modelPath = destination.absolutePath
        activeChatId = null
        reloadBeforeNextGeneration = false
        loadingModel = false
        withContext(Dispatchers.Main) { listener.onLoaded(name, destination.length()) }
    }

    fun generate(chatId: String, prompt: String, listener: GenerationListener) {
        generate(chatId, prompt, 320, listener)
    }

    fun generate(chatId: String, prompt: String, predictLength: Int, listener: GenerationListener) {
        val previousGeneration = generationJob
        previousGeneration?.cancel()
        generationJob = scope.launch {
            try {
                previousGeneration?.cancelAndJoin()
                if (benchmarking) error("Motor testi sürüyor; bitmesini bekle.")
                if (loadingModel || modelJob?.isActive == true) error("Model yükleniyor; bitmesini bekle.")
                val modelEngine = engine ?: error("Önce bir GGUF modeli yükle.")
                val path = modelPath ?: error("Önce bir GGUF modeli yükle.")
                if (modelEngine.state.value is InferenceEngine.State.ProcessingSystemPrompt) {
                    modelEngine.state.first {
                        it is InferenceEngine.State.ModelReady || it is InferenceEngine.State.Error
                    }
                }

                val state = modelEngine.state.value
                val needsReload = reloadBeforeNextGeneration ||
                    (activeChatId != null && activeChatId != chatId) ||
                    state is InferenceEngine.State.Initialized ||
                    state is InferenceEngine.State.Error ||
                    state is InferenceEngine.State.Benchmarking

                if (needsReload) {
                    withContext(Dispatchers.Main) { listener.onStatus("Sohbet bağlamı hazırlanıyor…") }
                    if (state is InferenceEngine.State.ModelReady || state is InferenceEngine.State.Error ||
                        state is InferenceEngine.State.Benchmarking) {
                        // A failed bench() can strand the engine in Benchmarking; best-effort reset.
                        try { modelEngine.cleanUp() } catch (_: Throwable) { }
                    }
                    modelEngine.loadModel(path)
                    modelEngine.setSystemPrompt(currentSystemPrompt())
                }

                activeChatId = chatId
                reloadBeforeNextGeneration = false
                withContext(Dispatchers.Main) { listener.onStatus("Yanıt üretiliyor…") }
                val startedAt = System.nanoTime()
                var firstTokenAt = 0L
                var tokenCount = 0
                modelEngine.sendUserPrompt(prompt, predictLength = predictLength.coerceIn(32, 2048)).collect { token ->
                    if (firstTokenAt == 0L) firstTokenAt = System.nanoTime()
                    tokenCount++
                    withContext(Dispatchers.Main) { listener.onToken(token) }
                }
                val totalMs = (System.nanoTime() - startedAt) / 1_000_000
                val firstTokenMs = if (firstTokenAt == 0L) 0L else (firstTokenAt - startedAt) / 1_000_000
                withContext(Dispatchers.Main) { listener.onMetrics(firstTokenMs, totalMs, tokenCount) }
                withContext(Dispatchers.Main) { listener.onComplete() }
            } catch (cancelled: CancellationException) {
                activeChatId = null
                reloadBeforeNextGeneration = true
                // The coroutine is already cancelled here, so the stop event must bypass cancellation.
                withContext(NonCancellable + Dispatchers.Main) { listener.onStopped() }
                throw cancelled
            } catch (failure: Throwable) {
                activeChatId = null
                reloadBeforeNextGeneration = true
                withContext(NonCancellable + Dispatchers.Main) {
                    listener.onError(failure.message ?: "Yanıt üretilemedi.")
                }
            }
        }
    }

    fun benchmark(listener: BenchmarkListener) {
        if (benchmarking) return
        if (modelJob?.isActive == true) {
            listener.onError("Model yüklenirken motor testi başlatılamaz.")
            return
        }
        if (generationJob?.isActive == true) {
            listener.onError("Yanıt üretilirken motor testi başlatılamaz.")
            return
        }
        val modelEngine = engine ?: run {
            listener.onError("Önce bir GGUF modeli yükle.")
            return
        }
        if (modelEngine.state.value !is InferenceEngine.State.ModelReady) {
            listener.onError("Model hazır değil.")
            return
        }
        benchmarking = true
        benchmarkJob = scope.launch {
            try {
                withContext(Dispatchers.Main) { listener.onStatus("Motor testi çalışıyor…") }
                val result = modelEngine.bench(pp = 64, tg = 16, pl = 1, nr = 1)
                benchmarking = false
                withContext(Dispatchers.Main) { listener.onResult(result) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // A bench() failure can leave the engine stranded in Benchmarking;
                // attempt a best-effort reset so the next generation can reload.
                if (modelEngine.state.value is InferenceEngine.State.Benchmarking) {
                    try { modelEngine.cleanUp() } catch (_: Throwable) { }
                }
                benchmarking = false
                val recovery = if (modelEngine.state.value is InferenceEngine.State.ModelReady) ""
                    else "\nModel durumu bozuldu; uygulamayı kapatıp yeniden açman ve modeli yeniden yüklemen gerekebilir."
                withContext(NonCancellable + Dispatchers.Main) {
                    listener.onError((failure.message ?: "Motor testi tamamlanamadı.") + recovery)
                }
            } finally {
                benchmarking = false
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
    }

    fun unload(listener: ModelListener) {
        if (modelJob?.isActive == true) return
        loadingModel = true
        modelJob = scope.launch {
            try {
                benchmarkJob?.cancelAndJoin()
                benchmarking = false
                generationJob?.cancelAndJoin()
                val modelEngine = engine
                if (modelEngine != null && modelEngine.state.value is InferenceEngine.State.ModelReady) {
                    modelEngine.cleanUp()
                }
                modelPath = null
                activeChatId = null
                reloadBeforeNextGeneration = false
                loadingModel = false
                withContext(Dispatchers.Main) { listener.onStatus("Model bellekten kaldırıldı.") }
            } catch (failure: Throwable) {
                loadingModel = false
                withContext(NonCancellable + Dispatchers.Main) {
                    listener.onError(failure.message ?: "Model kaldırılamadı.")
                }
            }
        }
    }

    private suspend fun readModelInfo(file: File): ModelInfo? = withContext(Dispatchers.IO) {
        try {
            file.inputStream().buffered().use { input ->
                val metadata = GgufMetadataReader.create().readStructuredMetadata(input)
                ModelInfo(
                    name = metadata.basic.nameLabel ?: metadata.basic.name,
                    architecture = metadata.architecture?.architecture,
                    quantization = metadata.architecture?.fileType
                        ?.let { FileType.fromCode(it).label },
                    contextLength = metadata.dimensions?.contextLength,
                    sizeLabel = metadata.basic.sizeLabel
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    private fun modelDirectory(context: Context): File =
        context.getExternalFilesDir("models") ?: File(context.filesDir, "models")

    private fun displayName(context: Context, uri: Uri): String {
        var name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "model.gguf"
        name = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (name.length > 72) name = name.take(72) // pathological SAF names must stay within filesystem limits
        if (!name.endsWith(".gguf", ignoreCase = true)) name += ".gguf"
        return name
    }

    private fun contentSize(context: Context, uri: Uri): Long? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0L } else null
        }
    } catch (_: Exception) { null }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> String.format("%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format("%.0f MB", bytes / 1_000_000.0)
        else -> String.format("%.0f KB", bytes / 1_000.0)
    }
}
