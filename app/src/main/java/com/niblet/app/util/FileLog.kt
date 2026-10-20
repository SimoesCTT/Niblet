package com.niblet.app.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLog {
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(ctx: Context, tag: String, msg: String) {
        try {
            val f = File(ctx.filesDir, "niblet.log")
            f.appendText("${fmt.format(Date())} $tag: $msg\n")
            // rotate at 2MB
            if (f.length() > 2_000_000) {
                f.renameTo(File(ctx.filesDir, "niblet.log.old"))
            }
        } catch (_: Exception) { /* best effort */ }
    }

    fun read(ctx: Context): String = try {
        File(ctx.filesDir, "niblet.log").readText()
    } catch (_: Exception) { "" }

    fun clear(ctx: Context) {
        runCatching { File(ctx.filesDir, "niblet.log").delete() }
    }
}
