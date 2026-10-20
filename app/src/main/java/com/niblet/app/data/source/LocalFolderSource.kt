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

    override suspend fun listSeries(): List<Series> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val files = root.listFiles()
        val out = mutableListOf<Series>()

        files.filter { it.isDirectory && it.name != null }.forEach { dir ->
            val children = dir.listFiles()
            val archives = children.filter { it.isFile && isArchive(it.name) }
            val images = children.filter { it.isFile && isImage(it.name) }
            if (archives.isNotEmpty() || images.isNotEmpty()) {
                out.add(Series(
                    id = "local:folder:${dir.name}",
                    title = dir.name!!,
                    chapterCount = (archives.size + (if (images.isNotEmpty()) 1 else 0)),
                    sourceId = "local"
                ))
            }
        }

        val rootArchives = files.filter { it.isFile && isArchive(it.name) }
        val rootImages = files.filter { it.isFile && isImage(it.name) }
        if (rootArchives.isNotEmpty() || rootImages.isNotEmpty()) {
            val rootName = root.name?.takeIf { it.isNotBlank() } ?: "Library"
            out.add(Series(
                id = "local:root",
                title = "$rootName (flat)",
                chapterCount = (rootArchives.size + (if (rootImages.isNotEmpty()) 1 else 0)),
                sourceId = "local"
            ))
        }

        out.sortedBy { it.title.lowercase() }
    }

    override suspend fun listChapters(seriesId: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
            val files: List<DocumentFile> = when {
                seriesId == "local:root" -> {
                    val all = root.listFiles()
                    val arcs = all.filter { it.isFile && isArchive(it.name) }
                    val imgs = all.filter { it.isFile && isImage(it.name) }
                    if (arcs.isNotEmpty()) arcs else listOf(root)  // root itself is the image-folder chapter
                }
                seriesId.startsWith("local:folder:") -> {
                    val name = seriesId.removePrefix("local:folder:")
                    val dir = root.findFile(name) ?: return@withContext emptyList()
                    val all = dir.listFiles()
                    val arcs = all.filter { it.isFile && isArchive(it.name) }
                    val imgs = all.filter { it.isFile && isImage(it.name) }
                    if (arcs.isNotEmpty()) arcs else listOf(dir)
                }
                else -> emptyList()
            }
            files.map { f ->
                val isFolderChapter = f.isDirectory
                Chapter(
                    id = "$seriesId:${if (isFolderChapter) "__folder__" else f.name}",
                    title = if (isFolderChapter) "Images" else (f.name ?: "untitled"),
                    fileUri = f.uri.toString(),
                    seriesId = seriesId
                )
            }.sortedBy { it.title.lowercase() }
        }

    override suspend fun readPage(chapter: Chapter, index: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val reader = openReaders.getOrPut(chapter.id) {
                if (chapter.title == "Images") {
                    // Folder chapter unsupported in this build
                    error("image folders not yet supported")
                } else {
                    val f = FileUtils.copyUriToCache(
                        context, Uri.parse(chapter.fileUri), chapter.id)
                    UniversalArchive(f, chapter.title, context.cacheDir)
                }
            }
            val bmp = reader.page(index)
            ByteArrayOutputStream().use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        }

    override fun close() {
        openReaders.values.forEach { runCatching { it.close() } }
        openReaders.clear()
    }

    private fun isArchive(n: String?): Boolean {
        val s = n?.lowercase() ?: return false
        return s.endsWith(".cbz") || s.endsWith(".cbr") || s.endsWith(".cb7") ||
               s.endsWith(".cbt") || s.endsWith(".pdf") || s.endsWith(".epub") ||
               s.endsWith(".zip") || s.endsWith(".rar") || s.endsWith(".7z") ||
               s.endsWith(".tar")
    }

    private fun isImage(n: String?): Boolean {
        val s = n?.lowercase() ?: return false
        return s.endsWith(".jpg") || s.endsWith(".jpeg") ||
               s.endsWith(".png") || s.endsWith(".webp") ||
               s.endsWith(".bmp") || s.endsWith(".gif") ||
               s.endsWith(".heif") || s.endsWith(".heic") ||
               s.endsWith(".avif") || s.endsWith(".jp2") || s.endsWith(".j2k")
    }
}
