package com.traillink.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.traillink.app.R
import com.traillink.app.databinding.ActivityMainBinding
import com.traillink.app.util.Prefs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.shareCard.setOnClickListener {
            startActivity(Intent(this, ShareActivity::class.java))
        }
        binding.trackCard.setOnClickListener {
            startActivity(Intent(this, TrackActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        binding.setupBanner.visibility = if (Prefs.isConfigured()) View.GONE else View.VISIBLE
        binding.shareStatusText.text = if (Prefs.sharingActive) {
            getString(R.string.sharing_active) + " · " + Prefs.mySharingCode
        } else {
            getString(R.string.not_sharing)
        }
    }
}
