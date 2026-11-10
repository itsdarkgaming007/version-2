package com.quicksender.pro

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class QuickSenderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        applySavedTheme(this)
    }

    companion object {
        fun applySavedTheme(context: Context) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            AppCompatDelegate.setDefaultNightMode(themeMode)
        }

        fun updateLocale(context: Context, langCode: String): Context {
            val locale = Locale(langCode)
            Locale.setDefault(locale)
            val config = context.resources.configuration
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }
    }
}