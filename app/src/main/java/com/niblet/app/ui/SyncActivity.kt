package com.niblet.app.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.niblet.app.data.SyncStore
import com.niblet.app.databinding.ActivitySyncBinding
import kotlinx.coroutines.launch

class SyncActivity : AppCompatActivity() {

    private lateinit var b: ActivitySyncBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySyncBinding.inflate(layoutInflater)
        setContentView(b.root)
        title = "Sync"

        if (SyncStore.isConfigured(this)) {
            b.configGroup.visibility = View.GONE
            b.actionGroup.visibility = View.VISIBLE
            b.configuredUrl.text = SyncStore.urlOf(this)
        } else {
            b.configGroup.visibility = View.VISIBLE
            b.actionGroup.visibility = View.GONE
        }

        b.saveConfig.setOnClickListener {
            val url = b.urlInput.editText?.text?.toString()?.trim().orEmpty()
            if (url.isBlank()) {
                snack("URL required"); return@setOnClickListener
            }
            val user = b.userInput.editText?.text?.toString().orEmpty()
            val pass = b.passInput.editText?.text?.toString().orEmpty()
            SyncStore.configure(this, url, user, pass)
            snack("Sync configured")
            recreate()
        }

        b.uploadButton.setOnClickListener {
            lifecycleScope.launch {
                val r = SyncStore.upload(this@SyncActivity)
                snack(r.getOrElse { "Upload failed: ${it.message}" })
            }
        }

        b.downloadButton.setOnClickListener {
            lifecycleScope.launch {
                val r = SyncStore.download(this@SyncActivity)
                snack(r.getOrElse { "Download failed: ${it.message}" })
            }
        }

        b.clearButton.setOnClickListener {
            SyncStore.clear(this)
            snack("Cleared")
            recreate()
        }
    }

    private fun snack(s: String) =
        Snackbar.make(b.root, s, Snackbar.LENGTH_LONG).show()
}
