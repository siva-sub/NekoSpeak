package com.nekospeak.tts.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "nekospeak_prefs"
        private const val KEY_VOICE = "current_voice"
        private const val KEY_MODEL = "current_model"
        private const val KEY_THREADS = "cpu_threads"
        private const val KEY_SPEED = "speech_speed"
        private const val KEY_TOKEN_SIZE = "stream_token_size"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_THEME_COLOR = "app_theme_v2"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_POCKET_TEMP = "pocket_temperature"
        private const val KEY_POCKET_LSD = "pocket_lsd_steps"
        private const val KEY_POCKET_EOS = "pocket_frames_after_eos"
        private const val KEY_POCKET_DECODE = "pocket_decoding_mode"
        private const val KEY_POCKET_CHUNK = "pocket_decode_chunk_size"
    }

    // Reactive counter for theme updates
    private val _themeCounter = MutableStateFlow(0)
    val themeCounter = _themeCounter.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_THEME_COLOR || key == KEY_DARK_MODE) {
            _themeCounter.value += 1
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    var currentVoice: String
        get() = prefs.getString(KEY_VOICE, "af_heart") ?: "af_heart"
        set(value) = prefs.edit().putString(KEY_VOICE, value).apply()

    var currentModel: String
        get() = prefs.getString(KEY_MODEL, "kokoro_v1.0") ?: "kokoro_v1.0"
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var cpuThreads: Int
        get() = prefs.getInt(KEY_THREADS, 6)
        set(value) = prefs.edit().putInt(KEY_THREADS, value).apply()

    var speechSpeed: Float
        get() = prefs.getFloat(KEY_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SPEED, value).apply()

    var streamTokenSize: Int
        get() = prefs.getInt(KEY_TOKEN_SIZE, 0)
        set(value) = prefs.edit().putInt(KEY_TOKEN_SIZE, value).apply()

    var isOnboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()
        
    var appTheme: String
        get() = prefs.getString(KEY_THEME_COLOR, "BLUE") ?: "BLUE"
        set(value) = prefs.edit().putString(KEY_THEME_COLOR, value).apply()
    
    var darkMode: String
        get() = prefs.getString(KEY_DARK_MODE, "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
        set(value) = prefs.edit().putString(KEY_DARK_MODE, value).apply()

    // Restored Pocket-TTS settings
    var pocketTemperature: Float
        get() = prefs.getFloat(KEY_POCKET_TEMP, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_POCKET_TEMP, value).apply()
    
    var pocketLsdSteps: Int
        get() = prefs.getInt(KEY_POCKET_LSD, 10)
        set(value) = prefs.edit().putInt(KEY_POCKET_LSD, value).apply()
    
    var pocketFramesAfterEos: Int
        get() = prefs.getInt(KEY_POCKET_EOS, 3)
        set(value) = prefs.edit().putInt(KEY_POCKET_EOS, value).apply()
    
    var pocketDecodingMode: String
        get() = prefs.getString(KEY_POCKET_DECODE, "batch") ?: "batch"
        set(value) = prefs.edit().putString(KEY_POCKET_DECODE, value).apply()
    
    var pocketDecodeChunkSize: Int
        get() = prefs.getInt(KEY_POCKET_CHUNK, 15)
        set(value) = prefs.edit().putInt(KEY_POCKET_CHUNK, value).apply()
}
