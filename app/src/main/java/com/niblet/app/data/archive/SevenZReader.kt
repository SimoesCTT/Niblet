package com.niblet.app.data.archive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.ByteArrayOutputStream
import java.io.File

class SevenZReader(private val file: File) {

    private val TAG = "NibletSevenZ"

    // Load all entries once at construction into memory. For CB7 files
    // (typically < 500 MB) this is fine and avoids the hang caused by
    // reopening SevenZFile per page.
    private val entryBytes: List<Pair<String, ByteArray>> = run {
        val out = mutableListOf<Pair<String, ByteArray>>()
        try {
            SevenZFile.builder().setFile(file).get().use { z ->
                var entry = z.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImage(entry.name)) {
                        val buf = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = try { z.read(chunk) } catch (e: Exception) { -1 }
                            if (n <= 0) break
                            buf.write(chunk, 0, n)
                        }
                        out.add(entry.name to buf.toByteArray())
                    }
                    entry = try { z.nextEntry } catch (e: Exception) { null }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "load failed: ${e.message}", e)
            /* context not available here; rely on LocalFolderSource log */
        }
        out.sortedWith(compareBy(
            { it.first.replace(Regex("\\d+"), "").lowercase() },
            { it.first.replace(Regex("\\D"), "").toIntOrNull() ?: 0 }
        ))
    }

    val pageCount: Int get() = entryBytes.size

    suspend fun page(index: Int): Bitmap = withContext(Dispatchers.IO) {
        require(index in entryBytes.indices) { "page $index out of range" }
        val bytes = entryBytes[index].second
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("decode failed: ${entryBytes[index].first}")
    }

    fun close() { /* nothing to close */ }

    private fun isImage(n: String): Boolean {
        val s = n.lowercase()
        return s.endsWith(".jpg") || s.endsWith(".jpeg") ||
               s.endsWith(".png") || s.endsWith(".webp")
    }
}
