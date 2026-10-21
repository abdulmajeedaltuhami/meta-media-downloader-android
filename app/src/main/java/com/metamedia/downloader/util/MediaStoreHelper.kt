package com.metamedia.downloader.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.metamedia.downloader.core.Prefs
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * نشر الملفات في مكان يظهر للمستخدم مع احترام Scoped Storage:
 *  - إذا اختار المستخدم مجلداً عبر SAF ننسخ إليه.
 *  - وإلا نستخدم MediaStore (أندرويد 10+) أو التنزيلات المباشرة (8-9).
 */
object MediaStoreHelper {

    private const val SUB_FOLDER = "MetaVideosBot"

    /** نشر ملف موجود على القرص. يعيد وصفاً للمسار/URI. */
    fun publishFile(context: Context, file: File): String {
        val mime = guessMime(file.name)
        val tree = Prefs.saveTree(context)
        if (tree != null) {
            runCatching { copyToTree(context, Uri.parse(tree), file.name, mime, file.inputStream()) }
                .onSuccess { return it }
            // إذا فشل SAF نكمل إلى MediaStore
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            insertIntoMediaStore(context, file.name, mime, file.inputStream())
        } else {
            copyToLegacyDownloads(context, file, mime)
        }
    }

    /**
     * نشر تدفق (يُستخدم لحالات واتساب).
     * نُفرّغ التدفق أولاً إلى ملف مؤقت لأن التدفق لا يمكن قراءته مرتين،
     * وبذلك يبقى مسار الاحتياط (MediaStore) صالحاً حتى لو فشل نسخ SAF.
     */
    fun publishStream(context: Context, input: InputStream, name: String, mime: String): String {
        val dir = File(context.cacheDir, "streams").apply { mkdirs() }
        val temp = uniqueFile(dir, name)
        try {
            input.use { src -> temp.outputStream().use { src.copyTo(it) } }
            return publishFile(context, temp)
        } finally {
            runCatching { temp.delete() }
            runCatching { dir.delete() }
        }
    }

    // ------------------------------------------------------------ SAF
    private fun copyToTree(
        context: Context,
        treeUri: Uri,
        name: String,
        mime: String,
        input: InputStream
    ): String {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("تعذّر الوصول إلى المجلد المحدد")
        if (!root.canWrite()) throw IOException("المجلد للقراءة فقط")
        val targetName = uniqueDocumentName(root, name)
        val doc = root.createFile(mime, targetName)
            ?: throw IOException("تعذّر إنشاء الملف في المجلد")
        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            input.use { it.copyTo(out) }
        } ?: throw IOException("تعذّر فتح الملف للكتابة")
        return doc.uri.toString()
    }

    private fun uniqueDocumentName(root: DocumentFile, name: String): String {
        if (root.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (root.findFile(candidate) == null) return candidate
            i++
        }
    }

    // ------------------------------------------------------------ MediaStore
    private fun insertIntoMediaStore(
        context: Context,
        name: String,
        mime: String,
        input: InputStream
    ): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$SUB_FOLDER"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: throw IOException("تعذّر إنشاء سجل في MediaStore")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                input.use { it.copyTo(out) }
            } ?: throw IOException("تعذّر فتح مجرى الكتابة")

            val done = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
            return uri.toString()
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    // ------------------------------------------------------------ قديم (API 26-28)
    private fun legacyDir(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            SUB_FOLDER
        )

    private fun copyToLegacyDownloads(context: Context, file: File, mime: String): String {
        val dir = legacyDir()
        dir.mkdirs()
        val out = uniqueFile(dir, file.name)
        file.inputStream().use { input -> out.outputStream().use { input.copyTo(it) } }
        MediaScannerConnection.scanFile(
            context, arrayOf(out.absolutePath), arrayOf(mime), null
        )
        return out.absolutePath
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i)$ext")
            i++
        }
        return candidate
    }

    // ------------------------------------------------------------ فتح/حذف
    fun openFile(context: Context, location: String) {
        val uri = toUri(context, location)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, guessMime(location))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }

    fun deleteFile(context: Context, location: String) {
        runCatching {
            if (location.startsWith("content://")) {
                context.contentResolver.delete(Uri.parse(location), null, null)
            } else {
                File(location).delete()
            }
        }
    }

    private fun toUri(context: Context, location: String): Uri {
        if (location.startsWith("content://")) return Uri.parse(location)
        val file = File(location)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "vtt", "srt" -> "text/plain"
        else -> "application/octet-stream"
    }
}
