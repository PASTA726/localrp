package app.localrp

import java.io.File
import java.net.URL
import java.security.MessageDigest

/**
 * Pure decision layer for GGUF downloads: URL policy, file-name hardening,
 * resumable-range validation and SHA-256 verification. No Android imports so it
 * stays unit-testable on the JVM. ModelDownload consults it at every risky point;
 * a download may never proceed, resume or install on the strength of an unchecked
 * server response.
 */
object DownloadPolicy {

    enum class Verdict { RESUME, RESTART }

    /** Where a request at [rangeStart] bytes should continue, and the trusted total. */
    class Decision(val verdict: Verdict, val total: Long?)

    /** Downloads must begin on https; redirects are re-checked by ModelDownload after following. */
    fun isHttps(url: String): Boolean =
        try {
            URL(url).protocol == "https"
        } catch (failure: Exception) {
            false
        }

    /**
     * The downloader only ever writes model files into its own directory, so the target
     * name must be a plain ".gguf" file name (no separators, no traversal, no dotfiles).
     * @return the usable name, or null when the name is unsafe (download refused).
     */
    fun sanitizeFileName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains('/') || trimmed.contains('\\')) return null
        if (trimmed.contains("..")) return null
        if (trimmed.startsWith('.')) return null
        if (trimmed.any { it.code < 32 }) return null
        if (trimmed.length > 96) return null
        return if (trimmed.endsWith(".gguf")) trimmed else null
    }

    /**
     * Validates whether an existing partial file can genuinely be appended to.
     * A 206 reply is trusted only when its Content-Range literally starts at the bytes
     * we already hold; a server that ignored the Range header or answers with an
     * inconsistent range forces a clean restart instead of a corrupt splice.
     */
    fun resumeDecision(responseCode: Int, rangeStart: Long, contentRangeHeader: String?, contentLength: Long): Decision {
        if (rangeStart <= 0L) {
            val total = if (responseCode in 200..299 && contentLength > 0) contentLength else null
            return Decision(Verdict.RESUME, total) // fresh fetch, nothing to append
        }
        if (responseCode == 206) {
            val range = parseContentRange(contentRangeHeader)
            if (range != null && range.first == rangeStart && range.third > rangeStart) {
                return Decision(Verdict.RESUME, range.third)
            }
            return Decision(Verdict.RESTART, null)
        }
        // 200 to a Range request means the server ignored it (or another 2xx shape):
        // appending would duplicate data. Restart from zero.
        return Decision(Verdict.RESTART, null)
    }

    /** Parses "bytes start-end/total" or returns null (absent, malformed, inconsistent). */
    fun parseContentRange(header: String?): Triple<Long, Long, Long>? {
        if (header == null) return null
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").let { pattern ->
            pattern.matchEntire(header.trim())
        } ?: return null
        val (start, end, total) = match.destructured
        val s = start.toLongOrNull() ?: return null
        val e = end.toLongOrNull() ?: return null
        val t = total.toLongOrNull() ?: return null
        if (t <= 0 || s > e || e >= t) return null
        return Triple(s, e, t)
    }

    /** Streaming SHA-256, lowercase hex; null when the file is unreadable. */
    fun sha256Hex(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    } catch (failure: Exception) {
        null
    }

    /** Trusted-vs-actual check; comparison is case-insensitive over hex digests. */
    fun shaMatches(expected: String, actual: String): Boolean =
        expected.trim().length == 64 && expected.trim().equals(actual, ignoreCase = true)
}
