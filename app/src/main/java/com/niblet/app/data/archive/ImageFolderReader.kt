package com.niblet.app.data.archive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImageFolderReader(private val folder: File) {

    private val pages: List<File> = folder.listFiles()
        ?.filter { it.isFile && isImage(it.name) }
        ?.sortedWith(compareBy(
            { it.name.replace(Regex("\\d+"), "").lowercase() },
            { it.name.replace(Regex("\\D"), "").toIntOrNull() ?: 0 }
        ))
        ?: emptyList()

    val pageCount: Int get() = pages.size

    suspend fun page(index: Int): Bitmap = withContext(Dispatchers.IO) {
        require(index in pages.indices) { "page $index out of range" }
        BitmapFactory.decodeFile(pages[index].absolutePath)
            ?: error("decode failed: ${pages[index].name}")
    }

    fun close() {}

    private fun isImage(n: String): Boolean {
        val s = n.lowercase()
        return s.endsWith(".jpg") || s.endsWith(".jpeg") ||
               s.endsWith(".png") || s.endsWith(".webp") ||
               s.endsWith(".bmp") || s.endsWith(".gif") ||
               s.endsWith(".heif") || s.endsWith(".heic") ||
               s.endsWith(".avif") || s.endsWith(".jp2") || s.endsWith(".j2k")
    }
}
