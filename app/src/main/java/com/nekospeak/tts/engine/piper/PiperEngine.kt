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

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing PiperEngine for voice: $voiceId")
            
            val repo = com.nekospeak.tts.data.VoiceRepository(context)
            val files = repo.getLocalPath(voiceId)
            
            if (files == null) {
                Log.e(TAG, "Voice files not found for $voiceId")
                return@withContext false
            }
            
            val (modelFile, jsonFile) = files
            
            // 1. Load Config
            // If json file exists, load it. If bundled, we might need to verify if getLocalPath returns the extracted path or asset path logic?
            // VoiceRepository.getLocalPath for bundled returns filesDir path.
            // We must ensure they are extracted if they don't exist, similar to before.
            // BUT VoiceRepository doesn't extract.
            
            // Extraction Logic for Bundled Voice (Amy)
            if (voiceId == "en_US-amy-low") {
                 // Hardcoded check for bundled extraction to ensure safety
                 if (!modelFile.exists()) {
                     Log.i(TAG, "Extracting bundled model $voiceId...")
                     context.assets.open("piper/$voiceId.onnx").use { input ->
                         modelFile.outputStream().use { output -> input.copyTo(output) }
                     }
                     context.assets.open("piper/$voiceId.onnx.json").use { input ->
                         jsonFile.outputStream().use { output -> input.copyTo(output) }
                     }
                 }
            } else {
                 if (!modelFile.exists() || !jsonFile.exists()) {
                     Log.e(TAG, "Model files missing for $voiceId at ${modelFile.absolutePath}")
                     return@withContext false
                 }
            }
            
            val jsonString = jsonFile.readText()
            config = Gson().fromJson(jsonString, PiperConfig::class.java)
            
            // 2. Init ONNX
            ortEnv = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                 setIntraOpNumThreads(4)
            }
            ortSession = ortEnv?.createSession(modelFile.absolutePath, opts)
            
            // 3. Init Espeak
             val dataDir = java.io.File(context.filesDir, "espeak-ng-data")
            if (!dataDir.exists()) {
                 com.nekospeak.tts.utils.AssetUtils.extractAssets(context, "espeak-ng-data", context.filesDir)
            }
            espeak = EspeakWrapper()
            val res = espeak?.initialize(context.filesDir.absolutePath)
            if (res == -1) {
                Log.e(TAG, "Espeak init failed")
                return@withContext false
            }
            
            // 4. Init Misaki G2P (for English voices only)
            val espeakVoice = config?.espeak?.voice ?: "en-us"
            if (espeakVoice.startsWith("en")) {
                try {
                    val isBritish = espeakVoice.contains("gb")
                    misakiLexicon = Lexicon(context, isBritish)
                    misakiLexicon?.load()
                    
                    // Create G2P with eSpeak fallback
                    misakiG2P = G2P(misakiLexicon!!) { word ->
                        // Fallback: use eSpeak for unknown words
                        val espeakPhonemes = espeak?.textToPhonemes(word, espeakVoice)
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
            rawPhonemes = espeak?.textToPhonemes(text, conf.espeak.voice) ?: ""
            Log.d(TAG, "eSpeak Phonemes: $rawPhonemes")
        }
        
        // Debug Log
        Log.d(TAG, "Raw Phonemes: $rawPhonemes")
        
        // 2. Tokenize (Map to IDs + Padding)
        val idMap = conf.phonemeIdMap
        val tokenIds = mutableListOf<Long>()
        
        // BOS
        idMap[BOS]?.forEach { tokenIds.add(it.toLong()) }
        idMap[PAD]?.forEach { tokenIds.add(it.toLong()) }
        
        for (char in rawPhonemes) {
            val charStr = char.toString()
            if (idMap.containsKey(charStr)) {
                idMap[charStr]?.forEach { tokenIds.add(it.toLong()) }
                idMap[PAD]?.forEach { tokenIds.add(it.toLong()) }
            } else {
                 // Try ignoring unknown? Or map to space?
                 // Log.w(TAG, "Unknown char: $char")
            }
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
        val finalLengthScale = baseLengthScale / speed
        
        Log.i(TAG, "Generating with speed=$speed, baseScale=$baseLengthScale, finalScale=$finalLengthScale. Token Count: ${tokenIds.size}")
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
            val outputs = session.run(inputs)
            val audioTensor = outputs[0] as OnnxTensor // Output is usually just 'output'
            val floatBuf = audioTensor.floatBuffer
            val audio = FloatArray(floatBuf.remaining())
            floatBuf.get(audio)
            
            callback(audio)
            
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
    override fun getVoices(): List<String> = listOf("en_US-amy-medium") // Hardcoded for now
    override fun isInitialized(): Boolean = initialized
    
    override fun release() {
        ortSession?.close()
        ortEnv?.close()
        initialized = false
    }
}
