package com.niblet.app.data.archive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File

class CbzReader(private val file: File) {

    private val zip = ZipFile(file)

    private val entries: List<String> = zip.entries.asSequence()
        .filter { !it.isDirectory }
        .map { it.name }
        .filter { isImage(it) }
        .sortedWith(naturalOrder())
        .toList()

    val pageCount: Int get() = entries.size

    suspend fun page(index: Int): Bitmap = withContext(Dispatchers.IO) {
        require(index in entries.indices) { "page $index out of range" }
        val entry = zip.getEntry(entries[index])
        zip.getInputStream(entry).use { input ->
            BitmapFactory.decodeStream(input) ?: error("decode failed: ${entries[index]}")
        }
    }

    fun close() { zip.close() }

    private fun isImage(n: String): Boolean {
        val s = n.lowercase()
        return s.endsWith(".jpg") || s.endsWith(".jpeg") ||
               s.endsWith(".png") || s.endsWith(".webp")
    }

    private fun naturalOrder(): Comparator<String> = compareBy(
        { it.replace(Regex("\\d+"), "").lowercase() },
        { it.replace(Regex("\\D"), "").toIntOrNull() ?: 0 }
    )
}
