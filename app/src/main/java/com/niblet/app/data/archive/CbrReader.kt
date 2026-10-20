package com.niblet.app.data.archive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class CbrReader(private val file: File) {

    private val archive = Archive(file)

    private val headers: List<FileHeader> =
        archive.fileHeaders
            .filter { !it.isDirectory && isImage(it.fileNameString) }
            .sortedWith(compareBy { naturalKey(it.fileNameString) })

    val pageCount: Int get() = headers.size

    suspend fun page(index: Int): Bitmap = withContext(Dispatchers.IO) {
        require(index in headers.indices) { "page $index out of range" }
        val h = headers[index]
        val out = ByteArrayOutputStream()
        archive.extractFile(h, out)
        val bytes = out.toByteArray()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("decode failed: ${h.fileNameString}")
    }

    fun close() { runCatching { archive.close() } }

    private fun isImage(n: String): Boolean {
        val s = n.lowercase()
        return s.endsWith(".jpg") || s.endsWith(".jpeg") ||
               s.endsWith(".png") || s.endsWith(".webp")
    }

    private fun naturalKey(name: String): String {
        val prefix = name.replace(Regex("\\d+"), "")
        val n = Regex("\\d+").find(name)?.value?.toIntOrNull() ?: 0
        return prefix.lowercase() + n.toString().padStart(10, '0')
    }
}
