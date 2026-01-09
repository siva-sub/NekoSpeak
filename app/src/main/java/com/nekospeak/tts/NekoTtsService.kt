package com.nekospeak.tts

import android.content.Intent
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.media.AudioFormat
import android.util.Log
import com.nekospeak.tts.engine.TtsEngine
import com.nekospeak.tts.engine.KokoroEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * NekoSpeak Text-to-Speech Service
 * 
 * Provides TTS functionality using ONNX-based engines:
 * - Kokoro: Fast, simple TTS with multiple voices
 * - VibeVoice: Expressive, emotional TTS (future)
 */
class NekoTtsService : TextToSpeechService() {
    
    companion object {
        private const val TAG = "NekoTtsService"
        const val SAMPLE_RATE = 24000
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var currentEngine: TtsEngine? = null
    @Volatile private var stopRequested = false

    private val initJob = serviceScope.async {
        try {
            Log.i(TAG, "Starting engine initialization...")
            val engine = KokoroEngine(this@NekoTtsService)
            if (engine.initialize()) {
                Log.i(TAG, "Kokoro engine initialized successfully")
                currentEngine = engine
                engine
            } else {
                Log.e(TAG, "Failed to initialize Kokoro engine")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing engine", e)
            null
        }
    }
    
    // Supported languages (English for now)
    private val supportedLanguages = listOf(
        Locale.US,
        Locale.UK,
        Locale("en", "AU"),
        Locale("en", "IN")
    )
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NekoTtsService created")
        // initJob starts automatically
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (initJob.isCompleted) {
             runBlocking { initJob.await()?.release() }
        }
        serviceScope.cancel()
        Log.i(TAG, "NekoTtsService destroyed")
    }
    
    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        if (lang.isNullOrEmpty()) return TextToSpeech.LANG_NOT_SUPPORTED
        
        // Normalize input: Android settings often pass ISO3 ("eng", "USA")
        // We match against our supported locales which are usually ISO2 ("en", "US")
        val isEnglish = "eng".equals(lang, ignoreCase = true) || "en".equals(lang, ignoreCase = true)
        
        if (!isEnglish) return TextToSpeech.LANG_NOT_SUPPORTED
        
        // Return COUNTRY_AVAILABLE for ALL English variants to ensure "Play Sample" works
        // for users in SG, IN, AU, etc. even if we fallback to US/UK models.
        return TextToSpeech.LANG_COUNTRY_AVAILABLE
    }
    
    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        val isEnglish = "eng".equals(lang, ignoreCase = true) || "en".equals(lang, ignoreCase = true)
        if (isEnglish) {
            // Return current preferred voice, or a safe default
            // If the service isn't fully created, this might be tricky, but usually it is.
            return try {
                val prefs = com.nekospeak.tts.data.PrefsManager(this)
                prefs.currentVoice
            } catch (e: Exception) {
                "af_heart"
            }
        }
        return super.onGetDefaultVoiceNameFor(lang, country, variant)
    }
    
    override fun onGetLanguage(): Array<String> {
        return arrayOf("eng", "USA", "")
    }
    
    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }
    
    override fun onStop() {
        Log.d(TAG, "onStop called")
        stopRequested = true
    }
    
    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val text = request.charSequenceText?.toString() ?: return
        
        stopRequested = false
        
        if (text.isBlank()) {
            callback.done()
            return
        }
        
        // Wait for engine init (max 15 seconds)
        val engine = runBlocking {
            try {
                // Wait for init to complete
                kotlinx.coroutines.withTimeoutOrNull(15000) { 
                    initJob.await() 
                }
            } catch (e: Exception) {
                Log.e(TAG, "Timeout waiting for engine init", e)
                null
            }
        }

        if (engine == null || !engine.isInitialized()) {
            Log.e(TAG, "Engine not initialized after wait")
            callback.error()
            return
        }
        
        // Get speech parameters
        val speechRate = request.speechRate / 100f  // Convert from percentage
        
        // Determine voice to use
        // Determine voice to use
        // 1. Try requested voice (if valid)
        // 2. Try saved voice preference
        // 3. Fallback to default (engine handles null)
        val requestedVoice = if (android.os.Build.VERSION.SDK_INT >= 21) request.voiceName else null
        val availableVoices = engine.getVoices()
        
        val voiceToUse = if (requestedVoice != null && availableVoices.contains(requestedVoice)) {
            requestedVoice
        } else {
            // Fallback to saved preference
            val prefs = com.nekospeak.tts.data.PrefsManager(this)
            val savedVoice = prefs.currentVoice
            if (availableVoices.contains(savedVoice)) savedVoice else null
        }
        
        Log.d(TAG, "Synthesizing: '$text' (rate=$speechRate, voice=$voiceToUse)")
        
        // Start audio stream
        callback.start(
            SAMPLE_RATE,
            AudioFormat.ENCODING_PCM_16BIT,
            1  // Mono
        )
        
        try {
            // Run synthesis (blocking for TTS callback)
            runBlocking {
                engine.generate(
                    text = text,
                    speed = speechRate.coerceIn(0.5f, 2.0f),
                    voice = voiceToUse
                ) { samples ->
                    if (stopRequested) return@generate
                    
                    // Convert float samples to PCM 16-bit bytes
                    val bytes = floatToPcm16(samples)
                    
                    // Stream audio in chunks
                    val maxBufferSize = callback.maxBufferSize
                    var offset = 0
                    while (offset < bytes.size) {
                        if (stopRequested) return@generate
                        val bytesToWrite = minOf(maxBufferSize, bytes.size - offset)
                        callback.audioAvailable(bytes, offset, bytesToWrite)
                        offset += bytesToWrite
                    }
                }
                // Log total bytes? We can't easily here without a var
                // But we can trust if it works.
            }
            
            callback.done()
            Log.d(TAG, "Synthesis complete")
            
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis error", e)
            callback.error()
        }
    }

    override fun onGetVoices(): List<Voice> {
        val engine = currentEngine ?: return emptyList()
        return engine.getVoices().map { name ->
            val locale = if (name.startsWith("af") || name.startsWith("am")) Locale.US else Locale.UK
            // Simple mapping of gender/region
            val gender = if (name.contains("female") || name.startsWith("af") || name.startsWith("bf")) {
                 "female" // Voice params is complex, using constructor
            } else {
                 "male"
            }
            
            Voice(
                name,
                locale,
                Voice.QUALITY_VERY_HIGH,
                Voice.LATENCY_NORMAL,
                false,
                setOf(gender)
            )
        }
    }
    
    /**
     * Convert float audio samples [-1.0, 1.0] to PCM 16-bit bytes
     */
    private fun floatToPcm16(samples: FloatArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            // Clamp and scale to 16-bit range
            val sample = (samples[i] * 32767f).toInt().coerceIn(-32768, 32767)
            // Little-endian byte order
            bytes[2 * i] = sample.toByte()
            bytes[2 * i + 1] = (sample shr 8).toByte()
        }
        return bytes
    }
}
