package com.nekospeak.tts.engine.pocket

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin SentencePiece Unigram Tokenizer for Pocket-TTS.
 * 
 * Parses the tokenizer.model (Protocol Buffers format) directly without
 * requiring native libraries, making it compatible with Android.
 * 
 * Implements the Unigram language model tokenization algorithm.
 */
class PocketTokenizer(private val context: Context) {
    
    companion object {
        private const val TAG = "PocketTokenizer"
        private const val TOKENIZER_MODEL = "pocket/tokenizer.model"
        
        // Special tokens (from parsed model)
        const val UNK_TOKEN_ID = 0L  // <unk>
        const val BOS_TOKEN_ID = 1L  // <s>
        const val EOS_TOKEN_ID = 2L  // </s>
        const val PAD_TOKEN_ID = 3L  // <pad>
        
        // SentencePiece uses U+2581 (▁) for word boundary
        private const val WORD_BOUNDARY = "▁"
    }
    
    // Vocabulary: token string -> (id, score)
    private val vocabulary = mutableMapOf<String, Pair<Int, Float>>()
    private val reverseVocab = mutableMapOf<Int, String>()
    
    // Byte fallback tokens (for unknown bytes)
    private val byteFallbackTokens = mutableMapOf<Int, Int>() // byte value -> token id
    
    private var isLoaded = false
    private var unkTokenId = 0
    
    /**
     * Load and parse the SentencePiece model file.
     */
    fun load() {
        val tokenizerFile = File(context.filesDir, TOKENIZER_MODEL)
        
        if (!tokenizerFile.exists()) {
            Log.e(TAG, "Tokenizer model not found: ${tokenizerFile.absolutePath}")
            throw IllegalStateException("Tokenizer model not found")
        }
        
        try {
            val data = tokenizerFile.readBytes()
            parseProtobuf(data)
            isLoaded = true
            Log.i(TAG, "Loaded SentencePiece tokenizer: ${vocabulary.size} tokens")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse tokenizer model", e)
            throw e
        }
    }
    
    /**
     * Parse the SentencePiece ModelProto protobuf format.
     * 
     * ModelProto structure (simplified):
     * - Field 1 (repeated): SentencePiece pieces
     *   - Field 1: piece (string)
     *   - Field 2: score (float)
     *   - Field 3: type (int enum: 1=NORMAL, 2=UNKNOWN, 3=CONTROL, 6=BYTE)
     */
    private fun parseProtobuf(data: ByteArray) {
        var pos = 0
        var tokenId = 0
        
        while (pos < data.size) {
            // Read varint: wire type and field number
            val (wireAndField, newPos) = readVarint(data, pos)
            pos = newPos
            
            val fieldNum = wireAndField shr 3
            val wireType = wireAndField and 0x07
            
            when (wireType) {
                0 -> { // Varint
                    val (_, p) = readVarint(data, pos)
                    pos = p
                }
                1 -> { // 64-bit
                    pos += 8
                }
                2 -> { // Length-delimited
                    val (length, p) = readVarint(data, pos)
                    pos = p
                    val value = data.sliceArray(pos until (pos + length).coerceAtMost(data.size))
                    pos += length
                    
                    // Field 1 in ModelProto is pieces
                    if (fieldNum == 1 && value.size > 2) {
                        parsePiece(value, tokenId)
                        tokenId++
                    }
                }
                5 -> { // 32-bit
                    pos += 4
                }
                else -> break
            }
        }
        
        Log.d(TAG, "Parsed $tokenId tokens, vocabulary size: ${vocabulary.size}")
    }
    
    private fun parsePiece(data: ByteArray, tokenId: Int) {
        var pos = 0
        var pieceStr: String? = null
        var score = 0f
        var pieceType = 1 // Default: NORMAL
        
        while (pos < data.size) {
            val (wireAndField, newPos) = readVarint(data, pos)
            pos = newPos
            if (pos >= data.size) break
            
            val fieldNum = wireAndField shr 3
            val wireType = wireAndField and 0x07
            
            when (wireType) {
                0 -> { // Varint
                    val (v, p) = readVarint(data, pos)
                    pos = p
                    if (fieldNum == 3) pieceType = v
                }
                2 -> { // Length-delimited (string)
                    val (length, p) = readVarint(data, pos)
                    pos = p
                    if (fieldNum == 1) {
                        pieceStr = try {
                            String(data, pos, length, Charsets.UTF_8)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    pos += length
                }
                5 -> { // 32-bit (float)
                    if (fieldNum == 2 && pos + 4 <= data.size) {
                        score = ByteBuffer.wrap(data, pos, 4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .float
                    }
                    pos += 4
                }
                else -> break
            }
        }
        
        if (pieceStr != null) {
            vocabulary[pieceStr] = Pair(tokenId, score)
            reverseVocab[tokenId] = pieceStr
            
            // Track special tokens
            when (pieceType) {
                2 -> unkTokenId = tokenId  // UNKNOWN
                6 -> {  // BYTE fallback token like <0x00>
                    val byteMatch = Regex("<0x([0-9A-Fa-f]{2})>").find(pieceStr)
                    if (byteMatch != null) {
                        val byteValue = byteMatch.groupValues[1].toInt(16)
                        byteFallbackTokens[byteValue] = tokenId
                    }
                }
            }
        }
    }
    
    private fun readVarint(data: ByteArray, startPos: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var pos = startPos
        
        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF
            pos++
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift >= 32) break
        }
        
        return Pair(result, pos)
    }
    
    /**
     * Encode text to token IDs using Unigram tokenization.
     * 
     * Matches reference implementation: NO BOS/EOS tokens are added.
     * The model handles these internally.
     * 
     * @param text Input text
     * @param skipPreprocessing If true, skip punctuation/capitalization preprocessing
     * @return LongArray of token IDs
     */
    fun encode(text: String, skipPreprocessing: Boolean = false): LongArray {
        if (!isLoaded) {
            throw IllegalStateException("Tokenizer not loaded")
        }
        
        // Optionally preprocess text (can be controlled by engine layer)
        val processedText = if (skipPreprocessing) text.trim() else preprocessText(text)
        
        // Normalize: add word boundary marker at the start and after spaces
        val normalized = normalizeText(processedText)
        val tokens = tokenize(normalized)
        
        Log.d(TAG, "Tokenized '${text.take(30)}...' -> ${tokens.size} tokens: ${tokens.take(10)}...")
        
        // Reference implementation does NOT add BOS/EOS - model handles this
        return tokens.map { it.toLong() }.toLongArray()
    }
    
    /**
     * Preprocess text for the model (matching reference implementation).
     * - Ensure proper punctuation at end
     * - Capitalize first letter
     */
    private fun preprocessText(text: String): String {
        var processed = text.trim()
        if (processed.isEmpty()) return processed
        
        // Ensure proper punctuation at end
        if (processed.last().isLetterOrDigit()) {
            processed = "$processed."
        }
        
        // Capitalize first letter if lowercase
        if (processed.first().isLowerCase()) {
            processed = processed.replaceFirstChar { it.uppercase() }
        }
        
        return processed
    }
    
    /**
     * Normalize text for SentencePiece.
     * - Apply NFKC Unicode normalization (standard SentencePiece behavior)
     * - Replace spaces with the word boundary marker (▁)
     */
    private fun normalizeText(text: String): String {
        // NFKC normalization (standard for SentencePiece)
        val nfkc = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
        // SentencePiece: word boundary at start and after each space
        return WORD_BOUNDARY + nfkc.replace(" ", WORD_BOUNDARY)
    }
    
    /**
     * Tokenize using Viterbi algorithm for proper Unigram language model.
     * 
     * This finds the best tokenization by maximizing the sum of log-probabilities (scores)
     * using dynamic programming. O(n * maxLen) complexity.
     */
    private fun tokenize(text: String): List<Int> {
        if (text.isEmpty()) return emptyList()
        
        val n = text.length
        val maxLen = 32  // Maximum token length
        
        // DP arrays: best score and backpointer to achieve it
        val bestScore = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val bestLen = IntArray(n + 1) { 0 }
        val bestTokenId = IntArray(n + 1) { unkTokenId }
        bestScore[0] = 0f
        
        // Forward pass: find best path
        for (pos in 0 until n) {
            if (bestScore[pos] == Float.NEGATIVE_INFINITY) continue
            
            // Try all possible token lengths at this position
            val remainingLen = minOf(maxLen, n - pos)
            for (len in 1..remainingLen) {
                val substr = text.substring(pos, pos + len)
                val tokenInfo = vocabulary[substr]
                
                if (tokenInfo != null) {
                    val (tokenId, score) = tokenInfo
                    val newScore = bestScore[pos] + score
                    
                    if (newScore > bestScore[pos + len]) {
                        bestScore[pos + len] = newScore
                        bestLen[pos + len] = len
                        bestTokenId[pos + len] = tokenId
                    }
                }
            }
            
            // If no token matches at all, use byte fallback (score = -10)
            if (bestScore[pos + 1] == Float.NEGATIVE_INFINITY) {
                // Handle Unicode code point properly (for emoji/surrogates)
                val codePoint = Character.codePointAt(text, pos)
                val charCount = Character.charCount(codePoint)
                val cpBytes = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
                
                // Use byte fallback tokens
                var fallbackScore = bestScore[pos]
                for (b in cpBytes) {
                    val byteValue = b.toInt() and 0xFF
                    val fallbackToken = byteFallbackTokens[byteValue]
                    if (fallbackToken != null) {
                        fallbackScore += -10f  // Penalty for byte fallback
                    }
                }
                
                val endPos = pos + charCount
                if (endPos <= n && fallbackScore > bestScore[endPos]) {
                    bestScore[endPos] = fallbackScore
                    bestLen[endPos] = charCount
                    bestTokenId[endPos] = -1  // Marker for byte fallback
                }
            }
        }
        
        // Backward pass: reconstruct best path
        val tokens = mutableListOf<Int>()
        var pos = n
        
        while (pos > 0) {
            val len = bestLen[pos]
            val tokenId = bestTokenId[pos]
            
            if (len == 0) {
                // Fallback: couldn't find path, use UNK for remaining
                tokens.add(0, unkTokenId)
                pos--
            } else if (tokenId == -1) {
                // Byte fallback case
                val startPos = pos - len
                val codePoint = Character.codePointAt(text, startPos)
                val cpBytes = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
                
                for (b in cpBytes.reversed()) {
                    val byteValue = b.toInt() and 0xFF
                    val fallbackToken = byteFallbackTokens[byteValue]
                    if (fallbackToken != null) {
                        tokens.add(0, fallbackToken)
                    } else {
                        tokens.add(0, unkTokenId)
                    }
                }
                pos = startPos
            } else {
                tokens.add(0, tokenId)
                pos -= len
            }
        }
        
        return tokens
    }
    
    /**
     * Decode token IDs back to text.
     */
    fun decode(tokens: LongArray): String {
        return tokens
            .filter { it !in listOf(PAD_TOKEN_ID, BOS_TOKEN_ID, EOS_TOKEN_ID, UNK_TOKEN_ID.toLong()) }
            .mapNotNull { reverseVocab[it.toInt()] }
            .joinToString("")
            .replace(WORD_BOUNDARY, " ")
            .trim()
    }
    
    fun isLoaded(): Boolean = isLoaded
    
    fun usingSentencePiece(): Boolean = isLoaded
    
    fun release() {
        vocabulary.clear()
        reverseVocab.clear()
        byteFallbackTokens.clear()
        isLoaded = false
    }
}
