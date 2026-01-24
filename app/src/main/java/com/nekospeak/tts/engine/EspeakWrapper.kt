package com.nekospeak.tts.engine

import android.util.Log

class EspeakWrapper {
    companion object {
        private const val TAG = "EspeakWrapper"

        private val globalLock = Any()

        @Volatile private var isLibraryLoaded: Boolean = false
        @Volatile private var isNativeInitialized: Boolean = false
        @Volatile private var initResult: Int = Int.MIN_VALUE // unknown

        init {
            isLibraryLoaded = try {
                System.loadLibrary("nekospeak")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load native lib 'nekospeak'", t)
                false
            }
        }

        fun isReady(): Boolean = isLibraryLoaded && isNativeInitialized
        fun lastInitResult(): Int = initResult
    }

    private external fun initialize(dataPath: String): Int
    private external fun textToPhonemes(text: String, language: String): String

    /**
     * Initialise eSpeak native layer. Safe to call multiple times.
     * Returns the original init result (eg sample rate) once initialised.
     */
    fun initializeSafe(dataPath: String): Int = synchronized(globalLock) {
        if (!isLibraryLoaded) {
            initResult = -1
            return initResult
        }

        if (isNativeInitialized) {
            return initResult
        }

        val res = try {
            initialize(dataPath)
        } catch (t: Throwable) {
            Log.e(TAG, "Native initialize() threw", t)
            -1
        }

        initResult = res
        if (res >= 0) {
            isNativeInitialized = true
            Log.i(TAG, "eSpeak initialized successfully, result=$res")
        } else {
            // keep false, allow retry
            isNativeInitialized = false
            Log.e(TAG, "eSpeak init failed, result=$res")
        }
        return initResult
    }

    fun textToPhonemesSafe(text: String, language: String): String = synchronized(globalLock) {
        if (!isLibraryLoaded) {
            Log.w(TAG, "textToPhonemesSafe: native library not loaded")
            return ""
        }
        if (!isNativeInitialized) {
            Log.w(TAG, "textToPhonemesSafe: eSpeak not initialized")
            return ""
        }
        return try {
            textToPhonemes(text, language)
        } catch (t: Throwable) {
            Log.e(TAG, "Native textToPhonemes() threw", t)
            ""
        }
    }
}
