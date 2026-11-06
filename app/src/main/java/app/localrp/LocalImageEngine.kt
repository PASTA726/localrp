package app.localrp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device image generation engine built on libstable-diffusion.so
 * (stable-diffusion.cpp, arm64-v8a). One full checkpoint (.safetensors with baked
 * CLIP + VAE) is loaded at a time; generation runs on a background thread and pushes
 * progress + the finished PNG path through [ImageListener]. Offline end to end:
 * nothing is uploaded.
 */
object LocalImageEngine {

    interface ImageListener {
        fun onStatus(message: String)
        fun onReady(file: File)
        fun onError(message: String)
    }

    @Volatile private var nativeLoaded: Boolean? = null
    @Volatile private var ctx: Long = 0L
    @Volatile private var loadedFile: String? = null
    @Volatile private var loadingFile: File? = null
    @Volatile private var busyGenerating = false

    private val busy = AtomicBoolean(false)

    /** Progress sink patched into the JNI layer for the current generation. */
    private val progressTarget = object {
        @Suppress("unused") // Called from JNI (sd_progress_cb).
        fun onStep(step: Int, steps: Int) {
            imageProgress = if (steps > 0) step / steps.toFloat() else 0f
            imageStatus = "$step/$steps"
        }
    }

    /** [0, 1] progress of the running generation, or null when idle. */
    @Volatile var imageProgress: Float? = null
        private set
    @Volatile var imageStatus: String? = null
        private set

    /** Recommended sampling steps of the checkpoint most recently loaded from a catalog card. */
    @Volatile var preparedSteps: Int = 20

    /** Java-facing accessors: @Volatile object properties confuse javac's member resolution. */
    fun preparedStepCount(): Int = preparedSteps
    fun setPreparedStepCount(value: Int) { preparedSteps = value }
    fun statusText(): String? = imageStatus

    fun isModelLoading(): Boolean = loadingFile != null
    fun isModelReady(): Boolean = ctx != 0L && loadedFile != null
    fun loadedModelName(): String? = loadedFile?.substringAfterLast('/')
    fun isGenerating(): Boolean = busyGenerating

    private fun ensureLibrary(): Boolean {
        nativeLoaded?.let { return it }
        return synchronized(this) {
            nativeLoaded ?: try {
                System.loadLibrary("sd-jni")
                true
            } catch (t: Throwable) {
                false
            }.also { nativeLoaded = it }
        }
    }

    /** Generated files and downloaded checkpoints live beside the chat models. */
    fun imagesDirectory(context: Context?): File {
        val base = LocalAiRuntime.modelsDirectory(context!!.applicationContext)
        return File(base, "images")
    }

    fun storedModels(context: Context): List<File> {
        val directory = imagesDirectory(context)
        return (directory.listFiles() ?: emptyArray())
            .filter { it.isFile && ImageStorePolicy.isModelFile(it.name) }
            .sortedBy { it.name.lowercase() }
    }

    /** Generated previews (gen-*.png) are never removable as "models". */
    fun deleteStoredModel(context: Context, file: File): Boolean {
        if (file.absolutePath == loadedFile) return false
        if (ImageStorePolicy.isGenerated(file.name)) return false
        val directory = imagesDirectory(context)
        if (file.parentFile?.absolutePath != directory.absolutePath) return false
        return file.delete()
    }

    fun loadStoredModel(context: Context, file: File, listener: ImageListener) {
        // refuse when another load/generation is in flight
        if (!busy.compareAndSet(false, true)) {
            listener.onError(L10n.s("Görsel motoruyla bir işlem sürüyor; bitmesini bekle."))
            return
        }
        loadingFile = file
        Thread({
            try {
                listener.onStatus(L10n.s("Görsel modeli yükleniyor…"))
                closeLocked()
                if (!ensureLibrary()) {
                    listener.onError(L10n.s("Görsel motoru bu cihazda bulunamadı."))
                    return@Thread
                }
                val handle = nativeCreateCtx(file.absolutePath, cpuThreads())
                if (handle == 0L) {
                    listener.onError(L10n.s("Görsel modeli yüklenemedi: ") + file.name)
                    return@Thread
                }
                ctx = handle
                loadedFile = file.absolutePath
                loadingFile = null
                listener.onReady(file)
            } catch (failure: Throwable) {
                loadingFile = null
                listener.onError(
                    failure.message ?: (L10n.s("Görsel modeli yüklenemedi: ") + file.name))
            } finally {
                busy.set(false)
            }
        }, "localrp-image-load").start()
    }

    fun unload() {
        closeLocked()
    }

    /** Cancels nothing about live generation here; the caller drives stopGeneration. */
    private fun closeLocked() {
        val old = ctx
        ctx = 0L
        loadedFile = null
        if (old != 0L) nativeDestroyCtx(old)
    }

    fun stopGeneration() {
        val handle = ctx
        if (handle != 0L) nativeCancel(handle)
    }

    /**
     * Generates one image for [prompt]. The model must be ready. The caller learns the
     * written PNG file (inside private storage) through listener.onReady.
     */
    fun generate(context: Context, prompt: String, negativePrompt: String,
                 width: Int, height: Int, steps: Int, cfgScale: Float, seed: Long,
                 listener: ImageListener): Boolean {
        if (!isModelReady()) {
            listener.onError(L10n.s("Görsel üretmeden önce bir görsel modeli yükle."))
            return false
        }
        if (!busy.compareAndSet(false, true)) {
            listener.onError(L10n.s("Görsel motoruyla bir işlem sürüyor; bitmesini bekle."))
            return false
        }
        busyGenerating = true
        imageProgress = 0f
        imageStatus = null
        nativeSetProgressTarget(progressTarget)
        val app = context.applicationContext
        val usedSeed = if (seed < 0) System.currentTimeMillis() else seed
        Thread({
            try {
                listener.onStatus(L10n.s("Görsel üretiliyor…"))
                val bytes = nativeGenerate(ctx, prompt, negativePrompt, width, height, steps, cfgScale, -1, usedSeed)
                if (bytes == null || bytes.isEmpty()) {
                    listener.onError(L10n.s("Görsel üretilemedi."))
                    return@Thread
                }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                        .format(java.util.Date())
                val output = File(imagesDirectory(app).apply { mkdirs() }, "gen-$stamp.png")
                FileOutputStream(output).use { it.write(bytes) }
                listener.onReady(output)
            } catch (failure: Throwable) {
                listener.onError(failure.message ?: L10n.s("Görsel üretilemedi."))
            } finally {
                busyGenerating = false
                imageProgress = null
                imageStatus = null
                busy.set(false)
            }
        }, "localrp-image-gen").apply { priority = Thread.MAX_PRIORITY }.start()
        return true
    }

    /** Decodes a stored PNG for display; downsamples huge results defensively. */
    fun decode(path: String, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (t: Throwable) { null }
    }

    private fun cpuThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            cores <= 2 -> 2
            cores <= 4 -> cores
            else -> 4 // phone-class CPU; beyond four threads ggml gains little here
        }
    }

    private external fun nativeSetProgressTarget(target: Any)
    private external fun nativeCreateCtx(modelPath: String, threads: Int): Long
    private external fun nativeDestroyCtx(ptr: Long)
    private external fun nativeCancel(ptr: Long)
    private external fun nativeGenerate(ptr: Long, prompt: String, negativePrompt: String,
                                        width: Int, height: Int, steps: Int, cfgScale: Float,
                                        clipSkip: Int, seed: Long): ByteArray?
    private external fun nativeSystemInfo(): String
}

/** What counts as an image-model download vs. a generated preview inside images/. */
object ImageStorePolicy {
    fun isModelFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".safetensors") || lower.endsWith(".gguf")
    }

    fun isGenerated(name: String): Boolean =
        name.startsWith("gen-") && name.lowercase().endsWith(".png")
}
