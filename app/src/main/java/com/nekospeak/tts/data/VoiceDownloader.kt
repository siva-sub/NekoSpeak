package com.nekospeak.tts.data

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.util.Log

data class VoiceDownloadStatus(
    val state: DownloadState,
    val progress: Float // 0.0 to 1.0
)

class VoiceDownloader(private val context: Context) {
    
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs: SharedPreferences = context.getSharedPreferences("piper_downloads", Context.MODE_PRIVATE)

    fun downloadVoice(voiceId: String, onnxUrl: String, jsonUrl: String) {
        // Clear previous
        prefs.edit().remove("${voiceId}_onnx").remove("${voiceId}_json").apply()
        
        val onnxId = enqueueDownload(voiceId, onnxUrl, "$voiceId.onnx")
        val jsonId = enqueueDownload(voiceId, jsonUrl, "$voiceId.onnx.json")
        
        prefs.edit()
            .putLong("${voiceId}_onnx", onnxId)
            .putLong("${voiceId}_json", jsonId)
            .apply()
    }
    
    fun getDownloadStatus(voiceId: String): VoiceDownloadStatus {
        val onnxId = prefs.getLong("${voiceId}_onnx", -1)
        val jsonId = prefs.getLong("${voiceId}_json", -1)
        
        if (onnxId == -1L || jsonId == -1L) {
             // If not in generic prefs, check repository for completion (side-loaded or finished)
             // But here we strictly check active downloads.
             return VoiceDownloadStatus(DownloadState.NotDownloaded, 0f)
        }
        
        val onnxStatus = queryStatus(onnxId)
        val jsonStatus = queryStatus(jsonId)
        
        // Aggregate
        if (onnxStatus.state == DownloadState.Downloading || jsonStatus.state == DownloadState.Downloading) {
            val avg = (onnxStatus.progress + jsonStatus.progress) / 2f
            return VoiceDownloadStatus(DownloadState.Downloading, avg)
        }
        
        if (onnxStatus.state == DownloadState.Downloaded && jsonStatus.state == DownloadState.Downloaded) {
            return VoiceDownloadStatus(DownloadState.Downloaded, 1.0f)
        }
        
        if (onnxStatus.state == DownloadState.NotAvailable || jsonStatus.state == DownloadState.NotAvailable) {
             // Maybe failed?
             return VoiceDownloadStatus(DownloadState.NotAvailable, 0f)
        }

        // Default
        return VoiceDownloadStatus(DownloadState.Downloading, 0f)
    }

    private fun queryStatus(id: Long): VoiceDownloadStatus {
        val query = DownloadManager.Query().setFilterById(id)
        var cursor: Cursor? = null
        try {
            cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusCol)
                
                val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val downloaded = cursor.getLong(downloadedCol)
                val total = cursor.getLong(totalCol)
                
                val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                
                return when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> VoiceDownloadStatus(DownloadState.Downloaded, 1f)
                    DownloadManager.STATUS_RUNNING -> VoiceDownloadStatus(DownloadState.Downloading, progress)
                    DownloadManager.STATUS_PENDING -> VoiceDownloadStatus(DownloadState.Downloading, 0f)
                    DownloadManager.STATUS_FAILED -> VoiceDownloadStatus(DownloadState.NotAvailable, 0f)
                    else -> VoiceDownloadStatus(DownloadState.NotDownloaded, 0f)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return VoiceDownloadStatus(DownloadState.NotDownloaded, 0f)
    }

    private fun enqueueDownload(voiceId: String, url: String, fileName: String): Long {
        return try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Downloading Voice: $voiceId")
                .setDescription(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                .setDestinationInExternalFilesDir(context, "piper_downloads", fileName)
                
            val id = downloadManager.enqueue(request)
            Log.i("VoiceDownloader", "Enqueued download for $fileName (ID: $id)")
            id
        } catch (e: Exception) {
            Log.e("VoiceDownloader", "Download failed", e)
            -1L
        }
    }
}
