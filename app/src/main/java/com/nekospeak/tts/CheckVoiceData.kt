package com.nekospeak.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

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
        val availableVoices = arrayListOf(
            "eng-USA",
            "eng-GBR",
            "eng-AUS"
        )
        
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
