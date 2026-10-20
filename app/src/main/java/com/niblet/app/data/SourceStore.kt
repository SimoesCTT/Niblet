package com.niblet.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NetworkSource(
    val id: String,
    val displayName: String,
    val kind: String  // SMB, SFTP, WEBDAV
)

object SourceStore {
    private const val PREFS = "niblet_sources"
    private const val KEY = "list"

    fun load(ctx: Context): List<NetworkSource> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                NetworkSource(
                    id = o.getString("id"),
                    displayName = o.getString("displayName"),
                    kind = o.getString("kind")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(ctx: Context, list: List<NetworkSource>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id); put("displayName", s.displayName); put("kind", s.kind)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun add(ctx: Context, s: NetworkSource) {
        val l = load(ctx).toMutableList(); l.removeAll { it.id == s.id }; l.add(s); save(ctx, l)
    }

    fun remove(ctx: Context, id: String) {
        save(ctx, load(ctx).filterNot { it.id == id })
        listOf("host","port","user","password","share","path","url").forEach {
            SecureStore.remove(ctx, "src:$id:$it")
        }
    }
}
