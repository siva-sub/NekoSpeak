package com.nekospeak.tts.engine

/**
 * Interface for TTS engines (Kokoro, VibeVoice)
 */
interface TtsEngine {
    /**
     * Initialize the engine, loading ONNX models
     * @return true if initialization succeeded
     */
    suspend fun initialize(): Boolean
    
    /**
     * Generate audio samples from text
     * @param text Input text to synthesize
     * @param speed Speech speed (0.5 to 2.0)
     * @param voice Voice name/ID
     * @param callback Called with audio chunks as they're generated
     */
    suspend fun generate(
        text: String,
        speed: Float = 1.0f,
        voice: String? = null,
        callback: (FloatArray) -> Unit
    )
    
    /**
     * Get the sample rate of generated audio
     */
    fun getSampleRate(): Int
    
    /**
     * Get list of available voices
     */
    fun getVoices(): List<String>
    
    /**
     * Release resources
     */
    fun release()
    
    /**
     * Check if engine is initialized
     */
    fun isInitialized(): Boolean
}
