package com.niblet.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SyncStore {
    private const val PREFS = "niblet_sync"
    private const val SNAP = "niblet-progress.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun configure(ctx: Context, url: String, user: String, pass: String) {
        SecureStore.put(ctx, "sync:url", url.trimEnd('/'))
        SecureStore.put(ctx, "sync:user", user)
        SecureStore.put(ctx, "sync:pass", pass)
    }

    fun clear(ctx: Context) {
        SecureStore.remove(ctx, "sync:url")
        SecureStore.remove(ctx, "sync:user")
        SecureStore.remove(ctx, "sync:pass")
    }

    fun isConfigured(ctx: Context): Boolean =
        SecureStore.get(ctx, "sync:url").isNotBlank()

    fun urlOf(ctx: Context): String = SecureStore.get(ctx, "sync:url")

    private fun urlFor(ctx: Context): String {
        val base = SecureStore.get(ctx, "sync:url").trimEnd('/')
        return "$base/$SNAP"
    }

    private fun auth(ctx: Context): String? {
        val u = SecureStore.get(ctx, "sync:user")
        return if (u.isBlank()) null
               else Credentials.basic(u, SecureStore.get(ctx, "sync:pass"))
    }

    suspend fun upload(ctx: Context): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured(ctx)) return@withContext Result.failure(Exception("not configured"))
        runCatching {
            val json = buildSnapshot(ctx).toString(2)
            val req = Request.Builder()
                .url(urlFor(ctx))
                .put(json.toRequestBody("application/json".toMediaType()))
                .apply { auth(ctx)?.let { addHeader("Authorization", it) } }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                "Uploaded ${countLocal(ctx)} entries"
            }
        }
    }

    suspend fun download(ctx: Context): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured(ctx)) return@withContext Result.failure(Exception("not configured"))
        runCatching {
            val req = Request.Builder()
                .url(urlFor(ctx))
                .get()
                .apply { auth(ctx)?.let { addHeader("Authorization", it) } }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val json = resp.body?.string() ?: error("empty response")
                val n = merge(ctx, JSONObject(json))
                "Merged $n entries"
            }
        }
    }

    private fun countLocal(ctx: Context): Int =
        ctx.getSharedPreferences("niblet_progress", Context.MODE_PRIVATE).all.size

    private fun buildSnapshot(ctx: Context): JSONObject {
        val progress = ctx.getSharedPreferences("niblet_progress", Context.MODE_PRIVATE)
        val entries = JSONObject()
        progress.all.forEach { (k, v) ->
            if (v is Int) {
                entries.put(k, JSONObject().apply {
                    put("page", v)
                    put("at", System.currentTimeMillis())
                })
            }
        }
        return JSONObject().apply {
            put("schema", 1)
            put("entries", entries)
        }
    }

    private fun merge(ctx: Context, remote: JSONObject): Int {
        if (remote.optInt("schema") != 1) return 0
        val entries = remote.optJSONObject("entries") ?: return 0
        val local = ctx.getSharedPreferences("niblet_progress", Context.MODE_PRIVATE)
        val editor = local.edit()
        var merged = 0
        for (key in entries.keys()) {
            val o = entries.getJSONObject(key)
            val remotePage = o.optInt("page", 0)
            val localPage = local.getInt(key, -1)
            if (remotePage != localPage && remotePage > 0) {
                editor.putInt(key, remotePage)
                merged++
            }
        }
        editor.apply()
        return merged
    }
}
