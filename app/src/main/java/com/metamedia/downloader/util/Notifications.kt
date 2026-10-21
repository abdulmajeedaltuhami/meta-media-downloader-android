package com.metamedia.downloader.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.metamedia.downloader.R
import com.metamedia.downloader.core.DownloadItem
import com.metamedia.downloader.core.formatBytes
import com.metamedia.downloader.service.DownloadService
import com.metamedia.downloader.ui.MainActivity

/** إنشاء القنوات وبناء إشعارات التقدم. */
object Notifications {

    const val CHANNEL_DOWNLOAD = "downloads"
    const val CHANNEL_UPDATES = "updates"

    const val PROGRESS_ID = 1001
    const val UPDATE_ID = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val download = NotificationChannel(
            CHANNEL_DOWNLOAD,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
        }
        val updates = NotificationChannel(
            CHANNEL_UPDATES,
            context.getString(R.string.notif_channel_update),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(download)
        manager.createNotificationChannel(updates)
    }

    /** إشعار التقدم مع زر الإلغاء. */
    fun progress(
        context: Context,
        item: DownloadItem,
        percent: Int,
        speed: String,
        downloaded: Long,
        total: Long,
        indeterminate: Boolean = false
    ): Notification {
        val detail = if (total > 0) {
            context.getString(
                R.string.notif_progress_detail,
                percent,
                speed.ifBlank { "—" },
                formatBytes(downloaded),
                formatBytes(total)
            )
        } else {
            context.getString(
                R.string.notif_progress_percent,
                percent,
                speed.ifBlank { "—" }
            )
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            context,
            item.id.hashCode(),
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL
                putExtra(DownloadService.EXTRA_ID, item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(item.title.ifBlank { context.getString(R.string.notif_downloading, "") })
            .setContentText(detail)
            .setProgress(100, percent.coerceIn(0, 100), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_cancel,
                context.getString(R.string.cancel),
                cancelIntent
            )
            .build()
    }

    fun finished(context: Context, item: DownloadItem, success: Boolean) {
        val contentIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(if (success) R.drawable.ic_check else R.drawable.ic_error)
            .setContentTitle(item.title.ifBlank { context.getString(R.string.app_name) })
            .setContentText(
                context.getString(if (success) R.string.notif_done else R.string.notif_failed)
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .build()
        notify(context, PROGRESS_ID, notification)
    }

    fun canceled(context: Context) {
        NotificationManagerCompat.from(context).cancel(PROGRESS_ID)
    }

    fun notifyUpdateDone(context: Context, version: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_refresh)
            .setContentTitle(context.getString(R.string.ytdlp))
            .setContentText(context.getString(R.string.notif_update_done, version))
            .setAutoCancel(true)
            .build()
        notify(context, UPDATE_ID, notification)
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        // على أندرويد 13+ قد لا يكون الإذن ممنوحاً؛ نتجاهل الخطأ بهدوء
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
