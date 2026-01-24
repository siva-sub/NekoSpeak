package com.nekospeak.tts.engine

import android.content.Context
import com.nekospeak.tts.data.PrefsManager
import com.nekospeak.tts.engine.piper.PiperEngine
import com.nekospeak.tts.engine.pocket.PocketTtsEngine

/**
 * Factory to create the appropriate TTS engine based on user preferences.
 * Ensures a cohesive workflow by centralizing engine selection logic.
 */
object EngineFactory {
    
    /**
     * Create or retrieve the appropriate engine instance.
     * @param context Application context
     * @param modelId Optional explicit model ID (overrides preferences)
     */
    fun createEngine(context: Context, modelId: String? = null): TtsEngine {
        val prefs = PrefsManager(context)
        val selectedModel = modelId ?: prefs.currentModel
        
        android.util.Log.i("EngineFactory", "createEngine: modelId=$modelId, prefs.currentModel=${prefs.currentModel}, selectedModel=$selectedModel")
        
        return when {
            selectedModel == "pocket_v1" -> {
                android.util.Log.i("EngineFactory", "Creating PocketTtsEngine")
                PocketTtsEngine(context)
            }
            selectedModel == "kitten_nano" -> {
                // Kitten uses the same KokoroEngine structure but with quantized model
                // For now, we reuse KokoroEngine but it will load nano weights internally if implemented
                // or we can fallback to standard Kokoro if Kitten not fully separate yet.
                KokoroEngine(context)
            }
            selectedModel.startsWith("piper") -> {
                val voiceId = selectedModel.removePrefix("piper_")
                PiperEngine(context, voiceId)
            }
            else -> {
                // Default to Kokoro
                KokoroEngine(context)
            }
        }
    }
}
