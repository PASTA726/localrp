package app.localrp

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Streams a GGUF file from a direct HTTPS URL into the app's model directory.
 * Supports cancel, resumable downloads (HTTP Range on verified Hugging Face
 * CDN links) and simple progress reporting; no API keys are used or stored.
 */
object ModelDownload {
    interface Listener {
        fun onProgress(percent: Int, downloadedBytes: Long, totalBytes: Long)
        fun onDone(file: File)
        fun onError(message: String)
    }

    private const val REPORT_CHUNK = 4L * 1024 * 1024 // bytes between progress events without Content-Length

    @Volatile private var job: Job? = null

    /**
     * Single-flight gate. Taken when a download launches and released in the
     * coroutine's finally, so a just-cancelled job keeps the slot until its
     * writer is really gone — attempts during that small unwind get `false`.
     */
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    private val gate = Any()
    @Volatile var currentUrl: String? = null
        private set

    fun isActive(): Boolean = running.get()

    fun cancel() {
        synchronized(gate) {
            currentUrl = null
            job?.cancel()
        }
    }

    /** @return false when refused (another download in flight or still unwinding). */
    fun start(context: Context, url: String, displayName: String, listener: Listener): Boolean {
        synchronized(gate) {
            if (!running.compareAndSet(false, true)) return false
            currentUrl = url
        }
        val app = context.applicationContext
        val target = File(LocalAiRuntime.modelsDirectory(app), displayName)
        val started = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var connection: HttpURLConnection? = null
            try {
                if (currentUrl != url) return@launch // cancel() raced between start() and job assignment
                withContext(Dispatchers.Main) { listener.onProgress(0, 0L, -1L) }
                val directory = target.parentFile ?: error("Model klasörü yok.")
                if (!directory.exists() && !directory.mkdirs()) error("Model klasörü oluşturulamadı.")
                val temporary = File(directory, target.name + ".part")
                val alreadyHave = if (temporary.isFile) temporary.length() else 0L

                var activeConnection = open(url, alreadyHave)
                connection = activeConnection
                var code = activeConnection.responseCode
                if (alreadyHave > 0 && code != HttpURLConnection.HTTP_PARTIAL) {
                    // Server ignored Range: restart cleanly.
                    temporary.delete()
                    activeConnection.disconnect()
                    activeConnection = open(url, 0L)
                    connection = activeConnection
                    code = activeConnection.responseCode
                }
                if (code < 200 || code >= 300) {
                    error("Sunucu $code döndürdü; indirme başlatılamadı.")
                }
                val finalUrl = activeConnection.url
                if (finalUrl.protocol != "https") {
                    error("İndirme güvenli olmayan (https olmayan) bir adrese yönlendirildi; iptal edildi.")
                }

                val startOffset = if (code == HttpURLConnection.HTTP_PARTIAL) alreadyHave else 0L
                val total: Long? = fullTotal(activeConnection, startOffset)
                val remaining = if (total != null) total - startOffset else -1L
                if (total != null && remaining <= 0) {
                    finalize(target, temporary)
                    withContext(Dispatchers.Main) { listener.onDone(target) }
                    return@launch
                }
                if (total != null) {
                    val available = android.os.StatFs(directory.absolutePath).availableBytes
                    if (remaining > available - 128L * 1024 * 1024) {
                        error("Depolama yetersiz. Gerekli: ${(remaining / 1_000_000_000.0)} GB, boş: ${(available / 1_000_000_000.0)} GB.")
                    }
                }

                activeConnection.inputStream.use { input ->
                    FileOutputStream(temporary, startOffset > 0L).use { output ->
                        var copied = startOffset
                        var lastPercent = if (total == null) -1 else ((copied * 100L) / total).toInt()
                        var lastReported = copied
                        val buffer = ByteArray(1024 * 256)
                        while (true) {
                            if (!coroutineContext.isActive) throw CancellationException()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            var report = false
                            if (total != null) {
                                val percent = ((copied * 100L) / total).toInt()
                                if (percent > lastPercent) {
                                    lastPercent = percent
                                    report = true
                                }
                            } else if (copied - lastReported >= REPORT_CHUNK) {
                                lastReported = copied
                                report = true
                            }
                            if (report) {
                                val at = copied
                                val currentTotal = total ?: -1L
                                withContext(Dispatchers.Main) { listener.onProgress(lastPercent.coerceAtLeast(0), at, currentTotal) }
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (total != null && temporary.length() != total) {
                    error("Dosya tam inmedi (${temporary.length()}/${total}); tekrar deneyebilirsin.")
                }
                finalize(target, temporary)
                withContext(Dispatchers.Main) { listener.onDone(target) }
            } catch (cancelled: CancellationException) {
                // Keep the .part file so the next "İndir" resumes instead of restarting 0.8–2.4 GB.
                throw cancelled
            } catch (failure: Throwable) {
                withContext(NonCancellable + Dispatchers.Main) {
                    listener.onError(failure.message ?: "İndirme tamamlanamadı.")
                }
            } finally {
                connection?.disconnect()
                currentUrl = null
                running.set(false)
            }
        }
        synchronized(gate) { job = started }
        return true
    }

    private fun open(url: String, rangeStart: Long): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "LocalRP/0.7.4 (+android)")
        if (rangeStart > 0L) connection.setRequestProperty("Range", "bytes=$rangeStart-")
        return connection
    }

    /** Resolves the full file size across 206 (Content-Range) and 200 (Content-Length) replies. */
    private fun fullTotal(connection: HttpURLConnection, startOffset: Long): Long? {
        if (connection.responseCode == HttpURLConnection.HTTP_PARTIAL) {
            val range = connection.getHeaderField("Content-Range")
            if (range != null) {
                val total = range.substringAfterLast('/').toLongOrNull()
                if (total != null && total > 0) return total
            }
            val remaining = connection.contentLengthLong
            if (remaining > 0) return startOffset + remaining
        }
        val length = connection.contentLengthLong
        return if (length > 0) length else null
    }

    private fun finalize(target: File, temporary: File) {
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }
}
