package com.niblet.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.niblet.app.data.model.Chapter
import com.niblet.app.databinding.ItemChapterBinding

class ChapterAdapter(private val onClick: (Chapter) -> Unit) :
    RecyclerView.Adapter<ChapterAdapter.VH>() {

    private val items = ArrayList<Chapter>()
    fun submit(list: List<Chapter>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemChapterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(private val b: ItemChapterBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: Chapter) {
            b.title.text = c.title
            b.root.setOnClickListener { onClick(c) }
        }
    }
}
