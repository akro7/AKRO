package com.eko.media.util

import com.eko.media.model.Platform
import com.eko.media.model.VideoFormat
import com.eko.media.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * EKO MEDIA ENGINE — Video Info Extractor
 *
 * Uses cobalt.tools API (free, no API key) as primary.
 * Falls back to noembed for thumbnails.
 *
 * For production, integrate yt-dlp binary via NDK or
 * use a self-hosted yt-dlp JSON endpoint.
 */
object VideoExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        
        .build()

    // ─────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────

    sealed class Result {
        data class Success(val info: VideoInfo) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun extract(url: String): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val platform = Platform.detect(url)

            // Try cobalt.tools first
            val cobaltResult = tryCobalt(url, platform)
            if (cobaltResult is Result.Success) return@withContext cobaltResult

            // Fallback — build minimal info from noembed/oembed
            val oembedResult = tryOembed(url, platform)
            oembedResult

        } catch (e: Exception) {
            Result.Error("EKO Engine error: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────
    // cobalt.tools (supports YouTube, Twitter, TikTok, etc.)
    // ─────────────────────────────────────────────────────────

    private fun tryCobalt(url: String, platform: Platform): Result {
        return try {
            val body = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"),
                """{"url":"$url","vCodec":"h264","vQuality":"1080","aFormat":"mp3","isAudioMuted":false}"""
            )

            val req = Request.Builder()
                .url("https://api.cobalt.tools/api/json")
                .post(body)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(req).execute()
            val json = JSONObject(response.body()?.string() ?: "{}")

            val status = json.optString("status")
            if (status == "stream" || status == "redirect" || status == "tunnel") {
                val directUrl = json.optString("url")

                // Build format list — cobalt gives one direct link
                val formats = buildList {
                    add(VideoFormat("1080p", "mp4", "1080", null))
                    add(VideoFormat("720p",  "mp4", "720",  null))
                    add(VideoFormat("480p",  "mp4", "480",  null))
                    add(VideoFormat("360p",  "mp4", "360",  null))
                    add(VideoFormat("Audio Only", "mp3", "audio", null, isAudioOnly = true))
                }

                val info = VideoInfo(
                    title     = json.optString("filename", "Video").removeSuffix(".mp4"),
                    thumbnail = "",
                    duration  = "–",
                    author    = platform.displayName,
                    platform  = platform,
                    formats   = formats
                )
                Result.Success(info)
            } else {
                Result.Error(json.optString("text", "cobalt failed"))
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "cobalt error")
        }
    }

    // ─────────────────────────────────────────────────────────
    // oEmbed fallback (YouTube / public platforms)
    // ─────────────────────────────────────────────────────────

    private fun tryOembed(url: String, platform: Platform): Result {
        return try {
            val oembedUrl = "https://noembed.com/embed?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
            val req = Request.Builder().url(oembedUrl).build()
            val response = client.newCall(req).execute()
            val json = JSONObject(response.body()?.string() ?: "{}")

            val title = json.optString("title", "Unknown Video")
            val author = json.optString("author_name", platform.displayName)
            val thumb = json.optString("thumbnail_url", "")

            val formats = listOf(
                VideoFormat("1080p", "mp4", "1080", null),
                VideoFormat("720p",  "mp4", "720",  null),
                VideoFormat("480p",  "mp4", "480",  null),
                VideoFormat("360p",  "mp4", "360",  null),
                VideoFormat("Audio Only", "mp3", "audio", null, isAudioOnly = true)
            )

            Result.Success(
                VideoInfo(
                    title     = title,
                    thumbnail = thumb,
                    duration  = "–",
                    author    = author,
                    platform  = platform,
                    formats   = formats
                )
            )
        } catch (e: Exception) {
            Result.Error("Cannot extract: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────
    // Build cobalt download URL for a specific quality
    // ─────────────────────────────────────────────────────────

    suspend fun buildDownloadUrl(sourceUrl: String, format: VideoFormat): String? =
        withContext(Dispatchers.IO) {
            try {
                val quality = when (format.formatId) {
                    "1080" -> "1080"
                    "720"  -> "720"
                    "480"  -> "480"
                    "360"  -> "360"
                    "audio" -> "max"
                    else    -> "720"
                }

                val isAudio = format.isAudioOnly

                val bodyStr = if (isAudio) {
                    """{"url":"$sourceUrl","isAudioOnly":true,"aFormat":"mp3"}"""
                } else {
                    """{"url":"$sourceUrl","vCodec":"h264","vQuality":"$quality","isAudioMuted":false}"""
                }

                val body = okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse("application/json"), bodyStr
                )
                val req = Request.Builder()
                    .url("https://api.cobalt.tools/api/json")
                    .post(body)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(req).execute()
                val json = JSONObject(response.body()?.string() ?: "{}")
                val status = json.optString("status")
                if (status in listOf("stream", "redirect", "tunnel")) {
                    json.optString("url").takeIf { it.isNotBlank() }
                } else null
            } catch (e: Exception) {
                null
            }
        }
}
