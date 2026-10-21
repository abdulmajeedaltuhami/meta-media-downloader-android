package com.metamedia.downloader.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.metamedia.downloader.R
import com.metamedia.downloader.core.DownloadItem
import com.metamedia.downloader.core.DownloadStatus
import com.metamedia.downloader.core.DownloadStore
import com.metamedia.downloader.core.YtDlpEngine
import com.metamedia.downloader.ui.MainActivity
import com.metamedia.downloader.util.MediaStoreHelper
import com.metamedia.downloader.util.Notifications
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue

/**
 * خدمة أمامية (ForegroundService) تدير طابور التنزيلات.
 *
 * لماذا ForegroundService؟
 *  - يمنع النظام من قتل التنزيل عند تصغير التطبيق.
 *  - يسمح بإشعار دائم يعرض النسبة والسرعة والحجم مع زر إلغاء.
 */
class DownloadService : Service() {

    companion object {
        const val ACTION_START = "com.metamedia.downloader.action.START"
        const val ACTION_CANCEL = "com.metamedia.downloader.action.CANCEL"
        const val EXTRA_ITEM = "extra_item"
        const val EXTRA_ID = "extra_id"

        /** إضافة تنزيل إلى الطابور وبدء الخدمة. */
        fun enqueue(context: Context, item: DownloadItem) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ITEM, item.toJson().toString())
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** إلغاء تنزيل جارٍ أو في الانتظار. */
        fun cancel(context: Context, id: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_ID, id)
            }
            runCatching { context.startService(intent) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = LinkedBlockingQueue<DownloadItem>()
    private val lock = Any()

    private var worker: Job? = null
    private var currentId: String? = null
    @Volatile private var cancelRequested = false
    private var lastNotifyAt = 0L

    private val fgsType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // يجب استدعاء startForeground خلال 5 ثوانٍ من startForegroundService
        startForegroundCompat(initialNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                intent.getStringExtra(EXTRA_ID)?.let { cancelItem(it) }
            }
            ACTION_START -> {
                val json = intent.getStringExtra(EXTRA_ITEM)
                if (json != null) {
                    runCatching { DownloadItem.fromJson(JSONObject(json)) }
                        .getOrNull()
                        ?.let { handleNewItem(it) }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        worker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ الطابور
    private fun handleNewItem(item: DownloadItem) {
        if (DownloadStore.isDuplicate(item.url, item.format.formatId)) {
            DownloadStore.add(
                item.copy(
                    status = DownloadStatus.FAILED,
                    error = getString(R.string.duplicate_download)
                )
            )
            Notifications.finished(this, item, false)
            stopIfIdle()
            return
        }
        DownloadStore.add(item.copy(status = DownloadStatus.QUEUED))
        queue.put(item)
        ensureWorker()
    }

    private fun ensureWorker() {
        synchronized(lock) {
            if (worker?.isActive == true) return
            worker = scope.launch {
                while (true) {
                    val item = queue.poll()
                    if (item == null) {
                        synchronized(lock) {
                            if (queue.isEmpty()) {
                                worker = null
                                stopSelf()
                                return@launch
                            }
                        }
                        continue
                    }
                    runDownload(item)
                }
            }
        }
    }

    private fun stopIfIdle() {
        synchronized(lock) {
            if (queue.isEmpty() && worker?.isActive != true) {
                stopSelf()
            }
        }
    }

    private fun cancelItem(id: String) {
        if (currentId == id) {
            cancelRequested = true
            // إنهاء عملية yt-dlp فوراً
            runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
            DownloadStore.updateAndPersist(id) {
                it.copy(
                    status = DownloadStatus.CANCELED,
                    speed = "",
                    error = "أُلغي بواسطة المستخدم"
                )
            }
            Notifications.canceled(this)
        } else {
            val removed = queue.removeAll { it.id == id }
            if (removed) {
                DownloadStore.updateAndPersist(id) {
                    it.copy(status = DownloadStatus.CANCELED, error = "أُلغي قبل البدء")
                }
            }
        }
    }

    // ------------------------------------------------------------ التنزيل
    private suspend fun runDownload(item: DownloadItem) = withContext(Dispatchers.IO) {
        currentId = item.id
        cancelRequested = false

        DownloadStore.updateAndPersist(item.id) {
            it.copy(status = DownloadStatus.RUNNING, progress = 0f, error = null)
        }
        notifyProgress(item, 0, "", 0L, item.format.filesizeBytes, indeterminate = true)

        val tempDir = File(cacheDir, "downloads/${item.id}").apply { mkdirs() }

        try {
            val request = YtDlpEngine.buildDownloadRequest(this@DownloadService, item, tempDir)

            val response = YoutubeDL.getInstance().execute(request, item.id) { progress, _, line ->
                if (!cancelRequested) {
                    val pct = progress.coerceIn(0f, 100f)
                    val speed = parseSpeed(line)
                    val total = parseTotalBytes(line) ?: item.format.filesizeBytes
                    val done = if (total > 0) (total * pct / 100f).toLong() else 0L
                    DownloadStore.update(item.id) {
                        it.copy(
                            progress = pct,
                            speed = speed,
                            downloadedBytes = done,
                            totalBytes = total
                        )
                    }
                    notifyProgress(item, pct.toInt(), speed, done, total)
                }
                Unit
            }

            if (cancelRequested) throw CancellationException("canceled")

            if (response.exitCode != 0) {
                val message = response.err.ifBlank { response.out }
                throw IOException(message.takeLast(1200).ifBlank { "فشل التنزيل" })
            }

            // نجمع كل الملفات الناتجة (فيديو + ترجمة + عناصر القائمة)
            val files = tempDir.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                .toList()

            if (files.isEmpty()) throw IOException("لم يتم العثور على الملف الناتج")

            var firstPath: String? = null
            files.forEach { file ->
                val published = MediaStoreHelper.publishFile(this@DownloadService, file)
                if (firstPath == null) firstPath = published
                runCatching { file.delete() }
            }

            DownloadStore.updateAndPersist(item.id) {
                it.copy(
                    status = DownloadStatus.DONE,
                    progress = 100f,
                    speed = "",
                    filePath = firstPath,
                    error = null
                )
            }
            Notifications.finished(this@DownloadService, item, true)
        } catch (e: Exception) {
            if (cancelRequested || e is CancellationException) {
                DownloadStore.updateAndPersist(item.id) {
                    it.copy(
                        status = DownloadStatus.CANCELED,
                        speed = "",
                        error = "أُلغي بواسطة المستخدم"
                    )
                }
                Notifications.canceled(this@DownloadService)
            } else {
                DownloadStore.updateAndPersist(item.id) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        speed = "",
                        error = (e.message ?: e.toString()).take(1200)
                    )
                }
                Notifications.finished(this@DownloadService, item, false)
            }
        } finally {
            runCatching { tempDir.deleteRecursively() }
            currentId = null
            cancelRequested = false
        }
    }

    // ------------------------------------------------------------ الإشعار
    private fun initialNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(getString(R.string.notif_preparing))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun notifyProgress(
        item: DownloadItem,
        percent: Int,
        speed: String,
        downloaded: Long,
        total: Long,
        indeterminate: Boolean = false
    ) {
        val now = SystemClock.elapsedRealtime()
        if (!indeterminate && now - lastNotifyAt < 500) return
        lastNotifyAt = now

        val notification = Notifications.progress(
            this, item, percent, speed, downloaded, total, indeterminate
        )
        startForegroundCompat(notification)
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                Notifications.PROGRESS_ID,
                notification,
                fgsType
            )
        }
    }

    // ------------------------------------------------------------ تحليل مخرجات yt-dlp
    private val speedRegex = Regex("""at\s+([\d.]+)\s*([KMGTP]?i?B)/s""", RegexOption.IGNORE_CASE)
    private val sizeRegex = Regex("""of\s+~?\s*([\d.]+)\s*([KMGTP]?i?B)""", RegexOption.IGNORE_CASE)

    private fun parseSpeed(line: String): String {
        val m = speedRegex.find(line) ?: return ""
        val value = m.groupValues[1].toDoubleOrNull() ?: return ""
        val unit = m.groupValues[2].uppercase().replace("IB", "B")
        return String.format(java.util.Locale.US, "%.1f %s/s", value, unit)
    }

    private fun parseTotalBytes(line: String): Long? {
        val m = sizeRegex.find(line) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = m.groupValues[2].uppercase().replace("IB", "B")
        val multiplier = when (unit) {
            "B" -> 1L
            "KB" -> 1024L
            "MB" -> 1024L * 1024
            "GB" -> 1024L * 1024 * 1024
            "TB" -> 1024L * 1024 * 1024 * 1024
            else -> 1L
        }
        return (value * multiplier).toLong()
    }
}
