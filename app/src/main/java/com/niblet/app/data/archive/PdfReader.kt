package com.niblet.app.data.archive

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfReader(private val file: File) {

    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = PdfRenderer(pfd)

    val pageCount: Int get() = renderer.pageCount

    suspend fun page(index: Int): Bitmap = withContext(Dispatchers.IO) {
        require(index in 0 until renderer.pageCount) { "page $index out of range" }
        val page = renderer.openPage(index)
        try {
            val pw = page.width
            val ph = page.height
            if (pw <= 0 || ph <= 0) error("bad page size ${pw}x$ph")
            val maxDim = 2048f
            val scale = minOf(maxDim / pw, maxDim / ph, 2f).coerceAtLeast(0.5f)
            val w = (pw * scale).toInt().coerceAtLeast(1)
            val h = (ph * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, Rect(0, 0, w, h), null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } finally { page.close() }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}
