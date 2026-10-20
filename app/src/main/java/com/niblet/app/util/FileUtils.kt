package com.niblet.app.util

import android.content.Context
import android.net.Uri
import java.io.File

object FileUtils {
    fun copyUriToCache(context: Context, uri: Uri, name: String): File {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val out = File(context.cacheDir, "niblet/$safe").apply { parentFile?.mkdirs() }
        if (out.exists() && out.length() > 0L) return out
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: error("cannot open $uri")
        return out
    }
}
