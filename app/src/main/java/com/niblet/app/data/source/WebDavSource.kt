package com.niblet.app.data.source

import android.content.Context
import com.niblet.app.data.archive.UniversalArchive
import com.niblet.app.data.model.Chapter
import com.niblet.app.data.model.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class WebDavSource(
    private val context: Context,
    override val id: String,
    private val baseUrl: String,
    private val user: String,
    private val pass: String
) : ComicSource {

    override val displayName = "WebDAV: ${baseUrl.substringAfter("://").substringBefore("/")}"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun auth() = Credentials.basic(user, pass)

    private fun urlFor(p: String) = baseUrl.trimEnd('/') + "/" + p.trimStart('/')

    private data class Dav(val name: String, val isDir: Boolean)

    private fun propfind(path: String): List<Dav> = try {
        val body = """<?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:"><d:prop>
                <d:resourcetype/>
            </d:prop></d:propfind>"""
        val req = Request.Builder()
            .url(urlFor(path))
            .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .addHeader("Authorization", auth())
            .addHeader("Depth", "1")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val xml = resp.body?.string() ?: return emptyList()
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(xml.byteInputStream())
            val nl = doc.getElementsByTagNameNS("DAV:", "response")
            (0 until nl.length).mapNotNull { i ->
                val el = nl.item(i) as Element
                val hrefN = el.getElementsByTagNameNS("DAV:", "href")
                if (hrefN.length == 0) return@mapNotNull null
                val href = hrefN.item(0).textContent
                val isDir = el.getElementsByTagNameNS("DAV:", "collection").length > 0
                val name = URLDecoder.decode(href, "UTF-8").trimEnd('/').substringAfterLast('/')
                if (name.isBlank()) null else Dav(name, isDir)
            }
        }
    } catch (_: Exception) { emptyList() }

    override suspend fun listSeries(): List<Series> = withContext(Dispatchers.IO) {
        propfind("").filter { it.isDir }.map {
            Series(id = "$id:${it.name}", title = it.name,
                chapterCount = 0, sourceId = id)
        }.sortedBy { it.title.lowercase() }
    }

    override suspend fun listChapters(seriesTitle: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            propfind(seriesTitle).filter { !it.isDir && isArchive(it.name) }.map {
                Chapter(
                    id = "$id:$seriesTitle:${it.name}",
                    title = it.name,
                    fileUri = urlFor("$seriesTitle/${it.name}"),
                    seriesId = "$id:$seriesTitle")
            }.sortedBy { it.title.lowercase() }
        }

    override suspend fun readPage(chapter: Chapter, index: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val cache = java.io.File(context.cacheDir,
                "niblet/dav_${chapter.id.hashCode()}.cbz").apply { parentFile?.mkdirs() }
            if (!cache.exists() || cache.length() == 0L) {
                val req = Request.Builder()
                    .url(chapter.fileUri)
                    .addHeader("Authorization", auth())
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("GET ${resp.code}")
                    cache.outputStream().use { resp.body!!.byteStream().copyTo(it) }
                }
            }
            val u = UniversalArchive(cache, chapter.title, context.cacheDir)
            try {
                val bmp = u.page(index)
                ByteArrayOutputStream().use { o ->
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o)
                    o.toByteArray()
                }
            } finally { u.close() }
        }

    override fun close() {}

    private fun isArchive(n: String?): Boolean {
        val s = n?.lowercase() ?: return false
        return s.endsWith(".cbz") || s.endsWith(".cbr") ||
               s.endsWith(".cb7") || s.endsWith(".pdf") || s.endsWith(".epub")
    }
}
