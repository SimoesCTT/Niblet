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
import com.niblet.app.data.SourceStore
import com.niblet.app.data.model.Chapter
import com.niblet.app.data.source.SourceManager
import com.niblet.app.databinding.ActivityReaderBinding
import com.niblet.app.reader.PageLoader
import com.niblet.app.reader.SleepTimer
import com.niblet.app.reader.WebtoonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderNetworkActivity : AppCompatActivity() {

    private lateinit var b: ActivityReaderBinding
    private lateinit var chapter: Chapter
    private var sourceId = ""
    private var seriesTitle = ""
    private var pageIndex = 0
    private var pageCount = Int.MAX_VALUE
    private val sleep = SleepTimer()

    private var webtoonAdapter: WebtoonReader? = null
    private var webtoonNextPage = 0
    private var webtoonLoading = false
    private var webtoonPrevChapter: Chapter? = null
    private var webtoonPrevPage = -1
    private var webtoonPrevLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(b.root)

        sourceId = intent.getStringExtra("source_id")!!
        chapter = Chapter(
            id = intent.getStringExtra("chapter_id")!!,
            title = intent.getStringExtra("chapter_title") ?: "",
            fileUri = intent.getStringExtra("chapter_uri") ?: "",
            seriesId = ""
        )
        seriesTitle = intent.getStringExtra("series_title") ?: ""
        pageIndex = intent.getIntExtra("start_page", -1).takeIf { it >= 0 }
            ?: ProgressStore.loadPage(this, chapter.id)
        val cachedPageCount = PageCountStore.load(this, chapter.id)
        if (cachedPageCount > 0) pageCount = cachedPageCount
        b.reader.direction = SettingsStore.direction(this)
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
        b.reader.onLongPress = { showMenu() }

        if (SettingsStore.direction(this) == SettingsStore.Direction.WEBTOON) enableWebtoon()
        else if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                 SettingsStore.spreadMode(this)) showSpread()
        else lifecycleScope.launch { loadSingle() }
    }

    private fun enableWebtoon() {
        b.reader.visibility = View.GONE
        b.webtoon.visibility = View.VISIBLE
        webtoonAdapter = WebtoonReader(onNearEnd = { loadMore() }, onNearStart = { loadPrev() })
        b.webtoon.layoutManager = LinearLayoutManager(this)
        b.webtoon.adapter = webtoonAdapter
        b.webtoon.setOnLongClickListener { showMenu(); true }
        webtoonNextPage = pageIndex
        loadMore()
    }

    private fun loadMore() {
        if (webtoonLoading) return
        webtoonLoading = true
        val adapter = webtoonAdapter ?: return
        lifecycleScope.launch {
            val ns = SourceStore.load(this@ReaderNetworkActivity).firstOrNull { it.id == sourceId }
            val src = ns?.let { SourceManager.open(this@ReaderNetworkActivity, it) }
            if (src == null) { webtoonLoading = false; return@launch }
            val key = "${chapter.id}:$webtoonNextPage"
            val bmp = PageLoader.get(key) ?: run {
                val bytes = runCatching { src.readPage(chapter, webtoonNextPage) }.getOrNull()
                if (bytes == null) { webtoonLoading = false; return@launch }
                val decoded = withContext(Dispatchers.Default) { PageLoader.decode(bytes) }
                if (decoded != null) PageLoader.put(key, decoded)
                decoded
            }
            if (bmp != null) {
                adapter.append(bmp)
                pageIndex = webtoonNextPage
                ProgressStore.savePage(this@ReaderNetworkActivity, chapter.id, pageIndex)
                webtoonNextPage++
            }
            webtoonLoading = false
        }
    }

    private fun loadPrev() {
        if (webtoonPrevLoading) return
        val prev = webtoonPrevChapter ?: return
        val adapter = webtoonAdapter ?: return
        if (adapter.size() == 0) return
        webtoonPrevLoading = true
        lifecycleScope.launch {
            val lm = b.webtoon.layoutManager as? LinearLayoutManager
            val ns = SourceStore.load(this@ReaderNetworkActivity).firstOrNull { it.id == sourceId }
            val src = ns?.let { SourceManager.open(this@ReaderNetworkActivity, it) }
            if (src == null) { webtoonPrevLoading = false; return@launch }

            if (webtoonPrevPage < 0) {
                val cached = PageCountStore.load(this@ReaderNetworkActivity, prev.id)
                val found = if (cached > 0) cached - 1 else {
                    var f = -1
                    for (p in 999 downTo 0) {
                        val bytes = runCatching { src.readPage(prev, p) }.getOrNull()
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
                val bytes = runCatching { src.readPage(prev, webtoonPrevPage) }.getOrNull()
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
                lm?.scrollToPositionWithOffset(1, 0)
                webtoonPrevPage--
                if (webtoonPrevPage < 0) webtoonPrevChapter = null
            }
            webtoonPrevLoading = false
        }
    }

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setItems(arrayOf(
                "Direction: ${SettingsStore.direction(this)}",
                "Fit: ${PerSeriesStore.effectiveFit(this, seriesTitle)}",
                "Bookmark this page",
                "Sleep timer 15 min",
                "Sleep timer 30 min",
                "Cancel sleep timer"
            )) { _, i ->
                when (i) {
                    0 -> {
                        val next = when (SettingsStore.direction(this)) {
                            SettingsStore.Direction.LTR -> SettingsStore.Direction.RTL
                            SettingsStore.Direction.RTL -> SettingsStore.Direction.WEBTOON
                            SettingsStore.Direction.WEBTOON -> SettingsStore.Direction.LTR
                        }
                        SettingsStore.setDirection(this, next)
                        recreate()
                    }
                    1 -> {
                        val cur = PerSeriesStore.effectiveFit(this, seriesTitle)
                        val next = when (cur) {
                            SettingsStore.Fit.PAGE -> SettingsStore.Fit.WIDTH
                            SettingsStore.Fit.WIDTH -> SettingsStore.Fit.HEIGHT
                            SettingsStore.Fit.HEIGHT -> SettingsStore.Fit.PAGE
                        }
                        PerSeriesStore.setFit(this, seriesTitle, next)
                        b.reader.fit = next
                        if (SettingsStore.direction(this) != SettingsStore.Direction.WEBTOON) {
                            lifecycleScope.launch { loadSingle() }
                        }
                    }
                    2 -> BookmarksStore.add(this, Bookmark(
                        chapterId = chapter.id,
                        chapterTitle = chapter.title,
                        page = pageIndex,
                        seriesTitle = seriesTitle,
                        sourceId = sourceId,
                        at = System.currentTimeMillis()))
                    3 -> sleep.start(15 * 60_000L,
                        onTick = { updateTitle() }, onFire = { finish() })
                    4 -> sleep.start(30 * 60_000L,
                        onTick = { updateTitle() }, onFire = { finish() })
                    5 -> { sleep.stop(); updateTitle() }
                }
            }.show()
    }

    private fun showSpread() {
        if (SettingsStore.direction(this) == SettingsStore.Direction.WEBTOON) return
        if (!SettingsStore.spreadMode(this)) return
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return

        b.reader.visibility = View.GONE
        b.spread.visibility = View.VISIBLE
        b.webtoon.visibility = View.GONE

        lifecycleScope.launch {
            val ns = SourceStore.load(this@ReaderNetworkActivity)
                .firstOrNull { it.id == sourceId } ?: return@launch
            val src = SourceManager.open(this@ReaderNetworkActivity, ns) ?: return@launch

            val key1 = "${chapter.id}:$pageIndex"
            val bmp1 = PageLoader.get(key1) ?: run {
                val bytes = runCatching { src.readPage(chapter, pageIndex) }.getOrNull()
                if (bytes == null) return@launch
                val decoded = withContext(Dispatchers.Default) { PageLoader.decode(bytes) }
                if (decoded != null) PageLoader.put(key1, decoded)
                decoded
            }
            val nextIndex = pageIndex + 1
            val key2 = "${chapter.id}:$nextIndex"
            val bmp2 = PageLoader.get(key2) ?: run {
                val bytes = runCatching { src.readPage(chapter, nextIndex) }.getOrNull()
                val decoded = if (bytes != null)
                    withContext(Dispatchers.Default) { PageLoader.decode(bytes) }
                else null
                if (decoded != null) PageLoader.put(key2, decoded)
                decoded
            }

            val isRtl = SettingsStore.direction(this@ReaderNetworkActivity) ==
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
                ProgressStore.savePage(this@ReaderNetworkActivity, chapter.id, pageIndex)
                lifecycleScope.launch { showSpread() }
            }

            updateTitle()
        }
    }

    private suspend fun loadSingle() {
        val ns = SourceStore.load(this).firstOrNull { it.id == sourceId }
            ?: run { title = "source missing"; return }
        val src = SourceManager.open(this, ns) ?: run { title = "open failed"; return }
        val key = "${chapter.id}:$pageIndex"
        val cached = PageLoader.get(key)
        if (cached != null) { b.reader.show(cached); updateTitle(); return }
        runCatching { src.readPage(chapter, pageIndex) }
            .onSuccess { bytes ->
                val bmp = withContext(Dispatchers.Default) { PageLoader.decode(bytes) }
                if (bmp != null) { PageLoader.put(key, bmp); b.reader.show(bmp); updateTitle() }
            }
            .onFailure { title = "Error: ${it.message}" }
    }

    private fun updateTitle() {
        title = "${chapter.title} • ${pageIndex + 1} [${SettingsStore.direction(this).name}]"
    }

    private fun next() {
        com.niblet.app.data.SettingsStore.bumpPagesRead(this)
        pageIndex++
        ProgressStore.savePage(this, chapter.id, pageIndex)
        lifecycleScope.launch { loadSingle() }
    }

    private fun prev() {
        if (pageIndex <= 0) return
        pageIndex--
        ProgressStore.savePage(this, chapter.id, pageIndex)
        lifecycleScope.launch { loadSingle() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    override fun onDestroy() { super.onDestroy(); sleep.stop() }
}
