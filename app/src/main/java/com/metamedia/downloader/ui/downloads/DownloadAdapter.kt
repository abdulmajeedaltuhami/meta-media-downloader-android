package com.metamedia.downloader.ui.downloads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.metamedia.downloader.R
import com.metamedia.downloader.core.DownloadItem
import com.metamedia.downloader.core.DownloadStatus
import com.metamedia.downloader.core.formatBytes
import com.metamedia.downloader.databinding.ItemDownloadBinding
import com.metamedia.downloader.util.gone
import com.metamedia.downloader.util.visible

/** واجهة الأحداث التي تُبلّغ الشاشة بنقرات المستخدم على أزرار العنصر. */
interface DownloadActions {
    fun onCancel(item: DownloadItem)
    fun onRetry(item: DownloadItem)
    fun onOpen(item: DownloadItem)
    fun onDelete(item: DownloadItem)
}

/**
 * محوّل قائمة التحميلات. يستخدم ListAdapter + DiffUtil لتحديث سلس
 * أثناء تغيّر النسبة والسرعة عشرات المرات في الثانية.
 */
class DownloadAdapter(
    private val actions: DownloadActions
) : ListAdapter<DownloadItem, DownloadAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDownloadBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), actions)
    }

    class VH(private val b: ItemDownloadBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: DownloadItem, actions: DownloadActions) = with(b) {
            itemTitle.text = item.title.ifBlank { item.url }
            itemFormat.text = item.format.display()

            // شارة الحالة
            val (label, colorRes) = when (item.status) {
                DownloadStatus.QUEUED -> R.string.status_queued to R.color.on_surface_variant
                DownloadStatus.RUNNING -> R.string.status_running to R.color.primary
                DownloadStatus.DONE -> R.string.status_done to R.color.primary
                DownloadStatus.FAILED -> R.string.status_failed to R.color.error
                DownloadStatus.CANCELED -> R.string.status_canceled to R.color.on_surface_variant
            }
            itemStatus.setText(label)
            itemStatus.setTextColor(itemStatus.context.color(colorRes))

            // شريط التقدم
            val running = item.status == DownloadStatus.RUNNING
            if (running) {
                itemProgress.visible()
                itemProgress.isIndeterminate = false
                itemProgress.progress = item.progress.toInt().coerceIn(0, 100)
            } else {
                itemProgress.gone()
            }

            // التفاصيل
            val detail = when (item.status) {
                DownloadStatus.RUNNING -> buildString {
                    if (item.speed.isNotBlank()) append(item.speed)
                    if (item.totalBytes > 0) {
                        if (isNotEmpty()) append(" • ")
                        append("${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}")
                    }
                }
                DownloadStatus.DONE -> item.filePath?.let {
                    itemDetail.context.getString(R.string.saved_to, it.substringAfterLast('/'))
                }.orEmpty()
                DownloadStatus.QUEUED -> itemDetail.context.getString(R.string.status_queued)
                else -> ""
            }
            if (detail.isBlank()) itemDetail.gone() else {
                itemDetail.visible()
                itemDetail.text = detail
            }

            // رسالة الخطأ
            if (item.status == DownloadStatus.FAILED && !item.error.isNullOrBlank()) {
                itemError.visible()
                itemError.text = item.error
            } else {
                itemError.gone()
            }

            // الأزرار
            btnCancel.visibleIf(running || item.status == DownloadStatus.QUEUED)
            btnRetry.visibleIf(item.status == DownloadStatus.FAILED || item.status == DownloadStatus.CANCELED)
            btnOpen.visibleIf(item.status == DownloadStatus.DONE && item.filePath != null)
            btnDelete.visible()

            btnCancel.setOnClickListener { actions.onCancel(item) }
            btnRetry.setOnClickListener { actions.onRetry(item) }
            btnOpen.setOnClickListener { actions.onOpen(item) }
            btnDelete.setOnClickListener { actions.onDelete(item) }
        }

        private fun View.visibleIf(condition: Boolean) {
            if (condition) visible() else gone()
        }

        private fun android.content.Context.color(id: Int) =
            androidx.core.content.ContextCompat.getColor(this, id)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(a: DownloadItem, b: DownloadItem) = a.id == b.id
            override fun areContentsTheSame(a: DownloadItem, b: DownloadItem) = a == b
        }
    }
}
