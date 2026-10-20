package com.niblet.app.data.source

import android.content.Context
import com.niblet.app.data.archive.CbzReader
import com.niblet.app.data.model.Chapter
import com.niblet.app.data.model.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

data class OpdsEntry(
    val title: String,
    val id: String,
    val navUrl: String?,
    val acquisitionUrl: String?,
    val coverUrl: String?
)

class OpdsSource(
    private val context: Context,
    override val id: String,
    private val catalogUrl: String,
    private val user: String,
    private val pass: String
) : ComicSource {

    override val displayName = "OPDS: ${catalogUrl.substringAfter("://").substringBefore("/")}"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun auth(): String? =
        if (user.isBlank()) null else Credentials.basic(user, pass)

    private data class Feed(
        val entries: List<OpdsEntry>,
        val nextPageUrl: String?,
        val searchUrl: String?   // template with {searchTerms}
    )

    private fun fetch(url: String, searchQuery: String? = null): Feed = try {
        val resolvedUrl = if (searchQuery != null && url.contains("{searchTerms}")) {
            url.replace("{searchTerms}", URLEncoder.encode(searchQuery, "UTF-8"))
        } else url
        val req = Request.Builder()
            .url(resolvedUrl)
            .apply { auth()?.let { addHeader("Authorization", it) } }
            .header("Accept", "application/atom+xml")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return Feed(emptyList(), null, null)
            val xml = resp.body?.string() ?: return Feed(emptyList(), null, null)
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(xml.byteInputStream())
            val feedEl = doc.documentElement
            val baseUrl = resolvedUrl.substringBeforeLast('/') + "/"

            // Parse feed-level links: next (pagination), search
            var nextUrl: String? = null
            var searchUrl: String? = null
            val feedLinks = feedEl.getElementsByTagNameNS("*", "link")
            for (i in 0 until feedLinks.length) {
                val link = feedLinks.item(i) as Element
                val rel = link.getAttribute("rel")
                val type = link.getAttribute("type")
                val href = link.getAttribute("href")
                if (href.isBlank()) continue
                val abs = absolutize(href, baseUrl)
                when {
                    rel == "next" -> nextUrl = abs
                    rel == "search" && type.contains("atom+xml") -> searchUrl = abs
                    rel.contains("search") && type.contains("atom+xml") -> searchUrl = abs
                }
            }

            val entries = doc.getElementsByTagNameNS("*", "entry")
            val list = (0 until entries.length).mapNotNull { i ->
                val el = entries.item(i) as Element
                val title = el.getElementsByTagNameNS("*", "title").item(0)?.textContent
                    ?: return@mapNotNull null
                val idEl = el.getElementsByTagNameNS("*", "id").item(0)?.textContent ?: title
                var navUrl: String? = null
                var acqUrl: String? = null
                var coverUrl: String? = null

                val links = el.getElementsByTagNameNS("*", "link")
                for (j in 0 until links.length) {
                    val link = links.item(j) as Element
                    val rel = link.getAttribute("rel")
                    val type = link.getAttribute("type")
                    val href = link.getAttribute("href")
                    if (href.isBlank()) continue
                    val abs = absolutize(href, baseUrl)
                    when {
                        rel.contains("acquisition") && isArchiveUrl(href) -> acqUrl = abs
                        rel == "http://opds-spec.org/image" ||
                        rel == "http://opds-spec.org/image/thumbnail" -> coverUrl = abs
                        type.contains("atom+xml") && rel.contains("subsection") -> navUrl = abs
                        type.contains("atom+xml") && navUrl == null && acqUrl == null -> navUrl = abs
                    }
                }
                OpdsEntry(title, idEl, navUrl, acqUrl, coverUrl)
            }
            Feed(list, nextUrl, searchUrl)
        }
    } catch (_: Exception) { Feed(emptyList(), null, null) }

    private fun absolutize(href: String, baseUrl: String): String =
        if (href.startsWith("http://") || href.startsWith("https://")) href
        else if (href.startsWith("/")) {
            val root = baseUrl.substringBefore("/", baseUrl).let {
                // strip path: "https://host:port/foo/bar/" -> "https://host:port"
                baseUrl.substringBefore("/").let { scheme -> scheme }
            }
            // Simplest: use baseUrl origin
            val schemeAndHost = baseUrl.substringBefore("/", "https://x").let { _ ->
                val idx = baseUrl.indexOf("://")
                if (idx < 0) return baseUrl + href.removePrefix("/")
                val afterScheme = baseUrl.substring(idx + 3)
                val hostEnd = afterScheme.indexOf('/')
                if (hostEnd < 0) baseUrl + href
                else baseUrl.substring(0, idx + 3 + hostEnd) + href
            }
            schemeAndHost
        } else baseUrl + href

    private fun isArchiveUrl(url: String): Boolean {
        val u = url.lowercase()
        return u.endsWith(".cbz") || u.endsWith(".cbr") ||
               u.endsWith(".cb7") || u.endsWith(".cbt") ||
               u.endsWith(".pdf") || u.endsWith(".epub")
    }

    override suspend fun listSeries(): List<Series> = withContext(Dispatchers.IO) {
        val allEntries = mutableListOf<OpdsEntry>()
        var feed = fetch(catalogUrl)
        allEntries.addAll(feed.entries)
        var page = feed.nextPageUrl
        var pagesFetched = 0
        while (page != null && pagesFetched < 20) {
            feed = fetch(page)
            allEntries.addAll(feed.entries)
            page = feed.nextPageUrl
            pagesFetched++
        }
        allEntries.mapNotNull { e ->
            if (e.navUrl != null && e.acquisitionUrl == null) {
                Series(id = "$id:${e.id}", title = e.title,
                    chapterCount = 0, sourceId = id)
            } else null
        }.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    }

    /**
     * OPDS search. Called from SeriesDetailActivity when user types in the
     * search box while an OPDS series is open? No — search is global. Exposed
     * so the library can call it when the user's search text triggers OPDS.
     */
    suspend fun search(query: String): List<OpdsEntry> = withContext(Dispatchers.IO) {
        val feed = fetch(catalogUrl)
        val searchUrl = feed.searchUrl ?: return@withContext emptyList()
        fetch(searchUrl, query).entries
    }

    override suspend fun listChapters(seriesTitle: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            // Find the series entry by title, then read its feed for acquisition links
            val root = fetch(catalogUrl)
            val match = root.entries.firstOrNull {
                it.title == seriesTitle && it.navUrl != null
            } ?: return@withContext emptyList()
            val allAcq = mutableListOf<OpdsEntry>()
            var feed = fetch(match.navUrl!!)
            allAcq.addAll(feed.entries)
            var page = feed.nextPageUrl
            var pagesFetched = 0
            while (page != null && pagesFetched < 10) {
                feed = fetch(page)
                allAcq.addAll(feed.entries)
                page = feed.nextPageUrl
                pagesFetched++
            }
            allAcq.mapNotNull { e ->
                if (e.acquisitionUrl != null) {
                    Chapter(
                        id = "$id:${match.id}:${e.id}",
                        title = e.title,
                        fileUri = e.acquisitionUrl!!,
                        seriesId = "$id:${match.id}"
                    )
                } else null
            }.distinctBy { it.id }.sortedBy { it.title.lowercase() }
        }

    override suspend fun readPage(chapter: Chapter, index: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val cache = File(context.cacheDir,
                "niblet/opds_${chapter.id.hashCode()}.cbz").apply { parentFile?.mkdirs() }
            if (!cache.exists() || cache.length() == 0L) {
                val req = Request.Builder()
                    .url(chapter.fileUri)
                    .apply { auth()?.let { addHeader("Authorization", it) } }
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("OPDS GET ${resp.code}")
                    cache.outputStream().use { resp.body!!.byteStream().copyTo(it) }
                }
            }
            val reader = CbzReader(cache)
            try {
                val bmp = reader.page(index)
                ByteArrayOutputStream().use { o ->
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o)
                    o.toByteArray()
                }
            } finally { reader.close() }
        }

    override fun close() {}
}
