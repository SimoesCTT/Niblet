package com.niblet.app.data.source

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.niblet.app.data.archive.UniversalArchive
import com.niblet.app.data.model.Chapter
import com.niblet.app.data.model.Series
import com.niblet.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class LocalFolderSource(
    private val context: Context,
    private val treeUri: Uri
) : ComicSource {

    override val id = "local"
    override val displayName = "Local"
    private val openReaders = HashMap<String, UniversalArchive>()

    private fun trace(msg: String) {
        try {
            File(context.filesDir, "trace.log").appendText("${System.currentTimeMillis()} $msg\n")
        } catch (_: Exception) {}
    }

    override suspend fun listSeries(): List<Series> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        root.listFiles()
            .filter { it.isDirectory && it.name != null }
            .mapNotNull { dir ->
                val chapters = dir.listFiles().filter { it.isFile && isArchive(it.name) }
                if (chapters.isEmpty()) return@mapNotNull null
                Series(
                    id = "local:${dir.name}",
                    title = dir.name!!,
                    chapterCount = chapters.size,
                    sourceId = "local"
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    override suspend fun listChapters(seriesTitle: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
            root.findFile(seriesTitle)?.listFiles()
                ?.filter { it.isFile && isArchive(it.name) }
                ?.map { f ->
                    Chapter(
                        id = "local:$seriesTitle:${f.name}",
                        title = f.name ?: "untitled",
                        fileUri = f.uri.toString(),
                        seriesId = "local:$seriesTitle"
                    )
                }?.sortedBy { it.title.lowercase() } ?: emptyList()
        }

    override suspend fun readPage(chapter: Chapter, index: Int): ByteArray =
        withContext(Dispatchers.IO) {
            trace("readPage START chapter=${chapter.title} index=$index")
            try {
                val reader = openReaders.getOrPut(chapter.id) {
                    val f = FileUtils.copyUriToCache(
                        context, Uri.parse(chapter.fileUri), chapter.id)
                    trace("cached file: ${f.absolutePath} size=${f.length()}")
                    UniversalArchive(f, chapter.title)
                }
                trace("reader opened, calling page($index)")
                val bmp = reader.page(index)
                trace("bitmap: ${bmp.width}x${bmp.height}")
                ByteArrayOutputStream().use { out ->
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
            } catch (e: Exception) {
                trace("ERROR: ${e.javaClass.simpleName}: ${e.message}")
                e.stackTrace.take(5).forEach { trace("  at $it") }
                throw e
            }
        }

    override fun close() {
        openReaders.values.forEach { runCatching { it.close() } }
        openReaders.clear()
    }

    private fun isArchive(n: String?): Boolean {
        val s = n?.lowercase() ?: return false
        return s.endsWith(".cbz") || s.endsWith(".cbr") || s.endsWith(".cb7") ||
               s.endsWith(".cbt") || s.endsWith(".pdf") || s.endsWith(".epub")
    }
}
