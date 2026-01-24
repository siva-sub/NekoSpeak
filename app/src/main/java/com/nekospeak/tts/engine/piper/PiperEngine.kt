package com.nekospeak.tts.engine.piper

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.nekospeak.tts.engine.EspeakWrapper
import com.nekospeak.tts.engine.TtsEngine
import com.nekospeak.tts.engine.misaki.G2P
import com.nekospeak.tts.engine.misaki.Lexicon
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

class PiperEngine(
    private val context: Context,
    private val voiceId: String
) : TtsEngine {

    companion object {
        private const val TAG = "PiperEngine"
        private const val PAD = "_"
        private const val BOS = "^"
        private const val EOS = "$"
    }

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var config: PiperConfig? = null
    private var espeak: EspeakWrapper? = null
    private var misakiG2P: G2P? = null
    private var misakiLexicon: Lexicon? = null
    private var initialized = false
    
    @Volatile private var stopFlag = false

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "PiperEngine: initializing voice $voiceId")
            
            val repo = com.nekospeak.tts.data.VoiceRepository(context)
            val files = repo.getLocalPath(voiceId)
            
            if (files == null) {
                Log.e(TAG, "Voice files not found for $voiceId")
                return@withContext false
            }
            
            val (modelFile, jsonFile) = files
            Log.v(TAG, "STEP 1: Files resolved. Model=${modelFile.absolutePath}, JSON=${jsonFile.absolutePath}")
            
            // Extraction Logic for Bundled Voice (Amy)
            if (voiceId == "en_US-amy-low") {
                 if (!modelFile.exists()) {
                     Log.i(TAG, "Extracting bundled model $voiceId... (Asset Open)")
                     try {
                         context.assets.open("piper/$voiceId.onnx").use { input ->
                             modelFile.outputStream().use { output -> input.copyTo(output) }
                         }
                         Log.v(TAG, "Model extracted.")
                         context.assets.open("piper/$voiceId.onnx.json").use { input ->
                             jsonFile.outputStream().use { output -> input.copyTo(output) }
                         }
                         Log.v(TAG, "JSON extracted.")
                     } catch (e: Exception) {
                         Log.e(TAG, "Asset extraction failed!", e)
                         return@withContext false
                     }
                 }
            } else {
                 if (!modelFile.exists() || !jsonFile.exists()) {
                     Log.e(TAG, "Model files missing for $voiceId at ${modelFile.absolutePath}")
                     return@withContext false
                 }
            }
            Log.v(TAG, "STEP 2: Assets verified.")
            
            val jsonString = jsonFile.readText()
            Log.v(TAG, "STEP 3: JSON read (${jsonString.length} chars). Parsing Gson...")
            config = Gson().fromJson(jsonString, PiperConfig::class.java)
            if (config == null) {
                 Log.e(TAG, "Gson returned null config!")
                 return@withContext false
            }
            Log.v(TAG, "STEP 4: Config loaded. SampleRate=${config?.audio?.sampleRate}")
            
            // 2. Init ONNX
            Log.v(TAG, "STEP 5: Initializing ONNX Environment...")
            try {
                ortEnv = OrtEnvironment.getEnvironment()
                Log.v(TAG, "ONNX Environment created. Creating SessionOptions...")
                val opts = OrtSession.SessionOptions().apply {
                     setIntraOpNumThreads(4)
                }
                Log.v(TAG, "Creating ONNX Session from ${modelFile.absolutePath}...")
                ortSession = ortEnv?.createSession(modelFile.absolutePath, opts)
                Log.v(TAG, "ONNX Session created.")
            } catch (e: Throwable) {
                Log.e(TAG, "CRITICAL: ONNX Init Failed!", e)
                throw e
            }
            Log.v(TAG, "STEP 6: ONNX Ready.")
            
            // 3. Init Espeak
            Log.v(TAG, "STEP 7: Checking Espeak Data...")
             val dataDir = java.io.File(context.filesDir, "espeak-ng-data")
            if (!dataDir.exists()) {
                 Log.v(TAG, "Extracting espeak-ng-data...")
                 com.nekospeak.tts.utils.AssetUtils.extractAssets(context, "espeak-ng-data", context.filesDir)
            }
            Log.v(TAG, "Initializing EspeakWrapper JNI...")
            espeak = EspeakWrapper()
            val res = espeak?.initializeSafe(context.filesDir.absolutePath)
            if (res == -1) {
                Log.e(TAG, "Espeak init failed (JNI returned -1)")
                return@withContext false
            }
            Log.v(TAG, "STEP 8: Espeak Ready.")
            
            // 4. Init Misaki G2P (for English voices only)
            val espeakVoice = config?.espeak?.voice ?: "en-us"
            Log.v(TAG, "STEP 9: Init Misaki (Voice=$espeakVoice)...")
            if (espeakVoice.startsWith("en")) {
                try {
                    val isBritish = espeakVoice.contains("gb")
                    misakiLexicon = Lexicon(context, isBritish)
                    misakiLexicon?.load()
                    
                    // Create G2P with eSpeak fallback
                    misakiG2P = G2P(misakiLexicon!!) { word ->
                        // Fallback: use eSpeak for unknown words
                        val espeakPhonemes = espeak?.textToPhonemesSafe(word, espeakVoice)
                        espeakPhonemes
                    }
                    Log.i(TAG, "Misaki G2P initialized (british=$isBritish)")
                } catch (e: Exception) {
                    Log.w(TAG, "Misaki init failed, using eSpeak only", e)
                    misakiG2P = null
                }
            }

            initialized = true
            Log.i(TAG, "PiperEngine initialized successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PiperEngine", e)
            false
        }
    }

    override suspend fun generate(
        text: String,
        speed: Float, // Used as length_scale inverse? 
        voice: String?,
        callback: (FloatArray) -> Unit
    ) {
        withContext(Dispatchers.Default) {
        // Reset stop flag at start of new generation
        stopFlag = false
        
        if (!initialized) throw IllegalStateException("Not initialized")
        val session = ortSession!!
        val env = ortEnv!!
        val conf = config!!
        
        // 1. Phonemize
        // Try Misaki G2P first (for English), then convert to IPA for Piper
        // Fall back to eSpeak if Misaki not available or non-English
        val rawPhonemes: String
        if (misakiG2P != null) {
            val misakiResult = misakiG2P!!.phonemize(text)
            // Convert Misaki phonemes to Piper IPA
            rawPhonemes = MisakiToPiperIPA.convert(misakiResult)
            Log.d(TAG, "Misaki Phonemes: $misakiResult")
            Log.d(TAG, "Converted to IPA: $rawPhonemes")
        } else {
            // Fallback to pure eSpeak
            rawPhonemes = espeak?.textToPhonemesSafe(text, conf.espeak.voice) ?: ""
            Log.d(TAG, "eSpeak Phonemes: $rawPhonemes")
        }
        
        // Debug Log
        Log.d(TAG, "Raw Phonemes: $rawPhonemes")
        
        // 2. Tokenize using greedy longest-match (NOT char-by-char)
        // This properly handles multi-character phonemes like "eɪ", "dʒ", "tʃ"
        val idMap = conf.phonemeIdMap
        val tokenIds = mutableListOf<Long>()
        
        // Compute max key length for efficiency
        val maxKeyLen = idMap.keys.maxOfOrNull { it.length } ?: 1
        val keySet = idMap.keys
        
        // BOS
        idMap[BOS]?.forEach { tokenIds.add(it.toLong()) }
        idMap[PAD]?.forEach { tokenIds.add(it.toLong()) }
        
        // Greedy longest-match tokenization
        var i = 0
        var droppedCount = 0
        while (i < rawPhonemes.length) {
            var matched = false
            // Try lengths from maxKeyLen down to 1
            for (len in minOf(maxKeyLen, rawPhonemes.length - i) downTo 1) {
                val substr = rawPhonemes.substring(i, i + len)
                if (keySet.contains(substr)) {
                    idMap[substr]?.forEach { tokenIds.add(it.toLong()) }
                    idMap[PAD]?.forEach { tokenIds.add(it.toLong()) }
                    i += len
                    matched = true
                    break  // Exit the for loop on match
                }
            }
            if (!matched) {
                // Skip unknown symbol
                droppedCount++
                i++
            }
        }
        
        if (droppedCount > 0) {
            Log.w(TAG, "Dropped $droppedCount unknown phoneme symbols during tokenization")
        }
        
        idMap[EOS]?.forEach { tokenIds.add(it.toLong()) }
        
        if (tokenIds.size <= 2) {
            Log.e(TAG, "No valid phonemes generated for text: '$text'. Voice ($voiceId) likely does not support this language/script.")
            return@withContext
        }
        
        // 3. Tensors
        val inputIds = tokenIds.toLongArray()
        val inputLengths = longArrayOf(inputIds.size.toLong())
        // Scales: [noise, length, noise_w]
        // length_scale: Lower = Faster. So we take config default / speed? Or just passed speed?
        // Let's assume passed 'speed' modifier multiplies the config default.
        // If config is 1.0 and we want 2x speed, length_scale should be 0.5.
        
        val baseLengthScale = conf.inference.lengthScale
        // Clamp speed to prevent divide by zero or extreme values
        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        val finalLengthScale = baseLengthScale / safeSpeed
        
        Log.i(TAG, "Generating with speed=$safeSpeed, baseScale=$baseLengthScale, finalScale=$finalLengthScale. Token Count: ${tokenIds.size}")
        Log.d(TAG, "Token IDs: $tokenIds")

        val scales = floatArrayOf(
            conf.inference.noiseScale,
            finalLengthScale,
            conf.inference.noiseW
        )
        
        val inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(1, inputIds.size.toLong()))
        val lengthTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputLengths), longArrayOf(1))
        val scalesTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), longArrayOf(3))
        
        val inputs = mapOf(
            "input" to inputTensor,
            "input_lengths" to lengthTensor,
            "scales" to scalesTensor
        )
        // Add sid if multi-speaker (not for Amy)
        
        // 4. Run
        try {
            // Check for stop request before expensive inference
            if (stopFlag) {
                inputTensor.close()
                lengthTensor.close()
                scalesTensor.close()
                return@withContext
            }
            
            val outputs = session.run(inputs)
            val audioTensor = outputs[0] as OnnxTensor
            val floatBuf = audioTensor.floatBuffer
            val audio = FloatArray(floatBuf.remaining())
            floatBuf.get(audio)
            
            // Check for stop request before callback
            if (!stopFlag) {
                callback(audio)
            }
            
            audioTensor.close()  // Close extracted tensor
            outputs.close()
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
        } finally {
            inputTensor.close()
            lengthTensor.close()
            scalesTensor.close()
        }
    }
    }

    override fun getSampleRate(): Int = config?.audio?.sampleRate ?: 22050
    override fun getVoices(): List<String> = listOf(voiceId)  // Return actual voice ID
    override fun isInitialized(): Boolean = initialized
    
    override fun stop() {
        stopFlag = true
        Log.d(TAG, "Stop requested")
    }
    
    override fun release() {
        ortSession?.close()
        ortEnv?.close()
        initialized = false
    }
}
