package com.niblet.app.data.source

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.niblet.app.data.archive.UniversalArchive
import com.niblet.app.data.model.Chapter
import com.niblet.app.data.model.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.EnumSet

class SmbSource(
    private val context: Context,
    override val id: String,
    private val host: String,
    private val user: String,
    private val pass: String,
    private val shareName: String,
    private val basePath: String
) : ComicSource {

    override val displayName = "SMB: $host/$shareName"

    private var client: SMBClient? = null
    private var conn: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    private fun ensure() {
        if (share != null) return
        val c = SMBClient(); client = c
        conn = c.connect(host.trim('\\'))
        val ac = if (user.isBlank()) AuthenticationContext.anonymous()
                 else AuthenticationContext(user, pass.toCharArray(), null)
        session = conn!!.authenticate(ac)
        share = session!!.connectShare(shareName) as DiskShare
    }

    override suspend fun listSeries(): List<Series> = withContext(Dispatchers.IO) {
        ensure()
        val p = basePath.trim('/').replace('/', '\\')
        share!!.list(p).mapNotNull { e ->
            val isDir = (e.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            if (!isDir || e.fileName == "." || e.fileName == "..") null
            else {
                val child = if (p.isBlank()) e.fileName else "$p\\${e.fileName}"
                val n = runCatching {
                    share!!.list(child).count { isArchive(it.fileName) }
                }.getOrDefault(0)
                if (n == 0) null else Series(
                    id = "$id:${e.fileName}", title = e.fileName,
                    chapterCount = n, sourceId = id)
            }
        }.sortedBy { it.title.lowercase() }
    }

    override suspend fun listChapters(seriesTitle: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            ensure()
            val folder = if (basePath.isBlank()) seriesTitle
                        else basePath.trim('/').replace('/', '\\') + "\\" + seriesTitle
            share!!.list(folder).filter { isArchive(it.fileName) }.map { e ->
                Chapter(
                    id = "$id:$seriesTitle:${e.fileName}",
                    title = e.fileName,
                    fileUri = "$folder\\${e.fileName}",
                    seriesId = "$id:$seriesTitle")
            }.sortedBy { it.title.lowercase() }
        }

    override suspend fun readPage(chapter: Chapter, index: Int): ByteArray =
        withContext(Dispatchers.IO) {
            ensure()
            val cache = java.io.File(context.cacheDir,
                "niblet/smb_${chapter.id.hashCode()}.cbz").apply { parentFile?.mkdirs() }
            if (!cache.exists() || cache.length() == 0L) {
                val remote = share!!.openFile(
                    chapter.fileUri,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null)
                try {
                    remote.inputStream.use { i -> cache.outputStream().use { i.copyTo(it) } }
                } finally { remote.close() }
            }
            val u = UniversalArchive(cache, chapter.title, context.cacheDir)
            try {
                val bmp = u.page(index)
                ByteArrayOutputStream().use { o ->
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o)
                    o.toByteArray()
                }
            } finally { u.close() }
        }

    override fun close() {
        runCatching { share?.close() }; runCatching { session?.close() }
        runCatching { conn?.close() }; runCatching { client?.close() }
        share = null; session = null; conn = null; client = null
    }

    private fun isArchive(n: String?): Boolean {
        val s = n?.lowercase() ?: return false
        return s.endsWith(".cbz") || s.endsWith(".cbr") ||
               s.endsWith(".cb7") || s.endsWith(".pdf") || s.endsWith(".epub")
    }
}
