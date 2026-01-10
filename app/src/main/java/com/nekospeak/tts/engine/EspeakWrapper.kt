package com.nekospeak.tts.engine

class EspeakWrapper {
    companion object {
        init {
            try {
                System.loadLibrary("nekospeak")
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        
        // Global lock to prevent concurrent initialization/usage of native Espeak library
        private val globalLock = Object()
        private var isNativeInitialized = false
    }
    
    // Native method is now private, accessed via updated safe public methods
    private external fun initialize(dataPath: String): Int
    private external fun textToPhonemes(text: String, language: String): String
    
    fun initializeSafe(dataPath: String): Int = synchronized(globalLock) {
        if (isNativeInitialized) {
            return 0 // Already initialized, success
        }
        val res = initialize(dataPath)
        if (res >= 0) { // Assuming 0 or positive sample rate is success
             isNativeInitialized = true
        }
        return res
    }
    
    fun textToPhonemesSafe(text: String, language: String): String = synchronized(globalLock) {
        return textToPhonemes(text, language)
    }
}
