package com.niblet.app.data

import android.content.Context

object ProgressStore {
    private const val PREFS = "niblet_progress"

    fun savePage(c: Context, chapterId: String, page: Int) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(chapterId, page).apply()

    fun loadPage(c: Context, chapterId: String): Int =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(chapterId, 0)
}
