package com.nekospeak.tts.engine.pocket

/**
 * Voice state data class that holds the extracted speaker embedding
 * from a reference audio file for voice cloning.
 * 
 * This is serialized to/from binary files for persistence.
 */
data class PocketVoiceState(
    /** Unique identifier for this voice */
    val id: String,
    
    /** Display name shown in UI */
    val displayName: String,
    
    /** Speaker embeddings from mimi_encoder [frames, 1024] flattened */
    val latents: FloatArray,
    
    /** Number of frames in the latent representation */
    val numFrames: Int,
    
    /** Whether this is a bundled pre-made voice or user-cloned */
    val isBundled: Boolean = false,
    
    /** Timestamp when this voice was created/loaded */
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        // Match PocketTtsEngine.EMBED_DIM - mimi_encoder outputs 1024-dim embeddings
        const val EMBED_DIM = 1024
        
        /**
         * Deserialize voice state from binary format
         */
        fun fromBytes(bytes: ByteArray): PocketVoiceState {
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            
            // Read id
            val idLen = buffer.int
            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val id = String(idBytes, Charsets.UTF_8)
            
            // Read displayName
            val nameLen = buffer.int
            val nameBytes = ByteArray(nameLen)
            buffer.get(nameBytes)
            val displayName = String(nameBytes, Charsets.UTF_8)
            
            // Read numFrames
            val numFrames = buffer.int
            
            // Read isBundled
            val isBundled = buffer.get() == 1.toByte()
            
            // Read createdAt
            val createdAt = buffer.long
            
            // Read latents
            val latentSize = numFrames * EMBED_DIM
            val latents = FloatArray(latentSize)
            for (i in 0 until latentSize) {
                latents[i] = buffer.float
            }
            
            return PocketVoiceState(
                id = id,
                displayName = displayName,
                latents = latents,
                numFrames = numFrames,
                isBundled = isBundled,
                createdAt = createdAt
            )
        }
    }
    
    /**
     * Serialize voice state to binary format for storage
     */
    fun toBytes(): ByteArray {
        val idBytes = id.toByteArray(Charsets.UTF_8)
        val nameBytes = displayName.toByteArray(Charsets.UTF_8)
        
        // Calculate total size
        val totalSize = 4 + idBytes.size +    // id length + id
                        4 + nameBytes.size +   // name length + name
                        4 +                     // numFrames
                        1 +                     // isBundled
                        8 +                     // createdAt
                        latents.size * 4        // latents (float32)
        
        val buffer = java.nio.ByteBuffer.allocate(totalSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        
        // Write id
        buffer.putInt(idBytes.size)
        buffer.put(idBytes)
        
        // Write displayName
        buffer.putInt(nameBytes.size)
        buffer.put(nameBytes)
        
        // Write numFrames
        buffer.putInt(numFrames)
        
        // Write isBundled
        buffer.put(if (isBundled) 1.toByte() else 0.toByte())
        
        // Write createdAt
        buffer.putLong(createdAt)
        
        // Write latents
        for (f in latents) {
            buffer.putFloat(f)
        }
        
        return buffer.array()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PocketVoiceState
        return id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}
