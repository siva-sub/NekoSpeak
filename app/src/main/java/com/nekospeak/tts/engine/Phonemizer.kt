package com.nekospeak.tts.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.*
import com.nekospeak.tts.engine.misaki.G2P
import com.nekospeak.tts.engine.misaki.Lexicon

class Phonemizer(private val context: Context) {
    
    companion object {
        private const val TAG = "Phonemizer"
        private const val MAX_PHONEME_LENGTH = 400
    }
    
    // Default vocabulary mapping phonemes to tokens
    // Official Kokoro/Kitten TTS Vocabulary (0-177)
    private val defaultVocab = mapOf(
        "$" to 0, ";" to 1, ":" to 2, "," to 3, "." to 4, "!" to 5, "?" to 6, "¡" to 7, "¿" to 8, "—" to 9,
        "…" to 10, "\"" to 11, "«" to 12, "»" to 13, "“" to 14, "”" to 15, " " to 16,
        "A" to 17, "B" to 18, "C" to 19, "D" to 20, "E" to 21, "F" to 22, "G" to 23, "H" to 24, "I" to 25,
        "J" to 26, "K" to 27, "L" to 28, "M" to 29, "N" to 30, "O" to 31, "P" to 32, "Q" to 33, "R" to 34,
        "S" to 35, "T" to 36, "U" to 37, "V" to 38, "W" to 39, "X" to 40, "Y" to 41, "Z" to 42,
        "a" to 43, "b" to 44, "c" to 45, "d" to 46, "e" to 47, "f" to 48, "g" to 49, "h" to 50, "i" to 51,
        "j" to 52, "k" to 53, "l" to 54, "m" to 55, "n" to 56, "o" to 57, "p" to 58, "q" to 59, "r" to 60,
        "s" to 61, "t" to 62, "u" to 63, "v" to 64, "w" to 65, "x" to 66, "y" to 67, "z" to 68,
        "ɑ" to 69, "ɐ" to 70, "ɒ" to 71, "æ" to 72, "ɓ" to 73, "ʙ" to 74, "β" to 75, "ɔ" to 76, "ɕ" to 77,
        "ç" to 78, "ɗ" to 79, "ɖ" to 80, "ð" to 81, "ʤ" to 82, "ə" to 83, "ɘ" to 84, "ɚ" to 85, "ɛ" to 86,
        "ɜ" to 87, "ɝ" to 88, "ɞ" to 89, "ɟ" to 90, "ʄ" to 91, "ɡ" to 92, "ɠ" to 93, "ɢ" to 94, "ʛ" to 95,
        "ɦ" to 96, "ɧ" to 97, "ħ" to 98, "ɥ" to 99, "ʜ" to 100, "ɨ" to 101, "ɪ" to 102, "ʝ" to 103, "ɭ" to 104,
        "ɬ" to 105, "ɫ" to 106, "ɮ" to 107, "ʟ" to 108, "ɱ" to 109, "ɯ" to 110, "ɰ" to 111, "ŋ" to 112, "ɳ" to 113,
        "ɲ" to 114, "ɴ" to 115, "ø" to 116, "ɵ" to 117, "ɸ" to 118, "θ" to 119, "œ" to 120, "ɶ" to 121, "ʘ" to 122,
        "ɹ" to 123, "ɺ" to 124, "ɾ" to 125, "ɻ" to 126, "ʀ" to 127, "ʁ" to 128, "ɽ" to 129, "ʂ" to 130, "ʃ" to 131,
        "ʈ" to 132, "ʧ" to 133, "ʉ" to 134, "ʊ" to 135, "ʋ" to 136, "ⱱ" to 137, "ʌ" to 138, "ɣ" to 139, "ɤ" to 140,
        "ʍ" to 141, "χ" to 142, "ʎ" to 143, "ʏ" to 144, "ʑ" to 145, "ʐ" to 146, "ʒ" to 147, "ʔ" to 148, "ʡ" to 149,
        "ʕ" to 150, "ʢ" to 151, "ǀ" to 152, "ǁ" to 153, "ǂ" to 154, "ǃ" to 155, "ˈ" to 156, "ˌ" to 157, "ː" to 158,
        "ˑ" to 159, "ʼ" to 160, "ʴ" to 161, "ʰ" to 162, "ʱ" to 163, "ʲ" to 164, "ʷ" to 165, "ˠ" to 166, "ˤ" to 167,
        "˞" to 168, "↓" to 169, "↑" to 170, "→" to 171, "↗" to 172, "↘" to 173, "'" to 174, "̩" to 175, "ᵻ" to 177
    )
    
    private lateinit var g2pUS: G2P
    private lateinit var g2pGB: G2P
    private lateinit var espeak: EspeakWrapper
    private var isLoaded = false
    
    // Legacy maps for non-English or fallback if G2P fails (though G2P handles fallback)
    // We keep them for now but they likely won't be used for English.

    suspend fun load() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext
        try {
            // Espeak Initialization
            val dataDir = java.io.File(context.filesDir, "espeak-ng-data")
            if (!dataDir.exists()) {
                Log.i(TAG, "Extracting espeak-ng-data...")
                com.nekospeak.tts.utils.AssetUtils.extractAssets(context, "espeak-ng-data", context.filesDir)
            }
            
            espeak = EspeakWrapper()
            val initRes = espeak.initialize(context.filesDir.absolutePath)
            if (initRes < 0) { // -1 is error, but returns sample rate on success?
                 Log.e(TAG, "Espeak init failed: $initRes")
            } else {
                 Log.i(TAG, "Espeak initialized with data in ${context.filesDir.absolutePath}")
            }

            val usLexicon = Lexicon(context, british = false)
            usLexicon.load()
            g2pUS = G2P(usLexicon) { text -> 
                try { espeak.textToPhonemes(text, "en-us") } catch (e: Exception) { null }
            }
            
            val gbLexicon = Lexicon(context, british = true)
            gbLexicon.load()
            g2pGB = G2P(gbLexicon) { text ->
                try { espeak.textToPhonemes(text, "en-gb") } catch (e: Exception) { null } 
            }
            
            isLoaded = true
            Log.i(TAG, "Loaded Misaki G2P engines")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load G2P", e)
        }
    }

    fun phonemize(text: String, language: String = "en-us"): String {
        if (!isLoaded) {
             Log.w(TAG, "Phonemizer not loaded, returning empty string")
             return ""
        }
        return try {
             // Normalization is now handled inside G2P logic (preprocess step)
             // But we might want some basic cleanup? 
             // G2P.phonemize takes raw text.
            
            val phonemes = when (language.lowercase()) {
                "en", "en-us", "a" -> g2pUS.phonemize(text)
                "en-gb", "b" -> g2pGB.phonemize(text)
                else -> {
                    // Try direct Espeak for other languages
                    try {
                        espeak.textToPhonemes(text, language)
                    } catch (e: Exception) {
                        Log.w(TAG, "Espeak failed for $language, falling back to English")
                        g2pUS.phonemize(text)
                    }
                }
            }
            
            phonemes.take(MAX_PHONEME_LENGTH)
        } catch (e: Exception) {
            Log.e(TAG, "Phonemization failed", e)
            text.filter { defaultVocab.containsKey(it.toString()) }.take(50)
        }
    }
    
    fun tokenize(phonemes: String): List<Int> {
        val tokens = mutableListOf<Int>()
        var i = 0
        while (i < phonemes.length && i < MAX_PHONEME_LENGTH) {
            var matched = false
            for (length in 3 downTo 1) {
                if (i + length <= phonemes.length) {
                    val substring = phonemes.substring(i, i + length)
                    defaultVocab[substring]?.let { tokenId ->
                        tokens.add(tokenId)
                        i += length
                        matched = true
                        return@let
                    }
                }
            }
            if (!matched) i++
        }
        return tokens
    }
}
