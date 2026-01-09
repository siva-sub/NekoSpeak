package com.nekospeak.tts.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
                }
            }
            
            Divider()
            
            SettingsSection(title = "Performance") {
                Column(Modifier.padding(horizontal = 16.dp)) {
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
                    subtitle = "Version 1.0.0",
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
