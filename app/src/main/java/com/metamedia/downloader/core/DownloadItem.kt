package com.metamedia.downloader.core

import org.json.JSONObject
import java.util.UUID

enum class DownloadStatus { QUEUED, RUNNING, DONE, FAILED, CANCELED }

/**
 * عنصر تنزيل واحد. يُخزَّن كـ JSON في السجل، ويُمرَّر إلى الخدمة عبر Intent.
 */
data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val format: MediaFormat,
    val subtitles: Boolean = false,
    val playlist: Boolean = false,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val speed: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("url", url)
        put("title", title)
        put("format", format.toJson())
        put("subtitles", subtitles)
        put("playlist", playlist)
        put("status", status.name)
        put("progress", progress.toDouble())
        put("speed", speed)
        put("downloadedBytes", downloadedBytes)
        put("totalBytes", totalBytes)
        put("filePath", filePath ?: JSONObject.NULL)
        put("error", error ?: JSONObject.NULL)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = DownloadItem(
            id = o.optString("id", UUID.randomUUID().toString()),
            url = o.optString("url"),
            title = o.optString("title", o.optString("url")),
            format = MediaFormat.fromJson(o.optJSONObject("format") ?: JSONObject()),
            subtitles = o.optBoolean("subtitles", false),
            playlist = o.optBoolean("playlist", false),
            status = runCatching {
                DownloadStatus.valueOf(o.optString("status", "QUEUED"))
            }.getOrDefault(DownloadStatus.QUEUED),
            progress = o.optDouble("progress", 0.0).toFloat(),
            speed = o.optString("speed"),
            downloadedBytes = o.optLong("downloadedBytes"),
            totalBytes = o.optLong("totalBytes"),
            filePath = if (o.isNull("filePath")) null else o.optString("filePath"),
            error = if (o.isNull("error")) null else o.optString("error"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }
}
