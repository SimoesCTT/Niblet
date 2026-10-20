package com.niblet.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.niblet.app.data.LastReadStore
import com.niblet.app.data.SourceStore
import com.niblet.app.data.TreeStore
import com.niblet.app.data.model.Series
import com.niblet.app.data.source.LocalFolderSource
import com.niblet.app.data.source.SourceManager
import com.niblet.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: SeriesAdapter
    private lateinit var contAdapter: SeriesAdapter
    private var local: LocalFolderSource? = null
    private var allSeries: List<Series> = emptyList()
    private var currentQuery: String = ""

    private val pick = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { onLocal(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        adapter = SeriesAdapter(local = { local }, onClick = { openSeries(it) })
        b.grid.layoutManager = GridLayoutManager(this, 2)
        b.grid.adapter = adapter

        contAdapter = SeriesAdapter(local = { local }, onClick = { openSeries(it) })
        b.continueGrid.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.continueGrid.adapter = contAdapter

        b.addSourceButton.setOnClickListener { pick.launch(null) }
        b.addNetworkButton.setOnClickListener {
            startActivity(Intent(this, AddNetworkSourceActivity::class.java))
        }
        b.syncButton.setOnClickListener {
            startActivity(Intent(this, SyncActivity::class.java))
        }
        b.bookmarksButton.setOnClickListener {
            startActivity(Intent(this, BookmarksActivity::class.java))
        }
        b.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        b.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString()?.trim()?.lowercase() ?: ""
                applyFilter()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
        })

        TreeStore.load(this)?.let { onLocal(it) }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun onLocal(uri: Uri) {
        TreeStore.save(this, uri)
        local?.close()
        local = LocalFolderSource(this, uri)
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val out = mutableListOf<Series>()
            local?.let { runCatching { it.listSeries() }.getOrNull()?.let(out::addAll) }
            for (ns in SourceStore.load(this@MainActivity)) {
                val src = SourceManager.open(this@MainActivity, ns) ?: continue
                runCatching { src.listSeries() }.getOrNull()?.let(out::addAll)
            }
            allSeries = out.sortedBy { it.title.lowercase() }
            buildContinue()
            applyFilter()
        }
    }

    private fun buildContinue() {
        val recent = LastReadStore.all(this).take(6)
        val items = recent.mapNotNull { e ->
            allSeries.firstOrNull { it.id == e.seriesId && it.sourceId == e.sourceId }
        }
        if (items.isEmpty()) {
            b.continueHeader.visibility = View.GONE
            b.continueGrid.visibility = View.GONE
        } else {
            b.continueHeader.visibility = View.VISIBLE
            b.continueGrid.visibility = View.VISIBLE
            contAdapter.submit(items)
        }
    }

    private fun applyFilter() {
        val visible = if (currentQuery.isEmpty()) allSeries
                      else allSeries.filter { it.title.lowercase().contains(currentQuery) }
        adapter.submit(visible)
        b.emptyState.visibility = if (visible.isEmpty() && allSeries.isNotEmpty())
            View.VISIBLE else View.GONE
        b.grid.visibility = if (visible.isEmpty() && allSeries.isNotEmpty())
            View.GONE else View.VISIBLE
    }

    private fun openSeries(s: Series) {
        LastReadStore.mark(this, s.id, s.title, s.sourceId)
        LastReadStore.prune(this)
        if (s.sourceId == "local") {
            startActivity(Intent(this, SeriesDetailActivity::class.java).apply {
                putExtra("series_title", s.title)
            })
        } else {
            startActivity(Intent(this, SeriesDetailNetworkActivity::class.java).apply {
                putExtra("source_id", s.sourceId)
                putExtra("series_title", s.title)
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        local?.close()
    }
}
