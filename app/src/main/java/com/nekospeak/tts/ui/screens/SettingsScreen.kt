package com.nekospeak.tts.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            val prefs = remember { com.nekospeak.tts.data.PrefsManager(context) }
            // Use .value for manual control to avoid 'by' delegation complexity with mismatched imports if simpler
            var currentModel by remember { mutableStateOf(prefs.currentModel) }
            var threads by remember { mutableFloatStateOf(prefs.cpuThreads.toFloat()) }
            
            SettingsSection(title = "AI Model") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                currentModel = "kokoro_v1.0"
                                prefs.currentModel = "kokoro_v1.0"
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = currentModel == "kokoro_v1.0",
                            onClick = { 
                                currentModel = "kokoro_v1.0"
                                prefs.currentModel = "kokoro_v1.0"
                            }
                        )
                        Column {
                            Text("Kokoro v1.0 (Standard)", fontWeight = FontWeight.Bold)
                            Text("Best quality, slower (82M params)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                currentModel = "kitten_nano"
                                prefs.currentModel = "kitten_nano"
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = currentModel == "kitten_nano",
                            onClick = { 
                                currentModel = "kitten_nano"
                                prefs.currentModel = "kitten_nano"
                            }
                        )
                        Column {
                            Text("Kitten TTS Nano", fontWeight = FontWeight.Bold)
                            Text("Faster, lower quality", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Piper (ONNX) - Generic Selection
                    // Selection logic: If current model is already piper, keep it. 
                    // If switching TO piper, default to amy-low or check stored voice.
                    val isPiper = currentModel.startsWith("piper")
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isPiper) {
                                    // Switch to default/bundled Piper voice
                                    currentModel = "piper_en_US-amy-low"
                                    prefs.currentModel = "piper_en_US-amy-low"
                                    prefs.currentVoice = "en_US-amy-low" 
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = isPiper,
                            onClick = { 
                                if (!isPiper) {
                                    currentModel = "piper_en_US-amy-low"
                                    prefs.currentModel = "piper_en_US-amy-low"
                                    prefs.currentVoice = "en_US-amy-low"
                                }
                            }
                        )
                        Column {
                            Text("Piper (ONNX)", fontWeight = FontWeight.Bold)
                            val subtext = if (isPiper) {
                                val voiceId = currentModel.removePrefix("piper_")
                                "Active Voice: $voiceId"
                            } else {
                                "Fast, natural offline voices"
                            }
                            Text(subtext, style = MaterialTheme.typography.bodySmall)
                            
                            // Link to Voices Screen
                            if (isPiper) {
                                Text(
                                    "Ensure to select your preferred voice in the 'Voices' tab.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top=4.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Divider()
            
            SettingsSection(title = "Performance") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    // CPU Threads
                    Text("CPU Threads: ${threads.toInt()}", fontWeight = FontWeight.Medium)
                    Text(
                        "More threads = faster. Excessive threads may cause throttling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = threads,
                        onValueChange = { threads = it },
                        onValueChangeFinished = { prefs.cpuThreads = threads.toInt() },
                        valueRange = 1f..8f,
                        steps = 6
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Streaming Optimization (Token Size)
                    var tokenSize by remember { mutableIntStateOf(prefs.streamTokenSize) }
                    val displayTokenSize = if (tokenSize == 0) "Auto" else tokenSize.toString()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Stream Buffer: $displayTokenSize tokens", fontWeight = FontWeight.Medium)
                            Text(
                                "Lower = lower latency (faster start). Higher = more stable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tokenSize > 0 && tokenSize < 50) {
                                Text(
                                    "Warning: Very low values may cause crashes on some devices!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (currentModel == "kitten_nano" && tokenSize > 0 && tokenSize > 300) {
                                Text(
                                    "Warning: High values (>300) may cause crashes with Kitten TTS!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Button(
                            onClick = { 
                                tokenSize = 0 
                                prefs.streamTokenSize = 0
                            },
                            enabled = tokenSize != 0
                        ) {
                            Text("Reset")
                        }
                    }
                    
                    Slider(
                        value = if (tokenSize == 0) 50f else tokenSize.toFloat(),
                        onValueChange = { tokenSize = it.toInt() },
                        onValueChangeFinished = { prefs.streamTokenSize = tokenSize },
                        valueRange = 10f..500f,
                        steps = 48 
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Speed Control (Kitten & Piper)
                    if (currentModel == "kitten_nano" || currentModel.startsWith("piper")) {
                         var speed by remember { mutableFloatStateOf(prefs.speechSpeed) }
                         
                         Row(
                             verticalAlignment = Alignment.CenterVertically,
                             modifier = Modifier.fillMaxWidth()
                         ) {
                             Text("Speech Speed: ${String.format("%.1f", speed)}x", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                             Button(
                                 onClick = { 
                                     speed = 1.0f
                                     prefs.speechSpeed = 1.0f 
                                 },
                                 enabled = speed != 1.0f
                             ) {
                                 Text("Reset")
                             }
                         }
                         if (currentModel.startsWith("piper")) {
                             Text(
                                 "Note: Speed may not work on all Piper models due to model limitations.",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                             )
                         } else {
                             Text(
                                 "Adjust speaking rate.",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                             )
                         }
                         Slider(
                             value = speed,
                             onValueChange = { speed = it },
                             onValueChangeFinished = { prefs.speechSpeed = speed },
                             valueRange = 0.5f..2.0f,
                             steps = 14 // 0.1 increments
                         )
                    } else {
                        Text("Speech Speed", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                         Text(
                             "Fixed at 1.0x for Kokoro v1.0 (Standard Model).",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                         )
                    }
                }
            }
            
            Divider()
            
            SettingsSection(title = "Battery") {
                 SettingsItem(
                    title = "Disable Battery Optimization",
                    subtitle = "Recommended for seamless background playback on OnePlus/Oppo devices.",
                    icon = Icons.Default.Warning,
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback to generic settings
                             val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                             context.startActivity(intent)
                        }
                    }
                 )
            }

            Divider()
            
            SettingsSection(title = "General") {
                SettingsItem(
                    title = "System TTS Settings",
                    subtitle = "Manage engines and default settings",
                    icon = Icons.Default.Settings,
                    onClick = {
                        val intent = android.content.Intent("com.android.settings.TTS_SETTINGS")
                        context.startActivity(intent)
                    }
                )
            }
            
            Divider()
            
            SettingsSection(title = "About") {
                SettingsItem(
                    title = "NekoSpeak TTS",
                    subtitle = "Version ${com.nekospeak.tts.BuildConfig.VERSION_NAME}",
                    icon = Icons.Default.Info,
                    onClick = { }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let {
            { Icon(imageVector = it, contentDescription = null) }
        },
        modifier = Modifier.clickable { onClick() }
    )
}
