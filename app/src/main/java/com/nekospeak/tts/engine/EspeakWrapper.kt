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
    }
    
    external fun initialize(dataPath: String): Int
    external fun textToPhonemes(text: String, language: String): String
}
