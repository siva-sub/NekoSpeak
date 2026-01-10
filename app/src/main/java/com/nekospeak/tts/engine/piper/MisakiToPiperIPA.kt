package com.nekospeak.tts.engine.piper

/**
 * Converts Misaki phonemes to Piper-compatible IPA.
 * 
 * Misaki uses compact notation:
 * - Uppercase vowels for diphthongs: A=eɪ, I=aɪ, O=oʊ, W=aʊ, Y=ɔɪ, Q=əʊ
 * - Merged affricates: ʤ=dʒ, ʧ=tʃ
 * - Custom symbols: ᵊ (small schwa), ɾ (flap), ᵻ (reduced vowel)
 * 
 * Piper uses standard IPA as trained with eSpeak.
 */
object MisakiToPiperIPA {
    
    // Expand Misaki diphthongs to IPA (American English)
    private val DIPHTHONG_MAP = mapOf(
        "A" to "eɪ",  // hey
        "I" to "aɪ",  // high
        "O" to "oʊ",  // go
        "W" to "aʊ",  // how
        "Y" to "ɔɪ",  // soy
        "Q" to "əʊ"   // British only, but include for safety
    )
    
    // Expand Misaki merged consonants to IPA
    private val AFFRICATE_MAP = mapOf(
        "ʤ" to "dʒ",  // jump
        "ʧ" to "tʃ"   // church
    )
    
    // Other conversions
    private val OTHER_MAP = mapOf(
        "ᵊ" to "ə",   // Small schwa -> regular schwa (Piper may not have ᵊ)
        "T" to "ɾ",   // Kokoro's T -> flap (already in Piper as ɾ)
        "ɡ" to "ɡ"    // Ensure correct g character (U+0261)
    )
    
    /**
     * Convert Misaki phoneme string to Piper-compatible IPA.
     */
    fun convert(misakiPhonemes: String): String {
        var result = misakiPhonemes
        
        // Apply all mappings
        for ((misaki, ipa) in DIPHTHONG_MAP) {
            result = result.replace(misaki, ipa)
        }
        for ((misaki, ipa) in AFFRICATE_MAP) {
            result = result.replace(misaki, ipa)
        }
        for ((misaki, ipa) in OTHER_MAP) {
            result = result.replace(misaki, ipa)
        }
        
        return result
    }
    
    /**
     * Check if a phoneme string contains any Misaki-specific symbols
     * that need conversion before passing to Piper.
     */
    fun needsConversion(phonemes: String): Boolean {
        return DIPHTHONG_MAP.keys.any { phonemes.contains(it) } ||
               AFFRICATE_MAP.keys.any { phonemes.contains(it) } ||
               OTHER_MAP.keys.any { phonemes.contains(it) }
    }
}
