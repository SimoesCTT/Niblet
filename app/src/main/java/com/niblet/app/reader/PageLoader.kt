package com.niblet.app.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

object PageLoader {
    private val mem = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(k: String, v: Bitmap) = v.byteCount
    }

    fun get(key: String): Bitmap? = mem.get(key)
    fun put(key: String, bmp: Bitmap) { mem.put(key, bmp) }
    fun clear() { mem.evictAll() }

    fun decode(bytes: ByteArray): Bitmap? {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
