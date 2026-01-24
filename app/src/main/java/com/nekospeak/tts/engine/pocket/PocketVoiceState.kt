package com.nekospeak.tts.engine.pocket

import android.util.Log

/**
 * Voice state data class that holds the extracted speaker embedding
 * from a reference audio file for voice cloning.
 * 
 * Binary format v1:
 * - 4 bytes: Magic header "PKVS" (0x50 0x4B 0x56 0x53)
 * - 2 bytes: Version (little-endian short)
 * - 2 bytes: Reserved
 * - 4 bytes: idLen
 * - idLen bytes: id (UTF-8)
 * - 4 bytes: nameLen
 * - nameLen bytes: displayName (UTF-8)
 * - 4 bytes: numFrames
 * - 1 byte: isBundled
 * - 8 bytes: createdAt
 * - numFrames * EMBED_DIM * 4 bytes: latents (float32)
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
        private const val TAG = "PocketVoiceState"
        
        // Match PocketTtsEngine.EMBED_DIM - mimi_encoder outputs 1024-dim embeddings
        const val EMBED_DIM = 1024
        
        // File format constants
        private const val MAGIC = 0x53564B50  // "PKVS" in little-endian
        private const val VERSION: Short = 1
        
        // Bounds for validation
        private const val MAX_ID_LEN = 256
        private const val MAX_NAME_LEN = 256
        private const val MAX_FRAMES = 10000  // ~13 minutes of audio at 12.5fps
        
        /**
         * Deserialize voice state from binary format
         * 
         * @throws IllegalArgumentException if the file is corrupted or malformed
         */
        fun fromBytes(bytes: ByteArray): PocketVoiceState {
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            
            // Check minimum size for header
            if (bytes.size < 8) {
                throw IllegalArgumentException("File too small: ${bytes.size} bytes")
            }
            
            // Check for v1 format with magic header
            val firstInt = buffer.int
            if (firstInt == MAGIC) {
                // New v1+ format with header
                return fromBytesV1(buffer)
            } else {
                // Legacy format (no magic header) - treat firstInt as idLen
                buffer.rewind()
                return fromBytesLegacy(buffer)
            }
        }
        
        private fun fromBytesV1(buffer: java.nio.ByteBuffer): PocketVoiceState {
            // Read version
            val version = buffer.short
            if (version != VERSION) {
                Log.w(TAG, "Unknown version $version, attempting to read as v1")
            }
            
            // Skip reserved bytes
            buffer.short
            
            // Read id with bounds check
            val idLen = buffer.int
            if (idLen < 1 || idLen > MAX_ID_LEN) {
                throw IllegalArgumentException("Invalid id length: $idLen (max: $MAX_ID_LEN)")
            }
            if (buffer.remaining() < idLen) {
                throw IllegalArgumentException("File truncated: expected $idLen bytes for id")
            }
            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val id = String(idBytes, Charsets.UTF_8)
            
            // Read displayName with bounds check
            val nameLen = buffer.int
            if (nameLen < 0 || nameLen > MAX_NAME_LEN) {
                throw IllegalArgumentException("Invalid name length: $nameLen (max: $MAX_NAME_LEN)")
            }
            if (buffer.remaining() < nameLen) {
                throw IllegalArgumentException("File truncated: expected $nameLen bytes for name")
            }
            val nameBytes = ByteArray(nameLen)
            buffer.get(nameBytes)
            val displayName = String(nameBytes, Charsets.UTF_8)
            
            // Read numFrames with bounds check
            val numFrames = buffer.int
            if (numFrames < 1 || numFrames > MAX_FRAMES) {
                throw IllegalArgumentException("Invalid numFrames: $numFrames (max: $MAX_FRAMES)")
            }
            
            // Read isBundled
            val isBundled = buffer.get() == 1.toByte()
            
            // Read createdAt
            val createdAt = buffer.long
            
            // Validate remaining size for latents
            val latentSize = numFrames * EMBED_DIM
            val expectedBytes = latentSize * 4
            if (buffer.remaining() < expectedBytes) {
                throw IllegalArgumentException("File truncated: expected $expectedBytes bytes for latents, got ${buffer.remaining()}")
            }
            
            // Read latents
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
        
        /**
         * Legacy format parser for backward compatibility
         */
        private fun fromBytesLegacy(buffer: java.nio.ByteBuffer): PocketVoiceState {
            // Read id (legacy: no bounds check but add safety)
            val idLen = buffer.int
            if (idLen < 1 || idLen > MAX_ID_LEN) {
                throw IllegalArgumentException("Invalid legacy id length: $idLen")
            }
            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val id = String(idBytes, Charsets.UTF_8)
            
            // Read displayName
            val nameLen = buffer.int
            if (nameLen < 0 || nameLen > MAX_NAME_LEN) {
                throw IllegalArgumentException("Invalid legacy name length: $nameLen")
            }
            val nameBytes = ByteArray(nameLen)
            buffer.get(nameBytes)
            val displayName = String(nameBytes, Charsets.UTF_8)
            
            // Read numFrames
            val numFrames = buffer.int
            if (numFrames < 1 || numFrames > MAX_FRAMES) {
                throw IllegalArgumentException("Invalid legacy numFrames: $numFrames")
            }
            
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
     * Serialize voice state to binary format v1 with magic header
     */
    fun toBytes(): ByteArray {
        val idBytes = id.toByteArray(Charsets.UTF_8)
        val nameBytes = displayName.toByteArray(Charsets.UTF_8)
        
        // Calculate total size with header
        val totalSize = 4 +                     // magic
                        2 +                     // version
                        2 +                     // reserved
                        4 + idBytes.size +      // id length + id
                        4 + nameBytes.size +    // name length + name
                        4 +                     // numFrames
                        1 +                     // isBundled
                        8 +                     // createdAt
                        latents.size * 4        // latents (float32)
        
        val buffer = java.nio.ByteBuffer.allocate(totalSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        
        // Write header
        buffer.putInt(MAGIC)
        buffer.putShort(VERSION)
        buffer.putShort(0)  // Reserved
        
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
