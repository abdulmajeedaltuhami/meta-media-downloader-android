package com.metamedia.downloader.ui.download

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.metamedia.downloader.R
import com.metamedia.downloader.core.DownloadItem
import com.metamedia.downloader.core.DownloadStore
import com.metamedia.downloader.core.FormatMapper
import com.metamedia.downloader.core.MediaFormat
import com.metamedia.downloader.core.YtDlpEngine
import com.metamedia.downloader.core.formatDuration
import com.metamedia.downloader.databinding.FragmentDownloadBinding
import com.metamedia.downloader.service.DownloadService
import com.metamedia.downloader.util.gone
import com.metamedia.downloader.util.showAlert
import com.metamedia.downloader.util.snack
import com.metamedia.downloader.util.visible
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * شاشة التنزيل:
 *  1) لصق الرابط ثم "فحص" → yt-dlp --dump-json
 *  2) تعبئة القائمة المنسدلة بالصيغ + الأحجام
 *  3) زر التنزيل معطّل حتى ينتهي الفحص
 */
class DownloadFragment : Fragment() {

    private var _binding: FragmentDownloadBinding? = null
    private val binding get() = _binding!!

    private var formats: List<MediaFormat> = emptyList()
    private var inspected: VideoInfo? = null
    private var inspectedUrl: String? = null
    private var inspecting = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.inspectButton.setOnClickListener { inspect() }

        // زر "فحص" من لوحة المفاتيح
        binding.urlInput.setOnEditorActionListener { _, _, _ ->
            inspect(); true
        }

        binding.downloadFab.setOnClickListener { startDownload() }

        // أي تعديل على الرابط يُبطل نتيجة الفحص السابقة
        binding.urlInput.doAfterTextChanged {
            if (inspectedUrl != null && binding.urlInput.text.toString().trim() != inspectedUrl) {
                resetInspection()
            }
        }

        // تغيير خيار "قائمة كاملة" يغيّر نتيجة الفحص، لذا نُبطلها
        binding.chipPlaylist.setOnCheckedChangeListener { _, _ ->
            if (inspectedUrl != null) resetInspection()
        }
    }

    // ------------------------------------------------------------ الفحص
    private fun inspect() {
        if (inspecting) return
        val url = binding.urlInput.text?.toString()?.trim().orEmpty()
        if (!isValidUrl(url)) {
            binding.urlLayout.error = getString(R.string.invalid_url)
            return
        }
        binding.urlLayout.error = null

        inspecting = true
        setBusy(true)
        resetInspection(keepBusy = true)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    YtDlpEngine.inspect(requireContext(), url, binding.chipPlaylist.isChecked)
                }
            }
            inspecting = false
            setBusy(false)

            result.onSuccess { info -> onInspected(url, info) }
                .onFailure { e -> onInspectError(e) }
        }
    }

    private fun onInspected(url: String, info: VideoInfo) {
        val mapped = FormatMapper.fromInfo(info)
        if (mapped.isEmpty()) {
            snack(getString(R.string.no_formats))
            return
        }

        inspected = info
        inspectedUrl = url
        formats = mapped

        // معلومات الفيديو
        binding.videoTitle.text = info.title ?: url
        val meta = buildList {
            info.uploader?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (info.duration > 0) add(formatDuration(info.duration))
            info.height.takeIf { it > 0 }?.let { add("${it}p") }
        }.joinToString(" • ")
        binding.videoMeta.text = meta
        binding.infoCard.visible()

        // القائمة المنسدلة
        val labels = formats.map { it.display() }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            labels
        )
        binding.formatInput.setAdapter(adapter)
        binding.formatInput.setText(labels.first(), false)
        binding.formatLayout.isEnabled = true
        binding.formatInput.isEnabled = true

        binding.downloadFab.isEnabled = true
        binding.downloadHint.text = getString(R.string.quality_label)
    }

    private fun onInspectError(e: Throwable) {
        val message = e.message ?: e.toString()
        if (YtDlpEngine.isRateLimited(message)) {
            requireContext().showAlert(
                getString(R.string.error_429_title),
                getString(R.string.error_429_msg)
            )
        } else {
            requireContext().showAlert(
                getString(R.string.video_info),
                getString(R.string.error_generic, message.take(400))
            )
        }
    }

    // ------------------------------------------------------------ التنزيل
    private fun startDownload() {
        val info = inspected ?: return
        val url = inspectedUrl ?: return
        val index = formats.indexOfFirst { it.display() == binding.formatInput.text.toString() }
            .takeIf { it >= 0 } ?: 0
        val format = formats.getOrNull(index) ?: return

        if (DownloadStore.isDuplicate(url, format.formatId)) {
            snack(getString(R.string.duplicate_download))
            return
        }

        val item = DownloadItem(
            url = url,
            title = info.title ?: url,
            format = format,
            subtitles = binding.chipSubtitles.isChecked,
            playlist = binding.chipPlaylist.isChecked
        )
        DownloadService.enqueue(requireContext(), item)
        snack(getString(R.string.download_started))
    }

    // ------------------------------------------------------------ مساعدات
    private fun setBusy(busy: Boolean) {
        binding.inspectButton.isEnabled = !busy
        binding.inspectProgress.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) binding.downloadFab.isEnabled = false
    }

    private fun resetInspection(keepBusy: Boolean = false) {
        inspected = null
        inspectedUrl = null
        formats = emptyList()
        binding.infoCard.gone()
        binding.formatLayout.isEnabled = false
        binding.formatInput.isEnabled = false
        binding.formatInput.setText("", false)
        if (!keepBusy) binding.downloadFab.isEnabled = false
        binding.downloadHint.text = getString(R.string.inspect_first)
    }

    private fun isValidUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** امتداد صغير لتشغيل كود بعد كل تغيير نصي دون تكرار TextWatcher. */
private fun android.widget.EditText.doAfterTextChanged(block: () -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = block()
    })
}
