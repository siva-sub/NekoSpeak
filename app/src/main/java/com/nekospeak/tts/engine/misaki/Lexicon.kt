package com.nekospeak.tts.engine.misaki

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.util.Locale

class Lexicon(private val context: Context, private val british: Boolean) {

    private val golds: MutableMap<String, Any> = mutableMapOf()
    private val silvers: MutableMap<String, Any> = mutableMapOf()
    private val capStresses = Pair(0.5, 2.0)
    
    private val US_TAUS = setOf("A", "I", "O", "W", "Y", "i", "u", "æ", "ɑ", "ə", "ɛ", "ɪ", "ɹ", "ʊ", "ʌ")
    private val CURRENCIES = mapOf(
        "$" to Pair("dollar", "cent"),
        "£" to Pair("pound", "pence"),
        "€" to Pair("euro", "cent")
    )
    private val ADD_SYMBOLS = mapOf("." to "dot", "/" to "slash")
    private val SYMBOLS = mapOf("%" to "percent", "&" to "and", "+" to "plus", "@" to "at")
    
    companion object {
        private const val TAG = "Lexicon"
    }
    
    suspend fun load() {
        Log.d(TAG, "Loading lexicons (british=$british)...")
        golds.putAll(loadJson(if (british) "gb_gold.json" else "us_gold.json"))
        silvers.putAll(loadJson(if (british) "gb_silver.json" else "us_silver.json"))
        Log.d(TAG, "Loaded golds: ${golds.size}, silvers: ${silvers.size}")
    }
    
    private fun loadJson(filename: String): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        try {
            context.assets.open(filename).bufferedReader().use { reader ->
                android.util.JsonReader(reader).use { jsonReader ->
                    jsonReader.beginObject()
                    while (jsonReader.hasNext()) {
                        val key = jsonReader.nextName()
                        val token = jsonReader.peek()
                        if (token == android.util.JsonToken.BEGIN_OBJECT) {
                            // Variant map
                            val variantMap = mutableMapOf<String, String>()
                            jsonReader.beginObject()
                            while (jsonReader.hasNext()) {
                                val vKey = jsonReader.nextName()
                                if (jsonReader.peek() == android.util.JsonToken.NULL) {
                                    jsonReader.nextNull()
                                } else {
                                    variantMap[vKey] = jsonReader.nextString()
                                }
                            }
                            jsonReader.endObject()
                            map[key] = variantMap
                        } else if (token == android.util.JsonToken.STRING) {
                            map[key] = jsonReader.nextString()
                        } else {
                            jsonReader.skipValue()
                        }
                    }
                    jsonReader.endObject()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $filename", e)
        }
        return map
    }

    private fun getParentTag(tag: String?): String? {
        if (tag == null) return null
        if (tag.startsWith("VB")) return "VERB"
        if (tag.startsWith("NN")) return "NOUN"
        if (tag.startsWith("ADV") || tag.startsWith("RB")) return "ADV"
        if (tag.startsWith("ADJ") || tag.startsWith("JJ")) return "ADJ"
        return tag
    }

    fun isKnown(word: String, tag: String? = null): Boolean {
        if (golds.containsKey(word) || SYMBOLS.containsKey(word) || silvers.containsKey(word)) return true
        // Additional checks matching python logic...
        if (word.length == 1 && word[0].isLetter()) return true // Single letter fallback
        return false // Simplified
    }
    
    fun lookup(word: String, tag: String?, stress: Double?, ctx: TokenContext?): Pair<String?, Int> {
        var lookupWord = word
        var isNNP = false
        
        // Case handling logic
        // Python: if word == word.upper() and word not in self.golds: word = word.lower(); is_NNP = tag == 'NNP'
        if (word == word.uppercase(Locale.getDefault()) && !golds.containsKey(word)) {
            lookupWord = word.lowercase(Locale.getDefault())
            isNNP = tag == "NNP"
        }

        var ps: Any? = golds[lookupWord]
        var rating = 4
        
        if (ps == null && !isNNP) {
            ps = silvers[lookupWord]
            rating = 3
        }
        
        if (ps is Map<*, *>) {
             @Suppress("UNCHECKED_CAST")
             val psMap = ps as Map<String, String>
             var currentTag = tag
             
             if (ctx?.futureVowel == null && psMap.containsKey("None")) {
                 currentTag = "None"
             } else if (currentTag != null && !psMap.containsKey(currentTag)) {
                 currentTag = getParentTag(currentTag)
             }
             
             ps = psMap[currentTag] ?: psMap["DEFAULT"]
        }
        
        var phonemes = ps as? String
        
        if (phonemes == null || (isNNP && !"ˈ".toRegex().containsMatchIn(phonemes))) {
             // get_NNP logic
             // Simplified: assume not found
        }
        
        if (phonemes != null) {
            // Apply stress
             return Pair(applyStress(phonemes, stress), rating) 
        }
        
        return Pair(null, rating)
    }

    private fun applyStress(ps: String, stress: Double?): String {
        if (stress == null) return ps
        // Simplified stress application for now, essentially a placeholder for the logic in en.py:95
        // Implementing full stress handling requires parsing the IPA string.
        // For 0.5/2.0 cap usage mostly.
        if (stress >= 1.0 && !ps.contains("ˈ") && ps.contains("ˌ")) {
            return ps.replace("ˌ", "ˈ")
        }
        return ps
    }

    private fun _s(stem: String?): String? {
        if (stem.isNullOrEmpty()) return null
        val last = stem.last()
        return if ("ptkfθ".contains(last)) stem + "s"
        else if ("szʃʒʧʤ".contains(last)) stem + (if (british) "ɪ" else "ᵻ") + "z"
        else stem + "z"
    }

    private fun stem_s(word: String, tag: String?, stress: Double?, ctx: TokenContext?): Pair<String?, Int> {
        if (word.length < 3 || !word.endsWith("s")) return Pair(null, 4) // rating not really used in fail
        
        var stem: String? = null
        if (!word.endsWith("ss") && isKnown(word.dropLast(1), tag)) {
            stem = word.dropLast(1)
        } else if ((word.endsWith("'s") || (word.length > 4 && word.endsWith("es") && !word.endsWith("ies"))) && isKnown(word.dropLast(2), tag)) {
            stem = word.dropLast(2)
        } else if (word.length > 4 && word.endsWith("ies") && isKnown(word.dropLast(3) + "y", tag)) {
            stem = word.dropLast(3) + "y"
        } else {
            return Pair(null, 4)
        }
        
        val (lookupStem, rating) = lookup(stem!!, tag, stress, ctx)
        return Pair(_s(lookupStem), rating)
    }

    private fun _ed(stem: String?): String? {
        if (stem.isNullOrEmpty()) return null
        val last = stem.last()
        
        if ("pkfθʃsʧ".contains(last)) return stem + "t"
        if (last == 'd') return stem + (if (british) "ɪ" else "ᵻ") + "d"
        if (last != 't') return stem + "d"
        if (british || stem.length < 2) return stem + "ɪd"
        
        val secondLast = stem[stem.length - 2].toString()
        if (US_TAUS.contains(secondLast)) return stem.dropLast(1) + "ɾᵻd" // Flapping
        return stem + "ᵻd"
    }

    private fun stem_ed(word: String, tag: String?, stress: Double?, ctx: TokenContext?): Pair<String?, Int> {
        if (word.length < 4 || !word.endsWith("d")) return Pair(null, 4)
        
        var stem: String? = null
        if (!word.endsWith("dd") && isKnown(word.dropLast(1), tag)) {
            stem = word.dropLast(1)
        } else if (word.length > 4 && word.endsWith("ed") && !word.endsWith("eed") && isKnown(word.dropLast(2), tag)) {
            stem = word.dropLast(2)
        } else {
            return Pair(null, 4)
        }
        
        val (lookupStem, rating) = lookup(stem!!, tag, stress, ctx)
        return Pair(_ed(lookupStem), rating)
    }

    private fun _ing(stem: String?): String? {
        if (stem.isNullOrEmpty()) return null
        if (british) {
             if ("əː".contains(stem.last())) return null
        } else if (stem.length > 1 && stem.last() == 't') {
            val secondLast = stem[stem.length - 2].toString()
            if (US_TAUS.contains(secondLast)) return stem.dropLast(1) + "ɾɪŋ"
        }
        return stem + "ɪŋ"
    }

    private fun stem_ing(word: String, tag: String?, stress: Double?, ctx: TokenContext?): Pair<String?, Int> {
        if (word.length < 5 || !word.endsWith("ing")) return Pair(null, 4)
        
        var stem: String? = null
        if (word.length > 5 && isKnown(word.dropLast(3), tag)) {
            stem = word.dropLast(3)
        } else if (isKnown(word.dropLast(3) + "e", tag)) {
            stem = word.dropLast(3) + "e"
        } else if (word.length > 5 && Regex("([bcdgklmnprstvxz])\\1ing$|cking$").containsMatchIn(word) && isKnown(word.dropLast(4), tag)) {
            stem = word.dropLast(4)
        } else {
            return Pair(null, 4)
        }
        
        val (lookupStem, rating) = lookup(stem!!, tag, stress, ctx)
        return Pair(_ing(lookupStem), rating)
    }
    
    // Fallback logic calling stems
    fun getWord(word: String, tag: String?, stress: Double?, ctx: TokenContext?): Pair<String?, Int> {
        // Special case handling omitted (SYMBOLS etc handled in lookup or separate method?)
        // The python code calls get_special_case -> lookup -> stems.
        // We simplified lookup to do direct dictionary check.
        // We need this higher level orchestration.
        
        // Basic lookup first
        if (isKnown(word, tag)) {
            return lookup(word, tag, stress, ctx)
        }
        
        // Stemming fallbacks
        if (word.endsWith("s'") && isKnown(word.dropLast(2) + "'s", tag)) {
            return lookup(word.dropLast(2) + "'s", tag, stress, ctx)
        }
        if (word.endsWith("'") && isKnown(word.dropLast(1), tag)) {
            return lookup(word.dropLast(1), tag, stress, ctx)
        }
        
        val sRes = stem_s(word, tag, stress, ctx)
        if (sRes.first != null) return sRes
        
        val edRes = stem_ed(word, tag, stress, ctx)
        if (edRes.first != null) return edRes
        
        val ingRes = stem_ing(word, tag, if (stress == null) 0.5 else stress, ctx)
        if (ingRes.first != null) return ingRes
        
        return Pair(null, 4)
    }
}
