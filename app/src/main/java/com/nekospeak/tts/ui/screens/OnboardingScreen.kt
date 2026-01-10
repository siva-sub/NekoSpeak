package com.nekospeak.tts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.nekospeak.tts.data.PrefsManager
import com.nekospeak.tts.ui.navigation.Screen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }
    
    var selectedModel by remember { mutableStateOf("kokoro_v1.0") }
    var selectedVoice by remember { mutableStateOf("af_heart") }
    
    // Default voices for onboarding
    val kokoroStarters = listOf(
        "af_heart" to "Heart",
        "am_adam" to "Adam",
        "af_bella" to "Bella"
    )
    val kittenStarters = listOf(
        "expr-voice-2-f" to "Kitten F2",
        "expr-voice-2-m" to "Kitten M2"
    )
    
    // Update default voice when model changes if invalid
    LaunchedEffect(selectedModel) {
        val validVoices = when (selectedModel) {
            "kokoro_v1.0" -> kokoroStarters.map { it.first }
            "kitten_nano" -> kittenStarters.map { it.first }
            "piper_en_US-amy-low" -> listOf("en_US-amy-low")
            else -> emptyList()
        }
        
        if (selectedVoice !in validVoices) {
            selectedVoice = validVoices.firstOrNull() ?: "af_heart"
        }
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1B2E), Color(0xFF10111C))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Header (Static)
            Text(
                text = "Welcome to NekoSpeak",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Private, on-device AI Text-to-Speech.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Pager Content
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    when (page) {
                        0 -> {
                            // Step 1: Model Selection
                            Text(
                                text = "1. Choose AI Model",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            )
                            
                            ModelSelectionCard(
                                title = "Kokoro v1.0",
                                description = "Expressive, realistic, and emotional. Best for short content.",
                                warning = "CPU Intensive (Slower)",
                                isSelected = selectedModel == "kokoro_v1.0",
                                onClick = { selectedModel = "kokoro_v1.0" }
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            ModelSelectionCard(
                                title = "Kitten TTS Nano",
                                description = "Lightning fast and battery efficient. Ideal for long books.",
                                warning = null,
                                isSelected = selectedModel == "kitten_nano",
                                onClick = { selectedModel = "kitten_nano" }
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            ModelSelectionCard(
                                title = "Piper (Amy Low)",
                                description = "Fast, natural English voice. Bundled offline.",
                                warning = null,
                                isSelected = selectedModel == "piper_en_US-amy-low",
                                onClick = { selectedModel = "piper_en_US-amy-low" }
                            )
                        }
                        1 -> {
                            // Step 2: Voice Selection
                            Text(
                                text = "2. Choose Starter Voice",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            )
                            
                            val voices = when (selectedModel) {
                                "kokoro_v1.0" -> kokoroStarters
                                "kitten_nano" -> kittenStarters
                                "piper_en_US-amy-low" -> listOf("en_US-amy-low" to "Amy (Low)")
                                else -> kokoroStarters
                            }
                            
                            // Simple list for voices
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                voices.forEach { (id, name) ->
                                    VoiceChip(
                                        name = name,
                                        isSelected = selectedVoice == id,
                                        onClick = { selectedVoice = id },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        2 -> {
                            // Step 3: System Setup
                            Text(
                                text = "3. Enable System-wide",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            )
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.05f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "To use NekoSpeak with other apps, set it as your default TTS engine.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = android.content.Intent("com.android.settings.TTS_SETTINGS")
                                                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Fallback
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Text("Open TTS Settings")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Pager Indicators
            Row(
                Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pagerState.pageCount) { iteration ->
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f)
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back / Skip logic could go here, but keeping it simple for now
                if (pagerState.currentPage > 0) {
                     TextButton(
                        onClick = { 
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    ) {
                        Text("Back", color = Color.White.copy(alpha = 0.7f))
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }

                if (pagerState.currentPage < 2) {
                    Button(
                        onClick = { 
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    ) {
                        Text("Next")
                    }
                } else {
                    Button(
                        onClick = {
                            prefs.currentModel = selectedModel
                            prefs.currentVoice = selectedVoice
                            prefs.isOnboardingComplete = true
                            
                            navController.navigate(Screen.Voices.route) {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Get Started")
                    }
                }
            }
        }
    }
}

@Composable
fun ModelSelectionCard(
    title: String,
    description: String,
    warning: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (warning != null) {
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF8A65), // Orange-ish
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
        modifier = modifier.height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
