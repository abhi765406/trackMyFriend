package com.traillink.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.traillink.app.R
import com.traillink.app.databinding.ActivityShareBinding
import com.traillink.app.service.LocationShareService
import com.traillink.app.util.CodeGenerator
import com.traillink.app.util.Prefs

class ShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareBinding
    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            tickHandler.postDelayed(this, 2000)
        }
    }

    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            requestBackgroundIfNeeded()
        } else {
            refreshPermissionBanner()
        }
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissionBanner() }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Prefs.mySharingCode.isBlank()) Prefs.mySharingCode = CodeGenerator.generate()
        binding.codeText.text = Prefs.mySharingCode
        selectIntervalRadio(Prefs.updateIntervalSeconds)

        binding.backBtn.setOnClickListener { finish() }
        binding.codeBox.setOnClickListener { copyCode() }
        binding.shareCodeButton.setOnClickListener { shareCode() }
        binding.grantPermissionButton.setOnClickListener { requestForegroundPermissions() }
        binding.toggleButton.setOnClickListener { onToggleClicked() }

        binding.intervalGroup.setOnCheckedChangeListener { _, checkedId ->
            Prefs.updateIntervalSeconds = when (checkedId) {
                R.id.interval10 -> 10
                R.id.interval30 -> 30
                else -> 15
            }
            if (Prefs.sharingActive) LocationShareService.start(this) // apply new interval immediately
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionBanner()
        refreshStatus()
        tickHandler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        tickHandler.removeCallbacks(tickRunnable)
    }

    private fun selectIntervalRadio(seconds: Int) {
        val id = when (seconds) {
            10 -> R.id.interval10
            30 -> R.id.interval30
            else -> R.id.interval15
        }
        binding.intervalGroup.check(id)
    }

    private fun hasForegroundLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true // implied pre-Android 10
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun refreshPermissionBanner() {
        val ok = hasForegroundLocationPermission() && hasBackgroundLocationPermission()
        binding.permissionBanner.visibility = if (ok) View.GONE else View.VISIBLE
        binding.toggleButton.isEnabled = true
    }

    private fun requestForegroundPermissions() {
        foregroundPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestBackgroundIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission()) {
            backgroundPermissionLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            refreshPermissionBanner()
        }
    }

    private fun onToggleClicked() {
        if (!Prefs.isConfigured()) {
            Toast.makeText(this, getString(R.string.not_configured_title), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        if (Prefs.sharingActive) {
            stopSharing()
        } else {
            if (!hasForegroundLocationPermission()) {
                requestForegroundPermissions()
                return
            }
            startSharing()
        }
    }

    private fun startSharing() {
        Prefs.sharingActive = true
        Prefs.sharingStartedAt = System.currentTimeMillis()
        LocationShareService.start(this)
        refreshStatus()
    }

    private fun stopSharing() {
        Prefs.sharingActive = false
        LocationShareService.stop(this)
        com.traillink.app.net.FirebaseRestClient.deleteLocation(Prefs.mySharingCode) { }
        refreshStatus()
    }

    private fun refreshStatus() {
        val active = Prefs.sharingActive
        binding.toggleButton.text = getString(if (active) R.string.stop_sharing else R.string.start_sharing)
        binding.toggleButton.setBackgroundResource(
            if (active) R.drawable.bg_danger_button else R.drawable.bg_primary_button
        )
        binding.statusDot.setBackgroundResource(R.drawable.bg_status_dot)
        binding.statusDot.alpha = if (active) 1f else 0.35f

        binding.statusText.text = when {
            !active -> getString(R.string.not_sharing)
            Prefs.lastSendSuccessAt == 0L -> getString(R.string.waiting_for_fix)
            else -> {
                val secondsAgo = ((System.currentTimeMillis() - Prefs.lastSendSuccessAt) / 1000).coerceAtLeast(0)
                "${getString(R.string.sharing_active)} · ${getString(R.string.last_updated_prefix)} ${secondsAgo}s ago"
            }
        }
    }

    private fun copyCode() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("TrailLink code", Prefs.mySharingCode))
        Toast.makeText(this, getString(R.string.tap_to_copy), Toast.LENGTH_SHORT).show()
    }

    private fun shareCode() {
        val text = "Track my live location on TrailLink.\nCode: ${Prefs.mySharingCode}\n" +
            "(Open TrailLink → Track Someone → paste this code)"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_code_via)))
    }
}
