package com.metamedia.downloader.core

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.io.File

/**
 * تخزين إعدادات التطبيق في SharedPreferences.
 * كل الدوال تقبل Context لتفادي تسريب الذاكرة.
 */
object Prefs {

    private const val FILE = "meta_media_prefs"

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------- السمة ----------
    fun themeMode(c: Context): Int =
        sp(c).getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setThemeMode(c: Context, mode: Int) =
        sp(c).edit().putInt("theme_mode", mode).apply()

    // ---------- مسار الحفظ (SAF) ----------
    fun saveTree(c: Context): String? = sp(c).getString("save_tree", null)

    fun setSaveTree(c: Context, uri: String?) =
        sp(c).edit().putString("save_tree", uri).apply()

    // ---------- مجلد حالات واتساب (SAF) ----------
    fun statusTree(c: Context): String? = sp(c).getString("status_tree", null)

    fun setStatusTree(c: Context, uri: String?) =
        sp(c).edit().putString("status_tree", uri).apply()

    // ---------- الكوكيز ----------
    fun cookiesFile(c: Context): File = File(c.filesDir, "cookies.txt")

    fun hasCookies(c: Context): Boolean = cookiesFile(c).exists()

    // ---------- تحديث yt-dlp ----------
    fun lastUpdateCheck(c: Context): Long = sp(c).getLong("last_update_check", 0L)

    fun setLastUpdateCheck(c: Context, time: Long) =
        sp(c).edit().putLong("last_update_check", time).apply()

    fun lastKnownVersion(c: Context): String? = sp(c).getString("ytdlp_version", null)

    fun setLastKnownVersion(c: Context, v: String?) =
        sp(c).edit().putString("ytdlp_version", v).apply()

    // ---------- سجل التحميلات ----------
    fun history(c: Context): String? = sp(c).getString("history", null)

    fun setHistory(c: Context, json: String) =
        sp(c).edit().putString("history", json).apply()

    fun clearHistory(c: Context) =
        sp(c).edit().remove("history").apply()
}
