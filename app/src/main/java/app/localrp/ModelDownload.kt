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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Streams a GGUF file from a direct HTTPS URL into the app's model directory.
 * Supports cancel and simple progress reporting; no API keys are used or stored.
 */
object ModelDownload {
    interface Listener {
        fun onProgress(percent: Int, downloadedBytes: Long)
        fun onDone(file: File)
        fun onError(message: String)
    }

    @Volatile private var job: Job? = null
    @Volatile private var active = false
    @Volatile var currentUrl: String? = null
        private set

    fun isActive(): Boolean = active

    fun cancel() {
        job?.cancel()
    }

    fun start(context: Context, url: String, displayName: String, listener: Listener) {
        if (active) return
        val app = context.applicationContext
        val target = File(LocalAiRuntime.modelsDirectory(app), displayName)
        active = true
        currentUrl = url
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) { listener.onProgress(0, 0L) }
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "LocalRP/0.7 (+android)")
                val code = connection.responseCode
                if (code < 200 || code >= 300) {
                    error("Sunucu $code döndürdü; indirme başlatılamadı.")
                }
                val total = try {
                    connection.contentLengthLong.takeIf { it > 0 }
                } catch (_: Exception) { null }
                val directory = target.parentFile ?: error("Model klasörü yok.")
                if (!directory.exists() && !directory.mkdirs()) error("Model klasörü oluşturulamadı.")
                val available = android.os.StatFs(directory.absolutePath).availableBytes
                if (total != null && total > available - 128L * 1024 * 1024) {
                    error("Depolama yetersiz. Gerekli: ${(total / 1_000_000_000.0)} GB, boş: ${(available / 1_000_000_000.0)} GB.")
                }
                val temporary = File(directory, target.name + ".part")
                temporary.delete()
                connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(1024 * 256)
                        var copied = 0L
                        var lastPercent = -1
                        while (true) {
                            if (!active) throw CancellationException()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total != null) {
                                val percent = ((copied * 100L) / total).toInt()
                                if (percent > lastPercent) {
                                    lastPercent = percent
                                    withContext(Dispatchers.Main) { listener.onProgress(percent, copied) }
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (total != null && temporary.length() != total) {
                    temporary.delete()
                    error("Dosya tam inmedi; tekrar deneyebilirsin.")
                }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                active = false
                currentUrl = null
                withContext(Dispatchers.Main) { listener.onDone(target) }
            } catch (cancelled: CancellationException) {
                active = false
                currentUrl = null
                File(target.path + ".part").delete()
                throw cancelled
            } catch (failure: Throwable) {
                active = false
                currentUrl = null
                File(target.path + ".part").delete()
                withContext(NonCancellable + Dispatchers.Main) {
                    listener.onError(failure.message ?: "İndirme tamamlanamadı.")
                }
            }
        }
    }
}
