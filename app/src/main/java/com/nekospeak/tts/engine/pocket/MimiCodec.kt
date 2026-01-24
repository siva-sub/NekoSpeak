package com.nekospeak.tts.engine.pocket

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Wrapper for the Mimi audio codec (encoder/decoder).
 * 
 * - Encoder: Converts audio waveform to speaker latents for voice cloning
 * - Decoder: Converts generated latents back to audio waveform
 */
class MimiCodec(
    private val encoder: OrtSession,
    private val decoder: OrtSession,
    private val env: OrtEnvironment
) {
    companion object {
        private const val TAG = "MimiCodec"
        const val SAMPLE_RATE = 24000
        const val LATENT_DIM = 32
    }
    
    // Decoder state tensors (for streaming) - map of state name to (type, data)
    private var decoderState: MutableMap<String, Pair<String, Any>>? = null
    
    /**
     * Encode audio waveform to speaker latents for voice cloning.
     * 
     * @param audio FloatArray of audio samples at 24kHz mono
     * @return Pair of (latents FloatArray, numFrames Int)
     */
    fun encode(audio: FloatArray): Pair<FloatArray, Int> {
        Log.d(TAG, "Encoding ${audio.size} audio samples...")
        
        // Input shape: [1, 1, audio_len]
        val audioTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(audio),
            longArrayOf(1, 1, audio.size.toLong())
        )
        
        val outputs = encoder.run(mapOf("audio" to audioTensor))
        val latentsTensor = outputs[0] as OnnxTensor
        
        // Output shape: [1, frames, 32]
        val shape = latentsTensor.info.shape
        val numFrames = shape[1].toInt()
        
        val result = latentsTensor.floatBuffer.let { buf ->
            FloatArray(buf.remaining()).also { buf.get(it) }
        }
        
        Log.d(TAG, "Encoded to $numFrames frames (${result.size} floats)")
        
        audioTensor.close()
        outputs.close()
        
        return Pair(result, numFrames)
    }
    
    /**
     * Initialize decoder state for streaming generation.
     * Must be called before first decode() call.
     */
    fun initDecoderState() {
        val stateMap = mutableMapOf<String, Pair<String, Any>>()
        
        decoder.inputInfo.forEach { (name, info) ->
            if (name.startsWith("state_")) {
                val tensorInfo = info.info as? ai.onnxruntime.TensorInfo ?: return@forEach
                val shape = tensorInfo.shape
                val size = shape.fold(1L) { acc, dim -> acc * if (dim < 0) 0 else dim }.toInt().coerceAtLeast(0)
                val type = tensorInfo.type.toString()
                
                Log.d(TAG, "Decoder state $name: type=$type, shape=${shape.contentToString()}, size=$size")
                
                val data: Any = when {
                    type.contains("bool", ignoreCase = true) -> ByteArray(size) { 0 }
                    type.contains("int64", ignoreCase = true) -> LongArray(size) { 0L }
                    else -> FloatArray(size) { 0f }
                }
                
                stateMap[name] = Pair(type, data)
            }
        }
        
        Log.d(TAG, "Initialized ${stateMap.size} decoder state tensors")
        decoderState = stateMap
    }
    
    /**
     * Decode latents to audio waveform (streaming).
     * Call initDecoderState() before first use.
     * 
     * @param latent FloatArray of shape [T * 32] (T frames, 32 dims each)
     * @param numFrames Number of frames in the latent
     * @return Audio samples at 24kHz
     */
    fun decode(latent: FloatArray, numFrames: Int): FloatArray {
        val currentState = decoderState ?: run {
            initDecoderState()
            decoderState!!
        }
        
        // Build inputs
        val inputs = mutableMapOf<String, OnnxTensor>()
        
        // Latent input: [1, T, 32]
        inputs["latent"] = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(latent),
            longArrayOf(1, numFrames.toLong(), LATENT_DIM.toLong())
        )
        
        // Add state inputs with proper typing
        currentState.forEach { (name, typeAndData) ->
            val (type, data) = typeAndData
            val stateInfo = decoder.inputInfo[name]?.info as? ai.onnxruntime.TensorInfo ?: return@forEach
            
            inputs[name] = when {
                type.contains("bool", ignoreCase = true) -> {
                    // ONNX Runtime expects boolean as byte array
                    OnnxTensor.createTensor(
                        env,
                        ByteBuffer.wrap(data as ByteArray),
                        stateInfo.shape,
                        ai.onnxruntime.OnnxJavaType.BOOL
                    )
                }
                type.contains("int64", ignoreCase = true) -> {
                    OnnxTensor.createTensor(
                        env,
                        LongBuffer.wrap(data as LongArray),
                        stateInfo.shape
                    )
                }
                else -> {
                    OnnxTensor.createTensor(
                        env,
                        FloatBuffer.wrap(data as FloatArray),
                        stateInfo.shape
                    )
                }
            }
        }
        
        val outputs = decoder.run(inputs)
        
        // Extract audio (first output)
        val audioTensor = outputs[0] as OnnxTensor
        val audio = audioTensor.floatBuffer.let { buf ->
            FloatArray(buf.remaining()).also { buf.get(it) }
        }
        
        // Update decoder state from outputs (critical for avoiding garbling)
        // Reference impl: for k, val in enumerate(res[1:]): mimi_state[f"state_{k}"] = val
        // This means: output at index 1 -> state_0, output at index 2 -> state_1, etc.
        val outputNames = decoder.outputNames.toList()
        for (k in 0 until (outputNames.size - 1)) {
            val outputIdx = k + 1  // Skip first output (audio)
            val outputName = outputNames[outputIdx]
            val stateName = "state_$k"
            val typeAndData = currentState[stateName] ?: continue
            
            val stateResult = outputs.get(outputName)
            val stateTensor = stateResult?.get() as? OnnxTensor ?: continue
            
            val newData: Any = when {
                typeAndData.first.contains("bool", ignoreCase = true) -> {
                    val buf = stateTensor.byteBuffer
                    ByteArray(buf.remaining()).also { buf.get(it) }
                }
                typeAndData.first.contains("int64", ignoreCase = true) -> {
                    val buf = stateTensor.longBuffer
                    LongArray(buf.remaining()).also { buf.get(it) }
                }
                else -> {
                    val buf = stateTensor.floatBuffer
                    FloatArray(buf.remaining()).also { buf.get(it) }
                }
            }
            currentState[stateName] = Pair(typeAndData.first, newData)
        }
        
        // Cleanup
        inputs.values.forEach { (it as OnnxTensor).close() }
        outputs.close()
        
        return audio
    }
    
    /**
     * Reset decoder state for a new generation session.
     */
    fun resetDecoderState() {
        decoderState = null
    }
    
    /**
     * Release resources.
     */
    fun release() {
        decoderState = null
    }
}
