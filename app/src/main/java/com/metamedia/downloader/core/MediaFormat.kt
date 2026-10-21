package com.metamedia.downloader.core

import org.json.JSONObject
import java.util.Locale

/**
 * صيغة تنزيل واحدة معروضة للمستخدم.
 * filesizeBytes = 0 يعني أن الحجم غير معروف.
 */
data class MediaFormat(
    val formatId: String,
    val label: String,          // مثال: 1080p أو "صوت فقط"
    val ext: String,            // mp4 / webm / mp3
    val height: Int,            // 0 للصوت
    val filesizeBytes: Long,    // الحجم الدقيق أو التقريبي
    val approx: Boolean,        // true إذا استخدمنا filesize_approx
    val isAudio: Boolean,
    val hasAudio: Boolean,      // هل الصيغة تحتوي مسار صوت مدمج؟
    val tbr: Int
) {

    /** النص المعروض في القائمة المنسدلة */
    fun display(): String {
        val size = if (filesizeBytes > 0) {
            (if (approx) "~" else "") + formatBytes(filesizeBytes)
        } else {
            "حجم غير معروف"
        }
        return if (isAudio) {
            "صوت فقط (MP3) - $size"
        } else {
            "$label (${ext.uppercase(Locale.US)}) - $size"
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("formatId", formatId)
        put("label", label)
        put("ext", ext)
        put("height", height)
        put("filesizeBytes", filesizeBytes)
        put("approx", approx)
        put("isAudio", isAudio)
        put("hasAudio", hasAudio)
        put("tbr", tbr)
    }

    companion object {
        fun fromJson(o: JSONObject) = MediaFormat(
            formatId = o.optString("formatId"),
            label = o.optString("label"),
            ext = o.optString("ext", "mp4"),
            height = o.optInt("height", 0),
            filesizeBytes = o.optLong("filesizeBytes", 0L),
            approx = o.optBoolean("approx", false),
            isAudio = o.optBoolean("isAudio", false),
            hasAudio = o.optBoolean("hasAudio", true),
            tbr = o.optInt("tbr", 0)
        )
    }
}

/** تحويل بايت إلى نص مقروء (KB/MB/GB). */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "غير معروف"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024.0 && i < units.lastIndex) {
        value /= 1024.0
        i++
    }
    return if (i == 0) {
        String.format(Locale.US, "%.0f %s", value, units[i])
    } else {
        String.format(Locale.US, "%.1f %s", value, units[i])
    }
}

/** تحويل مدة بالفيديو إلى HH:MM:SS أو MM:SS. */
fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "--:--"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
