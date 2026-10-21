package com.metamedia.downloader.ui.status

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.metamedia.downloader.databinding.ItemStatusBinding

/** عنصر حالة واحدة (صورة أو فيديو) مع URI من SAF. */
data class StatusItem(
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean
)

/**
 * شبكة مصغّرات الحالات. نستخدم Glide لعرض الصور، ولفيديو نطلب إطاراً مصغّراً.
 */
class StatusAdapter(
    private val onSave: (StatusItem) -> Unit
) : ListAdapter<StatusItem, StatusAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), onSave)
    }

    class VH(private val b: ItemStatusBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: StatusItem, onSave: (StatusItem) -> Unit) = with(b) {
            statusName.text = item.name

            val request = Glide.with(statusThumb)
                .load(item.uri)
                .centerCrop()
            if (item.isVideo) request.frame(1_000_000L)
            request.into(statusThumb)

            statusSave.setOnClickListener { onSave(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<StatusItem>() {
            override fun areItemsTheSame(a: StatusItem, b: StatusItem) = a.uri == b.uri
            override fun areContentsTheSame(a: StatusItem, b: StatusItem) = a == b
        }
    }
}
