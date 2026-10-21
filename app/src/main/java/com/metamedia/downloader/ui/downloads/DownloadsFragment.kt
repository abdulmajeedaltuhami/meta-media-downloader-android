package com.metamedia.downloader.ui.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.metamedia.downloader.R
import com.metamedia.downloader.core.DownloadItem
import com.metamedia.downloader.core.DownloadStatus
import com.metamedia.downloader.core.DownloadStore
import com.metamedia.downloader.databinding.FragmentDownloadsBinding
import com.metamedia.downloader.service.DownloadService
import com.metamedia.downloader.util.MediaStoreHelper
import com.metamedia.downloader.util.gone
import com.metamedia.downloader.util.snack
import com.metamedia.downloader.util.visible

/**
 * شاشة "التحميلات": قائمة حية مرتبطة بـ DownloadStore (LiveData).
 * تعرض النسبة والسرعة أثناء التنزيل، وتتيح الإلغاء/إعادة المحاولة/الفتح/الحذف.
 */
class DownloadsFragment : Fragment(), DownloadActions {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: DownloadAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DownloadAdapter(this)
        binding.downloadsList.layoutManager = LinearLayoutManager(requireContext())
        binding.downloadsList.adapter = adapter

        DownloadStore.items.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            if (list.isEmpty()) {
                binding.emptyState.visible()
                binding.downloadsList.gone()
            } else {
                binding.emptyState.gone()
                binding.downloadsList.visible()
            }
        }
    }

    // ------------------------------------------------------------ أحداث العناصر
    override fun onCancel(item: DownloadItem) {
        DownloadService.cancel(requireContext(), item.id)
    }

    override fun onRetry(item: DownloadItem) {
        // نعيد الإرسال كعنصر جديد بنفس البيانات (معرّف جديد) لتجنّب تعارض الحالة
        val retry = item.copy(
            id = java.util.UUID.randomUUID().toString(),
            status = DownloadStatus.QUEUED,
            progress = 0f,
            speed = "",
            downloadedBytes = 0L,
            totalBytes = 0L,
            error = null,
            filePath = null,
            createdAt = System.currentTimeMillis()
        )
        DownloadStore.remove(item.id)
        DownloadService.enqueue(requireContext(), retry)
        snack(getString(R.string.download_started))
    }

    override fun onOpen(item: DownloadItem) {
        val path = item.filePath ?: return
        MediaStoreHelper.openFile(requireContext(), path)
    }

    override fun onDelete(item: DownloadItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(item.title)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                item.filePath?.let { MediaStoreHelper.deleteFile(requireContext(), it) }
                DownloadStore.remove(item.id)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
