package com.fatbug.hovr

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fatbug.hovr.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val items: List<HistoryManager.HistoryEntry>,
    private val historyManager: HistoryManager,
    private val onItemClick: (String) -> Unit,
    private val onItemDelete: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    inner class VH(val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = items[position]

        holder.b.tvName.text = entry.name
        holder.b.tvTime.text = historyManager.relativeTime(entry.timestamp)
        holder.b.tvGifBadge.visibility =
            if (entry.isGif) View.VISIBLE else View.GONE

        // Load thumbnail for local URIs only
        if (!entry.key.startsWith("http")) {
            Thread {
                try {
                    val uri = Uri.parse(entry.key)
                    val bmp = holder.b.root.context.contentResolver
                        .openInputStream(uri)?.use { stream ->
                            val opts = android.graphics.BitmapFactory.Options().apply {
                                inSampleSize = 4
                            }
                            BitmapFactory.decodeStream(stream, null, opts)
                        }
                    if (bmp != null) {
                        Handler(Looper.getMainLooper()).post {
                            holder.b.ivThumb.setImageBitmap(bmp)
                        }
                    }
                } catch (_: Exception) {}
            }.start()
        }

        holder.b.root.setOnClickListener { onItemClick(entry.key) }
        holder.b.btnDelete.setOnClickListener { onItemDelete(entry.key) }
    }
}
