package com.speechifier.assets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Progress for a single file download. [totalBytes] is -1 when unknown. */
data class DownloadProgress(val bytesRead: Long, val totalBytes: Long) {
    val fraction: Float get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes) else 0f
}

/**
 * Minimal HTTP file downloader (no extra dependency). Streams to a temp file and
 * atomically renames on success, so a partial download never looks complete.
 * Cancellable: respects coroutine cancellation between chunks.
 */
object Downloader {

    suspend fun download(
        url: String,
        dest: File,
        onProgress: (DownloadProgress) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
        }
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) {
                throw AssetDownloadException("HTTP ${conn.responseCode} for $url")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buffer).also { read = it } >= 0) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        done += read
                        onProgress(DownloadProgress(done, total))
                    }
                }
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
            if (e is AssetDownloadException) throw e
            throw AssetDownloadException("Download failed for $url: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }
}

/** Raised when an asset can't be downloaded; surfaced to the user. */
class AssetDownloadException(message: String) : Exception(message)
