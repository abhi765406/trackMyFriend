package com.traillink.app.util

import android.content.Context
import android.content.SharedPreferences

object Prefs {

    private const val FILE = "traillink_prefs"
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    /** The Firebase Realtime Database base URL, e.g. https://xxxx.firebaseio.com */
    var databaseUrl: String
        get() = sp.getString("database_url", "") ?: ""
        set(v) = sp.edit().putString("database_url", v.trim().removeSuffix("/")).apply()

    fun isConfigured(): Boolean = databaseUrl.isNotBlank()

    /** This device's own sharing code — generated once, reused every time sharing starts. */
    var mySharingCode: String
        get() = sp.getString("my_code", "") ?: ""
        set(v) = sp.edit().putString("my_code", v).apply()

    var sharingActive: Boolean
        get() = sp.getBoolean("sharing_active", false)
        set(v) = sp.edit().putBoolean("sharing_active", v).apply()

    /** Wall-clock time of the last successful send to the relay, 0 if none yet. */
    var lastSendSuccessAt: Long
        get() = sp.getLong("last_send_success_at", 0L)
        set(v) = sp.edit().putLong("last_send_success_at", v).apply()

    /** Wall-clock time sharing started, used to enforce the 12-hour auto-stop safeguard. */
    var sharingStartedAt: Long
        get() = sp.getLong("sharing_started_at", 0L)
        set(v) = sp.edit().putLong("sharing_started_at", v).apply()

    /** 10 = high accuracy, 30 = battery saver (seconds between updates) */
    var updateIntervalSeconds: Int
        get() = sp.getInt("update_interval", 15)
        set(v) = sp.edit().putInt("update_interval", v).apply()

    /** 0 = follow system, 1 = light, 2 = dark */
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(v) = sp.edit().putInt("theme_mode", v).apply()

    var recentTrackedCodes: List<String>
        get() = sp.getString("recent_codes", "")
            ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = sp.edit().putString("recent_codes", v.take(5).joinToString(",")).apply()

    fun addRecentCode(code: String) {
        val updated = (listOf(code) + recentTrackedCodes.filterNot { it == code }).take(5)
        recentTrackedCodes = updated
    }

    fun clearRecentCodes() {
        recentTrackedCodes = emptyList()
    }
}
