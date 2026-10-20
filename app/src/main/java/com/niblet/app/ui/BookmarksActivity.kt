package com.niblet.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.niblet.app.data.Bookmark
import com.niblet.app.data.BookmarksStore
import com.niblet.app.databinding.ActivityBookmarksBinding
import com.niblet.app.databinding.ItemBookmarkBinding

class BookmarksActivity : AppCompatActivity() {

    private lateinit var b: ActivityBookmarksBinding
    private lateinit var adapter: BookmarksAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBookmarksBinding.inflate(layoutInflater)
        setContentView(b.root)
        title = "Bookmarks"

        adapter = BookmarksAdapter(
            onOpen = { openBookmark(it) },
            onDelete = { confirmDelete(it) }
        )
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        refresh()
    }

    private fun refresh() {
        val items = BookmarksStore.all(this)
        adapter.submit(items)
        b.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        b.list.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openBookmark(bm: Bookmark) {
        if (bm.sourceId == "local") {
            startActivity(Intent(this, ReaderActivity::class.java).apply {
                putExtra("chapter_id", bm.chapterId)
                putExtra("chapter_title", bm.chapterTitle)
                putExtra("series_title", bm.seriesTitle)
                putExtra("start_page", bm.page)
            })
        } else {
            startActivity(Intent(this, ReaderNetworkActivity::class.java).apply {
                putExtra("source_id", bm.sourceId)
                putExtra("chapter_id", bm.chapterId)
                putExtra("chapter_title", bm.chapterTitle)
                putExtra("series_title", bm.seriesTitle)
                putExtra("start_page", bm.page)
            })
        }
    }

    private fun confirmDelete(bm: Bookmark) {
        AlertDialog.Builder(this)
            .setTitle("Delete bookmark?")
            .setMessage("${bm.seriesTitle} — ${bm.chapterTitle} p.${bm.page + 1}")
            .setPositiveButton("Delete") { _, _ ->
                BookmarksStore.remove(this, bm.chapterId, bm.page)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class BookmarksAdapter(
    private val onOpen: (Bookmark) -> Unit,
    private val onDelete: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarksAdapter.VH>() {

    private val items = mutableListOf<Bookmark>()
    fun submit(list: List<Bookmark>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(private val b: ItemBookmarkBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(bm: Bookmark) {
            b.series.text = bm.seriesTitle
            b.chapter.text = "${bm.chapterTitle} — page ${bm.page + 1}"
            b.root.setOnClickListener { onOpen(bm) }
            b.root.setOnLongClickListener { onDelete(bm); true }
        }
    }
}
