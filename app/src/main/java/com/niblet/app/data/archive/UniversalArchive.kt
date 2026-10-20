package com.niblet.app.data.archive

import android.graphics.Bitmap
import java.io.File

class UniversalArchive(private val file: File, private val nameHint: String) {

    private val impl: Impl = when {
        nameHint.lowercase().endsWith(".cbr") -> Impl.CBR(CbrReader(file))
        nameHint.lowercase().endsWith(".cb7") -> Impl.CB7(SevenZReader(file))
        nameHint.lowercase().endsWith(".cbt") -> Impl.CBT(TarReader(file))
        nameHint.lowercase().endsWith(".pdf") -> Impl.PDF(PdfReader(file))
        nameHint.lowercase().endsWith(".epub") -> Impl.EPUB(EpubReader(file))
        else -> Impl.CBZ(CbzReader(file))
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
