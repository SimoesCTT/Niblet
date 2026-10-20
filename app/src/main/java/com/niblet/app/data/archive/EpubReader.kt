package com.niblet.app.data.archive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File

class EpubReader(private val file: File) {

    private val zip = ZipFile(file)

    private val opfPath: String = run {
        val entry = zip.getEntry("META-INF/container.xml")
            ?: error("not an epub: missing container.xml")
        val xml = zip.getInputStream(entry).bufferedReader().readText()
        val regex = Regex("""full-path\s*=\s*"([^"]+)"""")
        regex.find(xml)?.groupValues?.get(1) ?: error("no rootfile in container.xml")
    }

    private val imagePaths: List<String> = run {
        val opfDir = opfPath.substringBeforeLast('/', "")
        val opfEntry = zip.getEntry(opfPath) ?: error("missing opf")
        val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
        // Extract every href ending in an image extension
        val hrefRe = Regex("""href\s*=\s*"([^"]+\.(?:jpg|jpeg|png|webp))"""",
            RegexOption.IGNORE_CASE)
        hrefRe.findAll(opfXml).map { m ->
            val href = m.groupValues[1]
            if (opfDir.isBlank()) href else "$opfDir/$href"
        }.toList()
    }

    val pageCount: Int get() = imagePaths.size

    suspend fun page(index: Int): Bitmap = withContext(Dispatchers.IO) {
        require(index in imagePaths.indices) { "page $index out of range" }
        val path = imagePaths[index]
        val entry = zip.getEntry(path)
        if (entry != null) {
            zip.getInputStream(entry).use { input ->
                BitmapFactory.decodeStream(input)
                    ?: blankPage()
            }
        } else {
            blankPage()
        }
    }

    private fun blankPage(): Bitmap {
        val bmp = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        return bmp
    }

    fun close() { runCatching { zip.close() } }
}
