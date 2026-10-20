package com.niblet.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(
    val chapterId: String,
    val chapterTitle: String,
    val page: Int,
    val seriesTitle: String,
    val sourceId: String,
    val at: Long
)

object BookmarksStore {
    private const val PREFS = "niblet_bookmarks"

    private fun read(c: Context): MutableList<Bookmark> {
        val raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("list", "[]") ?: "[]"
        val out = mutableListOf<Bookmark>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Bookmark(
                    chapterId = o.optString("c", ""),
                    chapterTitle = o.optString("ct", ""),
                    page = o.optInt("p", 0),
                    seriesTitle = o.optString("st", ""),
                    sourceId = o.optString("si", "local"),
                    at = o.optLong("at", 0L)
                ))
            }
        }
        return out
    }

    private fun write(c: Context, list: List<Bookmark>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("c", it.chapterId)
                put("ct", it.chapterTitle)
                put("p", it.page)
                put("st", it.seriesTitle)
                put("si", it.sourceId)
                put("at", it.at)
            })
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("list", arr.toString()).apply()
    }

    fun add(c: Context, b: Bookmark) {
        val l = read(c).filterNot { it.chapterId == b.chapterId && it.page == b.page }
            .toMutableList()
        l.add(b)
        write(c, l)
    }

    fun remove(c: Context, chapterId: String, page: Int) {
        write(c, read(c).filterNot { it.chapterId == chapterId && it.page == page })
    }

    fun all(c: Context): List<Bookmark> = read(c).sortedByDescending { it.at }
}
