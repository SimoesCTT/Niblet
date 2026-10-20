package com.niblet.app.data

import android.content.Context

object PageCountStore {
    private const val PREFS = "niblet_page_counts"

    fun save(c: Context, chapterId: String, count: Int) {
        if (count <= 0) return
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(chapterId, count).apply()
    }

    fun load(c: Context, chapterId: String): Int {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(chapterId, -1)
    }

    fun clear(c: Context, chapterId: String) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(chapterId).apply()
    }
}
