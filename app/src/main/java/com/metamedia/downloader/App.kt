package com.metamedia.downloader

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.metamedia.downloader.core.DownloadStore
import com.metamedia.downloader.core.Prefs
import com.metamedia.downloader.core.YtDlpEngine
import com.metamedia.downloader.util.Notifications
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // السمة قبل إنشاء أي Activity
        AppCompatDelegate.setDefaultNightMode(Prefs.themeMode(this))

        Notifications.createChannels(this)
        DownloadStore.init(this)

        // تهيئة محرك yt-dlp وFFmpeg (Python مدمج)
        runCatching {
            YoutubeDL.getInstance().init(this)
            FFmpeg.init(this)
        }

        // تحديث تلقائي إذا تجاوز إصدار yt-dlp 80 يوماً
        scope.launch {
            val newVersion = runCatching { YtDlpEngine.autoUpdateIfStale(this@App) }.getOrNull()
            if (!newVersion.isNullOrBlank()) {
                Notifications.notifyUpdateDone(this@App, newVersion)
            }
        }
    }
}
