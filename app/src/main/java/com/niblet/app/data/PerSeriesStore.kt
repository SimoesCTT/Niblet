package com.niblet.app.data

import android.content.Context

object PerSeriesStore {
    private const val PREFS = "niblet_per_series"

    fun setDirection(c: Context, seriesTitle: String, d: SettingsStore.Direction) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("dir:$seriesTitle", d.name).apply()
    }

    fun direction(c: Context, seriesTitle: String): SettingsStore.Direction? {
        val name = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("dir:$seriesTitle", null) ?: return null
        return runCatching { SettingsStore.Direction.valueOf(name) }.getOrNull()
    }

    fun effective(c: Context, seriesTitle: String): SettingsStore.Direction {
        return direction(c, seriesTitle) ?: SettingsStore.direction(c)
    }

    fun setFit(c: Context, seriesTitle: String, f: SettingsStore.Fit) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("fit:$seriesTitle", f.name).apply()
    }

    fun fit(c: Context, seriesTitle: String): SettingsStore.Fit? {
        val name = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("fit:$seriesTitle", null) ?: return null
        return runCatching { SettingsStore.Fit.valueOf(name) }.getOrNull()
    }

    fun effectiveFit(c: Context, seriesTitle: String): SettingsStore.Fit {
        return fit(c, seriesTitle) ?: SettingsStore.fit(c)
    }
}
