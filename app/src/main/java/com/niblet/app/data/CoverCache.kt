package com.niblet.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.niblet.app.data.model.Series
import com.niblet.app.data.source.LocalFolderSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CoverCache {
    private const val SIZE = 160

    private val mem = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(k: String, v: Bitmap) = v.byteCount
    }

    private fun diskFile(ctx: Context, key: String): File {
        val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(ctx.cacheDir, "covers/$safe.png").apply { parentFile?.mkdirs() }
    }

    fun get(ctx: Context, key: String): Bitmap? {
        mem.get(key)?.let { return it }
        val f = diskFile(ctx, key)
        if (!f.exists()) return null
        val bmp = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
            ?: return null
        mem.put(key, bmp)
        return bmp
    }

    fun put(ctx: Context, key: String, bmp: Bitmap) {
        mem.put(key, bmp)
        runCatching {
            diskFile(ctx, key).outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 85, out)
            }
        }
    }

    suspend fun extract(
        ctx: Context,
        source: LocalFolderSource,
        series: Series
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val chapters = source.listChapters(series.title)
            val first = chapters.firstOrNull() ?: return@withContext null
            val bytes = source.readPage(first, 0)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 4
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }.getOrNull()
    }
}
