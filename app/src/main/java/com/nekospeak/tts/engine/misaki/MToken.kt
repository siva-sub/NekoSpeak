package com.nekospeak.tts.engine.misaki

data class MToken(
    val text: String,
    val tag: String,
    var whitespace: String,
    var phonemes: String? = null,
    val startTs: Float? = null,
    val endTs: Float? = null,
    val attributes: Underscore = Underscore()
) {
    data class Underscore(
        var isHead: Boolean = false,
        var numFlags: String = "",
        var prespace: Boolean = false,
        var stress: Double? = null,
        var currency: String? = null,
        var alias: String? = null,
        var rating: Int? = null,
        // Added for JA logic (future proofing)
        var pron: String? = null,
        var acc: Int? = null,
        var moraSize: Int? = null,
        var chainFlag: Boolean = false,
        var moras: List<String>? = null,
        var accents: List<Int>? = null,
        var pitch: String? = null
    )
}
