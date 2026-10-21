package com.metamedia.downloader.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.metamedia.downloader.R
import com.metamedia.downloader.databinding.ActivityMainBinding
import com.metamedia.downloader.ui.download.DownloadFragment
import com.metamedia.downloader.ui.downloads.DownloadsFragment
import com.metamedia.downloader.ui.settings.SettingsFragment
import com.metamedia.downloader.ui.status.StatusFragment

/**
 * الشاشة الرئيسية: حاوية واحدة + BottomNavigationView بأربع شاشات.
 * نستخدم أسلوب add/show/hide بدل replace للحفاظ على حالة كل شاشة (الرابط، القائمة، إلخ).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /** يُستخدم من الإشعارات لفتح تبويب "التحميلات" مباشرة. */
        const val EXTRA_OPEN_DOWNLOADS = "extra_open_downloads"

        private const val TAG_DOWNLOAD = "tag_download"
        private const val TAG_DOWNLOADS = "tag_downloads"
        private const val TAG_STATUS = "tag_status"
        private const val TAG_SETTINGS = "tag_settings"
    }

    private lateinit var binding: ActivityMainBinding

    // طلب إذن الإشعارات (أندرويد 13+) — مطلوب لإظهار إشعار التقدم
    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* لا نمنع التطبيق إن رُفض؛ التنزيل يعمل لكن بلا إشعار */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            // نضيف الشاشات الأربع مرة واحدة ثم نخفي الكل عدا شاشة التنزيل
            supportFragmentManager.beginTransaction().apply {
                add(R.id.container, DownloadFragment(), TAG_DOWNLOAD)
                add(R.id.container, DownloadsFragment(), TAG_DOWNLOADS)
                add(R.id.container, StatusFragment(), TAG_STATUS)
                add(R.id.container, SettingsFragment(), TAG_SETTINGS)
            }.commitNow()

            supportFragmentManager.beginTransaction().apply {
                supportFragmentManager.fragments.forEach { f ->
                    if (f.tag != TAG_DOWNLOAD) hide(f)
                }
            }.commit()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_download -> { show(TAG_DOWNLOAD); true }
                R.id.nav_downloads -> { show(TAG_DOWNLOADS); true }
                R.id.nav_status -> { show(TAG_STATUS); true }
                R.id.nav_settings -> { show(TAG_SETTINGS); true }
                else -> false
            }
        }

        // فتح تبويب التحميلات عند الضغط على الإشعار
        if (intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true) {
            binding.bottomNav.selectedItemId = R.id.nav_downloads
        }

        askNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) {
            binding.bottomNav.selectedItemId = R.id.nav_downloads
        }
    }

    /** إظهار الشاشة المطلوبة وإخفاء البقية. */
    private fun show(tag: String) {
        val fm = supportFragmentManager
        fm.beginTransaction().apply {
            fm.fragments.forEach { f ->
                if (f.tag == tag) show(f) else hide(f)
            }
        }.commit()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
