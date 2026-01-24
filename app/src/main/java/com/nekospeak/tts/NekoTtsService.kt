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
    
    private val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "CRITICAL: Uncaught Coroutine Exception", throwable)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    @Volatile private var currentEngine: TtsEngine? = null
    @Volatile private var stopRequested = false
    
    private var initJob: kotlinx.coroutines.Deferred<TtsEngine?>? = null

    // Supported languages (English for now)
    private val supportedLanguages = listOf(
        Locale.US,
        Locale.UK,
        Locale("en", "AU"),
        Locale("en", "IN")
    )
    
    private lateinit var prefsManager: com.nekospeak.tts.data.PrefsManager
    
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "current_model", "cpu_threads" -> {
                Log.i(TAG, "Preference '$key' changed. Reloading engine...")
                reloadEngine()
            }
            "current_voice" -> Log.i(TAG, "Voice changed. Will be applied on next synthesis.")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NekoTtsService created")
        
        // Register Prefs Listener
        prefsManager = com.nekospeak.tts.data.PrefsManager(this)
        getSharedPreferences("nekospeak_prefs", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
        
        // Start init loop
        reloadEngine()
    }

    private fun reloadEngine() {
        // Cancel any pending init to avoid race conditions (especially with native libs)
        initJob?.cancel()
        
        initJob = serviceScope.async(Dispatchers.IO) {
            Log.i(TAG, "Starting engine initialization (Async)...")
            try {
                val prefs = com.nekospeak.tts.data.PrefsManager(applicationContext)
                val modelType = prefs.currentModel
                
                val newEngine = com.nekospeak.tts.engine.EngineFactory.createEngine(applicationContext, modelType)
                
                Log.i(TAG, "Created ${newEngine::class.java.simpleName}. Initializing...")
                
                if (newEngine.initialize()) {
                     Log.i(TAG, "Engine initialized successfully.")
                     
                     // Atomically swap
                     val oldEngine = currentEngine
                     currentEngine = newEngine
                     
                     // Safely release old engine
                     if (oldEngine != null && oldEngine != newEngine) {
                         Log.i(TAG, "Releasing old engine instance.")
                         oldEngine.release()
                     }
                     
                     newEngine
                } else {
                     Log.e(TAG, "Engine initialization FAILED.")
                     newEngine
                }
            } catch (t: Throwable) {
                Log.e(TAG, "CRITICAL: InitJob crashed", t)
                throw t
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        getSharedPreferences("nekospeak_prefs", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)

        val job = initJob
        if (job != null && job.isCompleted) {
             runBlocking { job.await()?.release() }
        }
        serviceScope.cancel()
        Log.i(TAG, "NekoTtsService destroyed")
    }
    
    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        Log.d(TAG, "onIsLanguageAvailable check: $lang-$country-$variant")
        if (lang.isNullOrEmpty()) return TextToSpeech.LANG_NOT_SUPPORTED
        
        // Normalize input: Android settings often pass ISO3 ("eng", "USA")
        // We match against our supported locales which are usually ISO2 ("en", "US")
        val isEnglish = "eng".equals(lang, ignoreCase = true) || "en".equals(lang, ignoreCase = true)
        
        if (!isEnglish) {
             Log.d(TAG, "Language NOT supported: $lang")
             return TextToSpeech.LANG_NOT_SUPPORTED
        }
        
        // Return COUNTRY_AVAILABLE for ALL English variants to ensure "Play Sample" works
        // for users in SG, IN, AU, etc. even if we fallback to US/UK models.
        Log.d(TAG, "Language supported (Generic English): $lang-$country")
        return TextToSpeech.LANG_COUNTRY_AVAILABLE
    }
    
    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        Log.d(TAG, "onGetDefaultVoiceNameFor: $lang-$country-$variant")
        val isEnglish = "eng".equals(lang, ignoreCase = true) || "en".equals(lang, ignoreCase = true)
        if (isEnglish) {
            // Return current preferred voice, or a safe default
            // If the service isn't fully created, this might be tricky, but usually it is.
            return try {
                val prefs = com.nekospeak.tts.data.PrefsManager(this)
                val voice = prefs.currentVoice
                Log.d(TAG, "Returning default voice: $voice")
                voice
            } catch (e: Exception) {
                Log.e(TAG, "Error getting default voice", e)
                "af_heart"
            }
        }
        return super.onGetDefaultVoiceNameFor(lang, country, variant)
    }
    
    override fun onGetLanguage(): Array<String> {
        // This is called when the system wants to know what is currently loaded.
        // We should probably return what they asked for if we support it?
        // Or return a safe default like eng-USA.
        Log.d(TAG, "onGetLanguage called")
        return arrayOf("eng", "USA", "")
    }
    
    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        Log.d(TAG, "onLoadLanguage: $lang-$country-$variant")
        val result = onIsLanguageAvailable(lang, country, variant)
        Log.d(TAG, "onLoadLanguage result: $result")
        return result
    }
    
    override fun onStop() {
        Log.d(TAG, "onStop called")
        stopRequested = true
    }
    
    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val text = request.charSequenceText?.toString() ?: return
        val reqId = System.identityHashCode(request)
        
        Log.i(TAG, "[$reqId] onSynthesizeText received. Length: ${text.length} chars")
        Log.v(TAG, "[$reqId] Text: ${text.take(100)}...")
        
        stopRequested = false
        
        if (text.isBlank()) {
            Log.i(TAG, "[$reqId] Text is blank, done.")
            callback.done()
            return
        }
        
        // Wait for engine init (max 15 seconds)
        // Wait for engine init (max 30 seconds)
        val engine = runBlocking {
            try {
                // Wait for init to complete
                val job = initJob
                if (job == null) {
                    Log.e(TAG, "[$reqId] InitJob is null!")
                    return@runBlocking null
                }
                
                kotlinx.coroutines.withTimeoutOrNull(30000) { 
                    job.await() 
                }
            } catch (e: Exception) {
                Log.e(TAG, "[$reqId] Timeout waiting for engine init", e)
                null
            }
        }

        if (engine == null || !engine.isInitialized()) {
            Log.e(TAG, "[$reqId] Engine not initialized after wait")
            callback.error()
            return
        }
        
        // Get speech parameters
        val speechRate = request.speechRate / 100f  // Convert from percentage
        
        // Determine voice
        val requestedVoice = if (android.os.Build.VERSION.SDK_INT >= 21) request.voiceName else null
        val availableVoices = engine.getVoices()
        
        val voiceToUse = if (requestedVoice != null && availableVoices.contains(requestedVoice)) {
            requestedVoice
        } else {
            val prefs = com.nekospeak.tts.data.PrefsManager(this)
            val savedVoice = prefs.currentVoice
            if (availableVoices.contains(savedVoice)) savedVoice else null
        }
        
        Log.i(TAG, "[$reqId] Starting synthesis. Voice: $voiceToUse, Rate: $speechRate")
        
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
                    if (stopRequested) {
                        Log.i(TAG, "[$reqId] Stop requested during generation")
                        return@generate
                    }
                    
                    // Convert float samples to PCM 16-bit bytes
                    val bytes = floatToPcm16(samples)
                    
                    // Stream audio in chunks
                    val maxBufferSize = callback.maxBufferSize
                    var offset = 0
                    while (offset < bytes.size) {
                        if (stopRequested) return@generate
                        val bytesToWrite = minOf(maxBufferSize, bytes.size - offset)
                        val ret = callback.audioAvailable(bytes, offset, bytesToWrite)
                        if (ret == TextToSpeech.ERROR) {
                             Log.w(TAG, "[$reqId] audioAvailable returned ERROR, stopping")
                             stopRequested = true
                             return@generate
                        }
                        offset += bytesToWrite
                    }
                }
            }
            
            if (!stopRequested) {
                callback.done()
                Log.i(TAG, "[$reqId] Synthesis complete successfully")
            } else {
                Log.i(TAG, "[$reqId] Synthesis stopped")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[$reqId] Synthesis error", e)
            callback.error()
        }
    }

    override fun onGetVoices(): List<Voice> {
        val engine = currentEngine ?: run {
             Log.w(TAG, "onGetVoices called but engine is null")
             return emptyList()
        }
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
