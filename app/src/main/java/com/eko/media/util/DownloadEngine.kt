package com.eko.media.util

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object DownloadEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // no timeout for download
        .build()

    data class Progress(
        val downloaded: Long,
        val total: Long,
        val percent: Int,
        val speedBps: Long,
        val done: Boolean = false,
        val filePath: String? = null,
        val error: String? = null
    )

    /**
     * Download file from [url] and emit progress via [onProgress].
     * Returns final file path or null on error.
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: suspend (Progress) -> Unit
    ): String? = withContext(Dispatchers.IO) {

        val dir = getDownloadDir(context)
        val safeFileName = sanitizeFileName(fileName)
        val file = File(dir, safeFileName)

        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "EkoMedia/1.0 (Android)")
                .build()

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) {
                onProgress(Progress(0, 0, 0, 0, error = "HTTP ${response.code()}"))
                return@withContext null
            }

            val totalBytes = response.body()?.contentLength() ?: -1L
            var downloadedBytes = 0L
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            FileOutputStream(file).use { fos ->
                response.body()?.byteStream()?.use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int

                    while (isActive) {
                        read = input.read(buffer)
                        if (read == -1) break

                        fos.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime
                        if (elapsed >= 500) {
                            val speed = ((downloadedBytes - lastBytes) * 1000L) / elapsed
                            val percent = if (totalBytes > 0)
                                (downloadedBytes * 100 / totalBytes).toInt()
                            else 0

                            onProgress(Progress(
                                downloaded = downloadedBytes,
                                total      = totalBytes,
                                percent    = percent,
                                speedBps   = speed
                            ))

                            lastTime  = now
                            lastBytes = downloadedBytes
                        }
                    }
                }
            }

            onProgress(Progress(
                downloaded = downloadedBytes,
                total      = totalBytes,
                percent    = 100,
                speedBps   = 0,
                done       = true,
                filePath   = file.absolutePath
            ))

            file.absolutePath

        } catch (e: Exception) {
            file.delete()
            onProgress(Progress(0, 0, 0, 0, error = e.message ?: "Download failed"))
            null
        }
    }

    fun getDownloadDir(context: Context): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "EkoMedia"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun formatSpeed(bps: Long): String = when {
        bps >= 1_048_576 -> "%.1f MB/s".format(bps / 1_048_576.0)
        bps >= 1_024     -> "%.0f KB/s".format(bps / 1_024.0)
        else             -> "$bps B/s"
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024         -> "%.0f KB".format(bytes / 1_024.0)
        else                   -> "$bytes B"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(200)
    }
}
