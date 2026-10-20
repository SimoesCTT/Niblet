package com.niblet.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.niblet.app.data.TreeStore
import com.niblet.app.data.model.Chapter
import com.niblet.app.data.source.LocalFolderSource
import com.niblet.app.databinding.ActivitySeriesDetailBinding
import kotlinx.coroutines.launch

class SeriesDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySeriesDetailBinding
    private lateinit var adapter: ChapterAdapter
    private lateinit var source: LocalFolderSource
    private lateinit var seriesTitle: String
    private var chapters: List<Chapter> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeriesDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        seriesTitle = intent.getStringExtra("series_title")!!
        title = seriesTitle

        val treeUri = TreeStore.load(this) ?: run { finish(); return }
        source = LocalFolderSource(this, treeUri)

        adapter = ChapterAdapter { openChapter(it) }
        binding.chapters.layoutManager = LinearLayoutManager(this)
        binding.chapters.adapter = adapter

        lifecycleScope.launch {
            runCatching { source.listChapters(seriesTitle) }
                .onSuccess {
                    chapters = it
                    adapter.submit(it)
                }
        }
    }

    private fun openChapter(chapter: Chapter) {
        val idx = chapters.indexOfFirst { it.id == chapter.id }
        val prev = if (idx > 0) chapters[idx - 1] else null
        val next = if (idx >= 0 && idx < chapters.size - 1) chapters[idx + 1] else null

        val isEpub = chapter.fileUri.lowercase().let { it.contains(".epub") }
        if (isEpub) {
            startActivity(Intent(this, ReaderEpubTextActivity::class.java).apply {
                putExtra("chapter_id", chapter.id)
                putExtra("chapter_title", chapter.title)
                putExtra("chapter_uri", chapter.fileUri)
                putExtra("series_title", seriesTitle)
            })
            return
        }
        startActivity(Intent(this, ReaderActivity::class.java).apply {
            putExtra("chapter_id", chapter.id)
            putExtra("chapter_title", chapter.title)
            putExtra("chapter_uri", chapter.fileUri)
            putExtra("series_title", seriesTitle)
            putExtra("prev_id", prev?.id)
            putExtra("prev_title", prev?.title)
            putExtra("prev_uri", prev?.fileUri)
            putExtra("next_id", next?.id)
            putExtra("next_title", next?.title)
            putExtra("next_uri", next?.fileUri)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        source.close()
    }
}
