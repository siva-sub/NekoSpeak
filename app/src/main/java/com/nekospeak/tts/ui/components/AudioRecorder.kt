package com.nekospeak.tts.ui.components

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * Audio recorder for voice cloning.
 * Records audio as 16-bit PCM WAV at 24kHz (matching Pocket-TTS requirements).
 */
class AudioRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 24000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val MAX_DURATION_MS = 30_000L
        const val MIN_DURATION_MS = 3_000L
    }
    
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false
    
    // Android audio effects for better voice quality
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()
    
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()
    
    private var outputFile: File? = null
    private var rawDataFile: File? = null
    
    sealed class RecordingState {
        object Idle : RecordingState()
        object Recording : RecordingState()
        data class Recorded(val audioPath: String, val durationMs: Long) : RecordingState()
        object Processing : RecordingState()
        data class Error(val message: String) : RecordingState()
    }
    
    /**
     * Start recording audio.
     * @return true if recording started successfully
     */
    suspend fun startRecording(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isRecording) {
                Log.w(TAG, "Already recording")
                return@withContext false
            }
            
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
                _recordingState.value = RecordingState.Error("Audio configuration not supported")
                return@withContext false
            }
            
            // Use VOICE_RECOGNITION for better platform voice processing
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _recordingState.value = RecordingState.Error("Failed to initialize audio recorder")
                audioRecord?.release()
                audioRecord = null
                return@withContext false
            }
            
            // Attach Android audio effects for better voice cloning quality
            val sessionId = audioRecord!!.audioSessionId
            
            // Noise Suppressor - removes background noise
            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)
                    Log.i(TAG, "NoiseSuppressor attached: ${noiseSuppressor?.enabled}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create NoiseSuppressor", e)
                }
            } else {
                Log.i(TAG, "NoiseSuppressor not available on this device")
            }
            
            // Automatic Gain Control - normalizes volume levels
            if (AutomaticGainControl.isAvailable()) {
                try {
                    automaticGainControl = AutomaticGainControl.create(sessionId)
                    Log.i(TAG, "AutomaticGainControl attached: ${automaticGainControl?.enabled}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create AutomaticGainControl", e)
                }
            } else {
                Log.i(TAG, "AutomaticGainControl not available on this device")
            }
            
            // Acoustic Echo Canceler - removes echo (useful if playing audio while recording)
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    acousticEchoCanceler = AcousticEchoCanceler.create(sessionId)
                    Log.i(TAG, "AcousticEchoCanceler attached: ${acousticEchoCanceler?.enabled}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create AcousticEchoCanceler", e)
                }
            } else {
                Log.i(TAG, "AcousticEchoCanceler not available on this device")
            }
            
            // Create output files
            val recordingsDir = File(context.cacheDir, "voice_recordings")
            recordingsDir.mkdirs()
            
            val timestamp = System.currentTimeMillis()
            rawDataFile = File(recordingsDir, "recording_${timestamp}.raw")
            outputFile = File(recordingsDir, "recording_${timestamp}.wav")
            
            isRecording = true
            _recordingState.value = RecordingState.Recording
            _durationMs.value = 0L
            
            audioRecord?.startRecording()
            
            // Start recording thread
            recordingThread = Thread {
                val buffer = ShortArray(bufferSize / 2)
                val rawOs = FileOutputStream(rawDataFile)
                val startTime = System.currentTimeMillis()
                var totalSamples = 0
                
                try {
                    while (isRecording) {
                        val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        
                        if (readCount > 0) {
                            // Write to raw file
                            val bytes = ByteArray(readCount * 2)
                            for (i in 0 until readCount) {
                                bytes[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                                bytes[i * 2 + 1] = (buffer[i].toInt() shr 8).toByte()
                            }
                            rawOs.write(bytes)
                            
                            totalSamples += readCount
                            
                            // Calculate amplitude for visualization
                            var maxAmp = 0
                            for (i in 0 until readCount) {
                                val amp = abs(buffer[i].toInt())
                                if (amp > maxAmp) maxAmp = amp
                            }
                            _amplitude.value = maxAmp.toFloat() / 32768f
                            
                            // Update duration
                            _durationMs.value = System.currentTimeMillis() - startTime
                            
                            // Auto-stop at max duration
                            if (_durationMs.value >= MAX_DURATION_MS) {
                                Log.i(TAG, "Max duration reached, stopping recording")
                                isRecording = false
                            }
                        }
                    }
                } finally {
                    rawOs.close()
                }
                
                // Convert raw to WAV
                convertRawToWav(rawDataFile!!, outputFile!!, totalSamples)
                
                // Cleanup raw file
                rawDataFile?.delete()
                
                val finalDuration = _durationMs.value
                _recordingState.value = RecordingState.Recorded(outputFile!!.absolutePath, finalDuration)
                
            }.also { it.start() }
            
            true
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission denied", e)
            _recordingState.value = RecordingState.Error("Microphone permission required")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _recordingState.value = RecordingState.Error("Failed to start recording: ${e.message}")
            false
        }
    }
    
    /**
     * Stop recording.
     */
    fun stopRecording() {
        if (!isRecording) return
        
        isRecording = false
        
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio record", e)
        }
        
        // Wait for thread to finish
        recordingThread?.join(1000)
        
        // Release audio effects
        try {
            noiseSuppressor?.release()
            noiseSuppressor = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing NoiseSuppressor", e)
        }
        
        try {
            automaticGainControl?.release()
            automaticGainControl = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AutomaticGainControl", e)
        }
        
        try {
            acousticEchoCanceler?.release()
            acousticEchoCanceler = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AcousticEchoCanceler", e)
        }
        
        audioRecord?.release()
        audioRecord = null
        recordingThread = null
        
        _amplitude.value = 0f
    }
    
    /**
     * Cancel recording and discard audio.
     */
    fun cancelRecording() {
        stopRecording()
        outputFile?.delete()
        rawDataFile?.delete()
        _recordingState.value = RecordingState.Idle
    }
    
    /**
     * Reset state to idle.
     */
    fun reset() {
        outputFile?.delete()
        rawDataFile?.delete()
        _recordingState.value = RecordingState.Idle
        _durationMs.value = 0L
        _amplitude.value = 0f
    }
    
    private fun convertRawToWav(rawFile: File, wavFile: File, totalSamples: Int) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val dataSize = totalSamples * channels * bitsPerSample / 8
        
        val rawData = rawFile.readBytes()
        
        FileOutputStream(wavFile).use { fos ->
            // RIFF header
            fos.write("RIFF".toByteArray())
            fos.write(intToBytes(36 + dataSize))  // File size - 8
            fos.write("WAVE".toByteArray())
            
            // fmt subchunk
            fos.write("fmt ".toByteArray())
            fos.write(intToBytes(16))  // Subchunk size
            fos.write(shortToBytes(1))  // Audio format (PCM)
            fos.write(shortToBytes(channels.toShort()))
            fos.write(intToBytes(SAMPLE_RATE))
            fos.write(intToBytes(byteRate))
            fos.write(shortToBytes((channels * bitsPerSample / 8).toShort()))  // Block align
            fos.write(shortToBytes(bitsPerSample.toShort()))
            
            // data subchunk
            fos.write("data".toByteArray())
            fos.write(intToBytes(dataSize))
            fos.write(rawData)
        }
        
        Log.d(TAG, "Converted to WAV: ${wavFile.absolutePath} (${wavFile.length()} bytes)")
    }
    
    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }
    
    private fun shortToBytes(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            (value.toInt() shr 8 and 0xFF).toByte()
        )
    }
    
    fun release() {
        cancelRecording()
    }
}
