package com.niblet.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.niblet.app.data.ProgressStore
import com.niblet.app.data.SettingsStore
import com.niblet.app.data.TreeStore
import com.niblet.app.databinding.ActivityReaderEpubTextBinding
import com.niblet.app.reader.EpubTextRenderer
import com.niblet.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReaderEpubTextActivity : AppCompatActivity() {

    private lateinit var b: ActivityReaderEpubTextBinding
    private var chapterId: String = ""
    private var chapterTitle: String = ""
    private var seriesTitle: String = ""
    private var docs: List<EpubTextRenderer.Doc> = emptyList()
    private var index = 0
    private var fontSize = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityReaderEpubTextBinding.inflate(layoutInflater)
        setContentView(b.root)

        chapterId = intent.getStringExtra("chapter_id")!!
        chapterTitle = intent.getStringExtra("chapter_title") ?: ""
        seriesTitle = intent.getStringExtra("series_title") ?: ""
        val uriString = intent.getStringExtra("chapter_uri") ?: run { finish(); return }
        index = ProgressStore.loadPage(this, chapterId)

        b.prevButton.setOnClickListener { prev() }
        b.nextButton.setOnClickListener { next() }

        b.webView.settings.javaScriptEnabled = false
        b.webView.settings.loadWithOverviewMode = true
        b.webView.settings.useWideViewPort = true
        b.webView.setBackgroundColor(0xFF121212.toInt())

        b.webView.setOnLongClickListener {
            showMenu()
            true
        }

        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val treeUri = TreeStore.load(this@ReaderEpubTextActivity) ?: return@withContext null
                val name = android.net.Uri.parse(uriString).lastPathSegment ?: chapterId
                FileUtils.copyUriToCache(this@ReaderEpubTextActivity,
                    android.net.Uri.parse(uriString), name)
            }
            if (file == null) { finish(); return@launch }
            docs = withContext(Dispatchers.IO) {
                runCatching { EpubTextRenderer.load(this@ReaderEpubTextActivity, file) }
                    .getOrElse { emptyList() }
            }
            if (docs.isEmpty()) {
                title = "No readable text in this EPUB"
                return@launch
            }
            if (index !in docs.indices) index = 0
            render()
        }
    }

    private fun render() {
        val doc = docs.getOrNull(index) ?: return
        val styled = wrapInStyle(doc.html)
        b.webView.loadDataWithBaseURL(null, styled, "text/html", "utf-8", null)
        title = "${doc.title} (${index + 1}/${docs.size})"
        b.prevButton.visibility = if (index > 0) View.VISIBLE else View.INVISIBLE
        b.nextButton.visibility = if (index < docs.size - 1) View.VISIBLE else View.INVISIBLE
        ProgressStore.savePage(this, chapterId, index)
    }

    private fun wrapInStyle(html: String): String {
        val size = fontSize
        return """<!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
            body {
              background:#121212; color:#FFFFFF;
              font-size:${size}%; line-height:1.55;
              padding:16px; font-family: serif;
              margin:0;
            }
            h1, h2, h3 { color:#F2C94C; }
            a { color:#F2C94C; }
            img { max-width:100%; height:auto; }
            pre, code { background:#1E1E1E; padding:6px; border-radius:4px; }
            </style></head><body>$html</body></html>"""
    }

    private fun showMenu() {
        val items = arrayOf(
            "Font size: ${fontSize}%",
            "Jump to chapter..."
        )
        AlertDialog.Builder(this).setItems(items) { _, i ->
            when (i) {
                0 -> cycleFontSize()
                1 -> showJumpDialog()
            }
        }.show()
    }

    private fun cycleFontSize() {
        fontSize = when {
            fontSize < 120 -> fontSize + 10
            else -> 80
        }
        render()
    }

    private fun showJumpDialog() {
        val titles = docs.mapIndexed { i, d -> "${i + 1}. ${d.title}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Jump to chapter")
            .setItems(titles) { _, i -> index = i; render() }
            .show()
    }

    private fun next() {
        if (index < docs.size - 1) { index++; render() }
    }

    private fun prev() {
        if (index > 0) { index--; render() }
    }
}
