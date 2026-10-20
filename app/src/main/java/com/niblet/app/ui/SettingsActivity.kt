package com.niblet.app.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.niblet.app.data.PerSeriesStore
import com.niblet.app.data.SettingsStore
import com.niblet.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        title = "Settings"

        val dirs = listOf("LTR", "RTL", "WEBTOON")
        b.directionSpinner.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, dirs))
        b.directionSpinner.setText(SettingsStore.direction(this).name, false)
        b.directionSpinner.setOnItemClickListener { _, _, pos, _ ->
            SettingsStore.setDirection(this, SettingsStore.Direction.valueOf(dirs[pos]))
        }

        val fits = listOf("PAGE", "WIDTH", "HEIGHT")
        b.fitSpinner.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, fits))
        b.fitSpinner.setText(SettingsStore.fit(this).name, false)
        b.fitSpinner.setOnItemClickListener { _, _, pos, _ ->
            SettingsStore.setFit(this, SettingsStore.Fit.valueOf(fits[pos]))
        }

        b.keepScreenOn.isChecked = SettingsStore.keepScreenOn(this)
        b.keepScreenOn.setOnCheckedChangeListener { _, on ->
            SettingsStore.setKeepScreenOn(this, on)
        }

        b.panelMode.isChecked = SettingsStore.panelMode(this)
        b.panelMode.setOnCheckedChangeListener { _, on ->
            SettingsStore.setPanelMode(this, on)
        }

        b.spreadMode.isChecked = SettingsStore.spreadMode(this)
        b.spreadMode.setOnCheckedChangeListener { _, on ->
            SettingsStore.setSpreadMode(this, on)
        }

        b.stats.text = "Pages read: ${SettingsStore.pagesRead(this)}"
        b.resetButton.setOnClickListener {
            SettingsStore.bumpPagesRead(this, -SettingsStore.pagesRead(this))
            b.stats.text = "Pages read: 0"
        }
    }
}
