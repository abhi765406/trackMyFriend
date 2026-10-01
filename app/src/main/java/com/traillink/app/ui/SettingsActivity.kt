package com.traillink.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import com.traillink.app.R
import com.traillink.app.databinding.ActivitySettingsBinding
import com.traillink.app.util.Prefs

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.databaseUrlInput.setText(Prefs.databaseUrl)
        selectThemeRadio(Prefs.themeMode)

        binding.backBtn.setOnClickListener { finish() }

        binding.saveUrlButton.setOnClickListener {
            val url = binding.databaseUrlInput.text.toString().trim()
            if (url.isNotBlank() && !url.startsWith("http")) {
                Toast.makeText(this, "URL should start with https://", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.databaseUrl = url
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        binding.openConsoleButton.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, "https://console.firebase.google.com/".toUri()))
            } catch (_: Exception) {
                Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.themeLight -> 1
                R.id.themeDark -> 2
                else -> 0
            }
            Prefs.themeMode = mode
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }

        binding.clearRecentRow.setOnClickListener {
            Prefs.clearRecentCodes()
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectThemeRadio(mode: Int) {
        val id = when (mode) {
            1 -> R.id.themeLight
            2 -> R.id.themeDark
            else -> R.id.themeSystem
        }
        binding.themeGroup.check(id)
    }
}
