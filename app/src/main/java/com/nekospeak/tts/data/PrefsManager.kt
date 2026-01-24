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
        private const val KEY_SPEED = "speech_speed"
        private const val KEY_TOKEN_SIZE = "stream_token_size"
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

    var speechSpeed: Float
        get() = prefs.getFloat(KEY_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SPEED, value).apply()

    var streamTokenSize: Int
        get() = prefs.getInt(KEY_TOKEN_SIZE, 0)
        set(value) = prefs.edit().putInt(KEY_TOKEN_SIZE, value).apply()

    var isOnboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()
        
    // Future use
    var theme: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()
    
    // Pocket-TTS specific settings (from KevinAHM reference)
    var pocketTemperature: Float
        get() = prefs.getFloat("pocket_temperature", 0.7f)
        set(value) = prefs.edit().putFloat("pocket_temperature", value).apply()
    
    var pocketLsdSteps: Int
        get() = prefs.getInt("pocket_lsd_steps", 10)
        set(value) = prefs.edit().putInt("pocket_lsd_steps", value).apply()
    
    var pocketFramesAfterEos: Int
        get() = prefs.getInt("pocket_frames_after_eos", 3)
        set(value) = prefs.edit().putInt("pocket_frames_after_eos", value).apply()
    
    // Decoding mode: "batch" (collect-then-decode, higher quality) or "streaming" (adaptive chunking, lower latency)
    var pocketDecodingMode: String
        get() = prefs.getString("pocket_decoding_mode", "batch") ?: "batch"
        set(value) = prefs.edit().putString("pocket_decoding_mode", value).apply()
    
    // Chunk size for batch decoding (default 15 frames per reference)
    var pocketDecodeChunkSize: Int
        get() = prefs.getInt("pocket_decode_chunk_size", 15)
        set(value) = prefs.edit().putInt("pocket_decode_chunk_size", value).apply()
}
