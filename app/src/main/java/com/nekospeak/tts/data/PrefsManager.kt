package com.nekospeak.tts.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "nekospeak_prefs"
        private const val KEY_VOICE = "current_voice"
        private const val KEY_MODEL = "current_model"
        private const val KEY_THREADS = "cpu_threads"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_THEME = "app_theme" // "dark", "light", "system"
    }

    var currentVoice: String
        get() = prefs.getString(KEY_VOICE, "af_heart") ?: "af_heart"
        set(value) = prefs.edit().putString(KEY_VOICE, value).apply()

    var currentModel: String
        get() = prefs.getString(KEY_MODEL, "kokoro_v1.0") ?: "kokoro_v1.0"
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var cpuThreads: Int
        get() = prefs.getInt(KEY_THREADS, 6) // Default to 6 (optimized)
        set(value) = prefs.edit().putInt(KEY_THREADS, value).apply()

    var isOnboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()
        
    // Future use
    var theme: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()
}
