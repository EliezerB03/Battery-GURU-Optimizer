package com.guru.batteryoptimizer.core

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

// Dark/Light Theme Behavior
class AppUI : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}