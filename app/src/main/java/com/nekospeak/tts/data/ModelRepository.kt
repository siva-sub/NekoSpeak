package com.nekospeak.tts.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ModelFile(
    val fileName: String,
    val downloadUrl: String,
    val description: String
)

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val files: List<ModelFile>,
    val version: String = "1.0"
)

object ModelRepository {
    private const val TAG = "ModelRepository"
    
    // URLs from KevinAHM's HuggingFace space and official tts-voices
    // Note: mimi_encoder and text_conditioner are FP32 only, others have INT8
    private const val HF_BASE = "https://huggingface.co/spaces/KevinAHM/pocket-tts-web/resolve/main"
    private const val VOICES_BASE = "https://huggingface.co/kyutai/tts-voices/resolve/main"
    
    val models = listOf(
        ModelInfo(
            id = "pocket_v1",
            name = "Pocket-TTS (Experimental)",
            description = "Required for voice cloning. Includes encoder, decoder, and flow matching models (~200MB total).",
            files = listOf(
                // ONNX Models - use correct filenames from HuggingFace
                ModelFile("pocket/models/mimi_encoder.onnx", "$HF_BASE/onnx/mimi_encoder.onnx", "Mimi Encoder (73MB)"),
                ModelFile("pocket/models/text_conditioner.onnx", "$HF_BASE/onnx/text_conditioner.onnx", "Text Conditioner (16MB)"),
                ModelFile("pocket/models/flow_lm_main_int8.onnx", "$HF_BASE/onnx/flow_lm_main_int8.onnx", "Flow LM Main INT8 (76MB)"),
                ModelFile("pocket/models/flow_lm_flow_int8.onnx", "$HF_BASE/onnx/flow_lm_flow_int8.onnx", "Flow LM Flow INT8 (10MB)"),
                ModelFile("pocket/models/mimi_decoder_int8.onnx", "$HF_BASE/onnx/mimi_decoder_int8.onnx", "Mimi Decoder INT8 (23MB)"),
                // Tokenizer
                ModelFile("pocket/tokenizer.model", "$HF_BASE/tokenizer.model", "Tokenizer (59KB)"),
                // Voice WAV files from official kyutai/tts-voices (encoded at runtime via mimi_encoder)
                ModelFile("pocket/voices/alba.wav", "$VOICES_BASE/alba-mackenna/casual.wav", "Alba Voice"),
                ModelFile("pocket/voices/marius.wav", "$VOICES_BASE/voice-donations/Selfie.wav", "Marius Voice"),
                ModelFile("pocket/voices/javert.wav", "$VOICES_BASE/voice-donations/Butter.wav", "Javert Voice"),
                ModelFile("pocket/voices/jean.wav", "$VOICES_BASE/ears/p010/freeform_speech_01.wav", "Jean Voice"),
                ModelFile("pocket/voices/fantine.wav", "$VOICES_BASE/vctk/p244_023.wav", "Fantine Voice"),
                ModelFile("pocket/voices/cosette.wav", "$VOICES_BASE/expresso/ex04-ex02_confused_001_channel1_499s.wav", "Cosette Voice"),
                ModelFile("pocket/voices/eponine.wav", "$VOICES_BASE/vctk/p262_023.wav", "Eponine Voice"),
                ModelFile("pocket/voices/azelma.wav", "$VOICES_BASE/vctk/p303_023.wav", "Azelma Voice")
            )
        ),
        ModelInfo(
            id = "kokoro_v1.0",
            name = "Kokoro v1.0",
            description = "High quality, expressive standard voices.",
            files = listOf(
                ModelFile("kokoro-v1.0.int8.onnx", "https://github.com/siva-sub/NekoSpeak/releases/download/v1.0.0/kokoro-v1.0.int8.onnx", "Model Weights"),
                ModelFile("voices-v1.0.bin", "https://github.com/siva-sub/NekoSpeak/releases/download/v1.0.0/voices-v1.0.bin", "Voice Pack")
            )
        ),
        ModelInfo(
            id = "kitten_nano",
            name = "Kitten TTS Nano",
            description = "Lightning fast, low latency model.",
            files = listOf(
                ModelFile("kitten_tts_nano_v0_1.onnx", "https://github.com/siva-sub/NekoSpeak/releases/download/v1.0.0/kitten_tts_nano_v0_1.onnx", "Model Weights"),
                ModelFile("voices.npz", "https://github.com/siva-sub/NekoSpeak/releases/download/v1.0.0/voices.npz", "Voice Pack")
            )
        )
    )
    
    // Simple in-memory download state tracker
    private val _downloadStates = mutableMapOf<String, MutableStateFlow<Float>>()
    
    fun getDownloadProgress(modelId: String): StateFlow<Float>? {
        return _downloadStates[modelId]?.asStateFlow()
    }

    fun isInstalled(context: Context, modelId: String): Boolean {
        val model = models.find { it.id == modelId } ?: return false
        return model.files.all { fileDef ->
            val file = File(context.filesDir, fileDef.fileName)
            // Check file exists and has reasonable size (> 1KB to avoid placeholder files)
            file.exists() && file.length() > 1024
        }
    }
    
    suspend fun downloadModel(context: Context, modelId: String, onComplete: (Boolean) -> Unit) = withContext(Dispatchers.IO) {
        val model = models.find { it.id == modelId } ?: return@withContext
        
        if (_downloadStates.containsKey(modelId)) return@withContext // Already downloading
        
        val progressFlow = MutableStateFlow(0f)
        _downloadStates[modelId] = progressFlow
        
        try {
            val totalFiles = model.files.size
            var filesDownloaded = 0
            
            for (fileDef in model.files) {
                val targetFile = File(context.filesDir, fileDef.fileName)
                
                // Skip if already downloaded and valid
                if (targetFile.exists() && targetFile.length() > 1024) {
                    Log.d(TAG, "File already exists: ${fileDef.fileName}")
                    filesDownloaded++
                    progressFlow.value = filesDownloaded.toFloat() / totalFiles
                    continue
                }
                
                // Create parent directories
                targetFile.parentFile?.mkdirs()
                
                Log.i(TAG, "Downloading: ${fileDef.downloadUrl}")
                
                val success = downloadFile(fileDef.downloadUrl, targetFile) { fileProgress ->
                    val totalProgress = (filesDownloaded + fileProgress) / totalFiles
                    progressFlow.value = totalProgress
                }
                
                if (!success) {
                    Log.e(TAG, "Failed to download: ${fileDef.fileName}")
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@withContext
                }
                
                filesDownloaded++
                Log.i(TAG, "Downloaded: ${fileDef.fileName} (${targetFile.length() / 1024}KB)")
            }
            
            Log.i(TAG, "Model $modelId download complete")
            withContext(Dispatchers.Main) { onComplete(true) }
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $modelId", e)
            e.printStackTrace()
            withContext(Dispatchers.Main) { onComplete(false) }
        } finally {
            _downloadStates.remove(modelId)
        }
    }
    
    private fun downloadFile(urlString: String, targetFile: File, onProgress: (Float) -> Unit): Boolean {
        var connection: HttpURLConnection? = null
        try {
            var currentUrl = urlString
            var redirectCount = 0
            val maxRedirects = 5
            
            // Handle redirects manually (HuggingFace uses them for LFS files)
            while (redirectCount < maxRedirects) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 120000  // 2 min timeout for large files
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "NekoSpeak-TTS/1.0")
                connection.instanceFollowRedirects = false  // Handle redirects manually
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Response code for $currentUrl: $responseCode")
                
                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> break  // Success, proceed to download
                    HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_MOVED_PERM, 
                    HttpURLConnection.HTTP_SEE_OTHER, 307, 308 -> {
                        // Follow redirect
                        val newUrl = connection.getHeaderField("Location")
                        if (newUrl == null) {
                            Log.e(TAG, "Redirect without Location header")
                            return false
                        }
                        Log.d(TAG, "Redirecting to: $newUrl")
                        currentUrl = newUrl
                        connection.disconnect()
                        redirectCount++
                    }
                    else -> {
                        Log.e(TAG, "HTTP error: $responseCode for $urlString")
                        return false
                    }
                }
            }
            
            if (redirectCount >= maxRedirects) {
                Log.e(TAG, "Too many redirects for $urlString")
                return false
            }
            
            val contentLength = connection!!.contentLengthLong
            Log.d(TAG, "Content length: $contentLength bytes")
            
            var bytesDownloaded = 0L
            
            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        
                        if (contentLength > 0) {
                            onProgress(bytesDownloaded.toFloat() / contentLength)
                        }
                    }
                }
            }
            
            Log.i(TAG, "Downloaded ${bytesDownloaded / 1024}KB to ${targetFile.name}")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            targetFile.delete() // Clean up partial download
            return false
        } finally {
            connection?.disconnect()
        }
    }
    
    fun deleteModel(context: Context, modelId: String) {
        val model = models.find { it.id == modelId } ?: return
        model.files.forEach { fileDef ->
             val file = File(context.filesDir, fileDef.fileName)
             if (file.exists()) file.delete()
        }
    }
}
