package com.nekospeak.tts.engine.pocket

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.nekospeak.tts.engine.TtsEngine
import com.nekospeak.tts.data.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Pocket-TTS Engine implementation with voice cloning support.
 * 
 * Uses 5 ONNX models:
 * - mimi_encoder: Audio -> Speaker latents (for voice cloning)
 * - text_conditioner: Text tokens -> Embeddings
 * - flow_lm_main: Backbone transformer
 * - flow_lm_flow: Flow matching ODE solver step
 * - mimi_decoder: Latents -> Audio
 */
class PocketTtsEngine(private val context: Context) : TtsEngine {
    
    companion object {
        private const val TAG = "PocketTtsEngine"
        const val SAMPLE_RATE = 24000
        const val LATENT_DIM = 32
        const val EMBED_DIM = 1024
        const val ODE_STEPS = 20  // More steps = better quality (default: 10, max: 50)
        
        // Model paths (relative to filesDir/pocket/models/)
        // Note: mimi_encoder and text_conditioner are FP32 only (no INT8 version available)
        private const val MODELS_DIR = "pocket/models"
        private const val MODEL_MIMI_ENCODER = "mimi_encoder.onnx"
        private const val MODEL_TEXT_CONDITIONER = "text_conditioner.onnx"
        private const val MODEL_FLOW_LM_MAIN = "flow_lm_main_int8.onnx"
        private const val MODEL_FLOW_LM_FLOW = "flow_lm_flow_int8.onnx"
        private const val MODEL_MIMI_DECODER = "mimi_decoder_int8.onnx"
        
        // Bundled voices path
        private const val BUNDLED_VOICES_DIR = "pocket/voices"
        
        // Cloned voices path
        private const val CLONED_VOICES_DIR = "pocket/cloned_voices"
    }
    
    private var ortEnv: OrtEnvironment? = null
    private var mimiEncoder: OrtSession? = null
    private var textConditioner: OrtSession? = null
    private var flowLmMain: OrtSession? = null
    private var flowLmFlow: OrtSession? = null
    private var mimiDecoder: OrtSession? = null
    
    private var mimiCodec: MimiCodec? = null
    private var tokenizer: PocketTokenizer? = null
    private var gtcrnDenoiser: GtcrnDenoiser? = null
    
    private val voiceStates = mutableMapOf<String, PocketVoiceState>()
    private var currentVoice: String = "alba"
    private var initialized = false
    @Volatile private var isReleased = false
    @Volatile private var stopRequested = false
    
    // Preferences for generation parameters
    private val prefs: PrefsManager by lazy { PrefsManager(context) }
    
    // Handler for showing Toast messages from background threads
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        mainHandler.post {
            Toast.makeText(context, message, duration).show()
        }
    }
    
    // Flow LM state (for autoregressive generation) - map of state name to tensor data
    // Each entry has: name -> Pair(type: String, data: Any) where data is FloatArray or LongArray
    private var flowLmState: MutableMap<String, Pair<String, Any>>? = null
    
    // Pre-computed flow buffers for Euler integration (s/t time steps)
    // Computed once per lsdSteps value to avoid repeated allocation
    private var precomputedFlowBuffers: List<Pair<FloatArray, FloatArray>>? = null
    private var precomputedLsdSteps: Int = 0
    
    // Adaptive streaming engine - auto-tunes based on device performance
    private val performanceTracker = PerformanceTracker()
    
    /**
     * Tracks device performance and calculates optimal buffer sizes
     */
    private class PerformanceTracker {
        private val recentFrameTimes = mutableListOf<Long>()
        private val maxSamples = 50
        private val audioFrameDurationMs = 80L // Each frame is 80ms of audio
        private var totalFrames = 0
        
        // Cached optimal values
        var optimalInitialBuffer = 15
            private set
        var optimalDecodeThreshold = 10
            private set
        var optimalReserve = 5
            private set
        
        @Synchronized
        fun recordFrameTime(timeMs: Long) {
            recentFrameTimes.add(timeMs)
            if (recentFrameTimes.size > maxSamples) {
                recentFrameTimes.removeAt(0)
            }
            totalFrames++
            
            // Recalculate optimal values every 10 frames after initial 10
            if (totalFrames >= 10 && totalFrames % 10 == 0) {
                calculateOptimalValues()
            }
        }
        
        private fun calculateOptimalValues() {
            val avgFrameTime = recentFrameTimes.average()
            
            // Calculate generation-to-playback ratio
            // If generation takes 120ms but audio is 80ms, ratio = 1.5
            val genToPlayRatio = avgFrameTime / audioFrameDurationMs
            
            // Calculate optimal initial buffer based on device speed
            // Faster device (ratio ~1.0) needs smaller buffer
            // Slower device (ratio ~2.0) needs larger buffer
            optimalInitialBuffer = when {
                genToPlayRatio <= 1.0 -> 8   // Device is faster than real-time
                genToPlayRatio <= 1.2 -> 10  // Slightly slower
                genToPlayRatio <= 1.5 -> 15  // Moderately slower
                genToPlayRatio <= 2.0 -> 20  // Quite slow
                else -> 30                    // Very slow device
            }
            
            // Decode threshold - how many frames to buffer before decoding
            optimalDecodeThreshold = when {
                genToPlayRatio <= 1.0 -> 3
                genToPlayRatio <= 1.2 -> 5
                genToPlayRatio <= 1.5 -> 8
                else -> 10
            }
            
            // Reserve frames to keep in buffer
            optimalReserve = when {
                genToPlayRatio <= 1.0 -> 2
                genToPlayRatio <= 1.5 -> 4
                else -> 6
            }
            
            // Log adaptive values (this function only runs every 10 frames)
            Log.d(TAG, "[Adaptive] Avg frame: ${avgFrameTime.toLong()}ms, ratio: ${String.format("%.2f", genToPlayRatio)}, " +
                      "buffer: $optimalInitialBuffer, threshold: $optimalDecodeThreshold, reserve: $optimalReserve")
        }
        
        fun reset() {
            recentFrameTimes.clear()
            totalFrames = 0
        }
    }
    
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing Pocket-TTS Engine...")
            
            ortEnv = OrtEnvironment.getEnvironment()
            
            val modelsDir = File(context.filesDir, MODELS_DIR)
            if (!modelsDir.exists()) {
                Log.w(TAG, "Models directory not found. Models need to be downloaded first.")
                return@withContext false
            }
            
            // Check if all models exist
            val modelFiles = listOf(
                MODEL_MIMI_ENCODER,
                MODEL_TEXT_CONDITIONER,
                MODEL_FLOW_LM_MAIN,
                MODEL_FLOW_LM_FLOW,
                MODEL_MIMI_DECODER
            )
            
            for (model in modelFiles) {
                val modelFile = File(modelsDir, model)
                if (!modelFile.exists()) {
                    Log.w(TAG, "Model not found: $model. Download required.")
                    return@withContext false
                }
            }
            
            // Load all ONNX sessions
            val sessionOptions = createSessionOptions()
            
            Log.d(TAG, "Loading ONNX models...")
            mimiEncoder = loadModel(modelsDir, MODEL_MIMI_ENCODER, sessionOptions)
            textConditioner = loadModel(modelsDir, MODEL_TEXT_CONDITIONER, sessionOptions)
            flowLmMain = loadModel(modelsDir, MODEL_FLOW_LM_MAIN, sessionOptions)
            flowLmFlow = loadModel(modelsDir, MODEL_FLOW_LM_FLOW, sessionOptions)
            mimiDecoder = loadModel(modelsDir, MODEL_MIMI_DECODER, sessionOptions)
            
            // Initialize codec
            mimiCodec = MimiCodec(mimiEncoder!!, mimiDecoder!!, ortEnv!!)
            
            // Initialize tokenizer
            tokenizer = PocketTokenizer(context)
            tokenizer?.load()
            
            // Initialize GTCRN denoiser for audio preprocessing (downloads model on first use)
            gtcrnDenoiser = GtcrnDenoiser(context)
            // Initialize async - don't block engine init, model downloads on-demand
            CoroutineScope(Dispatchers.IO).launch {
                gtcrnDenoiser?.initialize()
            }
            
            // Load available voices
            loadBundledVoices()
            loadClonedVoices()
            
            Log.i(TAG, "Pocket-TTS initialized with ${voiceStates.size} voices")
            initialized = true
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            e.printStackTrace()
            false
        }
    }
    
    private fun createSessionOptions(): OrtSession.SessionOptions {
        val prefs = com.nekospeak.tts.data.PrefsManager(context)
        return OrtSession.SessionOptions().apply {
            // Use user-configured threads (default 6, which works well on most devices)
            setIntraOpNumThreads(prefs.cpuThreads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setMemoryPatternOptimization(true)
        }
    }
    
    private fun loadModel(dir: File, name: String, options: OrtSession.SessionOptions): OrtSession {
        val modelFile = File(dir, name)
        Log.d(TAG, "Loading model: ${modelFile.absolutePath} (${modelFile.length() / 1024 / 1024}MB)")
        return ortEnv!!.createSession(modelFile.absolutePath, options)
    }
    
    // Voice embeddings cache: voiceId -> FloatArray of shape [N * 1024] flattened
    private val voiceEmbeddings = mutableMapOf<String, FloatArray>()
    private val voiceEmbeddingFrames = mutableMapOf<String, Int>()
    
    private val VOICE_CACHE_DIR = "pocket/voice_cache"
    
    private suspend fun loadBundledVoices() = withContext(Dispatchers.IO) {
        val voicesDir = File(context.filesDir, BUNDLED_VOICES_DIR)
        val cacheDir = File(context.filesDir, VOICE_CACHE_DIR)
        cacheDir.mkdirs()
        
        if (!voicesDir.exists()) {
            Log.d(TAG, "No bundled voices directory at ${voicesDir.absolutePath}")
            return@withContext
        }
        
        val encoder = mimiEncoder ?: run {
            Log.e(TAG, "mimi_encoder not initialized, cannot encode voices")
            return@withContext
        }
        
        voicesDir.listFiles()?.filter { it.extension == "wav" }?.forEach { wavFile ->
            try {
                val voiceId = wavFile.nameWithoutExtension
                val cacheFile = File(cacheDir, "$voiceId.emb")
                
                // Try to load from cache first (fast path)
                val embeddings = if (cacheFile.exists()) {
                    Log.d(TAG, "Loading cached voice: $voiceId")
                    loadCachedEmbedding(cacheFile)
                } else {
                    // Encode from WAV (slow path - only first time)
                    Log.d(TAG, "Encoding voice: $voiceId from ${wavFile.name}")
                    val encoded = encodeVoiceFromWav(wavFile, encoder)
                    if (encoded != null) {
                        // Save to cache for next time
                        saveCachedEmbedding(cacheFile, encoded.first, encoded.second)
                    }
                    encoded
                }
                
                if (embeddings != null) {
                    voiceEmbeddings[voiceId] = embeddings.first
                    voiceEmbeddingFrames[voiceId] = embeddings.second
                    
                    // Also create a PocketVoiceState for compatibility
                    voiceStates[voiceId] = PocketVoiceState(
                        id = voiceId,
                        displayName = voiceId.replace("_", " ").replaceFirstChar { it.uppercase() },
                        latents = embeddings.first,
                        numFrames = embeddings.second,
                        isBundled = true
                    )
                    
                    Log.i(TAG, "Loaded voice: $voiceId (${embeddings.second} frames)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load voice: ${wavFile.name}", e)
            }
        }
        
        Log.i(TAG, "Loaded ${voiceEmbeddings.size} bundled voices")
        
        // Also load downloaded celebrity voices from pocket/downloaded_voices
        val downloadedVoicesDir = File(context.filesDir, "pocket/downloaded_voices")
        if (downloadedVoicesDir.exists()) {
            downloadedVoicesDir.listFiles()?.filter { it.extension == "wav" }?.forEach { wavFile ->
                try {
                    val voiceId = wavFile.nameWithoutExtension
                    val cacheFile = File(cacheDir, "$voiceId.emb")
                    
                    // Try to load from cache first (fast path)
                    val embeddings = if (cacheFile.exists()) {
                        Log.d(TAG, "Loading cached celebrity voice: $voiceId")
                        loadCachedEmbedding(cacheFile)
                    } else {
                        // Encode from WAV (slow path - only first time)
                        Log.d(TAG, "Encoding celebrity voice: $voiceId from ${wavFile.name}")
                        val encoded = encodeVoiceFromWav(wavFile, encoder)
                        if (encoded != null) {
                            // Save to cache for next time
                            saveCachedEmbedding(cacheFile, encoded.first, encoded.second)
                        }
                        encoded
                    }
                    
                    if (embeddings != null) {
                        voiceEmbeddings[voiceId] = embeddings.first
                        voiceEmbeddingFrames[voiceId] = embeddings.second
                        
                        // Create a PocketVoiceState for compatibility
                        voiceStates[voiceId] = PocketVoiceState(
                            id = voiceId,
                            displayName = voiceId.replace("_", " ").replaceFirstChar { it.uppercase() },
                            latents = embeddings.first,
                            numFrames = embeddings.second,
                            isBundled = false
                        )
                        
                        Log.i(TAG, "Loaded celebrity voice: $voiceId (${embeddings.second} frames)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load celebrity voice: ${wavFile.name}", e)
                }
            }
        }
        
        Log.i(TAG, "Total loaded voices: ${voiceEmbeddings.size}")
    }
    
    /**
     * Load cached voice embedding from disk.
     */
    private fun loadCachedEmbedding(file: File): Pair<FloatArray, Int>? {
        try {
            val bytes = file.readBytes()
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            
            // Read numFrames (4 bytes)
            val numFrames = buffer.int
            
            // Read embeddings
            val embeddings = FloatArray(numFrames * EMBED_DIM)
            for (i in embeddings.indices) {
                embeddings[i] = buffer.float
            }
            
            return Pair(embeddings, numFrames)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached embedding", e)
            return null
        }
    }
    
    /**
     * Save voice embedding to disk cache.
     */
    private fun saveCachedEmbedding(file: File, embeddings: FloatArray, numFrames: Int) {
        try {
            val buffer = java.nio.ByteBuffer.allocate(4 + embeddings.size * 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            
            buffer.putInt(numFrames)
            for (f in embeddings) {
                buffer.putFloat(f)
            }
            
            file.writeBytes(buffer.array())
            Log.d(TAG, "Saved voice cache: ${file.name} (${file.length() / 1024}KB)")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cached embedding", e)
        }
    }
    
    /**
     * Encode a WAV file to voice embeddings using mimi_encoder.
     * Returns Pair<embeddings[N*1024], numFrames> or null on failure.
     * 
     * Includes audio preprocessing for better voice cloning:
     * - Silence trimming (VAD-like)
     * - Peak normalization
     * - High-pass filter (removes low-frequency rumble)
     */
    private fun encodeVoiceFromWav(wavFile: File, encoder: OrtSession): Pair<FloatArray, Int>? {
        try {
            // Load WAV audio samples
            val rawSamples = loadAudioFile(wavFile)
            if (rawSamples == null || rawSamples.isEmpty()) {
                Log.e(TAG, "Failed to load audio from ${wavFile.name}")
                return null
            }
            
            Log.d(TAG, "Loaded ${rawSamples.size} samples from ${wavFile.name}")
            
            // PREPROCESSING PIPELINE for better voice cloning
            // NOTE: GTCRN neural denoising DISABLED - causes voice distortion (female voices sound male)
            // The GTCRN model may not be suitable for voice cloning preprocessing
            // Using raw audio with basic cleanup only
            val denoised = rawSamples
            
            // Step 1: High-pass filter to remove rumble (>80Hz)
            val filtered = applyHighPassFilter(denoised, 80.0f, SAMPLE_RATE)
            Log.d(TAG, "Applied high-pass filter (80Hz)")
            
            // Step 2: Trim silence from beginning and end
            val trimmed = trimSilence(filtered, silenceThreshold = 0.02f)
            Log.d(TAG, "Trimmed silence: ${denoised.size} -> ${trimmed.size} samples")
            
            // Step 3: Peak normalize to [-1, 1]
            val normalized = peakNormalize(trimmed)
            Log.d(TAG, "Peak normalized audio")
            
            // Use FIRST 30 seconds (720000 samples at 24kHz) for voice capture
            // More audio = better voice characterization, but too much can cause OOM
            val maxSamples = 30 * SAMPLE_RATE // 720000 samples = 30 seconds
            val truncatedSamples = if (normalized.size > maxSamples) {
                Log.d(TAG, "Truncating ${normalized.size} samples to $maxSamples (30 seconds)")
                normalized.copyOfRange(0, maxSamples)
            } else {
                normalized
            }
            
            // Prepare input tensor: [1, 1, numSamples]
            val inputShape = longArrayOf(1, 1, truncatedSamples.size.toLong())
            val inputTensor = OnnxTensor.createTensor(
                ortEnv!!,
                java.nio.FloatBuffer.wrap(truncatedSamples),
                inputShape
            )
            
            // Run mimi_encoder
            val outputs = encoder.run(mapOf("audio" to inputTensor))
            val outputTensor = outputs[0].value as Array<*>
            
            // Output shape is [1, N, 1024]
            @Suppress("UNCHECKED_CAST")
            val output3d = outputTensor as Array<Array<FloatArray>>
            val numFrames = output3d[0].size
            
            // Flatten to [N * 1024]
            val flattened = FloatArray(numFrames * EMBED_DIM)
            for (i in 0 until numFrames) {
                System.arraycopy(output3d[0][i], 0, flattened, i * EMBED_DIM, EMBED_DIM)
            }
            
            inputTensor.close()
            outputs.close()
            
            return Pair(flattened, numFrames)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding voice from WAV", e)
            return null
        }
    }
    
    /**
     * Apply a simple first-order high-pass filter to remove low-frequency rumble.
     */
    private fun applyHighPassFilter(samples: FloatArray, cutoffHz: Float, sampleRate: Int): FloatArray {
        if (samples.isEmpty()) return samples
        
        // RC high-pass filter: y[n] = alpha * (y[n-1] + x[n] - x[n-1])
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoffHz)
        val dt = 1.0f / sampleRate
        val alpha = rc / (rc + dt)
        
        val output = FloatArray(samples.size)
        output[0] = samples[0]
        
        for (i in 1 until samples.size) {
            output[i] = alpha * (output[i - 1] + samples[i] - samples[i - 1])
        }
        
        return output
    }
    
    /**
     * Trim silence from the beginning and end of the audio.
     * Uses a simple energy threshold for detection.
     */
    private fun trimSilence(samples: FloatArray, silenceThreshold: Float = 0.02f): FloatArray {
        if (samples.isEmpty()) return samples
        
        // Use a window-based approach for more robust detection
        val windowSize = SAMPLE_RATE / 50 // 20ms windows
        
        // Find first non-silent sample
        var startIdx = 0
        for (i in 0 until samples.size - windowSize step windowSize) {
            var rms = 0.0f
            for (j in i until minOf(i + windowSize, samples.size)) {
                rms += samples[j] * samples[j]
            }
            rms = kotlin.math.sqrt(rms / windowSize)
            
            if (rms > silenceThreshold) {
                // Back up a bit to include attack
                startIdx = maxOf(0, i - windowSize * 2)
                break
            }
        }
        
        // Find last non-silent sample
        var endIdx = samples.size
        for (i in samples.size - 1 downTo windowSize step windowSize) {
            var rms = 0.0f
            for (j in maxOf(0, i - windowSize) until i) {
                rms += samples[j] * samples[j]
            }
            rms = kotlin.math.sqrt(rms / windowSize)
            
            if (rms > silenceThreshold) {
                // Add a bit more to include decay
                endIdx = minOf(samples.size, i + windowSize * 2)
                break
            }
        }
        
        return if (endIdx > startIdx) {
            samples.copyOfRange(startIdx, endIdx)
        } else {
            samples // Don't modify if detection failed
        }
    }
    
    /**
     * Peak normalize audio to [-1, 1] range.
     */
    private fun peakNormalize(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        
        var maxAbs = 0.0f
        for (sample in samples) {
            val abs = kotlin.math.abs(sample)
            if (abs > maxAbs) maxAbs = abs
        }
        
        if (maxAbs < 0.001f) return samples // Avoid division by near-zero
        
        val scale = 0.95f / maxAbs // Normalize to 95% of full scale
        return FloatArray(samples.size) { samples[it] * scale }
    }
    
    /**
     * Resample audio from one sample rate to another using linear interpolation.
     * Simple but effective for 24kHz <-> 16kHz conversion.
     */
    private fun resample(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return samples
        if (samples.isEmpty()) return samples
        
        val ratio = fromRate.toDouble() / toRate
        val outputLength = (samples.size / ratio).toInt()
        val output = FloatArray(outputLength)
        
        for (i in 0 until outputLength) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt()
            val frac = (srcPos - srcIdx).toFloat()
            
            val sample1 = samples[srcIdx]
            val sample2 = if (srcIdx + 1 < samples.size) samples[srcIdx + 1] else sample1
            
            output[i] = sample1 * (1 - frac) + sample2 * frac
        }
        
        return output
    }
    
    /**
     * Load a WAV file and return audio samples as FloatArray at 24kHz mono, normalized to [-1, 1].
     * 
     * Reference: KevinAHM/pocket-tts-onnx _load_audio()
     * - Convert to mono (average channels if stereo)
     * - Resample to 24kHz
     * - Normalize to [-1, 1] range
     * 
     * This implementation properly scans WAV chunks (fmt, data) rather than assuming offset=44.
     */
    private fun loadAudioFile(file: File): FloatArray? {
        try {
            val inputStream = java.io.FileInputStream(file)
            val audioBytes = inputStream.readBytes()
            inputStream.close()
            
            // Validate RIFF header
            if (audioBytes.size < 12) {
                Log.e(TAG, "WAV file too small: ${audioBytes.size} bytes")
                return null
            }
            
            val riff = String(audioBytes, 0, 4, Charsets.US_ASCII)
            val wave = String(audioBytes, 8, 4, Charsets.US_ASCII)
            if (riff != "RIFF" || wave != "WAVE") {
                Log.e(TAG, "Invalid WAV header: RIFF='$riff', WAVE='$wave'")
                return null
            }
            
            // Scan for fmt and data chunks
            var offset = 12
            var audioFormat = 1  // 1=PCM, 3=IEEE Float
            var numChannels = 1
            var sampleRate = 44100
            var bitsPerSample = 16
            var dataOffset = -1
            var dataSize = 0
            
            while (offset < audioBytes.size - 8) {
                val chunkId = String(audioBytes, offset, 4, Charsets.US_ASCII)
                val chunkSize = readInt32LE(audioBytes, offset + 4)
                
                when (chunkId) {
                    "fmt " -> {
                        // fmt chunk: format info
                        if (offset + 8 + 16 <= audioBytes.size) {
                            audioFormat = readInt16LE(audioBytes, offset + 8)
                            numChannels = readInt16LE(audioBytes, offset + 10)
                            sampleRate = readInt32LE(audioBytes, offset + 12)
                            // Skip byteRate (4 bytes) and blockAlign (2 bytes)
                            bitsPerSample = readInt16LE(audioBytes, offset + 22)
                            Log.d(TAG, "WAV fmt: format=$audioFormat, $sampleRate Hz, $bitsPerSample bits, $numChannels ch")
                        }
                    }
                    "data" -> {
                        dataOffset = offset + 8
                        dataSize = chunkSize
                        Log.d(TAG, "WAV data: offset=$dataOffset, size=$dataSize")
                        break  // Found data, stop scanning
                    }
                    // Skip other chunks (LIST, JUNK, fact, etc.)
                }
                
                // Move to next chunk (align to word boundary)
                offset += 8 + chunkSize
                if (chunkSize % 2 == 1) offset++  // Padding byte
            }
            
            if (dataOffset < 0 || dataSize <= 0) {
                Log.e(TAG, "Could not find data chunk in WAV file")
                return null
            }
            
            // Validate data range
            if (dataOffset + dataSize > audioBytes.size) {
                dataSize = audioBytes.size - dataOffset
                Log.w(TAG, "WAV data chunk extends beyond file, truncating to $dataSize")
            }
            
            // Extract PCM samples
            val bytesPerSample = bitsPerSample / 8
            val numSamplesPerChannel = dataSize / (bytesPerSample * numChannels)
            
            // Process samples - convert stereo to mono if needed
            var samples = FloatArray(numSamplesPerChannel)
            val isFloat = (audioFormat == 3)  // IEEE float format
            
            for (i in 0 until numSamplesPerChannel) {
                val baseOffset = dataOffset + i * bytesPerSample * numChannels
                if (baseOffset + bytesPerSample * numChannels > audioBytes.size) break
                
                if (numChannels == 1) {
                    // Mono: just read the sample
                    samples[i] = readPcmSample(audioBytes, baseOffset, bitsPerSample, isFloat)
                } else {
                    // Stereo or multi-channel: average all channels (reference uses mean)
                    var sum = 0f
                    for (ch in 0 until numChannels) {
                        val chOffset = baseOffset + ch * bytesPerSample
                        sum += readPcmSample(audioBytes, chOffset, bitsPerSample, isFloat)
                    }
                    samples[i] = sum / numChannels
                }
            }
            
            // Resample to 24kHz if needed
            if (sampleRate != SAMPLE_RATE) {
                samples = resampleAudio(samples, sampleRate, SAMPLE_RATE)
            }
            
            // Normalize to [-1, 1] range if max amplitude exceeds 1.0
            // Reference: if np.abs(audio).max() > 1.0: audio = audio / np.abs(audio).max()
            val maxAbs = samples.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
            if (maxAbs > 1.0f) {
                Log.d(TAG, "Normalizing audio (max abs = $maxAbs)")
                for (i in samples.indices) {
                    samples[i] = samples[i] / maxAbs
                }
            }
            
            return samples
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading audio file", e)
            return null
        }
    }
    
    /** Read 16-bit little-endian integer */
    private fun readInt16LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }
    
    /** Read 32-bit little-endian integer */
    private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
    
    /**
     * Read a single PCM sample from audio bytes.
     * 
     * @param isFloat If true and bitsPerSample=32, interpret as IEEE float; otherwise as int32 PCM.
     */
    private fun readPcmSample(audioBytes: ByteArray, offset: Int, bitsPerSample: Int, isFloat: Boolean = false): Float {
        return when (bitsPerSample) {
            16 -> {
                // 16-bit signed PCM, little-endian
                val low = audioBytes[offset].toInt() and 0xFF
                val high = audioBytes[offset + 1].toInt()
                ((high shl 8) or low).toShort().toFloat() / 32768f
            }
            24 -> {
                // 24-bit signed PCM, little-endian
                // Must sign-extend from bit 23 to full 32-bit int
                val b0 = audioBytes[offset].toInt() and 0xFF
                val b1 = audioBytes[offset + 1].toInt() and 0xFF
                val b2 = audioBytes[offset + 2].toInt() and 0xFF
                var sample = (b2 shl 16) or (b1 shl 8) or b0
                // Sign extend: if bit 23 is set, extend with 0xFF
                if ((sample and 0x800000) != 0) {
                    sample = sample or 0xFF000000.toInt()
                }
                sample.toFloat() / 8388608f // 2^23
            }
            32 -> {
                val bits = (audioBytes[offset].toInt() and 0xFF) or
                          ((audioBytes[offset + 1].toInt() and 0xFF) shl 8) or
                          ((audioBytes[offset + 2].toInt() and 0xFF) shl 16) or
                          ((audioBytes[offset + 3].toInt() and 0xFF) shl 24)
                if (isFloat) {
                    // 32-bit IEEE float
                    java.lang.Float.intBitsToFloat(bits)
                } else {
                    // 32-bit signed PCM integer
                    bits.toFloat() / 2147483648f // 2^31
                }
            }
            else -> {
                // 8-bit PCM (unsigned, centered at 128)
                (audioBytes[offset].toInt() and 0xFF - 128) / 128f
            }
        }
    }
    
    /**
     * Simple linear interpolation resampling.
     */
    private fun resampleAudio(samples: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        val ratio = srcRate.toFloat() / dstRate
        val dstSize = (samples.size / ratio).toInt()
        val result = FloatArray(dstSize)
        
        for (i in 0 until dstSize) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt()
            val frac = srcPos - srcIdx
            
            result[i] = if (srcIdx + 1 < samples.size) {
                samples[srcIdx] * (1 - frac) + samples[srcIdx + 1] * frac
            } else {
                samples[srcIdx.coerceIn(0, samples.size - 1)]
            }
        }
        
        return result
    }
    
    private fun loadClonedVoices() {
        val voicesDir = File(context.filesDir, CLONED_VOICES_DIR)
        if (!voicesDir.exists()) {
            voicesDir.mkdirs()
            return
        }
        
        voicesDir.listFiles()?.filter { it.extension == "bin" }?.forEach { file ->
            try {
                val state = PocketVoiceState.fromBytes(file.readBytes())
                voiceStates[state.id] = state
                Log.d(TAG, "Loaded cloned voice: ${state.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cloned voice: ${file.name}", e)
            }
        }
    }
    
    override suspend fun generate(
        text: String,
        speed: Float,
        voice: String?,
        callback: (FloatArray) -> Unit
    ): Unit = withContext(Dispatchers.Default) {
        // Reset stop flag at start of new generation
        stopRequested = false
        
        // Check if engine was released (concurrent stop/restart scenario)
        if (isReleased || !initialized) {
            Log.w(TAG, "Generate called but engine is released or not initialized")
            return@withContext
        }
        
        val session = flowLmMain ?: run {
            Log.w(TAG, "Generate called but flowLmMain is null")
            return@withContext
        }
        val flowSession = flowLmFlow ?: run {
            Log.w(TAG, "Generate called but flowLmFlow is null")
            return@withContext
        }
        val conditioner = textConditioner ?: run {
            Log.w(TAG, "Generate called but textConditioner is null")
            return@withContext
        }
        val codec = mimiCodec ?: run {
            Log.w(TAG, "Generate called but mimiCodec is null")
            return@withContext
        }
        val tok = tokenizer ?: run {
            Log.w(TAG, "Generate called but tokenizer is null")
            return@withContext
        }
        
        val voiceName = voice ?: currentVoice
        var voiceState = voiceStates[voiceName]
        
        // On-demand encoding: If voice not loaded but WAV file exists, encode it now
        if (voiceState == null) {
            Log.d(TAG, "Voice $voiceName not in voiceStates, checking for downloadable/cloned voice...")
            
            // Check celebrity download directory
            val celebrityWav = File(context.filesDir, "pocket/downloaded_voices/$voiceName.wav")
            // Check cloned voices directory  
            val clonedFile = File(context.filesDir, "pocket/cloned_voices/$voiceName.bin")
            
            if (celebrityWav.exists()) {
                Log.i(TAG, "Found celebrity WAV, encoding on-demand: $voiceName")
                showToast("🎤 Encoding voice: ${voiceName.replace("_", " ")}...", Toast.LENGTH_LONG)
                
                val encoder = mimiEncoder
                if (encoder != null) {
                    val cacheDir = File(context.cacheDir, "pocket")
                    cacheDir.mkdirs()
                    val cacheFile = File(cacheDir, "$voiceName.emb")
                    
                    val isFromCache = cacheFile.exists()
                    val embeddings = if (isFromCache) {
                        loadCachedEmbedding(cacheFile)
                    } else {
                        val encoded = encodeVoiceFromWav(celebrityWav, encoder)
                        if (encoded != null) {
                            saveCachedEmbedding(cacheFile, encoded.first, encoded.second)
                        }
                        encoded
                    }
                    
                    if (embeddings != null) {
                        voiceState = PocketVoiceState(
                            id = voiceName,
                            displayName = voiceName.replace("_", " ").replaceFirstChar { it.uppercase() },
                            latents = embeddings.first,
                            numFrames = embeddings.second,
                            isBundled = false
                        )
                        voiceStates[voiceName] = voiceState
                        Log.i(TAG, "On-demand encoded celebrity voice: $voiceName (${embeddings.second} frames)")
                        val loadedMsg = if (isFromCache) "⚡ Voice loaded: ${voiceName.replace("_", " ")}" else "✅ Voice ready: ${voiceName.replace("_", " ")}"
                        showToast(loadedMsg)
                    } else {
                        showToast("❌ Failed to encode voice", Toast.LENGTH_LONG)
                    }
                }
            } else if (clonedFile.exists()) {
                Log.i(TAG, "Found cloned voice file, loading: $voiceName")
                showToast("📂 Loading voice: ${voiceName.replace("_", " ")}...")
                try {
                    val state = PocketVoiceState.fromBytes(clonedFile.readBytes())
                    voiceStates[state.id] = state
                    voiceState = state
                    Log.i(TAG, "On-demand loaded cloned voice: $voiceName")
                    showToast("✅ Voice loaded: ${state.displayName}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load cloned voice: $voiceName", e)
                    showToast("❌ Failed to load voice", Toast.LENGTH_LONG)
                }
            }
        }
        
        // Final fallback to alba if still not found
        if (voiceState == null) {
            Log.w(TAG, "Voice not found after on-demand check: $voiceName, falling back to alba")
            voiceState = voiceStates["alba"] ?: return@withContext
        }
        
        Log.d(TAG, "Generating speech for: '${text.take(50)}...' with voice: $voiceName")
        
        try {
            // Initialize flow LM state for conditioning
            initFlowLmState()
            
            // Tokenize text
            val tokens = tok.encode(text)
            Log.d(TAG, "Tokenized to ${tokens.size} tokens")
            
            // Get text embeddings [numTokens, 1024]
            val textEmbeddings = runTextConditioner(conditioner, tokens)
            val numTextTokens = tokens.size
            
            // Voice embeddings from voice state [numFrames, 1024]
            val voiceEmbeddings = voiceState.latents
            val numVoiceFrames = voiceState.numFrames
            
            // Empty tensors for conditioning passes
            val emptySeq = FloatArray(0) // [1, 0, 32]
            val emptyText = FloatArray(0) // [1, 0, 1024]
            
            // PASS 1: Voice conditioning - empty sequence, voice embeddings
            Log.d(TAG, "Pass 1: Voice conditioning ($numVoiceFrames frames)")
            runFlowLmMainPass(session, emptySeq, 0, voiceEmbeddings, numVoiceFrames)
            
            // PASS 2: Text conditioning - empty sequence, text embeddings
            Log.d(TAG, "Pass 2: Text conditioning ($numTextTokens tokens)")
            runFlowLmMainPass(session, emptySeq, 0, textEmbeddings, numTextTokens)
            
            // PASS 3: Autoregressive generation loop
            Log.d(TAG, "Pass 3: Autoregressive generation")
            var generatedFrames = 0
            val maxFrames = 500 // Safety limit
            var eosStep: Int? = null
            
            // Use PrefsManager settings instead of hardcoded values
            val framesAfterEos = prefs.pocketFramesAfterEos
            val userLsdSteps = prefs.pocketLsdSteps.coerceIn(1, 50)
            val temperature = prefs.pocketTemperature.coerceIn(0f, 2f)
            val decodingMode = prefs.pocketDecodingMode
            val decodeChunkSize = prefs.pocketDecodeChunkSize.coerceIn(1, 50)
            
            // For streaming mode: use lower LSD steps (5) for faster generation
            // This enables real-time playback on mobile hardware
            // Batch mode uses user's setting for higher quality
            val lsdSteps = if (decodingMode == "streaming") {
                minOf(userLsdSteps, 3) // Cap at 3 for streaming - prioritize speed over quality
            } else {
                userLsdSteps
            }
            
            Log.d(TAG, "Generation params: lsdSteps=$lsdSteps (user: $userLsdSteps), temp=$temperature, framesAfterEos=$framesAfterEos, mode=$decodingMode")
            
            // Pre-compute flow buffers if not already done for this lsdSteps
            if (precomputedFlowBuffers == null || precomputedLsdSteps != lsdSteps) {
                val dt = 1.0f / lsdSteps
                precomputedFlowBuffers = (0 until lsdSteps).map { j ->
                    val s = j.toFloat() / lsdSteps
                    val t = s + dt
                    Pair(floatArrayOf(s), floatArrayOf(t))
                }
                precomputedLsdSteps = lsdSteps
                Log.d(TAG, "Pre-computed $lsdSteps flow buffers")
            }
            
            // Streaming mode parameters
            // Since generation and decoding run in parallel now, we can use a smaller initial buffer
            // 15 frames = ~1.2 seconds of audio - fast startup with parallel processing keeping up
            val FIRST_CHUNK_FRAMES = 15   // Reduced buffer for faster startup
            val MAX_CHUNK_FRAMES = 15     // Chunk size for subsequent decoding
            
            val allLatents = mutableListOf<FloatArray>()
            var currentLatent = FloatArray(LATENT_DIM) { Float.NaN }
            
            // STREAMING MODE: Use parallel generation and decoding with channels
            if (decodingMode == "streaming") {
                // Channel for passing latents from generator to decoder
                val latentChannel = Channel<FloatArray>(capacity = 50) // Buffer up to 50 frames
                
                // Initialize decoder state for streaming
                codec.initDecoderState()
                
                coroutineScope {
                    // Decoder coroutine - runs in parallel with generation
                    val decoderJob = launch(Dispatchers.Default) {
                        var decodedFrames = 0
                        val pendingLatents = mutableListOf<FloatArray>()
                        var playbackStarted = false
                        
                        for (latent in latentChannel) {
                            pendingLatents.add(latent)
                            
                            // Get adaptive thresholds based on measured device performance
                            val adaptiveThreshold = performanceTracker.optimalDecodeThreshold
                            val adaptiveReserve = performanceTracker.optimalReserve
                            
                            // Determine if we should decode
                            var chunkSize = 0
                            if (!playbackStarted) {
                                // Wait for initial buffer before starting playback
                                // Use adaptive initial buffer based on device speed
                                val adaptiveInitialBuffer = performanceTracker.optimalInitialBuffer
                                if (pendingLatents.size >= adaptiveInitialBuffer) {
                                    chunkSize = minOf(pendingLatents.size, MAX_CHUNK_FRAMES)
                                    playbackStarted = true
                                    Log.d(TAG, "[Decoder] Starting playback with ${pendingLatents.size} frames (adaptive: $adaptiveInitialBuffer)")
                                }
                            } else {
                                // After playback started, use adaptive thresholds
                                // These adjust based on measured generation speed
                                if (pendingLatents.size >= adaptiveThreshold) {
                                    chunkSize = minOf(pendingLatents.size - adaptiveReserve, MAX_CHUNK_FRAMES)
                                    if (chunkSize < 1) chunkSize = 0
                                }
                            }
                            
                            // Decode chunk if ready
                            if (chunkSize > 0) {
                                val chunkLatents = FloatArray(chunkSize * LATENT_DIM)
                                for (j in 0 until chunkSize) {
                                    System.arraycopy(pendingLatents[j], 0, chunkLatents, j * LATENT_DIM, LATENT_DIM)
                                }
                                
                                val audio = codec.decode(chunkLatents, chunkSize)
                                if (audio.isNotEmpty()) {
                                    callback(audio)
                                }
                                
                                // Remove decoded frames from pending
                                repeat(chunkSize) { pendingLatents.removeAt(0) }
                                decodedFrames += chunkSize
                            }
                        }
                        
                        // Decode any remaining frames after channel closes
                        if (pendingLatents.isNotEmpty()) {
                            Log.d(TAG, "[Decoder] Decoding final ${pendingLatents.size} frames")
                            val chunkLatents = FloatArray(pendingLatents.size * LATENT_DIM)
                            for (j in pendingLatents.indices) {
                                System.arraycopy(pendingLatents[j], 0, chunkLatents, j * LATENT_DIM, LATENT_DIM)
                            }
                            val audio = codec.decode(chunkLatents, pendingLatents.size)
                            if (audio.isNotEmpty()) {
                                callback(audio)
                            }
                        }
                        Log.d(TAG, "[Decoder] Complete, decoded $decodedFrames frames")
                    }
                    
                    // Generator - main coroutine continues generating
                    while (generatedFrames < maxFrames && isActive && !stopRequested) {
                        val frameStartTime = System.currentTimeMillis()
                        
                        // Run flow_lm_main with current latent and empty text
                        val (conditioning, eosLogit) = runFlowLmMainPass(
                            session,
                            currentLatent,
                            1,
                            emptyText,
                            0
                        )
                        val mainPassTime = System.currentTimeMillis() - frameStartTime
                        
                        // Check for EOS
                        if (eosLogit > -4.0f && eosStep == null) {
                            eosStep = generatedFrames
                            Log.d(TAG, "EOS detected at frame $generatedFrames")
                        }
                        
                        // Stop after frames_after_eos additional frames
                        val currentEosStep = eosStep
                        if (currentEosStep != null && generatedFrames >= currentEosStep + framesAfterEos) {
                            Log.d(TAG, "Stopping after EOS + $framesAfterEos frames")
                            break
                        }
                        
                        // Flow matching with Euler integration
                        val flowStartTime = System.currentTimeMillis()
                        val latent = runFlowMatching(flowSession, conditioning, lsdSteps, temperature)
                        val flowTime = System.currentTimeMillis() - flowStartTime
                        
                        // Log timing every 10 frames
                        val totalFrameTime = mainPassTime + flowTime
                        performanceTracker.recordFrameTime(totalFrameTime)
                        
                        if (generatedFrames % 10 == 0) {
                            Log.d(TAG, "Frame $generatedFrames timing: main=${mainPassTime}ms, flow=${flowTime}ms, total=${totalFrameTime}ms")
                        }
                        
                        // Send latent to decoder via channel (non-blocking with buffer)
                        latentChannel.send(latent)
                        // Note: We don't store allLatents in streaming mode - saves memory for long utterances
                        generatedFrames++
                        currentLatent = latent
                    }
                    
                    // Close channel to signal decoder to finish
                    latentChannel.close()
                    Log.d(TAG, "Generated $generatedFrames frames total, waiting for decoder...")
                    
                    // Wait for decoder to finish
                    decoderJob.join()
                }
            } else {
                // BATCH MODE: Generate all latents first, then decode
                while (generatedFrames < maxFrames && isActive && !stopRequested) {
                    val frameStartTime = System.currentTimeMillis()
                    
                    val (conditioning, eosLogit) = runFlowLmMainPass(
                        session,
                        currentLatent,
                        1,
                        emptyText,
                        0
                    )
                    val mainPassTime = System.currentTimeMillis() - frameStartTime
                    
                    if (eosLogit > -4.0f && eosStep == null) {
                        eosStep = generatedFrames
                        Log.d(TAG, "EOS detected at frame $generatedFrames")
                    }
                    
                    val currentEosStep = eosStep
                    if (currentEosStep != null && generatedFrames >= currentEosStep + framesAfterEos) {
                        Log.d(TAG, "Stopping after EOS + $framesAfterEos frames")
                        break
                    }
                    
                    val flowStartTime = System.currentTimeMillis()
                    val latent = runFlowMatching(flowSession, conditioning, lsdSteps, temperature)
                    val flowTime = System.currentTimeMillis() - flowStartTime
                    
                    if (generatedFrames % 10 == 0) {
                        Log.d(TAG, "Frame $generatedFrames timing: main=${mainPassTime}ms, flow=${flowTime}ms, total=${mainPassTime + flowTime}ms")
                    }
                    
                    allLatents.add(latent)
                    generatedFrames++
                    currentLatent = latent
                }
                
                Log.d(TAG, "Generated $generatedFrames frames total")
                
                // Decode all latents
                codec.initDecoderState()
                Log.d(TAG, "Batch decoding $generatedFrames frames...")
                
                for (i in allLatents.indices step decodeChunkSize) {
                    val endIdx = minOf(i + decodeChunkSize, allLatents.size)
                    val chunkSize = endIdx - i
                    
                    val chunkLatents = FloatArray(chunkSize * LATENT_DIM)
                    for (j in 0 until chunkSize) {
                        System.arraycopy(allLatents[i + j], 0, chunkLatents, j * LATENT_DIM, LATENT_DIM)
                    }
                    
                    val audio = codec.decode(chunkLatents, chunkSize)
                    if (audio.isNotEmpty()) {
                        callback(audio)
                    }
                }
            }
            
            Log.d(TAG, "Decoding complete")
            
        } finally {
            codec.resetDecoderState()
            resetFlowLmState()
        }
    }
    
    private fun runTextConditioner(session: OrtSession, tokens: LongArray): FloatArray {
        var tokensTensor: OnnxTensor? = null
        var outputs: OrtSession.Result? = null
        
        try {
            tokensTensor = OnnxTensor.createTensor(
                ortEnv!!,
                LongBuffer.wrap(tokens),
                longArrayOf(1, tokens.size.toLong())
            )
            
            outputs = session.run(mapOf("token_ids" to tokensTensor))
            
            // Get embeddings via floatBuffer (more robust than casting)
            val embeddingsTensor = outputs[0] as? OnnxTensor
                ?: throw IllegalStateException("Text conditioner output is not OnnxTensor")
            
            return embeddingsTensor.floatBuffer.let { buf ->
                FloatArray(buf.remaining()).also { buf.get(it) }
            }
        } finally {
            tokensTensor?.close()
            outputs?.close()
        }
    }
    
    /**
     * Run flow_lm_main for a single pass (voice conditioning, text conditioning, or generation).
     * 
     * @param session The flow_lm_main ONNX session
     * @param sequence Latent sequence [numFrames, 32], empty for conditioning passes
     * @param numSeqFrames Number of frames in sequence (0 for empty)
     * @param embeddings Text/voice embeddings [numTokens, 1024]
     * @param numEmbeddings Number of embedding tokens
     * @return Pair of (conditioning [1024], eos_logit)
     */
    private fun runFlowLmMainPass(
        session: OrtSession,
        sequence: FloatArray,
        numSeqFrames: Int,
        embeddings: FloatArray,
        numEmbeddings: Int
    ): Pair<FloatArray, Float> {
        val inputs = mutableMapOf<String, OnnxTensor>()
        var outputs: OrtSession.Result? = null
        
        try {
            // Sequence input: [1, T, 32] - latent frames
            inputs["sequence"] = OnnxTensor.createTensor(
                ortEnv!!,
                FloatBuffer.wrap(sequence),
                longArrayOf(1, numSeqFrames.toLong(), LATENT_DIM.toLong())
            )
            
            // Text embeddings: [1, T, 1024] - voice or text embeddings
            inputs["text_embeddings"] = OnnxTensor.createTensor(
                ortEnv!!,
                FloatBuffer.wrap(embeddings),
                longArrayOf(1, numEmbeddings.toLong(), EMBED_DIM.toLong())
            )
            
            // Add state inputs
            flowLmState?.forEach { (name, typeAndData) ->
                val (type, data) = typeAndData
                val stateInfo = session.inputInfo[name]?.info as? ai.onnxruntime.TensorInfo
                stateInfo?.let { info ->
                    inputs[name] = when {
                        type.contains("int64", ignoreCase = true) || type.contains("INT64") -> OnnxTensor.createTensor(
                            ortEnv!!,
                            java.nio.LongBuffer.wrap(data as LongArray),
                            info.shape
                        )
                        else -> OnnxTensor.createTensor(
                            ortEnv!!,
                            FloatBuffer.wrap(data as FloatArray),
                            info.shape
                        )
                    }
                }
            }
            
            outputs = session.run(inputs)
            
            // Get conditioning [1, 1, dim] -> [dim]
            // Note: OrtSession.Result owns the output tensors, closing Result closes them
            val conditioningResult = outputs.get("conditioning")
            val conditioningTensor = conditioningResult?.get() as? OnnxTensor
            val conditioning = conditioningTensor?.floatBuffer?.let { buf ->
                FloatArray(buf.remaining()).also { buf.get(it) }
            } ?: FloatArray(EMBED_DIM)
            
            // Get EOS logit [1, 1]
            val eosLogitResult = outputs.get("eos_logit")
            val eosLogitTensor = eosLogitResult?.get() as? OnnxTensor
            val eosLogit = eosLogitTensor?.floatBuffer?.get() ?: -10f
            
            // Update state from outputs (critical for proper generation)
            // Reference impl: for i in range(2, len(outputs)): if out_state_N -> state_N
            // FlowLM has: [0]=conditioning, [1]=eos_logit, [2+]=states
            val outputList = session.outputNames.toList()
            for (i in 2 until outputList.size) {
                val outName = outputList[i]
                if (outName.startsWith("out_state_")) {
                    val stateIdx = outName.removePrefix("out_state_").toIntOrNull() ?: continue
                    val stateName = "state_$stateIdx"
                    val typeAndData = flowLmState?.get(stateName) ?: continue
                    
                    val stateResult = outputs.get(outName)
                    val stateTensor = stateResult?.get() as? OnnxTensor ?: continue
                    
                    val newData: Any = when {
                        typeAndData.first.contains("int64", ignoreCase = true) -> {
                            val buf = stateTensor.longBuffer
                            LongArray(buf.remaining()).also { buf.get(it) }
                        }
                        else -> {
                            val buf = stateTensor.floatBuffer
                            FloatArray(buf.remaining()).also { buf.get(it) }
                        }
                    }
                    flowLmState!![stateName] = Pair(typeAndData.first, newData)
                }
            }
            
            return Pair(conditioning, eosLogit)
            
        } finally {
            // Cleanup: close all input tensors, then close outputs (which owns output tensors)
            inputs.values.forEach { tensor -> tensor.close() }
            outputs?.close()
        }
    }
    
    /**
     * Run flow matching (Euler integration) to generate a latent frame.
     * 
     * @param session The flow_lm_flow ONNX session
     * @param conditioning Conditioning vector [1024] from flow_lm_main
     * @param steps Number of Euler integration steps
     * @param temperature Sampling temperature (0 = deterministic)
     * @return Latent frame [32]
     */
    private fun runFlowMatching(
        session: OrtSession,
        conditioning: FloatArray,
        steps: Int,
        temperature: Float
    ): FloatArray {
        val dt = 1.0f / steps
        
        // Initialize x with temperature-scaled noise
        val std = kotlin.math.sqrt(temperature)
        var x = if (temperature > 0) {
            FloatArray(LATENT_DIM) { (java.util.Random().nextGaussian() * std).toFloat() }
        } else {
            FloatArray(LATENT_DIM) { 0f }
        }
        
        // Use pre-computed flow buffers if available for this step count
        val flowBuffers = if (precomputedLsdSteps == steps && precomputedFlowBuffers != null) {
            precomputedFlowBuffers!!
        } else {
            // Fallback: compute on-the-fly
            (0 until steps).map { j ->
                val s = j.toFloat() / steps
                val t = s + dt
                Pair(floatArrayOf(s), floatArrayOf(t))
            }
        }
        
        // Cache conditioning tensor once per frame (it never changes across steps)
        var cTensor: OnnxTensor? = null
        
        try {
            cTensor = OnnxTensor.createTensor(
                ortEnv!!,
                FloatBuffer.wrap(conditioning),
                longArrayOf(1, EMBED_DIM.toLong())
            )
            
            // Euler integration over flow network
            for (j in 0 until steps) {
                val (sArr, tArr) = flowBuffers[j]
                
                var sTensor: OnnxTensor? = null
                var tTensor: OnnxTensor? = null
                var xTensor: OnnxTensor? = null
                var outputs: OrtSession.Result? = null
                
                try {
                    sTensor = OnnxTensor.createTensor(ortEnv!!, FloatBuffer.wrap(sArr), longArrayOf(1, 1))
                    tTensor = OnnxTensor.createTensor(ortEnv!!, FloatBuffer.wrap(tArr), longArrayOf(1, 1))
                    xTensor = OnnxTensor.createTensor(ortEnv!!, FloatBuffer.wrap(x), longArrayOf(1, LATENT_DIM.toLong()))
                    
                    val inputs = mapOf("c" to cTensor, "s" to sTensor, "t" to tTensor, "x" to xTensor)
                    outputs = session.run(inputs)
                    
                    val flowOutput = (outputs[0] as? OnnxTensor)?.floatBuffer?.let { buf ->
                        FloatArray(buf.remaining()).also { buf.get(it) }
                    } ?: FloatArray(LATENT_DIM)
                    
                    // Euler step: x = x + flow_output * dt
                    for (i in x.indices) {
                        x[i] = x[i] + flowOutput[i] * dt
                    }
                } finally {
                    // Close per-step tensors (NOT cTensor which is reused)
                    sTensor?.close()
                    tTensor?.close()
                    xTensor?.close()
                    outputs?.close()
                }
            }
        } finally {
            // Close conditioning tensor after all steps complete
            cTensor?.close()
        }
        
        return x
    }
    
    private fun initFlowLmState() {
        val session = flowLmMain ?: return
        
        val stateMap = mutableMapOf<String, Pair<String, Any>>()
        
        session.inputInfo.forEach { (name, info) ->
            if (name.startsWith("state_")) {
                val tensorInfo = info.info as? ai.onnxruntime.TensorInfo ?: return@forEach
                val shape = tensorInfo.shape
                // Dynamic dims (-1) become 0 intentionally: initial state is empty KV cache
                // The model grows these during autoregressive generation
                val size = shape.fold(1L) { acc, dim -> acc * if (dim < 0) 0 else dim }.toInt().coerceAtLeast(0)
                val type = tensorInfo.type.toString()
                
                Log.d(TAG, "State $name: type=$type, shape=${shape.contentToString()}, size=$size")
                
                val data: Any = when {
                    type.contains("int64", ignoreCase = true) -> LongArray(size) { 0L }
                    type.contains("bool", ignoreCase = true) -> LongArray(size) { 0L }
                    else -> FloatArray(size) { 0f }
                }
                
                stateMap[name] = Pair(type, data)
            }
        }
        
        Log.d(TAG, "Initialized ${stateMap.size} flow LM states")
        flowLmState = stateMap
    }
    
    private fun updateFlowLmState(latent: FloatArray) {
        // State is updated in runFlowLmMain
    }
    
    private fun resetFlowLmState() {
        flowLmState = null
    }
    
    /**
     * Clone a voice from an audio file.
     * 
     * @param audioPath Path to the audio file (WAV, 24kHz recommended)
     * @param voiceName Display name for the cloned voice
     * @return ID of the cloned voice
     */
    override suspend fun cloneVoice(audioPath: String, voiceName: String): String? = withContext(Dispatchers.IO) {
        val encoder = mimiEncoder ?: run {
            Log.e(TAG, "Cannot clone voice - mimi_encoder not initialized")
            return@withContext null
        }
        
        Log.i(TAG, "Cloning voice from: $audioPath")
        
        // Load audio file
        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            Log.e(TAG, "Audio file not found: $audioPath")
            return@withContext null
        }
        
        // Use the same encoding method as bundled/celebrity voices
        // This uses mimi_encoder.onnx which produces 1024-dim embeddings
        val embeddings = encodeVoiceFromWav(audioFile, encoder)
        if (embeddings == null) {
            Log.e(TAG, "Failed to encode voice from: $audioPath")
            return@withContext null
        }
        
        val (latents, numFrames) = embeddings
        
        // Create voice state with consistent format
        val voiceId = "cloned_${System.currentTimeMillis()}"
        val voiceState = PocketVoiceState(
            id = voiceId,
            displayName = voiceName,
            latents = latents,
            numFrames = numFrames,
            isBundled = false
        )
        
        // Save to disk for persistence
        val voicesDir = File(context.filesDir, CLONED_VOICES_DIR)
        voicesDir.mkdirs()
        File(voicesDir, "$voiceId.bin").writeBytes(voiceState.toBytes())
        
        // Also cache the embeddings like we do for bundled voices (use same path)
        val embCacheDir = File(context.filesDir, VOICE_CACHE_DIR)
        embCacheDir.mkdirs()
        saveCachedEmbedding(File(embCacheDir, "$voiceId.emb"), latents, numFrames)
        
        // Add to runtime cache
        voiceStates[voiceId] = voiceState
        
        Log.i(TAG, "Voice cloned successfully: $voiceName (ID: $voiceId, $numFrames frames)")
        
        voiceId
    }
    
    private fun loadAudioFromWav(file: File): FloatArray {
        // Simple WAV parser for 16-bit PCM mono
        val bytes = file.readBytes()
        
        // Skip WAV header (44 bytes for standard WAV)
        val dataStart = 44
        val numSamples = (bytes.size - dataStart) / 2
        
        val audio = FloatArray(numSamples)
        val buffer = java.nio.ByteBuffer.wrap(bytes, dataStart, bytes.size - dataStart)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        
        for (i in 0 until numSamples) {
            val sample = buffer.short
            audio[i] = sample.toFloat() / 32768f
        }
        
        return audio
    }
    
    /**
     * Delete a cloned voice.
     */
    override fun deleteClonedVoice(voiceId: String): Boolean {
        val voiceState = voiceStates[voiceId] ?: return false
        if (voiceState.isBundled) {
            Log.w(TAG, "Cannot delete bundled voice: $voiceId")
            return false
        }
        
        val voiceFile = File(context.filesDir, "$CLONED_VOICES_DIR/$voiceId.bin")
        if (voiceFile.exists()) {
            voiceFile.delete()
        }
        
        voiceStates.remove(voiceId)
        Log.i(TAG, "Deleted cloned voice: $voiceId")
        return true
    }
    
    override fun getSampleRate(): Int = SAMPLE_RATE
    
    override fun getVoices(): List<String> = voiceStates.keys.toList()
    
    fun getVoiceStates(): Map<String, PocketVoiceState> = voiceStates.toMap()
    
    /**
     * Request the engine to stop current generation ASAP.
     * Thread-safe - can be called from any thread.
     */
    override fun stop() {
        stopRequested = true
        Log.d(TAG, "Stop requested")
    }
    
    override fun release() {
        isReleased = true
        mimiEncoder?.close()
        textConditioner?.close()
        flowLmMain?.close()
        flowLmFlow?.close()
        mimiDecoder?.close()
        ortEnv?.close()
        
        mimiCodec?.release()
        gtcrnDenoiser?.close()
        
        mimiEncoder = null
        textConditioner = null
        flowLmMain = null
        flowLmFlow = null
        mimiDecoder = null
        ortEnv = null
        mimiCodec = null
        
        initialized = false
        Log.i(TAG, "Pocket-TTS Engine released")
    }
    
    override fun isInitialized(): Boolean = initialized
    
    // Voice cloning support
    override fun supportsVoiceCloning(): Boolean = true
}
