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
 * Supports cancel, resumable downloads (HTTP Range validated against Content-Range)
 * and simple progress reporting; no API keys are used or stored.
 *
 * Every risky step consults the pure [DownloadPolicy] layer: https-only URLs, hardened
 * file names, range-resume only on a verified literal offset match, final size and
 * (when the catalog supplies one) SHA-256 verification. A download can never finish
 * as a "successful" model on the strength of an unchecked server response.
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

    /**
     * @param expectedBytes trusted catalog byte count; when > 0 the finished file must match.
     * @param expectedSha256 trusted catalog digest; only values shipping inside the app may be
     *   passed (a hash delivered over the same network proves nothing and must never be used).
     * @return false when refused (another download in flight or still unwinding).
     */
    fun start(
        context: Context,
        url: String,
        displayName: String,
        listener: Listener,
        expectedBytes: Long = -1L,
        expectedSha256: String? = null,
    ): Boolean {
        synchronized(gate) {
            if (!running.compareAndSet(false, true)) return false
            currentUrl = url
        }
        val app = context.applicationContext
        val started = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var connection: HttpURLConnection? = null
            try {
                if (currentUrl != url) return@launch // cancel() raced between start() and job assignment
                val safeName = DownloadPolicy.sanitizeFileName(displayName)
                if (safeName == null || !DownloadPolicy.isHttps(url)) {
                    withContext(NonCancellable + Dispatchers.Main) {
                        listener.onError("İndirme reddedildi: hedef dosya adı veya adres geçersiz.")
                    }
                    return@launch
                }
                val target = File(LocalAiRuntime.modelsDirectory(app), safeName)
                withContext(Dispatchers.Main) { listener.onProgress(0, 0L, -1L) }
                val directory = target.parentFile ?: error("Model klasörü yok.")
                if (!directory.exists() && !directory.mkdirs()) error("Model klasörü oluşturulamadı.")
                val temporary = File(directory, target.name + ".part")
                var alreadyHave = if (temporary.isFile) temporary.length() else 0L

                var activeConnection = open(url, alreadyHave)
                connection = activeConnection
                var code = activeConnection.responseCode
                if (alreadyHave > 0L) {
                    // 206 is appendable only when the server's range literally resumes at
                    // our offset; a server that ignored Range or answers inconsistently
                    // forces a clean restart instead of a corrupt blind append.
                    val decision = DownloadPolicy.resumeDecision(
                        code, alreadyHave,
                        activeConnection.getHeaderField("Content-Range"),
                        activeConnection.contentLengthLong)
                    if (decision.verdict == DownloadPolicy.Verdict.RESTART) {
                        temporary.delete()
                        alreadyHave = 0L
                        activeConnection.disconnect()
                        activeConnection = open(url, 0L)
                        connection = activeConnection
                        code = activeConnection.responseCode
                    }
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
                    if (expectedBytes > 0 && temporary.length() != expectedBytes) {
                        temporary.delete()
                        error("Dosya boyutu beklenenden farklı (${temporary.length()}/${expectedBytes}); indirilmedi.")
                    }
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
                if (expectedBytes > 0 && temporary.length() != expectedBytes) {
                    temporary.delete()
                    error("Dosya boyutu katalog bekleneniyle uyuşmuyor (${temporary.length()}/${expectedBytes}); indirilmedi.")
                }
                if (expectedSha256 != null) {
                    val actual = DownloadPolicy.sha256Hex(temporary)
                    if (actual == null || !DownloadPolicy.shaMatches(expectedSha256, actual)) {
                        temporary.delete()
                        error("SHA-256 doğrulaması başarısız; dosya silindi, indirme tekrar denenebilir.")
                    }
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
        connection.setRequestProperty("User-Agent", "LocalRP/0.13.0-alpha1 (+android)")
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (rangeStart > 0L) connection.setRequestProperty("Range", "bytes=$rangeStart-")
        return connection
    }

    /** Resolves the full file size across 206 (Content-Range) and 200 (Content-Length) replies. */
    private fun fullTotal(connection: HttpURLConnection, startOffset: Long): Long? {
        if (connection.responseCode == HttpURLConnection.HTTP_PARTIAL) {
            val range = DownloadPolicy.parseContentRange(connection.getHeaderField("Content-Range"))
            if (range != null && range.third > startOffset) return range.third
            val rest = connection.contentLengthLong
            if (rest > 0) return startOffset + rest
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
