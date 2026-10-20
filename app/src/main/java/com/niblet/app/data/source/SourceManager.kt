package com.niblet.app.data.source

import android.content.Context
import com.niblet.app.data.NetworkSource
import com.niblet.app.data.SecureStore

object SourceManager {

    private val open = HashMap<String, ComicSource>()

    fun open(context: Context, s: NetworkSource): ComicSource? {
        open[s.id]?.let { return it }
        val impl: ComicSource? = when (s.kind) {
            "SMB" -> SmbSource(
                context, s.id,
                SecureStore.get(context, "src:${s.id}:host"),
                SecureStore.get(context, "src:${s.id}:user"),
                SecureStore.get(context, "src:${s.id}:password"),
                SecureStore.get(context, "src:${s.id}:share"),
                SecureStore.get(context, "src:${s.id}:path")
            )
            "SFTP" -> SftpSource(
                context, s.id,
                SecureStore.get(context, "src:${s.id}:host"),
                SecureStore.get(context, "src:${s.id}:port").toIntOrNull() ?: 22,
                SecureStore.get(context, "src:${s.id}:user"),
                SecureStore.get(context, "src:${s.id}:password"),
                SecureStore.get(context, "src:${s.id}:path")
            )
            "WEBDAV" -> WebDavSource(
                context, s.id,
                SecureStore.get(context, "src:${s.id}:url"),
                SecureStore.get(context, "src:${s.id}:user"),
                SecureStore.get(context, "src:${s.id}:password")
            )
            "OPDS" -> OpdsSource(
                context, s.id,
                SecureStore.get(context, "src:${s.id}:url"),
                SecureStore.get(context, "src:${s.id}:user"),
                SecureStore.get(context, "src:${s.id}:password")
            )
            else -> null
        }
        if (impl != null) open[s.id] = impl
        return impl
    }

    fun closeAll() {
        open.values.forEach { runCatching { it.close() } }
        open.clear()
    }
}
