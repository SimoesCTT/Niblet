package com.niblet.app.reader

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.niblet.app.databinding.ItemWebtoonPageBinding

class WebtoonReader(
    private val onNearEnd: () -> Unit,
    private val onNearStart: () -> Unit
) : RecyclerView.Adapter<WebtoonReader.VH>() {

    private val pages = mutableListOf<Bitmap>()

    /** Append a page at the end (forward load). */
    fun append(bmp: Bitmap) {
        pages.add(bmp)
        notifyItemInserted(pages.size - 1)
    }

    /** Prepend a page at the top (backward load). */
    fun prepend(bmp: Bitmap) {
        pages.add(0, bmp)
        notifyItemInserted(0)
    }

    fun clear() {
        pages.clear()
        notifyDataSetChanged()
    }

    fun size(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemWebtoonPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(pages[position])
        // forward trigger
        if (position >= pages.size - 2) onNearEnd()
        // backward trigger
        if (position <= 1) onNearStart()
    }

    override fun getItemCount() = pages.size

    inner class VH(private val b: ItemWebtoonPageBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(bmp: Bitmap) {
            b.image.setImageBitmap(bmp)
        }
    }
}
