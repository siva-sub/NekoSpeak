package com.nekospeak.tts.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

// --- Data Models for voices.json ---
data class PiperVoiceMetadata(
    val key: String,
    val name: String,
    val language: PiperLanguage,
    val quality: String,
    val num_speakers: Int,
    val files: Map<String, PiperFileBase>
)

data class PiperLanguage(
    val code: String, // en_US
    val family: String, // en
    val region: String, // US
    val name_native: String,
    val name_english: String,
    val country_english: String
)

data class PiperFileBase(
    val size_bytes: Long,
    val md5_digest: String
)

// --- Domain Model ---
data class VoiceInfo(
    val id: String,
    val name: String,
    val languageCode: String, // en_US
    val languageName: String, // English (United States)
    val region: String, // US
    val quality: String, // low, medium, high
    val speakerCount: Int,
    val onnxUrl: String, // Absolute URL
    val jsonUrl: String, // Absolute URL
    val sizeBytes: Long,
    val isBundled: Boolean = false
)

class VoiceRepository(private val context: Context) {
    
    val availableVoices: List<VoiceInfo> by lazy { 
        val official = loadVoicesFromIndex() 
        val custom = loadCustomVoices()
        (official + custom).sortedBy { it.languageCode }
    }
    
    private fun loadCustomVoices(): List<VoiceInfo> {
        return listOf(
            VoiceInfo(
                id = "ta_IN-Valluvar-medium",
                name = "Valluvar",
                languageCode = "ta_IN",
                languageName = "Tamil (India)",
                region = "IN",
                quality = "medium",
                speakerCount = 1,
                onnxUrl = "https://huggingface.co/datasets/Jeyaram-K/piper-tamil-voice/resolve/main/ta_IN-Valluvar-medium/ta_IN-Valluvar-medium.onnx",
                jsonUrl = "https://huggingface.co/datasets/Jeyaram-K/piper-tamil-voice/resolve/main/ta_IN-Valluvar-medium/ta_IN-Valluvar-medium.onnx.json",
                sizeBytes = 63511038L, // Approximate
                isBundled = false
            )
        )
    }

    private fun loadVoicesFromIndex(): List<VoiceInfo> {
        val jsonString = try {
            context.assets.open("piper/voices.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
        
        val type = object : TypeToken<Map<String, PiperVoiceMetadata>>() {}.type
        val rawMap: Map<String, PiperVoiceMetadata> = Gson().fromJson(jsonString, type)
        
        return rawMap.values.map { meta ->
            // Find .onnx and .onnx.json in files map
            val onnxKey = meta.files.keys.find { it.endsWith(".onnx") }
            val jsonKey = meta.files.keys.find { it.endsWith(".onnx.json") }
            
            if (onnxKey != null && jsonKey != null) {
                // Check if bundled (Amy Low)
                val isBundled = (meta.key == "en_US-amy-low")
                val baseUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/"
                
                VoiceInfo(
                    id = meta.key,
                    name = meta.name,
                    languageCode = meta.language.code,
                    languageName = "${meta.language.name_english} (${meta.language.country_english})",
                    region = meta.language.region,
                    quality = meta.quality,
                    speakerCount = meta.num_speakers,
                    onnxUrl = baseUrl + onnxKey,
                    jsonUrl = baseUrl + jsonKey,
                    sizeBytes = meta.files[onnxKey]?.size_bytes ?: 0L,
                    isBundled = isBundled
                )
            } else {
                null
            }
        }.filterNotNull()
    }
    
    fun getDownloadState(voiceId: String): DownloadState {
        val voice = availableVoices.find { it.id == voiceId } ?: return DownloadState.NotAvailable
        
        if (voice.isBundled) return DownloadState.Downloaded
        
        // Check External path (Download location)
        val externalDir = context.getExternalFilesDir("piper_downloads")
        val onnx = File(externalDir, "${voice.id}.onnx")
        val json = File(externalDir, "${voice.id}.onnx.json")
        
        // Also check legacy/internal just in case, or merge specific logic?
        // sticking to external primarily for downloads.
        
        return if (onnx.exists() && json.exists()) {
             DownloadState.Downloaded
        } else {
             DownloadState.NotDownloaded
        }
    }
    
    fun getLocalPath(voiceId: String): Pair<File, File>? {
        val voice = availableVoices.find { it.id == voiceId } ?: return null
        
        if (voice.isBundled) {
             return Pair(
                 File(context.filesDir, "${voice.id}.onnx"),
                 File(context.filesDir, "${voice.id}.onnx.json")
             )
        } else {
             // Check External (Downloads)
             val externalDir = context.getExternalFilesDir("piper_downloads")
             val onnx = File(externalDir, "${voice.id}.onnx")
             val json = File(externalDir, "${voice.id}.onnx.json")
             
             if (onnx.exists()) {
                 return Pair(onnx, json)
             }
             
             // Check Internal (Legacy/Manual move)
             val piperDir = File(context.filesDir, "piper")
              val internalOnnx = File(piperDir, "${voice.id}.onnx")
              val internalJson = File(piperDir, "${voice.id}.onnx.json")
              if (internalOnnx.exists()) return Pair(internalOnnx, internalJson)
              
             return null
        }
    }
    
    /**
     * Delete a downloaded Piper voice. Returns true if deleted successfully.
     * Does not delete bundled voices.
     */
    fun deleteVoice(voiceId: String): Boolean {
        val voice = availableVoices.find { it.id == voiceId } ?: return false
        
        // Don't allow deleting bundled voices
        if (voice.isBundled) return false
        
        var deleted = false
        
        // Delete from external piper_downloads
        val externalDir = context.getExternalFilesDir("piper_downloads")
        val onnx = File(externalDir, "${voice.id}.onnx")
        val json = File(externalDir, "${voice.id}.onnx.json")
        if (onnx.exists()) { onnx.delete(); deleted = true }
        if (json.exists()) { json.delete() }
        
        // Also check/delete from internal piper folder (legacy)
        val piperDir = File(context.filesDir, "piper")
        val internalOnnx = File(piperDir, "${voice.id}.onnx")
        val internalJson = File(piperDir, "${voice.id}.onnx.json")
        if (internalOnnx.exists()) { internalOnnx.delete(); deleted = true }
        if (internalJson.exists()) { internalJson.delete() }
        
        return deleted
    }
    
    /**
     * Get list of downloaded (non-bundled) Piper voices
     */
    fun getDownloadedVoices(): List<VoiceInfo> {
        return availableVoices.filter { !it.isBundled && getDownloadState(it.id) == DownloadState.Downloaded }
    }
}

enum class DownloadState {
    NotAvailable,
    NotDownloaded,
    Downloading,
    Downloaded
}
