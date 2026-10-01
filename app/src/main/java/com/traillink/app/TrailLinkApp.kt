package com.traillink.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.traillink.app.util.Prefs

class TrailLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        AppCompatDelegate.setDefaultNightMode(
            when (Prefs.themeMode) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
