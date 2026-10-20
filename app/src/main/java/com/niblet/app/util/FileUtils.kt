package com.niblet.app.util

import android.content.Context
import android.net.Uri
import java.io.File

object FileUtils {

    fun copyUriToCache(context: Context, uri: Uri, nameHint: String): File {
        val safe = nameHint.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val out = File(context.cacheDir, "niblet/$safe").apply { parentFile?.mkdirs() }
        if (out.exists() && out.length() > 0L) return out

        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: run {
            out.delete()
            error("cannot open $uri")
        }

        if (out.length() == 0L) {
            out.delete()
            error("copied 0 bytes from $uri")
        }
        return out
    }
}
