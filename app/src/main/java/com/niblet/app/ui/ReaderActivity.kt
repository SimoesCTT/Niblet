package com.niblet.app.ui

import android.app.AlertDialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.niblet.app.data.Bookmark
import com.niblet.app.data.BookmarksStore
import com.niblet.app.data.PageCountStore
import com.niblet.app.data.PerSeriesStore
import com.niblet.app.data.ProgressStore
import com.niblet.app.data.SettingsStore
import com.niblet.app.data.TreeStore
import com.niblet.app.data.model.Chapter
import com.niblet.app.data.source.LocalFolderSource
import com.niblet.app.databinding.ActivityReaderBinding
import com.niblet.app.reader.PageLoader
import com.niblet.app.reader.SleepTimer
import com.niblet.app.reader.WebtoonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderActivity : AppCompatActivity() {

    private lateinit var b: ActivityReaderBinding
    private lateinit var source: LocalFolderSource
    private lateinit var chapter: Chapter
    private lateinit var seriesTitle: String
    private var pageIndex = 0
    private var pageCount = Int.MAX_VALUE
    private val sleep = SleepTimer()

    private var webtoonAdapter: WebtoonReader? = null
    private var webtoonNextPage = 0
    private var webtoonLoading = false
    private var webtoonPrevChapter: Chapter? = null
    private var webtoonPrevPage = -1
    private var webtoonPrevLoading = false
    private var webtoonPrependOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(b.root)

        val tree = TreeStore.load(this) ?: run { finish(); return }
        source = LocalFolderSource(this, tree)
        chapter = Chapter(
            id = intent.getStringExtra("chapter_id")!!,
            title = intent.getStringExtra("chapter_title") ?: "",
            fileUri = intent.getStringExtra("chapter_uri")!!,
            seriesId = ""
        )
        seriesTitle = intent.getStringExtra("series_title") ?: ""
        pageIndex = intent.getIntExtra("start_page", -1).takeIf { it >= 0 }
            ?: ProgressStore.loadPage(this, chapter.id)
        val cachedPageCount = PageCountStore.load(this, chapter.id)
        if (cachedPageCount > 0) pageCount = cachedPageCount

        val prevId = intent.getStringExtra("prev_id")
        val prevTitle = intent.getStringExtra("prev_title")
        val prevUri = intent.getStringExtra("prev_uri")
        if (prevId != null && prevTitle != null && prevUri != null) {
            webtoonPrevChapter = Chapter(
                id = prevId, title = prevTitle, fileUri = prevUri, seriesId = "")
        }

        val dir = PerSeriesStore.effective(this, seriesTitle)
        b.reader.direction = dir
        b.reader.fit = PerSeriesStore.effectiveFit(this, seriesTitle)
        b.reader.panelMode = SettingsStore.panelMode(this)
        b.reader.onPanelChanged = { cur, total ->
            if (total > 1) title = "${chapter.title} • p.${pageIndex + 1} panel $cur/$total"
        }
        if (SettingsStore.keepScreenOn(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        b.reader.onTapLeft = { prev() }
        b.reader.onTapRight = { next() }
        b.reader.setOnLongClickListener { showMenu(); true }

        if (dir == SettingsStore.Direction.WEBTOON) enableWebtoon()
        else if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                 SettingsStore.spreadMode(this)) showSpread()
        else lifecycleScope.launch { loadSingle() }
    }

    private fun enableWebtoon() {
        b.reader.visibility = View.GONE
        b.webtoon.visibility = View.VISIBLE
        webtoonAdapter = WebtoonReader(
            onNearEnd = { loadMoreWebtoon() },
            onNearStart = { loadPrevWebtoon() }
        )
        b.webtoon.layoutManager = LinearLayoutManager(this)
        b.webtoon.adapter = webtoonAdapter
        b.webtoon.setOnLongClickListener { showMenu(); true }
        webtoonNextPage = pageIndex
        webtoonPrevChapter?.let { c ->
            // find the last page index of prev chapter by probing
            // for simplicity, start at a generous guess and walk back on failure
            webtoonPrevPage = -1 // resolved lazily on first near-start
        }
        loadMoreWebtoon()
    }

    private fun loadMoreWebtoon() {
        if (webtoonLoading) return
        webtoonLoading = true
        val adapter = webtoonAdapter ?: return
        lifecycleScope.launch {
            val key = "${chapter.id}:$webtoonNextPage"
            val bmp = PageLoader.get(key) ?: run {
                val bytes = runCatching { source.readPage(chapter, webtoonNextPage) }.getOrNull()
                if (bytes == null) {
                    webtoonLoading = false
                    pageCount = webtoonNextPage
                    PageCountStore.save(this@ReaderActivity, chapter.id, pageCount)
                    return@launch
                }
                val decoded = withContext(Dispatchers.Default) { PageLoader.decode(bytes) }
                if (decoded != null) PageLoader.put(key, decoded)
                decoded
            }
            if (bmp != null) {
                adapter.append(bmp)
                pageIndex = webtoonNextPage
                ProgressStore.savePage(this@ReaderActivity, chapter.id, pageIndex)
                webtoonNextPage++
            } else {
                pageCount = webtoonNextPage
            }
            webtoonLoading = false
        }
    }

    private fun loadPrevWebtoon() {
        if (webtoonPrevLoading) return
        val prev = webtoonPrevChapter ?: return
        val adapter = webtoonAdapter ?: return
        if (adapter.size() == 0) return
        webtoonPrevLoading = true
        lifecycleScope.launch {
            val lm = b.webtoon.layoutManager as? LinearLayoutManager
            val insertPos = 0
            if (webtoonPrevPage < 0) {
                val cached = PageCountStore.load(this@ReaderActivity, prev.id)
                val found = if (cached > 0) cached - 1 else {
                    var f = -1
                    for (p in 999 downTo 0) {
                        val bytes = runCatching { source.readPage(prev, p) }.getOrNull()
                        if (bytes != null) { f = p; break }
                    }
                    f
                }
                if (found < 0) {
                    webtoonPrevChapter = null
                    webtoonPrevLoading = false
                    return@launch
                }
                webtoonPrevPage = found
            }
            val key = "${prev.id}:$webtoonPrevPage"
            val bmp = PageLoader.get(key) ?: run {
                val bytes = runCatching { source.readPage(prev, webtoonPrevPage) }.getOrNull()
                if (bytes == null) {
                    webtoonPrevChapter = null
                    webtoonPrevLoading = false
                    return@launch
                }
                val decoded = withContext(Dispatchers.Default) { PageLoader.decode(bytes) }
                if (decoded != null) PageLoader.put(key, decoded)
                decoded
            }
            if (bmp != null) {
                adapter.prepend(bmp)
                // preserve scroll position: the current first visible item is now at pos+1
                lm?.scrollToPositionWithOffset(insertPos + 1, 0)
                webtoonPrevPage--
                if (webtoonPrevPage < 0) {
                    // move to chapter before prev
                    webtoonPrevChapter = null
                }
            }
            webtoonPrevLoading = false
        }
    }

    private fun showMenu() {
        val items = mutableListOf(
            "Direction: ${PerSeriesStore.effective(this, seriesTitle)}",
            "Fit: ${PerSeriesStore.effectiveFit(this, seriesTitle)}",
            "Bookmark this page",
            "Sleep timer 15 min",
            "Sleep timer 30 min",
            "Cancel sleep timer",
            "Jump to page..."
        )
        if (pageCount != Int.MAX_VALUE) items.add("Page ${pageIndex + 1}/$pageCount")
        AlertDialog.Builder(this)
            .setItems(items.toTypedArray()) { _, i ->
                when (i) {
                    0 -> {
                        val current = PerSeriesStore.effective(this, seriesTitle)
                        val next = when (current) {
                            SettingsStore.Direction.LTR -> SettingsStore.Direction.RTL
                            SettingsStore.Direction.RTL -> SettingsStore.Direction.WEBTOON
                            SettingsStore.Direction.WEBTOON -> SettingsStore.Direction.LTR
                        }
                        PerSeriesStore.setDirection(this, seriesTitle, next)
                        recreate()
                    }
                    1 -> {
                        val current = PerSeriesStore.effectiveFit(this, seriesTitle)
                        val next = when (current) {
                            SettingsStore.Fit.PAGE -> SettingsStore.Fit.WIDTH
                            SettingsStore.Fit.WIDTH -> SettingsStore.Fit.HEIGHT
                            SettingsStore.Fit.HEIGHT -> SettingsStore.Fit.PAGE
                        }
                        PerSeriesStore.setFit(this, seriesTitle, next)
                        b.reader.fit = next
                        val curDir = PerSeriesStore.effective(this, seriesTitle)
                        if (curDir == SettingsStore.Direction.WEBTOON) { /* noop */ }
                        else if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                                 SettingsStore.spreadMode(this)) showSpread()
                        else lifecycleScope.launch { loadSingle() }
                    }
                    2 -> BookmarksStore.add(this, Bookmark(
                        chapterId = chapter.id,
                        chapterTitle = chapter.title,
                        page = pageIndex,
                        seriesTitle = seriesTitle,
                        sourceId = "local",
                        at = System.currentTimeMillis()))
                    3 -> sleep.start(15 * 60_000L,
                        onTick = { updateTitle() }, onFire = { finish() })
                    4 -> sleep.start(30 * 60_000L,
                        onTick = { updateTitle() }, onFire = { finish() })
                    5 -> { sleep.stop(); updateTitle() }
                    6 -> showJumpDialog()
                    7 -> { /* page-info no-op */ }
                }
            }.show()
    }

    private fun showSpread() {
        if (PerSeriesStore.effective(this, seriesTitle) == SettingsStore.Direction.WEBTOON) return
        if (!SettingsStore.spreadMode(this)) return
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return

        b.reader.visibility = View.GONE
        b.spread.visibility = View.VISIBLE
        b.webtoon.visibility = View.GONE

        lifecycleScope.launch {
            val key1 = "${chapter.id}:$pageIndex"
            val bmp1 = PageLoader.get(key1) ?: run {
                val bytes = runCatching { source.readPage(chapter, pageIndex) }.getOrNull()
                if (bytes == null) return@launch
                val decoded = withContext(Dispatchers.Default) { PageLoader.decode(bytes) }
                if (decoded != null) PageLoader.put(key1, decoded)
                decoded
            }
            val nextIndex = pageIndex + 1
            val key2 = "${chapter.id}:$nextIndex"
            val bmp2 = PageLoader.get(key2) ?: run {
                val bytes = runCatching { source.readPage(chapter, nextIndex) }.getOrNull()
                val decoded = if (bytes != null)
                    withContext(Dispatchers.Default) { PageLoader.decode(bytes) }
                else null
                if (decoded != null) PageLoader.put(key2, decoded)
                decoded
            }

            val isRtl = PerSeriesStore.effective(this@ReaderActivity, seriesTitle) ==
                SettingsStore.Direction.RTL
            if (isRtl) {
                b.leftPage.setImageBitmap(bmp2 ?: bmp1)
                b.rightPage.setImageBitmap(bmp1)
            } else {
                b.leftPage.setImageBitmap(bmp1)
                b.rightPage.setImageBitmap(bmp2)
            }

            b.spread.setOnClickListener {
                pageIndex += 2
                ProgressStore.savePage(this@ReaderActivity, chapter.id, pageIndex)
                lifecycleScope.launch { showSpread() }
            }

            updateTitle()
        }
    }

    private fun showJumpDialog() {
        val input = android.widget.EditText(this).apply {
            hint = if (pageCount != Int.MAX_VALUE) "1..$pageCount" else "page number"
            setText((pageIndex + 1).toString())
        }
        AlertDialog.Builder(this)
            .setTitle("Jump to page")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val p = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                if (p < 1) return@setPositiveButton
                if (pageCount != Int.MAX_VALUE && p > pageCount) return@setPositiveButton
                pageIndex = p - 1
                ProgressStore.savePage(this, chapter.id, pageIndex)
                if (PerSeriesStore.effective(this, seriesTitle) == SettingsStore.Direction.WEBTOON) {
                    webtoonAdapter?.clear()
                    webtoonNextPage = pageIndex
                    loadMoreWebtoon()
                } else {
                    lifecycleScope.launch { loadSingle() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun loadSingle() {
        val key = "${chapter.id}:$pageIndex"
        val cached = PageLoader.get(key)
        if (cached != null) { b.reader.show(cached); updateTitle(); return }
        runCatching { source.readPage(chapter, pageIndex) }
            .onSuccess { bytes ->
                val bmp = withContext(Dispatchers.Default) { PageLoader.decode(bytes) }
                if (bmp != null) { PageLoader.put(key, bmp); b.reader.show(bmp); updateTitle() }
            }
            .onFailure {
                if (pageIndex > 0) {
                    pageCount = pageIndex
                    PageCountStore.save(this@ReaderActivity, chapter.id, pageCount)
                    if (pageIndex > 0) { pageIndex = pageCount - 1; loadSingle() }
                } else {
                    title = "Error: ${it.message}"
                }
            }
    }

    private fun updateTitle() {
        title = "${chapter.title} • ${pageIndex + 1} [${PerSeriesStore.effective(this, seriesTitle).name}]"
    }

    private fun next() {
        SettingsStore.bumpPagesRead(this)
        val step = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                       SettingsStore.spreadMode(this)) 2 else 1
        if (pageCount != Int.MAX_VALUE && pageIndex + step >= pageCount) {
            nextChapter(); return
        }
        pageIndex += step
        ProgressStore.savePage(this, chapter.id, pageIndex)
        val dir = PerSeriesStore.effective(this, seriesTitle)
        if (dir == SettingsStore.Direction.WEBTOON) { /* noop */ }
        else if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                 SettingsStore.spreadMode(this)) showSpread()
        else lifecycleScope.launch { loadSingle() }
    }

    private fun prev() {
        if (pageIndex == 0) { prevChapter(); return }
        val step = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                       SettingsStore.spreadMode(this)) 2 else 1
        pageIndex = (pageIndex - step).coerceAtLeast(0)
        ProgressStore.savePage(this, chapter.id, pageIndex)
        val dir = PerSeriesStore.effective(this, seriesTitle)
        if (dir == SettingsStore.Direction.WEBTOON) { /* noop */ }
        else if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                 SettingsStore.spreadMode(this)) showSpread()
        else lifecycleScope.launch { loadSingle() }
    }

    private fun nextChapter() {
        val titleStr = intent.getStringExtra("next_title") ?: return
        val id = intent.getStringExtra("next_id") ?: return
        val uri = intent.getStringExtra("next_uri") ?: return
        startActivity(android.content.Intent(this, ReaderActivity::class.java).apply {
            putExtra("chapter_id", id)
            putExtra("chapter_title", titleStr)
            putExtra("chapter_uri", uri)
            putExtra("series_title", seriesTitle)
        })
        finish()
    }

    private fun prevChapter() {
        val titleStr = intent.getStringExtra("prev_title") ?: return
        val id = intent.getStringExtra("prev_id") ?: return
        val uri = intent.getStringExtra("prev_uri") ?: return
        startActivity(android.content.Intent(this, ReaderActivity::class.java).apply {
            putExtra("chapter_id", id)
            putExtra("chapter_title", titleStr)
            putExtra("chapter_uri", uri)
            putExtra("series_title", seriesTitle)
        })
        finish()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    override fun onDestroy() {
        super.onDestroy(); sleep.stop(); source.close()
    }
}
