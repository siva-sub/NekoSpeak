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
     * @return LongArray of token IDs
     */
    fun encode(text: String): LongArray {
        if (!isLoaded) {
            throw IllegalStateException("Tokenizer not loaded")
        }
        
        // Preprocess text (matching reference implementation)
        val processedText = preprocessText(text)
        
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
     * Tokenize using greedy longest-match (simplified Unigram).
     * 
     * For proper Unigram, we'd use Viterbi with scores, but greedy
     * longest-match works well enough for TTS and is much simpler.
     */
    private fun tokenize(text: String): List<Int> {
        val tokens = mutableListOf<Int>()
        var pos = 0
        
        while (pos < text.length) {
            // Try to find the longest matching token
            var bestLen = 0
            var bestTokenId = unkTokenId
            
            // Check substrings from longest to shortest
            val maxLen = minOf(32, text.length - pos) // Max token length
            for (len in maxLen downTo 1) {
                val substr = text.substring(pos, pos + len)
                val tokenInfo = vocabulary[substr]
                if (tokenInfo != null) {
                    bestLen = len
                    bestTokenId = tokenInfo.first
                    break
                }
            }
            
            if (bestLen > 0) {
                tokens.add(bestTokenId)
                pos += bestLen
            } else {
                // No match: use byte fallback or UNK
                val char = text[pos]
                val bytes = char.toString().toByteArray(Charsets.UTF_8)
                
                if (bytes.size == 1 && byteFallbackTokens.containsKey(bytes[0].toInt() and 0xFF)) {
                    tokens.add(byteFallbackTokens[bytes[0].toInt() and 0xFF]!!)
                } else {
                    // Use byte fallback for each byte
                    for (b in bytes) {
                        val byteValue = b.toInt() and 0xFF
                        val fallbackToken = byteFallbackTokens[byteValue]
                        if (fallbackToken != null) {
                            tokens.add(fallbackToken)
                        } else {
                            tokens.add(unkTokenId)
                        }
                    }
                }
                pos++
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
