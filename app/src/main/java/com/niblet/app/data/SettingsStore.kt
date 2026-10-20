package com.niblet.app.data

import android.content.Context

object SettingsStore {
    private const val PREFS = "niblet_settings"

    enum class Direction { LTR, RTL, WEBTOON }
    enum class Fit { WIDTH, HEIGHT, PAGE }

    fun setDirection(c: Context, d: Direction) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("dir", d.name).apply()
    fun direction(c: Context): Direction = runCatching {
        Direction.valueOf(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("dir", Direction.LTR.name)!!)
    }.getOrDefault(Direction.LTR)

    fun setFit(c: Context, f: Fit) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("fit", f.name).apply()
    fun fit(c: Context): Fit = runCatching {
        Fit.valueOf(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("fit", Fit.PAGE.name)!!)
    }.getOrDefault(Fit.PAGE)

    fun setKeepScreenOn(c: Context, on: Boolean) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("keepon", on).apply()
    fun keepScreenOn(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("keepon", true)

    fun setPanelMode(c: Context, on: Boolean) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("panels", on).apply()
    fun panelMode(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("panels", false)

    fun setPageTapDirection(c: Context, tapLeftIsPrev: Boolean) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("tapLeftPrev", tapLeftIsPrev).apply()
    fun pageTapDirection(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("tapLeftPrev", true)

    fun setSpreadMode(c: Context, on: Boolean) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("spread", on).apply()
    fun spreadMode(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("spread", true)

    fun bumpPagesRead(c: Context, n: Int = 1) {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putInt("pagesRead", p.getInt("pagesRead", 0) + n).apply()
    }
    fun pagesRead(c: Context): Int =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("pagesRead", 0)
}
