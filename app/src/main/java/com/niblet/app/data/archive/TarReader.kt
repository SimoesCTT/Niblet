package com.niblet.app.data.archive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

class TarReader(private val file: File) {

    private val entries: List<String> = run {
        val out = mutableListOf<String>()
        FileInputStream(file).use { fis ->
            TarArchiveInputStream(fis).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImage(entry.name)) out.add(entry.name)
                    entry = tar.nextEntry
                }
            }
        }
        out.sortedWith(naturalOrder())
    }

    val pageCount: Int get() = entries.size

    suspend fun page(index: Int): Bitmap = withContext(Dispatchers.IO) {
        require(index in entries.indices) { "page $index out of range" }
        val target = entries[index]
        FileInputStream(file).use { fis ->
            TarArchiveInputStream(fis).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (entry.name == target) {
                        val buf = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = tar.read(chunk)
                            if (n <= 0) break
                            buf.write(chunk, 0, n)
                        }
                        val bytes = buf.toByteArray()
                        return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?: error("decode failed: $target")
                    }
                    entry = tar.nextEntry
                }
            }
        }
        error("entry not found: $target")
    }

    fun close() { /* per-page open */ }

    private fun isImage(n: String): Boolean {
        val s = n.lowercase()
        return s.endsWith(".jpg") || s.endsWith(".jpeg") ||
               s.endsWith(".png") || s.endsWith(".webp") ||
               s.endsWith(".bmp") || s.endsWith(".gif") ||
               s.endsWith(".heif") || s.endsWith(".heic") ||
               s.endsWith(".avif") || s.endsWith(".jp2") || s.endsWith(".j2k")
    }

    private fun naturalOrder(): Comparator<String> = compareBy(
        { it.replace(Regex("\\d+"), "").lowercase() },
        { it.replace(Regex("\\D"), "").toIntOrNull() ?: 0 }
    )
}
