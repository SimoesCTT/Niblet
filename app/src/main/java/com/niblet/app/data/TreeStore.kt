package com.niblet.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri

object TreeStore {
    private const val PREFS = "niblet_tree"
    private const val KEY = "root"

    fun save(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, uri.toString()).apply()
    }

    fun load(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return Uri.parse(s)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
