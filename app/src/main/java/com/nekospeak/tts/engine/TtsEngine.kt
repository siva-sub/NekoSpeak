package com.nekospeak.tts.engine

/**
 * Interface for TTS engines (Kokoro, Piper, Pocket-TTS)
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
    
    // ========== Voice Cloning Support (Optional) ==========
    
    /**
     * Check if this engine supports voice cloning
     * @return true if cloneVoice() can be called
     */
    fun supportsVoiceCloning(): Boolean = false
    
    /**
     * Clone a voice from an audio file (if supported)
     * @param audioPath Path to the audio file (WAV format, 24kHz recommended)
     * @param voiceName Display name for the cloned voice
     * @return ID of the cloned voice, or null if cloning failed
     */
    suspend fun cloneVoice(audioPath: String, voiceName: String): String? = null
    
    /**
     * Get list of cloned voice IDs (if voice cloning is supported)
     * @return List of cloned voice IDs
     */
    fun getClonedVoices(): List<String> = emptyList()
    
    /**
     * Delete a cloned voice (if voice cloning is supported)
     * @param voiceId ID of the voice to delete
     * @return true if deletion succeeded
     */
    fun deleteClonedVoice(voiceId: String): Boolean = false
}

