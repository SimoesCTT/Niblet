package com.niblet.app.data.archive

import android.graphics.Bitmap
import java.io.File

class UniversalArchive(
    private val file: File,
    private val nameHint: String,
    private val cacheDir: File
) {
    private val impl: Impl = open()

    private fun open(): Impl {
        val lower = nameHint.lowercase()
        val ext = lower.substringAfterLast('.', "")

        return when {
            ext in setOf("cbz", "zip") -> Impl.CBZ(CbzReader(file))
            ext in setOf("cb7", "7z") -> Impl.CB7(SevenZReader(file))
            ext in setOf("cbt", "tar") -> Impl.CBT(TarReader(file))
            ext == "pdf" -> Impl.PDF(PdfReader(file))
            ext == "epub" -> Impl.EPUB(EpubReader(file))
            ext in setOf("cbr", "rar") -> openRar()
            // Folder of images (no extension match, but the source flagged it)

            else -> throw IllegalArgumentException("unsupported: $nameHint")
        }
    }

    private fun openRar(): Impl {
        var last: Throwable? = null

        // 1. junrar (RAR4, fast)
        try {
            val r = CbrReader(file)
            if (r.pageCount > 0) return Impl.CBR(r)
            r.close(); last = Exception("junrar: 0 pages")
        } catch (e: Throwable) { last = e }

        // 3. ZIP fallback (scanlations misname .cbz as .cbr)
        try { return Impl.CBZ(CbzReader(file)) } catch (_: Throwable) {}

        throw last ?: Exception("cannot open rar")
    }

    val pageCount: Int get() = when (val i = impl) {
        is Impl.CBZ -> i.r.pageCount
        is Impl.CBR -> i.r.pageCount
        is Impl.CB7 -> i.r.pageCount
        is Impl.CBT -> i.r.pageCount
        is Impl.PDF -> i.r.pageCount
        is Impl.EPUB -> i.r.pageCount
    }

    suspend fun page(index: Int): Bitmap = when (val i = impl) {
        is Impl.CBZ -> i.r.page(index)
        is Impl.CBR -> i.r.page(index)
        is Impl.CB7 -> i.r.page(index)
        is Impl.CBT -> i.r.page(index)
        is Impl.PDF -> i.r.page(index)
        is Impl.EPUB -> i.r.page(index)
    }

    fun close() = when (val i = impl) {
        is Impl.CBZ -> i.r.close()
        is Impl.CBR -> i.r.close()
        is Impl.CB7 -> i.r.close()
        is Impl.CBT -> i.r.close()
        is Impl.PDF -> i.r.close()
        is Impl.EPUB -> i.r.close()
    }

    private sealed class Impl {
        class CBZ(val r: CbzReader) : Impl()
        class CBR(val r: CbrReader) : Impl()
        class CB7(val r: SevenZReader) : Impl()
        class CBT(val r: TarReader) : Impl()
        class PDF(val r: PdfReader) : Impl()
        class EPUB(val r: EpubReader) : Impl()
    }
}
