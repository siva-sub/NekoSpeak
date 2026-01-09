package com.nekospeak.tts.engine.misaki

import java.util.regex.Pattern

class G2P(private val lexicon: Lexicon, private val fallback: ((String) -> String?)? = null) {

    // Regex adaptations
    // Note: Java regex requires double escaping backslashes.
    // Python subtokenize regex:
    // r"^['‘’]+|\p{Lu}(?=\p{Lu}\p{Ll})|(?:^-)?(?:\d?[,.]?\d)+|[-_]+|['‘’]{2,}|\p{L}*?(?:['‘’]\p{L})*?\p{Ll}(?=\p{Lu})|\p{L}+(?:['‘’]\p{L})*|[^-_\p{L}'‘’\d]|['‘’]+$"
    private val SUBTOKEN_REGEX = Pattern.compile(
        "^['‘’]+|\\p{Lu}(?=\\p{Lu}\\p{Ll})|(?:^-)?(?:\\d?[,.]?\\d)+|[-_]+|['‘’]{2,}|\\p{L}*?(?:['‘’]\\p{L})*?\\p{Ll}(?=\\p{Lu})|\\p{L}+(?:['‘’]\\p{L})*|[^-_\\p{L}'‘’\\d]|['‘’]+$"
    )
    
    private val LINK_REGEX = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]*)\\)")
    
    private val PUNCT_TAGS = setOf(".", ",", "-LRB-", "-RRB-", "``", "\"\"", "''", ":", "$", "#", "NFP")
    private val PUNCTS = setOf(";", ":", ",", ".", "!", "?", "—", "…", "\"", "“", "”")
    private val NON_QUOTE_PUNCTS = PUNCTS.filter { !it.contains("\"") && !it.contains("“") && !it.contains("”") }.toSet()
     private val PUNCT_TAG_PHONEMES = mapOf(
        "-LRB-" to "(", "-RRB-" to ")", 
        "``" to "\u201C", "\"\"" to "\u201D", "''" to "\u201D"
    )
    
    // Simple tokenizer roughly matching Spacy's behavior for English
    private fun simpleTokenize(text: String): List<MToken> {
        // This is a naive approximation. Real spacy tokenization is complex.
        // We split by spaces and preserve punctuation.
        // Or we can use the SUBTOKEN_REGEX logic partially?
        // Let's iterate and split.
        
        val tokens = mutableListOf<MToken>()
        // Very basic split for now to get moving. 
        // Improvement: split punctuation off words (e.g. "hello." -> "hello", ".")
        val rawTokens = text.split(Regex("(?<=\\s)|(?=\\s)"))
        
        for (raw in rawTokens) {
            val t = raw.trim()
            if (t.isEmpty()) continue
            
            // Check for punctuation split
            // e.g. "word." -> "word", "."
            // "word," -> "word", ","
            // This needs to be robust. 
            // For now, assume usage of SUBTOKEN_REGEX later handles internal splitting?
            // Actually G2P.kt in python uses subtokenize on the tokens produced by Spacy.
            // So we need distinct tokens. 
            val parts = splitPunctuation(t)
            tokens.addAll(parts.map { 
                MToken(text = it, tag = guessTag(it), whitespace = if (raw.endsWith(" ")) " " else "")
            })
        }
        
        // Fix whitespace attribution (naive)
        for (i in 0 until tokens.size - 1) {
             tokens[i].whitespace = " " // Default to space for now logic is weak here without real offset tracking
        }
        if (tokens.isNotEmpty()) tokens.last().whitespace = ""
        
        return tokens
    }
    
    private fun splitPunctuation(word: String): List<String> {
        // Regex to split punctuation from ends
        // e.g. (Hello) -> (, Hello, )
        if (word.length == 1) return listOf(word)
        val res = mutableListOf<String>()
        val p = Pattern.compile("^(\\p{Punct}*)(.*?)(\\p{Punct}*)$")
        val m = p.matcher(word)
        if (m.find()) {
            val pre = m.group(1) ?: ""
            val word = m.group(2) ?: ""
            val post = m.group(3) ?: ""
            if (pre.isNotEmpty()) res.add(pre)
            if (word.isNotEmpty()) res.add(word)
            if (post.isNotEmpty()) res.add(post)
        } else {
            res.add(word)
        }
        return res
    }

    private fun guessTag(word: String): String {
        // Simple heuristic tagger
        if (word.all { !it.isLetterOrDigit() }) {
             if (PUNCT_TAG_PHONEMES.containsKey(word)) return word // simplistic
             if (word == ".") return "."
             if (word == ",") return ","
             return ":" // generic punct
        }
        if (word.all { it.isDigit() }) return "CD"
        if (word.equals("the", ignoreCase = true)) return "DT"
        if (word.equals("a", ignoreCase = true) || word.equals("an", ignoreCase = true)) return "DT"
        if (word.endsWith("ing")) return "VBG"
        if (word.endsWith("ed")) return "VBD"
        if (word.endsWith("ly")) return "RB"
        if (word.isNotEmpty() && word[0].isUpperCase()) return "NNP" // Proper noun guess
        return "NN" // Default noun
    }
    
    
    private fun preprocess(text: String): Triple<String, List<String>, Map<Int, Any>> {
        val matcher = LINK_REGEX.matcher(text)
        val sb = StringBuffer()
        while(matcher.find()) {
             matcher.appendReplacement(sb, matcher.group(1) ?: "")
        }
        matcher.appendTail(sb)
        return Triple(sb.toString(), emptyList(), emptyMap())
    }
    
    private fun retokenize(tokens: List<MToken>): List<MToken> {
         val result = mutableListOf<MToken>()
         for (token in tokens) {
              val subMatcher = SUBTOKEN_REGEX.matcher(token.text)
              val subs = mutableListOf<String>()
              while (subMatcher.find()) {
                  subs.add(subMatcher.group())
              }
              
              if (subs.isEmpty()) {
                  result.add(token)
              } else {
                  for ((i, sub) in subs.withIndex()) {
                      val newT = token.copy(text = sub, whitespace = if(i == subs.size -1) token.whitespace else "")
                      // Update tags logic?
                      newT.attributes.isHead = i == 0
                      result.add(newT)
                  }
              }
         }
         return result
    }

    fun phonemize(text: String): String {
        // 1. Preprocess
        val (preprocessedText, tokens, features) = preprocess(text)
        
        // 2. Tokenize (Simulator)
        var mTokens = simpleTokenize(preprocessedText)
        
        // 3. Apply features (basic logic from python)
        
        // 4. Fold Left (skip)
        
        // 5. Retokenize
        val processedTokens = retokenize(mTokens)
        
        // 6. G2P Resolution
        var ctx = TokenContext()
        val result = StringBuilder()
        
        for (token in processedTokens) {
            if (token.phonemes == null) {
                // Lexicon Lookup
                val (ps, rating) = lexicon.getWord(token.text, token.tag, token.attributes.stress, ctx)
                token.phonemes = ps
                token.attributes.rating = rating
                
                // Fallback (Espeak)
                if (token.phonemes == null && fallback != null) {
                    token.phonemes = fallback.invoke(token.text)
                }
                
                if (token.phonemes == null) {
                     // Still null? Silence or keep text?
                     // Verify behavior. Python keeps text if unk?
                     // "unk" is '?' usually.
                }
            }
            
            // Context update
            ctx = tokenContext(ctx, token.phonemes, token)
            
            // Append
            result.append(token.phonemes ?: "")
            result.append(token.whitespace)
        }
        
        // Post-processing
        return result.toString()
            .replace("ɾ", "T")
            .replace("ʔ", "t")
            .trim()
    }

    private fun tokenContext(ctx: TokenContext, ps: String?, token: MToken): TokenContext {
        // Logic from en.py:646
        var vowel = ctx.futureVowel
        // python: vowel = next((None if c in NON_QUOTE_PUNCTS else (c in VOWELS) for c in ps ...
        // Complex logic to detect if next significant char is vowel.
        // For 'the' determination (the vs thee).
        
        // Simplified: check first phoneme
        if (!ps.isNullOrEmpty()) {
             // Basic vowel check (first char)
             val first = ps[0]
             // misaki VOWELS = "AIOQWYaiuæɑɒɔəɛɜɪʊʌᵻ" + "ieou..."
             val VOWELS = "AIOQWYaiuæɑɒɔəɛɜɪʊʌᵻ"
             vowel = VOWELS.contains(first)
        }
        
        val futureTo = token.text.equals("to", ignoreCase = true) || (token.text == "TO" && (token.tag == "TO" || token.tag == "IN"))
        
        return TokenContext(futureVowel = vowel, futureTo = futureTo)
    }
}
