package com.nekospeak.tts.engine.piper

import com.google.gson.annotations.SerializedName

data class PiperConfig(
    val audio: PiperAudio,
    val espeak: PiperEspeak,
    val inference: PiperInference,
    @SerializedName("phoneme_id_map") val phonemeIdMap: Map<String, List<Int>>,
    @SerializedName("speaker_id_map") val speakerIdMap: Map<String, Int> = emptyMap()
)

data class PiperAudio(
    @SerializedName("sample_rate") val sampleRate: Int
)

data class PiperEspeak(
    val voice: String
)

data class PiperInference(
    @SerializedName("noise_scale") val noiseScale: Float = 0.667f,
    @SerializedName("length_scale") val lengthScale: Float = 1.0f,
    @SerializedName("noise_w") val noiseW: Float = 0.8f
)
