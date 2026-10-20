package com.niblet.app.data

import android.content.Context
import org.json.JSONObject

object LastReadStore {
    private const val PREFS = "niblet_last_read"

    data class Entry(
        val seriesKey: String,
        val seriesId: String,
        val seriesTitle: String,
        val sourceId: String,
        val at: Long
    )

    fun mark(c: Context, seriesId: String, title: String, sourceId: String) {
        val key = "$sourceId|$seriesId"
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key, JSONObject().apply {
                put("id", seriesId)
                put("title", title)
                put("sourceId", sourceId)
                put("at", System.currentTimeMillis())
            }.toString())
            .apply()
    }

    fun all(c: Context): List<Entry> {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.all.mapNotNull { (k, v) ->
            runCatching {
                val o = JSONObject(v as String)
                Entry(k, o.getString("id"), o.getString("title"),
                    o.getString("sourceId"), o.getLong("at"))
            }.getOrNull()
        }.sortedByDescending { it.at }
    }

    fun prune(c: Context, keep: Int = 20) {
        val all = all(c)
        if (all.size <= keep) return
        val editor = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        all.drop(keep).forEach { editor.remove(it.seriesKey) }
        editor.apply()
    }
}
