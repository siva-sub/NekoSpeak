package com.nekospeak.tts.engine

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.zip.ZipInputStream

class KokoroEngine(private val context: Context) : TtsEngine {
    
    companion object {
        private const val TAG = "KokoroEngine"
        const val SAMPLE_RATE = 24000
        const val MAX_TOKENS = 250 // Reduced to prevent long pauses
        const val STYLE_DIM = 256
        
        // Kokoro Assets
        private const val MODEL_KOKORO_ASSET = "kokoro/kokoro-v1.0.int8.onnx"
        private const val MODEL_KOKORO_FILE = "kokoro_model.onnx"
        private const val VOICES_KOKORO_ASSET = "kokoro/voices-v1.0.bin"
        
        // Kitten Assets
        private const val MODEL_KITTEN_ASSET = "kitten/kitten_tts_nano_v0_1.onnx"
        private const val MODEL_KITTEN_FILE = "kitten_model.onnx"
        private const val VOICES_KITTEN_ASSET = "kitten/voices.npz"
    }
    
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var voiceCache = mutableMapOf<String, FloatArray>()
    private var initialized = false
    private var currentVoice = "af_heart"
    private var currentModelInfo = Triple(MODEL_KOKORO_ASSET, MODEL_KOKORO_FILE, VOICES_KOKORO_ASSET)
    
    private var inputIdsName = "input_ids"
    private var styleName = "style"
    private var speedName = "speed"
    private var useIntSpeed = false
    
    private var availableVoices = mutableListOf<String>()
    
    private var phonemizer: Phonemizer? = null
    
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = com.nekospeak.tts.data.PrefsManager(context)
            val modelName = prefs.currentModel
            val threads = prefs.cpuThreads
            
            Log.i(TAG, "Initializing Engine. Model: $modelName, Threads: $threads")
            
            // Select assets
            currentModelInfo = if (modelName == "kitten_nano") {
                Triple(MODEL_KITTEN_ASSET, MODEL_KITTEN_FILE, VOICES_KITTEN_ASSET)
            } else {
                Triple(MODEL_KOKORO_ASSET, MODEL_KOKORO_FILE, VOICES_KOKORO_ASSET)
            }
            
            val (modelAsset, modelFileName, voicesAsset) = currentModelInfo
            
            phonemizer = Phonemizer(context)
            phonemizer?.load()
            ortEnv = OrtEnvironment.getEnvironment()
            
            val modelFile = File(context.filesDir, modelFileName)
            if (!modelFile.exists() || modelFile.length() < 10 * 1024 * 1024) { // check > 10MB
                Log.i(TAG, "Extracting model $modelFileName...")
                context.assets.open(modelAsset).use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
            }
            
            // Scan voices
            availableVoices.clear()
            context.assets.open(voicesAsset).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".npy")) {
                            availableVoices.add(entry.name.removeSuffix(".npy"))
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            availableVoices.sort()
            Log.i(TAG, "Found ${availableVoices.size} voices in $voicesAsset")
            
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(threads)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            
            ortSession = ortEnv?.createSession(modelFile.absolutePath, options)
            
            // Introspect inputs
            val inputNames = ortSession?.inputNames ?: emptySet()
            Log.i(TAG, "Model Inputs: $inputNames")
            
            if ("tokens" in inputNames) inputIdsName = "tokens"
            if ("input_ids" in inputNames) inputIdsName = "input_ids"
            
            val speedInfo = ortSession?.inputInfo?.get("speed")?.info
            if (speedInfo is TensorInfo) {
                if (speedInfo.type == OnnxJavaType.INT32 || speedInfo.type == OnnxJavaType.INT64) {
                    useIntSpeed = true
                }
            }
            
            // Reset voice selection if needed
            currentVoice = prefs.currentVoice
            if (currentVoice !in availableVoices && availableVoices.isNotEmpty()) {
                currentVoice = availableVoices[0]
                prefs.currentVoice = currentVoice // Update pref
            }
            
            Log.i(TAG, "Loading saved voice: $currentVoice")
            loadVoice(currentVoice)
            
            initialized = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            e.printStackTrace()
            false
        }
    }
    
    private fun loadVoice(name: String): FloatArray {
        voiceCache[name]?.let { return it }
        val voicesAsset = currentModelInfo.third
        
        context.assets.open(voicesAsset).use { fis ->
            ZipInputStream(fis).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "$name.npy") {
                        val bytes = zis.readBytes()
                        
                        // Parse NPY header safely
                        // Magic: 0x93 NUMPY (6 bytes)
                        // Major (1), Minor (1)
                        // Header Len (2 bytes, little endian)
                        if (bytes.size < 10 || bytes[0] != 0x93.toByte() || String(bytes, 1, 5) != "NUMPY") {
                             // Fallback for Kokoro .bin files which might just be raw data with skip?
                             // But Kokoro voices-v1.0.bin ARE .npy files too. 
                             // Wait, existing code skipped 128 bytes. Let's try to be smart.
                             if (bytes.size > 128) {
                                 // Assume fixed 128 header for now if magic check fails? 
                                 // Actually Kokoro voices are standard NPY too.
                             }
                        }
                        
                        var headerLen = 0
                        var offset = 0
                        
                        if (bytes[0] == 0x93.toByte() && String(bytes, 1, 5) == "NUMPY") {
                            val headerLenShort = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
                            headerLen = headerLenShort
                            offset = 10 + headerLen
                            
                            // Align to 64 bytes? Some NPY writers pad.
                            // But usually NPY data starts immediately after header.
                            // However, Kokoro code skipped 128 bytes explicitly. 
                            // 10 + headerLen is likely < 128 for these small files.
                            // Let's stick to reading the proper header length.
                            if (headerLen % 64 == 0) {
                                // 64-byte alignment padding might be inside the header string itself
                            }
                        } else {
                            // Fallback to old behavior
                            offset = 128
                        }
                        
                        // Override for Kokoro: It seems they were padded to 128?
                        // Let's rely on standard NPY parsing if valid, else 128.
                        // Actually, let's just use the robust NPY read if magic present.
                        
                        if (offset >= bytes.size) throw IllegalStateException("Bad NPY file")
                        
                        val dataBytes = bytes.copyOfRange(offset, bytes.size)
                        val buffer = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val floats = FloatArray(dataBytes.size / 4) { buffer.float }
                        
                        voiceCache[name] = floats
                        return floats
                    }
                    entry = zis.nextEntry
                }
            }
        }
        throw IllegalArgumentException("Voice $name not found")
    }
    
    override suspend fun generate(
        text: String,
        speed: Float,
        voice: String?,
        callback: (FloatArray) -> Unit
    ) = withContext(Dispatchers.Default) {
        val session = ortSession ?: throw IllegalStateException("Not initialized")
        val env = ortEnv ?: throw IllegalStateException("Not initialized")
        val phonemizer = phonemizer ?: throw IllegalStateException("Phonemizer not initialized")
        
        try {
            // 1. Initial rough split by sentence terminators
            val rawSentences = text.split(Regex("(?<=[.!?])\\s+|\\n+|(?<=[;])\\s+"))
            
            // 2. Intelligence check: Is the first sentence too long? (>75 chars triggers latency)
            val sentences = if (rawSentences.isNotEmpty() && rawSentences[0].length > 75) {
                val first = rawSentences[0]
                val rest = rawSentences.drop(1)
                
                // Try splitting by comma
                val subParts = first.split(Regex("(?<=[,])\\s+"))
                if (subParts.size > 1) {
                    subParts + rest
                } else {
                    // Hard split by words approx every 60 chars
                    listOf(first.take(60), first.drop(60)) + rest
                }
            } else {
                rawSentences
            }
            
            Log.d(TAG, "Input split into ${sentences.size} chunks. First chunk len: ${sentences.getOrNull(0)?.length ?: 0}")
            
            val voiceName = voice ?: currentVoice
            // Ensure voice data is loaded if we switched
            val voiceData = if (voiceCache.containsKey(voiceName)) {
                voiceCache[voiceName]!!
            } else {
                 loadVoice(voiceName)
            }
            val numVectors = voiceData.size / STYLE_DIM
            
            // Speed Logic
            val isKitten = currentModelInfo.first.contains("kitten")
            val prefs = com.nekospeak.tts.data.PrefsManager(context)
            
            // If Kitten, use Prefs speed (ignoring system request 'speed' param if user preference exists? 
            // Or combine them? User prompt implies UI control for Kitten.
            // Let's use the UI setting preferentially for Kitten.
            // For Kokoro, force 1.0.
            val finalSpeed = if (isKitten) prefs.speechSpeed else 1.0f
            
            Log.d(TAG, "Generating with Speed: $finalSpeed (Model: ${if(isKitten) "Kitten" else "Kokoro"})")
            
            // Batching logic: Accumulate tokens to fill context window (MAX_TOKENS)
            val currentBatchTokens = ArrayList<Int>()
            var isFirstBatch = true
            
            for (sentence in sentences) {
                if (sentence.isBlank()) continue
                
                // G2P + Tokenization
                val startG2p = System.currentTimeMillis()
                val phonemes = phonemizer.phonemize(sentence, "en-us")
                val tokens = phonemizer.tokenize(phonemes)
                Log.d(TAG, "G2P time: ${System.currentTimeMillis() - startG2p}ms for ${tokens.size} tokens")
                
                if (tokens.isEmpty()) continue
                
                // PERFORMANCE FIX: Immediate flush for first sentence
                if (isFirstBatch) {
                    val startInf = System.currentTimeMillis()
                    processBatch(tokens, env, session, voiceData, numVectors, finalSpeed, useIntSpeed, startInf, callback)
                    Log.d(TAG, "First batch inference: ${System.currentTimeMillis() - startInf}ms")
                    isFirstBatch = false
                    continue
                }
                
                // If adding these tokens exceeds limit, process current batch first
                if (currentBatchTokens.size + tokens.size > MAX_TOKENS - 2) { // -2 for start/end tokens
                    // Process accumulated batch
                    val startInf = System.currentTimeMillis()
                    processBatch(currentBatchTokens, env, session, voiceData, numVectors, finalSpeed, useIntSpeed, startInf, callback)
                    Log.d(TAG, "Batch inference: ${System.currentTimeMillis() - startInf}ms")
                    currentBatchTokens.clear()
                }
                
                // If valid single sentence is HUGE (larger than max), strictly chunk it
                if (tokens.size > MAX_TOKENS - 2) {
                     val chunks = tokens.chunked(MAX_TOKENS - 2)
                     for (chunk in chunks) {
                         // Double check cancellation
                         if (!isActive) break
                         val startInf = System.currentTimeMillis()
                         processBatch(chunk, env, session, voiceData, numVectors, finalSpeed, useIntSpeed, startInf, callback)
                     }
                } else {
                    // Normal case: append to buffer
                    currentBatchTokens.addAll(tokens)
                }
                
                // Check for cancellation between sentences
                if (!isActive) break
            }
            
            // Process remaining
            if (currentBatchTokens.isNotEmpty() && isActive) {
                val startInf = System.currentTimeMillis()
                processBatch(currentBatchTokens, env, session, voiceData, numVectors, finalSpeed, useIntSpeed, startInf, callback)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            throw e
        }
    }
    
    override fun getSampleRate(): Int = SAMPLE_RATE
    override fun getVoices(): List<String> = availableVoices
    override fun release() {
        ortSession?.close()
        ortEnv?.close()
        ortSession = null
        initialized = false
    }
    override fun isInitialized(): Boolean = initialized

    private fun processBatch(
        tokens: List<Int>,
        env: OrtEnvironment,
        session: OrtSession,
        voiceData: FloatArray,
        numVectors: Int,
        speed: Float,
        useIntSpeed: Boolean,
        startTime: Long,
        callback: (FloatArray) -> Unit
    ) {
        // Enforce Speed Logic
        // PrefsManager instance needed to read user preference for Kitten? 
        // passing 'speed' argument comes from generate() which calls this.
        // So logic should be in generate(). But processBatch receives 'speed'.
        // Let's assume 'speed' passed here is already the correct one.
        
        val paddedTokens = LongArray(tokens.size + 2)
        paddedTokens[0] = 0
        tokens.forEachIndexed { i, t -> paddedTokens[i + 1] = t.toLong() }
        paddedTokens[paddedTokens.size - 1] = 0
        
        // Match style to chunk length
        val styleIdx = tokens.size.coerceIn(0, numVectors - 1)
        val offset = styleIdx * STYLE_DIM
        val style = voiceData.sliceArray(offset until offset + STYLE_DIM)
        
        val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(paddedTokens), longArrayOf(1, paddedTokens.size.toLong()))
        val styleTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(style), longArrayOf(1, STYLE_DIM.toLong()))
        
        val speedTensor = if (useIntSpeed) {
             // Round speed if int required, map to nearest int >= 1
             val intSpeed = (speed + 0.5f).toInt().coerceAtLeast(1)
             OnnxTensor.createTensor(env, IntBuffer.wrap(intArrayOf(intSpeed)), longArrayOf(1))
        } else {
            OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(speed)), longArrayOf(1))
        }
        
        val inputs = mapOf(
            inputIdsName to inputIdsTensor,
            styleName to styleTensor,
            speedName to speedTensor
        )
        
        val outputs = session.run(inputs)
        val audioTensor = outputs[0] as OnnxTensor
        val floatBuffer = audioTensor.floatBuffer
        val audioData = FloatArray(floatBuffer.remaining())
        floatBuffer.get(audioData)
        
        val genTimeMs = System.currentTimeMillis() - startTime
        
        // Model Specific Trimming (Robustness for Kitten Nano)
        val finalAudio = if (currentModelInfo.first.contains("kitten")) {
            // Trim 5000 from start, 10000 from end if size allows
            if (audioData.size > 15000) {
                audioData.sliceArray(5000 until (audioData.size - 10000))
            } else {
                audioData // Too small to trim safely
            }
        } else {
            audioData
        }
        
        val audioDurationSec = finalAudio.size.toFloat() / SAMPLE_RATE
        val rtf = genTimeMs / (audioDurationSec * 1000f)
        
        Log.d(TAG, "Batch Processed: ${tokens.size} tokens -> ${audioDurationSec}s (Trimmed from ${audioData.size}) in ${genTimeMs}ms")
        
        // STREAMING: Callback
        callback(finalAudio)
        
        inputIdsTensor.close()
        styleTensor.close()
        speedTensor.close()
        outputs.close()
    }
}
