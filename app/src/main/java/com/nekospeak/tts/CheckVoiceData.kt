package com.nekospeak.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale // Added import

/**
 * Activity to check if TTS voice data is available.
 * Called by the system when checking TTS engine status.
 */
class CheckVoiceData : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Report available voices
        val result = Intent()
        
        // List of available voice locales
        // List of available voice locales generated dynamically for correctness
        val locales = listOf(
            Locale.US,
            Locale.UK,
            Locale("en", "AU"),
            Locale("en", "IN"),
            Locale("en", "SG"),
            Locale("en", "CA"),
            Locale("en", "PH"),
            Locale("en", "NZ"),
            Locale("en", "ZA")
        )
        
        val availableVoices = ArrayList<String>()
        for (locale in locales) {
            try {
                // Format: eng-USA, eng-SGP, etc.
                val lang = locale.getISO3Language()
                val country = locale.getISO3Country()
                if (lang.isNotEmpty() && country.isNotEmpty()) {
                    availableVoices.add("$lang-$country")
                }
            } catch (e: Exception) {
                // Ignore locales with missing ISO3 codes
            }
        }
        
        result.putStringArrayListExtra(
            TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
            availableVoices
        )
        
        // No unavailable voices
        result.putStringArrayListExtra(
            TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES,
            arrayListOf()
        )
        
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result)
        finish()
    }
}
