package com.nekospeak.tts.engine

class EspeakWrapper {
    companion object {
        init {
            try {
                System.loadLibrary("nekospeak")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    external fun initialize(dataPath: String): Int
    external fun textToPhonemes(text: String, language: String): String
}
