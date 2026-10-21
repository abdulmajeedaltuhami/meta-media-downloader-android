package com.metamedia.downloader.core

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * غلاف حول محرك yt-dlp المدمج (Python).
 * كل الخيارات المشتركة (User-Agent، الكوكيز، تأخير الطلبات) تُطبَّق هنا.
 */
object YtDlpEngine {

    /**
     * User-Agent حديث يحاكي Chrome على أندرويد.
     * ملاحظة: yt-dlp نفسه يرسل UA افتراضياً؛ تخصيصه يقلل الحظر على بعض المواقع.
     */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val STALE_DAYS = 80L

    // ---------------------------------------------------------------- الفحص
    /**
     * فحص الرابط وإرجاع كل الصيغ المتاحة.
     * getInfo يضيف --dump-json تلقائياً في مكتبة youtubedl-android.
     */
    fun inspect(context: Context, url: String, playlist: Boolean): VideoInfo {
        val request = YoutubeDLRequest(url)
        applyCommonOptions(context, request, forDownload = false)
        if (!playlist) request.addOption("--no-playlist")
        return YoutubeDL.getInstance().getInfo(request)
    }

    // ---------------------------------------------------------------- التنزيل
    fun buildDownloadRequest(
        context: Context,
        item: DownloadItem,
        tempDir: File
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(item.url)
        applyCommonOptions(context, request, forDownload = true)

        val format = item.format
        if (format.isAudio) {
            request.addOption("-f", "bestaudio/best")
            request.addOption("-x")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0")
        } else {
            // إذا كانت الصيغة تحتوي صوتاً مدمجاً نستخدمها مباشرة،
            // وإلا ندمجها مع أفضل مسار صوتي.
            val selector = if (format.hasAudio) {
                format.formatId
            } else {
                "${format.formatId}+bestaudio/${format.formatId}/best[height<=${format.height}]"
            }
            request.addOption("-f", selector)
            request.addOption("--merge-output-format", "mp4")
        }

        if (!item.playlist) request.addOption("--no-playlist")

        if (item.subtitles) {
            request.addOption("--write-subs")
            request.addOption("--write-auto-subs")
            request.addOption("--sub-langs", "ar,en")
            request.addOption("--embed-subs")
        }

        // ننزّل إلى مجلد مؤقت داخل التطبيق ثم ننشر الملف إلى MediaStore/SAF
        request.addOption(
            "-o",
            File(tempDir, "%(title).150B [%(id)s].%(ext)s").absolutePath
        )
        // يطبع المسار النهائي للملف بعد الدمج — نستخدمه لنقل الملف
        request.addOption("--print", "after_move:filepath")
        request.addOption("--no-mtime")
        return request
    }

    // ---------------------------------------------------------------- خيارات مشتركة
    private fun applyCommonOptions(
        context: Context,
        request: YoutubeDLRequest,
        forDownload: Boolean
    ) {
        request.addOption("--user-agent", USER_AGENT)

        // عميل الويب هو الأكثر استقراراً حالياً لتفادي طلبات 429
        request.addOption("--extractor-args", "youtube:player_client=web")

        if (Prefs.hasCookies(context)) {
            request.addOption("--cookies", Prefs.cookiesFile(context).absolutePath)
        }

        if (forDownload) {
            request.addOption("--retries", 5)
            request.addOption("--fragment-retries", 5)
            // تأخير بين الطلبات لتقليل الحظر (لا نطبّقه على الفحص ليبقى سريعاً)
            request.addOption("--sleep-interval", 2)
            request.addOption("--max-sleep-interval", 5)
            request.addOption("--sleep-requests", 1)
        }
    }

    // ---------------------------------------------------------------- الإصدار والتحديث
    fun currentVersion(context: Context): String? =
        runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()

    /** يستخرج تاريخ الإصدار (مثل 2025.01.15) ويحسب عمره بالأيام. */
    fun versionAgeDays(version: String?): Long? {
        if (version.isNullOrBlank()) return null
        val match = Regex("""(\d{4})\.(\d{2})\.(\d{2})""").find(version) ?: return null
        return runCatching {
            val date = LocalDate.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt()
            )
            ChronoUnit.DAYS.between(date, LocalDate.now())
        }.getOrNull()
    }

    /**
     * تحديث yt-dlp. يجب استدعاؤه من Dispatchers.IO.
     * @return الإصدار الجديد إن نجح التحديث، أو null.
     */
    fun update(context: Context): String? {
        val status = YoutubeDL.getInstance()
            .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        return when (status) {
            YoutubeDL.UpdateStatus.DONE,
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> currentVersion(context)
            else -> null
        }
    }

    /**
     * التحقق التلقائي عند تشغيل التطبيق:
     * إذا تجاوز إصدار yt-dlp 80 يوماً، نحدّثه في الخلفية (مرة كل 24 ساعة كحد أقصى).
     */
    suspend fun autoUpdateIfStale(context: Context): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - Prefs.lastUpdateCheck(context) < UPDATE_INTERVAL_MS) return@withContext null
        Prefs.setLastUpdateCheck(context, now)

        val version = currentVersion(context) ?: return@withContext null
        Prefs.setLastKnownVersion(context, version)

        val age = versionAgeDays(version) ?: return@withContext null
        if (age < STALE_DAYS) return@withContext null

        // لا نحدّث أثناء وجود تنزيل جارٍ
        if (DownloadStore.snapshot().any { it.status == DownloadStatus.RUNNING }) {
            return@withContext null
        }

        runCatching { update(context) }.getOrNull()?.also {
            Prefs.setLastKnownVersion(context, it)
        }
    }

    /** هل الخطأ ناتج عن تجاوز حد الطلبات؟ */
    fun isRateLimited(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val m = message.lowercase()
        return m.contains("429") || m.contains("too many requests") || m.contains("rate limit")
    }
}
