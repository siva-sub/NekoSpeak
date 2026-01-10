package com.nekospeak.tts.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nekospeak.tts.data.PrefsManager
import com.nekospeak.tts.ui.components.VoiceCard
import com.nekospeak.tts.ui.viewmodel.VoicesViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicesScreen(
    navController: NavController,
    viewModel: VoicesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PrefsManager(context) }
    
    // Test Speech State
    var testText by remember { mutableStateOf("Hello, I am NekoSpeak.") }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    
    DisposableEffect(Unit) {
        // Explicitly use our own engine package to ensure we test NekoSpeak
        // regardless of the system-wide default setting.
        tts = TextToSpeech(context, { status ->
             if (status == TextToSpeech.SUCCESS) {
                 // Init
             }
        }, "com.nekospeak.tts")
        onDispose {
            tts?.shutdown()
        }
    }
    
    // Sync ViewModel selection with Prefs
    LaunchedEffect(uiState.selectedVoiceId) {
        uiState.selectedVoiceId?.let { 
             prefs.currentVoice = it 
        }
    }
    
    // Load initial selection and voice list from prefs
    LaunchedEffect(Unit) {
        viewModel.loadVoices() // Refresh list (in case model changed)
        viewModel.selectVoice(prefs.currentVoice)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Voices", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${uiState.filteredVoices.size} voices available",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Test Speech Bar
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = testText,
                        onValueChange = { testText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type to speak...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                             val voiceId = uiState.selectedVoiceId ?: prefs.currentVoice
                             val params = android.os.Bundle()
                             params.putString("voiceName", voiceId)
                             tts?.speak(testText, TextToSpeech.QUEUE_FLUSH, params, "test_id")
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.PlayArrow, "Speak")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search voices...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            
            // Visible Filters (LazyRow)
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedGender == null && uiState.selectedRegion == null,
                        onClick = { viewModel.clearFilters() },
                        label = { Text("All") },
                        leadingIcon = if (uiState.selectedGender == null && uiState.selectedRegion == null) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedRegion == "US",
                        onClick = { 
                            if (uiState.selectedRegion == "US") viewModel.selectRegion(null) else viewModel.selectRegion("US")
                        },
                        label = { Text("🇺🇸 US") }
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedRegion == "UK",
                        onClick = { 
                            if (uiState.selectedRegion == "UK") viewModel.selectRegion(null) else viewModel.selectRegion("UK")
                        },
                        label = { Text("🇬🇧 UK") }
                    )
                }
                item {
                     FilterChip(
                        selected = uiState.selectedGender == "Male",
                        onClick = { 
                            if (uiState.selectedGender == "Male") viewModel.selectGender(null) else viewModel.selectGender("Male")
                        },
                        label = { Text("Male") }
                    )
                }
                item {
                     FilterChip(
                        selected = uiState.selectedGender == "Female",
                        onClick = { 
                            if (uiState.selectedGender == "Female") viewModel.selectGender(null) else viewModel.selectGender("Female")
                        },
                        label = { Text("Female") }
                    )
                }
            }
            
            if (uiState.filteredVoices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No voices found", style = MaterialTheme.typography.titleMedium)
                        Text("Try adjusting your filters", style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { viewModel.clearFilters() },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Clear Filters")
                        }
                    }
                }
            } else {
                // Voice List
                LazyColumn(
                    contentPadding = PaddingValues(start=16.dp, end=16.dp, bottom=100.dp), // Extra bottom padding for TestBar
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.filteredVoices) { voice ->
                        VoiceCard(
                            voice = voice,
                            isSelected = voice.id == uiState.selectedVoiceId,
                            onVoiceSelected = { viewModel.selectVoice(voice.id) }
                        )
                    }
                }
            }
        }
    }
}
