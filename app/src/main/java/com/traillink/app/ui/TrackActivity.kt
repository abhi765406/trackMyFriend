package com.traillink.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.traillink.app.R
import com.traillink.app.data.LocationPoint
import com.traillink.app.databinding.ActivityTrackBinding
import com.traillink.app.net.FirebaseRestClient
import com.traillink.app.util.CodeGenerator
import com.traillink.app.util.Prefs
import kotlin.math.roundToInt

class TrackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrackBinding
    private var currentCode: String? = null
    private var lastPoint: LocationPoint? = null
    private var mapReady = false

    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            currentCode?.let { fetchLocation(it) }
            pollHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        renderRecentCodes()

        binding.backBtn.setOnClickListener { finish() }
        binding.trackButton.setOnClickListener { attemptTrack() }
        binding.refreshButton.setOnClickListener { currentCode?.let { fetchLocation(it) } }
        binding.changeCodeText.setOnClickListener { showEntry() }
        binding.openMapsButton.setOnClickListener { openInExternalMaps() }

        binding.codeInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                attemptTrack(); true
            } else false
        }
    }

    private fun setupMap() {
        binding.mapWebView.settings.javaScriptEnabled = true
        binding.mapWebView.settings.domStorageEnabled = true
        binding.mapWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                mapReady = true
                lastPoint?.let { pushToMap(it) }
            }
        }
        binding.mapWebView.loadUrl("file:///android_asset/map.html")
    }

    private fun attemptTrack() {
        val raw = binding.codeInput.text.toString()
        if (!CodeGenerator.isPlausible(raw)) {
            Toast.makeText(this, getString(R.string.enter_code_hint), Toast.LENGTH_SHORT).show()
            return
        }
        if (!Prefs.isConfigured()) {
            Toast.makeText(this, getString(R.string.not_configured_title), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val code = CodeGenerator.normalize(raw)
        Prefs.addRecentCode(code)
        startTracking(code)
    }

    private fun startTracking(code: String) {
        currentCode = code
        lastPoint = null
        mapReady = binding.mapWebView.url != null
        binding.entrySection.visibility = View.GONE
        binding.mapSection.visibility = View.VISIBLE
        binding.refreshButton.visibility = View.VISIBLE
        binding.trackedCodeText.text = formatCode(code)
        binding.lastUpdatedText.text = getString(R.string.waiting_for_fix)
        fetchLocation(code)
        pollHandler.removeCallbacks(pollRunnable)
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    private fun showEntry() {
        currentCode = null
        pollHandler.removeCallbacks(pollRunnable)
        binding.entrySection.visibility = View.VISIBLE
        binding.mapSection.visibility = View.GONE
        binding.refreshButton.visibility = View.GONE
        renderRecentCodes()
    }

    private fun fetchLocation(code: String) {
        FirebaseRestClient.getLocation(code) { point, error ->
            if (currentCode != code) return@getLocation // user navigated away mid-request
            if (point != null) {
                lastPoint = point
                pushToMap(point)
                updateInfoPanel(point)
            } else {
                binding.lastUpdatedText.text = when (error) {
                    "not_found" -> "No location yet for this code — ask them to tap Start Sharing"
                    "network" -> "Can't reach the server — retrying…"
                    else -> "Couldn't load location — retrying…"
                }
            }
        }
    }

    private fun pushToMap(point: LocationPoint) {
        if (!mapReady) return
        val js = "updateLocation(${point.lat}, ${point.lng}, ${point.accuracyMeters});"
        binding.mapWebView.evaluateJavascript(js, null)
    }

    private fun updateInfoPanel(point: LocationPoint) {
        val secondsAgo = ((System.currentTimeMillis() - point.timestampMillis) / 1000).coerceAtLeast(0)
        binding.lastUpdatedText.text =
            "${getString(R.string.last_updated_prefix)} ${DateUtils.formatElapsedTime(secondsAgo)} ago"
        binding.speedText.text = "${point.speedKmh.roundToInt()} ${getString(R.string.km_per_hour)}"
        binding.accuracyText.text =
            "${getString(R.string.accuracy_prefix)}${point.accuracyMeters.roundToInt()}${getString(R.string.meters_suffix)}"
    }

    private fun formatCode(code: String): String =
        if (code.length == 8) "${code.substring(0, 4)}-${code.substring(4, 8)}" else code

    private fun openInExternalMaps() {
        val p = lastPoint ?: return
        val uri = "geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}".toUri()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, "No maps app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderRecentCodes() {
        val recents = Prefs.recentTrackedCodes
        binding.recentLabel.visibility = if (recents.isEmpty()) View.GONE else View.VISIBLE
        binding.recentCodesContainer.removeAllViews()
        recents.forEach { code ->
            val tv = TextView(this).apply {
                text = formatCode(code)
                setPadding(20, 16, 20, 16)
                textSize = 14f
                setBackgroundResource(R.drawable.bg_recent_chip)
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 10
                layoutParams = lp
                setOnClickListener { startTracking(code) }
            }
            binding.recentCodesContainer.addView(tv)
        }
    }

    override fun onResume() {
        super.onResume()
        currentCode?.let {
            pollHandler.removeCallbacks(pollRunnable)
            pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
        }
    }

    override fun onPause() {
        super.onPause()
        pollHandler.removeCallbacks(pollRunnable)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 8000L
    }
}
