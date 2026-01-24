package com.nekospeak.tts.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.nekospeak.tts.ui.components.AudioRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderScreen(
    navController: NavController,
    onVoiceRecorded: (audioPath: String, name: String, transcript: String) -> Unit  // Callback with audio file path, name, and transcript
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Sample text for voice cloning (approx. 20-30 seconds of reading)
    // Note: Text is just a guide for what to read - it's NOT used for cloning
    // Voice cloning is purely audio-based via mimi_encoder
    val sampleTranscript = """Welcome to voice cloning. Please read this entire passage at your natural speaking pace. The technology behind voice cloning has made remarkable advances in recent years. Today, with just a short audio sample, we can create a digital representation of your unique voice. This works by analyzing the characteristics that make your voice distinctive, from your pitch and rhythm to your pronunciation patterns and natural pauses. For the best results, speak clearly but naturally, as if you were having a conversation with a friend. Avoid rushing through the text. Let your personality shine through in how you express each sentence.""".trimIndent()
    
    val audioRecorder = remember { AudioRecorder(context) }
    val recordingState by audioRecorder.recordingState.collectAsState()
    val amplitude by audioRecorder.amplitude.collectAsState()
    val durationMs by audioRecorder.durationMs.collectAsState()
    
    // Permission handling
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == 
                PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }
    
    // Voice name dialog
    var showNameDialog by remember { mutableStateOf(false) }
    var voiceName by remember { mutableStateOf("") }
    var recordedPath by remember { mutableStateOf<String?>(null) }
    
    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.release()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Voice") },
                navigationIcon = {
                    IconButton(onClick = { 
                        audioRecorder.cancelRecording()
                        navController.popBackStack() 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hasPermission) {
                // Permission request UI
                PermissionRequestContent(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            } else {
                // Recording UI
                RecordingContent(
                    recordingState = recordingState,
                    amplitude = amplitude,
                    durationMs = durationMs,
                    sampleTranscript = sampleTranscript,
                    onStartRecording = {
                        scope.launch {
                            audioRecorder.startRecording()
                        }
                    },
                    onStopRecording = {
                        audioRecorder.stopRecording()
                    },
                    onRetry = {
                        audioRecorder.reset()
                    },
                    onContinue = { path ->
                        recordedPath = path
                        showNameDialog = true
                    }
                )
            }
        }
    }
    
    // Voice name dialog
    if (showNameDialog && recordedPath != null) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Name Your Voice") },
            text = {
                Column {
                    Text(
                        "Give your cloned voice a name so you can find it later.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = voiceName,
                        onValueChange = { voiceName = it },
                        label = { Text("Voice Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNameDialog = false
                        // Return to previous screen with the path, name, AND transcript
                        onVoiceRecorded(recordedPath!!, voiceName, sampleTranscript)
                        navController.popBackStack()
                    },
                    enabled = voiceName.isNotBlank()
                ) {
                    Text("Clone Voice")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showNameDialog = false 
                    // Don't reset, let them try again or review
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Microphone Access Required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "To clone your voice, we need access to your microphone to record a sample.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onRequestPermission) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Grant Microphone Access")
        }
    }
}

@Composable
private fun RecordingContent(
    recordingState: AudioRecorder.RecordingState,
    amplitude: Float,
    durationMs: Long,
    sampleTranscript: String,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRetry: () -> Unit,
    onContinue: (String) -> Unit
) {
    val isRecording = recordingState is AudioRecorder.RecordingState.Recording
    val isRecorded = recordingState is AudioRecorder.RecordingState.Recorded
    val isError = recordingState is AudioRecorder.RecordingState.Error
    
    // Scroll state for small screens
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Waveform visualizer
        WaveformVisualizer(
            amplitude = amplitude,
            isRecording = isRecording,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Timer
        Text(
            formatDuration(durationMs),
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Light
            ),
            color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        
        if (isRecording) {
            Text(
                "Recording...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Record button
        RecordButton(
            isRecording = isRecording,
            isRecorded = isRecorded,
            onClick = {
                if (isRecording) {
                    onStopRecording()
                } else if (!isRecorded) {
                    onStartRecording()
                }
            }
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Tips or action buttons
        when {
            isError -> {
                val error = recordingState as AudioRecorder.RecordingState.Error
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        error.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(onClick = onRetry) {
                    Text("Try Again")
                }
            }
            
            isRecorded -> {
                val recorded = recordingState as AudioRecorder.RecordingState.Recorded
                val isValidDuration = recorded.durationMs >= AudioRecorder.MIN_DURATION_MS
                
                if (!isValidDuration) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            "Recording too short. Please record at least 3 seconds.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Playback controls
                var isPlaying by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val mediaPlayer = remember { MediaPlayer() }
                
                DisposableEffect(recorded.audioPath) {
                    try {
                        mediaPlayer.setDataSource(recorded.audioPath)
                        mediaPlayer.prepare()
                        mediaPlayer.setOnCompletionListener { isPlaying = false }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    onDispose {
                        mediaPlayer.release()
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                     // Play Preview
                    FilledTonalIconButton(
                        onClick = {
                            if (isPlaying) {
                                mediaPlayer.pause()
                                isPlaying = false
                            } else {
                                mediaPlayer.start()
                                isPlaying = true
                            }
                        },
                        enabled = isValidDuration
                    ) {
                        Icon(if (isPlaying) Icons.Default.Menu else Icons.Default.PlayArrow, "Play Preview")
                    }
                    
                    OutlinedButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Re-record")
                    }
                    
                    Button(
                        onClick = { onContinue(recorded.audioPath) },
                        enabled = isValidDuration
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Continue")
                    }
                }
            }
            
            else -> {
                // Tips
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "💡 Tips for best results:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Speak clearly and naturally")
                        Text("• Find a quiet environment")
                        Text("• Keep phone 6-12 inches away")
                        Text("• Record 15-30 seconds for best quality")
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            "Read this text aloud:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "(Any clear speech works - this text is just a guide)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                "\"$sampleTranscript\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    isRecorded: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val buttonColor by animateColorAsState(
        targetValue = if (isRecording) MaterialTheme.colorScheme.error 
                      else MaterialTheme.colorScheme.primary,
        label = "color"
    )
    
    Box(
        modifier = Modifier
            .size(100.dp)
            .scale(if (isRecording) scale else 1f)
            .clip(CircleShape)
            .background(buttonColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(buttonColor),
            enabled = !isRecorded
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                modifier = Modifier.size(40.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun WaveformVisualizer(
    amplitude: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val amplitudeHistory = remember { mutableStateListOf<Float>() }
    
    LaunchedEffect(amplitude) {
        if (isRecording) {
            amplitudeHistory.add(amplitude)
            if (amplitudeHistory.size > 50) {
                amplitudeHistory.removeAt(0)
            }
        }
    }
    
    LaunchedEffect(isRecording) {
        if (!isRecording) {
            amplitudeHistory.clear()
        }
    }
    
    val waveColor = if (isRecording) 
        MaterialTheme.colorScheme.error 
    else 
        MaterialTheme.colorScheme.outline
    
    Canvas(modifier = modifier) {
        val barWidth = 6f
        val barSpacing = 4f
        val centerY = size.height / 2
        val maxBars = ((size.width / (barWidth + barSpacing)).toInt()).coerceAtMost(50)
        
        val displayAmplitudes = if (amplitudeHistory.isEmpty()) {
            // Show placeholder bars when not recording
            List(maxBars) { 0.05f + (it % 5) * 0.02f }
        } else {
            amplitudeHistory.takeLast(maxBars)
        }
        
        val startX = (size.width - displayAmplitudes.size * (barWidth + barSpacing)) / 2
        
        displayAmplitudes.forEachIndexed { index, amp ->
            val barHeight = (amp * size.height * 0.8f).coerceIn(4f, size.height * 0.8f)
            val x = startX + index * (barWidth + barSpacing)
            
            drawLine(
                color = waveColor,
                start = Offset(x + barWidth / 2, centerY - barHeight / 2),
                end = Offset(x + barWidth / 2, centerY + barHeight / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000) / 60
    return "%02d:%02d".format(minutes, seconds)
}
