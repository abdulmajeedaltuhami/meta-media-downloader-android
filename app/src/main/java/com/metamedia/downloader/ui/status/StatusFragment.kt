package com.metamedia.downloader.ui.status

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.metamedia.downloader.R
import com.metamedia.downloader.core.Prefs
import com.metamedia.downloader.databinding.FragmentStatusBinding
import com.metamedia.downloader.util.MediaStoreHelper
import com.metamedia.downloader.util.gone
import com.metamedia.downloader.util.showAlert
import com.metamedia.downloader.util.snack
import com.metamedia.downloader.util.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * شاشة حالات واتساب.
 *
 * المشكلة: منذ أندرويد 11 (Scoped Storage) لم يعد بالإمكان قراءة
 * Android/media/com.whatsapp/WhatsApp/Media/.Statuses بمسار مباشر.
 * الحل: SAF عبر ACTION_OPEN_DOCUMENT_TREE مع تخزين الإذن بشكل دائم،
 * ثم البحث عن مجلد ".Statuses" داخل المجلد الذي اختاره المستخدم.
 */
class StatusFragment : Fragment() {

    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: StatusAdapter

    private val pickTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { onTreePicked(it) } }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = StatusAdapter { item -> saveItem(item) }
        binding.statusGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.statusGrid.adapter = adapter

        binding.pickFolder.setOnClickListener {
            if (!isWhatsAppInstalled()) {
                requireContext().showAlert(
                    getString(R.string.nav_status),
                    getString(R.string.whatsapp_not_installed)
                )
                return@setOnClickListener
            }
            pickTree.launch(null)
        }

        binding.swipeRefresh.setOnRefreshListener { refresh() }

        val saved = Prefs.statusTree(requireContext())
        if (saved != null) {
            binding.folderPath.text = saved
            refresh()
        } else {
            showEmpty(getString(R.string.status_folder_none))
        }
    }

    // ------------------------------------------------------------ اختيار المجلد
    private fun onTreePicked(uri: Uri) {
        // نحتفظ بالإذن بعد إعادة تشغيل التطبيق
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        Prefs.setStatusTree(requireContext(), uri.toString())
        binding.folderPath.text = uri.toString()
        snack(getString(R.string.folder_selected))
        refresh()
    }

    // ------------------------------------------------------------ قراءة الحالات
    private fun refresh() {
        val tree = Prefs.statusTree(requireContext()) ?: return
        binding.statusProgress.visible()
        binding.swipeRefresh.isRefreshing = true

        viewLifecycleOwner.lifecycleScope.launch {
            val items = runCatching {
                withContext(Dispatchers.IO) { loadStatuses(Uri.parse(tree)) }
            }.getOrElse { emptyList() }

            binding.statusProgress.gone()
            binding.swipeRefresh.isRefreshing = false
            adapter.submitList(items)

            if (items.isEmpty()) {
                showEmpty(getString(R.string.empty_status))
            } else {
                binding.statusEmpty.gone()
                binding.statusGrid.visible()
            }
        }
    }

    /** يبحث عن مجلد ".Statuses" داخل الشجرة المختارة (حتى عمق 3). */
    private fun loadStatuses(treeUri: Uri): List<StatusItem> {
        val root = DocumentFile.fromTreeUri(requireContext(), treeUri) ?: return emptyList()
        val statusesDir = findStatusesDir(root) ?: return emptyList()

        return statusesDir.listFiles()
            .filter { it.isFile }
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                val mime = file.type ?: MediaStoreHelper.guessMime(name)
                val isVideo = mime.startsWith("video/")
                val isImage = mime.startsWith("image/")
                if (!isVideo && !isImage) return@mapNotNull null
                StatusItem(file.uri, name, mime, isVideo)
            }
            .sortedByDescending { it.name }
    }

    /** بحث BFS محدود العمق عن مجلد اسمه .Statuses. */
    private fun findStatusesDir(root: DocumentFile): DocumentFile? {
        if (root.name == ".Statuses" && root.isDirectory) return root

        var level = listOf(root)
        repeat(3) {
            val next = mutableListOf<DocumentFile>()
            for (dir in level) {
                for (child in dir.listFiles()) {
                    if (!child.isDirectory) continue
                    if (child.name == ".Statuses") return child
                    next += child
                }
            }
            level = next
        }
        return null
    }

    // ------------------------------------------------------------ حفظ الحالة
    private fun saveItem(item: StatusItem) {
        binding.statusProgress.visible()
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val input = requireContext().contentResolver.openInputStream(item.uri)
                        ?: error("تعذّر فتح الملف")
                    input.use {
                        MediaStoreHelper.publishStream(
                            requireContext(), it, item.name, item.mime
                        )
                    }
                }
            }.isSuccess
            binding.statusProgress.gone()
            if (ok) snack(getString(R.string.saved))
            else snack(getString(R.string.error_generic, item.name))
        }
    }

    private fun showEmpty(message: String) {
        binding.statusEmpty.visible()
        binding.statusEmpty.text = message
        binding.statusGrid.gone()
    }

    private fun isWhatsAppInstalled(): Boolean {
        val pm = requireContext().packageManager
        return runCatching { pm.getPackageInfo("com.whatsapp", 0) }.isSuccess ||
            runCatching { pm.getPackageInfo("com.whatsapp.w4b", 0) }.isSuccess
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
