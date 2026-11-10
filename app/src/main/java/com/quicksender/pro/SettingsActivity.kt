package com.quicksender.pro

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.quicksender.pro.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_lang", "en") ?: "en"
        super.attachBaseContext(QuickSenderApp.updateLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbarSettings.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        when (prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) {
            AppCompatDelegate.MODE_NIGHT_YES -> b.rbThemeDark.isChecked = true
            AppCompatDelegate.MODE_NIGHT_NO -> b.rbThemeLight.isChecked = true
            else -> b.rbThemeSystem.isChecked = true
        }

        b.rgTheme.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.rbThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                R.id.rbThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit().putInt("theme_mode", mode).apply()
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        val currentLang = prefs.getString("app_lang", "en") ?: "en"
        if (currentLang == "ta") b.rbLangTa.isChecked = true else b.rbLangEn.isChecked = true

        b.rgLang.setOnCheckedChangeListener { _, id ->
            val newLang = if (id == R.id.rbLangTa) "ta" else "en"
            if (newLang != currentLang) {
                prefs.edit().putString("app_lang", newLang).apply()
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            }
        }

        val savedDelay = prefs.getInt("default_delay", 5)
        b.sliderDefaultDelay.value = savedDelay.toFloat()
        b.tvDelayDisplay.text = "Default Dispatch Delay: ${savedDelay}s"

        b.sliderDefaultDelay.addOnChangeListener { _, value, _ ->
            val intVal = value.toInt()
            b.tvDelayDisplay.text = "Default Dispatch Delay: ${intVal}s"
            prefs.edit().putInt("default_delay", intVal).apply()
        }

        val savedBatch = prefs.getInt("batch_size", 25)
        b.sliderBatchSize.value = savedBatch.toFloat()
        b.tvBatchDisplay.text = "Batch Size (Pause every N msgs): $savedBatch"

        b.sliderBatchSize.addOnChangeListener { _, value, _ ->
            val intVal = value.toInt()
            b.tvBatchDisplay.text = "Batch Size (Pause every N msgs): $intVal"
            prefs.edit().putInt("batch_size", intVal).apply()
        }

        b.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}