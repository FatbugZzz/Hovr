package com.fatbug.hovr

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fatbug.hovr.databinding.ItemFavoriteBinding

class FavoritesAdapter(
    private val items: List<Uri>,
    private val onItemClick: (Uri) -> Unit,
    private val onItemDelete: (Uri) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.VH>() {

    inner class VH(val b: ItemFavoriteBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = items[position]

        // Load thumbnail in background thread
        Thread {
            try {
                val bmp = holder.b.root.context.contentResolver
                    .openInputStream(uri)?.use { stream ->
                        val opts = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = 4  // downsample for thumbnails
                        }
                        BitmapFactory.decodeStream(stream, null, opts)
                    }
                if (bmp != null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        holder.b.ivThumbnail.setImageBitmap(bmp)
                    }
                }
            } catch (_: Exception) {}
        }.start()

        holder.b.root.setOnClickListener { onItemClick(uri) }
        holder.b.btnDelete.setOnClickListener { onItemDelete(uri) }
    }
}
