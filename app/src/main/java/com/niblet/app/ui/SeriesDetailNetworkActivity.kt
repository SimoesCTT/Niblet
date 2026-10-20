package com.niblet.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.niblet.app.data.SourceStore
import com.niblet.app.data.model.Chapter
import com.niblet.app.data.source.SourceManager
import com.niblet.app.databinding.ActivitySeriesDetailBinding
import kotlinx.coroutines.launch

class SeriesDetailNetworkActivity : AppCompatActivity() {

    private lateinit var b: ActivitySeriesDetailBinding
    private lateinit var adapter: ChapterAdapter
    private lateinit var sourceId: String
    private lateinit var titleStr: String
    private var chapters: List<Chapter> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySeriesDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        sourceId = intent.getStringExtra("source_id")!!
        titleStr = intent.getStringExtra("series_title")!!
        title = titleStr

        adapter = ChapterAdapter { openChapter(it) }
        b.chapters.layoutManager = LinearLayoutManager(this)
        b.chapters.adapter = adapter

        val ns = SourceStore.load(this).firstOrNull { it.id == sourceId } ?: run {
            finish(); return
        }

        lifecycleScope.launch {
            val src = SourceManager.open(this@SeriesDetailNetworkActivity, ns) ?: return@launch
            runCatching { src.listChapters(titleStr) }.onSuccess {
                chapters = it
                adapter.submit(it)
            }
        }
    }

    private fun openChapter(c: Chapter) {
        val idx = chapters.indexOfFirst { it.id == c.id }
        val prev = if (idx > 0) chapters[idx - 1] else null
        val next = if (idx >= 0 && idx < chapters.size - 1) chapters[idx + 1] else null

        startActivity(Intent(this, ReaderNetworkActivity::class.java).apply {
            putExtra("source_id", sourceId)
            putExtra("chapter_id", c.id)
            putExtra("chapter_title", c.title)
            putExtra("chapter_uri", c.fileUri)
            putExtra("series_title", titleStr)
            putExtra("prev_id", prev?.id)
            putExtra("prev_title", prev?.title)
            putExtra("prev_uri", prev?.fileUri)
            putExtra("next_id", next?.id)
            putExtra("next_title", next?.title)
            putExtra("next_uri", next?.fileUri)
        })
    }
}
