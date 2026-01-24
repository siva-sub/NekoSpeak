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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlin.math.roundToInt

/**
 * NekoSpeak Text-to-Speech Service
 * 
 * Provides TTS functionality using ONNX-based engines:
 * - Kokoro: Fast, simple TTS with multiple voices
 * - Pocket-TTS: Streaming, voice cloning support
 * - Piper: Multiple voice models
 * 
 * Thread-safety: Uses Mutex to serialize synthesis and engine reload.
 * This prevents "engine released while generating" races.
 */
class NekoTtsService : TextToSpeechService() {
    
    companion object {
        private const val TAG = "NekoTtsService"
        private const val INIT_TIMEOUT_MS = 5000L  // 5 seconds max wait for engine init
    }
    
    private val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "CRITICAL: Uncaught Coroutine Exception", throwable)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    
    // Engine access is serialized via synthMutex
    @Volatile private var currentEngine: TtsEngine? = null
    
    // Mutex for thread-safe engine access: covers engine swap AND synthesis
    // This prevents releasing an engine while it's synthesizing
    private val synthMutex = Mutex()
    
    // Per-request stop flag (global for simplicity - serialized access means only one request at a time)
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
        
        // Start engine initialization
        reloadEngine()
    }

    private fun reloadEngine() {
        // Cancel any pending init to avoid race conditions
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
                    
                    // Acquire mutex before swapping engines
                    synthMutex.withLock {
                        val oldEngine = currentEngine
                        currentEngine = newEngine
                        
                        // Safely release old engine (no synthesis can be running while we hold mutex)
                        if (oldEngine != null && oldEngine != newEngine) {
                            Log.i(TAG, "Releasing old engine instance.")
                            oldEngine.release()
                        }
                    }
                    
                    newEngine
                } else {
                    Log.e(TAG, "Engine initialization FAILED. Keeping old engine if available.")
                    // DON'T swap engines on failure - keep the old working engine
                    // Return null to indicate failure
                    null
                }
            } catch (t: Throwable) {
                Log.e(TAG, "CRITICAL: InitJob crashed", t)
                // Don't swap on exception either
                null
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        getSharedPreferences("nekospeak_prefs", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)

        runBlocking {
            synthMutex.withLock {
                currentEngine?.release()
                currentEngine = null
            }
        }
        serviceScope.cancel()
        Log.i(TAG, "NekoTtsService destroyed")
    }
    
    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        if (lang.isNullOrEmpty()) return TextToSpeech.LANG_NOT_SUPPORTED
        
        // Normalize input: Android settings often pass ISO3 ("eng", "USA")
        val isEnglish = "eng".equals(lang, ignoreCase = true) || "en".equals(lang, ignoreCase = true)
        
        if (!isEnglish) {
            return TextToSpeech.LANG_NOT_SUPPORTED
        }
        
        return TextToSpeech.LANG_COUNTRY_AVAILABLE
    }
    
    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        val isEnglish = "eng".equals(lang, ignoreCase = true) || "en".equals(lang, ignoreCase = true)
        if (isEnglish) {
            return try {
                val prefs = com.nekospeak.tts.data.PrefsManager(this)
                val prefVoice = prefs.currentVoice
                
                // Validate that the voice exists in current engine
                val availableVoices = currentEngine?.getVoices() ?: emptyList()
                if (availableVoices.contains(prefVoice)) {
                    prefVoice
                } else if (availableVoices.isNotEmpty()) {
                    // Return first available voice as safe default
                    availableVoices.first()
                } else {
                    "af_heart"  // Ultimate fallback
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting default voice", e)
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
        // Request engine to stop current generation
        currentEngine?.stop()
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
        
        // Wait for engine init with short timeout (don't block binder thread long)
        val engine = runBlocking {
            try {
                val job = initJob
                if (job == null) {
                    Log.e(TAG, "[$reqId] InitJob is null!")
                    return@runBlocking currentEngine  // Try current engine
                }
                
                kotlinx.coroutines.withTimeoutOrNull(INIT_TIMEOUT_MS) { 
                    job.await() 
                } ?: currentEngine  // If timeout, try current engine
            } catch (e: Exception) {
                Log.e(TAG, "[$reqId] Error waiting for engine init", e)
                currentEngine  // Fall back to current engine
            }
        }

        if (engine == null || !engine.isInitialized()) {
            Log.e(TAG, "[$reqId] Engine not initialized")
            callback.error()
            return
        }
        
        // Get speech parameters
        val speechRate = request.speechRate / 100f
        
        // Determine voice with proper fallback chain
        val requestedVoice = if (android.os.Build.VERSION.SDK_INT >= 21) request.voiceName else null
        val availableVoices = engine.getVoices()
        
        val voiceToUse = when {
            requestedVoice != null && availableVoices.contains(requestedVoice) -> requestedVoice
            else -> {
                val prefs = com.nekospeak.tts.data.PrefsManager(this)
                val savedVoice = prefs.currentVoice
                when {
                    availableVoices.contains(savedVoice) -> savedVoice
                    availableVoices.isNotEmpty() -> availableVoices.first()
                    else -> null
                }
            }
        }
        
        // Use engine's sample rate instead of hardcoded value
        val sampleRate = engine.getSampleRate()
        
        Log.i(TAG, "[$reqId] Starting synthesis. Voice: $voiceToUse, Rate: $speechRate, SampleRate: $sampleRate")
        
        // Start audio stream with engine's sample rate
        callback.start(
            sampleRate,
            AudioFormat.ENCODING_PCM_16BIT,
            1  // Mono
        )
        
        try {
            // Acquire mutex to prevent engine release during synthesis
            runBlocking {
                synthMutex.withLock {
                    // Re-check engine is still valid after acquiring lock
                    if (currentEngine != engine || !engine.isInitialized()) {
                        Log.w(TAG, "[$reqId] Engine changed during synthesis start")
                        return@withLock
                    }
                    
                    engine.generate(
                        text = text,
                        speed = speechRate.coerceIn(0.5f, 2.0f),
                        voice = voiceToUse
                    ) { samples ->
                        if (stopRequested) {
                            return@generate
                        }
                        
                        // Convert float samples to PCM 16-bit bytes (with rounding for quality)
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
            }
            
            // Always call done() to signal completion to the system
            callback.done()
            
            if (stopRequested) {
                Log.i(TAG, "[$reqId] Synthesis stopped by request")
            } else {
                Log.i(TAG, "[$reqId] Synthesis complete successfully")
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
        
        // Use consistent locale (Locale.US) for all voices
        return engine.getVoices().map { name ->
            Voice(
                name,
                Locale.US,  // Consistent locale for all voices
                Voice.QUALITY_VERY_HIGH,
                Voice.LATENCY_NORMAL,
                false,
                emptySet()  // Don't misuse features for gender
            )
        }
    }
    
    /**
     * Convert float audio samples [-1.0, 1.0] to PCM 16-bit bytes
     * Uses rounding instead of truncation for better audio quality
     */
    private fun floatToPcm16(samples: FloatArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            // Clamp and scale to 16-bit range with rounding
            val sample = (samples[i] * 32767f).roundToInt().coerceIn(-32768, 32767)
            // Little-endian byte order
            bytes[2 * i] = sample.toByte()
            bytes[2 * i + 1] = (sample shr 8).toByte()
        }
        return bytes
    }
}
