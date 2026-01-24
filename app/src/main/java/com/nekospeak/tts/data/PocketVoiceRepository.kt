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

/**
 * Voice sample info for on-demand download.
 */
data class DownloadableVoice(
    val id: String,
    val name: String,
    val gender: String,
    val category: String, // "celebrity", "character", "expressive"
    val description: String,
    val downloadUrl: String,
    val sizeBytes: Long = 0L // Approximate size if known
)

/**
 * Repository for on-demand downloadable Pocket-TTS voice samples.
 * Similar to ModelRepository but for voice WAV files from HuggingFace.
 */
object PocketVoiceRepository {
    private const val TAG = "PocketVoiceRepository"
    
    // Directory for downloaded voices
    private const val DOWNLOADED_VOICES_DIR = "pocket/downloaded_voices"
    
    // HuggingFace datasets base URLs
    private const val CELEBRITIES_BASE = "https://huggingface.co/datasets/sdialog/voices-celebrities/resolve/main/audio"
    
    /**
     * Celebrity voices from sdialog/voices-celebrities dataset.
     * These are curated samples suitable for voice cloning.
     * 
     * > [!WARNING] Celebrity voice cloning raises ethical concerns.
     * > Use responsibly and label synthetic voices clearly.
     */
    val celebrityVoices = listOf(
        // Politicians
        DownloadableVoice(
            id = "celebrity_trump",
            name = "Donald Trump",
            gender = "Male",
            category = "celebrity",
            description = "Former US President",
            downloadUrl = "$CELEBRITIES_BASE/donald-trump.wav"
        ),
        DownloadableVoice(
            id = "celebrity_biden",
            name = "Joe Biden",
            gender = "Male",
            category = "celebrity",
            description = "US President",
            downloadUrl = "$CELEBRITIES_BASE/joe-biden.wav"
        ),
        DownloadableVoice(
            id = "celebrity_obama",
            name = "Barack Obama",
            gender = "Male",
            category = "celebrity",
            description = "Former US President",
            downloadUrl = "$CELEBRITIES_BASE/barack-obama.wav"
        ),
        DownloadableVoice(
            id = "celebrity_hillary",
            name = "Hillary Clinton",
            gender = "Female",
            category = "celebrity",
            description = "Former Secretary of State",
            downloadUrl = "$CELEBRITIES_BASE/hillary_clinton.wav"
        ),
        DownloadableVoice(
            id = "celebrity_kamala",
            name = "Kamala Harris",
            gender = "Female",
            category = "celebrity",
            description = "US Vice President",
            downloadUrl = "$CELEBRITIES_BASE/kamala_harris.wav"
        ),
        // Tech Leaders
        DownloadableVoice(
            id = "celebrity_musk",
            name = "Elon Musk",
            gender = "Male",
            category = "celebrity",
            description = "Tech entrepreneur",
            downloadUrl = "$CELEBRITIES_BASE/elon-musk.wav"
        ),
        DownloadableVoice(
            id = "celebrity_bill_gates",
            name = "Bill Gates",
            gender = "Male",
            category = "celebrity",
            description = "Microsoft co-founder",
            downloadUrl = "$CELEBRITIES_BASE/bill_gates.wav"
        ),
        DownloadableVoice(
            id = "celebrity_zuckerberg",
            name = "Mark Zuckerberg",
            gender = "Male",
            category = "celebrity",
            description = "Meta CEO",
            downloadUrl = "$CELEBRITIES_BASE/mark-zuckerberg.wav"
        ),
        DownloadableVoice(
            id = "celebrity_jensen",
            name = "Jensen Huang",
            gender = "Male",
            category = "celebrity",
            description = "NVIDIA CEO",
            downloadUrl = "$CELEBRITIES_BASE/jensen-huang.wav"
        ),
        // Media & Authors
        DownloadableVoice(
            id = "celebrity_oprah",
            name = "Oprah Winfrey",
            gender = "Female",
            category = "celebrity",
            description = "Media personality",
            downloadUrl = "$CELEBRITIES_BASE/oprah_winfrey.wav"
        ),
        DownloadableVoice(
            id = "celebrity_jk_rowling",
            name = "J.K. Rowling",
            gender = "Female",
            category = "celebrity",
            description = "Harry Potter author",
            downloadUrl = "$CELEBRITIES_BASE/j_k_rowling.wav"
        ),
        // Activists & Others
        DownloadableVoice(
            id = "celebrity_greta",
            name = "Greta Thunberg",
            gender = "Female",
            category = "celebrity",
            description = "Climate activist",
            downloadUrl = "$CELEBRITIES_BASE/greta_thunberg.wav"
        ),
        DownloadableVoice(
            id = "celebrity_tate",
            name = "Andrew Tate",
            gender = "Male",
            category = "celebrity",
            description = "Internet personality",
            downloadUrl = "$CELEBRITIES_BASE/andrew-tate.wav"
        )
    )
    
    // Download state tracking
    private val _downloadStates = mutableMapOf<String, MutableStateFlow<Float>>()
    
    // Encoding/Processing state tracking (Bridge between Service and UI)
    private val _encodingStatus = MutableStateFlow<String?>(null)
    val encodingStatus: StateFlow<String?> = _encodingStatus.asStateFlow()
    
    /**
     * Update the current encoding status.
     * @param status Status message or null to clear.
     */
    fun setEncodingStatus(status: String?) {
        _encodingStatus.value = status
    }
    
    fun getDownloadProgress(voiceId: String): StateFlow<Float>? {
        return _downloadStates[voiceId]?.asStateFlow()
    }
    
    /**
     * Check if a voice sample is already downloaded.
     */
    fun isDownloaded(context: Context, voiceId: String): Boolean {
        val voice = celebrityVoices.find { it.id == voiceId } ?: return false
        val file = File(context.filesDir, "$DOWNLOADED_VOICES_DIR/${voice.id}.wav")
        return file.exists() && file.length() > 1000 // At least 1KB
    }
    
    /**
     * Get the local file path for a downloaded voice.
     */
    fun getVoicePath(context: Context, voiceId: String): File? {
        val voice = celebrityVoices.find { it.id == voiceId } ?: return null
        val file = File(context.filesDir, "$DOWNLOADED_VOICES_DIR/${voice.id}.wav")
        return if (file.exists()) file else null
    }
    
    /**
     * Get all downloaded voices.
     */
    fun getDownloadedVoices(context: Context): List<DownloadableVoice> {
        return celebrityVoices.filter { isDownloaded(context, it.id) }
    }
    
    /**
     * Download a voice sample.
     */
    suspend fun downloadVoice(
        context: Context, 
        voiceId: String, 
        onComplete: (Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        val voice = celebrityVoices.find { it.id == voiceId }
        if (voice == null) {
            Log.e(TAG, "Voice not found: $voiceId")
            onComplete(false)
            return@withContext
        }
        
        if (voice.downloadUrl.isEmpty()) {
            Log.e(TAG, "Voice has no download URL: $voiceId")
            onComplete(false)
            return@withContext
        }
        
        if (_downloadStates.containsKey(voiceId)) {
            Log.w(TAG, "Already downloading: $voiceId")
            return@withContext
        }
        
        val progressFlow = MutableStateFlow(0f)
        _downloadStates[voiceId] = progressFlow
        
        try {
            val dir = File(context.filesDir, DOWNLOADED_VOICES_DIR)
            if (!dir.exists()) dir.mkdirs()
            
            val targetFile = File(dir, "${voice.id}.wav")
            
            Log.d(TAG, "Downloading voice: ${voice.name} from ${voice.downloadUrl}")
            
            downloadFile(voice.downloadUrl, targetFile) { progress ->
                progressFlow.value = progress
            }
            
            progressFlow.value = 1f
            Log.i(TAG, "Downloaded voice: ${voice.name} (${targetFile.length()} bytes)")
            onComplete(true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download voice: ${voice.name}", e)
            onComplete(false)
        } finally {
            _downloadStates.remove(voiceId)
        }
    }
    
    private fun downloadFile(urlString: String, targetFile: File, onProgress: (Float) -> Unit) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        
        try {
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }
            
            val totalSize = connection.contentLength.toLong()
            var downloadedSize = 0L
            
            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        
                        if (totalSize > 0) {
                            onProgress(downloadedSize.toFloat() / totalSize)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Delete a downloaded voice.
     */
    fun deleteVoice(context: Context, voiceId: String): Boolean {
        val file = File(context.filesDir, "$DOWNLOADED_VOICES_DIR/$voiceId.wav")
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}
