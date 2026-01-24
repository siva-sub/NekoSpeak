package com.nekospeak.tts.engine.pocket

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jtransforms.fft.DoubleFFT_1D
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * GTCRN (Grouped Temporal Convolutional Recurrent Network) based audio denoiser.
 * 
 * Based on:
 * - https://github.com/Xiaobin-Rong/gtcrn (original model)
 * - https://github.com/k2-fsa/sherpa-onnx (ONNX integration reference)
 * 
 * Uses JTransforms for proper FFT-based STFT/iSTFT following sherpa-onnx's implementation.
 * Ultra-lightweight (48.2K params, 33 MMACs/s) real-time speech enhancement.
 * Processes 16kHz mono audio with STFT-based frame processing.
 */
class GtcrnDenoiser(private val context: Context) {
    
    companion object {
        private const val TAG = "GtcrnDenoiser"
        
        // Model paths
        private const val MODEL_FILENAME = "gtcrn_simple.onnx"
        private const val ASSET_MODELS_DIR = "pocket/models"  // Bundled in assets
        private const val MODELS_DIR = "pocket/models"        // Extracted to filesDir
        
        // GTCRN model parameters (fixed by model architecture)
        const val SAMPLE_RATE = 16000
        const val N_FFT = 512
        const val HOP_LENGTH = 256
        const val WIN_LENGTH = 512
        
        // Number of frequency bins (real FFT output)
        private const val NUM_BINS = N_FFT / 2 + 1  // 257
        
        // Cache tensor shapes (from model metadata)
        private val CONV_CACHE_SHAPE = longArrayOf(2, 1, 16, 16, 33)
        private val TRA_CACHE_SHAPE = longArrayOf(2, 3, 1, 1, 16)
        private val INTER_CACHE_SHAPE = longArrayOf(2, 1, 33, 16)
    }
    
    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var isInitialized = false
    
    // FFT instance (reusable)
    private val fft = DoubleFFT_1D(N_FFT.toLong())
    
    // Hanning window for STFT (satisfies COLA condition with 50% overlap)
    private val window = DoubleArray(WIN_LENGTH) { i ->
        0.5 * (1 - cos(2 * PI * i / WIN_LENGTH))
    }
    
    /**
     * Initialize the denoiser. Loads the model from bundled assets.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = ensureModelExtracted()
            if (modelFile == null) {
                Log.e(TAG, "Failed to extract GTCRN model from assets")
                return@withContext false
            }
            
            ortEnv = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            session = ortEnv!!.createSession(modelFile.absolutePath, sessionOptions)
            isInitialized = true
            
            Log.i(TAG, "GTCRN denoiser initialized successfully with JTransforms FFT")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GTCRN denoiser", e)
            false
        }
    }
    
    /**
     * Denoise an entire audio buffer.
     * 
     * @param audio Input audio at 16kHz mono
     * @return Denoised audio at 16kHz mono
     */
    suspend fun denoise(audio: FloatArray): FloatArray = withContext(Dispatchers.Default) {
        if (!isInitialized || session == null) {
            Log.w(TAG, "Denoiser not initialized, returning original audio")
            return@withContext audio
        }
        
        try {
            processFullAudio(audio)
        } catch (e: Exception) {
            Log.e(TAG, "Error during denoising, returning original audio", e)
            audio
        }
    }
    
    /**
     * Process full audio following sherpa-onnx reference implementation:
     * 1. Compute full STFT of input audio
     * 2. Process each frame through GTCRN model
     * 3. Compute iSTFT to reconstruct output audio
     */
    private fun processFullAudio(audio: FloatArray): FloatArray {
        val env = ortEnv ?: return audio
        val sess = session ?: return audio
        
        // Step 1: Compute STFT of entire input audio
        val stftResult = computeStft(audio)
        val numFrames = stftResult.numFrames
        
        Log.d(TAG, "STFT computed: $numFrames frames, ${stftResult.real.size} total bins")
        
        // Initialize model state caches
        var convCache = createZeroTensor(env, CONV_CACHE_SHAPE)
        var traCache = createZeroTensor(env, TRA_CACHE_SHAPE)
        var interCache = createZeroTensor(env, INTER_CACHE_SHAPE)
        
        // Enhanced STFT result (will be filled frame-by-frame)
        val enhancedReal = FloatArray(numFrames * NUM_BINS)
        val enhancedImag = FloatArray(numFrames * NUM_BINS)
        
        // Step 2: Process each frame through the model
        for (frameIdx in 0 until numFrames) {
            // Extract frame STFT data
            val frameOffset = frameIdx * NUM_BINS
            
            // Prepare input tensor: [1, NUM_BINS, 1, 2] with real/imag interleaved
            val mixInput = FloatArray(NUM_BINS * 2)
            for (i in 0 until NUM_BINS) {
                mixInput[i * 2] = stftResult.real[frameOffset + i]
                mixInput[i * 2 + 1] = stftResult.imag[frameOffset + i]
            }
            
            val mixShape = longArrayOf(1, NUM_BINS.toLong(), 1, 2)
            val mixTensor = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(mixInput), mixShape
            )
            
            // Run model inference
            val inputs = mapOf(
                "mix" to mixTensor,
                "conv_cache" to convCache,
                "tra_cache" to traCache,
                "inter_cache" to interCache
            )
            
            val outputs = sess.run(inputs)
            
            // Get enhanced output and updated caches
            val enhancedTensor = outputs.get(0) as OnnxTensor
            val newConvCache = outputs.get(1) as OnnxTensor
            val newTraCache = outputs.get(2) as OnnxTensor
            val newInterCache = outputs.get(3) as OnnxTensor
            
            // Extract enhanced STFT for this frame
            val enhancedBuffer = enhancedTensor.floatBuffer
            for (i in 0 until NUM_BINS) {
                enhancedReal[frameOffset + i] = enhancedBuffer.get(i * 2)
                enhancedImag[frameOffset + i] = enhancedBuffer.get(i * 2 + 1)
            }
            
            // Copy cache data BEFORE closing outputs
            val newConvData = FloatArray(CONV_CACHE_SHAPE.reduce { a, b -> a * b }.toInt())
            newConvCache.floatBuffer.get(newConvData)
            
            val newTraData = FloatArray(TRA_CACHE_SHAPE.reduce { a, b -> a * b }.toInt())
            newTraCache.floatBuffer.get(newTraData)
            
            val newInterData = FloatArray(INTER_CACHE_SHAPE.reduce { a, b -> a * b }.toInt())
            newInterCache.floatBuffer.get(newInterData)
            
            // Close old caches and input tensor
            convCache.close()
            traCache.close()
            interCache.close()
            mixTensor.close()
            
            // Create new cache tensors
            convCache = OnnxTensor.createTensor(env, FloatBuffer.wrap(newConvData), CONV_CACHE_SHAPE)
            traCache = OnnxTensor.createTensor(env, FloatBuffer.wrap(newTraData), TRA_CACHE_SHAPE)
            interCache = OnnxTensor.createTensor(env, FloatBuffer.wrap(newInterData), INTER_CACHE_SHAPE)
            
            // Close outputs (this also closes tensors returned from the run)
            outputs.close()
        }
        
        // Clean up final caches
        convCache.close()
        traCache.close()
        interCache.close()
        
        // Step 3: Compute iSTFT to reconstruct output audio
        val enhancedStft = StftResult(enhancedReal, enhancedImag, numFrames)
        val result = computeIstft(enhancedStft, audio.size)
        
        Log.d(TAG, "GTCRN denoising complete: ${audio.size} -> ${result.size} samples")
        return result
    }
    
    /**
     * STFT result container.
     */
    private data class StftResult(
        val real: FloatArray,   // [numFrames * NUM_BINS]
        val imag: FloatArray,   // [numFrames * NUM_BINS]
        val numFrames: Int
    )
    
    /**
     * Compute Short-Time Fourier Transform using JTransforms.
     * Output: real and imag arrays of shape [numFrames * NUM_BINS]
     */
    private fun computeStft(audio: FloatArray): StftResult {
        // Calculate number of frames
        val numFrames = if (audio.size >= WIN_LENGTH) {
            1 + (audio.size - WIN_LENGTH) / HOP_LENGTH
        } else {
            1
        }
        
        val real = FloatArray(numFrames * NUM_BINS)
        val imag = FloatArray(numFrames * NUM_BINS)
        
        // Temporary buffer for FFT (needs to be 2*N_FFT for complex output)
        val fftBuffer = DoubleArray(N_FFT * 2)
        
        for (frameIdx in 0 until numFrames) {
            val hopStart = frameIdx * HOP_LENGTH
            
            // Clear FFT buffer
            fftBuffer.fill(0.0)
            
            // Copy windowed samples to FFT buffer
            for (i in 0 until WIN_LENGTH) {
                val sampleIdx = hopStart + i
                val sample = if (sampleIdx < audio.size) audio[sampleIdx].toDouble() else 0.0
                fftBuffer[i] = sample * window[i]
            }
            
            // Perform FFT (in-place, result is complex interleaved: real[0], imag[0], real[1], imag[1], ...)
            fft.realForwardFull(fftBuffer)
            
            // Extract positive frequency bins (0 to N_FFT/2 inclusive)
            val frameOffset = frameIdx * NUM_BINS
            for (i in 0 until NUM_BINS) {
                real[frameOffset + i] = fftBuffer[2 * i].toFloat()
                imag[frameOffset + i] = fftBuffer[2 * i + 1].toFloat()
            }
        }
        
        return StftResult(real, imag, numFrames)
    }
    
    /**
     * Compute Inverse Short-Time Fourier Transform using JTransforms.
     * Uses overlap-add method for reconstruction.
     */
    private fun computeIstft(stft: StftResult, originalLength: Int): FloatArray {
        val numFrames = stft.numFrames
        
        // Output buffer with overlap-add accumulation
        val outputLength = (numFrames - 1) * HOP_LENGTH + WIN_LENGTH
        val output = FloatArray(outputLength)
        val windowSum = FloatArray(outputLength)  // For normalization
        
        // Temporary buffer for iFFT
        val ifftBuffer = DoubleArray(N_FFT * 2)
        
        for (frameIdx in 0 until numFrames) {
            val frameOffset = frameIdx * NUM_BINS
            val hopStart = frameIdx * HOP_LENGTH
            
            // Prepare complex spectrum for iFFT
            // Fill positive frequencies
            for (i in 0 until NUM_BINS) {
                ifftBuffer[2 * i] = stft.real[frameOffset + i].toDouble()
                ifftBuffer[2 * i + 1] = stft.imag[frameOffset + i].toDouble()
            }
            
            // Fill negative frequencies (conjugate symmetry for real signal)
            for (i in 1 until N_FFT / 2) {
                val negIdx = N_FFT - i
                ifftBuffer[2 * negIdx] = stft.real[frameOffset + i].toDouble()
                ifftBuffer[2 * negIdx + 1] = -stft.imag[frameOffset + i].toDouble()
            }
            
            // Perform inverse FFT
            fft.complexInverse(ifftBuffer, true)
            
            // Overlap-add with synthesis window
            for (i in 0 until WIN_LENGTH) {
                val outIdx = hopStart + i
                if (outIdx < output.size) {
                    // Real part is at even indices after complexInverse
                    val sample = ifftBuffer[2 * i] * window[i]
                    output[outIdx] += sample.toFloat()
                    windowSum[outIdx] += (window[i] * window[i]).toFloat()
                }
            }
        }
        
        // Normalize by window sum (COLA normalization)
        for (i in output.indices) {
            if (windowSum[i] > 1e-8f) {
                output[i] /= windowSum[i]
            }
        }
        
        // Return output trimmed to original length
        return output.copyOf(minOf(originalLength, output.size))
    }
    
    private fun createZeroTensor(env: OrtEnvironment, shape: LongArray): OnnxTensor {
        val size = shape.reduce { a, b -> a * b }.toInt()
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(size)), shape)
    }
    
    /**
     * Extract GTCRN model from bundled assets to filesDir.
     */
    private suspend fun ensureModelExtracted(): File? = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, MODELS_DIR)
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        
        val modelFile = File(modelsDir, MODEL_FILENAME)
        
        // Check if already extracted
        if (modelFile.exists() && modelFile.length() > 0) {
            Log.i(TAG, "GTCRN model already extracted: ${modelFile.absolutePath}")
            return@withContext modelFile
        }
        
        // Extract from assets
        Log.i(TAG, "Extracting GTCRN model from assets...")
        try {
            val assetPath = "$ASSET_MODELS_DIR/$MODEL_FILENAME"
            context.assets.open(assetPath).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "GTCRN model extracted successfully: ${modelFile.length()} bytes")
            modelFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract GTCRN model from assets", e)
            if (modelFile.exists()) {
                modelFile.delete()
            }
            null
        }
    }
    
    /**
     * Check if the model is available (bundled in assets or already extracted).
     */
    fun isModelAvailable(): Boolean {
        // Check if already extracted
        val modelsDir = File(context.filesDir, MODELS_DIR)
        val modelFile = File(modelsDir, MODEL_FILENAME)
        if (modelFile.exists() && modelFile.length() > 0) {
            return true
        }
        
        // Check if available in assets
        return try {
            val assetPath = "$ASSET_MODELS_DIR/$MODEL_FILENAME"
            context.assets.open(assetPath).use { true }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Release resources.
     */
    fun close() {
        session?.close()
        session = null
        ortEnv = null
        isInitialized = false
    }
}
