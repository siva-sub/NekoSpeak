package com.nekospeak.tts.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetUtils {
    private const val TAG = "AssetUtils"

    fun extractAssets(context: Context, assetPath: String, destinationDir: File) {
        val assets = context.assets
        try {
            val list = assets.list(assetPath) ?: return
            
            if (list.isEmpty()) {
                // It's a file
                copyAsset(context, assetPath, File(destinationDir, assetPath))
            } else {
                // It's a directory
                val dir = File(destinationDir, assetPath)
                if (!dir.exists()) dir.mkdirs()
                for (file in list) {
                    val subPath = if (assetPath.isEmpty()) file else "$assetPath/$file"
                    extractAssets(context, subPath, destinationDir)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract assets", e)
        }
    }

    private fun copyAsset(context: Context, assetPath: String, destinationFile: File) {
        try {
            // Check if file already exists and size matches? 
            // For now, overwrite or check existence.
            // If development, overwrite is safer.
            if (destinationFile.exists()) return // Optimization
            
            destinationFile.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset: $assetPath", e)
        }
    }
}
