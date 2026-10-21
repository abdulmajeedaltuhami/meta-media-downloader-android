package com.metamedia.downloader.core

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray

/**
 * مصدر الحقيقة الوحيد لسجل التحميلات.
 * - LiveData للواجهة.
 * - حفظ JSON في SharedPreferences ليبقى السجل بعد إغلاق التطبيق.
 */
object DownloadStore {

    private val _items = MutableLiveData<List<DownloadItem>>(emptyList())
    val items: LiveData<List<DownloadItem>> = _items

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        load()
    }

    fun snapshot(): List<DownloadItem> = _items.value.orEmpty()

    fun add(item: DownloadItem) {
        val list = snapshot().filterNot { it.id == item.id } + item
        _items.postValue(list.sortedByDescending { it.createdAt })
        persist()
    }

    fun update(id: String, block: (DownloadItem) -> DownloadItem) {
        val list = snapshot().map { if (it.id == id) block(it) else it }
        _items.postValue(list)
    }

    /** تحديث مع حفظ فوري (للحالات النهائية). */
    fun updateAndPersist(id: String, block: (DownloadItem) -> DownloadItem) {
        update(id, block)
        persist()
    }

    fun remove(id: String) {
        _items.postValue(snapshot().filterNot { it.id == id })
        persist()
    }

    fun clearAll() {
        _items.postValue(emptyList())
        appContext?.let { Prefs.clearHistory(it) }
    }

    fun clearFinished() {
        _items.postValue(
            snapshot().filter {
                it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED
            }
        )
        persist()
    }

    /** منع تكرار نفس الرابط مع نفس الصيغة إذا كان مكتملاً. */
    fun isDuplicate(url: String, formatId: String): Boolean = snapshot().any {
        it.url == url && it.format.formatId == formatId && it.status == DownloadStatus.DONE
    }

    private fun load() {
        val ctx = appContext ?: return
        val raw = Prefs.history(ctx) ?: return
        val loaded = runCatching {
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    var item = DownloadItem.fromJson(o)
                    // أي تنزيل كان يعمل عند إغلاق التطبيق يُعتبر متوقفاً
                    if (item.status == DownloadStatus.RUNNING || item.status == DownloadStatus.QUEUED) {
                        item = item.copy(
                            status = DownloadStatus.FAILED,
                            error = "توقف التطبيق أثناء التنزيل"
                        )
                    }
                    add(item)
                }
            }
        }.getOrDefault(emptyList())
        _items.postValue(loaded.sortedByDescending { it.createdAt })
    }

    private fun persist() {
        val ctx = appContext ?: return
        val arr = org.json.JSONArray()
        snapshot().take(200).forEach { arr.put(it.toJson()) }
        Prefs.setHistory(ctx, arr.toString())
    }
}
