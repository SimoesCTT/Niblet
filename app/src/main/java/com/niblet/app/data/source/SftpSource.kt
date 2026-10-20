package com.niblet.app.data.source

import android.content.Context
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.niblet.app.data.archive.UniversalArchive
import com.niblet.app.data.model.Chapter
import com.niblet.app.data.model.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Vector

class SftpSource(
    private val context: Context,
    override val id: String,
    private val host: String,
    private val port: Int,
    private val user: String,
    private val pass: String,
    private val basePath: String
) : ComicSource {

    override val displayName = "SFTP: $host:$port"

    private var session: Session? = null
    private var ch: ChannelSftp? = null

    private fun ensure() {
        if (ch?.isConnected == true) return
        val j = JSch()
        val s = j.getSession(user, host, port)
        s.setPassword(pass)
        s.setConfig("StrictHostKeyChecking", "no")
        s.connect(10_000)
        session = s
        ch = s.openChannel("sftp") as ChannelSftp
        ch!!.connect()
    }

    override suspend fun listSeries(): List<Series> = withContext(Dispatchers.IO) {
        ensure()
        val p = basePath.ifBlank { "." }
        @Suppress("UNCHECKED_CAST")
        val entries = ch!!.ls(p) as Vector<Any>
        entries.mapNotNull { obj ->
            val e = obj as ChannelSftp.LsEntry
            if (!e.attrs.isDir || e.filename == "." || e.filename == "..") null
            else {
                val child = if (p == ".") e.filename else "$p/${e.filename}"
                @Suppress("UNCHECKED_CAST")
                val count = runCatching {
                    var c = 0
                    for (o in ch!!.ls(child) as Vector<Any>) {
                        val le = o as ChannelSftp.LsEntry
                        if (!le.attrs.isDir && isArchive(le.filename)) c++
                    }
                    c
                }.getOrDefault(0)
                if (count == 0) null else Series(
                    id = "$id:${e.filename}", title = e.filename,
                    chapterCount = count, sourceId = id)
            }
        }.sortedBy { it.title.lowercase() }
    }

    override suspend fun listChapters(seriesTitle: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            ensure()
            val folder = if (basePath.isBlank()) seriesTitle else "$basePath/$seriesTitle"
            @Suppress("UNCHECKED_CAST")
            val entries = ch!!.ls(folder) as Vector<Any>
            entries.mapNotNull { obj ->
                val e = obj as ChannelSftp.LsEntry
                if (e.attrs.isDir || !isArchive(e.filename)) null else Chapter(
                    id = "$id:$seriesTitle:${e.filename}",
                    title = e.filename,
                    fileUri = "$folder/${e.filename}",
                    seriesId = "$id:$seriesTitle")
            }.sortedBy { it.title.lowercase() }
        }

    override suspend fun readPage(chapter: Chapter, index: Int): ByteArray =
        withContext(Dispatchers.IO) {
            ensure()
            val cache = java.io.File(context.cacheDir,
                "niblet/sftp_${chapter.id.hashCode()}.cbz").apply { parentFile?.mkdirs() }
            if (!cache.exists() || cache.length() == 0L) {
                ch!!.get(chapter.fileUri, cache.absolutePath)
            }
            val u = UniversalArchive(cache, chapter.title)
            try {
                val bmp = u.page(index)
                ByteArrayOutputStream().use { o ->
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o)
                    o.toByteArray()
                }
            } finally { u.close() }
        }

    override fun close() {
        runCatching { ch?.disconnect() }; runCatching { session?.disconnect() }
        ch = null; session = null
    }

    private fun isArchive(n: String?): Boolean {
        val s = n?.lowercase() ?: return false
        return s.endsWith(".cbz") || s.endsWith(".cbr") ||
               s.endsWith(".cb7") || s.endsWith(".pdf") || s.endsWith(".epub")
    }
}
