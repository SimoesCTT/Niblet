package com.niblet.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStore {
    private const val PREFS = "niblet_secure"

    private fun prefs(ctx: Context) = try {
        val key = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx, PREFS, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        ctx.getSharedPreferences(PREFS + "_fallback", Context.MODE_PRIVATE)
    }

    fun put(ctx: Context, k: String, v: String) = prefs(ctx).edit().putString(k, v).apply()
    fun get(ctx: Context, k: String): String = prefs(ctx).getString(k, "") ?: ""
    fun remove(ctx: Context, k: String) = prefs(ctx).edit().remove(k).apply()
}
