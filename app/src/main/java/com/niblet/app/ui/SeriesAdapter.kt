package com.niblet.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.niblet.app.data.CoverCache
import com.niblet.app.data.model.Series
import com.niblet.app.data.source.LocalFolderSource
import com.niblet.app.databinding.ItemSeriesBinding
import kotlinx.coroutines.launch

class SeriesAdapter(
    private val local: () -> LocalFolderSource?,
    private val onClick: (Series) -> Unit
) : RecyclerView.Adapter<SeriesAdapter.VH>() {

    private val items = ArrayList<Series>()
    fun submit(list: List<Series>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemSeriesBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(private val b: ItemSeriesBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: Series) {
            b.title.text = s.title
            b.subtitle.text = "${s.chapterCount} chapters"
            b.root.setOnClickListener { onClick(s) }

            val ctx = b.root.context
            val key = "${s.sourceId}:${s.id}"
            val cached = CoverCache.get(ctx, key)
            if (cached != null) { b.cover.setImageBitmap(cached); return }
            b.cover.setImageDrawable(null)
            b.cover.setBackgroundColor(0xFF1E1E1E.toInt())

            // Only attempt local sources for now (network cover extraction is expensive)
            if (!s.sourceId.startsWith("local")) return
            val src = local() ?: return
            val owner = ctx as? androidx.lifecycle.LifecycleOwner ?: return
            owner.lifecycleScope.launch {
                val bmp: Bitmap? = CoverCache.extract(ctx, src, s)
                if (bmp != null) {
                    CoverCache.put(ctx, key, bmp)
                    if (itemView.isAttachedToWindow &&
                        bindingAdapterPosition != RecyclerView.NO_POSITION) {
                        b.cover.setImageBitmap(bmp)
                    }
                }
            }
        }
    }
}
