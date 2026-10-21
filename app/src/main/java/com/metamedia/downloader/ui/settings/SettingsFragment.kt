package com.metamedia.downloader.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.metamedia.downloader.R
import com.metamedia.downloader.core.DownloadStore
import com.metamedia.downloader.core.Prefs
import com.metamedia.downloader.core.YtDlpEngine
import com.metamedia.downloader.databinding.FragmentSettingsBinding
import com.metamedia.downloader.util.gone
import com.metamedia.downloader.util.showAlert
import com.metamedia.downloader.util.snack
import com.metamedia.downloader.util.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * شاشة الإعدادات: السمة، مسار الحفظ (SAF)، الكوكيز، تحديث yt-dlp، والبيانات.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val pickSaveTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { onSaveTreePicked(it) } }

    private val pickCookies = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importCookies(it) } }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTheme()
        setupSaveFolder()
        setupCookies()
        setupYtDlp()
        setupData()
    }

    // ------------------------------------------------------------ السمة
    private fun setupTheme() {
        val checked = when (Prefs.themeMode(requireContext())) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.theme_light
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.theme_dark
            else -> R.id.theme_system
        }
        binding.themeGroup.check(checked)

        binding.themeGroup.addOnButtonCheckedListener { _, id, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (id) {
                R.id.theme_light -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.theme_dark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            Prefs.setThemeMode(requireContext(), mode)
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    // ------------------------------------------------------------ مسار الحفظ
    private fun setupSaveFolder() {
        binding.savePath.text = Prefs.saveTree(requireContext())
            ?: getString(R.string.default_location)

        binding.chooseSaveFolder.setOnClickListener { pickSaveTree.launch(null) }
        binding.resetSaveFolder.setOnClickListener {
            Prefs.setSaveTree(requireContext(), null)
            binding.savePath.text = getString(R.string.default_location)
            snack(getString(R.string.reset))
        }
    }

    private fun onSaveTreePicked(uri: Uri) {
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        Prefs.setSaveTree(requireContext(), uri.toString())
        binding.savePath.text = uri.toString()
        snack(getString(R.string.folder_selected))
    }

    // ------------------------------------------------------------ الكوكيز
    private fun setupCookies() {
        updateCookiesStatus()
        binding.importCookies.setOnClickListener {
            pickCookies.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
        }
        binding.deleteCookies.setOnClickListener {
            if (Prefs.cookiesFile(requireContext()).delete()) {
                updateCookiesStatus()
                snack(getString(R.string.delete_cookies))
            }
        }
    }

    private fun importCookies(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val input = requireContext().contentResolver.openInputStream(uri)
                        ?: error("تعذّر فتح الملف")
                    input.use { src ->
                        Prefs.cookiesFile(requireContext()).outputStream().use { src.copyTo(it) }
                    }
                }
            }.isSuccess
            if (ok) {
                updateCookiesStatus()
                snack(getString(R.string.cookies_imported))
            } else {
                requireContext().showAlert(
                    getString(R.string.cookies), getString(R.string.error_generic, uri.toString())
                )
            }
        }
    }

    private fun updateCookiesStatus() {
        binding.cookiesStatus.text = if (Prefs.hasCookies(requireContext())) {
            getString(R.string.cookies_imported)
        } else {
            getString(R.string.no_cookies)
        }
        binding.deleteCookies.isEnabled = Prefs.hasCookies(requireContext())
    }

    // ------------------------------------------------------------ yt-dlp
    private fun setupYtDlp() {
        loadVersion()
        binding.updateButton.setOnClickListener { updateEngine() }
    }

    private fun loadVersion() {
        viewLifecycleOwner.lifecycleScope.launch {
            val version = withContext(Dispatchers.IO) {
                YtDlpEngine.currentVersion(requireContext())
            }
            binding.ytdlpVersion.text = version?.let {
                getString(R.string.ytdlp_version, it)
            } ?: "—"
        }
    }

    private fun updateEngine() {
        binding.updateButton.isEnabled = false
        binding.updateProgress.visible()
        binding.ytdlpVersion.text = getString(R.string.updating)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { YtDlpEngine.update(requireContext()) }
            }
            binding.updateProgress.gone()
            binding.updateButton.isEnabled = true

            result.onSuccess { version ->
                Prefs.setLastKnownVersion(requireContext(), version)
                binding.ytdlpVersion.text = version?.let {
                    getString(R.string.ytdlp_version, it)
                } ?: "—"
                snack(getString(R.string.update_done))
            }.onFailure {
                loadVersion()
                requireContext().showAlert(
                    getString(R.string.ytdlp), getString(R.string.update_failed)
                )
            }
        }
    }

    // ------------------------------------------------------------ البيانات
    private fun setupData() {
        binding.openFolder.setOnClickListener { openDownloadsFolder() }
        binding.clearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_history)
                .setMessage(R.string.about_text)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    DownloadStore.clearAll()
                    snack(getString(R.string.history_cleared))
                }
                .show()
        }
    }

    private fun openDownloadsFolder() {
        val tree = Prefs.saveTree(requireContext())
        val intent = if (tree != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(tree), "vnd.android.document/directory")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            // فتح جذر التخزين في تطبيق "الملفات" كبديل افتراضي
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("content://com.android.externalstorage.documents/root/primary"),
                    "vnd.android.document/directory"
                )
            }
        }
        runCatching { startActivity(intent) }
            .onFailure { snack(getString(R.string.no_app_to_open)) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
