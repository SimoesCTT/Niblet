package com.niblet.app.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.niblet.app.data.NetworkSource
import com.niblet.app.data.SecureStore
import com.niblet.app.data.SourceStore
import com.niblet.app.data.source.SourceManager
import com.niblet.app.databinding.ActivityAddNetworkSourceBinding
import kotlinx.coroutines.launch

class AddNetworkSourceActivity : AppCompatActivity() {

    private lateinit var b: ActivityAddNetworkSourceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAddNetworkSourceBinding.inflate(layoutInflater)
        setContentView(b.root)

        val types = listOf("SMB", "SFTP", "WebDAV")
        b.kind.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, types))
        b.kind.setOnItemClickListener { _, _, pos, _ -> showFieldsFor(pos) }
        showFieldsFor(0)

        b.testButton.setOnClickListener { test() }
        b.saveButton.setOnClickListener { save() }
    }

    private fun showFieldsFor(i: Int) {
        val isSmb = i == 0; val isSftp = i == 1; val isDav = i == 2
        b.host.visibility  = if (isSmb || isSftp) View.VISIBLE else View.GONE
        b.share.visibility = if (isSmb) View.VISIBLE else View.GONE
        b.port.visibility  = if (isSftp) View.VISIBLE else View.GONE
        b.path.visibility  = if (isSmb || isSftp) View.VISIBLE else View.GONE
        b.url.visibility   = if (isDav) View.VISIBLE else View.GONE
    }

    private fun build(): NetworkSource? {
        val kind = listOf("SMB", "SFTP", "WEBDAV")[b.kind.listSelection]
        val id = "net-${System.currentTimeMillis()}"
        val name = when (kind) {
            "SMB" -> "SMB: ${b.host.editText?.text}/${b.share.editText?.text}"
            "SFTP" -> "SFTP: ${b.host.editText?.text}:${b.port.editText?.text}"
            else -> "WebDAV: ${b.url.editText?.text?.toString()?.substringAfter("://")?.substringBefore("/")}"
        }
        return NetworkSource(id = id, displayName = name, kind = kind)
    }

    private fun persist(id: String) {
        val kind = listOf("SMB", "SFTP", "WEBDAV")[b.kind.listSelection]
        when (kind) {
            "SMB" -> {
                SecureStore.put(this, "src:$id:host", b.host.editText?.text?.toString() ?: "")
                SecureStore.put(this, "src:$id:user", b.user.editText?.text?.toString() ?: "")
                SecureStore.put(this, "src:$id:password", b.pass.editText?.text?.toString() ?: "")
                SecureStore.put(this, "src:$id:share", b.share.editText?.text?.toString() ?: "")
                SecureStore.put(this, "src:$id:path", b.path.editText?.text?.toString() ?: "")
            }
            "SFTP" -> {
                SecureStore.put(this, "src:$id:host", b.host.editText?.text?.toString() ?: "")
                SecureStore.put(this, "src:$id:port", (b.port.editText?.text?.toString() ?: "").ifBlank { "22" })
                SecureStore.put(this, "src:$id:user", b.user.editText?.text?.toString() ?: "")
                SecureStore.put(this, "src:$id:password", b.pass.editText?.text?.toString() ?: "")
                SecureStore.put(this, "src:$id:path", b.path.editText?.text?.toString() ?: "")
            }
            "WEBDAV" -> {
                SecureStore.put(this, "src:$id:url", b.url.editText?.text?.toString() ?: "")
                SecureStore.put(this, "src:$id:user", b.user.editText?.text?.toString() ?: "")
                SecureStore.put(this, "src:$id:password", b.pass.editText?.text?.toString() ?: "")
            }
        }
    }

    private fun save() {
        val s = build() ?: run {
            Snackbar.make(b.root, "Fill required fields", Snackbar.LENGTH_SHORT).show()
            return
        }
        persist(s.id)
        SourceStore.add(this, s)
        finish()
    }

    private fun test() {
        val s = build() ?: run {
            Snackbar.make(b.root, "Fill required fields", Snackbar.LENGTH_SHORT).show()
            return
        }
        persist(s.id)
        lifecycleScope.launch {
            val r = runCatching {
                SourceManager.open(this@AddNetworkSourceActivity, s)?.listSeries()
            }
            Snackbar.make(b.root,
                r.map { "OK: ${it?.size ?: 0} series" }
                 .getOrElse { "Error: ${it.message}" },
                Snackbar.LENGTH_LONG).show()
        }
    }
}
