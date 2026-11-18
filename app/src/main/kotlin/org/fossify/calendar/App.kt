package org.fossify.calendar

import androidx.core.content.ContextCompat
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.hasDummyAlarm
import org.fossify.calendar.jobs.AppStartupWorker
import org.fossify.commons.FossifyApp

class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        applyPlanoraDefaultBrandThemeIfNeeded()
        if (!hasDummyAlarm()) {
            AppStartupWorker.start(this)
        }
    }

    private fun applyPlanoraDefaultBrandThemeIfNeeded() {
        val prefs = getSharedPreferences("planora_brand_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("planora_theme_v1_applied", false)) {
            config.apply {
                isSystemThemeEnabled = false
                primaryColor = ContextCompat.getColor(this@App, R.color.planora_indigo)
                accentColor = ContextCompat.getColor(this@App, R.color.planora_violet)
                backgroundColor = ContextCompat.getColor(this@App, R.color.planora_surface_light)
                textColor = ContextCompat.getColor(this@App, R.color.planora_text_dark)
            }
            prefs.edit().putBoolean("planora_theme_v1_applied", true).apply()
        }
    }
}
