package com.niblet.app.reader

import android.content.Context
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File

/**
 * Extracts the reading-order HTML documents from an EPUB. Returns a list of
 * (title, html) pairs. Used by the text-mode EPUB reader.
 */
object EpubTextRenderer {

    data class Doc(val title: String, val html: String)

    fun load(context: Context, epubFile: File): List<Doc> {
        val zip = ZipFile(epubFile)
        try {
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: error("not an epub")
            val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
            val opfPath = Regex("""full-path\s*=\s*"([^"]+)"""")
                .find(containerXml)?.groupValues?.get(1)
                ?: error("no opf path")

            val opfEntry = zip.getEntry(opfPath) ?: error("opf missing")
            val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
            val opfDir = opfPath.substringBeforeLast('/', "")

            // manifest: id -> href
            val manifest = mutableMapOf<String, String>()
            Regex("""<item\b[^>]*>""").findAll(opfXml).forEach { m ->
                val tag = m.value
                val id = Regex("""id\s*=\s*"([^"]+)"""").find(tag)?.groupValues?.get(1) ?: return@forEach
                val href = Regex("""href\s*=\s*"([^"]+)"""").find(tag)?.groupValues?.get(1) ?: return@forEach
                val type = Regex("""media-type\s*=\s*"([^"]+)"""").find(tag)?.groupValues?.get(1) ?: ""
                if (type.contains("html") || type.contains("xml")) {
                    manifest[id] = href
                }
            }

            // spine: ordered list of idref
            val order = mutableListOf<String>()
            Regex("""<itemref\b[^>]*>""").findAll(opfXml).forEach { m ->
                val idref = Regex("""idref\s*=\s*"([^"]+)"""").find(m.value)?.groupValues?.get(1)
                    ?: return@forEach
                order.add(idref)
            }

            val docs = mutableListOf<Doc>()
            for (idref in order) {
                val href = manifest[idref] ?: continue
                val fullPath = if (opfDir.isBlank()) href else "$opfDir/$href"
                val entry = zip.getEntry(fullPath) ?: continue
                val rawHtml = zip.getInputStream(entry).bufferedReader().readText()
                // Extract <title> or first heading
                val title = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                    .find(rawHtml)?.groupValues?.get(1)
                    ?: Regex("""<h1[^>]*>([^<]+)</h1>""", RegexOption.IGNORE_CASE)
                        .find(rawHtml)?.groupValues?.get(1)
                    ?: "Chapter ${docs.size + 1}"
                docs.add(Doc(title.trim(), rawHtml))
            }
            return docs
        } finally {
            zip.close()
        }
    }
}
