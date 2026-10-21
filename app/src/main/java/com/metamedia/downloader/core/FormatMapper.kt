package com.metamedia.downloader.core

import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo

/**
 * يحوّل قائمة الصيغ الخام القادمة من yt-dlp إلى قائمة مبسّطة للمستخدم.
 *
 * القواعد:
 *  - نأخذ صيغة واحدة لكل ارتفاع (أفضل mp4 ثم الأكبر حجماً ثم الأعلى bitrate).
 *  - الحجم: filesize أولاً، ثم filesize_approx، وإلا 0 = غير معروف.
 *  - نضيف خيار صوت واحد (MP3) باستخدام أفضل مسار صوتي.
 */
object FormatMapper {

    fun fromInfo(info: VideoInfo): List<MediaFormat> {
        val raw: List<VideoFormat> = info.formats.orEmpty()
        val result = mutableListOf<MediaFormat>()

        // ---------- صيغ الفيديو ----------
        val video = raw.filter { f ->
            f.height > 0 &&
                !f.formatId.isNullOrBlank() &&
                !f.vcodec.isNullOrBlank() &&
                f.vcodec != "none"
        }

        video.groupBy { it.height }
            .toSortedMap(compareByDescending { it })
            .forEach { (height, group) ->
                // نفضّل MP4 لأنه يدعم الدمج والاشتراك، ثم الحجم المعروف، ثم bitrate
                val best = group.sortedWith(
                    compareByDescending<VideoFormat> { it.ext.equals("mp4", ignoreCase = true) }
                        .thenByDescending { it.fileSize > 0 }
                        .thenByDescending { it.tbr }
                ).first()

                val exact = best.fileSize
                val approximate = best.fileSizeApproximate
                val size = if (exact > 0) exact else approximate

                result += MediaFormat(
                    formatId = best.formatId!!,
                    label = labelForHeight(height),
                    ext = best.ext ?: "mp4",
                    height = height,
                    filesizeBytes = size,
                    approx = exact <= 0 && approximate > 0,
                    isAudio = false,
                    hasAudio = !best.acodec.isNullOrBlank() && best.acodec != "none",
                    tbr = best.tbr
                )
            }

        // ---------- خيار الصوت ----------
        val audio = raw.filter { f ->
            !f.formatId.isNullOrBlank() &&
                (f.vcodec.isNullOrBlank() || f.vcodec == "none") &&
                !f.acodec.isNullOrBlank() && f.acodec != "none"
        }
        val bestAudio = audio.maxByOrNull { if (it.abr > 0) it.abr else it.tbr }
        if (bestAudio != null) {
            val exact = bestAudio.fileSize
            val approximate = bestAudio.fileSizeApproximate
            result += MediaFormat(
                formatId = bestAudio.formatId!!,
                label = "صوت فقط",
                ext = "mp3",
                height = 0,
                filesizeBytes = if (exact > 0) exact else approximate,
                approx = exact <= 0 && approximate > 0,
                isAudio = true,
                hasAudio = true,
                tbr = if (bestAudio.abr > 0) bestAudio.abr else bestAudio.tbr
            )
        }

        // ---------- احتياط: رابط مباشر/صيغة واحدة ----------
        if (result.none { !it.isAudio } && info.height > 0 && !info.formatId.isNullOrBlank()) {
            val size = if (info.fileSize > 0) info.fileSize else info.fileSizeApproximate
            result.add(
                0,
                MediaFormat(
                    formatId = info.formatId!!,
                    label = labelForHeight(info.height),
                    ext = info.ext ?: "mp4",
                    height = info.height,
                    filesizeBytes = size,
                    approx = info.fileSize <= 0 && info.fileSizeApproximate > 0,
                    isAudio = false,
                    hasAudio = true,
                    tbr = 0
                )
            )
        }

        return result
    }

    private fun labelForHeight(height: Int): String = when {
        height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        else -> "${height}p"
    }
}
