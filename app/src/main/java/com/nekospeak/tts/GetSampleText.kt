package com.nekospeak.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

/**
 * Activity to provide sample text for TTS testing.
 * Called by Settings when testing the TTS engine.
 */
class GetSampleText : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val result = Intent()
        result.putExtra(
            TextToSpeech.Engine.EXTRA_SAMPLE_TEXT,
            "Hello! This is NekoSpeak, your AI-powered text to speech engine."
        )
        
        setResult(RESULT_OK, result)
        finish()
    }
}
